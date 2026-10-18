package com.metrolist.music.ui.player

import java.io.ByteArrayInputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkedVideoInputStreamTest {
    @Test fun `a clip continues across range boundaries instead of ending after the first chunk`() {
        val bytes = ByteArray(37) { it.toByte() }
        val requests = mutableListOf<Pair<Long, Long>>()
        val input = ChunkedVideoInputStream(bytes.size.toLong(), 8) { offset, length ->
            requests += offset to length
            ByteArrayInputStream(bytes, offset.toInt(), length.toInt())
        }
        assertArrayEquals(bytes, input.use { it.readBytes() })
        assertEquals(listOf(0L to 8L, 8L to 8L, 16L to 8L, 24L to 8L, 32L to 5L), requests)
    }

    @Test fun `a seek starts at its requested offset and stops at the requested length`() {
        val bytes = ByteArray(50) { it.toByte() }
        val input = ChunkedVideoInputStream(13, 5) { offset, length ->
            ByteArrayInputStream(bytes, 17 + offset.toInt(), length.toInt())
        }
        assertArrayEquals(bytes.copyOfRange(17, 30), input.use { it.readBytes() })
    }

    @Test(expected = EOFException::class)
    fun `a truncated range is an error rather than a successful end of video`() {
        ChunkedVideoInputStream(20, 8) { _, _ -> ByteArrayInputStream(byteArrayOf(1, 2)) }
            .use { it.readBytes() }
    }
}
