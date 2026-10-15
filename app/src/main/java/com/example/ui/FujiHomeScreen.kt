package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.BeepDetectionState
import com.example.ir.FujiFanCommand
import com.example.ui.theme.AeroOnPrimary
import com.example.ui.theme.AeroOnSurface
import com.example.ui.theme.AeroOnSurfaceVariant
import com.example.ui.theme.AeroOutlineVariant
import com.example.ui.theme.AeroPrimary
import com.example.ui.theme.AeroPrimaryFixed
import com.example.ui.theme.AeroSecondary
import com.example.ui.theme.AeroSleepIndigo
import com.example.ui.theme.AeroSurface
import com.example.ui.theme.AeroSurfaceContainer
import com.example.ui.theme.AeroSurfaceContainerHigh
import com.example.ui.theme.AeroSurfaceContainerHighest
import com.example.ui.theme.AeroSurfaceContainerLow
import com.example.ui.theme.AeroSurfaceContainerLowest
import com.example.ui.theme.AeroTurboAmber
import com.example.ui.theme.Manrope
import com.example.ui.theme.SpaceGrotesk

/**
 * Giao diện Aero Minimalist IoT cho FujiHome Remote
 * Tích hợp nhận diện âm thanh tiếng pip 4020Hz xác nhận quạt đã nhận lệnh
 */
@Composable
fun FujiHomeScreen(
    viewModel: FujiHomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.retryLastCommand()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AeroSurfaceContainerLowest, AeroSurface, Color(0xFF060D1E))
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Header (Logo + Brand + Online Badge + Mic Toggle)
            FujiHeader(
                isHardwareAvailable = uiState.isHardwareAvailable,
                isLedFlashing = uiState.isLedFlashing,
                isMicEnabled = uiState.isAcousticVerificationEnabled,
                hasMicPermission = viewModel.hasRecordPermission(),
                onToggleMic = {
                    if (!viewModel.hasRecordPermission()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleAcousticVerification()
                    }
                }
            )

            // 2. Fan Status HUD Card
            FanStatusHudCard(
                uiState = uiState,
                onRequestMicPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onRetry = { viewModel.retryLastCommand() }
            )

            // 3. Central Power Glow Button
            CentralGlowingPowerButton(
                isPowerOn = uiState.isPowerOn,
                onTogglePower = { viewModel.sendCommand(FujiFanCommand.POWER_OFF) }
            )

            // 4. Combined Speed Controller Card (GIẢM & TĂNG)
            SpeedControllerCard(
                onSpeedDown = { viewModel.sendCommand(FujiFanCommand.SPEED_MINUS) },
                onSpeedUp = { viewModel.sendCommand(FujiFanCommand.SPEED_PLUS) }
            )

            // 5. Timer Row Card (Hẹn Giờ)
            TimerRowCard(
                timerHours = uiState.displayTimerHours,
                isSettingMode = uiState.isTimerSettingMode,
                onCycleTimer = { viewModel.sendCommand(FujiFanCommand.TIMER) }
            )

            // 6. 2x2 Tactical Grid (Ngang, Dọc, Turbo, Đêm)
            TacticalGrid2x2(
                uiState = uiState,
                onSendCommand = { viewModel.sendCommand(it) }
            )

            // 7. Discreet IR Ticker
            DiscreetIrTicker(
                lastResult = uiState.lastResult,
                isFlashing = uiState.isLedFlashing
            )
        }

        // Popup hỏi nhanh trạng thái quạt khi mở ứng dụng (1. Tắt, 2. Bật)
        if (uiState.showInitialPowerDialog) {
            InitialPowerCheckDialog(
                onSelectPowerState = { isOn ->
                    viewModel.setInitialFanPower(isOn)
                },
                onDismiss = {
                    viewModel.dismissInitialPowerDialog()
                }
            )
        }
    }
}

/**
 * Header: Logo quạt Cyan squircle + FujiHome SMART REMOTE + Mic Button + Pill ONLINE
 */
