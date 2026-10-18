package com.metrolist.music.spotify

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpotifySessionTest {
    @Test fun refreshRotationAndOptionalRefreshTokenFollowSpotifyProtocol() {
        val session = SpotifySession("public-client", "first-access", "first-refresh")
        session.updateTokens("second-access", "rotated-refresh")
        assertEquals("second-access", session.accessToken)
        assertEquals("rotated-refresh", session.refreshToken)
        session.updateTokens("third-access", null)
        assertEquals("third-access", session.accessToken)
        assertEquals("rotated-refresh", session.refreshToken)
        assertEquals("public-client", session.clientId)
    }

    @Test fun expiredPageIsRetriedWithRefreshedTokenWithoutRestartingImport() = runBlocking {
        val session = SpotifySession("public-client", "expired-access", "first-refresh")
        val usedTokens = mutableListOf<String>()
        var refreshes = 0
        val result = withSpotifyRefresh(request = {
            usedTokens.add(session.accessToken)
            if (session.accessToken == "expired-access") throw SpotifyImportException(SpotifyFailure.AUTHORIZATION)
            "page-at-offset-150"
        }, refresh = { refreshes++; session.updateTokens("fresh-access", "next-refresh") })
        assertEquals("page-at-offset-150", result)
        assertEquals(listOf("expired-access", "fresh-access"), usedTokens)
        assertEquals(1, refreshes)
        assertEquals("next-refresh", session.refreshToken)
    }

    @Test fun secondUnauthorizedResponseDoesNotLoopAndForbiddenDoesNotRefresh() = runBlocking {
        var refreshes = 0
        var calls = 0
        val failure = runCatching {
            withSpotifyRefresh(request = { calls++; throw SpotifyImportException(SpotifyFailure.AUTHORIZATION) }, refresh = { refreshes++ })
        }.exceptionOrNull()
        assertEquals(SpotifyFailure.AUTHORIZATION, (failure as SpotifyImportException).reason)
        assertEquals(2, calls)
        assertEquals(1, refreshes)
        runCatching {
            withSpotifyRefresh(request = { throw SpotifyImportException(SpotifyFailure.FORBIDDEN) }, refresh = { refreshes++ })
        }
        assertEquals(1, refreshes)
    }

    @Test fun malformedRefreshCannotDestroyCurrentSession() {
        val session = SpotifySession("public-client", "first-access", "first-refresh")
        val failure = runCatching { session.updateTokens("", "unexpected-refresh") }.exceptionOrNull()
        assertEquals(SpotifyFailure.AUTHORIZATION, (failure as SpotifyImportException).reason)
        assertEquals("first-access", session.accessToken)
        assertEquals("first-refresh", session.refreshToken)
    }
}
