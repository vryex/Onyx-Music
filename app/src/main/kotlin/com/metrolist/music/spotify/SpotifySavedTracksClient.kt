package com.metrolist.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SpotifyHttpResponse(val code: Int, val body: String, val retryAfter: String? = null)
internal data class SpotifySavedTracks(val tracks: List<SpotifyTrack>, val skipped: Int)

internal class SpotifySavedTracksClient(
    private val transport: suspend (Request) -> SpotifyHttpResponse = SpotifyHttpTransport()::execute,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private suspend fun json(request: Request): JsonObject {
        repeat(4) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                val response = transport(request)
                when {
                    response.code in 200..299 -> return try { Json.parseToJsonElement(response.body).jsonObject }
                        catch (_: Exception) { throw SpotifyImportException(SpotifyFailure.RESPONSE) }
                    response.code == 401 || (response.code == 400 && request.url.host == "accounts.spotify.com") -> throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
                    response.code == 403 -> throw SpotifyImportException(SpotifyFailure.FORBIDDEN)
                    response.code == 429 -> {
                        val seconds = response.retryAfter?.toLongOrNull() ?: 2L
                        if (attempt == 3 || seconds > 60) throw SpotifyImportException(SpotifyFailure.RATE_LIMIT)
                        wait(seconds.coerceIn(1, 60) * 1000)
                    }
                    response.code in 500..599 && attempt < 3 -> wait((attempt + 1) * 1500L)
                    else -> throw SpotifyImportException(SpotifyFailure.RESPONSE)
                }
            } catch (error: SpotifyImportException) { throw error
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                if (attempt == 3) throw SpotifyImportException(SpotifyFailure.NETWORK)
                wait((attempt + 1) * 1000L)
            }
        }
        throw SpotifyImportException(SpotifyFailure.NETWORK)
    }

    suspend fun exchange(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("client_id", SpotifyConnectionConfig.CLIENT_ID).add("grant_type", "authorization_code")
            .add("code", code).add("redirect_uri", SpotifyConnectionConfig.REDIRECT).add("code_verifier", verifier).build()
        json(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build())["access_token"]
            ?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
    }

    suspend fun load(token: String, onProgress: (Int, Int) -> Unit): SpotifySavedTracks = withContext(Dispatchers.IO) {
        val tracks = LinkedHashSet<SpotifyTrack>()
        val visited = HashSet<String>()
        var processed = 0
        var skipped = 0
        var expectedTotal = 0
        var next: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        while (next != null) {
            currentCoroutineContext().ensureActive()
            val url = next.toHttpUrl()
            if (url.scheme != "https" || url.host != "api.spotify.com" || url.port != 443 || url.encodedPath != "/v1/me/tracks" ||
                url.username.isNotEmpty() || url.password.isNotEmpty() || !visited.add(next)) throw SpotifyImportException(SpotifyFailure.RESPONSE)
            val page = json(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
            val items = page["items"]?.jsonArray ?: throw SpotifyImportException(SpotifyFailure.RESPONSE)
            for (item in items) {
                currentCoroutineContext().ensureActive()
                val raw = item.jsonObject["track"] as? JsonObject
                val title = raw?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                val artists = raw?.get("artists")?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
                val duration = raw?.get("duration_ms")?.jsonPrimitive?.intOrNull?.div(1000)
                if (title.isBlank() || artists.isEmpty() || duration == null || duration <= 0 || raw?.get("is_local")?.jsonPrimitive?.booleanOrNull == true) skipped++
                else if (!tracks.add(SpotifyTrack(title, artists, duration))) skipped++
                processed++
            }
            val advertised = page["total"]?.jsonPrimitive?.intOrNull
            if (advertised != null && advertised < 0) throw SpotifyImportException(SpotifyFailure.RESPONSE)
            expectedTotal = maxOf(expectedTotal, advertised ?: processed)
            onProgress(processed, maxOf(processed, expectedTotal))
            next = page["next"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        }
        if (processed < expectedTotal) throw SpotifyImportException(SpotifyFailure.INCOMPLETE_LIBRARY)
        if (tracks.isEmpty()) throw SpotifyImportException(SpotifyFailure.EMPTY_LIBRARY)
        SpotifySavedTracks(tracks.toList(), skipped)
    }
}

private class SpotifyHttpTransport {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun execute(request: Request): SpotifyHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val value = SpotifyHttpResponse(it.code, it.body.string(), it.header("Retry-After"))
                        if (continuation.isActive) continuation.resume(value)
                    }
                } catch (error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
}
