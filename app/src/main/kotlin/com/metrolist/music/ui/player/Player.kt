/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import com.metrolist.music.ui.component.LiquidIconButton
import com.metrolist.music.ui.component.OnyxPlayerHeader
import com.metrolist.music.ui.theme.SonetLyricsBackdrop
import com.metrolist.music.ui.theme.rememberArtworkColor
import com.metrolist.music.ui.utils.resize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithCache
import com.metrolist.music.ui.theme.onyxGlass
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.metrolist.music.ui.component.SonetLoadingIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import com.metrolist.music.LocalNavController
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CropAlbumArtKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.HidePlayerThumbnailKey
import com.metrolist.music.constants.HideStatusBarOnFullscreenKey
import com.metrolist.music.constants.KeepScreenOn
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.PlayerButtonsStyle
import com.metrolist.music.constants.PlayerButtonsStyleKey
import com.metrolist.music.constants.PlayerHorizontalPadding
import com.metrolist.music.constants.QueuePeekHeight
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.SquigglySliderKey
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.Lyrics
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.ResizableIconButton
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.theme.PlayerColorExtractor
import com.metrolist.music.ui.theme.PlayerSliderColors
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.ui.utils.ShowOffsetDialog
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.metrolist.music.ui.component.Icon as MIcon


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val sleepTimerDefaultSetTemplate = stringResource(R.string.sleep_timer_default_set)
    val copiedTitleStr = stringResource(R.string.copied_title)
    val copiedArtistStr = stringResource(R.string.copied_artist)
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(HidePlayerThumbnailKey, false)
    val (hideStatusBarOnFullscreen) = rememberPreference(HideStatusBarOnFullscreenKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)

    var showInlineLyrics by rememberSaveable {
        mutableStateOf(false)
    }

    var isFullScreen by rememberSaveable {
        mutableStateOf(false)
    }


    val playerBackground = PlayerBackgroundStyle.GRADIENT
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT,
    )

    val useDarkTheme = true

    val shouldUseDarkButtonColors =
        remember(playerBackground, useDarkTheme) {
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> true
                PlayerBackgroundStyle.DEFAULT -> useDarkTheme
            }
        }

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val isKeepScreenOn by rememberPreference(KeepScreenOn, false)
    val keepScreenOn = isPlaying && isKeepScreenOn

    DisposableEffect(playerBackground, state.isExpanded, useDarkTheme, keepScreenOn, isFullScreen, hideStatusBarOnFullscreen) {
        val window = (context as? android.app.Activity)?.window
        if (window != null && state.isExpanded) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                    insetsController.isAppearanceLightStatusBars = false
                }

                PlayerBackgroundStyle.DEFAULT -> {
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            if (isFullScreen && hideStatusBarOnFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }

            if (keepScreenOn && state.isExpanded) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler(enabled = state.isExpanded) {
        state.collapseSoft()
    }

    val onBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurface
        }
    val useBlackBackground = false

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playerQueueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val playbackSource by playerConnection.playbackSource.collectAsStateWithLifecycle()
    val displayedSource = (playbackSource ?: playerQueueTitle)?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals(mediaMetadata?.title, ignoreCase = true) &&
            !it.equals("radio", ignoreCase = true)
    }
    val artworkColor by rememberArtworkColor(mediaMetadata?.thumbnailUrl)
    // Keep the small cover painter alive even when the player is collapsed.
    // This exact URL is already used by the mini player and can be drawn on the
    // first expanded frame while its sharper request finishes.
    val instantArtworkPainter = rememberAsyncImagePainter(
        model = mediaMetadata?.thumbnailUrl?.resize(120, 120),
    )
    var showVideo by rememberSaveable { mutableStateOf(false) }
    var videoUnavailable by remember(mediaMetadata?.id) { mutableStateOf(false) }
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val squigglySlider by rememberPreference(SquigglySliderKey, defaultValue = false)

    // Listen Together state (reactive)
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    // Cast state - safely access castConnectionHandler to prevent crashes during service lifecycle changes
    val castHandler =
        remember(playerConnection) {
            try {
                playerConnection.service.castConnectionHandler
            } catch (e: Exception) {
                null
            }
        }
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val canShowVideo = !videoUnavailable && !isCasting && !isListenTogetherGuest &&
        (mediaMetadata?.isVideoSong == true || currentSong?.song?.isVideo == true) &&
        mediaMetadata?.id?.matches(Regex("[A-Za-z0-9_-]{11}")) == true
    val onVideoUnavailable: () -> Unit = {
        videoUnavailable = true
        Toast.makeText(context, R.string.onyx_video_unavailable, Toast.LENGTH_SHORT).show()
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if focus request fails
            }
        }
    }

    // Use Cast state when casting, otherwise local player
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    // Use State objects for position/duration to pass to MiniPlayer without causing recomposition
    // These states persist across playback state changes to ensure continuous progress updates.
    // Seed from the player's current values so re-entering composition on resume shows the real
    // time immediately instead of flashing 0:00 until the first poll fires. runCatching guards the
    // player-not-ready race; the poll loop corrects duration if it isn't known yet.
    val positionState = remember(playerConnection) { mutableLongStateOf(runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)) }
    val durationState = remember(playerConnection) {
        mutableLongStateOf(
            (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: runCatching { playerConnection.player.duration }.getOrDefault(0L).coerceAtLeast(0L),
        )
    }

    // Convenience accessors for local use
    var position by positionState
    var duration by durationState

    val effectivePosition by remember {
        derivedStateOf {
            if (isCasting) {
                castPosition
            } else {
                position
            }
        }
    }

    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    // Track when we last manually set position to avoid Cast overwriting it
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }

    val TextBackgroundColor by animateColorAsState(
        targetValue =
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
                PlayerBackgroundStyle.BLUR -> Color.White
                PlayerBackgroundStyle.GRADIENT -> Color.White
            },
        label = "TextBackgroundColor",
    )

    val icBackgroundColor by animateColorAsState(
        targetValue =
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
                PlayerBackgroundStyle.BLUR -> Color.Black
                PlayerBackgroundStyle.GRADIENT -> Color.Black
            },
        label = "icBackgroundColor",
    )

    val (textButtonColor, iconButtonColor) =
        when {
            playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.GRADIENT -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        Pair(Color.White, Color.Black)
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }

            else -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        if (useDarkTheme) {
                            Pair(Color.White, Color.Black)
                        } else {
                            Pair(Color.Black, Color.White)
                        }
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
            }
        }

    // Separate colors for Previous/Next buttons in PRIMARY/TERTIARY modes
    val (sideButtonContainerColor, sideButtonContentColor) =
        when {
            playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.GRADIENT -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        Pair(
                            Color.White.copy(alpha = 0.2f),
                            Color.White,
                        )
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            else -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> {
                        Pair(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    PlayerButtonsStyle.PRIMARY -> {
                        Pair(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    PlayerButtonsStyle.TERTIARY -> {
                        Pair(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

    val download by LocalDownloadUtil.current
        .getDownload(mediaMetadata?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = null)

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer?.triggerTime,
            playerConnection.service.sleepTimer?.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer?.isActive ?: false
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer?.pauseWhenSongEnd == true) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        (playerConnection.service.sleepTimer?.triggerTime ?: 0L) - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    val scope = rememberCoroutineScope()
    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    val sleepTimerDefault by rememberPreference(SleepTimerDefaultKey, 30f)
    var sleepTimerValue by remember { mutableFloatStateOf(sleepTimerDefault) }
    val isAtDefault by remember {
        derivedStateOf { sleepTimerValue.roundToInt() == sleepTimerDefault.roundToInt() }
    }
    LaunchedEffect(sleepTimerDefault) { sleepTimerValue = sleepTimerDefault }
    val sleepTimerStopAfterCurrentSong by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val sleepTimerFadeOut by rememberPreference(SleepTimerFadeOutKey, false)


    if (showSleepTimerDialog) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer?.start(
                            minute = sleepTimerValue.roundToInt(),
                            stopAfterCurrentSong = sleepTimerStopAfterCurrentSong,
                            fadeOut = sleepTimerFadeOut,
                        )
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.minute,
                                sleepTimerValue.roundToInt(),
                                sleepTimerValue.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isAtDefault) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        context.safeDataStoreEdit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        context.safeDataStoreEdit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showSleepTimerDialog = false
                                playerConnection.service.sleepTimer?.start(minute = -1)
                            },
                        ) {
                            Text(stringResource(R.string.end_of_song))
                        }
                    }
                }
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(playerConnection, isPlaying, isCasting, state.isExpanded, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!isCasting && isPlaying) {
                while (isActive) {
                    if (sliderPosition == null) {
                        position = playerConnection.player.currentPosition.coerceAtLeast(0L)
                        playerConnection.player.duration.takeIf { it > 0 }?.let { duration = it }
                    }
                    delay(if (state.isExpanded) 250L else 500L)
                }
            }
        }
    }

    // Paused seeks do not change playbackState, so refresh directly from the player event.
    DisposableEffect(playerConnection.player, isCasting) {
        val player = playerConnection.player
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int,
            ) {
                if (!isCasting && sliderPosition == null) {
                    position = newPosition.positionMs.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Also update position when playback state changes (e.g., song change, seek)
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            position = playerConnection.player.currentPosition
            // Prefer the song's known duration (from metadata, available instantly from the restored
            // queue) so the slider range is right even when restored paused / before the stream
            // resolves; fall back to the player's duration once it is known.
            duration = (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: playerConnection.player.duration.coerceAtLeast(0L)
        }
    }

    // Auto-switch from repeat one to repeat all when song ends naturally
    var previousMediaId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playbackState, mediaMetadata?.id) {
        val currentId = mediaMetadata?.id

        // Only switch from REPEAT_ONE to REPEAT_ALL when playback naturally ended
        // (i.e., the player transitioned to ENDED state and then moved to next track).
        // Do NOT switch on manual skips.
        if (currentId != null &&
            currentId != previousMediaId &&
            previousMediaId != null &&
            playbackState == Player.STATE_ENDED &&
            repeatMode == Player.REPEAT_MODE_ONE &&
            !isListenTogetherGuest) {
            playerConnection.player.setRepeatMode(Player.REPEAT_MODE_ALL)
        }

        previousMediaId = currentId
    }

    // When casting, use Cast position/duration directly
    // But wait a bit after manual seeks to let Cast catch up
    LaunchedEffect(isCasting, castPosition, castDuration) {
        if (isCasting && sliderPosition == null) {
            val timeSinceManualSeek = System.currentTimeMillis() - lastManualSeekTime
            if (timeSinceManualSeek > 1500) {
                // Only update from Cast if we haven't manually seeked recently
                position = castPosition
                if (castDuration > 0) duration = castDuration
            }
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = dismissedBound,
            expandedBound = state.expandedBound,
            collapsedBound = dismissedBound + 1.dp,
            initialAnchor = 1,
        )



    val lyricsMix = animateFloatAsState(if (showInlineLyrics) 1f else 0f, tween(320), label = "lyricsPaletteBlend")
    val lyricsBackdropActive by remember(state, showInlineLyrics) {
        derivedStateOf { state.progress > 0f && (showInlineLyrics || lyricsMix.value > 0f) }
    }
    val artworkPlacement = remember { PlayerArtworkPlacement() }
    var hasShownLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(showInlineLyrics) { if (showInlineLyrics) hasShownLyrics = true }
    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded && !hasShownLyrics) {
            // Prepare after the sheet settles, outside the cover-to-lyrics flight.
            delay(500)
            hasShownLyrics = true
        }
    }
    BottomSheet(
        state = state,
        backgroundFollowsContent = true,
        retainExpandedContent = hasShownLyrics || showInlineLyrics,
        modifier = modifier,
        background = {
            // One seamless background for the ENTIRE expanded sheet. Painting
            // the image here (instead of inside Thumbnail) lets it begin behind
            // the status bar and now-playing header with no horizontal seam.
            val darkTone = artworkColor.copy(
                red = artworkColor.red * 0.45f,
                green = artworkColor.green * 0.45f,
                blue = artworkColor.blue * 0.45f,
            )
            Box(
                modifier = Modifier.fillMaxSize().onGloballyPositioned(artworkPlacement::placeContainer).background(
                    Brush.verticalGradient(
                        0f to darkTone,
                        0.62f to darkTone,
                        1f to Color.Black,
                    )
                )
            ) {
                SonetLyricsBackdrop(
                    artwork = mediaMetadata?.thumbnailUrl,
                    active = lyricsBackdropActive,
                    modifier = Modifier.matchParentSize().graphicsLayer { alpha = lyricsMix.value },
                )
                if (
                    !mediaMetadata?.thumbnailUrl.isNullOrBlank() &&
                    LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
                ) {
                    PlayerArtwork(
                        url = mediaMetadata?.thumbnailUrl,
                        placeholder = instantArtworkPainter,
                        mix = lyricsMix,
                        placement = artworkPlacement,
                        visibleInCoverMode = !showVideo && !hidePlayerThumbnail,
                    )
                }
            }
        },
        onDismiss =
            if (!isListenTogetherGuest) {
                {
                    playerConnection.service.clearAutomix()
                    playerConnection.player.stop()
                    playerConnection.player.clearMediaItems()
                }
            } else {
                null
            },
        collapsedContent = {
            MiniPlayer(
                positionState = positionState,
                durationState = durationState,
                onClick = { state.expandSoft() },
            )
        },
    ) {
        val metadataContent: @Composable (MediaMetadata) -> Unit = { mediaMetadata ->
            // Only the horizontal cover slot animates. A global bounds animation
            // here also chased the parent's fullscreen resize, delaying the title
            // and separating it from its cover, slider and buttons.
            val coverInset = if (showInlineLyrics && !mediaMetadata.thumbnailUrl.isNullOrBlank()) 60.dp else 0.dp
            val coverSlotWidth = animateDpAsState(
                coverInset,
                tween(320), label = "metadataCoverSlot",
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    // A layout anchor only. The bitmap is drawn once in the background.
                    Box(Modifier.size(48.dp).onGloballyPositioned(artworkPlacement::placeTarget))
                } else {
                    AsyncImage(
                        model = mediaMetadata.thumbnailUrl?.resize(96, 96),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                            .graphicsLayer { alpha = lyricsMix.value },
                    )
                }
                Column(
                    Modifier.fillMaxWidth()
                        .layout { measurable, constraints ->
                            // Measure text once for its destination width; animate
                            // placement only, without relaying out text each frame.
                            val inset = coverInset.roundToPx()
                            val text = measurable.measure(constraints.offset(horizontal = -inset))
                            layout(text.width + inset, text.height) {
                                text.placeRelative(coverSlotWidth.value.roundToPx(), 0)
                            }
                        }
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        mediaMetadata.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                val album = mediaMetadata.album?.id ?: currentSong?.album?.id ?: currentSong?.song?.albumId
                                if (album != null) {
                                    navController.navigate("album/$album")
                                    state.collapseSoft()
                                }
                            },
                            onLongClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText(copiedTitleStr, mediaMetadata.title))
                                Toast.makeText(context, copiedTitleStr, Toast.LENGTH_SHORT).show()
                            },
                        ),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if (mediaMetadata.explicit) MIcon.Explicit()
                        mediaMetadata.artists.forEachIndexed { index, artist ->
                            Text(
                                artist.name + if (index < mediaMetadata.artists.lastIndex) ", " else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        artist.id?.takeIf { it.isNotBlank() }?.let {
                                            navController.navigate("artist/$it")
                                            state.collapseSoft()
                                        }
                                    },
                                    onLongClick = {
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText(copiedArtistStr, artist.name))
                                        Toast.makeText(context, copiedArtistStr, Toast.LENGTH_SHORT).show()
                                    },
                                ),
                            )
                        }
                    }
                }
                }
                val favorite = if (currentSong?.song?.isEpisode == true) currentSong?.song?.inLibrary != null else currentSong?.song?.liked == true
                val favoritePulse = remember { Animatable(1f) }
                val favoriteRotation = remember { Animatable(0f) }
                val feedback = LocalHapticFeedback.current
                val favoriteScope = rememberCoroutineScope()
                val toggleFavorite = {
                    playerConnection.toggleLike()
                    feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    favoriteScope.launch {
                        favoritePulse.snapTo(0.82f)
                        favoritePulse.animateTo(1f, spring(dampingRatio = .38f, stiffness = Spring.StiffnessMedium))
                    }
                    favoriteScope.launch {
                        favoriteRotation.snapTo(-12f)
                        favoriteRotation.animateTo(0f, spring(dampingRatio = .35f))
                    }
                    Unit
                }
                PlayerIconButton(
                    if (showInlineLyrics) R.drawable.fullscreen else if (favorite) R.drawable.favorite else R.drawable.favorite_border,
                    if (showInlineLyrics) stringResource(R.string.onyx_fullscreen) else stringResource(R.string.liked),
                    onClick = { if (showInlineLyrics) isFullScreen = !isFullScreen else toggleFavorite() },
                    modifier = Modifier.graphicsLayer {
                        scaleX = favoritePulse.value; scaleY = favoritePulse.value
                        rotationZ = favoriteRotation.value
                    }.semantics { selected = favorite },
                    contentColor = Color.White,
                )
                Spacer(Modifier.width(12.dp))
                if (showInlineLyrics) {
                    val lyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
                    PlayerIconButton(R.drawable.more_horiz, stringResource(R.string.onyx_lyrics_tools), onClick = {
                        menuState.show {
                            com.metrolist.music.ui.menu.LyricsMenu(
                                lyricsProvider = { lyrics }, songProvider = { currentSong?.song },
                                mediaMetadataProvider = { mediaMetadata }, onDismiss = menuState::dismiss,
                                onShowOffsetDialog = {
                                    bottomSheetPageState.show { ShowOffsetDialog(songProvider = { currentSong?.song }) }
                                },
                            )
                        }
                    })
                } else PlayerMoreMenuButton(mediaMetadata, state, textButtonColor, iconButtonColor)
            }
        }
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            when (sliderStyle) {
                SliderStyle.DEFAULT -> {
                    val sliderValue = (sliderPosition ?: effectivePosition).toFloat()
                    val sliderRange = 0f..duration.coerceAtLeast(0L).toFloat()
                    val sliderState = remember(sliderRange) {
                        SliderState(value = sliderValue, trackRange = sliderRange)
                    }
                    sliderState.value = sliderValue
                    val scrubSize by animateDpAsState(if (sliderPosition != null) 18.dp else 12.dp, tween(140), label = "scrubThumb")
                    val scrubTrackHeight by animateDpAsState(if (sliderPosition != null) 8.dp else 6.dp, tween(140), label = "scrubTrack")
                    Slider(
                        state = sliderState,
                        onValueChange = {
                            if (!isListenTogetherGuest) {
                                sliderPosition = it.toLong()
                            }
                        },
                        onValueChangeFinished = {
                            if (!isListenTogetherGuest) {
                                sliderPosition?.let {
                                    if (isCasting) {
                                        castHandler?.seekTo(it)
                                        lastManualSeekTime = System.currentTimeMillis()
                                    } else {
                                        playerConnection.player.seekTo(it)
                                    }
                                    position = it
                                }
                                sliderPosition = null
                            }
                        },
                        enabled = !isListenTogetherGuest,
                        thumb = {
                            Box(Modifier.size(scrubSize).shadow(4.dp, CircleShape).background(Color.White, CircleShape))
                        },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = PlayerSliderColors.getSliderColors(Color(0xFFF5F5F7), playerBackground, useDarkTheme),
                                trackHeight = scrubTrackHeight,
                                accentColor = artworkColor,
                                bufferedFraction = if (duration > 0L) playerConnection.player.bufferedPosition.toFloat() / duration else 0f,
                            )
                        },
                        colors = PlayerSliderColors.getSliderColors(Color(0xFFF5F5F7), playerBackground, useDarkTheme),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )
                }

                SliderStyle.WAVY -> {
                    if (squigglySlider) {
                        SquigglySlider(
                            value = (sliderPosition ?: effectivePosition).toFloat(),
                            valueRange = 0f..(duration.coerceAtLeast(0L).toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    if (isCasting) {
                                        castHandler?.seekTo(it)
                                        lastManualSeekTime = System.currentTimeMillis()
                                    } else {
                                        playerConnection.player.seekTo(it)
                                    }
                                    position = it
                                }
                                sliderPosition = null
                            },
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                            colors = PlayerSliderColors.getSliderColors(Color(0xFFF5F5F7), playerBackground, useDarkTheme),
                            isPlaying = effectiveIsPlaying,
                        )
                    } else {
                        WavySlider(
                            value = (sliderPosition ?: effectivePosition).toFloat(),
                            valueRange = 0f..(duration.coerceAtLeast(0L).toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    if (isCasting) {
                                        castHandler?.seekTo(it)
                                        lastManualSeekTime = System.currentTimeMillis()
                                    } else {
                                        playerConnection.player.seekTo(it)
                                    }
                                    position = it
                                }
                                sliderPosition = null
                            },
                            colors = PlayerSliderColors.getSliderColors(Color(0xFFF5F5F7), playerBackground, useDarkTheme),
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                            isPlaying = effectiveIsPlaying,
                        )
                    }
                }

                SliderStyle.SLIM -> {
                    val sliderValue = (sliderPosition ?: effectivePosition).toFloat()
                    val sliderRange = 0f..(duration.coerceAtLeast(0L).toFloat())
                    val sliderState = remember(sliderRange) {
                        SliderState(value = sliderValue, trackRange = sliderRange)
                    }
                    sliderState.value = sliderValue
                    Slider(
                        state = sliderState,
                        onValueChange = {
                            if (!isListenTogetherGuest) {
                                sliderPosition = it.toLong()
                            }
                        },
                        onValueChangeFinished = {
                            if (!isListenTogetherGuest) {
                                sliderPosition?.let {
                                    if (isCasting) {
                                        castHandler?.seekTo(it)
                                        lastManualSeekTime = System.currentTimeMillis()
                                    } else {
                                        playerConnection.player.seekTo(it)
                                    }
                                    position = it
                                }
                                sliderPosition = null
                            }
                        },
                        enabled = !isListenTogetherGuest,
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = PlayerSliderColors.getSliderColors(Color(0xFFF5F5F7), playerBackground, useDarkTheme),
                            )
                        },
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding + 4.dp),
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: effectivePosition),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA1A1A6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA1A1A6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedVisibility(
                visible = !isFullScreen,
                enter = fadeIn(tween(320)) + expandVertically(tween(320)),
                exit = fadeOut(tween(240)) + shrinkVertically(tween(320)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .06f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE)
                    PlayerIconButton(R.drawable.shuffle, stringResource(R.string.shuffle),
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                        enabled = !isListenTogetherGuest,
                        modifier = Modifier.alpha(if (shuffleEnabled) 1f else .45f).semantics { selected = shuffleEnabled })
                    PlayerIconButton(R.drawable.skip_previous, stringResource(R.string.onyx_previous_track), playerConnection::seekToPrevious,
                        enabled = canSkipPrevious && !isListenTogetherGuest)
                    PlayerIconButton(
                        if (isListenTogetherGuest) {
                            if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                        } else if (playbackState == STATE_ENDED) R.drawable.replay
                        else if (effectiveIsPlaying) R.drawable.pause else R.drawable.play,
                        if (isListenTogetherGuest) {
                            if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                        } else if (effectiveIsPlaying) stringResource(R.string.player_pause) else stringResource(R.string.play),
                        onClick = {
                            if (isListenTogetherGuest) playerConnection.toggleMute()
                            else if (isCasting) {
                                if (castIsPlaying) castHandler?.pause() else castHandler?.play()
                            } else if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else playerConnection.togglePlayPause()
                        },
                        modifier = Modifier.size(52.dp).focusRequester(focusRequester), emphasized = true,
                        containerColor = Color.White, contentColor = Color.Black,
                    )
                    PlayerIconButton(R.drawable.skip_next, stringResource(R.string.onyx_next_track), playerConnection::seekToNext,
                        enabled = canSkipNext && !isListenTogetherGuest)
                    if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE)
                    PlayerIconButton(if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                        stringResource(when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                            Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                            else -> R.string.repeat_mode_off
                        }), onClick = { playerConnection.player.toggleRepeatMode() }, enabled = !isListenTogetherGuest,
                        modifier = Modifier.alpha(if (repeatMode == Player.REPEAT_MODE_OFF) .45f else 1f)
                            .semantics { selected = repeatMode != Player.REPEAT_MODE_OFF })
                }
            }
            if (!isFullScreen && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    PlayerIconButton(R.drawable.shuffle, stringResource(R.string.shuffle),
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled }, enabled = !isListenTogetherGuest,
                        modifier = Modifier.alpha(if (shuffleEnabled) 1f else .45f))
                    if (canShowVideo) PlayerIconButton(R.drawable.slow_motion_video, stringResource(R.string.onyx_video),
                        onClick = { showVideo = !showVideo }, modifier = Modifier.semantics { selected = showVideo })
                    PlayerIconButton(if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                        stringResource(R.string.repeat_mode_all), onClick = { playerConnection.player.toggleRepeatMode() },
                        enabled = !isListenTogetherGuest, modifier = Modifier.alpha(if (repeatMode == Player.REPEAT_MODE_OFF) .45f else 1f))
                }
            }
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Calculate vertical padding like OuterTune
                val density = LocalDensity.current
                val verticalPadding =
                    max(
                        WindowInsets.systemBars.getTop(density),
                        WindowInsets.systemBars.getBottom(density),
                    )
                val verticalPaddingDp = with(density) { verticalPadding.toDp() }
                val verticalWindowInsets = WindowInsets(left = 0.dp, top = verticalPaddingDp, right = 0.dp, bottom = verticalPaddingDp)

                Row(
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).add(verticalWindowInsets),
                            ).padding(bottom = if (isFullScreen) 0.dp else queueSheetState.collapsedBound)
                            .fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                    ) {
                        // Remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(280)) },
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    playbackActive = !state.isCollapsed && !state.isDismissed,
                                    positionProvider = { effectivePosition },
                                )
                            } else if (showVideo && canShowVideo && state.isExpanded) {
                                EmbeddedMusicVideo(
                                    videoId = mediaMetadata!!.id,
                                    artworkUrl = mediaMetadata?.thumbnailUrl,
                                    positionProvider = { effectivePosition },
                                    playingProvider = { effectiveIsPlaying },
                                    speedProvider = { playerConnection.player.playbackParameters.speed },
                                    onUnavailable = onVideoUnavailable,
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier,
                                    isPlayerExpanded = isExpandedProvider,
                                    isLandscape = true,
                                    isListenTogetherGuest = isListenTogetherGuest,
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f, false)
                                .verticalScroll(rememberScrollState())
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    ) {
                        mediaMetadata?.let {
                            metadataContent(it)
                            controlsContent(it)
                        }

                    }
                }
            }

            else -> {
                val bottomPadding by animateDpAsState(
                    targetValue = if (isFullScreen)
                        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp
                        else queueSheetState.collapsedBound,
                    animationSpec = tween(320),
                    label = "bottomPadding",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = bottomPadding)
                            ,
                ) {
                    OnyxPlayerHeader(
                        onCollapse = { state.collapseSoft() },
                        onEqualizer = { com.metrolist.music.utils.openSystemEqualizer(context, playerConnection.player.audioSessionId) },
                        onVideo = if (canShowVideo) ({ showVideo = !showVideo }) else null,
                        videoSelected = showVideo && canShowVideo,
                        showCast = true,
                        playbackSource = displayedSource,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        // matchParentSize children don't contribute to Box measurement.
                        // Keep the pane wide even when Thumbnail leaves composition.
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        // Remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        if (showInlineLyrics || hasShownLyrics) {
                            InlineLyricsView(
                                mediaMetadata = mediaMetadata,
                                showLyrics = showInlineLyrics,
                                playbackActive = showInlineLyrics && !state.isCollapsed && !state.isDismissed,
                                positionProvider = { effectivePosition },
                                modifier = Modifier.matchParentSize()
                                    .graphicsLayer { alpha = lyricsMix.value }
                                    .then(if (!showInlineLyrics) Modifier.clearAndSetSemantics { } else Modifier),
                            )
                        }
                        if (!showInlineLyrics) {
                            if (showVideo && canShowVideo && state.isExpanded) {
                                EmbeddedMusicVideo(
                                    videoId = mediaMetadata!!.id,
                                    artworkUrl = mediaMetadata?.thumbnailUrl,
                                    positionProvider = { effectivePosition },
                                    playingProvider = { effectiveIsPlaying },
                                    speedProvider = { playerConnection.player.playbackParameters.speed },
                                    onUnavailable = onVideoUnavailable,
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                    isPlayerExpanded = isExpandedProvider,
                                    isListenTogetherGuest = isListenTogetherGuest,
                                    fullBleedArtwork = true,
                                )
                            }
                        }
                    }

                    mediaMetadata?.let {
                        metadataContent(it)
                        controlsContent(it)
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Queue owns a full-screen sheet; animate its alpha instead of
        // collapsing/re-measuring it while the lyrics are resizing.
        val queueReveal by animateFloatAsState(if (isFullScreen) 0f else 1f, tween(320), label = "queueReveal")
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = queueReveal
            translationY = (1f - queueReveal) * state.expandedBound.toPx()
        }) {
            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
                // Queue stays pure black; keep grey current-song/header controls intact.
                background = Color.Black,
                onBackgroundColor = onBackgroundColor,
                TextBackgroundColor = TextBackgroundColor,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                pureBlack = pureBlack,
                showInlineLyrics = showInlineLyrics,
                playerBackground = playerBackground,
                onSleepTimer = {
                    if (sleepTimerEnabled) playerConnection.service.sleepTimer?.clear()
                    else showSleepTimerDialog = true
                },
                timerLabel = if (sleepTimerEnabled) makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0L)) else null,
                onToggleLyrics = {
                    showInlineLyrics = !showInlineLyrics
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    playbackActive: Boolean = true,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle(initialValue = -1)
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current

    var appInForeground by remember {
        mutableStateOf(
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }
    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer =
            LifecycleEventObserver { _, _ ->
                appInForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val nextMetadata =
        remember(queueWindows, currentWindowIndex) {
            if (currentWindowIndex >= 0 && currentWindowIndex + 1 < queueWindows.size) {
                queueWindows[currentWindowIndex + 1].mediaItem.metadata
            } else {
                null
            }
        }

    LaunchedEffect(mediaMetadata?.id, currentLyrics, playbackActive) {
        if (playbackActive && mediaMetadata != null && currentLyrics == null) {
            delay(500)
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint =
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                        )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
            }
        }
    }

    // Prefetch lyrics for the next queue item only while the lyrics pane is visible, the app is in the
    // foreground, and the current track's lyrics row has finished loading (avoids competing with the
    // active fetch).
    LaunchedEffect(
        nextMetadata?.id,
        showLyrics,
        playbackActive,
        appInForeground,
        mediaMetadata?.id,
        currentLyrics,
    ) {
        if (!showLyrics || !playbackActive || !appInForeground || nextMetadata == null) return@LaunchedEffect
        val loadedForCurrent =
            currentLyrics?.let { lyrics ->
                mediaMetadata == null || lyrics.id == mediaMetadata.id
            } == true
        if (mediaMetadata != null && !loadedForCurrent) return@LaunchedEffect
        val nextId = nextMetadata.id
        delay(400)
        if (!showLyrics || !appInForeground || !isActive) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val existing = database.lyrics(nextId).first()
                if (existing != null) return@withContext
                val entryPoint =
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                    )
                val lyricsHelper = entryPoint.lyricsHelper()
                val fetched = lyricsHelper.getLyrics(nextMetadata)
                database.query {
                    upsert(LyricsEntity(nextId, fetched.lyrics, fetched.provider))
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            lyrics == null -> {
                if (playbackActive) SonetLoadingIndicator()
            }

            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                val lyricsContent: @Composable () -> Unit = {
                    Lyrics(
                        sliderPositionProvider = positionProvider,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        showLyrics = showLyrics,
                        playbackActive = playbackActive,
                    )
                }
                ProvideTextStyle(
                    value =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        ),
                ) {
                    lyricsContent()
                }
            }
        }
    }
}

@Composable
private fun PlayerMoreMenuButton(
    mediaMetadata: MediaMetadata,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(textButtonColor)
                .clickable {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = {
                                mediaMetadata.id.let {
                                    bottomSheetPageState.show {
                                        ShowMediaInfo(it)
                                    }
                                }
                            },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
    ) {
        Image(
            painter = painterResource(R.drawable.more_horiz),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconButtonColor),
        )
    }
}

@Composable
private fun PlayerIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
) = LiquidIconButton(icon, description, onClick, modifier, emphasized, enabled,
    containerColor, contentColor, plain = true)
