/**
 * Onyx Music — lightweight artist-only search highlight.
 * Artist photos are sampled through the existing small, off-main-thread LRU palette.
 */
package com.metrolist.music.ui.screens.search

import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.ui.theme.rememberArtworkColor
import com.metrolist.music.ui.utils.resize

/**
 * Horizontal photo-colored wash behind individual artists: vivid on the right,
 * fading smoothly toward pure black on the left.
 * Keeps songs/albums and the search background unchanged; no expensive blur effects.
 */
@Composable
internal fun Modifier.artistSearchTint(item: YTItem): Modifier {
    val artist = item as? ArtistItem ?: return this
    val artwork = artist.thumbnail?.takeIf { it.isNotBlank() } ?: return this
    val tint by rememberArtworkColor(artwork.resize(144, 144))
    val gradient = remember(tint) {
        Brush.horizontalGradient(
            0.00f to Color.Black,
            0.26f to lerp(Color.Black, tint, 0.10f),
            0.62f to lerp(Color.Black, tint, 0.43f),
            1.00f to lerp(Color.Black, tint, 0.84f),
        )
    }
    // Draw-only highlight: unlike layout padding/clipping, this moves neither the
    // avatar nor the title, menus, dividers or scroll positions.
    return this.drawBehind {
        val inset = 8.dp.toPx()
        val corner = 22.dp.toPx()
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(inset, 0f),
            size = Size((size.width - inset * 2).coerceAtLeast(0f), size.height),
            cornerRadius = CornerRadius(corner, corner),
        )
    }
}
