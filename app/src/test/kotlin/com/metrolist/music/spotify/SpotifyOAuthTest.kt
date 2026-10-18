package com.metrolist.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket

class SpotifyOAuthTest {
    private fun send(url: String, query: String): String {
        val parsed = url.toHttpUrl()
        return Socket("127.0.0.1", parsed.port).use { socket ->
            socket.soTimeout = 4000
            socket.getOutputStream().write("GET /callback?$query HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
            socket.getInputStream().bufferedReader().readText()
        }
    }

    @Test fun wrongStateIsRejectedBeforeValidCallback() = runBlocking {
        SpotifyOAuth().use { oauth ->
            val authorize = oauth.authorizationUrl("0123456789abcdef0123456789abcdef").toHttpUrl()
            val code = async(Dispatchers.IO) { oauth.awaitCode() }
            val invalid = withContext(Dispatchers.IO) { send(oauth.redirect, "state=wrong&code=attacker") }
            assertTrue(invalid.startsWith("HTTP/1.1 400"))
            assertFalse(code.isCompleted)
            withContext(Dispatchers.IO) { send(oauth.redirect, "state=${authorize.queryParameter("state")}&code=accepted") }
            assertEquals("accepted", code.await())
            assertEquals("S256", authorize.queryParameter("code_challenge_method"))
            assertEquals("user-library-read", authorize.queryParameter("scope"))
        }
    }

    @Test fun stalledBrowserPreconnectionDoesNotAbortAuthorization() = runBlocking {
        SpotifyOAuth().use { oauth ->
            val authorize = oauth.authorizationUrl("0123456789abcdef0123456789abcdef").toHttpUrl()
            val code = async(Dispatchers.IO) { oauth.awaitCode() }
            Socket("127.0.0.1", oauth.redirect.toHttpUrl().port).use {
                delay(2400)
                assertFalse(code.isCompleted)
            }
            withContext(Dispatchers.IO) { send(oauth.redirect, "state=${authorize.queryParameter("state")}&code=accepted") }
            assertEquals("accepted", code.await())
        }
    }

    @Test fun cancellationClosesListenerAndDoesNotProduceACode() = runBlocking {
        val oauth = SpotifyOAuth()
        val port = oauth.redirect.toHttpUrl().port
        val code = async(Dispatchers.IO) { oauth.awaitCode() }
        code.cancel()
        oauth.close()
        code.join()
        assertTrue(code.isCancelled)
        assertTrue(runCatching { Socket("127.0.0.1", port).close() }.isFailure)
    }
}
