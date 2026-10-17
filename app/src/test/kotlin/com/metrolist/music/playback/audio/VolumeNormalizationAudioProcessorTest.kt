package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VolumeNormalizationAudioProcessorTest {
    @Test
    fun disabledNormalizationPreservesRawPcmForEverySupportedEncoding() {
        listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT).forEach { encoding ->
            val processor = configured(encoding)
            processor.setTargetGain(-600)
            val bytes = ByteArray(48) { (it * 37).toByte() }
            assertArrayEquals(bytes, process(processor, bytes))
        }
    }

    @Test
    fun unityGainPreservesRawFloatValuesIncludingNaNPayloads() {
        val processor = configured(C.ENCODING_PCM_FLOAT).apply { enabled = true }
        val bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x7fc00001).putInt(0x80000000.toInt()).putInt(1).putInt(0x7f800000).array()
        assertArrayEquals(bytes, process(processor, bytes))
    }

    @Test
    fun enabledGainStillProcessesPcmRatherThanBypassing() {
        val processor = configured(C.ENCODING_PCM_16BIT).apply { enabled = true; setTargetGain(-602) }
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(10000).putShort(-10000).array()
        val expected = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(5000).putShort(-5000).array()
        assertArrayEquals(expected, process(processor, input))
    }

    @Suppress("DEPRECATION")
    private fun configured(encoding: Int) = VolumeNormalizationAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(48000, 2, encoding))
        flush()
    }

    private fun process(processor: VolumeNormalizationAudioProcessor, bytes: ByteArray): ByteArray {
        processor.queueInput(ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).put(bytes).flip() as ByteBuffer)
        val output = processor.output
        return ByteArray(output.remaining()).also { output.get(it) }
    }
}
