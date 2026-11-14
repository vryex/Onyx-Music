package com.metrolist.music.spotify

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpotifyImportRepositoryTest {
    @Test fun moreThanTwentyImportsReachReactiveLibraryAndLikesAndReimportPreservesData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        val database = MusicDatabase(room)
        try {
            val originalDate = LocalDateTime.of(2026, 1, 1, 0, 0)
            val original = SongEntity(id = "id-0", title = "Edited", totalPlayTime = 1234,
                lyricsOffset = 500, isDownloaded = true, inLibrary = originalDate)
            database.insert(original)
            val tracks = (0 until 65).map { SpotifyTrack("Song $it", listOf("Artist"), 200) }
            val search = object : SpotifySongSearch {
                override suspend fun search(query: String, videos: Boolean): SpotifySearchPage {
                    val index = query.substringAfter("Song ").substringBefore(' ')
                    return SpotifySearchPage(listOf(SongItem(id = "id-$index", title = "Song $index",
                        artists = listOf(Artist("Artist", null)), duration = 200, thumbnail = "")))
                }
                override suspend fun continuation(token: String) = error("No continuation needed")
            }
            val repository = SpotifyImportRepository(database, SpotifyTrackResolver(search))
            var progress = SpotifyImportProgress()
            repository.import(tracks) { progress = it }
            assertEquals(65, progress.processed)
            assertEquals(65, progress.imported)
            assertEquals(65, database.likedSongsCount().first())
            assertEquals(65, database.likedSongs(SongSortType.CREATE_DATE, true).first().size)
            assertEquals(65, database.songs(SongSortType.CREATE_DATE, true).first().size)
            val imported = database.songEntity("id-0")!!
            assertEquals(original.title, imported.title)
            assertEquals(original.inLibrary, imported.inLibrary)
            assertEquals(original.totalPlayTime, imported.totalPlayTime)
            assertEquals(original.lyricsOffset, imported.lyricsOffset)
            assertTrue(imported.isDownloaded)
            repository.import(tracks) { progress = it }
            assertEquals(0, progress.imported)
            assertEquals(65, progress.already)
            assertEquals(imported, database.songEntity("id-0"))
            assertEquals(65, database.likedSongsCount().first())
        } finally { database.close() }
    }
}
