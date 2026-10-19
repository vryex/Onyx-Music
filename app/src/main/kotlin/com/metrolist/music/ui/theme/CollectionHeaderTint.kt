/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * A restrained, artwork-tinted header that scrolls away with the first lazy item.
 * The shared 96px palette sampler already caches its result off the UI thread.
 * No image blur, scrolling animation or per-frame bitmap processing is used here.
 */
@Composable
fun CollectionHeaderTint(
    artwork: String?,
    scrollState: LazyListState? = null,
    height: Dp = 480.dp,
    queueStyle: Boolean = false,
) {
    val color by rememberArtworkColor(artwork)
    val gradient = remember(color, queueStyle) {
        if (queueStyle) {
            // Player artwork is intentionally darkened for readable white labels.
            // Lift it slightly only for the queue; otherwise its 440dp wash had
            // already disappeared by the time the scrolling song rows began.
            val visibleTint = lerp(color, Color.White, 0.13f)
            Brush.verticalGradient(
                0.00f to lerp(Color.Black, visibleTint, 0.88f),
                0.33f to lerp(Color.Black, visibleTint, 0.52f),
                0.65f to lerp(Color.Black, visibleTint, 0.12f),
                0.94f to Color.Black,
                1.00f to Color.Black,
            )
        } else {
            Brush.verticalGradient(
                0.00f to lerp(Color.Black, color, 0.45f),
                0.36f to lerp(Color.Black, color, 0.22f),
                0.72f to lerp(Color.Black, color, 0.05f),
                1.00f to Color.Black,
            )
        }
    }
    // Collection pages scroll their tint away; the queue keeps its short wash fixed
    // because it automatically scrolls to the current song, often below item zero.
    if (scrollState == null || scrollState.firstVisibleItemIndex == 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset { IntOffset(0, -(scrollState?.firstVisibleItemScrollOffset ?: 0)) }
                .background(gradient),
        )
    }
}
