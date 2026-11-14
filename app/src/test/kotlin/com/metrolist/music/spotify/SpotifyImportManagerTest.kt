package com.metrolist.music.spotify

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpotifyImportManagerTest {
    @get:Rule val directory = TemporaryFolder()
    private val first = SpotifyTrack("First", listOf("Artist"), 123)
    private val second = SpotifyTrack("Second", listOf("Otro"), null)
    private val queue = listOf(first, second)

    @Test fun checkpointRestoresPendingQueueAndCountsAsPausedWithoutRewritingSource() {
        val checkpoint = SpotifyImportCheckpoint(directory.root)
        val initial = SpotifyImportSnapshot(id = "attempt", revision = 1, phase = SpotifyImportPhase.RUNNING,
            fileName = "songs.csv", localeTag = "es", progress = SpotifyImportProgress(total = 2), remaining = queue)
        checkpoint.begin(initial, queue)
        val source = File(directory.root, "spotify-import-queue.json").readText()
        checkpoint.save(initial.copy(revision = 2, progress = SpotifyImportProgress(processed = 2, total = 2,
            imported = 1, unmatched = listOf("Second — Otro"), unmatchedTracks = listOf(second)), remaining = listOf(second)))
        val restored = SpotifyImportCheckpoint(directory.root).load()!!
        assertEquals(SpotifyImportPhase.PAUSED, restored.phase)
        assertEquals(listOf(second), restored.remaining)
        assertEquals(1, restored.progress.imported)
        assertEquals(2, restored.progress.processed)
        assertEquals(listOf(second), restored.progress.unmatchedTracks)
        assertEquals("es", restored.localeTag)
        assertEquals(source, File(directory.root, "spotify-import-queue.json").readText())
        assertFalse(File(directory.root, "spotify-import-progress.json").readText().contains("Second"))
    }

    @Test fun rejectionDiagnosticsSurviveCheckpointAndOldCheckpointsRemainReadable() {
        val checkpoint = SpotifyImportCheckpoint(directory.root)
        val initial = SpotifyImportSnapshot(id = "diagnostic", revision = 1, phase = SpotifyImportPhase.COMPLETE,
            progress = SpotifyImportProgress(total = 2, processed = 2, imported = 1,
                unmatchedTracks = listOf(second), unmatchedReasons = mapOf(second to SpotifyMatchFailure.DURATION)),
            remaining = listOf(second))
        checkpoint.begin(initial, queue)
        assertEquals(SpotifyMatchFailure.DURATION, SpotifyImportCheckpoint(directory.root).load()!!.progress.unmatchedReasons[second])
        val file = File(directory.root, "spotify-import-progress.json")
        val legacy = org.json.JSONObject(file.readText()).apply { remove("reasons") }
        file.writeText(legacy.toString())
        assertTrue(SpotifyImportCheckpoint(directory.root).load()!!.progress.unmatchedReasons.isEmpty())
    }

    @Test fun retryProcessesOnlyPendingSongsAndKeepsGlobalCountsAfterProcessRestart() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val checkpoint = SpotifyImportCheckpoint(directory.root)
        checkpoint.begin(SpotifyImportSnapshot(id = "retry", revision = 1, phase = SpotifyImportPhase.COMPLETE,
            progress = SpotifyImportProgress(total = 2, processed = 2, imported = 1, unmatchedTracks = listOf(second)),
            remaining = listOf(second)), queue)
        val received = CompletableDeferred<List<SpotifyTrack>>()
        val manager = SpotifyImportManager(SpotifyImportCheckpoint(directory.root), { pending, progress ->
            received.complete(pending)
            progress(SpotifyImportProgress(total = 1, processed = 1, imported = 1, remainingTracks = emptyList()))
        }, scope)
        try {
            manager.awaitRestored()
            manager.startOrResume("retry-request")
            assertEquals(listOf(second), withTimeout(3000) { received.await() })
            withTimeout(3000) { while (manager.state.value.phase != SpotifyImportPhase.COMPLETE) delay(10) }
            val progress = manager.state.value.progress
            assertEquals(2, progress.total)
            assertEquals(2, progress.processed)
            assertEquals(2, progress.imported)
            assertEquals(0, progress.already)
            assertTrue(manager.state.value.remaining.isEmpty())
        } finally { manager.stop(); scope.cancel() }
    }

    @Test fun staleCheckpointCannotOverwriteCancellation() {
        val checkpoint = SpotifyImportCheckpoint(directory.root)
        val initial = SpotifyImportSnapshot(id = "attempt", revision = 1, phase = SpotifyImportPhase.RUNNING,
            progress = SpotifyImportProgress(total = 2), remaining = queue)
        checkpoint.begin(initial, queue)
        checkpoint.save(initial.copy(revision = 3, phase = SpotifyImportPhase.CANCELLED))
        checkpoint.save(initial.copy(revision = 2))
        assertEquals(SpotifyImportPhase.CANCELLED, SpotifyImportCheckpoint(directory.root).load()!!.phase)
    }

    @Test fun duplicateStartHasOneWorkerAndCancelIsIdempotent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val started = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val manager = SpotifyImportManager(SpotifyImportCheckpoint(directory.root), { _, _ ->
            calls.incrementAndGet()
            started.complete(Unit)
            awaitCancellation()
        }, scope)
        try {
            assertTrue(manager.prepare(queue, "songs.csv", false, 0, "en"))
            manager.startOrResume("request")
            withTimeout(3000) { started.await() }
            manager.startOrResume("request")
            assertFalse(manager.prepare(queue, "another.csv", false, 0, "en"))
            assertEquals(1, calls.get())
            manager.stop()
            val stopped = manager.state.value
            manager.stop()
            manager.startOrResume("request")
            assertEquals(stopped, manager.state.value)
            assertEquals(1, calls.get())
            assertEquals(SpotifyImportPhase.CANCELLED, stopped.phase)
            assertEquals(queue, stopped.remaining)
        } finally { scope.cancel() }
    }

    @Test fun cancellationWaitsForCommittedSuccessBeforePersistingRemainingQueue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val committing = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val manager = SpotifyImportManager(SpotifyImportCheckpoint(directory.root), { _, progress ->
            withContext(NonCancellable) {
                committing.complete(Unit)
                releaseCommit.await()
                progress(SpotifyImportProgress(processed = 1, total = 2, imported = 1, remainingTracks = listOf(second)))
            }
            currentCoroutineContext().ensureActive()
        }, scope)
        try {
            manager.prepare(queue, "songs.csv", false, 0, "es")
            manager.startOrResume("request")
            withTimeout(3000) { committing.await() }
            val stopping = async(Dispatchers.IO) { manager.stop() }
            delay(50)
            assertFalse(stopping.isCompleted)
            releaseCommit.complete(Unit)
            withTimeout(3000) { stopping.await() }
            val restored = SpotifyImportCheckpoint(directory.root).load()!!
            assertEquals(SpotifyImportPhase.CANCELLED, restored.phase)
            assertEquals(1, restored.progress.imported)
            assertEquals(listOf(second), restored.remaining)
        } finally { releaseCommit.complete(Unit); scope.cancel() }
    }
}
