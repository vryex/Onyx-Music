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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    shape: Shape = RoundedCornerShape(16.dp),
    emphasized: Boolean = false,
): Modifier {
    val c = MaterialTheme.colorScheme
    val surface = if (emphasized) c.surfaceContainerHigh else c.surfaceContainer
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(surface, c.surfaceContainerLow)))
        .border(1.dp, GlassEdge, shape)
}

@Composable
fun Modifier.onyxBackdrop(): Modifier {
    return background(MidnightCanvas)
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
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, GlassEdge, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.onyx_mark),
                contentDescription = null,
                tint = FrostGlow,
                modifier = Modifier.size(25.dp),
            )
        }
        Column {
            Text(
                text = "Onyx Music",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@Composable
fun OnyxPageHeader(title: String, modifier: Modifier = Modifier) {
    val headingStyle = MaterialTheme.typography.headlineLarge.copy(
        brush = Brush.verticalGradient(listOf(Color(0xFFD8ECF8), Color(0xFF98C0EF))),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawWithCache {
                val halo = Brush.radialGradient(
                    listOf(Color(0x187C91B6), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f),
                    radius = size.width.coerceAtLeast(1f),
                )
                val cell = 48.dp.toPx()
                onDrawBehind {
                    drawRect(halo)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(Color(0x08BAD7F7), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        x += cell
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color(0x08BAD7F7), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        y += cell
                    }
                }
            },
    ) {
        Text(
            text = title,
            style = headingStyle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        )
    }
}
