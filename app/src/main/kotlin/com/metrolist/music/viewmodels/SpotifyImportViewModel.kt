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
import com.metrolist.music.spotify.SpotifyImportRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class SpotifyImportState(
    val fileName: String = "", val validSongs: Int = 0, val skippedRows: Int = 0, val preview: List<String> = emptyList(),
    val busy: Boolean = false, val loading: Boolean = false, val complete: Boolean = false, val cancelled: Boolean = false,
    val spotifySource: Boolean = false, val connecting: Boolean = false, val authorizing: Boolean = false,
    val loaded: Int = 0, val loadTotal: Int = 0,
    val error: SpotifyFailure? = null, val progress: SpotifyImportProgress = SpotifyImportProgress(),
)

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val repository = SpotifyImportRepository(database)
    private val mutableState = MutableStateFlow(SpotifyImportState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var tracks: List<SpotifyTrack> = emptyList()
    @Volatile private var oauth: SpotifyOAuth? = null

    fun connect(acceptedMessage: String, rejectedMessage: String, openBrowser: (String) -> Unit) {
        if (job?.isActive == true) return
        mutableState.value = state.value.copy(busy = true, loading = true, connecting = true, authorizing = true,
            cancelled = false, error = null, loaded = 0, loadTotal = 0)
        job = viewModelScope.launch {
            try {
                val callback = withContext(Dispatchers.IO) {
                    SpotifyOAuth(acceptedMessage, rejectedMessage).also { oauth = it }
                }
                openBrowser(callback.authorizationUrl())
                val code = callback.awaitCode()
                callback.close()
                oauth = null
                mutableState.value = state.value.copy(authorizing = false)
                val client = SpotifySavedTracksClient()
                val token = client.exchange(code, callback.verifier)
                val saved = client.load(token) { loaded, total -> mutableState.value = state.value.copy(loaded = loaded, loadTotal = total) }
                tracks = saved.tracks
                mutableState.value = state.value.copy(fileName = "", spotifySource = true, validSongs = tracks.size,
                    skippedRows = saved.skipped, preview = tracks.take(5).map { "${it.title} — ${it.artists.joinToString()}" },
                    complete = false, progress = SpotifyImportProgress())
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(error = (error as? SpotifyImportException)?.reason ?: SpotifyFailure.RESPONSE)
            } finally {
                oauth?.close(); oauth = null
                mutableState.value = state.value.copy(busy = false, loading = false, connecting = false, authorizing = false)
            }
        }
    }

    fun chooseFile(uri: Uri) {
        if (job?.isActive == true) return
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
                mutableState.value = state.value.copy(fileName = fileName, spotifySource = false, complete = false, progress = SpotifyImportProgress(), validSongs = tracks.size, skippedRows = preview.skippedRows,
                    preview = tracks.take(5).map { "${it.title} — ${it.artists.joinToString()}" })
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(error = (error as? SpotifyImportException)?.reason ?: SpotifyFailure.FILE_READ)
            } finally { mutableState.value = state.value.copy(busy = false, loading = false) }
        }
    }

    fun import() {
        if (job?.isActive == true || tracks.isEmpty()) return
        mutableState.value = state.value.copy(busy = true, complete = false, cancelled = false, error = null, progress = SpotifyImportProgress(total = tracks.size))
        job = viewModelScope.launch {
            try {
                repository.import(tracks) { progress -> mutableState.value = state.value.copy(progress = progress) }
                mutableState.value = state.value.copy(complete = true)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = state.value.copy(error = (error as? SpotifyImportException)?.reason ?: SpotifyFailure.RESPONSE)
            } finally { mutableState.value = state.value.copy(busy = false) }
        }
    }

    fun cancel() {
        job?.cancel()
        oauth?.close()
        mutableState.value = state.value.copy(cancelled = true)
    }
    override fun onCleared() { oauth?.close(); super.onCleared() }
}
