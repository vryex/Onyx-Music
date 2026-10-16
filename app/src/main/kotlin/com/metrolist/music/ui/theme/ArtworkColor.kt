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

private val artworkColors = LruCache<String, Color>(24)
private val artworkFallback = Color(0xFF252529)

/** A tiny cached sample keeps palette work off the UI thread and playback frames. */
@Composable
fun rememberArtworkColor(artwork: String?): State<Color> {
    val context = LocalContext.current
    val color by produceState(artworkFallback, artwork, context) {
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
                val palette = Palette.from(image.toBitmap()).maximumColorCount(16).generate()
                val source = Color(palette.getDominantColor(0xFF252529.toInt()))
                val peak = maxOf(source.red, source.green, source.blue)
                val factor = if (peak > 0.42f) 0.42f / peak else 1f
                source.copy(red = source.red * factor, green = source.green * factor,
                    blue = source.blue * factor, alpha = 1f).also { artworkColors.put(artwork, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                artworkFallback
            }
        }
    }
    return animateColorAsState(color, tween(650, easing = FastOutSlowInEasing), label = "artworkColor")
}
