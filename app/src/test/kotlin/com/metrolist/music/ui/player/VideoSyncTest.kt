package com.metrolist.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSyncTest {
    @Test fun `a buffering decoder can start before seeking to the audio timeline`() {
        assertNull(videoSeekTarget(0, 65_000, ready = false))
        assertEquals(65_000L, videoSeekTarget(0, 65_000, ready = true))
        assertNull(videoSeekTarget(65_350, 65_500, ready = true))
    }

    @Test fun `manual seek while paused updates the video without drift seeking every poll`() {
        assertEquals(120_000L, videoSeekTarget(10_000, 120_000, ready = true))
        assertNull(videoSeekTarget(120_000, 120_000, ready = true))
    }

    @Test fun `rewinding never seeks before the start of the clip`() {
        assertEquals(0L, videoSeekTarget(20_000, -50, ready = true))
    }
}
