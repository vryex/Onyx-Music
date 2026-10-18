package com.metrolist.innertube.utils

suspend fun <T> publicSearchFallback(
    primary: suspend () -> Result<T>,
    fallback: suspend () -> Result<T>,
): Result<T> = primary()

suspend fun <T> loadYouTubePage(
    fetch: suspend () -> Result<T>,
    publish: (T) -> Unit,
    enrich: suspend (T) -> T,
    onFailure: (Throwable) -> Unit,
    requestTimeoutMillis: Long = 25_000,
    enrichmentTimeoutMillis: Long = 2_500,
) {
    fetch().onSuccess { publish(enrich(it)) }.onFailure(onFailure)
}
