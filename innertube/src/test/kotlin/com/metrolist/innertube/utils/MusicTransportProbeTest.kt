package com.metrolist.innertube.utils

import org.junit.Assert.*
import org.junit.Test

class MusicTransportProbeTest {
    @Test fun classifiesChallengeWithoutRetainingAnyPageText() {
        val page = "<html>Our systems have detected unusual traffic. private@example.com token=secret</html>"
        assertEquals("AUTOMATION_CHALLENGE", classifyMusicRejection("text/html", page))
    }

    @Test fun distinguishesConsentAndGenericDenial() {
        assertEquals("CONSENT", classifyMusicRejection("text/html", "<a href='https://consent.google.com/?private'>Continue</a>"))
        assertEquals("GOOGLE_PERMISSION", classifyMusicRejection("text/html", "Your client does not have permission to get URL /youtubei/v1/search"))
        assertEquals("ACCESS_DENIED", classifyMusicRejection("text/html", "<h1>Access denied</h1> private search term"))
    }

    @Test fun recordsOnlyRecognizedJsonErrorCategories() {
        assertEquals("PERMISSION_DENIED", classifyMusicRejection("application/json", """{"error":{"status":"PERMISSION_DENIED","message":"private query"}}"""))
        assertEquals("UNKNOWN", classifyMusicRejection("application/json", """{"error":{"status":"private-token","message":"private query"}}"""))
    }

    @Test fun malformedAndUnrecognizedPagesRemainUnknown() {
        assertEquals("UNKNOWN", classifyMusicRejection("application/json", "{broken"))
        assertEquals("UNKNOWN", classifyMusicRejection("text/html", "<h1>private search text</h1>"))
    }
}
