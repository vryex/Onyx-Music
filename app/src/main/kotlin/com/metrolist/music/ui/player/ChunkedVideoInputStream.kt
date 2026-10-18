package com.metrolist.music.ui.player

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Exposes one continuous file while the server accepts only bounded byte ranges. */
internal class ChunkedVideoInputStream(
    private val length: Long,
    private val chunkSize: Long,
    private val openChunk: (offset: Long, length: Long) -> InputStream,
) : InputStream() {
    private var position = 0L
    private var remainingInChunk = 0L
    private var current: InputStream? = null
    private var closed = false

    init { require(length >= 0 && chunkSize > 0) }

    fun connect() {
        check(!closed)
        if (current == null && position < length) {
            remainingInChunk = minOf(chunkSize, length - position)
            current = openChunk(position, remainingInChunk)
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (closed) throw IOException("Video stream closed")
        if (offset < 0 || count < 0 || offset > buffer.size - count) throw IndexOutOfBoundsException()
        if (count == 0) return 0
        if (position == length) return -1
        connect()
        val read = requireNotNull(current).read(buffer, offset, minOf(count.toLong(), remainingInChunk).toInt())
        if (read < 0) throw EOFException("Video byte range ended prematurely")
        position += read
        remainingInChunk -= read
        if (remainingInChunk == 0L) {
            current?.close()
            current = null
        }
        return read
    }

    override fun close() {
        closed = true
        current?.close()
        current = null
    }
}
