package com.metrolist.music.spotify

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal data class SpotifyTrack(val title: String, val artists: List<String>, val durationSeconds: Int? = null)

internal object SpotifyImportRules {
    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun matches(track: SpotifyTrack, title: String, artists: List<String>, duration: Int?): Boolean =
        track.title.isNotBlank() && track.artists.isNotEmpty() &&
            normalized(track.title) == normalized(title) &&
            (artists.any { normalized(it) == normalized(track.artists.first()) } ||
                normalized(artists.joinToString(", ")) == normalized(track.artists.first())) &&
            (track.durationSeconds == null || (track.durationSeconds > 0 && duration != null && duration > 0 && abs(track.durationSeconds - duration) <= 4))
}