@Composable
private fun FujiHeader(
    isHardwareAvailable: Boolean,
    isLedFlashing: Boolean,
    isMicEnabled: Boolean,
    hasMicPermission: Boolean,
    onToggleMic: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            isLedFlashing -> Color(0xFFFF1744)
            isHardwareAvailable -> AeroPrimary
            else -> AeroTurboAmber
        },
        label = "statusColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo + Brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AeroPrimary.copy(alpha = 0.2f))
                    .border(1.dp, AeroPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                QuadBladeFan(
                    modifier = Modifier.size(22.dp),
                    tint = AeroPrimary
                )
            }

            Column {
                Text(
                    text = "FujiHome",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = AeroOnSurface,
                    lineHeight = 21.sp
                )
                Text(
                    text = "SMART REMOTE",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = AeroOnSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nút Micro lắng nghe tiếng pip
            Surface(
                shape = CircleShape,
                color = if (isMicEnabled && hasMicPermission) AeroPrimary.copy(alpha = 0.2f) else AeroSurfaceContainer,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isMicEnabled && hasMicPermission) AeroPrimary.copy(alpha = 0.5f) else AeroOutlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onToggleMic)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isMicEnabled && hasMicPermission) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Bật/Tắt Micro nghe tiếng pip",
                        tint = if (isMicEnabled && hasMicPermission) AeroPrimary else AeroOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Pill Online
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = AeroSurfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = if (isLedFlashing) "PHÁT IR" else if (isHardwareAvailable) "ONLINE" else "MÔ PHỎNG",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = statusColor
                    )
                }
            }
        }
    }
}

/**
 * Card FAN STATUS HUD: Vòng cung đo, cánh quạt 4 cánh xoay, dòng xác nhận âm thanh Beep
 */
@Composable
private fun FanStatusHudCard(
    uiState: FujiHomeUiState,
    onRequestMicPermission: () -> Unit,
    onRetry: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotorSpin")
    val durationMillis = when {
        !uiState.isPowerOn -> 100000
        uiState.mode == FanMode.TURBO -> 340
        uiState.mode == FanMode.SLEEP -> 1700
        else -> (2100 - (uiState.speedLevel * 210)).coerceAtLeast(360)
    }

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotorAngle"
    )

    val arcColor = when {
        !uiState.isPowerOn -> AeroOutlineVariant.copy(alpha = 0.2f)
        uiState.mode == FanMode.TURBO -> AeroTurboAmber
        uiState.mode == FanMode.SLEEP -> AeroSleepIndigo
        else -> AeroPrimary
    }

    val sweepAngle = when {
        !uiState.isPowerOn -> 0f
        uiState.mode == FanMode.TURBO -> 360f
        uiState.mode == FanMode.SLEEP -> 90f
        else -> (uiState.speedLevel / 8f) * 360f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AeroSurfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top HUD Row: Speedometer + HUD Title + 5 mini icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = AeroPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "HUD",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp,
                        color = AeroPrimary
                    )
                }

                // 5 mini status glyphs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    MiniIcon(Icons.Default.PowerSettingsNew, uiState.isPowerOn, AeroPrimary)
                    MiniIcon(Icons.AutoMirrored.Filled.CompareArrows, uiState.isPowerOn && uiState.isHorizontalSwing, AeroPrimary)
                    MiniIcon(Icons.Default.SwapVert, uiState.isPowerOn && uiState.isVerticalSwing, AeroPrimary)
                    MiniIcon(Icons.Default.Bolt, uiState.isPowerOn && uiState.mode == FanMode.TURBO, AeroTurboAmber)
                    MiniIcon(Icons.Default.Bedtime, uiState.isPowerOn && uiState.mode == FanMode.SLEEP, AeroSleepIndigo)
                }
            }

            // Central Dial
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    drawCircle(
                        color = AeroSurfaceContainerHigh,
                        style = Stroke(width = strokeWidth)
                    )
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner Capsule
                Box(
                    modifier = Modifier
                        .size(114.dp)
                        .clip(CircleShape)
                        .background(AeroSurfaceContainer)
                        .border(1.5.dp, arcColor.copy(alpha = 0.35f), CircleShape)
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        QuadBladeFan(
                            modifier = Modifier
                                .size(38.dp)
                                .rotate(if (uiState.isPowerOn) rotationAngle else 0f),
                            tint = if (uiState.isPowerOn) arcColor else AeroOnSurfaceVariant.copy(alpha = 0.35f)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (!uiState.isPowerOn) {
                            Text(
                                text = "TẮT",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AeroOnSurfaceVariant.copy(alpha = 0.45f)
                            )
                        } else if (uiState.mode == FanMode.TURBO) {
                            Text(
                                text = "TURBO",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = AeroTurboAmber
                            )
                            Text(
                                text = "TỐI ĐA",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = AeroTurboAmber
                            )
                        } else if (uiState.mode == FanMode.SLEEP) {
                            Text(
                                text = "ĐÊM",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = AeroSleepIndigo
                            )
                            Text(
                                text = "ÊM ÁI",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = AeroSleepIndigo
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${uiState.speedLevel}",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = AeroOnSurface,
                                    lineHeight = 30.sp
                                )
                                Text(
                                    text = " / 8",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = AeroOnSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                            Text(
                                text = "CẤP ĐỘ",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                color = AeroPrimary
                            )
                        }
                    }
                }
            }

            // Bottom HUD Row: Verification Feedback / Telemetry
            AcousticVerificationFeedbackRow(
                verificationState = uiState.verificationState,
                longBeepNotification = uiState.longBeepNotification,
                isTimerSettingMode = uiState.isTimerSettingMode,
                timerHours = uiState.displayTimerHours,
                windDescriptor = uiState.windDescriptor,
                onRequestMicPermission = onRequestMicPermission,
                onRetry = onRetry
            )
        }
    }
}

