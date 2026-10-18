package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.parseTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Regular YouTube results include uploads absent from the Music catalogue. */
internal object VideoSearchPage {
    private fun JsonElement?.obj(key: String) = (this as? JsonObject)?.get(key) as? JsonObject
    private fun JsonElement?.array(key: String) = ((this as? JsonObject)?.get(key) as? JsonArray).orEmpty()
    private fun JsonElement?.text(): String = (this as? JsonObject)?.let { value ->
        (value["simpleText"] as? JsonPrimitive)?.contentOrNull
            ?: value.array("runs").joinToString("") { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty() }
    }.orEmpty()

    fun parse(response: JsonObject): SearchResult {
        val sections = response.obj("contents").obj("twoColumnSearchResultsRenderer")
            .obj("primaryContents").obj("sectionListRenderer").array("contents")
        val appended = (response.array("onResponseReceivedCommands") + response.array("onResponseReceivedActions"))
            .flatMap { it.obj("appendContinuationItemsAction").array("continuationItems") }
        val contents = (sections + appended).flatMap { item ->
            item.obj("itemSectionRenderer")?.array("contents") ?: listOf(item)
        }
        val songs = contents.mapNotNull { item ->
            val video = item.obj("videoRenderer") ?: return@mapNotNull null
            val id = (video["videoId"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
                ?: return@mapNotNull null
            val title = video["title"].text().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val owner = (video["ownerText"] ?: video["longBylineText"] ?: video["shortBylineText"]).text()
            val artistId = (video["ownerText"] ?: video["longBylineText"] ?: video["shortBylineText"])
                .array("runs").firstOrNull().obj("navigationEndpoint").obj("browseEndpoint")
                ?.get("browseId").let { (it as? JsonPrimitive)?.contentOrNull }
            val thumbnail = video.obj("thumbnail").array("thumbnails").lastOrNull()
                .let { ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull }.orEmpty()
            SongItem(id = id, title = title, artists = listOfNotNull(owner.takeIf(String::isNotBlank)?.let { Artist(it, artistId) }),
                duration = video["lengthText"].text().parseTime(), thumbnail = thumbnail,
                musicVideoType = "MUSIC_VIDEO_TYPE_OMV")
        }.distinctBy { it.id }
        val token = (sections + appended + contents).firstNotNullOfOrNull { item ->
            (item.obj("continuationItemRenderer").obj("continuationEndpoint").obj("continuationCommand")
                ?.get("token") as? JsonPrimitive)?.contentOrNull
        }
        return SearchResult(songs, token)
    }
}
