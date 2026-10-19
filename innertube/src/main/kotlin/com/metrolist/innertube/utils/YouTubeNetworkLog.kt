package com.metrolist.innertube.utils

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeNetworkEvent(
    val timestampMs: Long,
    val operation: String,
    val elapsedMs: Long,
    val status: Int?,
    val authenticated: Boolean,
    val failure: String?,
    val serverMessage: String?,
)

object YouTubeNetworkLog {
    private val events = ArrayDeque<YouTubeNetworkEvent>()
    private val operations = setOf("search", "browse", "player", "next", "get_search_suggestions", "get_queue")
    private val failures = setOf("DNS", "TLS", "TIMEOUT", "IO", "HTTP", "RESPONSE")
    private val safeMessages = setOf("Request is forbidden", "HTTP error response (JSON)",
        "HTTP error response (HTML)", "HTTP error response (OTHER)")

    @Synchronized
    fun record(operation: String, elapsedMs: Long, status: Int?, authenticated: Boolean,
               failure: String? = null, serverMessage: String? = null) {
        if (events.size == 100) events.removeFirst()
        events.addLast(YouTubeNetworkEvent(
            timestampMs = System.currentTimeMillis(),
            operation = operation.takeIf { it in operations } ?: "other",
            elapsedMs = elapsedMs.coerceAtLeast(0),
            status = status?.takeIf { it in 100..599 },
            authenticated = authenticated,
            failure = failure?.let { it.takeIf { it in failures } ?: "RESPONSE" },
            serverMessage = serverMessage?.takeIf { it in safeMessages },
        ))
    }

    @Synchronized
    fun snapshot(): List<YouTubeNetworkEvent> = events.toList()

    @Synchronized
    fun clear() { events.clear() }
}
