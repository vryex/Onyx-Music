package com.metrolist.music.spotify

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

internal data class SpotifyTrack(val title: String, val artists: List<String>, val durationSeconds: Int)

internal object SpotifyImportRules {
    const val REGISTERED_REDIRECT = "http://127.0.0.1/callback"
    fun randomSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun matches(track: SpotifyTrack, title: String, artists: List<String>, duration: Int?): Boolean =
        track.title.isNotBlank() && track.artists.isNotEmpty() && track.durationSeconds > 0 && duration != null && duration > 0 &&
            normalized(track.title) == normalized(title) &&
            artists.any { normalized(it) == normalized(track.artists.first()) } &&
            abs(track.durationSeconds - duration) <= 4
}

internal class SpotifyPageGuard {
    private val visited = HashSet<String>()
    fun accept(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == "api.spotify.com" && (uri.port == -1 || uri.port == 443) &&
            uri.rawPath == "/v1/me/tracks" && uri.rawUserInfo == null && uri.fragment == null && visited.add(value)
    }
}
