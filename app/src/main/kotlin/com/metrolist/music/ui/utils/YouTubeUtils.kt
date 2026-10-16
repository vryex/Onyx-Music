/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("LocalVariableName")

package com.metrolist.music.ui.utils

import kotlin.math.roundToInt

private val GOOGLEUSERCONTENT_SIZE_PATTERN =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=w(\\d+)-h(\\d+)[^?]*(\\?.*)?$")
private val GGPHT_SIZE_PATTERN =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")
fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    GOOGLEUSERCONTENT_SIZE_PATTERN
        .matchEntire(this)
        ?.groupValues
        ?.let { group ->
            val originalWidth = group[2].toInt()
            val originalHeight = group[3].toInt()
            val query = group[4]
            val targetWidth = width ?: ((height!!.toDouble() * originalWidth) / originalHeight).roundToInt()
            val targetHeight = height ?: ((width!!.toDouble() * originalHeight) / originalWidth).roundToInt()
            return "${group[1]}=w${targetWidth.coerceAtLeast(1)}-h${targetHeight.coerceAtLeast(1)}-p-l90-rj$query"
        }

    GGPHT_SIZE_PATTERN.matchEntire(this)?.groupValues?.let { group ->
        val query = group[2]
        return if (width != null && height != null) {
            "${group[1]}=w$width-h$height-p-l90-rj$query"
        } else {
            "${group[1]}=s${width ?: height}$query"
        }
    }

    return this
}

/** Request the large cover only on known image hosts; retain the source as UI fallback. */
fun String.recommendationArtworkUrl(): String {
    val resized = resize(1080, 1080)
    if (resized != this) return resized
    val match = Regex("^https://i[1-4]?\\.ytimg\\.com/(vi|vi_webp)/([A-Za-z0-9_-]{11})/[^/?]+(?:\\?.*)?$").matchEntire(this)
        ?: return this
    val extension = if (match.groupValues[1] == "vi_webp") "webp" else "jpg"
    return "https://i.ytimg.com/${match.groupValues[1]}/${match.groupValues[2]}/maxresdefault.$extension"
}
