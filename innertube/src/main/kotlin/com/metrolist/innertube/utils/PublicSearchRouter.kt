package com.metrolist.innertube.utils

internal class PublicSearchRouter(
    private val primaryTimeoutMillis: Long = 4_000,
    private val fallbackTimeoutMillis: Long = 6_000,
    private val cooldownMillis: Long = 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun <T> search(primary: suspend () -> Result<T>, fallback: suspend () -> Result<T>): Result<T> =
        publicSearchFallback(primary, fallback)
}
