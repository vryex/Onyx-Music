package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R

@Composable
fun rememberCollectionCover(kind: String, playlistId: String? = null): String? {
    val database = LocalDatabase.current
    val cover by remember(database, kind, playlistId) {
        when (kind) {
            "top" -> database.topPlaylistCover()
            "playlist" -> database.firstPlaylistCover(playlistId.orEmpty())
            else -> database.autoPlaylistCover(kind)
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    return cover
}

@Composable
fun rememberPlaylistCover(name: String, id: String, autoPlaylist: Boolean): String? {
    val kind = when {
        !autoPlaylist -> "playlist"
        name == stringResource(R.string.liked) -> "liked"
        name == stringResource(R.string.offline) -> "downloaded"
        name == stringResource(R.string.cached_playlist) -> "cached"
        name == stringResource(R.string.uploaded_playlist) -> "uploaded"
        else -> "top"
    }
    return rememberCollectionCover(kind, if (autoPlaylist) null else id)
}
