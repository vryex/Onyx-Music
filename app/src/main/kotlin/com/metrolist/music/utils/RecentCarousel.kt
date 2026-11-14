package com.metrolist.music.utils

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

internal const val RecentCarouselFadeMillis = 850

internal fun recentCarouselIndices(
    songCount: Int,
    requests: ReceiveChannel<Int>,
    fadeMillis: Long = RecentCarouselFadeMillis.toLong(),
    idleMillis: Long = 6500,
) = flow {
    if (songCount <= 0) return@flow
    var index = 0
    emit(index)
    if (songCount == 1) return@flow
    while (currentCoroutineContext().isActive) {
        val direction = withTimeoutOrNull(idleMillis) { requests.receive() } ?: 1
        index = Math.floorMod(index + direction, songCount)
        emit(index)
        // Finish one blend before consuming the latest queued swipe.
        delay(fadeMillis)
    }
}
