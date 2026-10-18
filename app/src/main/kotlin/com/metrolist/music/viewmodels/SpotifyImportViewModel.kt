package com.metrolist.music.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.spotify.SpotifyCsvParser
import com.metrolist.music.spotify.SpotifyFailure
import com.metrolist.music.spotify.SpotifyImportException
import com.metrolist.music.spotify.SpotifyImportProgress
import com.metrolist.music.spotify.SpotifyImportManager
import com.metrolist.music.spotify.SpotifyImportForegroundService
import com.metrolist.music.spotify.SpotifyImportPhase
import com.metrolist.music.spotify.SpotifyTrack
import com.metrolist.music.spotify.SpotifyOAuth
import com.metrolist.music.spotify.SpotifySavedTracksClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class SpotifyImportState(
    val fileName: String = "", val validSongs: Int = 0, val skippedRows: Int = 0, val preview: List<String> = emptyList(),
    val busy: Boolean = false, val loading: Boolean = false, val complete: Boolean = false, val cancelled: Boolean = false,
    val spotifySource: Boolean = false, val connecting: Boolean = false, val authorizing: Boolean = false,
    val loaded: Int = 0, val loadTotal: Int = 0, val pendingSongs: Int? = null,
    val resumable: Boolean = false, val backgroundImport: Boolean = false,
    val error: SpotifyFailure? = null, val progress: SpotifyImportProgress = SpotifyImportProgress(),
)

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: SpotifyImportManager,
    database: MusicDatabase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SpotifyImportState())
    val state = mutableState.asStateFlow()
    val likedCount = database.likedSongsCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private var job: Job? = null
    private var tracks: List<SpotifyTrack> = emptyList()
    private var previewActive = false
    private var sourceLoading = false
    private var localeTag = ""
    @Volatile private var oauth: SpotifyOAuth? = null

    init {
        viewModelScope.launch {
            manager.awaitRestored()
            manager.state.collect { snapshot ->
                if (snapshot.id.isNotBlank() && !previewActive && !sourceLoading) {
                    mutableState.update { it.copy(fileName = snapshot.fileName, spotifySource = snapshot.spotifySource,
                        validSongs = snapshot.progress.total, skippedRows = snapshot.skippedRows,
                        preview = snapshot.remaining.take(5).map { track -> "${track.title} — ${track.artists.joinToString()}" },
                        busy = snapshot.active, loading = false, connecting = false, authorizing = false,
                        complete = snapshot.phase == SpotifyImportPhase.COMPLETE,
                        cancelled = snapshot.phase == SpotifyImportPhase.CANCELLED,
                        resumable = snapshot.phase == SpotifyImportPhase.PAUSED,
                        backgroundImport = snapshot.active, pendingSongs = snapshot.remaining.size,
                        error = snapshot.failure, progress = snapshot.progress) }
                } else if (snapshot.id.isBlank() && snapshot.failure != null && !previewActive && !sourceLoading) {
                    mutableState.update { it.copy(error = snapshot.failure) }
                }
            }
        }
    }

    fun setLocale(languageTag: String) {
        localeTag = languageTag
        manager.setLocale(languageTag)
    }

    fun connect(acceptedMessage: String, rejectedMessage: String, openBrowser: (String) -> Unit) {
        if (job?.isActive == true || manager.state.value.active) return
        oauth?.close(); oauth = null
        sourceLoading = true
        mutableState.value = state.value.copy(busy = true, loading = true, connecting = true, authorizing = true,
            cancelled = false, error = null, loaded = 0, loadTotal = 0)
        job = viewModelScope.launch {
            try {
                val callback = withContext(Dispatchers.IO) {
                    SpotifyOAuth(acceptedMessage, rejectedMessage).also { oauth = it }
                }
                openBrowser(callback.authorizationUrl())
                val code = callback.awaitCode()
                mutableState.value = state.value.copy(authorizing = false)
                val client = SpotifySavedTracksClient()
                val token = client.exchange(code, callback.verifier)
                val saved = client.load(token) { loaded, total -> mutableState.value = state.value.copy(loaded = loaded, loadTotal = total) }
                tracks = saved.tracks
                previewActive = true
                mutableState.value = state.value.copy(fileName = "", spotifySource = true, validSongs = tracks.size,
                    skippedRows = saved.skipped, preview = tracks.take(5).map { "${it.title} — ${it.artists.joinToString()}" },
                    complete = false, resumable = false, backgroundImport = false, pendingSongs = null, progress = SpotifyImportProgress())
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(error = (error as? SpotifyImportException)?.reason ?: SpotifyFailure.RESPONSE)
            } finally {
                sourceLoading = false
                oauth?.finish()
                mutableState.value = state.value.copy(busy = false, loading = false, connecting = false, authorizing = false)
            }
        }
    }

    fun chooseFile(uri: Uri) {
        if (job?.isActive == true || manager.state.value.active) return
        sourceLoading = true
        mutableState.value = state.value.copy(busy = true, loading = true, cancelled = false, error = null)
        job = viewModelScope.launch {
            try {
                val (fileName, preview) = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    val fileName = runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else ""
                        }.orEmpty()
                    }.getOrDefault("")
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            coroutine.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > SpotifyCsvParser.MAX_BYTES) throw SpotifyImportException(SpotifyFailure.FILE_TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } ?: throw SpotifyImportException(SpotifyFailure.FILE_READ)
                    fileName to SpotifyCsvParser.parse(bytes) { coroutine.ensureActive() }
                }
                tracks = preview.tracks
                previewActive = true
                mutableState.value = state.value.copy(fileName = fileName, spotifySource = false, complete = false, resumable = false, backgroundImport = false, pendingSongs = null, progress = SpotifyImportProgress(), validSongs = tracks.size, skippedRows = preview.skippedRows,
                    preview = tracks.take(5).map { "${it.title} — ${it.artists.joinToString()}" })
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(error = (error as? SpotifyImportException)?.reason ?: SpotifyFailure.FILE_READ)
            } finally { sourceLoading = false; mutableState.value = state.value.copy(busy = false, loading = false) }
        }
    }

    fun import() {
        if (job?.isActive == true || manager.state.value.active) return
        if (previewActive && tracks.isEmpty()) return
        if (!previewActive && manager.state.value.remaining.isEmpty()) return
        job = viewModelScope.launch {
            try {
                if (previewActive) {
                    val preview = state.value
                    val prepared = withContext(Dispatchers.IO) {
                        manager.prepare(tracks, preview.fileName, preview.spotifySource, preview.skippedRows, localeTag)
                    }
                    if (!prepared) return@launch
                    previewActive = false
                }
                SpotifyImportForegroundService.start(context, localeTag)
            } catch (cancelled: CancellationException) {
                manager.requestStop(paused = true)
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(error = SpotifyFailure.RESPONSE) }
                manager.requestStop(paused = true, failure = SpotifyFailure.RESPONSE)
            }
        }
    }

    fun pause() {
        manager.requestStop(paused = true)
    }

    fun cancel() {
        job?.cancel()
        oauth?.close()
        if (manager.state.value.active) manager.requestStop(paused = false)
        else mutableState.value = state.value.copy(cancelled = true)
    }
    override fun onCleared() { oauth?.close(); super.onCleared() }
}
