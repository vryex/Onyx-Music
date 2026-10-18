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
    suspend fun generalVideos(query: String): SpotifySearchPage = SpotifySearchPage(emptyList())
}

internal class YouTubeSpotifySongSearch : SpotifySongSearch {
    override suspend fun search(query: String, videos: Boolean): SpotifySearchPage {
        SpotifyRequestPacer.shared.awaitTurn()
        val result = YouTube.search(query, if (videos) YouTube.SearchFilter.FILTER_VIDEO else YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        return SpotifySearchPage(result.items.filterIsInstance<SongItem>(), result.continuation)
    }
    override suspend fun generalVideos(query: String): SpotifySearchPage {
        SpotifyRequestPacer.shared.awaitTurn()
        val result = YouTube.searchVideos(query = query).getOrThrow()
        return SpotifySearchPage(result.items.filterIsInstance<SongItem>(), result.continuation?.let { "web:$it" })
    }
    override suspend fun continuation(token: String): SpotifySearchPage {
        SpotifyRequestPacer.shared.awaitTurn()
        if (token.startsWith("web:")) {
            val result = YouTube.searchVideos(continuation = token.removePrefix("web:")).getOrThrow()
            return SpotifySearchPage(result.items.filterIsInstance<SongItem>(), result.continuation?.let { "web:$it" })
        }
        val result = YouTube.searchContinuation(token).getOrThrow()
        return SpotifySearchPage(result.items.filterIsInstance<SongItem>(), result.continuation)
    }
}

internal data class SpotifyResolution(val song: SongItem? = null, val failure: SpotifyMatchFailure? = null)

internal class SpotifyTrackResolver(
    private val search: SpotifySongSearch = YouTubeSpotifySongSearch(),
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun resolve(track: SpotifyTrack): SongItem? = resolveDetailed(track).song

    suspend fun resolveDetailed(track: SpotifyTrack): SpotifyResolution {
        if (track.artists.isEmpty() || track.title.isBlank()) return SpotifyResolution(failure = SpotifyMatchFailure.TITLE)
        val failures = mutableSetOf<SpotifyMatchFailure>()
        val visited = mutableSetOf<String>()
        var requests = 0
        fun best(page: SpotifySearchPage, videos: Boolean): SongItem? = page.songs
            .asSequence().filterNot { it.isEpisode }.distinctBy { it.id }
            .mapNotNull { song ->
                val match = SpotifyImportRules.evaluate(track, song.title, song.artists.map { it.name }, song.duration,
                    videos || song.isVideoSong, song.album?.name)
                match.failure?.let(failures::add)
                match.score?.let { song to it }
            }
            .sortedWith(compareByDescending<Pair<SongItem, Int>> { it.second }.thenBy { it.first.id })
            .firstOrNull()?.first
        suspend fun query(value: String, videos: Boolean): SpotifySearchPage? {
            if (requests >= MAX_REQUESTS || !visited.add("$videos:$value")) return null
            requests++
            return retry { search.search(value, videos) }
        }
        suspend fun following(page: SpotifySearchPage?, videos: Boolean): SongItem? {
            var token = page?.continuation
            repeat(2) {
                val next = token ?: return null
                if (requests >= MAX_REQUESTS || !visited.add("continuation:$next")) return null
                requests++
                val more = retry { search.continuation(next) }
                best(more, videos)?.let { return it }
                token = more.continuation
            }
            return null
        }
        val original = "${track.title} ${track.artists.first()}"
        val cleaned = SpotifyImportRules.cleanedQuery(track)
        val first = query(original, false)
        first?.let { best(it, false) }?.let { return SpotifyResolution(song = it) }
        val secondary = if (SpotifyImportRules.normalized(cleaned) != SpotifyImportRules.normalized(original)) query(cleaned, false) else null
        secondary?.let { best(it, false) }?.let { return SpotifyResolution(song = it) }
        for (page in listOfNotNull(secondary, first)) {
            following(page, false)?.let { return SpotifyResolution(song = it) }
        }
        val video = query(cleaned, true)
        video?.let { best(it, true) }?.let { return SpotifyResolution(song = it) }
        following(video, true)?.let { return SpotifyResolution(song = it) }
        // Reserve the general YouTube fallback before spending the remaining budget on ranking variants.
        requests++
        val general = retry { search.generalVideos(cleaned) }
        best(general, true)?.let { return SpotifyResolution(song = it) }
        following(general, true)?.let { return SpotifyResolution(song = it) }
        val title = SpotifyImportRules.cleanedTitle(track)
        val variants = listOf("$title ${track.artists.joinToString(" ")}", "${track.artists.first()} - $title", "\"$title\" ${track.artists.first()}")
            .filter { SpotifyImportRules.normalized(it) !in setOf(SpotifyImportRules.normalized(original), SpotifyImportRules.normalized(cleaned)) || it.contains('"') }
            .distinct()
        for (videos in listOf(false, true)) {
            for (variant in variants) {
                val page = query(variant, videos) ?: continue
                best(page, videos)?.let { return SpotifyResolution(song = it) }
                following(page, videos)?.let { return SpotifyResolution(song = it) }
            }
        }
        for (variant in variants) {
            if (requests >= MAX_REQUESTS) break
            requests++
            val page = retry { search.generalVideos(variant) }
            best(page, true)?.let { return SpotifyResolution(song = it) }
            following(page, true)?.let { return SpotifyResolution(song = it) }
        }
        return SpotifyResolution(failure = failures.maxByOrNull { it.ordinal } ?: SpotifyMatchFailure.NO_RESULTS)
    }

    private companion object { const val MAX_REQUESTS = 16 }

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
