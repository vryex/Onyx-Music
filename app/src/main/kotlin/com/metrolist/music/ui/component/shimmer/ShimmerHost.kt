/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.shimmer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.defaultShimmerTheme
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    showGradient: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The library themes via a Shimmer instance (rememberShimmer), not via a
    // parameter on Modifier.shimmer(); LocalShimmerTheme is the other route.
    val shimmerInstance = rememberShimmer(shimmerBounds = ShimmerBounds.View, theme = ShimmerTheme)
    val baseModifier = modifier.shimmer(shimmerInstance)

    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        modifier = if (showGradient) {
            baseModifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithCache {
                val fade = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = fade, blendMode = BlendMode.DstIn)
                }
            }
        } else {
            baseModifier
        },
        content = content,
    )
}

val ShimmerTheme =
    defaultShimmerTheme.copy(
        animationSpec =
        infiniteRepeatable(
            animation =
            tween(
                durationMillis = 800,
                easing = LinearEasing,
                delayMillis = 250,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        shaderColors =
        listOf(
            Color.White.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.50f),
            Color.White.copy(alpha = 0.25f),
        ),
    )