/**
 * Hiển thị trạng thái phản hồi âm thanh (Beep 4020Hz) và xác nhận hẹn giờ
 */
@Composable
private fun AcousticVerificationFeedbackRow(
    verificationState: BeepDetectionState,
    longBeepNotification: String?,
    isTimerSettingMode: Boolean,
    timerHours: Int,
    windDescriptor: String,
    onRequestMicPermission: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            // Ưu tiên 1: Thông báo quạt kêu tiếng pip dài xác nhận
            longBeepNotification != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Text(longBeepNotification, fontFamily = SpaceGrotesk, fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
            }
            // Ưu tiên 2: Đang trong phiên điều chỉnh hẹn giờ (chờ debounce 3.5s)
            isTimerSettingMode -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AeroTurboAmber, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Đang chọn: ${timerHours}H (Chờ xác nhận...)",
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        color = AeroTurboAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            verificationState is BeepDetectionState.Transmitting -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = AeroPrimary, modifier = Modifier.size(15.dp))
                    Text("Đang phát IR...", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroPrimary, fontWeight = FontWeight.Bold)
                }
            }
            verificationState is BeepDetectionState.Listening -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = AeroPrimary, modifier = Modifier.size(15.dp))
                    Text("Đang nghe pip (4020Hz)...", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            verificationState is BeepDetectionState.BeepDetected -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Text("Quạt đã nhận lệnh!", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
            }
            verificationState is BeepDetectionState.NoBeepDetected -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(onClick = onRetry)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AeroTurboAmber, modifier = Modifier.size(15.dp))
                    Text("Chưa nghe pip • Thử lại ↺", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroTurboAmber, fontWeight = FontWeight.Bold)
                }
            }
            verificationState is BeepDetectionState.Error -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(onClick = onRequestMicPermission)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AeroTurboAmber, modifier = Modifier.size(15.dp))
                    Text("Bật quyền Mic 🎤", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroTurboAmber, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AeroPrimary, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (timerHours == 0) "HẸN GIỜ: TẮT" else "HẸN GIỜ: ${timerHours}H",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = AeroOnSurface
                    )
                }
            }
        }

        // Right side: Wind mode
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Air,
                contentDescription = null,
                tint = AeroSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = windDescriptor,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = AeroSecondary
            )
        }
    }
}

@Composable
private fun MiniIcon(icon: ImageVector, isActive: Boolean, activeColor: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isActive) activeColor else AeroOnSurfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.size(15.dp)
    )
}

/**
 * Cánh quạt 4 cánh cong khí động học chuẩn như hình mẫu
 */
@Composable
fun QuadBladeFan(
    modifier: Modifier = Modifier,
    tint: Color = AeroPrimary
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.94f
        val hubRadius = r * 0.22f

        for (i in 0 until 4) {
            val baseAngleDeg = i * 90.0
            val baseAngle = Math.toRadians(baseAngleDeg)
            val tipAngle = Math.toRadians(baseAngleDeg + 34.0)
            val backAngle = Math.toRadians(baseAngleDeg + 54.0)

            val pStart = Offset(
                cx + hubRadius * Math.cos(baseAngle).toFloat(),
                cy + hubRadius * Math.sin(baseAngle).toFloat()
            )
            val pTip = Offset(
                cx + r * Math.cos(tipAngle).toFloat(),
                cy + r * Math.sin(tipAngle).toFloat()
            )
            val pEnd = Offset(
                cx + hubRadius * Math.cos(backAngle).toFloat(),
                cy + hubRadius * Math.sin(backAngle).toFloat()
            )

            val ctrl1 = Offset(
                cx + r * 0.65f * Math.cos(baseAngle - 0.15).toFloat(),
                cy + r * 0.65f * Math.sin(baseAngle - 0.15).toFloat()
            )
            val ctrl2 = Offset(
                cx + r * 0.76f * Math.cos(backAngle + 0.1).toFloat(),
                cy + r * 0.76f * Math.sin(backAngle + 0.1).toFloat()
            )

            val path = Path().apply {
                moveTo(pStart.x, pStart.y)
                quadraticTo(ctrl1.x, ctrl1.y, pTip.x, pTip.y)
                quadraticTo(ctrl2.x, ctrl2.y, pEnd.x, pEnd.y)
                close()
            }
            drawPath(path, color = tint)
        }

        drawCircle(color = tint, radius = hubRadius)
        drawCircle(color = AeroSurfaceContainerLowest, radius = hubRadius * 0.45f)
    }
}

