package com.metrolist.music.ui.player

import android.app.Application
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class BoundedVideoDataSourceTest {
    @Test fun `Media3 receives the whole logical file through successive bounded HTTP requests`() {
        val bytes = ByteArray(31) { it.toByte() }
        val requests = mutableListOf<Pair<Long, Long>>()
        val factory = DataSource.Factory {
            val delegate = ByteArrayDataSource(bytes)
            object : DataSource by delegate {
                override fun open(dataSpec: DataSpec): Long {
                    requests += dataSpec.position to dataSpec.length
                    return delegate.open(dataSpec)
                }
            }
        }
        val source = BoundedVideoDataSource(factory, bytes.size.toLong(), 4)
        val spec = DataSpec.Builder().setUri("https://example.com/video").setPosition(7).setLength(17).build()
        try {
            assertEquals(17L, source.open(spec))
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(11)
            while (true) {
                val count = source.read(buffer, 0, buffer.size)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            assertArrayEquals(bytes.copyOfRange(7, 24), output.toByteArray())
            assertEquals(listOf(7L to 4L, 11L to 4L, 15L to 4L, 19L to 4L, 23L to 1L), requests)
        } finally { source.close() }
    }
}
