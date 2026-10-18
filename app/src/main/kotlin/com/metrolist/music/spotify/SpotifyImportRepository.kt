package com.metrolist.music.spotify

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

enum class SpotifyFailure { FILE_TOO_LARGE, TOO_MANY_ROWS, INVALID_CSV, MISSING_COLUMNS, EMPTY_FILE, FILE_READ, NETWORK, RESPONSE, DENIED, AUTHORIZATION, FORBIDDEN, RATE_LIMIT, TIMEOUT, PORT_BUSY, EMPTY_LIBRARY, INCOMPLETE_LIBRARY }
class SpotifyImportException(val reason: SpotifyFailure) : IOException(reason.name)

data class SpotifyImportProgress(
    val processed: Int = 0, val total: Int = 0, val imported: Int = 0, val already: Int = 0,
    val unmatched: List<String> = emptyList(), val unmatchedTracks: List<SpotifyTrack> = emptyList(),
    val remainingTracks: List<SpotifyTrack>? = null,
    val unmatchedReasons: Map<SpotifyTrack, SpotifyMatchFailure> = emptyMap(),
)

internal class SpotifyImportRepository(private val database: MusicDatabase, private val resolver: SpotifyTrackResolver = SpotifyTrackResolver()) {
    suspend fun import(tracks: List<SpotifyTrack>, onProgress: (SpotifyImportProgress) -> Unit) = withContext(Dispatchers.IO) {
        val reasons = ConcurrentHashMap<SpotifyTrack, SpotifyMatchFailure>()
        SpotifyImportPipeline(
            resolve = { track: SpotifyTrack ->
                val result = resolver.resolveDetailed(track)
                result.failure?.let { reasons[track] = it }
                result.song
            },
            store = { candidate ->
                var result = SpotifyStoreResult.ALREADY
                database.withTransaction {
                    val existing = songEntity(candidate.id)
                    val already = existing?.liked == true && existing.inLibrary != null
                    if (!already) {
                        val now = LocalDateTime.now()
                        if (existing == null) insert(candidate.toMediaMetadata()) { it.withSpotifyImportMembership(now) }
                        else update(existing.withSpotifyImportMembership(now))
                        result = SpotifyStoreResult.IMPORTED
                    }
                }
                result
            },
        ).import(tracks) { progress ->
            onProgress(progress.copy(unmatchedReasons = progress.unmatchedTracks.mapNotNull { track ->
                reasons[track]?.let { track to it }
            }.toMap()))
        }
    }
}
