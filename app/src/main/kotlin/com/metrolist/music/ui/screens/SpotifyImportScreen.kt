package com.metrolist.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.spotify.SpotifyFailure
import com.metrolist.music.spotify.SpotifyImportRules
import com.metrolist.music.viewmodels.SpotifyImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(navController: NavController, viewModel: SpotifyImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("sonet_spotify_import"), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.spotify_import_title)) }, navigationIcon = {
            TextButton(onClick = { navController.navigateUp() }) { Text(stringResource(R.string.spotify_import_back)) }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(stringResource(R.string.spotify_import_description)) }
            if (!state.connected) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.spotify_import_setup))
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.spotify.com/dashboard"))) }) {
                            Text(stringResource(R.string.spotify_import_dashboard))
                        }
                        Text(stringResource(R.string.spotify_import_redirect))
                        SelectionContainer { Text(SpotifyImportRules.REGISTERED_REDIRECT) }
                        OutlinedTextField(value = state.clientId, onValueChange = viewModel::setClientId,
                            label = { Text(stringResource(R.string.spotify_import_client_id)) }, singleLine = true,
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("sonet_spotify_client_id"))
                    }
                }
            }
            item {
                if (state.busy) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(if (state.authorizing) R.string.spotify_import_authorizing else R.string.spotify_import_running))
                        TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.spotify_import_cancel)) }
                    }
                } else {
                    Button(modifier = Modifier.testTag("sonet_spotify_connect"), onClick = { viewModel.connect { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                        enabled = state.clientId.matches(Regex("[a-fA-F0-9]{32}"))) {
                        Text(stringResource(if (state.connected) R.string.spotify_import_retry else R.string.spotify_import_connect))
                    }
                }
            }
            item { Text(stringResource(R.string.spotify_import_progress, state.progress.processed, state.progress.total, state.progress.imported, state.progress.already, state.progress.unmatched.size)) }
            if (state.complete) item { Text(stringResource(R.string.spotify_import_complete)) }
            if (state.cancelled) item { Text(stringResource(R.string.spotify_import_cancelled)) }
            state.error?.let { failure -> item {
                Text(stringResource(when (failure) {
                    SpotifyFailure.DENIED -> R.string.spotify_import_denied
                    SpotifyFailure.FORBIDDEN -> R.string.spotify_import_forbidden
                    SpotifyFailure.AUTHORIZATION -> R.string.spotify_import_authorization_error
                    SpotifyFailure.RATE_LIMIT -> R.string.spotify_import_rate_limit
                    SpotifyFailure.NETWORK -> R.string.spotify_import_network
                    SpotifyFailure.TIMEOUT -> R.string.spotify_import_timeout
                    SpotifyFailure.RESPONSE -> R.string.spotify_import_error
                }))
            } }
            if (state.connected && !state.busy) item {
                TextButton(onClick = viewModel::disconnect) { Text(stringResource(R.string.spotify_import_disconnect)) }
            }
            if (state.progress.unmatched.isNotEmpty()) {
                item { Text(stringResource(R.string.spotify_import_unmatched)) }
                items(state.progress.unmatched) { Text(it) }
            }
        }
    }
}
