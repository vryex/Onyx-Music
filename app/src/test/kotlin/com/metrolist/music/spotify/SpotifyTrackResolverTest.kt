package com.metrolist.music.spotify

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SpotifyTrackResolverTest {
    private fun song(id: String = "id", title: String = "Song", artist: String = "Artist", duration: Int = 200, album: String? = null) =
        SongItem(id = id, title = title, artists = listOf(Artist(artist, null)), duration = duration, thumbnail = "",
            album = album?.let { Album(it, "album-id") })

    private class FakeSearch(val answer: suspend (String, Boolean) -> SpotifySearchPage) : SpotifySongSearch {
        val calls = mutableListOf<String>()
        var following = SpotifySearchPage(emptyList())
        override suspend fun search(query: String, videos: Boolean): SpotifySearchPage {
            calls.add(if (videos) "video:$query" else "song:$query")
            return answer(query, videos)
        }
        override suspend fun continuation(token: String): SpotifySearchPage { calls.add("continuation:$token"); return following }
    }

    @Test fun successfulFirstPageCostsOneSearchAndEquivalentUploadsChooseClosestDuration() = runBlocking {
        val search = FakeSearch { _, _ -> SpotifySearchPage(listOf(song("farther", duration = 207), song("z-best"), song("a-best"))) }
        assertEquals("a-best", SpotifyTrackResolver(search).resolve(SpotifyTrack("Song", listOf("Artist"), 200))?.id)
        assertEquals(1, search.calls.size)
    }

    @Test fun noisyFirstPageUsesCleanedFeatureQueryBeforeVideoSearch() = runBlocking {
        val search = FakeSearch { query, _ ->
            if (query.contains("feat.")) SpotifySearchPage(listOf(song(artist = "Unrelated")))
            else SpotifySearchPage(listOf(song(title = "klk", artist = "Mietze Conte", duration = 200)
                .copy(artists = listOf(Artist("Mietze Conte", null), Artist("S1RENA", null)))))
        }
        val result = SpotifyTrackResolver(search).resolve(SpotifyTrack("klk (feat. S1rena)", listOf("Mietze Conte", "S1RENA"), 200))
        assertNotNull(result)
        assertEquals(2, search.calls.size)
        assertFalse(search.calls.any { it.startsWith("video:") })
    }

    @Test fun continuationIsBoundedToOnePageAndOnlyUsedAfterRejectedFirstPage() = runBlocking {
        val search = FakeSearch { _, _ -> SpotifySearchPage(listOf(song(artist = "Someone Else")), "next") }
        search.following = SpotifySearchPage(listOf(song()), "more")
        assertNotNull(SpotifyTrackResolver(search).resolve(SpotifyTrack("Song", listOf("Artist"), 200)))
        assertEquals(listOf("song:Song Artist", "continuation:next"), search.calls)
    }

    @Test fun officialVideoFallbackAllowsCreditsInTitleAndShortIntroButRejectsWrongVersions() = runBlocking {
        val search = FakeSearch { _, videos ->
            if (!videos) SpotifySearchPage(emptyList())
            else SpotifySearchPage(listOf(song("wrong-live", "Orion - Higher (Live)", "Orion", 210),
                song("official", "Orion - Higher (Official Music Video)", "Uploader", 212)))
        }
        assertEquals("official", SpotifyTrackResolver(search).resolve(SpotifyTrack("Higher", listOf("Orion"), 200))?.id)
        assertEquals(listOf("song:Higher Orion", "video:Higher orion"), search.calls)
    }

    @Test fun missingSongCannotTurnIntoSameTitleCoverOrDifferentAlbumRecording() = runBlocking {
        val search = FakeSearch { _, _ -> SpotifySearchPage(listOf(song("cover", artist = "Other Artist"),
            song("concert", album = "Live at the Arena"), song("session", album = "Live Sessions"),
            song("unplugged", album = "Unplugged"), song("acoustic", album = "Acoustic"))) }
        assertNull(SpotifyTrackResolver(search).resolve(SpotifyTrack("Song", listOf("Artist"), 200)))
    }

    @Test fun transientSearchFailuresRetryButCancellationDoesNotBecomeUnmatched() = runBlocking {
        var attempts = 0
        val search = FakeSearch { _, _ -> attempts++; if (attempts < 3) throw IOException("offline") else SpotifySearchPage(listOf(song())) }
        assertNotNull(SpotifyTrackResolver(search, wait = {}).resolve(SpotifyTrack("Song", listOf("Artist"), 200)))
        assertEquals(3, attempts)
        val cancelled = FakeSearch { _, _ -> throw CancellationException("cancelled") }
        val failure = runCatching { SpotifyTrackResolver(cancelled, wait = {}).resolve(SpotifyTrack("Song", listOf("Artist"), 200)) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, cancelled.calls.size)
    }

    @Test fun exhaustedNetworkRetriesStopInsteadOfMarkingUnavailable() = runBlocking {
        val search = FakeSearch { _, _ -> throw IOException("offline") }
        val failure = runCatching { SpotifyTrackResolver(search, wait = {}).resolve(SpotifyTrack("Song", listOf("Artist"), 200)) }.exceptionOrNull()
        assertEquals(SpotifyFailure.NETWORK, (failure as SpotifyImportException).reason)
        assertEquals(3, search.calls.size)
    }
}
