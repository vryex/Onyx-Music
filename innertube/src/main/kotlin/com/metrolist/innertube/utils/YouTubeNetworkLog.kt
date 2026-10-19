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
    fun record(operation: String, elapsedMs: Long, status: Int?, authenticated: Boolean,
               failure: String? = null, serverMessage: String? = null, secrets: List<String> = emptyList()) = Unit
    fun snapshot(): List<YouTubeNetworkEvent> = emptyList()
    fun clear() = Unit
}
