/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.metrolist.music.ui.component.LiquidIconButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.metrolist.music.ui.theme.onyxGlass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import com.metrolist.music.ui.theme.rememberArtworkColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.ShowArtistDescriptionKey
import com.metrolist.music.constants.ShowArtistSubscriberCountKey
import com.metrolist.music.constants.ShowMonthlyListenersKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.Queue
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LinkSegment
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.ButtonPlaceholder
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.isScrollingUp
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val artistNameAliases = LocalArtistNameAliases.current
    val displayArtistName =
        ArtistNameAliases.resolve(
            artistNameAliases,
            viewModel.artistId,
            artistPage?.artist?.title ?: libraryArtist?.artist?.name.orEmpty(),
        ).ifEmpty { null }
    // Every queue launched from the artist page keeps its source label,
    // including individual songs, radio, shuffle and play-all.
    fun playArtistQueue(queue: Queue) {
        playerConnection.playQueue(queue, source = displayArtistName)
    }
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val isChannelSubscribed by viewModel.isChannelSubscribed.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val showArtistDescription by rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val showArtistSubscriberCount by rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val showMonthlyListeners by rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    // Sample a 224px version only once per artist using Onyx's existing LRU palette cache.
    // No bitmap blur or per-frame color extraction is introduced.
    val artistArtwork = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
    val artistColor by rememberArtworkColor(artistArtwork?.resize(224, 224))
    val artistGradient = remember(artistColor) {
        Brush.verticalGradient(
            0f to artistColor,
            0.36f to artistColor.copy(alpha = 0.65f),
            0.78f to Color.Black,
            1f to Color.Black,
        )
    }

    val distinctItemsBySection = remember(artistPage?.sections) {
        artistPage?.sections?.map { section ->
            section.items.distinctBy { it.id }
        } ?: emptyList()
    }

    LaunchedEffect(libraryArtist) {
        // always show local page for local artists. Show local page remote artist when offline
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .semantics { testTagsAsResourceId = true }.testTag("onyx_artist"),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            if (artistPage == null && !showLocal) {
                item(key = "shimmer") {
                    ShimmerHost {
                        Column(Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            TextPlaceholder(height = 36.dp, modifier = Modifier.fillMaxWidth(.65f))
                            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ButtonPlaceholder(Modifier.weight(1f).height(48.dp))
                                ButtonPlaceholder(Modifier.width(48.dp).height(48.dp))
                            }
                            repeat(4) { ListItemPlaceHolder() }
                        }
                    }
                }
            } else {
                item(key = "header") {
                    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
                    Box(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(460.dp).background(artistGradient)) {
                            if (thumbnail != null) AsyncImage(
                                thumbnail.resize(1080, 1080), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = .2f), .3f to Color.Transparent,
                                .48f to Color.Black.copy(alpha = .25f),
                                .68f to Color.Black.copy(alpha = .68f),
                                .88f to Color.Black, 1f to Color.Black,
                            )))
                        }
                    Column(Modifier.fillMaxWidth().windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
                        .padding(start = 20.dp, end = 20.dp, top = 240.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.artists),
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .25f), RoundedCornerShape(32.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(displayArtistName ?: stringResource(R.string.widget_recognizer_unknown_artist),
                            Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Medium, fontSize = 36.sp,
                            maxLines = 3, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                        // Remove only the outlined glass frame; keep the exact padding and control positions.
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.toggleChannelSubscription() },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isChannelSubscribed) MaterialTheme.colorScheme.surfaceContainerHigh
                                            else MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text(stringResource(if (isChannelSubscribed) R.string.subscribed else R.string.subscribe),
                                        style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                if (!showLocal && !isGuest) {
                                    artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                        LiquidIconButton(R.drawable.radio, stringResource(R.string.radio),
                                            { playArtistQueue(YouTubeQueue(radioEndpoint)) })
                                    }
                                    artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                        LiquidIconButton(R.drawable.shuffle, stringResource(R.string.shuffle),
                                            { playArtistQueue(YouTubeQueue(shuffleEndpoint)) }, emphasized = true)
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // Show loading shimmer for sections when API hasn't returned yet
                if (artistPage == null && !showLocal) {
                    item(key = "section_shimmer") {
                        ShimmerHost {
                            repeat(4) { ListItemPlaceHolder() }
                        }
                    }
                }

                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            NavigationTitle(
                                title = stringResource(R.string.songs),
                                modifier = Modifier,
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                            )
                        }

                        val filteredLibrarySongs =
                            if (hideExplicit) {
                                librarySongs.filter { !it.song.explicit }
                            } else {
                                librarySongs
                            }
                        itemsIndexed(
                            items = filteredLibrarySongs,
                            key = { index, item -> "local_song_${item.id}_$index" },
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (!isGuest) {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playArtistQueue(
                                                            ListQueue(
                                                                title = displayArtistName ?: "Unknown Artist",
                                                                items = librarySongs.map { it.toMediaItem() },
                                                                startIndex = index,
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            NavigationTitle(
                                title = stringResource(R.string.albums),
                                modifier = Modifier,
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                },
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums =
                                if (hideExplicit) {
                                    libraryAlbums.filter { !it.album.explicit }
                                } else {
                                    libraryAlbums
                                }
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { "local_album_${it.id}_${filteredLibraryAlbums.indexOf(it)}" },
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        navController.navigate("album/${album.id}")
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = album,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    },
                                                ),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    artistPage?.sections?.forEachIndexed { index, section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "section_${section.title}") {
                                NavigationTitle(
                                    title = section.title,
                                    modifier = Modifier,
                                    onClick =
                                        section.moreEndpoint?.let {
                                            {
                                                navController.navigate(
                                                    "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                                )
                                            }
                                        },
                                )
                            }
                        }

                        if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            items(
                                items = distinctItemsBySection.getOrNull(index) ?: section.items,
                                key = { "youtube_song_${it.id}" },
                            ) { song ->
                                YouTubeListItem(
                                    item = song as SongItem,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    if (!isGuest) {
                                                        if (song.id == mediaMetadata?.id) {
                                                            playerConnection.togglePlayPause()
                                                        } else {
                                                            playArtistQueue(
                                                                YouTubeQueue(
                                                                    WatchEndpoint(videoId = song.id),
                                                                    song.toMediaMetadata(),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ),
                                )
                            }
                        } else {
                            item(key = "section_list_${section.title}") {
                                LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(
                                        items = distinctItemsBySection.getOrNull(index) ?: section.items,
                                        key = { "youtube_album_${it.id}" },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive =
                                                when (item) {
                                                    is SongItem -> mediaMetadata?.id == item.id
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            thumbnailRatio = 1f, // Use square thumbnails for all items in horizontal scroll
                                            modifier =
                                                Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is SongItem -> {
                                                                    if (!isGuest) {
                                                                        playArtistQueue(
                                                                            YouTubeQueue(
                                                                                WatchEndpoint(videoId = item.id),
                                                                                item.toMediaMetadata(),
                                                                            ),
                                                                        )
                                                                    }
                                                                }

                                                                is AlbumItem -> {
                                                                    navController.navigate("album/${item.id}")
                                                                }

                                                                is ArtistItem -> {
                                                                    navController.navigate("artist/${item.id}")
                                                                }

                                                                is PlaylistItem -> {
                                                                    navController.navigate("online_playlist/${item.id}")
                                                                }

                                                                is PodcastItem -> {
                                                                    navController.navigate("online_podcast/${item.id}")
                                                                }

                                                                is EpisodeItem -> {
                                                                    if (!isGuest) {
                                                                        playArtistQueue(
                                                                            YouTubeQueue(
                                                                                WatchEndpoint(videoId = item.id),
                                                                                item.toMediaMetadata(),
                                                                            ),
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                when (item) {
                                                                    is SongItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is AlbumItem -> {
                                                                        YouTubeAlbumMenu(
                                                                            albumItem = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is ArtistItem -> {
                                                                        YouTubeArtistMenu(
                                                                            artist = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PlaylistItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item,
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PodcastItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item.asPlaylistItem(),
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is EpisodeItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item.asSongItem(),
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // About Artist Section
                if (!showLocal && (showArtistDescription || showArtistSubscriberCount || showMonthlyListeners)) {
                    val description = artistPage?.description
                    val descriptionRuns = artistPage?.descriptionRuns
                    val subscriberCount = artistPage?.subscriberCountText
                    val monthlyListeners = artistPage?.monthlyListenerCount

                    if ((showArtistDescription && !description.isNullOrEmpty()) ||
                        (showArtistSubscriberCount && !subscriberCount.isNullOrEmpty()) ||
                        (showMonthlyListeners && !monthlyListeners.isNullOrEmpty())
                    ) {
                        item(key = "about_artist") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 20.dp)
                                        .onyxGlass(RoundedCornerShape(16.dp))
                                        .padding(20.dp),
                            ) {
                                if (showArtistDescription && (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty())) {
                                    Text(
                                        text = stringResource(R.string.about_artist),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }

                                if (showArtistSubscriberCount && !subscriberCount.isNullOrEmpty()) {
                                    Text(
                                        text = subscriberCount,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }

                                if (showMonthlyListeners && !monthlyListeners.isNullOrEmpty()) {
                                    Text(
                                        text = monthlyListeners,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                            Modifier.padding(
                                                bottom =
                                                    if (showArtistDescription &&
                                                        !description.isNullOrEmpty()
                                                    ) {
                                                        8.dp
                                                    } else {
                                                        0.dp
                                                    },
                                            ),
                                    )
                                }

                                if (showArtistDescription && (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty())) {
                                    ExpandableText(
                                        text = description.orEmpty(),
                                        runs =
                                            descriptionRuns?.map {
                                                LinkSegment(
                                                    text = it.text,
                                                    url = it.navigationEndpoint?.urlEndpoint?.url,
                                                )
                                            },
                                        collapsedMaxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }

        val isScrollingUp = lazyListState.isScrollingUp()
        val showLocalFab = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true

        // Library/Local Toggle FAB
        HideOnScrollFAB(
            visible = showLocalFab,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            },
        )

        // Play All FAB (Stacked above Library/Local FAB if visible)
        val canPlayAll =
            !isGuest && (
                (showLocal && librarySongs.isNotEmpty()) ||
                    (
                        !showLocal && artistPage?.sections?.any {
                            (it.items.firstOrNull() as? SongItem)?.album != null
                        } == true
                    )
            )

        if (canPlayAll) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isScrollingUp,
                enter = androidx.compose.animation.slideInVertically { it * 2 },
                exit = androidx.compose.animation.slideOutVertically { it * 2 },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        )
                        // Add padding to position it above the other FAB (56dp height + 16dp padding + 8dp spacing)
                        // If the other FAB is visible.
                        .padding(bottom = if (showLocalFab) 64.dp else 0.dp),
            ) {
                val onPlayAllClick: () -> Unit = {
                    if (!isGuest) {
                        if (showLocal) {
                            if (librarySongs.isNotEmpty()) {
                                playArtistQueue(
                                    ListQueue(
                                        title = displayArtistName ?: "Unknown Artist",
                                        items = librarySongs.map { it.toMediaItem() },
                                    ),
                                )
                            }
                        } else if (artistPage != null) {
                            val songSection =
                                artistPage.sections.find { section ->
                                    (section.items.firstOrNull() as? SongItem)?.album != null
                                }

                            val moreEndpoint = songSection?.moreEndpoint
                            if (moreEndpoint != null) {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val result = YouTube.artistItems(moreEndpoint).getOrNull()
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (result != null && result.items.isNotEmpty()) {
                                            val songs = result.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                            playArtistQueue(
                                                ListQueue(
                                                    title = displayArtistName ?: artistPage.artist.title,
                                                    items = songs,
                                                ),
                                            )
                                        } else {
                                            // Fallback to loaded items
                                            val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                            if (songs.isNotEmpty()) {
                                                playArtistQueue(
                                                    ListQueue(
                                                        title = displayArtistName ?: artistPage.artist.title,
                                                        items = songs,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (songSection != null) {
                                // Use loaded items if no more endpoint
                                val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                playArtistQueue(
                                    ListQueue(
                                        title = displayArtistName ?: artistPage.artist.title,
                                        items = songs,
                                    ),
                                )
                            } else {
                                // Fallback to shuffle endpoint (stripped) if no song section found
                                val shuffleEndpoint = artistPage.artist.shuffleEndpoint
                                if (shuffleEndpoint != null) {
                                    val endpoint =
                                        if (shuffleEndpoint.playlistId != null) {
                                            WatchEndpoint(
                                                playlistId = shuffleEndpoint.playlistId,
                                                params = null, // Remove shuffle params to play in order
                                                videoId = null, // Ensure videoId is null to start from beginning of playlist
                                            )
                                        } else {
                                            shuffleEndpoint
                                        }
                                    playArtistQueue(YouTubeQueue(endpoint))
                                }
                            }
                        }
                    }
                }

                if (showLocalFab) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        modifier = Modifier.padding(16.dp).offset(x = (-4).dp), // Align center with standard FAB (56dp vs 48dp)
                        onClick = onPlayAllClick,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Play All",
                        )
                    }
                } else {
                    androidx.compose.material3.FloatingActionButton(
                        modifier = Modifier.padding(16.dp),
                        onClick = onPlayAllClick,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Play All",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .align(Alignment.BottomCenter),
        )
    }

    TopAppBar(
        title = { if (!transparentAppBar) Text(displayArtistName.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    viewModel.artistPage?.artist?.shareLink?.let { link ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Artist Link", link)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    painterResource(R.drawable.link),
                    contentDescription = null,
                )
            }
        },
        colors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            } else {
                TopAppBarDefaults.topAppBarColors()
            },
    )
}
