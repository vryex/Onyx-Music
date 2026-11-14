package com.metrolist.music.playback

import androidx.media3.exoplayer.offline.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUtilTest {
    @Test
    fun `completed downloads are not prepared again`() {
        assertFalse(shouldPrepareDownload(Download.STATE_COMPLETED))
        assertTrue(shouldPrepareDownload(Download.STATE_FAILED))
        assertTrue(shouldPrepareDownload(null))
    }

    @Test
    fun `in-flight downloads are not prepared again`() {
        assertFalse(shouldPrepareDownload(Download.STATE_QUEUED))
        assertFalse(shouldPrepareDownload(Download.STATE_DOWNLOADING))
        assertFalse(shouldPrepareDownload(Download.STATE_REMOVING))
        assertTrue(shouldPrepareDownload(Download.STATE_STOPPED))
    }

    @Test
    fun `downloadArtworkUrls keeps distinct song and album covers`() {
        assertEquals(
            listOf("song-cover", "album-cover"),
            downloadArtworkUrls("song-cover", "album-cover"),
        )
        assertEquals(
            listOf("song-cover"),
            downloadArtworkUrls("song-cover", "song-cover"),
        )
        assertEquals(emptyList<String>(), downloadArtworkUrls("", null))
    }
}
