package com.metrolist.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.ConnectException

class SpotifyOAuthTest {
    private fun send(query: String): String = Socket("127.0.0.1", 8888).use { socket ->
        socket.soTimeout = 4000
        socket.getOutputStream().write("GET /callback?$query HTTP/1.1\r\nHost: 127.0.0.1:8888\r\n\r\n".toByteArray())
        socket.getInputStream().bufferedReader().readText()
    }

    @Test fun successfulCallbackContinuesServingRefreshAndFaviconWithoutReplayingCode() = runBlocking {
        SpotifyOAuth("Ya puedes volver a Sonet.", "Respuesta no válida.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            val first = withContext(Dispatchers.IO) { send("state=$state&code=accepted") }
            assertEquals("accepted", result.await())
            val repeated = withContext(Dispatchers.IO) { send("state=$state&code=other") }
            assertEquals(first, repeated)
            assertEquals("accepted", result.await())
            assertTrue(withContext(Dispatchers.IO) { send("state=wrong&code=other") }.startsWith("HTTP/1.1 400"))
            val favicon = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", 8888).use { socket ->
                    socket.soTimeout = 4000
                    socket.getOutputStream().write("GET /favicon.ico HTTP/1.1\r\nHost: 127.0.0.1:8888\r\n\r\n".toByteArray())
                    socket.getInputStream().bufferedReader().readText()
                }
            }
            assertTrue(favicon.startsWith("HTTP/1.1 204"))
            assertEquals("accepted", result.await())
        }
    }

    @Test fun successfulResponseIncludesWholeUtf8BodyAndDrainsSplitHeaders() = runBlocking {
        val message = "Ya puedes volver a Sonet. Éxito. " + "é".repeat(100_000)
        SpotifyOAuth(message, "Invalid.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            val response = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", 8888).use { socket ->
                    socket.soTimeout = 4000
                    val output = socket.getOutputStream()
                    output.write("GET /callback?state=$state&code=accepted HTTP/1.1\r\nHost: 127.0.0.1:8888\r\n".toByteArray())
                    output.flush()
                    delay(50)
                    assertFalse(result.isCompleted)
                    output.write("User-Agent: browser\r\nAccept: */*\r\n\r\n".toByteArray())
                    socket.getInputStream().readBytes()
                }
            }
            val text = response.toString(Charsets.UTF_8)
            assertTrue(text.contains("Content-Length: ${message.toByteArray(Charsets.UTF_8).size}\r\n"))
            assertEquals(message, text.substringAfter("\r\n\r\n"))
            assertEquals("accepted", result.await())
        }
    }

    @Test fun abandonedResponseDoesNotReleaseCodeAndBrowserCanRetry() = runBlocking {
        SpotifyOAuth("x".repeat(8_000_000), "Invalid.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            withContext(Dispatchers.IO) {
                Socket("127.0.0.1", 8888).use { socket ->
                    socket.soTimeout = 4000
                    socket.receiveBufferSize = 1024
                    socket.getOutputStream().write("GET /callback?state=$state&code=abandoned HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    assertEquals('H'.code, socket.getInputStream().read())
                    socket.setSoLinger(true, 0)
                }
            }
            delay(100)
            assertFalse(result.isCompleted)
            val response = withContext(Dispatchers.IO) { send("state=$state&code=retried") }
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertEquals("retried", result.await())
        }
    }

    @Test fun delayedBrowserHeadersStillReceiveTheWholeResponseBeforeCodeIsReleased() = runBlocking {
        SpotifyOAuth("Éxito. Vuelve a Sonet.", "Invalid.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            val response = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", 8888).use { socket ->
                    socket.soTimeout = 6000
                    socket.getOutputStream().write("GET /callback?state=$state&code=accepted HTTP/1.1\r\nHost: local\r\n".toByteArray())
                    delay(2200)
                    assertFalse(result.isCompleted)
                    socket.getOutputStream().write("User-Agent: browser\r\n\r\n".toByteArray())
                    socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                }
            }
            assertEquals("Éxito. Vuelve a Sonet.", response.substringAfter("\r\n\r\n"))
            assertEquals("accepted", result.await())
        }
    }

    @Test fun truncatedAndOversizedHeadersDoNotPoisonTheNextCallback() = runBlocking {
        SpotifyOAuth("Return.", "Invalid.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            withContext(Dispatchers.IO) {
                for (request in listOf("GET /callback?state=$state&code=incomplete HTTP/1.1\r\nHost: local", "x".repeat(9000))) {
                    Socket("127.0.0.1", 8888).use { socket ->
                        socket.soTimeout = 4000
                        socket.getOutputStream().write(request.toByteArray())
                        socket.shutdownOutput()
                        runCatching { socket.getInputStream().readBytes() }
                    }
                }
            }
            assertFalse(result.isCompleted)
            assertTrue(withContext(Dispatchers.IO) { send("state=$state&code=recovered") }.startsWith("HTTP/1.1 200"))
            assertEquals("recovered", result.await())
        }
    }

    @Test fun metadataCompletionKeepsGraceThenReleasesPort() = runBlocking {
        SpotifyOAuth("Return.", "Invalid.", completionGraceMillis = 500, callbackLifetimeMillis = 5000).use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            withContext(Dispatchers.IO) { send("state=$state&code=accepted") }
            assertEquals("accepted", result.await())
            oauth.finish()
            assertTrue(withContext(Dispatchers.IO) { send("state=$state&code=accepted") }.startsWith("HTTP/1.1 200"))
            awaitReleasedPort()
        }
    }

    @Test fun callbackResponderExpiresEvenIfMetadataNeverFinishes() = runBlocking {
        SpotifyOAuth("Return.", "Invalid.", callbackLifetimeMillis = 200).use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            withContext(Dispatchers.IO) { send("state=$state&code=accepted") }
            assertEquals("accepted", result.await())
            awaitReleasedPort()
        }
    }

    @Test fun cancellingAwaitCodeAloneReleasesAnActivePreconnection() = runBlocking {
        SpotifyOAuth("Return.", "Invalid.").use { oauth ->
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            Socket("127.0.0.1", 8888).use {
                delay(50)
                withTimeout(1000) { result.cancelAndJoin() }
            }
            SpotifyOAuth("Return.", "Invalid.").close()
        }
    }

    private suspend fun awaitReleasedPort() = withTimeout(3000) {
        while (true) {
            val refused = withContext(Dispatchers.IO) {
                try { Socket("127.0.0.1", 8888).close(); false } catch (_: ConnectException) { true }
            }
            if (refused) break
            delay(25)
        }
        SpotifyOAuth("Return.", "Invalid.").close()
    }

    @Test fun pkceMatchesRfc7636AndStateHasHighEntropy() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SpotifyPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val secret = SpotifyPkce.randomSecret()
        assertTrue(secret.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertNotEquals(secret, SpotifyPkce.randomSecret())
    }

    @Test fun wrongStateIsRejectedThenValidCallbackUsesLocalizedMessage() = runBlocking {
        SpotifyOAuth("Ya puedes volver a Sonet.", "Respuesta no válida.").use { oauth ->
            val authorize = oauth.authorizationUrl().toHttpUrl()
            val code = async(Dispatchers.IO) { oauth.awaitCode() }
            assertTrue(withContext(Dispatchers.IO) { send("state=wrong&code=attacker") }.startsWith("HTTP/1.1 400"))
            assertFalse(code.isCompleted)
            val response = withContext(Dispatchers.IO) { send("state=${authorize.queryParameter("state")}&code=accepted") }
            assertTrue(response.contains("Ya puedes volver a Sonet."))
            assertEquals("accepted", code.await())
            assertEquals("S256", authorize.queryParameter("code_challenge_method"))
            assertEquals("user-library-read", authorize.queryParameter("scope"))
            assertEquals(SpotifyConnectionConfig.REDIRECT, authorize.queryParameter("redirect_uri"))
        }
    }

    @Test fun denialIsReportedInsteadOfProducingACode() = runBlocking {
        SpotifyOAuth("Return to Sonet.", "Invalid.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { runCatching { oauth.awaitCode() } }
            withContext(Dispatchers.IO) { send("state=$state&error=access_denied") }
            assertEquals(SpotifyFailure.DENIED, (result.await().exceptionOrNull() as SpotifyImportException).reason)
        }
    }

    @Test fun stalledPreconnectionDoesNotAbortAuthorization() = runBlocking {
        SpotifyOAuth("Return.", "Invalid.").use { oauth ->
            val state = oauth.authorizationUrl().toHttpUrl().queryParameter("state")
            val result = async(Dispatchers.IO) { oauth.awaitCode() }
            Socket("127.0.0.1", 8888).use { delay(2400); assertFalse(result.isCompleted) }
            withContext(Dispatchers.IO) { send("state=$state&code=accepted") }
            assertEquals("accepted", result.await())
        }
    }

    @Test fun authorizationTimeoutIsReportedAndListenerCanBeReleased() = runBlocking {
        SpotifyOAuth("Return.", "Invalid.", timeoutMillis = 10).use { oauth ->
            val error = runCatching { oauth.awaitCode() }.exceptionOrNull()
            assertEquals(SpotifyFailure.TIMEOUT, (error as SpotifyImportException).reason)
        }
        SpotifyOAuth("Return.", "Invalid.").close()
    }

    @Test fun cancellationClosesPortAndPortConflictHasFriendlyError() = runBlocking {
        val oauth = SpotifyOAuth("Return.", "Invalid.")
        val conflict = runCatching { SpotifyOAuth("Return.", "Invalid.") }.exceptionOrNull()
        assertEquals(SpotifyFailure.PORT_BUSY, (conflict as SpotifyImportException).reason)
        val result = async(Dispatchers.IO) { oauth.awaitCode() }
        result.cancel()
        oauth.close()
        result.cancelAndJoin()
        assertTrue(result.isCancelled)
        SpotifyOAuth("Return.", "Invalid.").close()
    }
}