/**
 * Nút Power trung tâm phát sáng mạnh mẽ
 */
@Composable
private fun CentralGlowingPowerButton(
    isPowerOn: Boolean,
    onTogglePower: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        label = "pwrScale"
    )

    Box(
        modifier = Modifier.padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isPowerOn) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AeroPrimary.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            )
        }

        Surface(
            modifier = Modifier
                .scale(scale)
                .size(78.dp)
                .testTag("btn_power")
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = if (isPowerOn) Color.White else AeroPrimary),
                    onClick = onTogglePower
                ),
            shape = CircleShape,
            color = if (isPowerOn) AeroPrimary else AeroSurfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (isPowerOn) AeroPrimaryFixed else AeroOutlineVariant.copy(alpha = 0.4f)
            ),
            shadowElevation = if (isPowerOn) 12.dp else 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Bật/Tắt quạt",
                    tint = if (isPowerOn) AeroOnPrimary else AeroOnSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "POWER",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = if (isPowerOn) AeroOnPrimary else AeroOnSurfaceVariant
                )
            }
        }
    }
}

/**
 * Thẻ Điều Chỉnh Tốc Độ: Khung card hợp nhất chứa 2 nút GIẢM và TĂNG
 */
@Composable
private fun SpeedControllerCard(
    onSpeedDown: () -> Unit,
    onSpeedUp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AeroSurfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HalfSpeedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_speed_minus"),
                title = "GIẢM",
                icon = Icons.Default.Remove,
                isLeftAligned = true,
                onClick = onSpeedDown
            )

            HalfSpeedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_speed_plus"),
                title = "TĂNG",
                icon = Icons.Default.Add,
                isLeftAligned = false,
                onClick = onSpeedUp
            )
        }
    }
}

@Composable
private fun HalfSpeedButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isLeftAligned: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        label = "btnScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(58.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = AeroPrimary),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = AeroSurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPressed) AeroPrimary.copy(alpha = 0.5f) else AeroOutlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isLeftAligned) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AeroSurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = AeroPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AeroOnSurface
                )
            } else {
                Text(
                    text = title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AeroOnSurface
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AeroSurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = AeroPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Thẻ Hẹn Giờ Tắt: Squircle icon + Tiêu đề + Pill badge bên phải
 */
@Composable
private fun TimerRowCard(
    timerHours: Int,
    isSettingMode: Boolean,
    onCycleTimer: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        label = "timerScale"
    )

    val isSavedActive = !isSettingMode && timerHours > 0

    val borderColor = when {
        isSettingMode -> AeroTurboAmber.copy(alpha = 0.6f)
        isSavedActive -> AeroPrimary.copy(alpha = 0.5f)
        else -> AeroOutlineVariant.copy(alpha = 0.3f)
    }

    val iconBgColor = when {
        isSettingMode -> AeroTurboAmber.copy(alpha = 0.25f)
        isSavedActive -> AeroPrimary
        else -> AeroSurfaceContainerHigh
    }

    val iconColor = when {
        isSettingMode -> AeroTurboAmber
        isSavedActive -> AeroOnPrimary
        else -> AeroPrimary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .height(54.dp)
            .testTag("btn_timer")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = AeroPrimary),
                onClick = onCycleTimer
            ),
        shape = RoundedCornerShape(20.dp),
        color = AeroSurfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Hẹn giờ",
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = "Hẹn Giờ",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AeroOnSurface
                    )
                    if (isSettingMode) {
                        Text(
                            text = if (timerHours == 0) "Chờ tắt hẹn giờ..." else "Chờ lưu hẹn giờ...",
                            fontFamily = SpaceGrotesk,
                            fontSize = 10.sp,
                            color = AeroTurboAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = when {
                    isSettingMode -> AeroTurboAmber.copy(alpha = 0.2f)
                    isSavedActive -> AeroPrimary.copy(alpha = 0.2f)
                    else -> AeroSurfaceContainer
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isSettingMode -> AeroTurboAmber.copy(alpha = 0.7f)
                        isSavedActive -> AeroPrimary.copy(alpha = 0.6f)
                        else -> AeroOutlineVariant.copy(alpha = 0.3f)
                    }
                )
            ) {
                Text(
                    text = when {
                        isSettingMode && timerHours == 0 -> "0 Giờ (Chờ...)"
                        isSettingMode -> "${timerHours} Giờ (Chờ...)"
                        timerHours == 0 -> "0 Giờ"
                        else -> "${timerHours} Giờ"
                    },
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when {
                        isSettingMode -> AeroTurboAmber
                        isSavedActive -> AeroPrimary
                        else -> AeroOnSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Lưới 4 nút điều khiển 2x2: Ngang, Dọc, Turbo, Đêm
 */
@Composable
private fun TacticalGrid2x2(
    uiState: FujiHomeUiState,
    onSendCommand: (FujiFanCommand) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GridCardButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_horizontal"),
                title = "Ngang",
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                isActive = uiState.isPowerOn && uiState.isHorizontalSwing,
                activeColor = AeroPrimary,
                onClick = { onSendCommand(FujiFanCommand.HORIZONTAL) }
            )

            GridCardButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_vertical"),
                title = "Dọc",
                icon = Icons.Default.SwapVert,
                isActive = uiState.isPowerOn && uiState.isVerticalSwing,
                activeColor = AeroPrimary,
                onClick = { onSendCommand(FujiFanCommand.VERTICAL) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GridCardButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_turbo"),
                title = "Turbo",
                icon = Icons.Default.Bolt,
                isActive = uiState.isPowerOn && uiState.mode == FanMode.TURBO,
                activeColor = AeroTurboAmber,
                onClick = { onSendCommand(FujiFanCommand.TURBO) }
            )

            GridCardButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_sleep"),
                title = "Đêm",
                icon = Icons.Default.Bedtime,
                isActive = uiState.isPowerOn && uiState.mode == FanMode.SLEEP,
                activeColor = AeroSleepIndigo,
                onClick = { onSendCommand(FujiFanCommand.SLEEP) }
            )
        }
    }
}

