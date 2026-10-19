/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.metrolist.innertube.utils.loadYouTubePage
import com.metrolist.music.utils.YouTubeConnectionDiagnostics
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = SearchRoutes.decodeQuery(savedStateHandle.get<String>("query").orEmpty())
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
    var connectionError by mutableStateOf<String?>(null)
        private set
    private var requestJob: Job? = null
    private var moreJob: Job? = null
    private var continuationFailed = false

    private suspend fun resolveSearchMetadata(items: List<YTItem>): List<YTItem> =
        coroutineScope {
            val knownDurations =
                items
                    .filterIsInstance<SongItem>()
                    .mapNotNull { song -> song.duration?.let { song.id to it } }
                    .toMap()
            val missingDurationIds =
                items
                    .filterIsInstance<SongItem>()
                    .filter { it.duration == null && it.id !in knownDurations }
                    .map { it.id }
                    .distinct()
            val resolvedArtists = async { YouTube.resolveArtistIds(items) }
            val fetchedDurations =
                async {
                    if (missingDurationIds.isEmpty()) {
                        emptyMap()
                    } else {
                        YouTube
                            .queue(videoIds = missingDurationIds)
                            .getOrDefault(emptyList())
                            .mapNotNull { song -> song.duration?.let { song.id to it } }
                            .toMap()
                    }
                }
            val durations = knownDurations + fetchedDurations.await()

            resolvedArtists.await().map { item ->
                if (item is SongItem && item.duration == null) {
                    item.copy(duration = durations[item.id])
                } else {
                    item
                }
            }
        }

    private suspend fun filterItems(items: List<YTItem>): List<YTItem> = items
        .filterExplicit(context.dataStore.get(HideExplicitKey, false))
        .filterVideoSongs(context.dataStore.get(HideVideoSongsKey, false))
        .filterYoutubeShorts(context.dataStore.get(HideYoutubeShortsKey, false))

    private fun requestFailed(error: Throwable) {
        connectionError = YouTubeConnectionDiagnostics.code(error)
        reportException(error)
    }

    private fun publishSummary(page: SearchSummaryPage) {
        summaryPage = page
        if (filter.value == YouTube.SearchFilter.FILTER_EPISODE) {
            val episodes = page.summaries.firstOrNull { it.title == "Episodes" }?.items.orEmpty()
            viewStateMap[YouTube.SearchFilter.FILTER_EPISODE.value] = ItemsPage(episodes, null)
        }
    }

    private suspend fun loadSummaryPage() {
        summaryPage?.let { publishSummary(it); return }
        loadYouTubePage(
            fetch = {
                YouTube.searchSummary(query, recordSearchHistory = !context.dataStore.get(PauseSearchHistoryKey, false))
                    .map { page -> page.copy(summaries = page.summaries.map { it.copy(items = filterItems(it.items)) }.filter { it.items.isNotEmpty() }) }
            },
            publish = ::publishSummary,
            enrich = { page ->
                val resolved = resolveSearchMetadata(page.summaries.flatMap { it.items })
                var offset = 0
                page.copy(summaries = page.summaries.map { summary ->
                    val start = offset
                    offset += summary.items.size
                    summary.copy(items = resolved.subList(start, offset))
                })
            },
            onFailure = ::requestFailed,
        )
    }

    private fun loadCurrentFilter() {
        requestJob?.cancel()
        moreJob?.cancel()
        val selected = filter.value
        requestJob = viewModelScope.launch {
            connectionError = null
            continuationFailed = false
            if (selected == null || selected == YouTube.SearchFilter.FILTER_EPISODE) {
                loadSummaryPage()
            } else if (viewStateMap[selected.value] == null) {
                loadYouTubePage(
                    fetch = {
                        YouTube.search(query, selected, recordSearchHistory = !context.dataStore.get(PauseSearchHistoryKey, false))
                            .map { it.copy(items = filterItems(it.items).distinctBy { item -> item.id }) }
                    },
                    publish = { viewStateMap[selected.value] = ItemsPage(it.items, it.continuation) },
                    enrich = { it.copy(items = resolveSearchMetadata(it.items)) },
                    onFailure = ::requestFailed,
                )
            }
        }
    }

    init {
        viewModelScope.launch { filter.collect { loadCurrentFilter() } }
    }

    fun retry() {
        if (continuationFailed) loadMore() else loadCurrentFilter()
    }

    fun loadMore() {
        if (moreJob?.isActive == true) return
        val selected = filter.value ?: return
        val previous = viewStateMap[selected.value] ?: return
        val continuation = previous.continuation ?: return
        requestJob?.cancel()
        moreJob = viewModelScope.launch {
            connectionError = null
            continuationFailed = false
            loadYouTubePage(
                fetch = {
                    YouTube.searchContinuation(continuation, recordSearchHistory = !context.dataStore.get(PauseSearchHistoryKey, false))
                        .map { it.copy(items = filterItems(it.items)) }
                },
                publish = {
                    if (filter.value == selected) {
                        viewStateMap[selected.value] = ItemsPage((previous.items + it.items).distinctBy { item -> item.id }, it.continuation)
                    }
                },
                enrich = { it },
                onFailure = { continuationFailed = true; requestFailed(it) },
            )
        }
    }
}
