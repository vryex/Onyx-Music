package com.metrolist.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.metrolist.music.eq.data.ParametricEQ
import com.metrolist.music.eq.data.ParametricEQBand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomEqualizerAudioProcessorTest {
    @Test
    fun `preamp-only profile scales and clips PCM including negative full scale`() {
        val processor = configured()
        processor.applyProfile(ParametricEQ(6.020599913279624, emptyList()))
        assertArrayEquals(shortArrayOf(2000, -2000, 32767, -32768), process(processor, 1000, -1000, 20000, -20000))
    }

    @Test
    fun `disabled processor preserves every PCM bit and clears pending profile`() {
        val processor = CustomEqualizerAudioProcessor()
        processor.applyProfile(ParametricEQ(-12.0, emptyList()))
        processor.disable()
        configure(processor)
        assertArrayEquals(shortArrayOf(0, -32768, 32767, -1), process(processor, 0, -32768, 32767, -1))
    }

    @Test
    fun `profile survives reset and coefficients follow sample rate`() {
        val profile = ParametricEQ(-3.0, listOf(ParametricEQBand(1000.0, 8.0)))
        val processor = configured()
        processor.applyProfile(profile)
        process(processor, 10000, 0, 0, 0)
        processor.reset()
        configure(processor, 44100)
        val fresh = configured(44100).apply { applyProfile(profile) }
        assertArrayEquals(process(fresh, 10000, 0, 0, 0), process(processor, 10000, 0, 0, 0))
        configure(processor, 96000)
        val fresh96 = configured(96000).apply { applyProfile(profile) }
        assertArrayEquals(process(fresh96, 10000, 0, 0, 0), process(processor, 10000, 0, 0, 0))
    }

    @Test
    fun `stereo channels have independent histories`() {
        val processor = configured(channels = 2)
        processor.applyProfile(ParametricEQ(0.0, listOf(ParametricEQBand(1000.0, 8.0))))
        val output = process(processor, 10000, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(output.filterIndexed { index, _ -> index % 2 == 1 }.all { it == 0.toShort() })
        assertTrue(output[2] != 0.toShort())
    }

    @Test
    fun `stereo right channel matches an independent mono filter`() {
        val profile = ParametricEQ(-3.0, listOf(ParametricEQBand(1000.0, 8.0)))
        val stereo = configured(channels = 2).apply { applyProfile(profile) }
        val mono = configured().apply { applyProfile(profile) }
        val output = process(stereo, 10000, -4000, 0, 2000, 5000, 0, 0, 0)
        val right = output.filterIndexed { index, _ -> index % 2 == 1 }.toShortArray()
        assertArrayEquals(process(mono, -4000, 2000, 0, 0), right)
    }

    @Test
    fun `newest requested profile applies as one complete buffer configuration`() {
        val processor = configured()
        processor.applyProfile(ParametricEQ(-12.0, emptyList()))
        processor.applyProfile(ParametricEQ(0.0, emptyList()))
        assertArrayEquals(shortArrayOf(1000, -1000), process(processor, 1000, -1000))
    }

    @Test
    fun `equal profile requests preserve running filter history`() {
        val profile = ParametricEQ(0.0, listOf(ParametricEQBand(1000.0, 8.0)))
        val processor = configured().apply { applyProfile(profile) }
        val reference = configured().apply { applyProfile(profile) }
        process(processor, 10000, 0)
        process(reference, 10000, 0)
        processor.applyProfile(profile.copy(bands = profile.bands.toList()))
        assertArrayEquals(process(reference, 0, 0), process(processor, 0, 0))
    }

    @Test
    fun `invalid imported band cannot contaminate valid PCM with NaN`() {
        val processor = configured()
        processor.applyProfile(ParametricEQ(0.0, listOf(ParametricEQBand(Double.NaN, 8.0))))
        assertArrayEquals(shortArrayOf(1000, -1000), process(processor, 1000, -1000))
    }

    private fun configured(rate: Int = 48000, channels: Int = 1) =
        CustomEqualizerAudioProcessor().also { configure(it, rate, channels) }

    @Suppress("DEPRECATION")
    private fun configure(processor: CustomEqualizerAudioProcessor, rate: Int = 48000, channels: Int = 1) {
        processor.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        processor.flush()
    }

    private fun process(processor: CustomEqualizerAudioProcessor, vararg samples: Int): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / 2) { output.short }
    }
}
