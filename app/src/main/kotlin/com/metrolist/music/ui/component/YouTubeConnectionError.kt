package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

@Composable
fun YouTubeConnectionError(detail: String, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag("sonet_youtube_error")) {
        Text(stringResource(R.string.youtube_page_connection_error))
        Text(stringResource(R.string.youtube_connection_detail, detail))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}
