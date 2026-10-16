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
            .then(if (emphasized) Modifier.clip(CircleShape).background(containerColor ?: Color(0xFFF3F3F5)) else Modifier.onyxGlass(CircleShape))
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
    val context = LocalContext.current
    val request = remember(artwork, context) {
        ImageRequest.Builder(context).data(artwork).size(560).build()
    }
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
            .onyxGlass(RoundedCornerShape(32.dp), emphasized = true).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.onyx_daily_selection),
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.onyx_home_headline),
                    style = MaterialTheme.typography.headlineLarge,
                    letterSpacing = (-1).sp, fontWeight = FontWeight.Medium)
            }
            Box(
                Modifier.widthIn(max = 128.dp).weight(0.8f).aspectRatio(0.92f)
                    .graphicsLayer { rotationZ = 6f }
                    .onyxGlass(RoundedCornerShape(24.dp)).padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) AsyncImage(request, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                else Icon(painterResource(R.drawable.onyx_mark), null, Modifier.size(64.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidIconButton(R.drawable.play, stringResource(R.string.play), onPlay, Modifier.size(56.dp), emphasized = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title ?: stringResource(R.string.onyx_find_your_sound), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist ?: stringResource(R.string.onyx_made_for_you), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LiquidIconButton(R.drawable.arrow_forward, stringResource(R.string.search), onExplore)
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
        LiquidIconButton(R.drawable.expand_more, stringResource(R.string.onyx_minimize_player), onCollapse)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ONYX", style = MaterialTheme.typography.labelMedium, letterSpacing = 4.sp)
            Text(stringResource(R.string.onyx_now_playing), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LiquidIconButton(R.drawable.equalizer, stringResource(R.string.equalizer), onEqualizer)
    }
}
