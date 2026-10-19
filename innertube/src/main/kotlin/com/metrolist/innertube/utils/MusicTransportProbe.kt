package com.metrolist.innertube.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.preparePost
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Inet6Address
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun classifyMusicRejection(contentType: String, text: String): String {
    val sample = text.take(16_384)
    if (contentType.substringBefore(';').trim().equals("application/json", ignoreCase = true)) {
        val status = runCatching {
            Json.parseToJsonElement(sample).jsonObject["error"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return status?.takeIf { it in setOf("PERMISSION_DENIED", "UNAUTHENTICATED", "RESOURCE_EXHAUSTED", "INVALID_ARGUMENT", "FAILED_PRECONDITION") } ?: "UNKNOWN"
    }
    val lower = sample.lowercase()
    return when {
        listOf("unusual traffic", "automated queries", "automated requests", "recaptcha", "captcha").any { it in lower } -> "AUTOMATION_CHALLENGE"
        "consent.google" in lower || "consent.youtube" in lower -> "CONSENT"
        "your client does not have permission" in lower -> "GOOGLE_PERMISSION"
        "access denied" in lower || "request blocked" in lower || "403 forbidden" in lower -> "ACCESS_DENIED"
        else -> "UNKNOWN"
    }
}

/** A single anonymous Music request, deliberately bypassing facade retry policy. */
internal suspend fun requestMusicProbe(client: HttpClient, locale: YouTubeLocale, visitor: String?) =
    client.preparePost("https://music.youtube.com/youtubei/v1/search") {
        val identity = YouTubeClient.WEB_REMIX
        val language = locale.hl.replace('_', '-')
        val regional = if ('-' in language || locale.gl.isEmpty()) language else "$language-${locale.gl}"
        val fallback = language.substringBefore('-')
        contentType(ContentType.Application.Json)
        header("X-Goog-Api-Format-Version", "1")
        header("X-YouTube-Client-Name", identity.clientId)
        header("X-YouTube-Client-Version", identity.clientVersion)
        header("Origin", "https://music.youtube.com")
        header("X-Origin", "https://music.youtube.com")
        header("Referer", "https://music.youtube.com/")
        header("User-Agent", identity.userAgent)
        header("Accept-Language", if (regional == fallback) regional else "$regional,$fallback;q=0.9")
        visitor?.let { header("X-Goog-Visitor-Id", it) }
        parameter("prettyPrint", false)
        val json = Json { encodeDefaults = true; explicitNulls = false }
        setBody(buildJsonObject {
            put("context", json.encodeToJsonElement(identity.toContext(locale, visitor, null)))
            put("query", "The Weeknd")
            put("params", YouTube.SearchFilter.FILTER_SONG.value)
        }.toString())
    }

@Serializable
data class MusicProbeResult(
    val transport: String,
    val visitorMode: String,
    val visitorPresent: Boolean,
    val pass: Int,
    val protocol: String,
    val addressFamily: String,
    val status: Int?,
    val elapsedMs: Long,
    val marker: String,
    val failure: String?,
)

object MusicTransportProbe {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun start() { scope.launch { run() } }

    private val lock = Mutex()
    private val mutableResults = MutableStateFlow<List<MusicProbeResult>>(emptyList())
    private val mutableRunning = MutableStateFlow(false)
    val results = mutableResults.asStateFlow()
    val running = mutableRunning.asStateFlow()

    private data class Wire(val protocol: String = "UNKNOWN", val family: String = "UNKNOWN", val status: Int? = null, val marker: String = "NONE")

    suspend fun run() {
        if (!lock.tryLock()) return
        mutableRunning.value = true
        mutableResults.value = emptyList()
        try {
            withContext(Dispatchers.IO) {
                val visitor = YouTube.visitorData
                val locale = YouTube.locale
                val cases = listOf("AUTO" to "CURRENT", "HTTP1" to "CURRENT", "AUTO" to "NONE", "HTTP1" to "NONE")
                repeat(2) { pass ->
                    for ((transport, visitorMode) in if (pass == 0) cases else cases.reversed()) {
                        currentCoroutineContext().ensureActive()
                        val wire = AtomicReference(Wire())
                        val began = System.nanoTime()
                        var failure: String? = null
                        try {
                            withTimeout(15_000) {
                                newClient(transport, wire).use { client ->
                                    requestMusicProbe(client, locale, visitor.takeIf { visitorMode == "CURRENT" }).execute { response ->
                                        if (response.status.value !in 200..299) throw IOException("Probe HTTP rejection")
                                        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                                        check(body["contents"] != null) { "Unexpected response structure" }
                                    }
                                }
                            }
                        } catch (error: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            failure = "TIMEOUT"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            val causes = generateSequence<Throwable>(error) { it.cause?.takeUnless { cause -> cause === it } }.take(12).toList()
                            failure = when {
                                wire.get().status?.let { it !in 200..299 } == true -> "HTTP"
                                causes.any { it is UnknownHostException } -> "DNS"
                                causes.any { it is SSLException } -> "TLS"
                                causes.any { it is SocketTimeoutException } -> "TIMEOUT"
                                causes.any { it is IOException } -> "IO"
                                else -> "RESPONSE"
                            }
                        }
                        val observed = wire.get()
                        mutableResults.value = mutableResults.value + MusicProbeResult(
                            transport, visitorMode, visitorMode == "CURRENT" && !visitor.isNullOrBlank(), pass + 1,
                            observed.protocol, observed.family, observed.status, (System.nanoTime() - began) / 1_000_000,
                            observed.marker, failure,
                        )
                        delay(400)
                    }
                }
            }
        } finally {
            mutableRunning.value = false
            lock.unlock()
        }
    }

    private fun newClient(transport: String, wire: AtomicReference<Wire>) = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }) }
        install(ContentEncoding) { gzip(0.9F); deflate(0.8F) }
        install(HttpTimeout) { requestTimeoutMillis = 12_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 8_000 }
        defaultRequest {
            url("https://music.youtube.com/youtubei/v1/")
            header("Accept", "application/json")
            header("Cache-Control", "no-cache")
        }
        engine {
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
                protocols(if (transport == "HTTP1") listOf(okhttp3.Protocol.HTTP_1_1) else listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                connectTimeout(8, TimeUnit.SECONDS)
                readTimeout(8, TimeUnit.SECONDS)
                writeTimeout(8, TimeUnit.SECONDS)
                addNetworkInterceptor { chain ->
                    val request = chain.request()
                    if (request.url.host != "music.youtube.com" || !request.url.encodedPath.endsWith("/search") ||
                        request.header("Authorization") != null || request.header("Cookie") != null) {
                        throw IOException("Probe accepts only anonymous Music search")
                    }
                    val response = chain.proceed(request)
                    val family = chain.connection()?.route()?.socketAddress?.address?.let { if (it is Inet6Address) "IPV6" else "IPV4" } ?: "UNKNOWN"
                    val observed = Wire(response.protocol.toString(), family, response.code)
                    wire.set(observed)
                    if (!response.isSuccessful) {
                        // Only this explicit standalone probe reads rejection bodies; raw text is never retained.
                        val marker = runCatching {
                            val bytes = response.peekBody(16_384).bytes()
                            val stream = ByteArrayInputStream(bytes)
                            val decoded = when (response.header("Content-Encoding")?.lowercase()) {
                                "gzip" -> GZIPInputStream(stream)
                                "deflate" -> InflaterInputStream(stream)
                                else -> stream
                            }.use { input ->
                                val output = ByteArray(16_384)
                                var used = 0
                                while (used < output.size) {
                                    val count = input.read(output, used, output.size - used)
                                    if (count < 0) break
                                    if (count == 0) break
                                    used += count
                                }
                                output.copyOf(used).toString(Charsets.UTF_8)
                            }
                            classifyMusicRejection(response.header("Content-Type").orEmpty(), decoded)
                        }.getOrDefault("UNKNOWN")
                        wire.set(observed.copy(marker = marker))
                    }
                    response
                }
            }
        }
    }
}
