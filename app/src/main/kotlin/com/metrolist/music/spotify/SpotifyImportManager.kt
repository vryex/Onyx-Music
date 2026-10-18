package com.metrolist.music.spotify

import android.content.Context
import com.metrolist.music.db.MusicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyImportManager internal constructor(
    private val checkpoint: SpotifyImportCheckpoint,
    private val importer: suspend (List<SpotifyTrack>, (SpotifyImportProgress) -> Unit) -> Unit,
    private val scope: CoroutineScope,
) {
    @Inject constructor(@ApplicationContext context: Context, database: MusicDatabase) : this(
        SpotifyImportCheckpoint(context.filesDir), SpotifyImportRepository(database)::import,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val mutableState = MutableStateFlow(SpotifyImportSnapshot())
    internal val state = mutableState.asStateFlow()
    private val restored = CompletableDeferred<Unit>()
    private val control = Mutex()
    @Volatile private var worker: Job? = null
    @Volatile private var stopping = false

    init {
        scope.launch {
            try {
                checkpoint.load()?.let { mutableState.value = it }
            } catch (_: Exception) {
                mutableState.value = SpotifyImportSnapshot(phase = SpotifyImportPhase.ERROR, failure = SpotifyFailure.FILE_READ)
            } finally { restored.complete(Unit) }
        }
    }

    internal suspend fun prepare(
        tracks: List<SpotifyTrack>, fileName: String, spotifySource: Boolean, skippedRows: Int, localeTag: String,
    ): Boolean {
        restored.await()
        return control.withLock {
            if (worker?.isCompleted == false || state.value.active || tracks.isEmpty()) return@withLock false
            val queue = tracks.distinct().toList()
            val next = SpotifyImportSnapshot(id = UUID.randomUUID().toString(), revision = 1,
                phase = SpotifyImportPhase.PREPARED, fileName = fileName, spotifySource = spotifySource,
                skippedRows = skippedRows, localeTag = localeTag,
                progress = SpotifyImportProgress(total = queue.size, remainingTracks = queue), remaining = queue)
            checkpoint.begin(next, queue)
            mutableState.value = next
            true
        }
    }

    internal suspend fun startOrResume(requestId: String) {
        restored.await()
        control.withLock {
            if (worker?.isCompleted == false || state.value.remaining.isEmpty()) return@withLock
            val previous = state.value
            if (previous.requestId == requestId && previous.phase in setOf(SpotifyImportPhase.COMPLETE, SpotifyImportPhase.CANCELLED, SpotifyImportPhase.ERROR)) return@withLock
            mutableState.value = previous.copy(revision = previous.revision + 1, requestId = requestId, phase = SpotifyImportPhase.RUNNING, failure = null)
            checkpoint.save(state.value)
            worker = scope.launch(start = CoroutineStart.LAZY) { runImport(previous) }
            worker!!.start()
        }
    }

    private suspend fun runImport(previous: SpotifyImportSnapshot) {
        var lastWrite = 0L
        try {
            importer(previous.remaining) { progress ->
                val remaining = progress.remainingTracks ?: pendingSpotifyTracks(previous.remaining, progress)
                mutableState.update { current ->
                    if (current.id != previous.id || current.phase != SpotifyImportPhase.RUNNING) current
                    else current.copy(revision = current.revision + 1,
                        progress = progress.copy(total = previous.progress.total,
                            processed = previous.progress.imported + previous.progress.already + progress.processed,
                            imported = previous.progress.imported + progress.imported,
                            already = previous.progress.already + progress.already,
                            scanned = maxOf(previous.progress.scanned, previous.progress.processed + progress.scanned),
                            remainingTracks = remaining), remaining = remaining)
                }
                val now = System.nanoTime()
                if (now - lastWrite >= 1_000_000_000L) {
                    checkpoint.save(state.value)
                    lastWrite = now
                }
            }
            mutableState.update { if (it.phase == SpotifyImportPhase.RUNNING)
                it.copy(revision = it.revision + 1, phase = SpotifyImportPhase.COMPLETE) else it }
        } catch (cancelled: CancellationException) {
            mutableState.update { if (!stopping && it.phase == SpotifyImportPhase.RUNNING)
                it.copy(revision = it.revision + 1, phase = SpotifyImportPhase.PAUSED) else it }
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { if (it.phase == SpotifyImportPhase.RUNNING)
                it.copy(revision = it.revision + 1, phase = SpotifyImportPhase.ERROR,
                    failure = (error as? SpotifyImportException)?.reason ?: SpotifyFailure.RESPONSE) else it }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                try { checkpoint.save(state.value) }
                catch (_: Exception) {
                    mutableState.update { it.copy(failure = SpotifyFailure.FILE_READ) }
                }
            }
        }
    }

    internal suspend fun stop(paused: Boolean = false, failure: SpotifyFailure? = null) = withContext(NonCancellable + Dispatchers.IO) {
        restored.await()
        control.withLock {
            val wasActive = state.value.active
            stopping = true
            worker?.cancel()
            worker?.join()
            mutableState.update { if (wasActive) it.copy(revision = it.revision + 1,
                phase = if (paused) SpotifyImportPhase.PAUSED else SpotifyImportPhase.CANCELLED, failure = failure) else it }
            try { checkpoint.save(state.value) }
            catch (_: Exception) { mutableState.update { it.copy(failure = SpotifyFailure.FILE_READ) } }
            finally { stopping = false }
        }
    }

    internal fun requestStop(paused: Boolean, failure: SpotifyFailure? = null): Job = scope.launch { stop(paused, failure) }

    internal fun setLocale(localeTag: String) {
        scope.launch {
            restored.await()
            mutableState.update { if (it.localeTag == localeTag) it else it.copy(revision = it.revision + 1, localeTag = localeTag) }
            try { checkpoint.save(state.value) } catch (_: Exception) { }
        }
    }

    internal suspend fun awaitRestored() = restored.await()
}
