package com.metrolist.music.ui.player

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.component.SonetLoadingIndicator
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The service owns audio; this foreground-only player decodes the muted video track. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EmbeddedMusicVideo(
    videoId: String,
    artworkUrl: String?,
    positionProvider: () -> Long,
    playingProvider: () -> Boolean,
    speedProvider: () -> Float,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPosition by rememberUpdatedState(positionProvider)
    val latestPlaying by rememberUpdatedState(playingProvider)
    val latestSpeed by rememberUpdatedState(speedProvider)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    var attempt by remember(videoId) { mutableIntStateOf(0) }
    var rendered by remember(videoId, attempt) { mutableStateOf(false) }
    var advancing by remember(videoId, attempt) { mutableStateOf(false) }
    var failed by remember(videoId, attempt) { mutableStateOf(false) }
    var ratio by remember(videoId) { mutableStateOf(16f / 9f) }
    val player = remember(context, videoId, attempt) {
        ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 12_000, 500, 1_000).build())
            .build().apply {
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("SonetVideo", "Video decoder failed: " + error.errorCodeName)
                failed = true
                advancing = false
                latestUnavailable()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                advancing = false
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, videoId) {
        try {
            val stream = withContext(Dispatchers.IO) { InnerTubeXPlayer.videoStreamForPlayback(videoId) }
            val factory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(stream.headers)
                .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
            android.util.Log.i("SonetVideo", "Video stream resolved: client=" + stream.clientName +
                ", height=" + stream.videoHeight + ", bounded=" + (stream.requireBoundedRange || stream.useRangeChunks))
            val videoFactory = if (stream.requireBoundedRange || stream.useRangeChunks) {
                DataSource.Factory {
                    BoundedVideoDataSource(factory, requireNotNull(stream.videoContentLengthBytes), stream.rangeChunkSizeBytes)
                }
            } else factory
            val source = ProgressiveMediaSource.Factory(videoFactory).createMediaSource(
                MediaItem.Builder().setUri(requireNotNull(stream.videoUrl))
                    .setMimeType(stream.videoMimeType?.substringBefore(';')).build(),
            )
            player.setMediaSource(source, latestPosition().coerceAtLeast(0L))
            player.prepare()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Log only categories; signed URLs and authorization headers stay private.
            val reason = (error as? com.metrolist.innertubex.extraction.StreamResolveException)?.reason?.name.orEmpty()
            android.util.Log.w("SonetVideo", "Video resolve failed: " + error.javaClass.simpleName + " " + reason)
            failed = true
            latestUnavailable()
        }
    }
    LaunchedEffect(player, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousPosition = -1L
            while (isActive) {
                if (!failed) {
                    val target = latestPosition().coerceAtLeast(0L)
                    // Don't repeatedly seek a buffering decoder; that starves its first frame.
                    videoSeekTarget(player.currentPosition, target, player.playbackState == Player.STATE_READY)
                        ?.let(player::seekTo)
                    val speed = latestSpeed().takeIf { it.isFinite() && it > 0f } ?: 1f
                    if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
                    player.playWhenReady = latestPlaying()
                    val current = player.currentPosition
                    advancing = rendered && player.isPlaying && previousPosition >= 0L && current > previousPosition
                    previousPosition = current
                }
                delay(350)
            }
        }
    }
    val status = if (failed) "Video unavailable" else if (advancing) "Video playing" else if (rendered) "Video ready" else "Video loading"
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(.5f, 2.4f))
            .clip(RoundedCornerShape(20.dp)).background(Color.Black)
            .semantics { contentDescription = status }) {
            AndroidView(
                factory = { TextureView(it) },
                update = { player.setVideoTextureView(it) },
                onRelease = { player.clearVideoTextureView(it) },
                modifier = Modifier.fillMaxSize(),
            )
            if (!rendered || failed) {
                AsyncImage(artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                    if (failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.onyx_video_unavailable), color = Color.White)
                            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry), color = Color.White) }
                        }
                    } else SonetLoadingIndicator()
                }
            }
        }
    }
}
