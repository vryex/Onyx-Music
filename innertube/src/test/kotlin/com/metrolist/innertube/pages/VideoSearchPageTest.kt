package com.metrolist.innertube.pages

import com.metrolist.innertube.models.SongItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class VideoSearchPageTest {
    private val video = """{"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"Artist - Song (Official Video)"}]},"ownerText":{"runs":[{"text":"Artist - Topic","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}}]},"lengthText":{"simpleText":"3:20"},"thumbnail":{"thumbnails":[{"url":"small"},{"url":"large"}]}}}"""
    private val next = """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-page"}}}}"""
    private fun parse(value: String) = VideoSearchPage.parse(Json.parseToJsonElement(value).jsonObject)

    @Test fun parsesRegularVideoResultsAndContinuationWithoutAdsOrPlaylists() {
        val response = """{"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[$video,$video,{"adSlotRenderer":$video},{"playlistRenderer":{"playlistId":"playlist"}}]}},$next]}}}}}"""
        val result = parse(response)
        val song = result.items.single() as SongItem
        assertEquals("abcdefghijk", song.id)
        assertEquals("Artist - Song (Official Video)", song.title)
        assertEquals("Artist - Topic", song.artists.single().name)
        assertEquals("UCartist", song.artists.single().id)
        assertEquals(200, song.duration)
        assertEquals("large", song.thumbnail)
        assertTrue(song.isVideoSong)
        assertEquals("next-page", result.continuation)
    }

    @Test fun parsesContinuationAndKeepsMissingDurationForMatcherToReject() {
        val missing = video.replace("\"lengthText\":{\"simpleText\":\"3:20\"},", "")
        val result = parse("""{"onResponseReceivedCommands":[{"appendContinuationItemsAction":{"continuationItems":[{"itemSectionRenderer":{"contents":[$missing]}},$next]}}]}""")
        assertEquals(1, result.items.size)
        assertNull((result.items.single() as SongItem).duration)
        assertEquals("next-page", result.continuation)
        assertTrue(parse("""{"contents":null}""").items.isEmpty())
    }
}
