package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BeepDetectionState
import com.example.audio.IrTransmitVerifier
import com.example.audio.IrVerificationSession
import com.example.ir.FujiFanCommand
import com.example.ir.TransmitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class FanMode {
    NORMAL,
    TURBO,
    SLEEP
}

data class FujiHomeUiState(
    val isHardwareAvailable: Boolean = false,
    val lastResult: TransmitResult? = null,
    val isLedFlashing: Boolean = false,
    val activeCommand: FujiFanCommand? = null,
    val isPowerOn: Boolean = true,
    val speedLevel: Int = 4, // Mặc định mức 4 khi bật
    val savedNormalSpeed: Int = 4, // Tốc độ trước khi vào chế độ Turbo hoặc Sleep để phục hồi khi tắt
    val mode: FanMode = FanMode.NORMAL,
    val isHorizontalSwing: Boolean = false,
    val isVerticalSwing: Boolean = false,
    val timerHours: Int = 0, // Giá trị đã lưu chính thức: 0 (Tắt), 1, 2, 4, 8
    val pendingTimerHours: Int = 0, // Giá trị đang chỉnh trong phiên: luôn bắt đầu từ 0h
    val isTimerSettingMode: Boolean = false, // Đang trong phiên chờ xác nhận hẹn giờ
    val isAcousticVerificationEnabled: Boolean = true,
    val verificationState: BeepDetectionState = BeepDetectionState.Idle,
    val longBeepNotification: String? = null, // Thông báo khi quạt kêu tiếng pip dài
    val showInitialPowerDialog: Boolean = false // Đã tắt popup theo yêu cầu thiết kế
) {
    val displayTimerHours: Int
        get() = if (isTimerSettingMode) pendingTimerHours else timerHours

    val windDescriptor: String
        get() = when {
            !isPowerOn -> "Đã Tắt"
            mode == FanMode.TURBO -> "Turbo Tối Đa"
            mode == FanMode.SLEEP -> "Gió Đêm Êm Ái"
            else -> "Mức $speedLevel/8"
        }
}

class FujiHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val verifier = IrTransmitVerifier(application)
    private val prefs = application.getSharedPreferences("fuji_fan_state_prefs", Context.MODE_PRIVATE)

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private val _uiState = MutableStateFlow(
        FujiHomeUiState(
            isHardwareAvailable = verifier.irTransmitter.hasHardwareIrEmitter,
            isPowerOn = prefs.getBoolean("is_power_on", true),
            speedLevel = prefs.getInt("speed_level", 4).coerceIn(1, 8),
            savedNormalSpeed = prefs.getInt("saved_normal_speed", 4).coerceIn(1, 8),
            mode = try {
                FanMode.valueOf(prefs.getString("fan_mode", FanMode.NORMAL.name) ?: FanMode.NORMAL.name)
            } catch (_: Exception) {
                FanMode.NORMAL
            },
            isHorizontalSwing = prefs.getBoolean("is_h_swing", false),
            isVerticalSwing = prefs.getBoolean("is_v_swing", false),
            timerHours = prefs.getInt("timer_hours", 0),
            showInitialPowerDialog = false
        )
    )
    val uiState: StateFlow<FujiHomeUiState> = _uiState.asStateFlow()

    private fun persistState(state: FujiHomeUiState) {
        try {
            prefs.edit()
                .putBoolean("is_power_on", state.isPowerOn)
                .putInt("speed_level", state.speedLevel)
                .putInt("saved_normal_speed", state.savedNormalSpeed)
                .putString("fan_mode", state.mode.name)
                .putBoolean("is_h_swing", state.isHorizontalSwing)
                .putBoolean("is_v_swing", state.isVerticalSwing)
                .putInt("timer_hours", state.timerHours)
                .apply()
        } catch (e: Exception) {
            Log.e("FujiHomeViewModel", "Lỗi khi lưu trạng thái quạt vào SharedPreferences", e)
        }
    }

    // Hàng đợi lệnh (FIFO Channel) đảm bảo nhận diện chính xác từng tiếng pip khi người dùng bấm nhanh
    private val commandChannel = Channel<FujiFanCommand>(capacity = Channel.UNLIMITED)
    private var queueProcessorJob: Job? = null
    private var flashJob: Job? = null
    private var timerConfirmJob: Job? = null

    init {
        viewModelScope.launch {
            verifier.verificationState.collect { vState ->
                _uiState.update { it.copy(verificationState = vState) }
            }
        }
        ensureQueueProcessorRunning()
    }

    private fun ensureQueueProcessorRunning() {
        if (queueProcessorJob?.isActive != true) {
            queueProcessorJob = viewModelScope.launch {
                while (isActive) {
                    try {
                        val firstCommand = commandChannel.receive()
                        processCommandBatch(firstCommand)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FujiHomeViewModel", "Lỗi trong vòng lặp xử lý hàng đợi", e)
                    }
                }
            }
        }
    }

    fun hasRecordPermission(): Boolean = verifier.hasRecordPermission()

    fun toggleAcousticVerification() {
        _uiState.update { it.copy(isAcousticVerificationEnabled = !it.isAcousticVerificationEnabled) }
    }

    fun retryLastCommand() {
        val lastCmd = _uiState.value.activeCommand ?: FujiFanCommand.POWER_OFF
        sendCommand(lastCmd)
    }

    fun setInitialFanPower(isOn: Boolean) {
        _uiState.update {
            it.copy(
                isPowerOn = isOn,
                showInitialPowerDialog = false
            )
        }
        persistState(_uiState.value)
    }

    fun dismissInitialPowerDialog() {
        _uiState.update { it.copy(showInitialPowerDialog = false) }
    }

    /**
     * Gửi lệnh quạt thông qua Hàng Đợi (Command Queue):
     * - Phản hồi rung xúc giác tức thì khi bấm phím
     * - Khi bấm nhanh liên tiếp (ví dụ ấn tăng tốc độ 2 lần), cả 2 lệnh đều được xếp hàng tuần tự
     * - Mỗi lệnh phát IR -> nhận 1 tiếng pip từ quạt -> cập nhật UI đúng 1 mức tương ứng
     * - Worker chạy vòng lặp liên tục chờ lệnh; không bị tình trạng kết thúc tiến trình làm kẹt lệnh
     */
    fun sendCommand(command: FujiFanCommand) {
        // 1. Phản hồi xúc giác tức thì khi ngón tay chạm phím
        triggerTapHaptic()

        // 2. Nếu là nút TẮT NGUỒN: Xả sạch các lệnh tăng/giảm gió cũ đang xếp hàng
        if (command == FujiFanCommand.POWER_OFF) {
            while (commandChannel.tryReceive().isSuccess) {}
            timerConfirmJob?.cancel()
        }

        // 3. Đưa lệnh vào hàng đợi tuần tự
        commandChannel.trySend(command)

        // 4. Đảm bảo Worker xử lý hàng đợi luôn chạy
        ensureQueueProcessorRunning()
    }

    /**
     * Xử lý một đợt lệnh:
     * - Sử dụng phiên âm thanh liên tục (Audio Session) để không phải dừng/khởi tạo lại AudioRecord giữa các phím bấm nhanh.
     * - Sau khi xử lý firstCommand, giữ session mở trong 650ms để nhận các lệnh bấm tiếp theo.
     * - Nếu sau 650ms không có lệnh mới, session tự đóng giải phóng mic và vòng lặp chính quay về chờ lệnh mới.
     * - Nếu lệnh mới đến đúng lúc session đang đóng, vòng lặp chính sẽ lập tức nhận lệnh ở vòng lặp tiếp theo, không bao giờ bị kẹt.
     */
    private suspend fun processCommandBatch(firstCommand: FujiFanCommand) {
        if (_uiState.value.isAcousticVerificationEnabled && verifier.hasRecordPermission()) {
            var firstExecuted = false
            try {
                verifier.runVerificationSession { session ->
                    executeCommandInSession(session, firstCommand)
                    firstExecuted = true

                    while (true) {
                        // Chờ lệnh tiếp theo trong hàng đợi; nếu sau 650ms không có phím nào bấm thêm -> đóng session
                        val nextCommand = withTimeoutOrNull(650) {
                            commandChannel.receive()
                        } ?: break

                        executeCommandInSession(session, nextCommand)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FujiHomeViewModel", "Lỗi trong phiên xác thực âm thanh", e)
                if (!firstExecuted) {
                    executeCommandDirect(firstCommand)
                }
                while (true) {
                    val nextCommand = commandChannel.tryReceive().getOrNull() ?: break
                    executeCommandDirect(nextCommand)
                }
            }
        } else {
            // Chế độ không xác thực âm thanh hoặc chưa cấp mic
            executeCommandDirect(firstCommand)
            while (true) {
                val nextCommand = withTimeoutOrNull(500) {
                    commandChannel.receive()
                } ?: break

                executeCommandDirect(nextCommand)
            }
        }
    }

    private suspend fun executeCommandInSession(
        session: IrVerificationSession,
        command: FujiFanCommand
    ) {
        _uiState.update { it.copy(activeCommand = command) }

        // Hiệu ứng chớp đèn phát hồng ngoại trên UI
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            _uiState.update { it.copy(isLedFlashing = true) }
            delay(200)
            _uiState.update { it.copy(isLedFlashing = false) }
        }

        // Phát IR và chờ tiếng pip xác nhận từ quạt cho lệnh này
        val (transmitResult, detectResult) = session.transmitAndVerify(command)
        _uiState.update { it.copy(lastResult = transmitResult) }

        // QUY TẮC: Chỉ khi nhận diện được tiếng pip xác nhận từ quạt mới update UI
        if (detectResult is BeepDetectionState.BeepDetected) {
            if (command == FujiFanCommand.TIMER) {
                applyTimerCycleSuccess()
            } else {
                if (command == FujiFanCommand.POWER_OFF) {
                    timerConfirmJob?.cancel()
                }
                updateFanSimulationState(command)
            }
        } else if (detectResult is BeepDetectionState.NoBeepDetected) {
            // Quạt không nhận được tín hiệu -> Không cập nhật UI
            _uiState.update { it.copy(verificationState = detectResult) }
        }

        // Khoảng giãn cách an toàn 50ms giữa 2 lệnh liên tiếp để buồng âm thanh lắng xuống
        delay(50)
    }

    private suspend fun executeCommandDirect(command: FujiFanCommand) {
        _uiState.update { it.copy(activeCommand = command) }

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            _uiState.update { it.copy(isLedFlashing = true) }
            delay(200)
            _uiState.update { it.copy(isLedFlashing = false) }
        }

        val transmitResult = verifier.irTransmitter.transmit(command)
        if (command == FujiFanCommand.TIMER) {
            applyTimerCycleSuccess()
        } else {
            if (command == FujiFanCommand.POWER_OFF) {
                timerConfirmJob?.cancel()
            }
            updateFanSimulationState(command)
        }

        _uiState.update {
            it.copy(
                lastResult = transmitResult,
                verificationState = if (!verifier.hasRecordPermission()) {
                    BeepDetectionState.Error("Chưa cấp quyền Micro")
                } else {
                    BeepDetectionState.Idle
                }
            )
        }

        delay(75) // Giữ khoảng cách giữa các khung tín hiệu NEC
    }

    /**
     * Xử lý chu trình Hẹn Giờ khi nhận được tiếng pip từ quạt:
     * - Kích hoạt lần đầu hay bất kỳ lần kích hoạt tiếp theo: bắt đầu từ số 0
     * - Mỗi lần bấm tiếp trong phiên: 0h -> 1h -> 2h -> 4h -> 8h -> 0h
     * - Sau ~3.5s không bấm thêm: Quạt kêu tiếng pip dài xác nhận
     */
    private fun applyTimerCycleSuccess() {
        val current = _uiState.value

        val nextPending = if (!current.isTimerSettingMode) {
            0
        } else {
            when (current.pendingTimerHours) {
                0 -> 1
                1 -> 2
                2 -> 4
                4 -> 8
                else -> 0
            }
        }

        _uiState.update {
            it.copy(
                isPowerOn = true,
                isTimerSettingMode = true,
                pendingTimerHours = nextPending,
                longBeepNotification = null
            )
        }

        // Bộ hẹn giờ debounce (~3.5 giây không bấm thêm nút Hẹn Giờ)
        timerConfirmJob?.cancel()
        timerConfirmJob = viewModelScope.launch {
            delay(3500) // Chờ khoảng thời gian xác nhận từ quạt

            val pending = _uiState.value.pendingTimerHours
            val notificationText = if (pending == 0) {
                "Pip dài: Đã tắt hẹn giờ"
            } else {
                "Pip dài: Đã lưu hẹn giờ ${pending}H"
            }

            triggerLongBeepHaptic()

            _uiState.update {
                it.copy(
                    timerHours = pending,
                    isTimerSettingMode = false,
                    longBeepNotification = notificationText
                )
            }
            persistState(_uiState.value)

            delay(3000)
            _uiState.update {
                if (it.longBeepNotification == notificationText) {
                    it.copy(longBeepNotification = null)
                } else {
                    it
                }
            }
        }
    }

    private fun triggerTapHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (_: Exception) {}
    }

    private fun triggerLongBeepHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(450)
            }
        } catch (_: Exception) {}
    }

    /**
     * Cập nhật trạng thái giả lập của quạt trên UI sau khi quạt đã phản hồi tiếng Beep
     */
    private fun updateFanSimulationState(command: FujiFanCommand) {
        _uiState.update { current ->
            when (command) {
                FujiFanCommand.POWER_OFF -> {
                    current.copy(
                        isPowerOn = !current.isPowerOn,
                        // Khi bật quạt lên từ trạng thái tắt, mặc định mức 4 gió thường
                        speedLevel = if (!current.isPowerOn) 4 else current.speedLevel,
                        savedNormalSpeed = if (!current.isPowerOn) 4 else current.savedNormalSpeed,
                        mode = if (!current.isPowerOn) FanMode.NORMAL else current.mode,
                        isTimerSettingMode = false,
                        timerHours = if (!current.isPowerOn) current.timerHours else 0,
                        pendingTimerHours = 0,
                        longBeepNotification = null
                    )
                }

                FujiFanCommand.SPEED_PLUS -> {
                    when (current.mode) {
                        FanMode.TURBO -> {
                            // Khi chế độ Turbo đang bật, bấm tăng tốc độ chỉ làm tắt chế độ đó và khôi phục tốc độ cũ
                            current.copy(
                                isPowerOn = true,
                                mode = FanMode.NORMAL,
                                speedLevel = current.savedNormalSpeed
                            )
                        }
                        FanMode.SLEEP -> {
                            // Khi chế độ Ban đêm đang bật, bấm tăng tốc độ sẽ tắt chế độ ban đêm đồng thời tăng thêm 1 mức tốc độ
                            val baseSpeed = current.savedNormalSpeed
                            val nextSpeed = if (baseSpeed >= 8) 1 else baseSpeed + 1
                            current.copy(
                                isPowerOn = true,
                                mode = FanMode.NORMAL,
                                speedLevel = nextSpeed,
                                savedNormalSpeed = nextSpeed
                            )
                        }
                        FanMode.NORMAL -> {
                            val currentSpeed = current.speedLevel
                            val nextSpeed = if (currentSpeed >= 8) 1 else currentSpeed + 1
                            current.copy(
                                isPowerOn = true,
                                speedLevel = nextSpeed,
                                savedNormalSpeed = nextSpeed,
                                mode = FanMode.NORMAL
                            )
                        }
                    }
                }

                FujiFanCommand.SPEED_MINUS -> {
                    when (current.mode) {
                        FanMode.TURBO -> {
                            // Khi chế độ Turbo đang bật, bấm giảm tốc độ chỉ làm tắt chế độ đó và khôi phục tốc độ cũ
                            current.copy(
                                isPowerOn = true,
                                mode = FanMode.NORMAL,
                                speedLevel = current.savedNormalSpeed
                            )
                        }
                        FanMode.SLEEP -> {
                            // Khi chế độ Ban đêm đang bật, bấm giảm tốc độ sẽ tắt chế độ ban đêm đồng thời giảm thêm 1 mức tốc độ
                            val baseSpeed = current.savedNormalSpeed
                            val nextSpeed = if (baseSpeed <= 1) 8 else baseSpeed - 1
                            current.copy(
                                isPowerOn = true,
                                mode = FanMode.NORMAL,
                                speedLevel = nextSpeed,
                                savedNormalSpeed = nextSpeed
                            )
                        }
                        FanMode.NORMAL -> {
                            val currentSpeed = current.speedLevel
                            val nextSpeed = if (currentSpeed <= 1) 8 else currentSpeed - 1
                            current.copy(
                                isPowerOn = true,
                                speedLevel = nextSpeed,
                                savedNormalSpeed = nextSpeed,
                                mode = FanMode.NORMAL
                            )
                        }
                    }
                }

                FujiFanCommand.HORIZONTAL -> {
                    current.copy(
                        isPowerOn = true,
                        isHorizontalSwing = !current.isHorizontalSwing
                    )
                }

                FujiFanCommand.VERTICAL -> {
                    current.copy(
                        isPowerOn = true,
                        isVerticalSwing = !current.isVerticalSwing
                    )
                }

                FujiFanCommand.TURBO -> {
                    if (current.mode == FanMode.TURBO) {
                        // TẮT Turbo: Phục hồi lại giá trị tốc độ trước đó trên UI
                        current.copy(
                            isPowerOn = true,
                            mode = FanMode.NORMAL,
                            speedLevel = current.savedNormalSpeed
                        )
                    } else {
                        // BẬT Turbo: Lưu lại mức gió hiện tại và đặt hiển thị mức 8
                        val currentNormal = if (current.mode == FanMode.NORMAL) current.speedLevel else current.savedNormalSpeed
                        current.copy(
                            isPowerOn = true,
                            mode = FanMode.TURBO,
                            savedNormalSpeed = currentNormal,
                            speedLevel = 8
                        )
                    }
                }

                FujiFanCommand.SLEEP -> {
                    if (current.mode == FanMode.SLEEP) {
                        // TẮT Chế độ đêm: Phục hồi lại giá trị tốc độ trước đó trên UI
                        current.copy(
                            isPowerOn = true,
                            mode = FanMode.NORMAL,
                            speedLevel = current.savedNormalSpeed
                        )
                    } else {
                        // BẬT Chế độ đêm: Lưu lại mức gió hiện tại và đặt hiển thị mức 1
                        val currentNormal = if (current.mode == FanMode.NORMAL) current.speedLevel else current.savedNormalSpeed
                        current.copy(
                            isPowerOn = true,
                            mode = FanMode.SLEEP,
                            savedNormalSpeed = currentNormal,
                            speedLevel = 1
                        )
                    }
                }

                FujiFanCommand.TIMER -> {
                    current
                }
            }
        }
        persistState(_uiState.value)
    }
}
