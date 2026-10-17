package com.metrolist.music.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
        reveal.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        veil.animateTo(0f, tween(260))
        finished = true
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }.background(MidnightCanvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = reveal.value
                translationY = (1f - reveal.value) * 16.dp.toPx()
                scaleX = .96f + .04f * reveal.value
                scaleY = scaleX
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(painterResource(R.drawable.sonet_mark), null, Modifier.size(64.dp), tint = FrostGlow)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.merge(TextStyle(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp,
                    brush = Brush.linearGradient(listOf(Color(0xFFE59CFF), Color(0xFFBA9CFF), Color(0xFF9CB2FF))),
                )),
            )
        }
    }
}
