package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

data class FrameAnalysisResult(
    val targetEnergy: Float,
    val sineAmplitude: Float,
    val spectralPurity: Float,
    val prominenceRatio: Float,
    val totalPower: Double
)

class GoertzelAnalyzer(private val config: BeepConfig) {

    private val n = config.blockSize

    // Precompute Hann Window để triệt tiêu Spectral Leakage (-31.5 dB)
    private val hannWindow = FloatArray(n) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
    }

    // Hệ số Goertzel cho bin mục tiêu (k=67 -> 4020Hz) và 2 bin sát cạnh (phòng trường hợp lệch nhiệt độ ±30Hz)
    private val targetCoeff = computeCoeff(config.goertzelBinK)
    private val neighborLowCoeff = computeCoeff(config.goertzelBinK - 1)
    private val neighborHighCoeff = computeCoeff(config.goertzelBinK + 1)

    // Hệ số Goertzel cho các Guard Bins (3540Hz, 3720Hz, 4320Hz, 4500Hz)
    private val guardCoeffs = DoubleArray(config.guardBins.size) { idx ->
        computeCoeff(config.guardBins[idx])
    }

    // Buffer tái sử dụng để tránh cấp phát bộ nhớ (GC pause) trong vòng lặp realtime
    private val windowedSamples = FloatArray(n)
    private val guardEnergies = DoubleArray(config.guardBins.size)

    private fun computeCoeff(binK: Int): Double {
        return 2.0 * cos(2.0 * PI * binK / n)
    }

    /**
     * Phân tích toàn diện 1 frame (800 samples) đã qua Bandpass Filter:
     * 1. Khử nền trước khi tính Purity (Noise-Subtracted Purity)
     * 2. Gộp năng lượng 2 bin trung tâm
     * 3. Trimmed-Mean Guard Bins (loại bỏ 1 bin canh gác cao nhất)
     */
    fun analyzeFrame(
        pcmSamples: ShortArray,
        baselineNoiseTotalPower: Double = 0.0
    ): FrameAnalysisResult {
        var totalWindowedPower = 0.0

        // 1. Chuẩn hóa PCM 16-bit về [-1.0, 1.0] & áp dụng cửa sổ Hann
        for (i in 0 until n) {
            val normalized = pcmSamples[i] * (1.0f / 32768.0f)
            val wSample = normalized * hannWindow[i]
            windowedSamples[i] = wSample
            totalWindowedPower += wSample * wSample
        }

        // Nếu tín hiệu hoàn toàn im lặng
        if (totalWindowedPower < 1e-12) {
            return FrameAnalysisResult(0f, 0f, 0f, 0f, 0.0)
        }

        // 2. Tính năng lượng tại 4020Hz và gộp bin lân cận nếu có trôi tần
        val eCenter = runGoertzel(windowedSamples, targetCoeff)
        val eLow = runGoertzel(windowedSamples, neighborLowCoeff)
        val eHigh = runGoertzel(windowedSamples, neighborHighCoeff)
        val maxNeighbor = maxOf(eLow, eHigh)
        // Gộp năng lượng bin chính + thành phần dò sang bin bên cạnh
        val peakTargetEnergy = maxOf(eCenter, eCenter * 0.75 + maxNeighbor * 0.35, maxNeighbor * 0.85)

        // 3. Quy đổi ra biên độ sóng sin thực tế A ∈ [0.0, 1.0]
        val sineAmplitude = (4.0 * sqrt(peakTargetEnergy) / n).toFloat()

        // 4. Khử nền trước khi tính Purity (Noise-Subtracted Purity):
        // Trừ bớt năng lượng nhiễu nền tĩnh (fan/AC/TV) khỏi tổng năng lượng để không làm phình mẫu số
        val effectiveNoise = baselineNoiseTotalPower * 0.85
        val excessPower = (totalWindowedPower - effectiveNoise).coerceAtLeast(1e-12)
        val theoreticalSinePower = (3.0 * peakTargetEnergy) / n
        // Mẫu số tối thiểu bằng năng lượng lý thuyết của chính sóng sin
        val denominatorPower = maxOf(excessPower, theoreticalSinePower)

        val purity = (theoreticalSinePower / denominatorPower)
            .toFloat()
            .coerceIn(0f, 1f)

        // 5. Trimmed-Mean Guard Bins:
        // Đo 4 Guard Bins, loại bỏ 1 bin có năng lượng cao nhất (phòng nhạc/tiếng ồn có họa âm rơi vào 1 bin)
        var maxGuardIdx = 0
        var maxGuardVal = -1.0
        for (i in guardCoeffs.indices) {
            val energy = runGoertzel(windowedSamples, guardCoeffs[i])
            guardEnergies[i] = energy
            if (energy > maxGuardVal) {
                maxGuardVal = energy
                maxGuardIdx = i
            }
        }

        var trimmedSum = 0.0
        var trimmedCount = 0
        for (i in guardEnergies.indices) {
            if (i != maxGuardIdx) {
                trimmedSum += guardEnergies[i]
                trimmedCount++
            }
        }
        val avgGuardEnergy = if (trimmedCount > 0) trimmedSum / trimmedCount else maxGuardVal
        val prominenceRatio = (peakTargetEnergy / (avgGuardEnergy + 1e-12)).toFloat()

        return FrameAnalysisResult(
            targetEnergy = peakTargetEnergy.toFloat(),
            sineAmplitude = sineAmplitude,
            spectralPurity = purity,
            prominenceRatio = prominenceRatio,
            totalPower = totalWindowedPower
        )
    }

    private fun runGoertzel(samples: FloatArray, coeff: Double): Double {
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            val s0 = samples[i] + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }
}
