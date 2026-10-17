package com.metrolist.music.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Organic, non-directional artwork backdrop behind the lyrics.
 * Each authentic palette color follows a different slow, curved orbit;
 * there is no vertical gradient moving from top to bottom.
 *
 * Animation values are observed only in Canvas drawing so synchronized
 * lyrics are never remeasured at animation frame rate.
 */
@Composable
fun SonetLyricsBackdrop(artwork: String?, modifier: Modifier = Modifier, active: Boolean = true) {
    val tones by rememberArtworkTones(artwork)
    val colors = remember(tones) {
        listOf(tones.surface, tones.accent, tones.tertiary, tones.quaternary)
    }
    val phaseState = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            phaseState.animateTo(phaseState.value + 6.2831855f,
                tween(durationMillis = 35000, easing = LinearEasing))
        }
    }

    val contrastOverlay = remember {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .08f),
            .53f to Color.Transparent,
            1f to Color.Black.copy(alpha = .62f),
        )
    }

    Canvas(modifier) {
        drawRect(Color(0xFF020305))
        val phase = phaseState.value
        val radius = max(size.width, size.height) * 0.77f
        colors.forEachIndexed { index, color ->
            val indexF = index.toFloat()
            // Different speeds and phases create smoothly roaming color pools.
            // They can move sideways, diagonally, up or down independently.
            val x = (0.49f +
                0.32f * sin(phase * (0.81f + indexF * .14f) + indexF * 1.77f) +
                0.08f * cos(phase * 1.41f + indexF)).coerceIn(0.10f, 0.90f)
            val y = (0.43f +
                0.29f * cos(phase * (0.69f + indexF * .18f) + indexF * 2.16f) +
                0.09f * sin(phase * 1.23f + indexF)).coerceIn(0.12f, 0.83f)
            val center = Offset(size.width * x, size.height * y)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = .76f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                center = center,
                radius = radius,
            )
        }
        // Retain contrast at the bottom controls and across inactive words.
        drawRect(brush = contrastOverlay)
    }
}
