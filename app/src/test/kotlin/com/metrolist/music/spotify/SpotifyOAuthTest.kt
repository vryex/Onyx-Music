package com.metrolist.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket

class SpotifyOAuthTest {
    private fun send(query: String): String = Socket("127.0.0.1", 8888).use { socket ->
        socket.soTimeout = 4000
        socket.getOutputStream().write("GET /callback?$query HTTP/1.1\r\nHost: 127.0.0.1:8888\r\n\r\n".toByteArray())
        socket.getInputStream().bufferedReader().readText()
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
