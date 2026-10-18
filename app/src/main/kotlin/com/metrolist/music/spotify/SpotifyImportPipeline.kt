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
    private val fallbackResolve: (suspend (SpotifyTrack) -> T?)? = null,
) {
    init { require(parallelism in 1..3) }

    suspend fun import(tracks: List<SpotifyTrack>, onProgress: (SpotifyImportProgress) -> Unit) {
        val mutex = Mutex()
        val states = Array<SpotifyStoreResult?>(tracks.size) { null }
        val unmatched = BooleanArray(tracks.size)
        val deferred = BooleanArray(tracks.size)
        var scanned = 0
        var lastProgress: SpotifyImportProgress? = null

        fun report() {
            val unmatchedTracks = tracks.filterIndexed { index, _ -> unmatched[index] }
            val imported = states.count { it == SpotifyStoreResult.IMPORTED }
            val already = states.count { it == SpotifyStoreResult.ALREADY }
            val progress = SpotifyImportProgress(
                processed = imported + already + unmatchedTracks.size, total = tracks.size,
                imported = imported, already = already, scanned = scanned,
                unmatchedTracks = unmatchedTracks,
                unmatched = unmatchedTracks.map { "${it.title} — ${it.artists.joinToString()}" },
                remainingTracks = tracks.filterIndexed { index, _ -> states[index] == null },
            )
            if (progress != lastProgress) {
                onProgress(progress)
                lastProgress = progress
            }
        }

        // Resolve inexpensive song queries for the entire library first. Expensive
        // video/continuation fallback must never block scanning the next song.
        suspend fun round(deep: Boolean) = coroutineScope {
            var next = 0
            repeat(minOf(parallelism, tracks.size)) {
                launch {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val index = mutex.withLock {
                            while (next < tracks.size && deep && !deferred[next]) next++
                            if (next < tracks.size) next++ else null
                        } ?: break
                        val candidate = if (deep) fallbackResolve?.invoke(tracks[index]) else resolve(tracks[index])
                        mutex.withLock {
                            currentCoroutineContext().ensureActive()
                            if (!deep) scanned++
                            if (candidate == null) {
                                if (deep || fallbackResolve == null) {
                                    unmatched[index] = true
                                    deferred[index] = false
                                } else deferred[index] = true
                                report()
                            } else {
                                // The committed DB transaction and progress checkpoint are inseparable.
                                withContext(NonCancellable) {
                                    states[index] = store(candidate)
                                    deferred[index] = false
                                    unmatched[index] = false
                                    report()
                                }
                            }
                        }
                    }
                }
            }
        }

        report()
        try {
            round(deep = false)
            if (fallbackResolve != null && deferred.any { it }) round(deep = true)
        } finally {
            withContext(NonCancellable) { mutex.withLock { report() } }
        }
    }
}
