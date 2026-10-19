package com.metrolist.innertube.utils

import com.metrolist.innertubex.InnerTubeHttpException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PublicSearchRouterTest {
    @Test fun rejectedMusicRouteIsSkippedUntilCooldownExpires() = runBlocking {
        var time = 0L
        val router = PublicSearchRouter(cooldownMillis = 100, now = { time })
        var musicCalls = 0
        suspend fun search() = router.search(
            primary = { musicCalls++; Result.failure<String>(InnerTubeHttpException("search", HttpStatusCode.Forbidden)) },
            fallback = { Result.success("video") },
        )
        assertEquals("video", search().getOrThrow())
        assertEquals("video", search().getOrThrow())
        assertEquals("A known rejected endpoint must not delay every query", 1, musicCalls)
        time = 101
        search()
        assertEquals(2, musicCalls)
    }

    @Test fun stalledApiStillLeavesTimeForAlternateTransport() = runBlocking {
        val router = PublicSearchRouter(primaryTimeoutMillis = 20)
        val result = withTimeoutOrNull(500) {
            router.search<String>(primary = { awaitCancellation() }, fallback = { Result.success("html") })
        }
        assertEquals("html", result?.getOrNull())
    }

    @Test fun cancellationDoesNotStartFallbackOrPoisonRouteHealth() = runBlocking {
        val router = PublicSearchRouter()
        val started = CompletableDeferred<Unit>()
        var alternateCalls = 0
        val job = launch {
            router.search<String>(primary = { started.complete(Unit); awaitCancellation() },
                fallback = { alternateCalls++; Result.success("stale") })
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(0, alternateCalls)
        assertEquals("music", router.search(primary = { Result.success("music") },
            fallback = { fail("Cancellation is not a failed route"); Result.success("video") }).getOrThrow())
    }

    @Test fun rateLimitAndAuthenticationDoNotChangeRoutes() = runBlocking {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests)) {
            val router = PublicSearchRouter()
            val error = InnerTubeHttpException("search", status)
            repeat(2) {
                assertSame(error, router.search<String>(primary = { Result.failure(error) },
                    fallback = { fail("Do not amplify rate limits"); Result.success("unexpected") }).exceptionOrNull())
            }
        }
    }
}
