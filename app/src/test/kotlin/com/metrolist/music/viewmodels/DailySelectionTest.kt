package com.metrolist.music.viewmodels

import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DailySelectionTest {
    private fun song(id: String, thumbnail: String = "cover") =
        SongItem(id = id, title = id, artists = emptyList(), thumbnail = thumbnail)

    @Test fun `new listener gets online recommendations without local quick picks`() {
        assertEquals(listOf("online"), buildDailySelection(emptyList(), emptyList(), listOf(song("online"))).map { it.id })
    }

    @Test fun `discovery leads then local and online fill without duplicates`() {
        val result = buildDailySelection(listOf(song("discover")), listOf(song("local")), listOf(song("discover"), song("online")))
        assertEquals(listOf("discover", "local", "online"), result.map { it.id })
    }

    @Test fun `unplayable entries are excluded and shelf is bounded`() {
        val result = buildDailySelection(listOf(song(""), song("missing", "")), emptyList(), (1..20).map { song("$it") })
        assertEquals((1..8).map { "$it" }, result.map { it.id })
    }
}
