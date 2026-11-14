package com.metrolist.music.utils

import com.metrolist.innertubex.InnerTubeHttpException
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class YouTubeConnectionDiagnosticsTest {
    @Test fun reportsTheActualFailureWithoutIncludingResponseBodiesOrCredentials() {
        assertEquals("HTTP 403", YouTubeConnectionDiagnostics.code(InnerTubeHttpException("search", HttpStatusCode.Forbidden)))
        assertEquals("HTTP 429", YouTubeConnectionDiagnostics.code(InnerTubeHttpException("search", HttpStatusCode.TooManyRequests)))
        assertEquals("DNS", YouTubeConnectionDiagnostics.code(IOException("wrapped", UnknownHostException("private-host"))))
        assertEquals("TLS", YouTubeConnectionDiagnostics.code(SSLHandshakeException("secret-cookie")))
        assertEquals("TIMEOUT", YouTubeConnectionDiagnostics.code(SocketTimeoutException("token")))
        assertEquals("IO", YouTubeConnectionDiagnostics.code(IOException("secret-cookie")))
        assertEquals("RESPONSE", YouTubeConnectionDiagnostics.code(IllegalStateException("secret-token")))
    }

    @Test fun stalledRequestFinishesWithTimeoutInsteadOfLeavingLoadingForever() = runBlocking {
        val result = YouTubeConnectionDiagnostics.boundedRequest<Unit>(timeoutMillis = 20) { awaitCancellation() }
        assertTrue(result.isFailure)
        assertEquals("TIMEOUT", YouTubeConnectionDiagnostics.code(result.exceptionOrNull()!!))
    }

    @Test fun cancellingTheScreenRemainsCancellationRatherThanANetworkError() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val job = async {
            YouTubeConnectionDiagnostics.boundedRequest<Unit> { entered.complete(Unit); awaitCancellation() }
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun responseFailureIsPreservedAndSuccessfulRetryReturnsItsValue() = runBlocking {
        val bad = YouTubeConnectionDiagnostics.boundedRequest<Int> { error("invalid response") }
        assertEquals("RESPONSE", YouTubeConnectionDiagnostics.code(bad.exceptionOrNull()!!))
        assertEquals(5, YouTubeConnectionDiagnostics.boundedRequest { 5 }.getOrThrow())
    }
}
