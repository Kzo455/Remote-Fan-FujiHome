package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

interface BeepSession {
    suspend fun verifyCommand(
        onMicReadyTransmitIr: suspend () -> Boolean = { true }
    ): BeepDetectionState
}

class BeepDetector(
    private val context: Context,
    private val config: BeepConfig = BeepConfig()
) {

    private val goertzel = GoertzelAnalyzer(config)
    // Xếp chồng 2 tầng Bandpass Filter (Bậc 4 - 24dB/octave) để triệt tiêu tiếng nói chuyện và tạp âm
    private val filterStage1 = BandpassFilter(config.sampleRate, config.bandpassLowHz, config.bandpassHighHz)
    private val filterStage2 = BandpassFilter(config.sampleRate, config.bandpassLowHz, config.bandpassHighHz)

    private val _state = MutableStateFlow<BeepDetectionState>(BeepDetectionState.Idle)
    val state: StateFlow<BeepDetectionState> = _state.asStateFlow()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Mở một phiên lắng nghe âm thanh liên tục (Session).
     * Cho phép kiểm tra nhiều lệnh liên tiếp mà không cần hủy / khởi tạo lại AudioRecord,
     * loại bỏ độ trễ và hiện tượng mất tiếng pip khi người dùng bấm phím nhanh.
     */
    @SuppressLint("MissingPermission")
    suspend fun <R> runSession(
        block: suspend (session: BeepSession) -> R
    ): R = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            val err = BeepDetectionState.Error("Chưa cấp quyền Micro")
            _state.value = err
            throw SecurityException("Chưa cấp quyền Micro")
        }

        filterStage1.reset()
        filterStage2.reset()

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRate, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            val err = BeepDetectionState.Error("Không thể khởi tạo bộ đệm AudioRecord")
            _state.value = err
            throw IllegalStateException("Không thể khởi tạo bộ đệm AudioRecord")
        }

        val bufferSize = maxOf(minBufferSize * 2, config.blockSize * 8)
        val audioRecord = try {
            // VOICE_RECOGNITION tắt AGC và Noise Suppression, bảo toàn sóng sin 4020Hz
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                config.sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            val err = BeepDetectionState.Error("Lỗi khởi tạo mic: ${e.message}", e)
            _state.value = err
            throw e
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            val err = BeepDetectionState.Error("AudioRecord chưa sẵn sàng")
            _state.value = err
            throw IllegalStateException("AudioRecord chưa sẵn sàng")
        }

        try {
            audioRecord.startRecording()

            // 1. Đọc xả 2 block đầu tiên (loại bỏ tiếng pop khi phần cứng mic bật)
            val flushBuffer = ShortArray(config.blockSize)
            repeat(2) { audioRecord.read(flushBuffer, 0, config.blockSize) }

            // 2. Đo Noise Floor bằng MEDIAN trước khi phát IR (loại trừ tiếng chạm tay / va đập)
            val noiseTargetSamples = mutableListOf<Float>()
            val noiseTotalPowerSamples = mutableListOf<Double>()
            repeat(config.noiseCalibrationBlocks) {
                val read = audioRecord.read(flushBuffer, 0, config.blockSize)
                if (read == config.blockSize) {
                    filterStage1.process(flushBuffer)
                    filterStage2.process(flushBuffer)
                    val res = goertzel.analyzeFrame(flushBuffer, 0.0)
                    noiseTargetSamples.add(res.targetEnergy)
                    noiseTotalPowerSamples.add(res.totalPower)
                }
            }

            val baselineNoiseEnergy = if (noiseTargetSamples.isNotEmpty()) {
                noiseTargetSamples.sorted()[noiseTargetSamples.size / 2]
            } else 1e-9f

            val baselineNoiseTotalPower = if (noiseTotalPowerSamples.isNotEmpty()) {
                noiseTotalPowerSamples.sorted()[noiseTotalPowerSamples.size / 2]
            } else 0.0

            val sessionImpl = object : BeepSession {
                private var rollingNoise = baselineNoiseEnergy

                override suspend fun verifyCommand(
                    onMicReadyTransmitIr: suspend () -> Boolean
                ): BeepDetectionState {
                    // 1. Phát tín hiệu IR đồng bộ
                    _state.value = BeepDetectionState.Transmitting
                    val irSuccess = onMicReadyTransmitIr()
                    if (!irSuccess) {
                        val err = BeepDetectionState.Error("Không thể phát tín hiệu IR")
                        _state.value = err
                        return err
                    }

                    // 2. Xả sạch bộ đệm/bộ lọc sau khi phát IR để tránh bắt nhầm tiếng bấm phím
                    filterStage1.reset()
                    filterStage2.reset()

                    // 3. Bắt đầu vòng lặp phát hiện tiếng Beep với Hysteresis Trigger/Hold
                    val result = detectBeepLoop(audioRecord, rollingNoise, baselineNoiseTotalPower)
                    _state.value = result
                    return result
                }
            }

            block(sessionImpl)
        } catch (e: CancellationException) {
            throw e
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord.release()
            } catch (_: Exception) {}
            _state.value = BeepDetectionState.Idle
        }
    }

    /**
     * Phương thức đơn lẻ (Single-shot) cho một lệnh riêng lẻ
     */
    suspend fun listenForBeep(
        onMicReadyTransmitIr: suspend () -> Boolean = { true }
    ): BeepDetectionState = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            val err = BeepDetectionState.Error("Chưa cấp quyền Micro")
            _state.value = err
            return@withContext err
        }
        try {
            runSession { session ->
                session.verifyCommand(onMicReadyTransmitIr)
            }
        } catch (e: Exception) {
            val err = BeepDetectionState.Error("Lỗi xử lý audio: ${e.message}", e)
            _state.value = err
            err
        }
    }

    private suspend fun detectBeepLoop(
        audioRecord: AudioRecord,
        initialNoiseEnergy: Float,
        baselineNoiseTotalPower: Double
    ): BeepDetectionState {
        val startTime = System.currentTimeMillis()
        val hopBuffer = ShortArray(config.hopSize)
        val slidingWindow = ShortArray(config.blockSize)

        var rollingNoiseEnergy = initialNoiseEnergy
        var activeHops = 0
        var glitchHops = 0
        var beepStartTime = 0L
        var peakPurity = 0f
        var peakProminence = 0f
        var isNoiseTooLong = false

        // Đọc nửa đầu tiên (400 mẫu) để lấp đầy nửa đầu cửa sổ trượt
        audioRecord.read(slidingWindow, 0, config.hopSize)
        filterStage1.processHop(slidingWindow, 0, config.hopSize)
        filterStage2.processHop(slidingWindow, 0, config.hopSize)

        while (currentCoroutineContext().isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= config.listenWindowMs) {
                break
            }
            _state.value = BeepDetectionState.Listening(elapsed, config.listenWindowMs)

            // Đọc 400 mẫu mới (8.33ms)
            val read = audioRecord.read(hopBuffer, 0, config.hopSize)
            if (read != config.hopSize) continue

            // Lọc Bandpass bậc 4 trên 400 mẫu mới
            filterStage1.process(hopBuffer)
            filterStage2.process(hopBuffer)

            // Dịch cửa sổ trượt: đẩy 400 mẫu cũ sang trái, chép 400 mẫu mới vào nửa phải (50% overlap)
            System.arraycopy(slidingWindow, config.hopSize, slidingWindow, 0, config.hopSize)
            System.arraycopy(hopBuffer, 0, slidingWindow, config.hopSize, config.hopSize)

            // Phân tích cửa sổ 800 mẫu kết hợp Noise-Subtracted Purity
            val metrics = goertzel.analyzeFrame(slidingWindow, baselineNoiseTotalPower)

            if (activeHops == 0) {
                // GIAI ĐOẠN 1: TRIGGER (Khởi động chặt - Chặn đứng tạp âm)
                val isTrigger = metrics.sineAmplitude >= config.minSineAmplitude &&
                        metrics.targetEnergy >= rollingNoiseEnergy * config.triggerSnrMultiplier &&
                        metrics.spectralPurity >= config.triggerSpectralPurity &&
                        metrics.prominenceRatio >= config.triggerProminenceRatio

                if (isTrigger) {
                    activeHops = 1
                    glitchHops = 0
                    beepStartTime = System.currentTimeMillis()
                    peakPurity = metrics.spectralPurity
                    peakProminence = metrics.prominenceRatio
                    isNoiseTooLong = false
                } else {
                    // Cập nhật thích nghi nhiễu nền khi không có tín hiệu (EMA)
                    if (metrics.spectralPurity < 0.20f) {
                        rollingNoiseEnergy = 0.95f * rollingNoiseEnergy + 0.05f * metrics.targetEnergy
                    }
                }
            } else {
                // GIAI ĐOẠN 2: HOLD (Duy trì mở - Không bị đứt chuỗi khi có tiếng nói/ồn chèn ngang)
                val isHold = metrics.sineAmplitude >= config.holdMinSineAmplitude &&
                        metrics.targetEnergy >= rollingNoiseEnergy * config.holdSnrMultiplier &&
                        metrics.spectralPurity >= config.holdSpectralPurity &&
                        metrics.prominenceRatio >= config.holdProminenceRatio

                if (isHold) {
                    activeHops++
                    glitchHops = 0
                    peakPurity = maxOf(peakPurity, metrics.spectralPurity)
                    peakProminence = maxOf(peakProminence, metrics.prominenceRatio)

                    val currentDurationMs = activeHops * config.hopLatencyMs

                    // Nếu kéo dài vượt quá maxBeepDurationMs (320ms) -> là tiếng hú/rít liên tục
                    if (currentDurationMs > config.maxBeepDurationMs) {
                        isNoiseTooLong = true
                    } else if (!isNoiseTooLong && currentDurationMs >= config.confirmBeepDurationMs &&
                        peakPurity >= 0.38f && peakProminence >= 7.0f
                    ) {
                        // Early Lock: Tín hiệu cực sạch đã kéo dài >= 150ms -> Chốt thắng sớm
                        return BeepDetectionState.BeepDetected(
                            detectedFrequencyHz = config.actualFrequencyHz,
                            durationMs = currentDurationMs,
                            confidence = calculateConfidence(peakPurity, peakProminence, currentDurationMs),
                            responseTimeMs = beepStartTime - startTime
                        )
                    }
                } else {
                    // Cho phép gián đoạn tối đa 4 hops (~33ms) giữa tiếng beep
                    glitchHops++
                    if (glitchHops <= config.maxGlitchHops) {
                        activeHops++ // Giữ mạch thời gian liên tục
                    } else {
                        // Tiếng beep đã KẾT THÚC (Falling Edge) -> Kiểm tra tổng thời lượng
                        val trueBeepHops = activeHops - glitchHops
                        val finalDurationMs = trueBeepHops * config.hopLatencyMs

                        if (!isNoiseTooLong && finalDurationMs >= config.minBeepDurationMs) {
                            val confidence = calculateConfidence(peakPurity, peakProminence, finalDurationMs)
                            return BeepDetectionState.BeepDetected(
                                detectedFrequencyHz = config.actualFrequencyHz,
                                durationMs = finalDurationMs,
                                confidence = confidence,
                                responseTimeMs = beepStartTime - startTime
                            )
                        }

                        // Reset nếu xung quá ngắn (< 90ms: tiếng click/gõ bàn) hoặc bị rít liên tục
                        activeHops = 0
                        glitchHops = 0
                        peakPurity = 0f
                        peakProminence = 0f
                        isNoiseTooLong = false
                    }
                }
            }
        }

        // Trường hợp hết cửa sổ lắng nghe ngay lúc tiếng beep vừa hoàn tất
        val trueBeepHops = activeHops - glitchHops
        val finalDurationMs = trueBeepHops * config.hopLatencyMs
        if (!isNoiseTooLong && finalDurationMs in config.minBeepDurationMs.toFloat()..config.maxBeepDurationMs.toFloat()) {
            return BeepDetectionState.BeepDetected(
                detectedFrequencyHz = config.actualFrequencyHz,
                durationMs = finalDurationMs,
                confidence = calculateConfidence(peakPurity, peakProminence, finalDurationMs),
                responseTimeMs = beepStartTime - startTime
            )
        }

        return BeepDetectionState.NoBeepDetected(System.currentTimeMillis() - startTime)
    }

    private fun calculateConfidence(purity: Float, prominence: Float, durationMs: Float): Float {
        val purityScore = ((purity - config.holdSpectralPurity) / (1f - config.holdSpectralPurity)).coerceIn(0f, 1f)
        val promScore = ((prominence - config.holdProminenceRatio) / 30f).coerceIn(0f, 1f)
        val durationScore = (1f - abs(durationMs - 190f) / 100f).coerceIn(0f, 1f)
        return (purityScore * 0.40f + promScore * 0.40f + durationScore * 0.20f).coerceIn(0.1f, 1.0f)
    }
}
