package com.metrolist.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal class SpotifyOAuth : AutoCloseable {
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 1000 }
    val redirect = "http://127.0.0.1:${server.localPort}/callback"
    val verifier = SpotifyImportRules.randomSecret()
    private val state = SpotifyImportRules.randomSecret()
    fun authorizationUrl(clientId: String): String = "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder()
        .addQueryParameter("client_id", clientId).addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", redirect).addQueryParameter("scope", "user-library-read")
        .addQueryParameter("state", state).addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("code_challenge", SpotifyImportRules.challenge(verifier)).build().toString()

    suspend fun awaitCode(): String = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
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
                    val line = bytes.toByteArray().toString(Charsets.US_ASCII).trim()
                    val parts = line.split(' ')
                    val url = parts.getOrNull(1)?.let { target -> runCatching { ("http://127.0.0.1" + target).toHttpUrl() }.getOrNull() }
                    val valid = parts.firstOrNull() == "GET" && url?.encodedPath == "/callback" &&
                        MessageDigest.isEqual(state.toByteArray(), url.queryParameter("state").orEmpty().toByteArray())
                    val text = if (valid) "You can return to Sonet now." else "Invalid authorization callback. Return to Sonet and try again."
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
            }
        }
        throw SpotifyImportException(SpotifyFailure.TIMEOUT)
    }
    override fun close() { runCatching { server.close() } }
}
