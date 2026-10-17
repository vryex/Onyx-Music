package com.metrolist.music.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Draw-only, slow-moving lyrics background.
 *
 * Reuses the same tiny artwork palette as the player; no bitmap blur, shader,
 * randomized hues, background downloads or per-frame Palette extraction.
 * This composable exists only while inline lyrics are visible.
 */
@Composable
fun SonetLyricsBackdrop(artwork: String?, modifier: Modifier = Modifier) {
    val tones by rememberArtworkTones(artwork)
    val transition = rememberInfiniteTransition(label = "sonetLyricsBackgroundMotion")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "artworkPaletteDrift",
    )
    Canvas(modifier) {
        // Read animation only in DrawScope: redraw the cheap gradient, not the
        // lyric layout or word-timing calculation, on each animation frame.
        val amount = drift
        val top = lerp(tones.surface, tones.tertiary, amount)
        val middle = lerp(tones.accent, tones.quaternary, amount)
        val lower = lerp(tones.tertiary, tones.surface, amount)

        drawRect(
            brush = Brush.verticalGradient(
                0f to lerp(Color.Black, top, 0.86f),
                0.30f to lerp(Color.Black, middle, 0.68f),
                0.66f to lerp(Color.Black, lower, 0.52f),
                1f to Color.Black,
            ),
        )
        // Preserve high contrast for active and inactive synced lyrics.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.12f),
                0.48f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Black.copy(alpha = 0.55f),
            ),
        )
    }
}
