package com.metrolist.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.metrolist.music.ui.utils.resize

/**
 * Match the palette used by the artist profile.
 * Reuses the existing 96px off-main-thread LRU sampler; no blur or re-extraction
 * during scrolling through the home artist cards.
 */
@Composable
fun rememberHomeArtistTint(artwork: String?): Brush {
    val tint by rememberArtworkColor(artwork?.resize(224, 224))
    return remember(tint) {
        Brush.verticalGradient(
            0.00f to lerp(Color.Black, tint, 0.94f),
            0.52f to lerp(Color.Black, tint, 0.55f),
            1.00f to Color(0xFF111111),
        )
    }
}
