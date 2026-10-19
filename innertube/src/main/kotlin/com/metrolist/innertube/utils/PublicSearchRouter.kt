package com.metrolist.innertube.utils

import com.metrolist.innertubex.InnerTubeHttpException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Remember a rejected API briefly instead of paying for its failure on every search. */
internal class PublicSearchRouter(
    private val primaryTimeoutMillis: Long = 5_000,
    private val fallbackTimeoutMillis: Long = 6_000,
    private val cooldownMillis: Long = 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val blockedUntil = AtomicLong(Long.MIN_VALUE)

    suspend fun <T> search(primary: suspend () -> Result<T>, fallback: suspend () -> Result<T>): Result<T> {
        currentCoroutineContext().ensureActive()
        if (now() < blockedUntil.get()) return bounded(fallbackTimeoutMillis, fallback)
        val result = bounded(primaryTimeoutMillis, primary)
        val error = result.exceptionOrNull()
        if (error == null) {
            blockedUntil.set(Long.MIN_VALUE)
            return result
        }
        if (!(error is InnerTubeHttpException && error.status.value == 403) && error !is SocketTimeoutException) {
            return result
        }
        blockedUntil.set(now() + cooldownMillis)
        return bounded(fallbackTimeoutMillis, fallback)
    }

    private suspend fun <T> bounded(timeoutMillis: Long, request: suspend () -> Result<T>): Result<T> {
        val result = try {
            withTimeoutOrNull(timeoutMillis) { request() }
                ?: Result.failure(SocketTimeoutException("YouTube search route timed out"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        return result
    }
}
