package com.metrolist.music.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CollectionCoverTest {
    private lateinit var db: InternalDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), InternalDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun `liked cover follows newest like and updates when unliked`() = runBlocking {
        val now = LocalDateTime.now()
        val first = SongEntity(id = "a", title = "A", thumbnailUrl = "cover-a", liked = true, likedDate = now)
        val second = SongEntity(id = "b", title = "B", thumbnailUrl = "cover-b", liked = true, likedDate = now.plusSeconds(1))
        db.dao.insert(first)
        db.dao.insert(second)
        assertEquals("cover-b", db.dao.autoPlaylistCover("liked").first())
        db.dao.update(second.copy(liked = false))
        assertEquals("cover-a", db.dao.autoPlaylistCover("liked").first())
    }

    @Test fun `playlist cover respects song order rather than insert order`() = runBlocking {
        db.dao.insert(SongEntity(id = "a", title = "A", thumbnailUrl = "cover-a"))
        db.dao.insert(SongEntity(id = "b", title = "B", thumbnailUrl = "cover-b"))
        db.dao.insert(PlaylistEntity(id = "list", name = "List"))
        db.dao.insert(PlaylistSongMap(playlistId = "list", songId = "a", position = 1))
        db.dao.insert(PlaylistSongMap(playlistId = "list", songId = "b", position = 0))
        assertEquals("cover-b", db.dao.firstPlaylistCover("list").first())
        assertNull(db.dao.firstPlaylistCover("empty").first())
    }
}
