package com.metrolist.innertube.utils

import com.metrolist.innertube.models.YouTubeLocale
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class MusicTransportProbeTest {
    @Test fun transientFailureProducesOneAnonymousMusicRequest() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("music.youtube.com", request.url.host)
            assertEquals("/youtubei/v1/search", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("67", request.headers["X-YouTube-Client-Name"])
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            assertNull(request.headers["X-Goog-Visitor-Id"])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("WEB_REMIX", body["context"]!!.jsonObject["client"]!!.jsonObject["clientName"]!!.jsonPrimitive.content)
            respond("{}", HttpStatusCode.ServiceUnavailable)
        }
        HttpClient(engine) { expectSuccess = false }.use { client ->
            requestMusicProbe(client, YouTubeLocale("US", "en"), null).execute { response ->
                assertEquals(503, response.status.value)
            }
        }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test fun classifiesChallengeWithoutRetainingAnyPageText() {
        val page = "<html>Our systems have detected unusual traffic. private@example.com token=secret</html>"
        assertEquals("AUTOMATION_CHALLENGE", classifyMusicRejection("text/html", page))
    }

    @Test fun distinguishesConsentAndGenericDenial() {
        assertEquals("CONSENT", classifyMusicRejection("text/html", "<a href='https://consent.google.com/?private'>Continue</a>"))
        assertEquals("GOOGLE_PERMISSION", classifyMusicRejection("text/html", "Your client does not have permission to get URL /youtubei/v1/search"))
        assertEquals("ACCESS_DENIED", classifyMusicRejection("text/html", "<h1>Access denied</h1> private search term"))
    }

    @Test fun recordsOnlyRecognizedJsonErrorCategories() {
        assertEquals("PERMISSION_DENIED", classifyMusicRejection("application/json", """{"error":{"status":"PERMISSION_DENIED","message":"private query"}}"""))
        assertEquals("UNKNOWN", classifyMusicRejection("application/json", """{"error":{"status":"private-token","message":"private query"}}"""))
    }

    @Test fun malformedAndUnrecognizedPagesRemainUnknown() {
        assertEquals("UNKNOWN", classifyMusicRejection("application/json", "{broken"))
        assertEquals("UNKNOWN", classifyMusicRejection("text/html", "<h1>private search text</h1>"))
    }
}
