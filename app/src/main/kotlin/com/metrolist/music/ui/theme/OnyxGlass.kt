/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

/** Transparent, draw-only glass surfaces without realtime bitmap blur. */
@Composable
fun Modifier.onyxGlass(
    shape: Shape = RoundedCornerShape(26.dp),
    emphasized: Boolean = false,
): Modifier {
    val c = MaterialTheme.colorScheme
    val surface = if (emphasized) c.surfaceContainerHigh else c.surfaceContainer
    return this
        .clip(shape)
        .background(surface)
        .border(1.dp, c.onSurface.copy(alpha = 0.10f), shape)
}

@Composable
fun Modifier.onyxBackdrop(): Modifier {
    val c = MaterialTheme.colorScheme
    return background(Color.Black)
}

@Composable
fun OnyxBrand(
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.onyx_mark),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(25.dp),
            )
        }
        Column {
            Text(
                text = "Onyx Music",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
