package com.metrolist.music.ui.screens

import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.spotify.SpotifyMatchFailure
import com.metrolist.music.spotify.SpotifyFailure
import com.metrolist.music.viewmodels.SpotifyImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(navController: NavController, viewModel: SpotifyImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val likedCount by viewModel.likedCount.collectAsState()
    val context = LocalContext.current
    val acceptedMessage = stringResource(R.string.spotify_import_callback_success)
    val rejectedMessage = stringResource(R.string.spotify_import_callback_invalid)
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val localeTag = configuration.locales[0].toLanguageTag()
    LaunchedEffect(localeTag) { viewModel.setLocale(localeTag) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted
        viewModel.import()
    }
    val startImport: () -> Unit = {
        notificationDenied = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.import()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::chooseFile) }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false },
        title = { Text(stringResource(R.string.spotify_import_export_help)) },
        text = { Text(stringResource(R.string.spotify_import_export_steps)) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.spotify_import_help_done)) } })
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("sonet_spotify_import"), topBar = {
            TopAppBar(title = { Text(stringResource(R.string.spotify_import_title)) }, navigationIcon = {
                TextButton(onClick = { navController.navigateUp() }) { Text(stringResource(R.string.spotify_import_back)) }
            })
        }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.spotify_import_description)) }
            item { Text(stringResource(R.string.spotify_import_background_help)) }
            if (notificationDenied) item { Text(stringResource(R.string.spotify_import_notification_denied), modifier = Modifier.testTag("sonet_spotify_notification_denied")) }
            item { Button(modifier = Modifier.testTag("sonet_spotify_connect"), enabled = !state.busy, onClick = {
                viewModel.connect(acceptedMessage, rejectedMessage) { url ->
                    try { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
                    catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }) { Text(stringResource(R.string.spotify_import_connect)) } }
            item { Text(stringResource(R.string.spotify_import_csv_fallback)) }
            item { TextButton(modifier = Modifier.testTag("sonet_spotify_export_help"), onClick = { showHelp = true }) {
                Text(stringResource(R.string.spotify_import_export_help))
            } }
            item { TextButton(modifier = Modifier.testTag("sonet_spotify_choose_file"), enabled = !state.busy,
                onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/octet-stream", "*/*")) }) {
                Text(stringResource(R.string.spotify_import_choose_file))
            } }
            if (state.validSongs > 0) {
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.spotifySource) stringResource(R.string.spotify_import_spotify_library) else if (state.fileName.isBlank()) stringResource(R.string.spotify_import_selected_file) else state.fileName)
                    Text(stringResource(R.string.spotify_import_preview, state.validSongs, state.skippedRows))
                    state.preview.forEach { Text(it) }
                } }
                item { Button(modifier = Modifier.testTag(if (state.resumable) "sonet_spotify_resume" else "sonet_spotify_import_button"), enabled = !state.busy && state.pendingSongs != 0, onClick = startImport) {
                    Text(stringResource(if (state.resumable) R.string.spotify_import_resume else if (state.complete || state.cancelled || state.error != null) R.string.spotify_import_retry else R.string.spotify_import_start))
                } }
            }
            if (state.busy) item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.loading && state.progress.total > 0) {
                    LinearProgressIndicator(progress = { state.progress.processed.toFloat() / state.progress.total }, modifier = Modifier.fillMaxWidth())
                } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(when {
                    state.authorizing -> R.string.spotify_import_authorizing
                    state.connecting -> R.string.spotify_import_fetching
                    state.loading -> R.string.spotify_import_reading
                    else -> R.string.spotify_import_running
                }))
                if (state.connecting && !state.authorizing) Text(stringResource(R.string.spotify_import_fetch_progress, state.loaded, state.loadTotal))
                if (state.backgroundImport) TextButton(modifier = Modifier.testTag("sonet_spotify_pause"), onClick = viewModel::pause) {
                    Text(stringResource(R.string.spotify_import_pause))
                }
                TextButton(modifier = Modifier.testTag("sonet_spotify_cancel"), onClick = viewModel::cancel) { Text(stringResource(R.string.spotify_import_cancel)) }
            } }
            if (state.progress.total > 0) item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.spotify_import_progress, state.progress.processed, state.progress.total, state.progress.imported, state.progress.already, state.pendingSongs ?: state.progress.unmatched.size),
                    modifier = Modifier.testTag("sonet_spotify_progress"))
                if (state.progress.scanned > state.progress.processed) Text(
                    stringResource(R.string.spotify_import_scanned, state.progress.scanned, state.progress.total),
                    modifier = Modifier.testTag("sonet_spotify_scanned"))
            } }
            if (state.progress.imported + state.progress.already > 0) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.spotify_import_liked_count, likedCount))
                    TextButton(onClick = { navController.navigate("auto_playlist/liked") }) {
                        Text(stringResource(R.string.liked_songs))
                    }
                }
            }
            if (state.complete) item { Text(stringResource(if (state.pendingSongs == 0) R.string.spotify_import_complete else R.string.spotify_import_pending_complete)) }
            if (state.pendingSongs != null && state.pendingSongs!! > 0 && !state.busy) item {
                Text(stringResource(R.string.spotify_import_pending_count, state.pendingSongs!!))
            }
            if (state.resumable) item { Text(stringResource(R.string.spotify_import_paused)) }
            if (state.cancelled) item { Text(stringResource(R.string.spotify_import_cancelled)) }
            state.error?.let { error -> item { Text(stringResource(when (error) {
                SpotifyFailure.FILE_TOO_LARGE -> R.string.spotify_import_file_too_large
                SpotifyFailure.TOO_MANY_ROWS -> R.string.spotify_import_too_many_rows
                SpotifyFailure.INVALID_CSV -> R.string.spotify_import_invalid_csv
                SpotifyFailure.MISSING_COLUMNS -> R.string.spotify_import_missing_columns
                SpotifyFailure.EMPTY_FILE -> R.string.spotify_import_empty_file
                SpotifyFailure.FILE_READ -> R.string.spotify_import_file_read_error
                SpotifyFailure.NETWORK -> R.string.spotify_import_network
                SpotifyFailure.RESPONSE -> R.string.spotify_import_error
                SpotifyFailure.DENIED -> R.string.spotify_import_denied
                SpotifyFailure.AUTHORIZATION -> R.string.spotify_import_authorization_error
                SpotifyFailure.FORBIDDEN -> R.string.spotify_import_forbidden
                SpotifyFailure.RATE_LIMIT -> R.string.spotify_import_rate_limit
                SpotifyFailure.TIMEOUT -> R.string.spotify_import_timeout
                SpotifyFailure.PORT_BUSY -> R.string.spotify_import_port_busy
                SpotifyFailure.EMPTY_LIBRARY -> R.string.spotify_import_empty_library
                SpotifyFailure.INCOMPLETE_LIBRARY -> R.string.spotify_import_incomplete_library
            })) } }
            if (state.progress.unmatched.isNotEmpty()) {
                item { Text(stringResource(R.string.spotify_import_unmatched)) }
                items(state.progress.unmatchedTracks) { track ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${track.title} — ${track.artists.joinToString()}")
                        state.progress.unmatchedReasons[track]?.let { reason ->
                            Text(stringResource(when (reason) {
                                SpotifyMatchFailure.NO_RESULTS -> R.string.spotify_match_no_results
                                SpotifyMatchFailure.TITLE -> R.string.spotify_match_title
                                SpotifyMatchFailure.ARTIST -> R.string.spotify_match_artist
                                SpotifyMatchFailure.CREDITS -> R.string.spotify_match_credits
                                SpotifyMatchFailure.VERSION -> R.string.spotify_match_version
                                SpotifyMatchFailure.DURATION -> R.string.spotify_match_duration
                            }))
                        }
                    }
                }
            }
        }
    }
}
