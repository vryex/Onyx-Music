package com.metrolist.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.metrolist.music.eq.data.ParametricEQ
import com.metrolist.music.eq.data.ParametricEQBand
import java.nio.ByteBuffer
import kotlin.math.pow

/** Applies AutoEQ parametric profiles with biquad filters. */
@UnstableApi
class CustomEqualizerAudioProcessor : BaseAudioProcessor() {
    private data class Profile(val gain: Double, val bands: List<ParametricEQBand>)

    // UI/service threads only publish requests. Filter histories belong to the audio thread.
    @Volatile private var requestedProfile: Profile? = null
    private var appliedProfile: Profile? = null
    private var sampleRate = 0
    private var channelCount = 0
    private var filters: Array<BiquadFilter> = emptyArray()
    private var preampGain = 1.0

    fun applyProfile(parametricEQ: ParametricEQ) {
        val gain = 10.0.pow(parametricEQ.preamp / 20.0)
        require(parametricEQ.preamp.isFinite() && gain.isFinite()) { "Invalid EQ preamp" }
        val profile = Profile(gain, parametricEQ.bands.toList())
        if (requestedProfile != profile) requestedProfile = profile
    }

    fun disable() {
        requestedProfile = null
    }

    fun isEnabled(): Boolean = requestedProfile != null

    private fun applyRequestedProfile() {
        val profile = requestedProfile
        if (profile === appliedProfile) return
        filters = profile?.bands.orEmpty()
            .filter { band ->
                band.enabled && band.frequency.isFinite() && band.frequency > 0 &&
                    band.frequency < sampleRate / 2.0 && band.gain.isFinite() &&
                    band.q.isFinite() && band.q > 0
            }
            .mapNotNull { band ->
                // Malformed imported bands must not poison the entire PCM stream with NaN.
                runCatching {
                    BiquadFilter(sampleRate, band.frequency, band.gain, band.q, band.filterType)
                }.getOrNull()
            }
            .toTypedArray()
        preampGain = profile?.gain ?: 1.0
        appliedProfile = profile
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2 || inputAudioFormat.sampleRate <= 0
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        applyRequestedProfile()
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (appliedProfile == null || (filters.isEmpty() && preampGain == 1.0)) {
            output.put(inputBuffer)
        } else {
            val frameCount = inputBuffer.remaining() / (2 * channelCount)
            repeat(frameCount) {
                var left = inputBuffer.short.toDouble() / 32768.0
                var right = if (channelCount == 2) inputBuffer.short.toDouble() / 32768.0 else 0.0
                for (filter in filters) {
                    left = filter.processSample(left)
                    if (channelCount == 2) right = filter.processRightSample(right)
                }
                output.putShort(toPcm(left * preampGain))
                if (channelCount == 2) output.putShort(toPcm(right * preampGain))
            }
        }
        output.flip()
    }

    private fun toPcm(sample: Double): Short =
        (sample * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // BaseAudioProcessor commits a pending format at flush, after old-format buffers drain.
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        appliedProfile = null
        filters = emptyArray()
        preampGain = 1.0
        applyRequestedProfile()
    }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        appliedProfile = null
        filters = emptyArray()
        preampGain = 1.0
        // Preserve the service's selected profile for the next configured stream.
    }
}
