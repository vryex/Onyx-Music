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

    @Test fun reportedMissesMatchPresentationAndArtistMetadataVariants() {
        val examples = listOf(
            Triple(SpotifyTrack("Dead", listOf("Floats", "Flowerbabe"), 200), "Floats & Flowerbabe - Dead (Official Music Video)", listOf("Floats")),
            Triple(SpotifyTrack("On My Way", listOf("The Mirrors"), 200), "On My Way (Official Audio)", listOf("The Mirrors - Topic")),
            Triple(SpotifyTrack("Higher", listOf("Orion"), 200), "Orion - Higher (Official Audio)", listOf("Uploader")),
            Triple(SpotifyTrack("Hurt", listOf("Vandal Moon"), 200), "Hurt (Lyrics)", listOf("Vandal Moon - Topic")),
            Triple(SpotifyTrack("Echarme Al Olvido", listOf("Grupo Cañaveral De Humberto Pabón", "La Joaqui"), 200), "Echarme al Olvido (Video Oficial)", listOf("Grupo Cañaveral", "La Joaqui")),
            Triple(SpotifyTrack("Et ta mère - Solar Remix", listOf("Zoufris Maracas", "Reyna Tropical"), 200), "Et Ta Mere (Solar Remix)", listOf("Zoufris Maracas")),
            Triple(SpotifyTrack("Tic, Tic, Tac", listOf("Carrapicho"), 200), "Tic Tic Tac", listOf("Carrapicho - Topic")),
            Triple(SpotifyTrack("Hasta el Cielo Lloró", listOf("Grupo Cañaveral De Humberto Pabón"), 200), "Hasta El Cielo Lloro", listOf("Grupo Cañaveral")),
            Triple(SpotifyTrack("Reverie", listOf("DJ Sonnenbrand"), 200), "Reverie (Official Audio)", listOf("DJ Sonnenbrand - Topic")),
            Triple(SpotifyTrack("U-Bahn Ride", listOf("1tbsp", "Reaks"), 200), "U Bahn Ride (Visualizer)", listOf("1tbsp")),
            Triple(SpotifyTrack("klk (feat. S1rena)", listOf("Mietze Conte", "S1RENA"), 200), "klk", listOf("Mietze Conte", "S1rena")),
        )
        examples.forEach { (source, title, artists) -> assertTrue("${source.title} -> $title", SpotifyImportRules.matches(source, title, artists, 203)) }
    }

    @Test fun featureCleanupCannotIntroduceOrDiscardNamedGuests() {
        assertFalse(SpotifyImportRules.matches(SpotifyTrack("Song", listOf("Lead"), 200), "Song (feat. Different Guest)", listOf("Lead"), 200))
        assertFalse(SpotifyImportRules.matches(SpotifyTrack("Song (feat. Guest)", listOf("Lead", "Guest"), 200), "Song", listOf("Lead"), 200))
        assertFalse(SpotifyImportRules.matches(SpotifyTrack("Song", listOf("Lead", "Guest"), 200), "Guest - Song", listOf("Guest"), 200))
        assertTrue(SpotifyImportRules.matches(SpotifyTrack("Song", listOf("Lead", "Guest"), 200), "Song (feat. Guest)", listOf("Lead"), 200))
    }

    @Test fun remasterMetadataDoesNotDiscardLiveOrRemixVersions() {
        val source = SpotifyTrack("Song - Remastered 2011", listOf("Artist"), 200)
        assertTrue(SpotifyImportRules.matches(source, "Song (2017 Remaster)", listOf("Artist"), 202))
        assertFalse(SpotifyImportRules.matches(source, "Song (Live) - Remastered 2011", listOf("Artist"), 200))
        assertFalse(SpotifyImportRules.matches(SpotifyTrack("Song feat. Guest - Solar Remix", listOf("Artist", "Guest"), 200), "Song", listOf("Artist", "Guest"), 200))
    }

    @Test fun durationDriftIsBoundedAndDistinctVersionsRemainDifferent() {
        val original = SpotifyTrack("Song", listOf("Artist"), 240)
        assertTrue(SpotifyImportRules.matches(original, "Song (Official Audio)", listOf("Artist"), 247))
        for (version in listOf("Live", "Remix", "Slowed", "Sped Up", "Acoustic", "Instrumental", "Radio Edit", "Extended", "Cover")) {
            assertFalse(version, SpotifyImportRules.matches(original, "Song ($version)", listOf("Artist"), 240))
        }
        assertFalse(SpotifyImportRules.matches(SpotifyTrack("Song - Solar Remix", listOf("Artist"), 240), "Song - Lunar Remix", listOf("Artist"), 240))
        assertFalse(SpotifyImportRules.matches(original, "Song", listOf("Artist"), 280))
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
