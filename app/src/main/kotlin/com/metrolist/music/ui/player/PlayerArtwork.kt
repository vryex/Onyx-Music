package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.ui.utils.resize

/** Coordinates are relative to the moving sheet, so dragging it doesn't restart the flight. */
@Stable
internal class PlayerArtworkPlacement {
    private var container: LayoutCoordinates? = null
    private var target: LayoutCoordinates? = null
    var bounds by mutableStateOf(Rect.Zero)
        private set

    fun placeContainer(coordinates: LayoutCoordinates) {
        container = coordinates
        update()
    }

    fun placeTarget(coordinates: LayoutCoordinates) {
        target = coordinates
        update()
    }

    private fun update() {
        val source = container ?: return
        val destination = target ?: return
        if (source.isAttached && destination.isAttached) {
            bounds = source.localBoundingBoxOf(destination, clipBounds = false)
        }
    }
}

/** One decoded image and GPU transform; no lookahead layout of the player or queue. */
@Composable
internal fun PlayerArtwork(
    url: String?,
    placeholder: Painter?,
    mix: State<Float>,
    placement: PlayerArtworkPlacement,
    visibleInCoverMode: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.82f)
            .graphicsLayer {
                val t = mix.value
                val target = placement.bounds
                val hasTarget = target.width > 0f && target.height > 0f
                val sx = if (hasTarget) 1f + (target.width / size.width - 1f) * t else 1f
                val sy = if (hasTarget) 1f + (target.height / size.height - 1f) * t else 1f
                scaleX = sx
                scaleY = sy
                translationX = if (hasTarget) target.left * t else 0f
                translationY = if (hasTarget) target.top * t else 0f
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = if (visibleInCoverMode) 1f else t
                shape = RoundedCornerShape(12.dp * t / minOf(sx, sy).coerceAtLeast(0.001f))
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val lowerFade = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.40f to Color.Transparent,
                    0.63f to Color.Black.copy(alpha = 0.25f),
                    0.82f to Color.Black.copy(alpha = 0.78f),
                    0.98f to Color.Black,
                    1f to Color.Black,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(lowerFade, alpha = 1f - mix.value, blendMode = BlendMode.DstOut)
                }
            },
    ) {
        AsyncImage(
            model = url?.resize(960, 960),
            placeholder = placeholder,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val target = placement.bounds
                val t = mix.value
                if (target.width > 0f && target.height > 0f) {
                    val sx = 1f + (target.width / size.width - 1f) * t
                    val sy = 1f + (target.height / size.height - 1f) * t
                    // Crop the source instead of stretching it into a square.
                    scaleY = sx / sy.coerceAtLeast(0.001f)
                }
            },
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = 1f - mix.value }
                .background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.26f),
                    0.19f to Color.Transparent,
                    1f to Color.Transparent,
                )),
        )
    }
}
