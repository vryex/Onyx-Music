/**
 * Onyx Music — lightweight artist-only search highlight.
 * Artist photos are sampled through the existing small, off-main-thread LRU palette.
 */
package com.metrolist.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.ui.theme.rememberArtworkColor
import com.metrolist.music.ui.utils.resize

/**
 * Vertical photo-colored wash behind individual artists only.
 * Keeps songs/albums and the search background unchanged; no expensive blur effects.
 */
@Composable
internal fun Modifier.artistSearchTint(item: YTItem): Modifier {
    val artist = item as? ArtistItem ?: return this
    val artwork = artist.thumbnail?.takeIf { it.isNotBlank() } ?: return this
    val tint by rememberArtworkColor(artwork.resize(144, 144))
    val gradient = remember(tint) {
        Brush.verticalGradient(
            0.00f to lerp(Color.Black, tint, 0.84f),
            0.38f to lerp(Color.Black, tint, 0.48f),
            0.78f to lerp(Color.Black, tint, 0.14f),
            1.00f to Color.Black,
        )
    }
    return this
        .padding(horizontal = 8.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(22.dp))
        .background(gradient)
}
