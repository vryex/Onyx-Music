package com.metrolist.music.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

internal class SpotifyOAuth(
    private val acceptedMessage: String,
    private val rejectedMessage: String,
    private val timeoutMillis: Long = 180_000,
    private val completionGraceMillis: Long = 15_000,
    private val callbackLifetimeMillis: Long = 60_000,
) : AutoCloseable {
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val code = CompletableDeferred<String>()
    @Volatile private var capturedAt: Long? = null
    @Volatile private var responseDeadline = Long.MAX_VALUE
    private val responder = scope.launch(start = CoroutineStart.LAZY) {
        responseDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val expiry = scope.launch {
            while (isActive) {
                delay(250)
                if (System.nanoTime() >= responseDeadline) {
                    code.completeExceptionally(SpotifyImportException(SpotifyFailure.TIMEOUT))
                    close()
                }
            }
        }
        try {
            serve()
        } catch (error: Exception) {
            code.completeExceptionally(error)
        } finally {
            expiry.cancel()
            close()
        }
    }
    val redirect = SpotifyConnectionConfig.REDIRECT
    val verifier = SpotifyPkce.randomSecret()
    private val state = SpotifyPkce.randomSecret()
    fun authorizationUrl(): String = "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder()
        .addQueryParameter("client_id", SpotifyConnectionConfig.CLIENT_ID).addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", redirect).addQueryParameter("scope", "user-library-read")
        .addQueryParameter("state", state).addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("code_challenge", SpotifyPkce.challenge(verifier)).build().toString()

    suspend fun awaitCode(): String {
        responder.start()
        return try {
            code.await()
        } catch (cancelled: CancellationException) {
            close()
            throw cancelled
        }
    }

    // Finishing metadata loading must not race the browser rendering or retrying its callback.
    fun finish() {
        val captured = capturedAt
        if (captured == null) close()
        else responseDeadline = minOf(responseDeadline,
            maxOf(System.nanoTime(), captured + TimeUnit.MILLISECONDS.toNanos(completionGraceMillis)))
    }

    private suspend fun serve() {
        while (System.nanoTime() < responseDeadline) {
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
            if (server.isClosed) socket.close()
            try {
                socket.use {
                    it.soTimeout = 2000
                    val input = it.getInputStream()
                    val bytes = ArrayList<Byte>()
                    var lineComplete = false
                    while (bytes.size < 8192) {
                        val b = input.read()
                        if (b == -1) throw IOException("Incomplete callback")
                        if (b == 10) { lineComplete = true; break }
                        bytes.add(b.toByte())
                    }
                    if (!lineComplete) throw IOException("Oversized callback")
                    var emptyHeader = true
                    var headersComplete = false
                    var headerBytes = 0
                    while (!headersComplete && headerBytes++ < 8192) {
                        val byte = input.read()
                        if (byte == -1) throw IOException("Incomplete callback")
                        if (byte == 10) {
                            if (emptyHeader) headersComplete = true
                            emptyHeader = true
                        } else if (byte != 13) emptyHeader = false
                    }
                    if (!headersComplete) throw IOException("Oversized callback")
                    val parts = bytes.toByteArray().toString(Charsets.US_ASCII).trim().split(' ')
                    val url = parts.getOrNull(1)?.takeIf { target -> target.startsWith('/') }?.let { target ->
                        runCatching { ("http://127.0.0.1" + target).toHttpUrl() }.getOrNull()
                    }
                    if (parts.firstOrNull() == "GET" && url?.encodedPath == "/favicon.ico") {
                        respond(it, "204 No Content", "")
                        return@use
                    }
                    val valid = parts.firstOrNull() == "GET" && url?.encodedPath == "/callback" &&
                        MessageDigest.isEqual(state.toByteArray(), url.queryParameter("state").orEmpty().toByteArray())
                    val callbackCode = url?.queryParameter("code")?.takeIf(String::isNotBlank)
                    val accepted = valid && (capturedAt != null || (callbackCode != null && url?.queryParameter("error") == null))
                    respond(it, if (accepted) "200 OK" else "400 Bad Request", if (accepted) acceptedMessage else rejectedMessage)
                    if (valid && capturedAt == null) {
                        if (url!!.queryParameter("error") != null) {
                            code.completeExceptionally(SpotifyImportException(SpotifyFailure.DENIED))
                            return
                        }
                        if (callbackCode == null) {
                            code.completeExceptionally(SpotifyImportException(SpotifyFailure.AUTHORIZATION))
                            return
                        }
                        val captured = System.nanoTime()
                        responseDeadline = captured + TimeUnit.MILLISECONDS.toNanos(callbackLifetimeMillis)
                        capturedAt = captured
                        code.complete(callbackCode)
                    }
                }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                // An abandoned request (including a failed response write) may be retried by the browser.
            } finally { activeSocket = null }
        }
        if (!code.isCompleted) code.completeExceptionally(SpotifyImportException(SpotifyFailure.TIMEOUT))
    }

    private fun respond(socket: Socket, status: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
        socket.shutdownOutput()
    }

    override fun close() {
        runCatching { activeSocket?.close() }
        runCatching { server.close() }
        code.cancel()
        scope.cancel()
    }
}
