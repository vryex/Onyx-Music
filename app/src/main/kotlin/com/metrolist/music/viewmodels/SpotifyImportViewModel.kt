package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.spotify.SpotifyFailure
import com.metrolist.music.spotify.SpotifyImportException
import com.metrolist.music.spotify.SpotifyImportProgress
import com.metrolist.music.spotify.SpotifyImportRepository
import com.metrolist.music.spotify.SpotifyOAuth
import com.metrolist.music.spotify.SpotifySession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SpotifyImportState(
    val clientId: String = "", val busy: Boolean = false, val authorizing: Boolean = false,
    val connected: Boolean = false, val complete: Boolean = false, val cancelled: Boolean = false,
    val error: SpotifyFailure? = null, val progress: SpotifyImportProgress = SpotifyImportProgress(),
)

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val preferences = context.getSharedPreferences("spotify_import", Context.MODE_PRIVATE)
    private val repository = SpotifyImportRepository(database)
    private val mutableState = MutableStateFlow(SpotifyImportState(clientId = preferences.getString("client_id", "").orEmpty()))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var oauth: SpotifyOAuth? = null
    private var token: SpotifySession? = null

    fun setClientId(value: String) {
        if (state.value.busy) return
        if (value.trim() != state.value.clientId) token = null
        mutableState.value = state.value.copy(clientId = value.trim(), connected = token != null, complete = false)
    }

    fun connect(openBrowser: (String) -> Unit) {
        if (job?.isActive == true) return
        val clientId = state.value.clientId
        if (!clientId.matches(Regex("[a-fA-F0-9]{32}"))) return
        preferences.edit().putString("client_id", clientId).apply()
        mutableState.value = state.value.copy(busy = true, authorizing = token == null, complete = false, cancelled = false, error = null, progress = SpotifyImportProgress())
        job = viewModelScope.launch {
            try {
                if (token == null) {
                    val callback = withContext(Dispatchers.IO) { SpotifyOAuth() }
                    oauth = callback
                    try {
                        openBrowser(callback.authorizationUrl(clientId))
                        token = repository.exchange(clientId, callback, callback.awaitCode())
                    } finally { callback.close(); oauth = null }
                }
                mutableState.value = state.value.copy(authorizing = false, connected = true)
                repository.import(requireNotNull(token)) { progress -> mutableState.value = state.value.copy(progress = progress) }
                mutableState.value = state.value.copy(complete = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val reason = (e as? SpotifyImportException)?.reason ?: SpotifyFailure.RESPONSE
                if (reason == SpotifyFailure.AUTHORIZATION) token = null
                mutableState.value = state.value.copy(error = reason, connected = token != null)
            } finally {
                oauth?.close(); oauth = null
                mutableState.value = state.value.copy(busy = false, authorizing = false)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        oauth?.close()
        mutableState.value = state.value.copy(cancelled = true)
    }

    fun disconnect() {
        cancel()
        token = null
        mutableState.value = state.value.copy(connected = false)
    }

    override fun onCleared() { oauth?.close(); token = null; super.onCleared() }
}
