package com.metrolist.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.InetAddress
import java.net.BindException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object SpotifyConnectionConfig {
    const val CLIENT_ID = "78de29936d714ba38c77ca4913e587b3"
    const val REDIRECT = "http://127.0.0.1:8888/callback"
}

internal class SpotifyOAuth(private val acceptedMessage: String, private val rejectedMessage: String, private val timeoutMillis: Long = 180_000) : AutoCloseable {
    private val server = ServerSocket().apply {
        try {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8888), 4)
            soTimeout = minOf(1000L, timeoutMillis).coerceAtLeast(1).toInt()
        } catch (error: IOException) {
            close()
            if (error is BindException) throw SpotifyImportException(SpotifyFailure.PORT_BUSY)
            throw error
        }
    }
    @Volatile private var activeSocket: Socket? = null
    val redirect = SpotifyConnectionConfig.REDIRECT
    val verifier = SpotifyPkce.randomSecret()
    private val state = SpotifyPkce.randomSecret()
    fun authorizationUrl(): String = "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder()
        .addQueryParameter("client_id", SpotifyConnectionConfig.CLIENT_ID).addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", redirect).addQueryParameter("scope", "user-library-read")
        .addQueryParameter("state", state).addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("code_challenge", SpotifyPkce.challenge(verifier)).build().toString()

    suspend fun awaitCode(): String = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val socket = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                throw error
            }
            activeSocket = socket
            try {
                socket.use {
                    it.soTimeout = 2000
                    // Bound the request line: never accept an unbounded local HTTP request.
                    val bytes = ArrayList<Byte>()
                    val input = it.getInputStream()
                    while (bytes.size < 8192) {
                        val b = input.read()
                        if (b == -1 || b == 10) break
                        bytes.add(b.toByte())
                    }
                    var emptyHeader = true
                    var headersComplete = false
                    repeat(8192) {
                        if (!headersComplete) {
                            val byte = input.read()
                            if (byte == -1) throw IOException("Incomplete callback")
                            if (byte == 10) {
                                if (emptyHeader) headersComplete = true
                                emptyHeader = true
                            } else if (byte != 13) emptyHeader = false
                        }
                    }
                    if (!headersComplete) throw IOException("Oversized callback")
                    val line = bytes.toByteArray().toString(Charsets.US_ASCII).trim()
                    val parts = line.split(' ')
                    val url = parts.getOrNull(1)?.let { target -> runCatching { ("http://127.0.0.1" + target).toHttpUrl() }.getOrNull() }
                    val valid = parts.firstOrNull() == "GET" && url?.encodedPath == "/callback" &&
                        MessageDigest.isEqual(state.toByteArray(), url.queryParameter("state").orEmpty().toByteArray())
                    val text = if (valid) acceptedMessage else rejectedMessage
                    runCatching {
                        it.getOutputStream().write(("HTTP/1.1 ${if (valid) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${text.toByteArray().size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n$text").toByteArray())
                    }
                    if (valid) {
                        if (url!!.queryParameter("error") != null) throw SpotifyImportException(SpotifyFailure.DENIED)
                        return@withContext url.queryParameter("code")?.takeIf(String::isNotBlank)
                            ?: throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
                    }
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                if (e is SpotifyImportException) throw e
                // Browsers may preconnect or abandon a socket before sending a request.
            } finally { activeSocket = null }
        }
        throw SpotifyImportException(SpotifyFailure.TIMEOUT)
    }
    override fun close() { runCatching { activeSocket?.close() }; runCatching { server.close() } }
}
