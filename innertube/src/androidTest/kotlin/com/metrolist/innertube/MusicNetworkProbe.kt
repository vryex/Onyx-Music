package com.metrolist.innertube

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Log
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Anonymous, opt-in emulator diagnostic; never runs in the installed app. */
class MusicNetworkProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    private fun report(data: JSONObject) {
        val line = data.toString()
        Log.i("SonetNetworkProbe", line)
        sendStatus(0, Bundle().apply { putString("stream", line + "\n") })
    }

    private fun safeMessage(message: String?) = message.orEmpty()
        .replace(Regex("https?://\\S+"), "[url]").take(350)

    override fun onStart() {
        var completed = 0
        try {
            runBlocking {
                HttpClient(OkHttp) {
                    expectSuccess = false
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true })
                    }
                    install(ContentEncoding) { gzip(0.9F); deflate(0.8F) }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 20_000
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 20_000
                    }
                    defaultRequest {
                        url("https://music.youtube.com/youtubei/v1/")
                        header("Accept", "application/json")
                        header("Cache-Control", "no-cache")
                    }
                    engine {
                        config {
                            protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                            connectTimeout(10, TimeUnit.SECONDS)
                            readTimeout(20, TimeUnit.SECONDS)
                            addInterceptor { chain ->
                                val request = chain.request()
                                check(request.url.host == "music.youtube.com") { "Non-Music host rejected" }
                                check(request.header("Authorization") == null)
                                check(request.header("Cookie").orEmpty().split(';').all { it.isBlank() || it.trim().startsWith("PREF=") })
                                val started = System.nanoTime()
                                val response = chain.proceed(request)
                                val entry = JSONObject()
                                    .put("kind", "http")
                                    .put("path", request.url.encodedPath)
                                    .put("host", request.url.host)
                                    .put("status", response.code)
                                    .put("elapsedMs", (System.nanoTime() - started) / 1_000_000)
                                    .put("protocol", response.protocol.toString())
                                    .put("clientName", request.header("X-Youtube-Client-Name"))
                                    .put("clientVersion", request.header("X-Youtube-Client-Version"))
                                    .put("contentType", response.header("Content-Type"))
                                if (!response.isSuccessful) {
                                    val text = response.peekBody(8192).string()
                                    val error = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
                                    entry.put("errorCode", error?.optInt("code"))
                                        .put("errorStatus", error?.optString("status"))
                                        .put("errorMessage", safeMessage(error?.optString("message")))
                                        .put("bodyFormat", if (error != null) "json-error" else if (text.trimStart().startsWith("<")) "html" else "other")
                                }
                                report(entry)
                                response
                            }
                        }
                    }
                }.use { client ->
                    val transport = InnerTube(client)
                    for (country in listOf("MX", "US")) {
                        transport.locale = YouTubeLocale(gl = country, hl = if (country == "MX") "es" else "en")
                        val cases = listOf(
                            "summary" to null,
                            "songs" to YouTube.SearchFilter.FILTER_SONG.value,
                            "artists" to YouTube.SearchFilter.FILTER_ARTIST.value,
                        )
                        for ((name, filter) in cases) {
                            val began = System.nanoTime()
                            val result = runCatching {
                                withTimeout(25_000) {
                                    val response = transport.search(YouTubeClient.WEB_REMIX, "The Weeknd", filter, setLogin = name != "songs")
                                    val body = JSONObject(response.bodyAsText())
                                    check(body.has("contents") || body.has("continuationContents")) { "No search contents" }
                                }
                            }
                            report(JSONObject().put("kind", "case").put("case", name).put("country", country)
                                .put("ok", result.isSuccess).put("elapsedMs", (System.nanoTime() - began) / 1_000_000)
                                .put("exception", result.exceptionOrNull()?.javaClass?.simpleName)
                                .put("message", safeMessage(result.exceptionOrNull()?.message)))
                            completed++
                        }
                        val result = runCatching {
                            withTimeout(25_000) {
                                val response = transport.player(YouTubeClient.WEB_REMIX, "dQw4w9WgXcQ", null, null)
                                val body = JSONObject(response.bodyAsText())
                                val status = body.optJSONObject("playabilityStatus")
                                report(JSONObject().put("kind", "player").put("country", country)
                                    .put("status", status?.optString("status"))
                                    .put("reason", safeMessage(status?.optString("reason")))
                                    .put("hasStreamingData", body.has("streamingData")))
                            }
                        }
                        report(JSONObject().put("kind", "case").put("case", "player").put("country", country)
                            .put("ok", result.isSuccess).put("exception", result.exceptionOrNull()?.javaClass?.simpleName)
                            .put("message", safeMessage(result.exceptionOrNull()?.message)))
                        completed++
                    }
                }
                YouTube.locale = YouTubeLocale(gl = "MX", hl = "es")
                for (query in listOf("The Weeknd", "Mia Boyka", "Cult Member")) {
                    val began = System.nanoTime()
                    val result = runCatching { withTimeout(25_000) { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow() } }
                    report(JSONObject().put("kind", "app-parser").put("query", query).put("ok", result.isSuccess)
                        .put("items", result.getOrNull()?.items?.size)
                        .put("elapsedMs", (System.nanoTime() - began) / 1_000_000)
                        .put("exception", result.exceptionOrNull()?.javaClass?.simpleName)
                        .put("message", safeMessage(result.exceptionOrNull()?.message)))
                    completed++
                }
            }
            check(completed == 11) { "Incomplete diagnostic matrix: $completed" }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PROBE_COMPLETE cases=$completed\n") })
        } catch (error: Throwable) {
            report(JSONObject().put("kind", "probe-failure").put("exception", error.javaClass.simpleName)
                .put("message", safeMessage(error.message)))
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "PROBE_INCOMPLETE cases=$completed\n") })
        }
    }
}
