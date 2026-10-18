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
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime

enum class SpotifyFailure { FILE_TOO_LARGE, TOO_MANY_ROWS, INVALID_CSV, MISSING_COLUMNS, EMPTY_FILE, FILE_READ, NETWORK, RESPONSE, DENIED, AUTHORIZATION, FORBIDDEN, RATE_LIMIT, TIMEOUT, PORT_BUSY, EMPTY_LIBRARY }
class SpotifyImportException(val reason: SpotifyFailure) : IOException(reason.name)

data class SpotifyImportProgress(
    val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val already: Int = 0,
    val unmatched: List<String> = emptyList(),
)

internal class SpotifyImportRepository(private val database: MusicDatabase) {
    suspend fun import(tracks: List<SpotifyTrack>, onProgress: (SpotifyImportProgress) -> Unit) = withContext(Dispatchers.IO) {
        var progress = SpotifyImportProgress(total = tracks.size)
        onProgress(progress)
        for (track in tracks) {
            currentCoroutineContext().ensureActive()
            val matches = search("${track.title} ${track.artists.first()}")
                .filter { SpotifyImportRules.matches(track, it.title, it.artists.map { a -> a.name }, it.duration) }
                .distinctBy { it.id }
            // Multiple equally plausible recordings need human judgment; leave these unmatched.
            if (matches.size != 1) {
                progress = progress.copy(unmatched = progress.unmatched + listOf(track.title + " — ${track.artists.joinToString()}"))
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