@Composable
private fun GridCardButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        label = "gridScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(68.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = activeColor),
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) activeColor.copy(alpha = 0.16f) else AeroSurfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive || isPressed) 1.5.dp else 1.dp,
            color = if (isActive || isPressed) activeColor else AeroOutlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else AeroSurfaceContainerHighest)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) activeColor else AeroOnSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isActive) activeColor else AeroOnSurface
                )
            }
        }
    }
}

/**
 * Dòng ticker nhỏ gọn ở chân màn hình
 */
@Composable
private fun DiscreetIrTicker(
    lastResult: com.example.ir.TransmitResult?,
    isFlashing: Boolean
) {
    Text(
        text = if (lastResult != null) {
            "${lastResult.command.fullHex} • 38kHz"
        } else {
            "NEC 38kHz • Addr: 0x00"
        },
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = if (isFlashing) AeroPrimary else AeroOnSurfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

/**
 * Popup hỏi nhanh người dùng quạt đang bật hay tắt khi mở ứng dụng
 */
@Composable
private fun InitialPowerCheckDialog(
    onSelectPowerState: (isOn: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AeroSurfaceContainerHigh),
            border = BorderStroke(1.dp, AeroOutlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Biểu tượng quạt trung tâm với hiệu ứng nền AeroPrimary
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AeroPrimary.copy(alpha = 0.12f))
                        .border(1.dp, AeroPrimary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = AeroPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Trạng thái quạt hiện tại",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = AeroOnSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Quạt của bạn hiện đang bật hay tắt?",
                    fontFamily = Manrope,
                    fontSize = 14.sp,
                    color = AeroOnSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Lựa chọn 1: TẮT
                Surface(
                    onClick = { onSelectPowerState(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("power_status_off_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF23171B),
                    border = BorderStroke(1.dp, Color(0xFF6E2A38).copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "1. Tắt",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AeroOnSurface
                            )
                            Text(
                                text = "Quạt hiện đang tắt nguồn",
                                fontFamily = Manrope,
                                fontSize = 12.sp,
                                color = AeroOnSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lựa chọn 2: BẬT
                Surface(
                    onClick = { onSelectPowerState(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("power_status_on_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F2B36),
                    border = BorderStroke(1.5.dp, AeroPrimary.copy(alpha = 0.85f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AeroPrimary.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AeroPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "2. Bật",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AeroPrimary
                            )
                            Text(
                                text = "Quạt hiện đang chạy",
                                fontFamily = Manrope,
                                fontSize = 12.sp,
                                color = AeroOnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
