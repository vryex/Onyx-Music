package com.metrolist.music.spotify

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class SpotifyImportPipelineTest {
    private val tracks = (0..7).map { SpotifyTrack("Song $it", listOf("Artist"), 200) }
    private fun index(track: SpotifyTrack) = track.title.substringAfterLast(' ').toInt()
    private fun Job.descendantCount(): Int = children.sumOf { 1 + it.descendantCount() }

    @Test fun fixedWorkersBoundActiveLookupsAndCoroutineCountForLargeImports() = runBlocking {
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val active = AtomicInteger()
        val started = AtomicInteger()
        val pipeline = SpotifyImportPipeline<Int>(resolve = {
            active.incrementAndGet(); started.incrementAndGet(); entered.send(Unit)
            try { awaitCancellation() } finally { active.decrementAndGet() }
        }, store = { SpotifyStoreResult.IMPORTED })
        val job = async { pipeline.import((0..1368).map { SpotifyTrack("Song $it", listOf("Artist")) }) {} }
        repeat(3) { entered.receive() }
        assertEquals(3, active.get())
        assertEquals(3, started.get())
        assertTrue("Only a scope and three worker jobs should exist", job.descendantCount() <= 4)
        job.cancelAndJoin()
        assertEquals(0, active.get())
        assertEquals(3, started.get())
    }

    @Test fun outOfOrderCompletionKeepsStableUnmatchedAndAllNonSuccessTracksOnCancellation() = runBlocking {
        val gates = List(tracks.size) { CompletableDeferred<Unit>() }
        val entered = Channel<Int>(Channel.UNLIMITED)
        val progress = Channel<SpotifyImportProgress>(Channel.UNLIMITED)
        val snapshots = mutableListOf<SpotifyImportProgress>()
        val pipeline = SpotifyImportPipeline(resolve = { track: SpotifyTrack ->
            val index = index(track); entered.send(index); gates[index].await(); if (index == 0) null else index
        }, store = { if (it == 3) SpotifyStoreResult.ALREADY else SpotifyStoreResult.IMPORTED })
        val job = async { pipeline.import(tracks) { snapshots.add(it); progress.trySend(it) } }
        assertEquals(0, progress.receive().processed)
        repeat(3) { entered.receive() }
        gates[2].complete(Unit)
        assertEquals(1, progress.receive().processed)
        assertEquals(3, entered.receive())
        gates[0].complete(Unit)
        assertEquals(2, progress.receive().processed)
        assertEquals(4, entered.receive())
        gates[3].complete(Unit)
        val checkpoint = progress.receive()
        assertEquals(3, checkpoint.processed)
        job.cancelAndJoin()
        val last = snapshots.last()
        assertEquals(1, last.imported)
        assertEquals(1, last.already)
        assertEquals(listOf(tracks[0]), last.unmatchedTracks)
        assertEquals(listOf(tracks[0], tracks[1], tracks[4], tracks[5], tracks[6], tracks[7]), last.remainingTracks)
        assertEquals(listOf(0, 1, 2, 3), snapshots.map { it.processed })
        snapshots.forEach { assertEquals(it.processed, it.imported + it.already + it.unmatchedTracks.size) }
    }

    @Test fun cancellationDuringCommittedStoreCannotLoseSuccessfulCheckpoint() = runBlocking {
        val storing = CompletableDeferred<Unit>()
        val finishStore = CompletableDeferred<Unit>()
        val snapshots = mutableListOf<SpotifyImportProgress>()
        val pipeline = SpotifyImportPipeline(resolve = { track: SpotifyTrack ->
            if (index(track) == 0) 0 else awaitCancellation()
        }, store = {
            storing.complete(Unit)
            finishStore.await()
            SpotifyStoreResult.IMPORTED
        })
        val job = async { pipeline.import(tracks.take(2)) { snapshots.add(it) } }
        storing.await()
        job.cancel()
        finishStore.complete(Unit)
        job.join()
        assertEquals(1, snapshots.last().imported)
        assertEquals(listOf(tracks[1]), snapshots.last().remainingTracks)
    }

    @Test fun networkFailureCancelsOtherWorkersAndRetainsOnlyUnsuccessfulInputs() = runBlocking {
        val fail = CompletableDeferred<Unit>()
        val successful = CompletableDeferred<Unit>()
        val otherCancelled = CompletableDeferred<Unit>()
        var last = SpotifyImportProgress()
        val pipeline = SpotifyImportPipeline(resolve = { track: SpotifyTrack ->
            when (index(track)) {
                0 -> 0
                1 -> try { awaitCancellation() } finally { otherCancelled.complete(Unit) }
                else -> { fail.await(); throw IOException("offline") }
            }
        }, store = { SpotifyStoreResult.IMPORTED })
        val outcome = async { runCatching { pipeline.import(tracks) { last = it; if (it.imported == 1) successful.complete(Unit) } } }
        successful.await()
        fail.complete(Unit)
        assertTrue(outcome.await().exceptionOrNull() is IOException)
        otherCancelled.await()
        assertEquals(1, last.imported)
        assertEquals(tracks.drop(1), last.remainingTracks)
    }

    @Test fun fullImportCountsDuplicatesAsAlreadyAndKeepsUnmatchedInOriginalOrder() = runBlocking {
        val stored = HashSet<Int>()
        val input = listOf(tracks[2], tracks[0], tracks[2], tracks[1], tracks[3])
        var last = SpotifyImportProgress()
        val pipeline = SpotifyImportPipeline(resolve = { track: SpotifyTrack -> if (index(track) in 0..1) null else index(track) },
            store = { if (stored.add(it)) SpotifyStoreResult.IMPORTED else SpotifyStoreResult.ALREADY })
        pipeline.import(input) { last = it }
        assertEquals(5, last.processed)
        assertEquals(2, last.imported)
        assertEquals(1, last.already)
        assertEquals(listOf(tracks[0], tracks[1]), last.unmatchedTracks)
        assertEquals(listOf(tracks[0], tracks[1]), last.remainingTracks)
    }
}
