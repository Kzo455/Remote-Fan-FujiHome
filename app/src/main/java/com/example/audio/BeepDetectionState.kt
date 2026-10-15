package com.example.audio

/**
 * Trạng thái của quá trình phát IR + lắng nghe xác nhận tiếng beep từ quạt.
 */
sealed class BeepDetectionState {
    data object Idle : BeepDetectionState()

    data object Transmitting : BeepDetectionState()

    data class Listening(val elapsedMs: Long, val totalMs: Long) : BeepDetectionState()

    data class BeepDetected(
        val detectedFrequencyHz: Float,
        val durationMs: Float,
        val confidence: Float,
        val responseTimeMs: Long
    ) : BeepDetectionState() {
        val isLongBeep: Boolean get() = durationMs >= 300f
    }

    data class NoBeepDetected(val listenedMs: Long) : BeepDetectionState()

    data class Error(val message: String, val exception: Throwable? = null) : BeepDetectionState()
}
