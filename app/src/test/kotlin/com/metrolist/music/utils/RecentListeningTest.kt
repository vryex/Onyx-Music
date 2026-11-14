package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentListeningTest {
    @Test fun `each started song appears without waiting for history threshold`() {
        val ids = listOf("a", "b", "c").fold(emptyList<String>()) { ids, id -> recentSongIds(id, ids) }
        assertEquals(listOf("c", "b", "a"), ids)
    }
    @Test fun `replays move to front without duplicates`() {
        assertEquals(listOf("a", "c", "b"), recentSongIds("a", listOf("c", "a", "b")))
    }
    @Test fun `recent songs are bounded and reject blank ids`() {
        val previous = (1..20).map(Int::toString)
        assertEquals(previous, recentSongIds("", previous))
        assertEquals(listOf("new") + previous.take(19), recentSongIds("new", previous))
    }
}
