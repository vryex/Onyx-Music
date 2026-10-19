package com.metrolist.innertube.utils

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class YouTubeNetworkLogTest {
    @After fun clear() = YouTubeNetworkLog.clear()

    @Test fun recordsHttpFailureWithTimingAndSessionPresence() {
        YouTubeNetworkLog.record("search", 1234, 403, true, serverMessage = "Request is forbidden")
        val item = YouTubeNetworkLog.snapshot().single()
        assertEquals("search", item.operation)
        assertEquals(403, item.status)
        assertEquals(1234L, item.elapsedMs)
        assertTrue(item.authenticated)
        assertEquals("Request is forbidden", item.serverMessage)
        assertTrue(item.timestampMs > 0)
    }

    @Test fun exportCannotRetainCredentialsUrlsOrUnknownOperationText() {
        val cookie = "very-private-cookie"
        YouTubeNetworkLog.record("search?query=private", 1, 403, true,
            serverMessage = "$cookie Bearer secret-token SAPISIDHASH 123_private Cookie: SID=private; https://host/path?token=private person@example.com access_token=private",
            secrets = listOf(cookie))
        val item = YouTubeNetworkLog.snapshot().single()
        assertEquals("other", item.operation)
        val exported = item.serverMessage.orEmpty()
        for (secret in listOf(cookie, "secret-token", "123_private", "SID=private", "https://", "person@example.com", "access_token=private")) {
            assertFalse("Leaked $secret in $exported", exported.contains(secret))
        }
    }

    @Test fun retainsOnlyTheLastHundredEventsAndClearsThem() {
        repeat(130) { YouTubeNetworkLog.record("browse", it.toLong(), 200, false) }
        val items = YouTubeNetworkLog.snapshot()
        assertEquals(100, items.size)
        assertEquals(30L, items.first().elapsedMs)
        assertEquals(129L, items.last().elapsedMs)
        YouTubeNetworkLog.clear()
        assertTrue(YouTubeNetworkLog.snapshot().isEmpty())
        assertEquals(100, items.size)
    }

    @Test fun recordsDnsAndTimeoutWithoutExceptionMessages() {
        YouTubeNetworkLog.record("player", -1, null, false, failure = "DNS")
        val item = YouTubeNetworkLog.snapshot().single()
        assertEquals("DNS", item.failure)
        assertEquals(0L, item.elapsedMs)
        assertNull(item.serverMessage)
        assertNull(item.status)
    }
}
