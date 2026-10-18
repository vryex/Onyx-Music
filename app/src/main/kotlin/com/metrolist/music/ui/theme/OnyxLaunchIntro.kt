package com.metrolist.music.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

@Composable
fun OnyxLaunchIntro() {
    var finished by rememberSaveable { mutableStateOf(false) }
    if (finished) return
    val reveal = remember { Animatable(0f) }
    val veil = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        veil.animateTo(0f, tween(260))
        finished = true
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val progress = reveal.value
            val center = Offset(size.width / 2f, size.height / 2f - 42.dp.toPx())
            for (index in 0..2) {
                val wave = (progress - index * .13f).coerceAtLeast(0f)
                drawCircle(
                    color = Color.White.copy(alpha = (1f - wave) * .16f * progress),
                    radius = 28.dp.toPx() + size.maxDimension * .62f * wave,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            val width = size.width * .34f * progress
            for (index in 0..18) {
                val x = center.x - width / 2f + width * index / 18f
                val envelope = sin(index / 18f * kotlin.math.PI).toFloat()
                val height = (4f + 15f * envelope *
                    kotlin.math.abs(sin(index * .84f + progress * 5f))) * density * progress
                drawLine(Color.White.copy(alpha = .30f * progress),
                    Offset(x, center.y + 126.dp.toPx() - height / 2f),
                    Offset(x, center.y + 126.dp.toPx() + height / 2f),
                    strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Column(
            Modifier.graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 16.dp.toPx()
                scaleX = .88f + .12f * reveal.value
                scaleY = scaleX
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(painterResource(R.drawable.sonet_mark), null,
                Modifier.size(64.dp).graphicsLayer { rotationZ = (1f - reveal.value) * -14f },
                tint = Color.White)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.merge(TextStyle(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp,
                    color = Color.White,
                )),
            )
        }
    }
}
