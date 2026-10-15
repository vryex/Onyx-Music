package com.metrolist.music.ui.player

internal fun timelineDuration(duration: Long): Long = duration.coerceAtLeast(1L)

internal fun timelinePosition(position: Long, duration: Long): Long =
    position.coerceIn(0L, duration.coerceAtLeast(0L))
