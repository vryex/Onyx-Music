package com.metrolist.innertube.utils

import com.metrolist.innertubex.InnerTubeHttpException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** A Music search rejection must not make the separate public video search unreachable. */
suspend fun <T> publicSearchFallback(
    primary: suspend () -> Result<T>,
    fallback: suspend () -> Result<T>,
): Result<T> {
    val result = primary()
    currentCoroutineContext().ensureActive()
    val error = result.exceptionOrNull()
    if (error is CancellationException) throw error
    if (error !is InnerTubeHttpException || error.status.value != 403) return result
    return fallback().also {
        currentCoroutineContext().ensureActive()
        (it.exceptionOrNull() as? CancellationException)?.let { cancelled -> throw cancelled }
    }
}

/** Optional metadata may improve a page, but must never gate or erase its usable results. */
suspend fun <T : Any> loadYouTubePage(
    fetch: suspend () -> Result<T>,
    publish: (T) -> Unit,
    enrich: suspend (T) -> T,
    onFailure: (Throwable) -> Unit,
    requestTimeoutMillis: Long = 25_000,
    enrichmentTimeoutMillis: Long = 2_500,
) {
    val result = try {
        withTimeoutOrNull(requestTimeoutMillis) { fetch() }
            ?: Result.failure(SocketTimeoutException("YouTube request timed out"))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
    currentCoroutineContext().ensureActive()
    val page = result.getOrElse {
        if (it is CancellationException) throw it
        onFailure(it)
        return
    }
    publish(page)
    try {
        val enriched = withTimeoutOrNull(enrichmentTimeoutMillis) { enrich(page) }
        currentCoroutineContext().ensureActive()
        if (enriched != null && enriched != page) publish(enriched)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // The base page is already visible; optional metadata failure must not replace it.
    }
}
