package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val state = rememberSliderState(value = value)
    state.value = value
    val colors = SliderDefaults.colors(activeTrackColor = accentColor,
        inactiveTrackColor = Color.White.copy(alpha = .18f), thumbColor = accentColor)
    Slider(state = state, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, onValueChangeFinished = onValueChangeFinished, colors = colors,
        thumb = { Box(Modifier.size(12.dp).background(accentColor, CircleShape)) },
        track = { PlayerSliderTrack(it, colors = colors, trackHeight = 5.dp) })
}
