package com.metrolist.innertube.utils

import com.metrolist.innertubex.InnerTubeHttpException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class YouTubeRequestsTest {
    @Test fun basePageIsVisibleWhileOptionalMetadataIsStillStalled() = runBlocking {
        val published = CompletableDeferred<String>()
        val enriching = CompletableDeferred<Unit>()
        val job = launch {
            loadYouTubePage(
                fetch = { Result.success("base songs") },
                publish = { published.complete(it) },
                enrich = { enriching.complete(Unit); awaitCancellation() },
                onFailure = { fail("Base request succeeded") },
                enrichmentTimeoutMillis = 30,
            )
        }
        enriching.await()
        assertTrue("Existing results must not wait for metadata", published.isCompleted)
        job.join()
        assertEquals("base songs", published.await())
    }

    @Test fun metadataFailureKeepsTheBasePage() = runBlocking {
        val pages = mutableListOf<String>()
        var errors = 0
        loadYouTubePage(
            fetch = { Result.success("base") }, publish = pages::add,
            enrich = { throw InnerTubeHttpException("search", HttpStatusCode.Forbidden) },
            onFailure = { errors++ },
        )
        assertEquals(listOf("base"), pages)
        assertEquals(0, errors)
    }

    @Test fun initialFailureIsReportedWithoutPublishingAFakeEmptyPage() = runBlocking {
        val error = InnerTubeHttpException("browse", HttpStatusCode.Forbidden)
        var failure: Throwable? = null
        loadYouTubePage<String>(
            fetch = { Result.failure(error) }, publish = { fail("No page received") },
            enrich = { it }, onFailure = { failure = it },
        )
        assertSame(error, failure)
    }

    @Test fun hungInitialRequestHasADeadline() = runBlocking {
        var failure: Throwable? = null
        withTimeout(500) {
            loadYouTubePage<String>(
                fetch = { awaitCancellation() }, publish = { fail("No page received") },
                enrich = { it }, onFailure = { failure = it }, requestTimeoutMillis = 20,
            )
        }
        assertTrue(failure is java.net.SocketTimeoutException)
    }

    @Test fun cancellingMetadataDoesNotPublishStaleResults() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val pages = mutableListOf<String>()
        val job = launch {
            loadYouTubePage(fetch = { Result.success("base") }, publish = pages::add,
                enrich = { started.complete(Unit); awaitCancellation() },
                onFailure = { fail("Cancellation is not a request error") })
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf("base"), pages)
        assertTrue(job.isCancelled)
    }
    @Test fun loadingNextPageCancelsInitialMetadataWithoutErasingNewSongs() = runBlocking {
        val enrichmentStarted = CompletableDeferred<Unit>()
        val releaseMetadata = CompletableDeferred<Unit>()
        var visible = listOf<String>()
        val firstPage = launch {
            loadYouTubePage(fetch = { Result.success(listOf("first")) }, publish = { visible = it },
                enrich = { enrichmentStarted.complete(Unit); releaseMetadata.await(); listOf("enriched first") },
                onFailure = { fail("Initial request succeeded") })
        }
        enrichmentStarted.await()
        firstPage.cancelAndJoin()
        visible = visible + "second page"
        releaseMetadata.complete(Unit)
        yield()
        assertEquals(listOf("first", "second page"), visible)
    }

}
