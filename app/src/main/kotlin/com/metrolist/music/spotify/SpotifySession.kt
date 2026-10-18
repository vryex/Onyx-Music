package com.metrolist.music.spotify

// Tokens belong only to the screen's import session and are never persisted.
internal class SpotifySession(
    val clientId: String,
    accessToken: String,
    refreshToken: String?,
) {
    var accessToken: String = accessToken
        private set
    var refreshToken: String? = refreshToken
        private set

    fun updateTokens(accessToken: String, rotatedRefreshToken: String?) {
        if (accessToken.isBlank() || accessToken == "null") throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
        this.accessToken = accessToken
        if (!rotatedRefreshToken.isNullOrBlank() && rotatedRefreshToken != "null") refreshToken = rotatedRefreshToken
    }
}

internal suspend fun <T> withSpotifyRefresh(request: suspend () -> T, refresh: suspend () -> Unit): T {
    try {
        return request()
    } catch (error: SpotifyImportException) {
        if (error.reason != SpotifyFailure.AUTHORIZATION) throw error
    }
    refresh()
    // Only the interrupted page is retried; a second authorization failure escapes.
    return request()
}
