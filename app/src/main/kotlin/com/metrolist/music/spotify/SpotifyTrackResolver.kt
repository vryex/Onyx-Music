package com.metrolist.music.spotify

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal data class SpotifySearchPage(val songs: List<SongItem>, val continuation: String? = null)
internal interface SpotifySongSearch {
    suspend fun search(query: String, videos: Boolean = false): SpotifySearchPage
    suspend fun continuation(token: String): SpotifySearchPage
}

internal class YouTubeSpotifySongSearch : SpotifySongSearch {
    override suspend fun search(query: String, videos: Boolean): SpotifySearchPage {
        val result = YouTube.search(query, if (videos) YouTube.SearchFilter.FILTER_VIDEO else YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        return SpotifySearchPage(result.items.filterIsInstance<SongItem>(), result.continuation)
    }
    override suspend fun continuation(token: String): SpotifySearchPage {
        val result = YouTube.searchContinuation(token).getOrThrow()
        return SpotifySearchPage(result.items.filterIsInstance<SongItem>(), result.continuation)
    }
}

internal class SpotifyTrackResolver(
    private val search: SpotifySongSearch = YouTubeSpotifySongSearch(),
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun resolve(track: SpotifyTrack): SongItem? {
        if (track.artists.isEmpty() || track.title.isBlank()) return null
        val originalQuery = "${track.title} ${track.artists.first()}"
        val first = retry { search.search(originalQuery) }
        best(track, first.songs)?.let { return it }
        val cleanedQuery = SpotifyImportRules.cleanedQuery(track)
        var secondary: SpotifySearchPage? = null
        if (SpotifyImportRules.normalized(cleanedQuery) != SpotifyImportRules.normalized(originalQuery)) {
            secondary = retry { search.search(cleanedQuery) }
            best(track, secondary.songs)?.let { return it }
        }
        // One additional results page is enough to escape ranking noise without scanning entire search feeds.
        (secondary?.continuation ?: first.continuation)?.let { token ->
            best(track, retry { search.continuation(token) }.songs)?.let { return it }
        }
        return best(track, retry { search.search(cleanedQuery, videos = true) }.songs, videos = true)
    }

    private fun best(track: SpotifyTrack, songs: List<SongItem>, videos: Boolean = false): SongItem? = songs
        .asSequence().filterNot { it.isEpisode }.distinctBy { it.id }
        .mapNotNull { song -> SpotifyImportRules.score(track, song.title, song.artists.map { it.name }, song.duration,
            videos || song.isVideoSong, song.album?.name)?.let { score -> song to score } }
        .sortedWith(compareByDescending<Pair<SongItem, Int>> { it.second }.thenBy { it.first.id })
        .firstOrNull()?.first

    private suspend fun <T> retry(block: suspend () -> T): T {
        repeat(3) { attempt ->
            currentCoroutineContext().ensureActive()
            try { return block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (attempt == 2) throw SpotifyImportException(SpotifyFailure.NETWORK)
                wait((attempt + 1) * 1500L)
            }
        }
        throw SpotifyImportException(SpotifyFailure.NETWORK)
    }
}
