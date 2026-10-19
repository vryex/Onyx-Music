package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.R
import com.metrolist.music.ui.utils.resize

@Composable
fun SpeedDialGridItem(item: YTItem, isPinned: Boolean, modifier: Modifier = Modifier,
    isActive: Boolean = false, isPlaying: Boolean = false) {
    Row(modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color(0xFF282828)),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp).fillMaxHeight()) {
            ItemThumbnail(item.thumbnail?.resize(200, 200), isActive, isPlaying,
                if (item is ArtistItem) CircleShape else RoundedCornerShape(4.dp), Modifier.fillMaxSize())
        }
        Text(item.title, Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (isPinned) Icon(painterResource(R.drawable.ic_push_pin), null,
            Modifier.padding(end = 6.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
