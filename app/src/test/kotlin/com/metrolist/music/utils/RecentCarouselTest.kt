package com.metrolist.music.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentCarouselTest {
    @Test fun `rapid swipes finish the current fade and keep the latest pending direction`() = runBlocking {
        val requests = Channel<Int>(Channel.CONFLATED)
        val shown = Channel<Int>()
        val job = launch { recentCarouselIndices(3, requests, fadeMillis = 100, idleMillis = 10_000).collect { shown.send(it) } }
        try {
            assertEquals(0, shown.receive())
            requests.send(1)
            assertEquals(1, shown.receive())
            val start = System.nanoTime()
            requests.send(1)
            requests.send(-1)
            assertEquals(0, shown.receive())
            assertTrue("A swipe interrupted the active fade", (System.nanoTime() - start) / 1_000_000 >= 90)
        } finally { job.cancel() }
    }

    @Test fun `automatic rotation wraps back to the first song`() = runBlocking {
        val requests = Channel<Int>(Channel.CONFLATED)
        val shown = Channel<Int>()
        val job = launch { recentCarouselIndices(2, requests, fadeMillis = 1, idleMillis = 1).collect { shown.send(it) } }
        try {
            assertEquals(0, shown.receive())
            assertEquals(1, shown.receive())
            assertEquals(0, shown.receive())
        } finally { job.cancel() }
    }
}
