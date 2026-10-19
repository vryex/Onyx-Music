package com.metrolist.music.ui.theme

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Secondary/tertiary swatches are drawn from the same bitmap palette, never
// synthesized with arbitrary hue rotations.
data class ArtworkTones(
    val surface: Color,
    val accent: Color,
    val tertiary: Color = accent,
    val quaternary: Color = surface,
)

private val artworkColors = LruCache<String, ArtworkTones>(96)
private val artworkFallback = ArtworkTones(Color(0xFF252529), Color(0xFF353539))

/** Reuse one tiny cached bitmap sample to obtain both the dominant and alternate colors. */
@Composable
fun rememberArtworkTones(artwork: String?): State<ArtworkTones> {
    val context = LocalContext.current
    // Recycled cards should start with their cached palette, not replay a
    // fallback-to-color animation whenever they scroll back into view.
    val initial = remember(artwork) { artwork?.let(artworkColors::get) ?: artworkFallback }
    return produceState(initial, artwork, context) {
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
                // Additional prominent, visually distinct swatches are only
                // used by the animated lyrics background. All are real colors
                // from the SAME decoded album cover (no extra artwork loads).
                val selected = mutableListOf(main, secondary)
                val maxPopulation = swatches.maxOfOrNull { it.population } ?: 0
                swatches.sortedByDescending { it.population }.forEach { swatch ->
                    if (selected.size >= 4) return@forEach
                    if (swatch.population < maxPopulation * 0.05f) return@forEach
                    val candidate = swatch.rgb
                    val sufficientlyDifferent = selected.all { existing ->
                        val red = ((candidate shr 16) and 255) - ((existing shr 16) and 255)
                        val green = ((candidate shr 8) and 255) - ((existing shr 8) and 255)
                        val blue = (candidate and 255) - (existing and 255)
                        red * red + green * green + blue * blue >= 45 * 45
                    }
                    if (sufficientlyDifferent) selected.add(candidate)
                }
                ArtworkTones(
                    surface = Color(ArtworkPalette.surface(main)),
                    accent = Color(ArtworkPalette.surface(secondary)),
                    tertiary = Color(ArtworkPalette.surface(selected.getOrElse(2) { secondary })),
                    quaternary = Color(ArtworkPalette.surface(selected.getOrElse(3) { main })),
                ).also { artworkColors.put(artwork, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                artworkFallback
            }
        }
    }
}

/**
 * Illuminate sung lyrics with an ACTUAL color from the cover palette.
 * Only lightness is raised for accessibility: no artificial hue rotation.
 * Neutral covers stay neutral rather than turning purple/pink.
 */
@Composable
fun rememberLyricsAccent(artwork: String?): State<Color> {
    val tones by rememberArtworkTones(artwork)
    val visibleAccent = remember(tones) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(tones.accent.toArgb(), hsl)
        if (hsl[1] < 0.12f) {
            // An almost monochrome artwork has no meaningful chromatic accent.
            Color(0xFFE5E5E5)
        } else {
            // Preserve the source hue AND saturation. Increase brightness only.
            hsl[2] = hsl[2].coerceAtLeast(0.72f)
            Color(ColorUtils.HSLToColor(hsl))
        }
    }
    return animateColorAsState(
        targetValue = visibleAccent,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "sonetLyricsAccent",
    )
}

/** Existing color behavior/transition retained for every original caller. */
@Composable
fun rememberArtworkColor(artwork: String?): State<Color> {
    val tones by rememberArtworkTones(artwork)
    return animateColorAsState(tones.surface, tween(650, easing = FastOutSlowInEasing), label = "artworkColor")
}
