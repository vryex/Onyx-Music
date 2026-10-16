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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
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
    return clip(shape)
        .background(if (emphasized) Color(0xFF10093A) else Color(0xFF060317))
        .drawWithCache {
            val innerLight = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.045f), Color.Transparent))
            onDrawBehind { drawRect(innerLight) }
        }
        .border(1.dp, GlassEdge, shape)
}

@Composable
fun Modifier.onyxBackdrop(): Modifier {
    return background(MidnightCanvas).drawWithCache {
        val stars = List(32) { i ->
            Offset(((i * 73 + 19) % 997) / 997f * size.width, ((i * 137 + 43) % 991) / 991f * size.height)
        }
        onDrawBehind {
            stars.forEachIndexed { i, point ->
                drawCircle(FrostGlow.copy(alpha = if (i % 3 == 0) 0.12f else 0.05f), 0.65.dp.toPx(), point)
            }
        }
    }
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
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, GlassEdge, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.onyx_mark),
                contentDescription = null,
                tint = FrostGlow,
                modifier = Modifier.size(23.dp),
            )
        }
        Column {
            Text(
                text = "Onyx Music",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = (-0.5).sp,
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
fun OnyxPageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title, style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp,
        )
        if (subtitle != null) Text(
            subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Saved per screen so returning a lazy header to view does not replay it. */
@Composable
fun Modifier.onyxEntrance(key: Any? = Unit): Modifier {
    var appeared by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (appeared) 1f else 0f) }
    LaunchedEffect(key) {
        if (!appeared) {
            progress.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            appeared = true
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 26.dp.toPx()
    }
}
