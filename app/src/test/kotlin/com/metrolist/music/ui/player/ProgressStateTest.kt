package com.metrolist.music.ui.player

import androidx.compose.runtime.mutableLongStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressStateTest {
    @Test
    fun `unknown or nonpositive duration produces zero progress`() {
        val position = mutableLongStateOf(500L)
        val duration = mutableLongStateOf(0L)
        val progress = ProgressState(position, duration)
        assertEquals(0f, progress.progress, 0f)
        duration.longValue = Long.MIN_VALUE
        assertEquals(0f, progress.progress, 0f)
    }

    @Test
    fun `progress follows live state and clamps seeks beyond track bounds`() {
        val position = mutableLongStateOf(25L)
        val duration = mutableLongStateOf(100L)
        val progress = ProgressState(position, duration)
        assertEquals(0.25f, progress.progress, 0f)
        position.longValue = -10L
        assertEquals(0f, progress.progress, 0f)
        position.longValue = 200L
        assertEquals(1f, progress.progress, 0f)
        duration.longValue = 400L
        assertEquals(0.5f, progress.progress, 0f)
    }

    @Test
    fun `extreme state values keep progress finite`() {
        val progress = ProgressState(mutableLongStateOf(Long.MAX_VALUE), mutableLongStateOf(1L))
        assertTrue(progress.progress.isFinite())
        assertEquals(1f, progress.progress, 0f)
    }
}
