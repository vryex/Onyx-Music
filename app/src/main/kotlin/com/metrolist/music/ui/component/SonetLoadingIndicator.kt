package com.metrolist.music.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

@Composable
fun SonetLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "sonetLoading")
    val rotation by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "rotation")
    val fill by transition.animateFloat(.06f, 1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "fill")
    Box(modifier.size(44.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
        .graphicsLayer { rotationZ = rotation }) {
        Icon(painterResource(R.drawable.sonet_mark), null, tint = Color.White.copy(alpha = .18f),
            modifier = Modifier.matchParentSize())
        Icon(painterResource(R.drawable.sonet_mark), null, tint = color,
            modifier = Modifier.matchParentSize().drawWithContent {
                clipRect(top = size.height * (1f - fill)) { this@drawWithContent.drawContent() }
            })
    }
}
