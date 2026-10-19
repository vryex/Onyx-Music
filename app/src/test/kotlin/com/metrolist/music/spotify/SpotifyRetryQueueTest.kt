package com.metrolist.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyRetryQueueTest {
    private val tracks = (1..4).map { SpotifyTrack("Song $it", listOf("Artist"), 200) }

    @Test fun explicitOutOfOrderCheckpointTakesPriorityOverSequentialProcessedCount() {
        val progress = SpotifyImportProgress(processed = 2, total = 4, imported = 1, unmatchedTracks = listOf(tracks[0]),
            remainingTracks = listOf(tracks[0], tracks[1], tracks[3]))
        assertEquals(listOf(tracks[0], tracks[1], tracks[3]), pendingSpotifyTracks(tracks, progress))
    }

    @Test fun explicitlyEmptyRemainingQueueCannotRestoreLegacyTail() {
        assertEquals(emptyList<SpotifyTrack>(), pendingSpotifyTracks(tracks, SpotifyImportProgress(remainingTracks = emptyList())))
    }

    @Test fun completedImportRetriesOnlyUnresolvedSongs() {
        val progress = SpotifyImportProgress(processed = 4, total = 4, imported = 3, unmatchedTracks = listOf(tracks[1]))
        assertEquals(listOf(tracks[1]), pendingSpotifyTracks(tracks, progress))
    }

    @Test fun cancellationRetainsUnresolvedSongsAndUnprocessedTail() {
        val progress = SpotifyImportProgress(processed = 2, total = 4, imported = 1, unmatchedTracks = listOf(tracks[0]))
        assertEquals(listOf(tracks[0], tracks[2], tracks[3]), pendingSpotifyTracks(tracks, progress))
    }

    @Test fun failureBeforeFirstResultKeepsEntireQueue() {
        assertEquals(tracks, pendingSpotifyTracks(tracks, SpotifyImportProgress(total = 4)))
    }

    @Test fun fullyImportedQueueDoesNotRepeatAnyLookup() {
        assertEquals(emptyList<SpotifyTrack>(), pendingSpotifyTracks(tracks, SpotifyImportProgress(processed = 4, total = 4, imported = 4)))
    }
}
