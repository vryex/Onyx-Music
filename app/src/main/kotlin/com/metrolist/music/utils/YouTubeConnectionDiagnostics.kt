package com.metrolist.music.utils

import com.metrolist.innertubex.InnerTubeHttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** Only coarse failure codes are retained, never URLs, cookies, or server bodies. */
object YouTubeConnectionDiagnostics {
    private val mutableFailures = MutableStateFlow<Map<String, String>>(emptyMap())
    val failures = mutableFailures.asStateFlow()

    fun code(error: Throwable): String {
        val causes = generateSequence(error) { it.cause?.takeUnless { cause -> cause === it } }.take(12).toList()
        causes.filterIsInstance<InnerTubeHttpException>().firstOrNull()?.let { return "HTTP ${it.status.value}" }
        if (causes.any { it is UnknownHostException }) return "DNS"
        if (causes.any { it is SSLException }) return "TLS"
        if (causes.any { it is SocketTimeoutException || it is TimeoutCancellationException }) return "TIMEOUT"
        if (causes.any { it is IOException }) return "IO"
        return "RESPONSE"
    }

    fun record(operation: String, error: Throwable) {
        mutableFailures.update { it + (operation to code(error)) }
    }

    fun clear(operation: String) {
        mutableFailures.update { it - operation }
    }

    suspend fun <T> boundedRequest(timeoutMillis: Long = 25_000, request: suspend () -> T): Result<T> = try {
        withTimeoutOrNull(timeoutMillis) { Result.success(request()) }
            ?: Result.failure(SocketTimeoutException("YouTube request timed out"))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
