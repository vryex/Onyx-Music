package com.metrolist.music.spotify

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SpotifyCsvParserTest {
    private fun parse(csv: String) = SpotifyCsvParser.parse(csv.toByteArray(Charsets.UTF_8))

    @Test fun exportifyReadsBomQuotedCommasEscapedQuotesAndMultilineCrLf() {
        val result = parse("\uFEFFTrack URI,Track Name,Artist Name(s),Track Duration (ms)\r\n" +
            "spotify:track:1,\"Hello, \"\"world\"\"\r\nAgain\",\"First;Second\",200500\r\n" +
            "spotify:track:2,Other,Third,180000\r\n")
        assertEquals(2, result.tracks.size)
        assertEquals("Hello, \"world\"\r\nAgain", result.tracks[0].title)
        assertEquals(listOf("First", "Second"), result.tracks[0].artists)
        assertEquals(200, result.tracks[0].durationSeconds)
        assertEquals(0, result.skippedRows)
    }

    @Test fun semicolonExportWithOptionalDurationAndDuplicates() {
        val result = parse("Name;Artists\nHello;Adele\nHello;Adele\n;Missing title\nOther;Someone\n")
        assertEquals(listOf("Hello", "Other"), result.tracks.map { it.title })
        assertNull(result.tracks.first().durationSeconds)
        assertEquals(2, result.skippedRows)
    }

    @Test fun invalidRowsAreCountedWithoutDroppingOtherSongs() {
        val result = parse("Name,Artists,duration_ms\nHello,Adele,295000\nBad,Artist,not-a-number\nBroken\nOther,Artist,180000\n")
        assertEquals(2, result.tracks.size)
        assertEquals(2, result.skippedRows)
    }

    @Test fun malformedQuotingOrMissingHeadersAreClearErrors() {
        assertFailure(SpotifyFailure.INVALID_CSV, "Name,Artists\n\"Unclosed,Adele")
        assertFailure(SpotifyFailure.INVALID_CSV, "Name,Artists\n\"Closed\"garbage,Adele")
        assertFailure(SpotifyFailure.MISSING_COLUMNS, "Album,Year\n25,2015")
        assertFailure(SpotifyFailure.EMPTY_FILE, "Name,Artists\n,Adele")
    }

    @Test fun nonUtf8InputIsRejectedRatherThanReplacingSongNames() {
        val failure = runCatching { SpotifyCsvParser.parse(byteArrayOf(0xC3.toByte(), 0x28)) }.exceptionOrNull()
        assertEquals(SpotifyFailure.INVALID_CSV, (failure as SpotifyImportException).reason)
    }

    @Test fun limitsRejectWholeFileRatherThanTruncate() {
        val tooMany = "Name,Artists\n" + (0..SpotifyCsvParser.MAX_ROWS).joinToString("\n") { "Song$it,Artist" }
        assertFailure(SpotifyFailure.TOO_MANY_ROWS, tooMany)
        val error = runCatching { SpotifyCsvParser.parse(ByteArray(SpotifyCsvParser.MAX_BYTES + 1)) }.exceptionOrNull()
        assertEquals(SpotifyFailure.FILE_TOO_LARGE, (error as SpotifyImportException).reason)
    }

    @Test fun cancellationIsCheckedWhileParsing() {
        var checks = 0
        val failure = runCatching {
            SpotifyCsvParser.parse(("Name,Artists\n" + (0..2000).joinToString("\n") { "Song$it,Artist" }).toByteArray()) {
                checks++
                if (checks == 3) throw CancellationException("cancelled")
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(3, checks)
    }

    private fun assertFailure(reason: SpotifyFailure, csv: String) {
        val error = runCatching { parse(csv) }.exceptionOrNull()
        assertEquals(reason, (error as SpotifyImportException).reason)
    }
}
