package com.metrolist.music.spotify

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.toMediaMetadata
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
    val unmatched: List<String> = emptyList(), val unmatchedTracks: List<SpotifyTrack> = emptyList(),
)

internal class SpotifyImportRepository(private val database: MusicDatabase, private val resolver: SpotifyTrackResolver = SpotifyTrackResolver()) {
    suspend fun import(tracks: List<SpotifyTrack>, onProgress: (SpotifyImportProgress) -> Unit) = withContext(Dispatchers.IO) {
        var progress = SpotifyImportProgress(total = tracks.size)
        onProgress(progress)
        for (track in tracks) {
            currentCoroutineContext().ensureActive()
            val candidate = resolver.resolve(track)
            if (candidate == null) {
                progress = progress.copy(unmatched = progress.unmatched + listOf(track.title + " — ${track.artists.joinToString()}"),
                    unmatchedTracks = progress.unmatchedTracks + track)
            } else {
                var already = false
                database.withTransaction {
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

}
