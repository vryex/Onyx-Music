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
    private val urls = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val emails = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val authorization = Regex("(?:Bearer|SAPISIDHASH)\\s+[^\\s,;]+", RegexOption.IGNORE_CASE)
    private val cookies = Regex("(?:set-cookie|cookie)\\s*:[^\\r\\n]*", RegexOption.IGNORE_CASE)
    private val assignments = Regex("(?:access_token|refresh_token|id_token|poToken|visitorData|SAPISID|SID|token|api[_-]?key)\\s*[=:]\\s*[^\\s,;]+", RegexOption.IGNORE_CASE)
    private val opaqueValues = Regex("[A-Za-z0-9_~+/=-]{24,}")

    @Synchronized
    fun record(operation: String, elapsedMs: Long, status: Int?, authenticated: Boolean,
               failure: String? = null, serverMessage: String? = null, secrets: List<String> = emptyList()) {
        var message = serverMessage?.take(4096)
        secrets.filter { it.length >= 4 }.sortedByDescending { it.length }.forEach { secret ->
            message = message?.replace(secret, "[redacted]")
        }
        for (pattern in listOf(urls, emails, authorization, cookies, assignments, opaqueValues)) {
            message = message?.replace(pattern, "[redacted]")
        }
        if (events.size == 100) events.removeFirst()
        events.addLast(YouTubeNetworkEvent(
            timestampMs = System.currentTimeMillis(),
            operation = operation.takeIf { it in operations } ?: "other",
            elapsedMs = elapsedMs.coerceAtLeast(0),
            status = status?.takeIf { it in 100..599 },
            authenticated = authenticated,
            failure = failure?.let { it.takeIf { it in failures } ?: "RESPONSE" },
            serverMessage = message?.replace(Regex("[\\r\\n\\t]+"), " ")?.take(350),
        ))
    }

    @Synchronized
    fun snapshot(): List<YouTubeNetworkEvent> = events.toList()

    @Synchronized
    fun clear() { events.clear() }
}
