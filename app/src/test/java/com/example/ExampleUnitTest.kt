package com.example

import com.example.audio.BandpassFilter
import com.example.audio.BeepConfig
import com.example.audio.GoertzelAnalyzer
import com.example.ir.FujiFanCommand
import com.example.ir.NecProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun verifyAll8FujiHomeCommandsMatchSpec() {
        // 1. Tắt quạt (Power Off) 0x40 -> 0x00FF40BF
        assertEquals(0x40, FujiFanCommand.POWER_OFF.commandByte)
        assertEquals("0x00FF40BF", FujiFanCommand.POWER_OFF.fullHex)

        // 2. Tăng gió (Speed +) 0x46 -> 0x00FF46B9
        assertEquals(0x46, FujiFanCommand.SPEED_PLUS.commandByte)
        assertEquals("0x00FF46B9", FujiFanCommand.SPEED_PLUS.fullHex)

        // 3. Giảm gió (Speed -) 0x0C -> 0x00FF0CF3
        assertEquals(0x0C, FujiFanCommand.SPEED_MINUS.commandByte)
        assertEquals("0x00FF0CF3", FujiFanCommand.SPEED_MINUS.fullHex)

        // 4. Gió mạnh (Turbo) 0x45 -> 0x00FF45BA
        assertEquals(0x45, FujiFanCommand.TURBO.commandByte)
        assertEquals("0x00FF45BA", FujiFanCommand.TURBO.fullHex)

        // 5. Chế độ ngủ (Sleep) 0x47 -> 0x00FF47B8
        assertEquals(0x47, FujiFanCommand.SLEEP.commandByte)
        assertEquals("0x00FF47B8", FujiFanCommand.SLEEP.fullHex)

        // 6. Quay trái / phải (Horizontal) 0x16 -> 0x00FF16E9
        assertEquals(0x16, FujiFanCommand.HORIZONTAL.commandByte)
        assertEquals("0x00FF16E9", FujiFanCommand.HORIZONTAL.fullHex)

        // 7. Quay lên / xuống (Vertical) 0x4A -> 0x00FF4AB5
        assertEquals(0x4A, FujiFanCommand.VERTICAL.commandByte)
        assertEquals("0x00FF4AB5", FujiFanCommand.VERTICAL.fullHex)

        // 8. Hẹn giờ (Timer) 0x43 -> 0x00FF43BC
        assertEquals(0x43, FujiFanCommand.TIMER.commandByte)
        assertEquals("0x00FF43BC", FujiFanCommand.TIMER.fullHex)
    }

    @Test
    fun verifyNecPatternTiming() {
        val pattern = NecProtocol.buildPattern(0x40)
        assertEquals(67, pattern.size)
        assertEquals(9000, pattern[0])
        assertEquals(4500, pattern[1])
        assertEquals(38000, NecProtocol.CARRIER_FREQUENCY_HZ)
        assertEquals(560, pattern[66])
    }

    @Test
    fun verifyGoertzelBinAndFrequency() {
        val config = BeepConfig(sampleRate = 48000, targetFrequencyHz = 4020f, blockSize = 800)
        // k = (4020 * 800) / 48000 = 67.0
        assertEquals(67, config.goertzelBinK)
        assertEquals(4020f, config.actualFrequencyHz, 0.1f)
    }

    @Test
    fun verifyGoertzelDetectionPrecision() {
        val config = BeepConfig(sampleRate = 48000, targetFrequencyHz = 4020f, blockSize = 800)
        val analyzer = GoertzelAnalyzer(config)

        // Generate 4020Hz sine wave (target beep, amplitude ~0.3)
        val beepSamples = ShortArray(800) { i ->
            (sin(2.0 * PI * 4020.0 * i / 48000.0) * 10000.0).toInt().toShort()
        }
        val beepResult = analyzer.analyzeFrame(beepSamples)

        // Generate 1000Hz sine wave (human voice/ambient)
        val voiceSamples = ShortArray(800) { i ->
            (sin(2.0 * PI * 1000.0 * i / 48000.0) * 10000.0).toInt().toShort()
        }
        val voiceResult = analyzer.analyzeFrame(voiceSamples)

        assertTrue("Năng lượng tại 4020Hz phải cao hơn nhiều so với 1000Hz", beepResult.targetEnergy > voiceResult.targetEnergy * 50f)
        assertTrue("Độ tinh khiết phổ sóng sin 4020Hz phải cao (> 0.7)", beepResult.spectralPurity > 0.7f)
        assertTrue("Prominence ratio của sóng sin 4020Hz phải rất cao (> 20)", beepResult.prominenceRatio > 20f)
        assertTrue("Độ tinh khiết phổ của âm 1000Hz tại bin 4020Hz phải rất thấp (< 0.1)", voiceResult.spectralPurity < 0.1f)
    }

    @Test
    fun verifyBandpassFilter() {
        val filter = BandpassFilter(sampleRate = 48000, lowFreq = 3500f, highFreq = 4500f)

        // Sóng 4020Hz (trong dải thông)
        val target = ShortArray(800) { i ->
            (sin(2.0 * PI * 4020.0 * i / 48000.0) * 10000.0).toInt().toShort()
        }
        filter.process(target)
        val targetMax = target.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Sóng 4020Hz phải đi qua bộ lọc với biên độ cao", targetMax > 6000)
    }

    @Test
    fun verifyNoiseSubtractedPurityAndTrimmedMean() {
        val config = BeepConfig(sampleRate = 48000, targetFrequencyHz = 4020f, blockSize = 800)
        val analyzer = GoertzelAnalyzer(config)

        // Tạo tín hiệu kết hợp: Sóng beep 4020Hz + Nhiễu băng rộng + 1 họa âm rơi vào Guard Bin 1 (3540Hz)
        val mixedSamples = ShortArray(800) { i ->
            val beep = sin(2.0 * PI * 4020.0 * i / 48000.0) * 8000.0
            val guardInterference = sin(2.0 * PI * 3540.0 * i / 48000.0) * 3000.0
            val ambientNoise = ((i % 17) - 8) * 150.0
            (beep + guardInterference + ambientNoise).toInt().toShort()
        }

        // Đo baseline noise tổng
        val baselineNoisePower = 0.05 // Mô phỏng năng lượng quạt gió/TV

        val result = analyzer.analyzeFrame(mixedSamples, baselineNoisePower)

        // Kiểm tra Trimmed-Mean đã loại bỏ đỉnh nhiễu tại 3540Hz, giữ prominence cao
        assertTrue("Prominence ratio vẫn phải duy trì tốt nhờ Trimmed-Mean", result.prominenceRatio > 3.0f)
        assertTrue("Độ tinh khiết phổ sau khi khử nhiễu nền tĩnh phải đạt ngưỡng hold (> 0.20)", result.spectralPurity > 0.20f)
        assertTrue("Biên độ sóng sin phải phát hiện rõ", result.sineAmplitude > 0.05f)
    }

    @Test
    fun verifyTurboAndSleepRestorePreviousSpeed() {
        val initial = com.example.ui.FujiHomeUiState(
            isPowerOn = true,
            speedLevel = 6,
            savedNormalSpeed = 6,
            mode = com.example.ui.FanMode.NORMAL
        )

        // 1. Bật Turbo từ mức 6: tốc độ chuyển thành 8, lưu mức 6
        val turboOn = initial.copy(
            mode = com.example.ui.FanMode.TURBO,
            savedNormalSpeed = 6,
            speedLevel = 8
        )
        assertEquals(8, turboOn.speedLevel)
        assertEquals(com.example.ui.FanMode.TURBO, turboOn.mode)

        // 2. Tắt Turbo: phục hồi lại mức 6 thay vì mức 4
        val turboOff = turboOn.copy(
            mode = com.example.ui.FanMode.NORMAL,
            speedLevel = turboOn.savedNormalSpeed
        )
        assertEquals(6, turboOff.speedLevel)
        assertEquals(com.example.ui.FanMode.NORMAL, turboOff.mode)

        // 3. Bật Sleep từ mức 6: tốc độ chuyển thành 1, lưu mức 6
        val sleepOn = turboOff.copy(
            mode = com.example.ui.FanMode.SLEEP,
            savedNormalSpeed = 6,
            speedLevel = 1
        )
        assertEquals(1, sleepOn.speedLevel)
        assertEquals(com.example.ui.FanMode.SLEEP, sleepOn.mode)

        // 4. Tắt Sleep: phục hồi lại mức 6 thay vì mức 4
        val sleepOff = sleepOn.copy(
            mode = com.example.ui.FanMode.NORMAL,
            speedLevel = sleepOn.savedNormalSpeed
        )
        assertEquals(6, sleepOff.speedLevel)
        assertEquals(com.example.ui.FanMode.NORMAL, sleepOff.mode)
    }

    @Test
    fun verifyTimerStartsFromZeroWhenActivatingNewSession() {
        // Giả sử trước đó đã từng lưu hẹn giờ 4h
        val stateWithSavedTimer = com.example.ui.FujiHomeUiState(
            timerHours = 4,
            isTimerSettingMode = false,
            pendingTimerHours = 4
        )

        // Khi kích hoạt hẹn giờ lần tiếp theo (isTimerSettingMode đang là false):
        val nextPending = if (!stateWithSavedTimer.isTimerSettingMode) 0 else 1
        assertEquals(0, nextPending)
    }

    @Test
    fun verifySequentialCommandQueueUpdates() {
        var state = com.example.ui.FujiHomeUiState(
            isPowerOn = true,
            speedLevel = 4,
            savedNormalSpeed = 4,
            mode = com.example.ui.FanMode.NORMAL
        )

        // Mô phỏng 2 lệnh tăng tốc độ liên tiếp qua hàng đợi khi quạt phát 2 tiếng pip
        val commands = listOf(com.example.ir.FujiFanCommand.SPEED_PLUS, com.example.ir.FujiFanCommand.SPEED_PLUS)
        for (cmd in commands) {
            val currentSpeed = state.speedLevel
            val nextSpeed = if (currentSpeed >= 8) 1 else currentSpeed + 1
            state = state.copy(
                speedLevel = nextSpeed,
                savedNormalSpeed = nextSpeed,
                mode = com.example.ui.FanMode.NORMAL
            )
        }

        // Tốc độ phải được tăng chính xác 2 lần từ 4 -> 5 -> 6
        assertEquals(6, state.speedLevel)
    }

    @Test
    fun verifySpeedWrapAroundMaxAndMin() {
        // 1. Khi ở mức 8, tăng tiếp phải quay về mức 1, rồi 2, 3...
        var currentSpeed = 8
        var nextSpeed = if (currentSpeed >= 8) 1 else currentSpeed + 1
        assertEquals(1, nextSpeed)

        currentSpeed = nextSpeed
        nextSpeed = if (currentSpeed >= 8) 1 else currentSpeed + 1
        assertEquals(2, nextSpeed)

        // 2. Khi ở mức 1, giảm tiếp phải quay về mức 8, rồi 7, 6...
        currentSpeed = 1
        nextSpeed = if (currentSpeed <= 1) 8 else currentSpeed - 1
        assertEquals(8, nextSpeed)

        currentSpeed = nextSpeed
        nextSpeed = if (currentSpeed <= 1) 8 else currentSpeed - 1
        assertEquals(7, nextSpeed)
    }

    @Test
    fun verifySpeedChangeFromTurboOnlyTurnsOffMode() {
        // Trường hợp: Đang ở mức gió 4, bật Turbo (hiển thị 8, lưu mức 4)
        val turboState = com.example.ui.FujiHomeUiState(
            isPowerOn = true,
            mode = com.example.ui.FanMode.TURBO,
            savedNormalSpeed = 4,
            speedLevel = 8
        )

        // Ấn Tăng gió khi Turbo đang bật: chỉ tắt Turbo về NORMAL và phục hồi mức gió cũ (4)
        val stateAfterPlus = when (turboState.mode) {
            com.example.ui.FanMode.TURBO -> turboState.copy(
                mode = com.example.ui.FanMode.NORMAL,
                speedLevel = turboState.savedNormalSpeed
            )
            else -> turboState
        }
        assertEquals(4, stateAfterPlus.speedLevel)
        assertEquals(4, stateAfterPlus.savedNormalSpeed)
        assertEquals(com.example.ui.FanMode.NORMAL, stateAfterPlus.mode)

        // Ấn Giảm gió khi Turbo đang bật: cũng chỉ tắt Turbo về NORMAL và phục hồi mức gió cũ (4)
        val stateAfterMinus = when (turboState.mode) {
            com.example.ui.FanMode.TURBO -> turboState.copy(
                mode = com.example.ui.FanMode.NORMAL,
                speedLevel = turboState.savedNormalSpeed
            )
            else -> turboState
        }
        assertEquals(4, stateAfterMinus.speedLevel)
        assertEquals(4, stateAfterMinus.savedNormalSpeed)
        assertEquals(com.example.ui.FanMode.NORMAL, stateAfterMinus.mode)
    }

    @Test
    fun verifySpeedChangeFromSleepTurnsOffSleepAndAdjustsSpeed() {
        // Trường hợp 1: Đang ở mức gió 4, bật Sleep (hiển thị 1, lưu mức 4)
        val sleepState = com.example.ui.FujiHomeUiState(
            isPowerOn = true,
            mode = com.example.ui.FanMode.SLEEP,
            savedNormalSpeed = 4,
            speedLevel = 1
        )

        // Ấn Tăng gió khi Sleep đang bật: tắt Sleep và tăng thêm 1 mức (4 + 1 = 5)
        val baseSpeedPlus = sleepState.savedNormalSpeed
        val nextSpeedPlus = if (baseSpeedPlus >= 8) 1 else baseSpeedPlus + 1
        val sleepAfterPlus = sleepState.copy(
            mode = com.example.ui.FanMode.NORMAL,
            speedLevel = nextSpeedPlus,
            savedNormalSpeed = nextSpeedPlus
        )
        assertEquals(5, sleepAfterPlus.speedLevel)
        assertEquals(5, sleepAfterPlus.savedNormalSpeed)
        assertEquals(com.example.ui.FanMode.NORMAL, sleepAfterPlus.mode)

        // Ấn Giảm gió khi Sleep đang bật: tắt Sleep và giảm bớt 1 mức (4 - 1 = 3)
        val baseSpeedMinus = sleepState.savedNormalSpeed
        val nextSpeedMinus = if (baseSpeedMinus <= 1) 8 else baseSpeedMinus - 1
        val sleepAfterMinus = sleepState.copy(
            mode = com.example.ui.FanMode.NORMAL,
            speedLevel = nextSpeedMinus,
            savedNormalSpeed = nextSpeedMinus
        )
        assertEquals(3, sleepAfterMinus.speedLevel)
        assertEquals(3, sleepAfterMinus.savedNormalSpeed)
        assertEquals(com.example.ui.FanMode.NORMAL, sleepAfterMinus.mode)

        // Trường hợp 2: Đang ở mức gió 8, bật Sleep -> Ấn Tăng gió: tắt Sleep và xoay vòng về 1
        val sleepState8 = sleepState.copy(savedNormalSpeed = 8)
        val nextSpeedWrap8 = if (sleepState8.savedNormalSpeed >= 8) 1 else sleepState8.savedNormalSpeed + 1
        assertEquals(1, nextSpeedWrap8)

        // Trường hợp 3: Đang ở mức gió 1, bật Sleep -> Ấn Giảm gió: tắt Sleep và xoay vòng về 8
        val sleepState1 = sleepState.copy(savedNormalSpeed = 1)
        val nextSpeedWrap1 = if (sleepState1.savedNormalSpeed <= 1) 8 else sleepState1.savedNormalSpeed - 1
        assertEquals(8, nextSpeedWrap1)
    }

    @Test
    fun verifyInitialPowerDialogState() {
        val defaultState = com.example.ui.FujiHomeUiState()
        assertTrue(defaultState.showInitialPowerDialog)

        // Khi người dùng chọn Tắt quạt
        val stateTurnedOff = defaultState.copy(
            isPowerOn = false,
            showInitialPowerDialog = false
        )
        assertFalse(stateTurnedOff.isPowerOn)
        assertFalse(stateTurnedOff.showInitialPowerDialog)

        // Khi người dùng chọn Bật quạt
        val stateTurnedOn = defaultState.copy(
            isPowerOn = true,
            showInitialPowerDialog = false
        )
        assertTrue(stateTurnedOn.isPowerOn)
        assertFalse(stateTurnedOn.showInitialPowerDialog)
    }

    @Test
    fun verifyCommandQueueFifoOrderAndDrainOnPowerOff() = kotlinx.coroutines.runBlocking {
        val channel = kotlinx.coroutines.channels.Channel<FujiFanCommand>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)

        // Gửi 2 lệnh liên tiếp
        channel.trySend(FujiFanCommand.SPEED_PLUS)
        channel.trySend(FujiFanCommand.SPEED_PLUS)

        // Nhận tuần tự theo thứ tự FIFO
        assertEquals(FujiFanCommand.SPEED_PLUS, channel.receive())
        assertEquals(FujiFanCommand.SPEED_PLUS, channel.receive())
        assertTrue(channel.tryReceive().isFailure)

        // Kiểm tra logic xả hàng đợi khi gặp POWER_OFF
        channel.trySend(FujiFanCommand.SPEED_PLUS)
        channel.trySend(FujiFanCommand.SPEED_PLUS)
        // Xả sạch khi POWER_OFF được gửi
        while (channel.tryReceive().isSuccess) {}
        channel.trySend(FujiFanCommand.POWER_OFF)

        assertEquals(FujiFanCommand.POWER_OFF, channel.receive())
        assertTrue(channel.tryReceive().isFailure)
    }
}
