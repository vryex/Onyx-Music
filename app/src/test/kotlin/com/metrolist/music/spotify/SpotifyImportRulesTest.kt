package com.metrolist.music.spotify

import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class SpotifyImportRulesTest {
    @Test fun commaSeparatedCollaboratorsMatchWithoutSplittingArtistNames() {
        assertTrue(SpotifyImportRules.matches(SpotifyTrack("Song", listOf("Artist A, Artist B")), "Song", listOf("Artist A", "Artist B"), 200))
        assertTrue(SpotifyImportRules.matches(SpotifyTrack("Song", listOf("Tyler, The Creator")), "Song", listOf("Tyler, The Creator"), 200))
        assertFalse(SpotifyImportRules.matches(SpotifyTrack("Song", listOf("Artist A, Artist B")), "Song", listOf("Artist A"), 200))
    }

    @Test fun repeatedImportPreservesExistingSongDataAndMembershipDates() {
        val now = LocalDateTime.of(2026, 10, 18, 12, 0)
        val existing = SongEntity(id = "local-id", title = "Edited title", totalPlayTime = 74000, lyricsOffset = 123,
            inLibrary = now.minusDays(10), isDownloaded = true)
        val imported = existing.withSpotifyImportMembership(now)
        val repeated = imported.withSpotifyImportMembership(now.plusDays(1))
        assertTrue(imported.liked)
        assertEquals(existing.inLibrary, imported.inLibrary)
        assertEquals(now, imported.likedDate)
        assertEquals(existing.title, imported.title)
        assertEquals(existing.totalPlayTime, imported.totalPlayTime)
        assertEquals(existing.lyricsOffset, imported.lyricsOffset)
        assertTrue(imported.isDownloaded)
        assertEquals(imported, repeated)
    }
    @Test fun missingDurationStillRequiresExactTitleAndArtist() {
        val track = SpotifyTrack("Hello", listOf("Adele"))
        assertTrue(SpotifyImportRules.matches(track, "Hello", listOf("Adele"), null))
        assertFalse(SpotifyImportRules.matches(track, "Hello Live", listOf("Adele"), 295))
        assertFalse(SpotifyImportRules.matches(track, "Hello", listOf("Cover artist"), 295))
    }
    @Test fun recordingVersionsAndCoversAreNeverCollapsed() {
        val track = SpotifyTrack("Hello", listOf("Adele"), 295)
        assertTrue(SpotifyImportRules.matches(track, "HELLO", listOf("Adele"), 294))
        assertFalse(SpotifyImportRules.matches(track, "Hello (Live)", listOf("Adele"), 295))
        assertFalse(SpotifyImportRules.matches(track, "Hello - Remix", listOf("Adele"), 295))
        assertFalse(SpotifyImportRules.matches(track, "Hello", listOf("Cover artist"), 295))
        assertFalse(SpotifyImportRules.matches(track, "Hello", listOf("Adele"), null))
        assertFalse(SpotifyImportRules.matches(track, "Hello", listOf("Adele"), 310))
    }
    @Test fun unicodeAndPunctuationCanMatchWithoutRemovingVersionWords() {
        val track = SpotifyTrack("Déjà Vu", listOf("Beyoncé"), 220)
        assertTrue(SpotifyImportRules.matches(track, "Deja-Vu", listOf("Beyonce"), 221))
        assertFalse(SpotifyImportRules.matches(track, "Deja Vu Acoustic", listOf("Beyonce"), 220))
    }
}
