package com.metrolist.music.spotify

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal data class SpotifyCsvPreview(val tracks: List<SpotifyTrack>, val skippedRows: Int)

internal object SpotifyCsvParser {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_ROWS = 20_000

    fun parse(bytes: ByteArray, checkCancelled: () -> Unit = {}): SpotifyCsvPreview {
        if (bytes.size > MAX_BYTES) throw SpotifyImportException(SpotifyFailure.FILE_TOO_LARGE)
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) { throw SpotifyImportException(SpotifyFailure.INVALID_CSV) }
        val delimiter = delimiter(text, checkCancelled)
        val rows = records(text, delimiter, checkCancelled)
        if (rows.isEmpty()) throw SpotifyImportException(SpotifyFailure.EMPTY_FILE)
        fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val headers = rows.first().map(::normalized)
        val titleIndex = headers.indexOfFirst { it in setOf("trackname", "name", "title", "songname") }
        val artistIndex = headers.indexOfFirst { it in setOf("artistnames", "artistname", "artists", "artist") }
        val durationIndex = headers.indexOfFirst { it in setOf("trackdurationms", "durationms") }
        if (titleIndex < 0 || artistIndex < 0) throw SpotifyImportException(SpotifyFailure.MISSING_COLUMNS)
        var skipped = 0
        val tracks = LinkedHashSet<SpotifyTrack>()
        rows.drop(1).forEach { row ->
            checkCancelled()
            val title = row.getOrNull(titleIndex)?.trim().orEmpty()
            val artists = row.getOrNull(artistIndex)?.split(';', '|')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
            val rawDuration = row.getOrNull(durationIndex)?.trim().orEmpty()
            val milliseconds = rawDuration.takeIf(String::isNotBlank)?.toLongOrNull()
            if (title.isBlank() || artists.isEmpty() || row.size != headers.size ||
                (rawDuration.isNotBlank() && (milliseconds == null || milliseconds <= 0 || milliseconds / 1000 > Int.MAX_VALUE))) {
                skipped++
            } else {
                val track = SpotifyTrack(title, artists, milliseconds?.div(1000)?.toInt())
                if (!tracks.add(track)) skipped++
            }
        }
        if (tracks.isEmpty()) throw SpotifyImportException(SpotifyFailure.EMPTY_FILE)
        return SpotifyCsvPreview(tracks.toList(), skipped)
    }

    private fun delimiter(text: String, checkCancelled: () -> Unit): Char {
        var quoted = false
        var commas = 0
        var semicolons = 0
        for ((index, char) in text.withIndex()) {
            if (index % 1024 == 0) checkCancelled()
            if (char == '"') quoted = !quoted
            if (!quoted) when (char) { ',' -> commas++; ';' -> semicolons++; '\r', '\n' -> break }
        }
        return if (semicolons > commas) ';' else ','
    }

    private fun records(text: String, delimiter: Char, checkCancelled: () -> Unit): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var index = 0
        fun endField() { row.add(field.toString()); field.setLength(0); afterQuote = false }
        fun endRow() {
            endField()
            if (row.any(String::isNotBlank)) {
                rows.add(row)
                if (rows.size > MAX_ROWS + 1) throw SpotifyImportException(SpotifyFailure.TOO_MANY_ROWS)
            }
            row = ArrayList()
        }
        while (index < text.length) {
            if (index % 1024 == 0) checkCancelled()
            val char = text[index++]
            if (quoted) {
                if (char == '"') {
                    if (index < text.length && text[index] == '"') { field.append('"'); index++ }
                    else { quoted = false; afterQuote = true }
                } else field.append(char)
            } else when {
                char == delimiter -> endField()
                char == '\r' || char == '\n' -> {
                    if (char == '\r' && index < text.length && text[index] == '\n') index++
                    endRow()
                }
                afterQuote && char.isWhitespace() -> Unit
                afterQuote -> throw SpotifyImportException(SpotifyFailure.INVALID_CSV)
                char == '"' && field.isEmpty() -> quoted = true
                char == '"' -> throw SpotifyImportException(SpotifyFailure.INVALID_CSV)
                else -> field.append(char)
            }
        }
        if (quoted) throw SpotifyImportException(SpotifyFailure.INVALID_CSV)
        if (field.isNotEmpty() || row.isNotEmpty() || afterQuote) endRow()
        return rows
    }
}
