package com.metrolist.music.spotify

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class SpotifyTrack(val title: String, val artists: List<String>, val durationSeconds: Int? = null)

internal object SpotifyImportRules {
    private val featured = Regex("(?i)[\\[(]\\s*(?:feat\\.?|ft\\.?|featuring)\\s+([^\\])]+)[\\])]|\\s+(?:feat\\.?|ft\\.?|featuring)\\s+(.+?)(?=\\s+[-–—|]\\s+|\\s*[\\[(]|$)")
    private val displayLabel = Regex("(?i)\\s*[\\[(]\\s*(?:(?:(?:official|oficial)\\s+)?(?:music\\s+)?(?:audio|video|lyrics?|lyric video|visuali[sz]er)(?:\\s+(?:official|oficial))?|official|letra|hd|hq|4k)\\s*[\\])]\\s*")
    private val displaySuffix = Regex("(?i)\\s+(?:[-–—|]\\s*)?(?:(?:official\\s+)(?:music\\s+)?(?:audio|video|lyrics?|lyric video|visuali[sz]er)|lyric video|lyrics|visuali[sz]er|video oficial|audio oficial)\\s*$")
    private val remasterLabel = Regex("(?i)\\s*[\\[(]\\s*(?:[0-9]{4}\\s+)?remaster(?:ed)?(?:\\s+(?:version|[0-9]{4})){0,2}\\s*[\\])]\\s*|\\s+[-–—]\\s*(?:[0-9]{4}\\s+)?remaster(?:ed)?(?:\\s+(?:version|[0-9]{4})){0,2}\\s*$")
    private val connector = Regex("\\b(?:and|with|x|y)\\b")
    private val albumVersion = Regex("(?i)(?:^|[\\[(]|\\s[-–—]\\s)(live(?:\\s+(?:(?:at|from|in)\\b.*|sessions?.*|version))?|unplugged|acoustic|remixes?|slowed|sped up|instrumental|karaoke|covers?)(?:[\\])]|$)")

    internal fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun artistName(value: String): String {
        val name = normalized(value).replace(Regex("(?: topic| official| vevo|vevo)$"), "").trim()
        return when (name) {
            "grupo canaveral de humberto pabon" -> "grupo canaveral"
            else -> name
        }
    }
    private fun sameArtist(a: String, b: String): Boolean = artistName(a).isNotEmpty() && artistName(a).replace(" ", "") == artistName(b).replace(" ", "")
    private fun features(title: String): List<String> = featured.findAll(title).map { it.groupValues[1].ifBlank { it.groupValues[2] } }.toList()
    private fun cleanTitle(title: String): String = featured.replace(remasterLabel.replace(displaySuffix.replace(displayLabel.replace(title, " "), ""), " "), " ").trim()
    private fun coveredNames(text: String, names: List<String>): Boolean {
        var remaining = normalized(text)
        for (name in names.flatMap { listOf(normalized(it), artistName(it)) }.filter(String::isNotBlank).distinct().sortedByDescending(String::length)) {
            remaining = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])").replace(remaining, " ")
        }
        return connector.replace(remaining, " ").isBlank()
    }
    private fun containsLead(text: String, lead: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(artistName(lead))}(?![\\p{L}\\p{N}])").containsMatchIn(artistName(text))
    private fun titleAndCredits(track: SpotifyTrack, title: String): Pair<String, List<String>> {
        for (separator in listOf(" - ", " – ", " — ", " | ")) {
            val index = title.indexOf(separator)
            if (index > 0) {
                val prefix = title.substring(0, index)
                if (coveredNames(prefix, track.artists) && track.artists.any { containsLead(prefix, it) }) {
                    return cleanTitle(title.substring(index + separator.length)) to listOf(prefix)
                }
            }
        }
        return cleanTitle(title) to emptyList()
    }

    fun cleanedQuery(track: SpotifyTrack): String = "${cleanTitle(track.title)} ${artistName(track.artists.first())}".trim()

    fun score(track: SpotifyTrack, title: String, artists: List<String>, duration: Int?, video: Boolean = false, album: String? = null): Int? {
        if (track.title.isBlank() || track.artists.isEmpty()) return null
        val (candidateTitle, prefixCredits) = titleAndCredits(track, title)
        val sourceTitle = normalized(cleanTitle(track.title))
        val normalizedCandidate = normalized(candidateTitle)
        if (sourceTitle.isBlank() || sourceTitle.replace(" ", "") != normalizedCandidate.replace(" ", "")) return null
        val credits = artists + prefixCredits
        val lead = track.artists.first()
        val metadataArtist = artists.any { sameArtist(it, lead) } || sameArtist(artists.joinToString(", "), lead)
        val prefixArtist = prefixCredits.any { coveredNames(it, track.artists) && containsLead(it, lead) }
        if (!metadataArtist && !prefixArtist) return null
        val sourceFeatures = features(track.title)
        val candidateFeatures = features(title)
        if (sourceFeatures.any { !coveredNames(it, credits + candidateFeatures) }) return null
        if (candidateFeatures.any { !coveredNames(it, track.artists + sourceFeatures) }) return null
        // Album-level performance labels can reveal an alternative recording even when its song title omits them.
        for (version in albumVersion.findAll(album.orEmpty())) {
            val kind = normalized(version.groupValues[1]).substringBefore(' ').let { if (it.startsWith("remix")) "remix" else it.removeSuffix("s") }
            if (!Regex("\\b${Regex.escape(kind)}s?\\b").containsMatchIn(sourceTitle)) return null
        }
        var durationScore = 0
        track.durationSeconds?.let { expected ->
            if (expected <= 0 || duration == null || duration <= 0) return null
            val difference = abs(expected - duration)
            val tolerance = if (video) 20 else (expected * 0.035).toInt().coerceIn(6, 12)
            if (difference > tolerance) return null
            durationScore = 25 - difference
        }
        val collaboratorScore = track.artists.drop(1).count { source -> credits.any { sameArtist(it, source) } } * 3
        return (if (sourceTitle == normalizedCandidate) 50 else 45) + (if (metadataArtist) 25 else 15) +
            durationScore + collaboratorScore + (if (video) 0 else 5)
    }

    fun matches(track: SpotifyTrack, title: String, artists: List<String>, duration: Int?): Boolean = score(track, title, artists, duration) != null
}
