package com.metrolist.music.spotify

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal enum class SpotifyImportPhase { IDLE, PREPARED, RUNNING, PAUSED, COMPLETE, CANCELLED, ERROR }

internal data class SpotifyImportSnapshot(
    val id: String = "",
    val revision: Long = 0,
    val requestId: String = "",
    val phase: SpotifyImportPhase = SpotifyImportPhase.IDLE,
    val fileName: String = "",
    val spotifySource: Boolean = false,
    val skippedRows: Int = 0,
    val localeTag: String = "",
    val progress: SpotifyImportProgress = SpotifyImportProgress(),
    val remaining: List<SpotifyTrack> = emptyList(),
    val failure: SpotifyFailure? = null,
) {
    val active get() = phase == SpotifyImportPhase.PREPARED || phase == SpotifyImportPhase.RUNNING
}

/** The queue is immutable; frequent checkpoints contain only indices and counters. */
internal class SpotifyImportCheckpoint(directory: File) {
    private val queueFile = AtomicFile(File(directory, "spotify-import-queue.json"))
    private val progressFile = AtomicFile(File(directory, "spotify-import-progress.json"))
    private var queueId = ""
    private var tracks = emptyList<SpotifyTrack>()
    private var indices = emptyMap<SpotifyTrack, Int>()
    private var savedRevision = -1L

    @Synchronized fun begin(snapshot: SpotifyImportSnapshot, source: List<SpotifyTrack>) {
        val data = JSONObject().put("version", 1).put("id", snapshot.id)
            .put("tracks", JSONArray().apply { source.forEach { track ->
                put(JSONObject().put("title", track.title).put("artists", JSONArray(track.artists))
                    .put("duration", track.durationSeconds ?: JSONObject.NULL))
            } })
        write(queueFile, data)
        queueId = snapshot.id
        tracks = source.toList()
        indices = tracks.withIndex().associate { it.value to it.index }
        savedRevision = -1
        save(snapshot)
    }

    @Synchronized fun save(snapshot: SpotifyImportSnapshot) {
        if (snapshot.id != queueId || snapshot.revision <= savedRevision) return
        fun indexed(values: List<SpotifyTrack>) = JSONArray(values.map { requireNotNull(indices[it]) })
        val progress = snapshot.progress
        val data = JSONObject().put("version", 1).put("id", snapshot.id).put("revision", snapshot.revision)
            .put("requestId", snapshot.requestId).put("phase", snapshot.phase.name).put("fileName", snapshot.fileName)
            .put("spotifySource", snapshot.spotifySource).put("skippedRows", snapshot.skippedRows)
            .put("locale", snapshot.localeTag).put("processed", progress.processed).put("total", progress.total)
            .put("imported", progress.imported).put("already", progress.already)
            .put("remaining", indexed(snapshot.remaining)).put("unmatched", indexed(progress.unmatchedTracks))
            .put("failure", snapshot.failure?.name ?: JSONObject.NULL)
        write(progressFile, data)
        savedRevision = snapshot.revision
    }

    @Synchronized fun load(): SpotifyImportSnapshot? {
        if (!queueFile.baseFile.exists() && !File(queueFile.baseFile.path + ".bak").exists()) return null
        val queue = JSONObject(queueFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val saved = JSONObject(progressFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        require(queue.getInt("version") == 1 && saved.getInt("version") == 1 && queue.getString("id") == saved.getString("id"))
        val values = queue.getJSONArray("tracks")
        tracks = List(values.length()) { index -> values.getJSONObject(index).let { track ->
            val artists = track.getJSONArray("artists")
            SpotifyTrack(track.getString("title"), List(artists.length()) { artists.getString(it) },
                if (track.isNull("duration")) null else track.getInt("duration"))
        } }
        queueId = queue.getString("id")
        indices = tracks.withIndex().associate { it.value to it.index }
        savedRevision = saved.getLong("revision")
        fun restored(key: String): List<SpotifyTrack> = saved.getJSONArray(key).let { array ->
            List(array.length()) { tracks[array.getInt(it)] }
        }
        val remaining = restored("remaining")
        val unmatched = restored("unmatched")
        val phase = SpotifyImportPhase.valueOf(saved.getString("phase"))
        return SpotifyImportSnapshot(id = queueId, revision = savedRevision, requestId = saved.optString("requestId"),
            phase = if (phase == SpotifyImportPhase.RUNNING || phase == SpotifyImportPhase.PREPARED) SpotifyImportPhase.PAUSED else phase,
            fileName = saved.getString("fileName"), spotifySource = saved.getBoolean("spotifySource"),
            skippedRows = saved.getInt("skippedRows"), localeTag = saved.getString("locale"),
            progress = SpotifyImportProgress(processed = saved.getInt("processed"), total = saved.getInt("total"),
                imported = saved.getInt("imported"), already = saved.getInt("already"),
                unmatched = unmatched.map { it.title + " — " + it.artists.joinToString() }, unmatchedTracks = unmatched,
                remainingTracks = remaining), remaining = remaining,
            failure = saved.optString("failure").takeIf { it.isNotEmpty() && it != "null" }?.let(SpotifyFailure::valueOf))
    }

    private fun write(file: AtomicFile, data: JSONObject) {
        val output = file.startWrite()
        try {
            output.write(data.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
