/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.music.ui.utils.recommendationArtworkUrl
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.metrolist.music.ui.theme.rememberHomeArtistTint
import com.metrolist.music.ui.theme.rememberArtworkColor
import androidx.compose.ui.graphics.lerp
import com.metrolist.music.ui.utils.resize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.YTItem
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
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(130), label = "controlPress")
    val shape = RoundedCornerShape(24.dp)
    val accent = MaterialTheme.colorScheme.primary
    val surface = if (emphasized) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .clip(shape)
            .then(
                if (plain && !emphasized) Modifier else Modifier
                    .background(containerColor ?: surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (emphasized) 0.55f else 0.18f), shape),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon), description,
            tint = contentColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(if (emphasized) 28.dp else 22.dp),
        )
    }
}

@Composable
private fun ObservatoryStars(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val stars = listOf(
            .06f to .27f, .14f to .73f, .22f to .13f, .31f to .56f,
            .41f to .09f, .52f to .85f, .64f to .18f, .72f to .64f,
            .81f to .11f, .9f to .47f, .96f to .8f,
        )
        stars.forEachIndexed { index, (x, y) ->
            drawCircle(tint.copy(alpha = if (index % 3 == 0) .65f else .28f),
                radius = if (index % 3 == 0) 1.3.dp.toPx() else .7.dp.toPx(),
                center = Offset(size.width * x, size.height * y))
        }
    }
}

@Composable
fun OnyxHomeObservatory() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.home), style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.onyx_home_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OnyxHomeShortcuts(onHistory: () -> Unit, onExplore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeShortcut(stringResource(R.string.history), R.drawable.history, onHistory, Modifier.weight(1f))
        HomeShortcut(stringResource(R.string.mood_and_genres), R.drawable.discover_tune, onExplore, Modifier.weight(1f))
    }
}

@Composable
private fun HomeShortcut(title: String, icon: Int, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OnyxHomeHero(
    title: String?,
    artist: String?,
    artwork: String?,
    label: String,
    onPlay: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (title.isNullOrBlank() || artwork.isNullOrBlank()) return
    var fallbackArt by remember(artwork) { mutableStateOf(false) }
    val artTone by rememberArtworkColor(artwork.resize(224, 224))
    // One understated surface. The parent crossfades artwork AND metadata in
    // this same position; no card carousel, pagination, or magazine layout.
    Box(
        modifier = modifier.height(316.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
            .clickable(role = Role.Button, onClick = onExplore),
    ) {
        AsyncImage(
            model = if (fallbackArt) artwork else artwork.recommendationArtworkUrl(),
            contentDescription = title,
            onError = { fallbackArt = true },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .10f),
                    .32f to Color.Transparent,
                    .62f to artTone.copy(alpha = .12f),
                    .82f to Color.Black.copy(alpha = .76f),
                    1f to Color.Black.copy(alpha = .96f),
                ),
            ),
        )
        Text(
            label,
            color = Color.White.copy(alpha = .92f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title, color = Color.White, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    artist.orEmpty(), color = Color.White.copy(alpha = .76f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = .20f))
                    .clickable(role = Role.Button, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.play), contentDescription = stringResource(R.string.play),
                    tint = Color.White, modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun OnyxQuickAccessItem(item: YTItem, isPinned: Boolean, modifier: Modifier = Modifier,
    isActive: Boolean = false, isPlaying: Boolean = false) {
    val artistTint = if (item is com.metrolist.innertube.models.ArtistItem) rememberHomeArtistTint(item.thumbnail) else null
    Row(modifier.clip(RoundedCornerShape(16.dp))
        .then(
            if (artistTint != null) Modifier.background(artistTint)
            else Modifier.background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) .5f else .16f), RoundedCornerShape(16.dp))
        )
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemThumbnail(item.thumbnail, isActive, isPlaying, RoundedCornerShape(8.dp), Modifier.size(56.dp))
        Text(item.title, Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (isPinned) Icon(painterResource(R.drawable.ic_push_pin), null, Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun OnyxCollectionCard(title: String, subtitle: String, icon: Int, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.onyxGlass(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).onyxGlass(RoundedCornerShape(24.dp), emphasized = true), contentAlignment = Alignment.Center) {
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
fun OnyxPlayerHeader(onCollapse: () -> Unit, onEqualizer: () -> Unit, modifier: Modifier = Modifier, onVideo: (() -> Unit)? = null, videoSelected: Boolean = false, showCast: Boolean = false, playbackSource: String? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        LiquidIconButton(R.drawable.expand_more, stringResource(R.string.onyx_minimize_player), onCollapse, plain = true)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.sonet_now_playing), style = MaterialTheme.typography.labelMedium,
                color = Color.White, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!playbackSource.isNullOrBlank()) {
                Text(stringResource(R.string.sonet_playback_from, playbackSource),
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.84f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showCast) CastButton(tintColor = Color.White)
            if (onVideo != null) LiquidIconButton(R.drawable.slow_motion_video, stringResource(R.string.onyx_video), onVideo,
                emphasized = videoSelected, plain = !videoSelected)
            LiquidIconButton(R.drawable.equalizer, stringResource(R.string.equalizer), onEqualizer, plain = true)
        }
    }
}
