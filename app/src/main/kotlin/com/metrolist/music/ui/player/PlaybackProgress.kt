package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.PlayerHorizontalPadding
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.utils.makeTimeString

// Read the playback clock here so the cover, menus and controls do not subscribe to every tick.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackProgress(
    positionProvider: () -> Long,
    durationState: LongState,
    previewState: MutableState<Long?>,
    style: SliderStyle,
    squiggly: Boolean,
    isPlaying: Boolean,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    val duration = durationState.longValue
    val position = timelinePosition(previewState.value ?: positionProvider(), duration)
    val range = 0f..timelineDuration(duration).toFloat()
    val canSeek = enabled && duration > 0
    val colors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.onSurface,
        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        thumbColor = MaterialTheme.colorScheme.onSurface,
    )
    val onChange: (Float) -> Unit = { if (canSeek) previewState.value = timelinePosition(it.toLong(), duration) }
    val onFinish: () -> Unit = {
        if (canSeek) previewState.value?.let { onSeek(timelinePosition(it, durationState.longValue)) }
        previewState.value = null
    }
    Column(Modifier.padding(horizontal = PlayerHorizontalPadding)) {
        when {
            style == SliderStyle.WAVY && squiggly -> SquigglySlider(
                value = position.toFloat(),
                valueRange = range,
                onValueChange = onChange,
                onValueChangeFinished = onFinish,
                colors = colors,
                enabled = canSeek,
                isPlaying = isPlaying,
            )
            style == SliderStyle.WAVY -> WavySlider(
                value = position.toFloat(),
                valueRange = range,
                onValueChange = onChange,
                onValueChangeFinished = onFinish,
                colors = colors,
                enabled = canSeek,
                isPlaying = isPlaying,
            )
            else -> Slider(
                value = position.toFloat(),
                valueRange = range,
                onValueChange = onChange,
                onValueChangeFinished = onFinish,
                enabled = canSeek,
                colors = colors,
                thumb = {
                    Box(Modifier.size(if (style == SliderStyle.SLIM) 0.dp else 10.dp)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape))
                },
                track = { PlayerSliderTrack(it, colors = colors, trackHeight = 4.dp) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(makeTimeString(position), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (duration > 0) makeTimeString(duration) else "–:––",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
