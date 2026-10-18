package com.metrolist.music.spotify

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class SpotifyStoreResult { IMPORTED, ALREADY }

internal class SpotifyImportPipeline<T : Any>(
    private val resolve: suspend (SpotifyTrack) -> T?,
    private val store: suspend (T) -> SpotifyStoreResult,
    private val parallelism: Int = 3,
) {
    init { require(parallelism in 1..3) }

    suspend fun import(tracks: List<SpotifyTrack>, onProgress: (SpotifyImportProgress) -> Unit) {
        val mutex = Mutex()
        val states = Array<SpotifyStoreResult?>(tracks.size) { null }
        val unmatched = BooleanArray(tracks.size)
        var next = 0
        var lastProgress: SpotifyImportProgress? = null
        fun report() {
            val unmatchedTracks = tracks.filterIndexed { index, _ -> unmatched[index] }
            val imported = states.count { it == SpotifyStoreResult.IMPORTED }
            val already = states.count { it == SpotifyStoreResult.ALREADY }
            val progress = SpotifyImportProgress(
                processed = imported + already + unmatchedTracks.size, total = tracks.size,
                imported = imported, already = already, unmatchedTracks = unmatchedTracks,
                unmatched = unmatchedTracks.map { "${it.title} — ${it.artists.joinToString()}" },
                remainingTracks = tracks.filterIndexed { index, _ -> states[index] == null },
            )
            if (progress != lastProgress) {
                onProgress(progress)
                lastProgress = progress
            }
        }
        report()
        try {
            coroutineScope {
                // Fixed workers pull one index at a time; never allocate one async job per song.
                repeat(minOf(parallelism, tracks.size)) {
                    launch {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val index = mutex.withLock { if (next < tracks.size) next++ else null } ?: break
                            val candidate = resolve(tracks[index])
                            mutex.withLock {
                                currentCoroutineContext().ensureActive()
                                if (candidate == null) {
                                    unmatched[index] = true
                                    report()
                                } else {
                                    // A short local transaction and its checkpoint must finish together after commit.
                                    withContext(NonCancellable) {
                                        states[index] = store(candidate)
                                        report()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) { mutex.withLock { report() } }
        }
    }
}
