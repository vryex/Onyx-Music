package com.metrolist.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimelineTest {
    @Test fun `unknown duration has a nonempty slider range`() {
        assertEquals(1L, timelineDuration(-9223372036854775807L))
        assertEquals(1L, timelineDuration(0))
    }

    @Test fun `seek stays within the current track after track changes`() {
        assertEquals(30_000L, timelinePosition(90_000, 30_000))
        assertEquals(0L, timelinePosition(-300, 30_000))
        assertEquals(0L, timelinePosition(3_000, 0))
        assertEquals(12_345L, timelinePosition(12_345, 60_000))
    }
}
