package com.metrolist.innertube.pages

import com.metrolist.innertube.models.SongItem
import org.junit.Assert.*
import org.junit.Test

class SearchSummaryFallbackTest {
    @Test fun hidingVideosRemovesEmptySectionsWithoutLosingTheFallbackSource() {
        val video = SongItem(id = "abcdefghijk", title = "Song", artists = emptyList(),
            duration = 123, thumbnail = "", musicVideoType = "MUSIC_VIDEO_TYPE_OMV")
        val page = SearchSummaryPage(listOf(SearchSummary("Videos", listOf(video))), isVideoFallback = true)
        val hidden = page.filterVideoSongs(true)
        assertTrue(hidden.summaries.isEmpty())
        assertTrue(hidden.isVideoFallback)
        assertTrue(page.filterExplicit(true).isVideoFallback)
        assertTrue(page.filterYoutubeShorts(true).isVideoFallback)
    }

    @Test fun ordinaryMusicSummaryIsNotTreatedAsVideoFallback() {
        assertFalse(SearchSummaryPage(emptyList()).isVideoFallback)
    }
}
