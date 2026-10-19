package com.metrolist.music.ui.player

import kotlin.math.abs

/** Buffering must finish before correcting drift; otherwise every seek restarts decoding. */
internal fun videoSeekTarget(position: Long, audioPosition: Long, ready: Boolean): Long? {
    val target = audioPosition.coerceAtLeast(0L)
    return target.takeIf { ready && abs(position - target) > 1_500L }
}
