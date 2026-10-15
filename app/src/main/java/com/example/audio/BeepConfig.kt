package com.example.audio

import kotlin.math.roundToInt

data class BeepConfig(
    val sampleRate: Int = 48000,

    // === Goertzel & Sliding Window ===
    val targetFrequencyHz: Float = 4020f,
    /** N = 800 samples (16.67ms), Δf = 60Hz, k = 67 */
    val blockSize: Int = 800,
    /** Hop size = 400 samples (8.33ms, 50% overlap) */
    val hopSize: Int = 400,

    // === Bandpass Filter ===
    val bandpassLowHz: Float = 3400f,
    val bandpassHighHz: Float = 4600f,

    // === Ngưỡng Kép Hysteresis (Khởi động chặt - Duy trì mở) ===
    /** Biên độ sóng sin tối thiểu */
    val minSineAmplitude: Float = 0.0015f,
    val holdMinSineAmplitude: Float = 0.0010f,

    /** Hệ số SNR vượt nhiễu nền tại 4020Hz */
    val triggerSnrMultiplier: Float = 4.5f,
    val holdSnrMultiplier: Float = 2.5f,

    /** Độ tinh khiết phổ sau khi khử nhiễu nền tĩnh (Noise-Subtracted Purity) */
    val triggerSpectralPurity: Float = 0.28f,
    val holdSpectralPurity: Float = 0.15f,

    /** Độ nhọn đỉnh 4020Hz so với Trimmed-Mean của Guard Bins */
    val triggerProminenceRatio: Float = 5.5f,
    val holdProminenceRatio: Float = 2.6f,

    // === Kiểm tra Thời lượng Xung (Chuẩn: ~185-200ms, phòng vang 240-280ms) ===
    /** Thời lượng tối thiểu (loại bỏ tiếng click/gõ phím < 90ms) */
    val minBeepDurationMs: Int = 90,
    /** Chốt thắng sớm nếu sóng sin sạch kéo dài >= 150ms */
    val confirmBeepDurationMs: Int = 150,
    /** Tối đa trước khi coi là tiếng rít/huýt sáo liên tục (cho phép vang phòng reverb tới 320ms) */
    val maxBeepDurationMs: Int = 320,
    /** Số hop gián đoạn tối đa được phép tha thứ giữa tiếng beep (4 hops ≈ 33.3ms) */
    val maxGlitchHops: Int = 4,

    // === Timing & Calibration ===
    val listenWindowMs: Long = 500,
    val noiseCalibrationBlocks: Int = 8
) {
    val goertzelBinK: Int get() = (targetFrequencyHz * blockSize / sampleRate).roundToInt()
    val actualFrequencyHz: Float get() = goertzelBinK.toFloat() * sampleRate / blockSize
    val hopLatencyMs: Float get() = hopSize.toFloat() / sampleRate * 1000f

    /** 4 Guard Bins cách 4020Hz khoảng ±300Hz và ±480Hz */
    val guardBins: IntArray get() = intArrayOf(
        goertzelBinK - 8, // 3540 Hz
        goertzelBinK - 5, // 3720 Hz
        goertzelBinK + 5, // 4320 Hz
        goertzelBinK + 8  // 4500 Hz
    )
}
