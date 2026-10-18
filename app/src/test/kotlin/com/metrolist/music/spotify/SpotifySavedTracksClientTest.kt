package com.metrolist.music.spotify

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SpotifySavedTracksClientTest {
    private fun page(name: String, next: String = "null") = """{"items":[{"track":{"name":"$name","artists":[{"name":"Artist"}],"duration_ms":200000,"is_local":false}}],"next":$next,"total":2}"""

    @Test fun pkceExchangePostsOnlyPublicClientAndVerifier() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = SpotifySavedTracksClient(transport = { requests.add(it); SpotifyHttpResponse(200, """{"access_token":"temporary-token"}""") })
        assertEquals("temporary-token", client.exchange("authorization-code", "pkce-verifier"))
        val request = requests.single()
        assertEquals("https://accounts.spotify.com/api/token", request.url.toString())
        assertEquals("POST", request.method)
        val body = request.body as FormBody
        val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
        assertEquals(setOf("client_id", "grant_type", "code", "redirect_uri", "code_verifier"), fields.keys)
        assertEquals(SpotifyConnectionConfig.CLIENT_ID, fields["client_id"])
        assertEquals("http://127.0.0.1:8888/callback", fields["redirect_uri"])
        assertEquals("pkce-verifier", fields["code_verifier"])
        assertEquals("authorization-code", fields["code"])
    }

    @Test fun everyPageIsReadBeforePreviewAndBearerStaysOnSpotify() = runBlocking {
        val requests = mutableListOf<Request>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val responses = ArrayDeque(listOf(
            SpotifyHttpResponse(200, page("First", "\"https://api.spotify.com/v1/me/tracks?limit=50&offset=50\"")),
            SpotifyHttpResponse(200, page("Second")),
        ))
        val client = SpotifySavedTracksClient(transport = { requests.add(it); responses.removeFirst() })
        val saved = client.load("memory-token") { read, total -> progress.add(read to total) }
        assertEquals(listOf("First", "Second"), saved.tracks.map { it.title })
        assertEquals(2, requests.size)
        assertEquals("50", requests[1].url.queryParameter("offset"))
        assertTrue(requests.all { it.header("Authorization") == "Bearer memory-token" && it.method == "GET" })
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test fun foreignPaginationHostIsRejectedBeforeSendingToken() = runBlocking {
        var requests = 0
        val client = SpotifySavedTracksClient(transport = { requests++; SpotifyHttpResponse(200, page("First", "\"https://other.example/v1/me/tracks?offset=50\"")) })
        val error = runCatching { client.load("token") { _, _ -> } }.exceptionOrNull()
        assertEquals(SpotifyFailure.RESPONSE, (error as SpotifyImportException).reason)
        assertEquals(1, requests)
    }

    @Test fun forbiddenAndExpiredAuthorizationAreDistinctAndNotRetried() = runBlocking {
        for ((status, failure) in listOf(403 to SpotifyFailure.FORBIDDEN, 401 to SpotifyFailure.AUTHORIZATION)) {
            var requests = 0
            val client = SpotifySavedTracksClient(transport = { requests++; SpotifyHttpResponse(status, "{}") })
            val error = runCatching { client.load("token") { _, _ -> } }.exceptionOrNull()
            assertEquals(failure, (error as SpotifyImportException).reason)
            assertEquals(1, requests)
        }
    }

    @Test fun retryAfterIsHonoredAndRetriesAreBounded() = runBlocking {
        var requests = 0
        val waits = mutableListOf<Long>()
        val client = SpotifySavedTracksClient(transport = { requests++; SpotifyHttpResponse(429, "{}", "3") }, wait = { waits.add(it) })
        val error = runCatching { client.load("token") { _, _ -> } }.exceptionOrNull()
        assertEquals(SpotifyFailure.RATE_LIMIT, (error as SpotifyImportException).reason)
        assertEquals(4, requests)
        assertEquals(listOf(3000L, 3000L, 3000L), waits)
    }

    @Test fun networkFailureRetriesThenStops() = runBlocking {
        var requests = 0
        val client = SpotifySavedTracksClient(transport = { requests++; throw IOException("offline") }, wait = {})
        val error = runCatching { client.load("token") { _, _ -> } }.exceptionOrNull()
        assertEquals(SpotifyFailure.NETWORK, (error as SpotifyImportException).reason)
        assertEquals(4, requests)
    }

    @Test fun pendingMetadataLoadCanBeCancelled() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val client = SpotifySavedTracksClient(transport = { entered.complete(Unit); awaitCancellation() })
        val job = async { client.load("token") { _, _ -> } }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun emptySpotifyLibraryHasItsOwnError() = runBlocking {
        val client = SpotifySavedTracksClient(transport = { SpotifyHttpResponse(200, """{"items":[],"next":null,"total":0}""") })
        val error = runCatching { client.load("token") { _, _ -> } }.exceptionOrNull()
        assertEquals(SpotifyFailure.EMPTY_LIBRARY, (error as SpotifyImportException).reason)
    }
}
