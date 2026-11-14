package com.metrolist.music.spotify

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SpotifyRequestPacer(
    private val intervalMillis: Long = 150,
    private val clockNanos: () -> Long = System::nanoTime,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var previousStart: Long? = null

    suspend fun awaitTurn() = mutex.withLock {
        previousStart?.let { previous ->
            val remainingNanos = intervalMillis * 1_000_000 - (clockNanos() - previous)
            if (remainingNanos > 0) wait((remainingNanos + 999_999) / 1_000_000)
        }
        previousStart = clockNanos()
    }

    companion object {
        val shared = SpotifyRequestPacer()
    }
}
