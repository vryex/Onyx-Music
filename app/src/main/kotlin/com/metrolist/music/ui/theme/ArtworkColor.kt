package com.metrolist.music.ui.theme

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtworkTones(val surface: Color, val accent: Color)

private val artworkColors = LruCache<String, ArtworkTones>(24)
private val artworkFallback = ArtworkTones(Color(0xFF252529), Color(0xFF353539))

/** Reuse one tiny cached bitmap sample to obtain both the dominant and alternate colors. */
@Composable
fun rememberArtworkTones(artwork: String?): State<ArtworkTones> {
    val context = LocalContext.current
    return produceState(artworkFallback, artwork, context) {
        if (artwork.isNullOrBlank()) {
            value = artworkFallback
            return@produceState
        }
        value = artworkColors.get(artwork) ?: withContext(Dispatchers.IO) {
            try {
                val image = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(artwork).size(96)
                        .allowHardware(false).build(),
                ).image ?: return@withContext artworkFallback
                val swatches = Palette.from(image.toBitmap()).maximumColorCount(16).generate().swatches
                val colors = swatches.map { it.rgb }.toIntArray()
                val populations = swatches.map { it.population }.toIntArray()
                val main = ArtworkPalette.select(colors, populations)
                val secondary = ArtworkPalette.accent(colors, populations, main)
                ArtworkTones(
                    Color(ArtworkPalette.surface(main)),
                    Color(ArtworkPalette.surface(secondary)),
                ).also { artworkColors.put(artwork, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                artworkFallback
            }
        }
    }
}

/** Existing color behavior/transition retained for every original caller. */
@Composable
fun rememberArtworkColor(artwork: String?): State<Color> {
    val tones by rememberArtworkTones(artwork)
    return animateColorAsState(tones.surface, tween(650, easing = FastOutSlowInEasing), label = "artworkColor")
}
