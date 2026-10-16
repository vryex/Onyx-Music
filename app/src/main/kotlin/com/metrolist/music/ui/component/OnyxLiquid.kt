/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.R
import com.metrolist.music.ui.theme.onyxGlass

@Composable
fun LiquidIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    plain: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.91f else 1f, tween(130), label = "liquidPress")
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (enabled) 1f else 0.35f
            }
            .then(if (emphasized) Modifier.clip(CircleShape).background(containerColor ?: MaterialTheme.colorScheme.primary) else if (plain) Modifier.clip(CircleShape) else Modifier.clip(CircleShape))
            .clickable(interactionSource = interaction, indication = null,
                role = Role.Button, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description,
            tint = contentColor ?: if (emphasized) Color(0xFF101114) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(if (emphasized) 34.dp else 22.dp))
    }
}

@Composable
fun OnyxHomeHero(title: String?, artist: String?, artwork: String?, onPlay: () -> Unit, onExplore: () -> Unit) {
    if (title == null || artwork.isNullOrBlank()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.onyx_daily_selection), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF282828))
            .clickable(role = Role.Button, onClick = onExplore), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(artwork, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(artist.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            LiquidIconButton(R.drawable.play, stringResource(R.string.play), onPlay,
                Modifier.padding(end = 12.dp).size(48.dp), emphasized = true,
                containerColor = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun OnyxCollectionCard(title: String, subtitle: String, icon: Int, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.onyxGlass(RoundedCornerShape(26.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).onyxGlass(CircleShape, emphasized = true), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, Modifier.size(22.dp))
            }
            Icon(painterResource(R.drawable.arrow_forward), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun OnyxPlayerHeader(onCollapse: () -> Unit, onEqualizer: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        LiquidIconButton(R.drawable.expand_more, stringResource(R.string.onyx_minimize_player), onCollapse, plain = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.onyx_now_playing), style = MaterialTheme.typography.labelMedium)
        }
        LiquidIconButton(R.drawable.equalizer, stringResource(R.string.equalizer), onEqualizer, plain = true)
    }
}
