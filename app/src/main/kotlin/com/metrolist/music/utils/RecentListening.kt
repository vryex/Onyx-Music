package com.metrolist.music.utils

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

private val RecentlyHeardIds = stringPreferencesKey("sonet_recently_heard_ids")

internal fun recentSongIds(current: String, previous: List<String>): List<String> =
    if (current.isBlank()) previous else (listOf(current) + previous).distinct().take(20)

suspend fun Context.recordRecentlyHeard(metadata: MediaMetadata, database: MusicDatabase) {
    // Keep quick listens separate from timed history and statistics.
    database.withTransaction { insert(metadata) }
    safeDataStoreEdit { prefs ->
        val previous = prefs[RecentlyHeardIds].orEmpty().lineSequence().filter(String::isNotBlank).toList()
        prefs[RecentlyHeardIds] = recentSongIds(metadata.id, previous).joinToString("\n")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Context.recentlyHeardSongs(database: MusicDatabase) = combine(
    dataStore.data.map { it[RecentlyHeardIds].orEmpty() }.distinctUntilChanged(),
    database.recentlyHeardSongs(20),
) { encoded, history ->
    encoded.lineSequence().filter(String::isNotBlank).toList() to history.map { it.song }
}.mapLatest { (ids, history) ->
    val songs = database.getSongsByIds(ids).associateBy { it.id }
    (ids.mapNotNull(songs::get) + history).distinctBy { it.id }.take(20)
}.flowOn(Dispatchers.IO)
