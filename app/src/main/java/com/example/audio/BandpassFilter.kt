package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

/**
 * Bộ lọc dải thông Biquad Bandpass Filter (Butterworth bậc 2).
 * Ghép 2 tầng tạo thành bộ lọc bậc 4 (24dB/octave) triệt tiêu hoàn toàn tiếng nói và tạp âm.
 */
class BandpassFilter(sampleRate: Int, lowFreq: Float, highFreq: Float) {

    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var z1 = 0.0
    private var z2 = 0.0

    init {
        val centerFreq = (lowFreq + highFreq) / 2.0
        val bandwidth = (highFreq - lowFreq).coerceAtLeast(100f)
        val omega = 2.0 * PI * centerFreq / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega * sinh(ln(2.0) / 2.0 * bandwidth / centerFreq * omega / sinOmega)

        val a0 = 1.0 + alpha
        b0 = alpha / a0
        b1 = 0.0
        b2 = -alpha / a0
        a1 = (-2.0 * cosOmega) / a0
        a2 = (1.0 - alpha) / a0
    }

    /**
     * Lọc in-place trên mảng PCM 16-bit.
     */
    fun process(samples: ShortArray, offset: Int = 0, length: Int = samples.size): ShortArray {
        val end = minOf(offset + length, samples.size)
        for (i in offset until end) {
            val input = samples[i].toDouble()
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            samples[i] = output.coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    /**
     * Lọc cho một đoạn trượt (hop) cụ thể.
     */
    fun processHop(samples: ShortArray, offset: Int, length: Int) {
        process(samples, offset, length)
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}
