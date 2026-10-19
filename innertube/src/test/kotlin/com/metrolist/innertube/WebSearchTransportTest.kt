package com.metrolist.innertube

import com.metrolist.innertube.pages.VideoSearchPage
import com.metrolist.innertubex.InnerTubeHttpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WebSearchTransportTest {
    private val data = """{"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Song } \"quoted\""},"ownerText":{"simpleText":"Artist"},"lengthText":{"simpleText":"3:00"}}}]}},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"web-next"}}}}]}}}}}"""

    @Test fun rejectedApiFallsBackToAnonymousHtmlAndReusesHealthyRoute() = runBlocking {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) respond("denied", HttpStatusCode.Forbidden)
            else respond("<html><script>var ytInitialData = $data;</script></html>",
                headers = headersOf(HttpHeaders.ContentType, "text/html"))
        }
        HttpClient(engine) { install(ContentNegotiation) { json() } }.use { client ->
            val transport = InnerTube(client).apply { cookie = "SAPISID=private"; visitorData = "private-visitor" }
            repeat(2) {
                val result = VideoSearchPage.parse(transport.searchWeb("Artist & Song"))
                assertEquals("abcdefghijk", result.items.single().id)
                assertEquals("web-next", result.continuation)
            }
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Get, HttpMethod.Get), engine.requestHistory.map { it.method })
            engine.requestHistory.filter { it.method == HttpMethod.Get }.forEach { request ->
                assertEquals("www.youtube.com", request.url.host)
                assertEquals("/results", request.url.encodedPath)
                assertEquals("Artist & Song", request.url.parameters["search_query"])
                assertNull(request.headers[HttpHeaders.Cookie])
                assertNull(request.headers[HttpHeaders.Authorization])
                assertNull(request.headers["X-Goog-Visitor-Id"])
            }
        }
    }

    @Test fun htmlConsentOrMalformedPageIsAnErrorRatherThanNoMatches() = runBlocking {
        for (html in listOf("<html>consent required</html>", "<script>var ytInitialData = {broken</script>")) {
            val engine = MockEngine { request ->
                if (request.method == HttpMethod.Post) respond("denied", HttpStatusCode.Forbidden)
                else respond(html, headers = headersOf(HttpHeaders.ContentType, "text/html"))
            }
            HttpClient(engine) { install(ContentNegotiation) { json() } }.use { client ->
                val result = runCatching { InnerTube(client).searchWeb("song") }
                assertTrue(result.isFailure)
                assertFalse("Must reach and validate the independent HTML transport", result.exceptionOrNull() is InnerTubeHttpException)
            }
        }
    }

    @Test fun rateLimitDoesNotSendHtmlRequest() = runBlocking {
        val engine = MockEngine { respond("rate limited", HttpStatusCode.TooManyRequests,
            headersOf(HttpHeaders.RetryAfter, "120")) }
        HttpClient(engine) { install(ContentNegotiation) { json() } }.use { client ->
            assertTrue(runCatching { InnerTube(client).searchWeb("song") }.isFailure)
            assertEquals(1, engine.requestHistory.size)
        }
    }
}
