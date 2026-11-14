package com.metrolist.music.spotify

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyRequestPacerTest {
    @Test fun allWorkersShareRequestSpacingAcrossFastAndFallbackCalls() = runBlocking {
        var now = 0L
        val starts = mutableListOf<Long>()
        val pacer = SpotifyRequestPacer(intervalMillis = 150, clockNanos = { now }, wait = { now += it * 1_000_000 })
        coroutineScope {
            repeat(3) {
                launch { repeat(2) { pacer.awaitTurn(); starts.add(now / 1_000_000) } }
            }
        }
        assertEquals(listOf(0L, 150L, 300L, 450L, 600L, 750L), starts)
    }

    @Test fun timeSpentInNetworkDoesNotAddAnUnconditionalDelay() = runBlocking {
        var now = 0L
        val waits = mutableListOf<Long>()
        val pacer = SpotifyRequestPacer(clockNanos = { now }, wait = { waits.add(it); now += it * 1_000_000 })
        pacer.awaitTurn()
        now += 500_000_000
        pacer.awaitTurn()
        assertEquals(emptyList<Long>(), waits)
    }
}
