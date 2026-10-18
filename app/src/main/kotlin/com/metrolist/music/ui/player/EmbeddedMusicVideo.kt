package com.metrolist.music.ui.player

import android.view.TextureView
import android.graphics.Matrix
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Stable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.component.SonetLoadingIndicator
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Stable
class MusicVideoSession(val player: ExoPlayer) {
    var rendered by mutableStateOf(false)
    var advancing by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var ratio by mutableStateOf(16f / 9f)
}

/** Owned by the sheet, independently of its visible video surface. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberMusicVideoSession(
    videoId: String,
    positionProvider: () -> Long,
    playingProvider: () -> Boolean,
    visibleProvider: () -> Boolean,
    speedProvider: () -> Float,
    onUnavailable: () -> Unit,
    attempt: Int,
): MusicVideoSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPosition by rememberUpdatedState(positionProvider)
    val latestPlaying by rememberUpdatedState(playingProvider)
    val latestVisible by rememberUpdatedState(visibleProvider)
    val latestSpeed by rememberUpdatedState(speedProvider)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    val player = remember(context, videoId, attempt) {
        ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 12_000, 500, 1_000).build())
            .build().apply {
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setMaxVideoSize(1920, 1920).build()
            }
    }
    val session = remember(player) { MusicVideoSession(player) }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { session.rendered = true }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    session.ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("SonetVideo", "Video decoder failed: " + error.errorCodeName)
                session.failed = true
                session.advancing = false
                latestUnavailable()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                session.advancing = false
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
            val source = DefaultMediaSourceFactory(videoFactory).createMediaSource(
                MediaItem.Builder().setUri(requireNotNull(stream.videoUrl))
                    .setMimeType((stream.videoMimeType ?: stream.mimeType)?.substringBefore(';')).build(),
            )
            player.setMediaSource(source, latestPosition().coerceAtLeast(0L))
            player.prepare()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            // The extractor may invalidate its session without cancelling this UI job.
            android.util.Log.w("SonetVideo", "Video extraction session invalidated")
            session.failed = true
            latestUnavailable()
        } catch (error: Exception) {
            // Log only categories; signed URLs and authorization headers stay private.
            val reason = (error as? com.metrolist.innertubex.extraction.StreamResolveException)?.reason?.name.orEmpty()
            android.util.Log.w("SonetVideo", "Video resolve failed: " + error.javaClass.simpleName + " " + reason)
            session.failed = true
            latestUnavailable()
        }
    }
    LaunchedEffect(player, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousPosition = -1L
            while (isActive) {
                if (!latestVisible()) {
                    player.pause()
                    session.advancing = false
                    previousPosition = -1L
                } else if (!session.failed) {
                    val target = latestPosition().coerceAtLeast(0L)
                    // Don't repeatedly seek a buffering decoder; that starves its first frame.
                    videoSeekTarget(player.currentPosition, target, player.playbackState == Player.STATE_READY)
                        ?.let(player::seekTo)
                    val speed = latestSpeed().takeIf { it.isFinite() && it > 0f } ?: 1f
                    if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
                    player.playWhenReady = latestPlaying()
                    val current = player.currentPosition
                    session.advancing = session.rendered && player.isPlaying && previousPosition >= 0L && current > previousPosition
                    previousPosition = current
                }
                delay(350)
            }
        }
    }
    return session
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EmbeddedMusicVideo(
    session: MusicVideoSession,
    artworkUrl: String?,
    immersive: Boolean = false,
    modifier: Modifier = Modifier,
    onTogglePresentation: () -> Unit,
    onRetry: () -> Unit,
) {
    val status = if (session.failed) "Video unavailable" else if (session.advancing) "Video playing" else if (session.rendered) "Video ready" else "Video loading"
    Box(modifier.clipToBounds().clickable(onClick = onTogglePresentation)
        .semantics { contentDescription = status + if (immersive) ", Video immersive" else "" },
        contentAlignment = Alignment.Center) {
        fun fitSurface(view: TextureView) {
            if (view.width <= 0 || view.height <= 0) return
            val viewRatio = view.width.toFloat() / view.height
            val ratio = session.ratio.coerceAtLeast(.01f)
            val sx = if (immersive) maxOf(1f, ratio / viewRatio) else minOf(1f, ratio / viewRatio)
            val sy = if (immersive) maxOf(1f, viewRatio / ratio) else minOf(1f, viewRatio / ratio)
            view.setTransform(Matrix().apply { setScale(sx, sy, view.width / 2f, view.height / 2f) })
        }
        AndroidView(
            factory = { context -> TextureView(context).apply {
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> fitSurface(view as TextureView) }
            } },
            update = { view ->
                fitSurface(view)
                session.player.setVideoTextureView(view)
            },
            onRelease = { view -> session.player.clearVideoTextureView(view) },
            modifier = Modifier.fillMaxSize(),
        )
        if (!session.rendered || session.failed) {
            AsyncImage(artworkUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                if (session.failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.onyx_video_unavailable), color = Color.White)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = Color.White) }
                    }
                } else SonetLoadingIndicator()
            }
        }
        if (immersive) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = .30f), .20f to Color.Transparent,
            .55f to Color.Transparent, .85f to Color.Black.copy(alpha = .80f), 1f to Color.Black,
        )))
    }
}
