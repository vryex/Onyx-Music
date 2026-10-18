package com.metrolist.music.spotify

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class SpotifyFailure { DENIED, AUTHORIZATION, FORBIDDEN, RATE_LIMIT, NETWORK, TIMEOUT, RESPONSE }
class SpotifyImportException(val reason: SpotifyFailure) : IOException(reason.name)

data class SpotifyImportProgress(
    val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val already: Int = 0,
    val unmatched: List<String> = emptyList(),
)

internal class SpotifyImportRepository(private val database: MusicDatabase) {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() } else response.close()
            }
        })
    }

    private suspend fun json(request: Request): JSONObject {
        repeat(4) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                execute(request).use { response ->
                    when {
                        response.isSuccessful -> return JSONObject(response.body.string())
                        (response.code == 400 && request.url.host == "accounts.spotify.com") || response.code == 401 -> throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
                        response.code == 403 -> throw SpotifyImportException(SpotifyFailure.FORBIDDEN)
                        response.code == 429 -> {
                            val waitSeconds = response.header("Retry-After")?.toLongOrNull() ?: 2L
                            if (attempt == 3 || waitSeconds > 60L) throw SpotifyImportException(SpotifyFailure.RATE_LIMIT)
                            delay(waitSeconds.coerceIn(1L, 60L) * 1000L)
                        }
                        response.code in 500..599 && attempt < 3 -> delay((attempt + 1) * 1500L)
                        else -> throw SpotifyImportException(SpotifyFailure.RESPONSE)
                    }
                }
            } catch (e: SpotifyImportException) { throw e
            } catch (e: IOException) {
                if (attempt == 3) throw SpotifyImportException(SpotifyFailure.NETWORK)
                delay((attempt + 1) * 1000L)
            }
        }
        throw SpotifyImportException(SpotifyFailure.NETWORK)
    }

    suspend fun exchange(clientId: String, oauth: SpotifyOAuth, code: String): SpotifySession = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("client_id", clientId).add("grant_type", "authorization_code")
            .add("code", code).add("redirect_uri", oauth.redirect).add("code_verifier", oauth.verifier).build()
        val response = json(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build())
        val accessToken = response.optString("access_token").takeIf { it.isNotBlank() && it != "null" }
            ?: throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
        SpotifySession(clientId, accessToken, response.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" })
    }

    private suspend fun savedTracksPage(session: SpotifySession, url: okhttp3.HttpUrl): JSONObject {
        fun request() = Request.Builder().url(url).header("Authorization", "Bearer ${session.accessToken}").build()
        return withSpotifyRefresh(request = { json(request()) }, refresh = {
            currentCoroutineContext().ensureActive()
            val refreshToken = session.refreshToken ?: throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
            val body = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refreshToken)
                .add("client_id", session.clientId).build()
            val refreshed = json(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build())
            session.updateTokens(refreshed.optString("access_token"), refreshed.optString("refresh_token").takeIf(String::isNotBlank))
        })
    }

    suspend fun import(session: SpotifySession, onProgress: (SpotifyImportProgress) -> Unit) = withContext(Dispatchers.IO) {
        var progress = SpotifyImportProgress()
        var next: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        val pages = SpotifyPageGuard()
        while (next != null) {
            currentCoroutineContext().ensureActive()
            if (!pages.accept(next)) throw SpotifyImportException(SpotifyFailure.RESPONSE)
            val url = next.toHttpUrl()
            val page = savedTracksPage(session, url)
            progress = progress.copy(total = page.optInt("total", progress.total).coerceAtLeast(progress.processed))
            onProgress(progress)
            val items = page.getJSONArray("items")
            for (index in 0 until items.length()) {
                currentCoroutineContext().ensureActive()
                val raw = items.optJSONObject(index)?.optJSONObject("track")
                val title = raw?.optString("name").orEmpty()
                val artistArray = raw?.optJSONArray("artists")
                val artists = (0 until (artistArray?.length() ?: 0)).mapNotNull { artistArray?.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
                val track = SpotifyTrack(title, artists, (raw?.optInt("duration_ms") ?: 0) / 1000)
                val matches = if (title.isBlank() || artists.isEmpty() || raw?.optBoolean("is_local") == true) emptyList() else {
                    search("$title ${artists.first()}").filter { SpotifyImportRules.matches(track, it.title, it.artists.map { a -> a.name }, it.duration) }
                        .distinctBy { it.id }
                }
                // Multiple equally plausible recordings need human judgment; leave these unmatched.
                if (matches.size != 1) {
                    progress = progress.copy(unmatched = progress.unmatched + listOf(title.ifBlank { "—" } + if (artists.isEmpty()) "" else " — ${artists.joinToString() }"))
                } else {
                    var already = false
                    database.withTransaction {
                        val candidate = matches.single()
                        val existing = songEntity(candidate.id)
                        already = existing?.liked == true && existing.inLibrary != null
                        val now = LocalDateTime.now()
                        if (existing == null) insert(candidate.toMediaMetadata()) { it.withSpotifyImportMembership(now) }
                        else if (!already) update(existing.withSpotifyImportMembership(now))
                    }
                    progress = if (already) progress.copy(already = progress.already + 1) else progress.copy(imported = progress.imported + 1)
                }
                progress = progress.copy(processed = progress.processed + 1)
                onProgress(progress)
                delay(200)
            }
            next = page.optString("next").takeUnless { it.isBlank() || it == "null" }
        }
    }

    private suspend fun search(query: String): List<SongItem> {
        repeat(3) { attempt ->
            currentCoroutineContext().ensureActive()
            try { return YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items.filterIsInstance<SongItem>() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (attempt == 2) throw SpotifyImportException(SpotifyFailure.NETWORK)
                delay((attempt + 1) * 1500L)
            }
        }
        return emptyList()
    }
}
