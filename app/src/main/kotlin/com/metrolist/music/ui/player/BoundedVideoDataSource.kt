package com.metrolist.music.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream

/** Keeps the full logical length visible to Media3 while reopening each HTTP range. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class BoundedVideoDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val fileLength: Long,
    private val chunkSize: Long,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var upstream: DataSource? = null
    private var input: ChunkedVideoInputStream? = null

    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }

    override fun open(dataSpec: DataSpec): Long {
        require(fileLength > 0 && chunkSize > 0)
        val available = fileLength - dataSpec.position
        if (available < 0) throw IOException("Video seek outside file")
        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(available, dataSpec.length)
        val stream = ChunkedVideoInputStream(length, chunkSize) { offset, count ->
            val source = upstreamFactory.createDataSource()
            listeners.forEach(source::addTransferListener)
            try {
                source.open(dataSpec.subrange(offset, count))
            } catch (error: Exception) {
                source.close()
                throw error
            }
            upstream = source
            object : InputStream() {
                override fun read(): Int {
                    val one = ByteArray(1)
                    return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = source.read(buffer, offset, length)
                override fun close() = source.close()
            }
        }
        input = stream
        stream.connect()
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(input).read(buffer, offset, length)
    override fun getUri(): Uri? = upstream?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream?.responseHeaders.orEmpty()
    override fun close() { input?.close(); input = null; upstream = null }
}
