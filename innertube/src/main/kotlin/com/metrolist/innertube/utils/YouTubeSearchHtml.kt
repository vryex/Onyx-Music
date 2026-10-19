package com.metrolist.innertube.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Read the embedded JSON, without evaluating any script from the public search page. */
internal object YouTubeSearchHtml {
    private val assignment = Regex("""(?:var\s+ytInitialData|window\["ytInitialData"\]|ytInitialData)\s*=\s*""")

    fun parse(html: String): JsonObject {
        val start = assignment.find(html)?.range?.last?.plus(1)
            ?: error("YouTube HTML search data unavailable")
        require(html.getOrNull(start) == '{') { "Invalid YouTube HTML search data" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val data = Json.parseToJsonElement(html.substring(start, index + 1)) as? JsonObject
                                ?: error("Invalid YouTube HTML search data")
                            val contents = data["contents"] as? JsonObject
                            require(contents?.get("twoColumnSearchResultsRenderer") is JsonObject) {
                                "YouTube HTML search results unavailable"
                            }
                            return data
                        }
                    }
                }
            }
        }
        error("Incomplete YouTube HTML search data")
    }
}
