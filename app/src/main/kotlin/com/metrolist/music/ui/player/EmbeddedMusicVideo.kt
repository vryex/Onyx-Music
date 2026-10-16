package com.metrolist.music.ui.player

import android.content.Intent
import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Created only on explicit video selection; the service remains the audio/transport owner. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedMusicVideo(
    videoId: String,
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
    var ready by remember(videoId) { mutableStateOf(false) }
    var playing by remember(videoId) { mutableStateOf(false) }
    var failed by remember(videoId) { mutableStateOf(false) }
    val origin = remember(context) { "https://${context.packageName}" }
    val script = remember(context) { context.assets.open("onyx_video.js").bufferedReader().use { it.readText() } }
    val webView = remember(videoId, context) {
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !failed) {
                        failed = true
                        view.post { latestUnavailable() }
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val host = request.url.host.orEmpty()
                    if (request.hasGesture() && request.url.scheme == "https" &&
                        (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (!message.sourceId().startsWith(origin)) return false
                    when {
                        message.message() == "ONYX_VIDEO_READY" -> ready = true
                        message.message() == "ONYX_VIDEO_PLAYING" -> playing = true
                        message.message() == "ONYX_VIDEO_WAITING" -> playing = false
                        message.message().startsWith("ONYX_VIDEO_ERROR:") -> if (!failed) {
                            failed = true
                            post { latestUnavailable() }
                        }
                    }
                    return true
                }
            }
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body,#player{width:100%;height:100%;margin:0;background:#000;overflow:hidden}</style></head><body><div id="player"></div><script>var ONYX_VIDEO_ID='$videoId';var ONYX_ORIGIN='$origin';$script</script></body></html>"""
            loadDataWithBaseURL("$origin/", html, "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { webView.evaluateJavascript("onyxPause()", null); webView.onPause() }
                Lifecycle.Event.ON_START -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }
    LaunchedEffect(webView, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var loadingTicks = 0
            while (isActive && !failed) {
                if (!ready && ++loadingTicks > 40) {
                    failed = true
                    latestUnavailable()
                    break
                }
                val seconds = latestPosition().coerceAtLeast(0L) / 1000.0
                val speed = latestSpeed().takeIf { it.isFinite() && it > 0f } ?: 1f
                webView.evaluateJavascript("onyxSync($seconds,${latestPlaying()},$speed)", null)
                delay(500)
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        key(videoId) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp)).background(Color.Black)
                .semantics { contentDescription = if (playing) "Video playing" else if (ready) "Video ready" else "Video loading" })
        }
    }
}
