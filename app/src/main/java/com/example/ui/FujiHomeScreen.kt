package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.BeepDetectionState
import com.example.ir.FujiFanCommand
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroOnPrimary
import com.example.ui.theme.AeroOnSurface
import com.example.ui.theme.AeroOnSurfaceVariant
import com.example.ui.theme.AeroOutlineVariant
import com.example.ui.theme.AeroPrimary
import com.example.ui.theme.AeroPrimaryFixed
import com.example.ui.theme.AeroSecondary
import com.example.ui.theme.AeroSleepIndigo
import com.example.ui.theme.AeroSurfaceContainer
import com.example.ui.theme.AeroSurfaceContainerHigh
import com.example.ui.theme.AeroSurfaceContainerHighest
import com.example.ui.theme.AeroSurfaceContainerLow
import com.example.ui.theme.AeroSurfaceContainerLowest
import com.example.ui.theme.AeroTurboAmber
import com.example.ui.theme.Manrope
import com.example.ui.theme.SpaceGrotesk

/**
 * Giao diện Aero Minimalist IoT cho FujiHome Remote.
 * Bố cục chuẩn xác theo thiết kế HTML Cockpit với HUD tốc độ, điều khiển Nguồn/Tăng/Giảm,
 * cụm Turbo/Sleep và cụm Quay Ngang/Hẹn Giờ/Quay Dọc.
 * Tự động ghi nhớ trạng thái quạt khi thoát app mà không cần popup xác nhận.
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
                    colors = listOf(
                        Color(0xFF0B1120),
                        AeroBackground,
                        Color(0xFF060E20)
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header (Logo + Brand + Online Badge + Mic Toggle)
            CockpitHeader(
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

            // 2. HUD Cockpit Card (Speedometer, 5 status glyphs, Center dial, Timer & Wind descriptor)
            HudCockpitCard(
                uiState = uiState
            )

            // 3. Middle Stepper Row: GIẢM - NGUỒN - TĂNG
            PowerAndSpeedStepperCard(
                isPowerOn = uiState.isPowerOn,
                onTogglePower = { viewModel.sendCommand(FujiFanCommand.POWER_OFF) },
                onSpeedDown = { viewModel.sendCommand(FujiFanCommand.SPEED_MINUS) },
                onSpeedUp = { viewModel.sendCommand(FujiFanCommand.SPEED_PLUS) }
            )

            // 4. Grid 2 Columns: Turbo & Chế Độ Đêm
            TurboAndSleepRow(
                uiState = uiState,
                onToggleTurbo = { viewModel.sendCommand(FujiFanCommand.TURBO) },
                onToggleSleep = { viewModel.sendCommand(FujiFanCommand.SLEEP) }
            )

            // 5. Grid 3 Columns: Quay Ngang - Hẹn Giờ - Quay Dọc
            MotionAndTimerRow(
                uiState = uiState,
                onToggleHorizontal = { viewModel.sendCommand(FujiFanCommand.HORIZONTAL) },
                onCycleTimer = { viewModel.sendCommand(FujiFanCommand.TIMER) },
                onToggleVertical = { viewModel.sendCommand(FujiFanCommand.VERTICAL) }
            )

            // 6. Acoustic Verification Feedback & IR NEC Ticker
            CockpitAcousticFeedbackBanner(
                verificationState = uiState.verificationState,
                longBeepNotification = uiState.longBeepNotification,
                isTimerSettingMode = uiState.isTimerSettingMode,
                timerHours = uiState.displayTimerHours,
                lastResult = uiState.lastResult,
                isFlashing = uiState.isLedFlashing,
                onRequestMicPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onRetry = { viewModel.retryLastCommand() }
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * 1. Header: Fixed style header với squircle logo, FujiHome title, Smart Remote subtitle, Mic toggle và status pill.
 */
@Composable
private fun CockpitHeader(
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
            .height(56.dp),
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
                    .border(1.dp, AeroPrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                QuadBladeFan(
                    modifier = Modifier.size(20.dp),
                    tint = AeroPrimary
                )
            }

            Column {
                Text(
                    text = "FujiHome",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    color = AeroOnSurface
                )
                Text(
                    text = "Smart Remote",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    color = AeroOnSurfaceVariant
                )
            }
        }

        // Action icons: Mic + Online Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isMicEnabled && hasMicPermission) AeroPrimary.copy(alpha = 0.18f) else AeroSurfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (isMicEnabled && hasMicPermission) AeroPrimary.copy(alpha = 0.55f) else AeroOutlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onToggleMic)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isMicEnabled && hasMicPermission) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Micro nghe tiếng pip",
                        tint = if (isMicEnabled && hasMicPermission) AeroPrimary else AeroOnSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = AeroSurfaceContainer,
                border = BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
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
 * 2. HUD Cockpit Card:
 * Khung thẻ với rãnh than chì, vòng cung arc phát sáng, cánh quạt xoay, và 2 thông số Hẹn giờ + Luồng gió.
 */
@Composable
private fun HudCockpitCard(
    uiState: FujiHomeUiState
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotorSpin")
    val durationMillis = when {
        !uiState.isPowerOn -> 100000
        uiState.mode == FanMode.TURBO -> 320
        uiState.mode == FanMode.SLEEP -> 1800
        else -> (2100 - (uiState.speedLevel * 200)).coerceAtLeast(360)
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
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AeroSurfaceContainerLow),
        border = BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Bar of HUD: HUD COCKPIT + 5 Mini Status Glyphs
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
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "HUD COCKPIT",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        color = AeroPrimary
                    )
                }

                // 5 Status Glyphs Capsule
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = AeroSurfaceContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniGlyph(Icons.Default.PowerSettingsNew, uiState.isPowerOn, AeroPrimary)
                        MiniGlyph(Icons.AutoMirrored.Filled.CompareArrows, uiState.isPowerOn && uiState.isHorizontalSwing, AeroPrimary)
                        MiniGlyph(Icons.Default.SwapVert, uiState.isPowerOn && uiState.isVerticalSwing, AeroPrimary)
                        MiniGlyph(Icons.Default.Bolt, uiState.isPowerOn && uiState.mode == FanMode.TURBO, AeroTurboAmber)
                        MiniGlyph(Icons.Default.Bedtime, uiState.isPowerOn && uiState.mode == FanMode.SLEEP, AeroSleepIndigo)
                    }
                }
            }

            // Central Circular Arc Dial (w-52 h-52 = ~208dp)
            Box(
                modifier = Modifier.size(208.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow blob
                if (uiState.isPowerOn) {
                    Box(
                        modifier = Modifier
                            .size(208.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        when (uiState.mode) {
                                            FanMode.TURBO -> AeroTurboAmber.copy(alpha = 0.2f)
                                            FanMode.SLEEP -> AeroSleepIndigo.copy(alpha = 0.2f)
                                            else -> AeroPrimary.copy(alpha = 0.2f)
                                        },
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Dial SVG circle & active arc
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 9.dp.toPx()
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

                // Inner Capsule (w-36 h-36 = ~144dp)
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .background(AeroSurfaceContainer)
                        .border(1.dp, AeroOutlineVariant.copy(alpha = 0.25f), CircleShape)
                        .shadow(12.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        QuadBladeFan(
                            modifier = Modifier
                                .size(44.dp)
                                .rotate(if (uiState.isPowerOn) rotationAngle else 0f),
                            tint = if (uiState.isPowerOn) arcColor else AeroOnSurfaceVariant.copy(alpha = 0.35f)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (!uiState.isPowerOn) {
                            Text(
                                text = "TẮT",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = AeroOnSurfaceVariant.copy(alpha = 0.45f)
                            )
                            Text(
                                text = "STANDBY",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                color = AeroOutlineVariant
                            )
                        } else if (uiState.mode == FanMode.TURBO) {
                            Text(
                                text = "TURBO",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = AeroTurboAmber
                            )
                            Text(
                                text = "TỐI ĐA GIÓ",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                color = AeroTurboAmber
                            )
                        } else if (uiState.mode == FanMode.SLEEP) {
                            Text(
                                text = "ĐÊM",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = AeroSleepIndigo
                            )
                            Text(
                                text = "ÊM DỊU 18dB",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                color = AeroSleepIndigo
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${uiState.speedLevel}",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 42.sp,
                                    color = AeroOnSurface,
                                    lineHeight = 42.sp
                                )
                                Text(
                                    text = " / 8",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = AeroOnSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
                                )
                            }
                            Text(
                                text = "CẤP ĐỘ",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                color = AeroPrimary
                            )
                        }
                    }
                }
            }

            // Bottom Row of HUD: Timer badge + Wind descriptor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = AeroPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = if (uiState.displayTimerHours == 0) "TẮT" else "${uiState.displayTimerHours} GIỜ",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AeroOnSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(AeroOutlineVariant.copy(alpha = 0.4f))
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = AeroSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = when {
                            !uiState.isPowerOn -> "Đã Tắt"
                            uiState.mode == FanMode.TURBO -> "Tối Đa"
                            uiState.mode == FanMode.SLEEP -> "Êm Dịu 18dB"
                            else -> "Tự Nhiên"
                        },
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AeroSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniGlyph(icon: ImageVector, isActive: Boolean, activeColor: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isActive) activeColor else AeroOnSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.size(16.dp)
    )
}

/**
 * 3. Middle Stepper Row: GIẢM (phím trái) - NGUỒN (phím tròn giữa) - TĂNG (phím phải)
 */
@Composable
private fun PowerAndSpeedStepperCard(
    isPowerOn: Boolean,
    onTogglePower: () -> Unit,
    onSpeedDown: () -> Unit,
    onSpeedUp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AeroSurfaceContainerLow),
        border = BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút GIẢM (btn_speed_minus)
            StepperSpeedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_speed_minus"),
                title = "GIẢM",
                icon = Icons.Default.Remove,
                onClick = onSpeedDown
            )

            // Nút NGUỒN tròn trung tâm (btn_power)
            val pwrInteractionSource = remember { MutableInteractionSource() }
            val isPwrPressed by pwrInteractionSource.collectIsPressedAsState()
            val pwrScale by animateFloatAsState(
                targetValue = if (isPwrPressed) 0.94f else 1.0f,
                animationSpec = tween(120, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
                label = "pwrScale"
            )

            Surface(
                modifier = Modifier
                    .scale(pwrScale)
                    .size(92.dp)
                    .testTag("btn_power")
                    .clickable(
                        interactionSource = pwrInteractionSource,
                        indication = ripple(bounded = true, color = if (isPowerOn) Color.White else AeroPrimary),
                        onClick = onTogglePower
                    ),
                shape = CircleShape,
                color = if (isPowerOn) AeroPrimary else AeroSurfaceContainerHigh,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isPowerOn) AeroPrimaryFixed else AeroOutlineVariant.copy(alpha = 0.45f)
                ),
                shadowElevation = if (isPowerOn) 16.dp else 4.dp
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
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "NGUỒN",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = if (isPowerOn) AeroOnPrimary else AeroOnSurfaceVariant
                    )
                }
            }

            // Nút TĂNG (btn_speed_plus)
            StepperSpeedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_speed_plus"),
                title = "TĂNG",
                icon = Icons.Default.Add,
                onClick = onSpeedUp
            )
        }
    }
}

@Composable
private fun StepperSpeedButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(120, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
        label = "stepperScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(96.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = AeroPrimary),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = AeroSurfaceContainer,
        border = BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AeroSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = AeroPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AeroOnSurface
            )
        }
    }
}

/**
 * 4. Grid 2 Columns: Turbo & Chế Độ Đêm
 */
@Composable
private fun TurboAndSleepRow(
    uiState: FujiHomeUiState,
    onToggleTurbo: () -> Unit,
    onToggleSleep: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Turbo button
        ModeGridButton(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_turbo"),
            title = "Turbo",
            subtitle = "Tối đa gió",
            icon = Icons.Default.Bolt,
            isActive = uiState.isPowerOn && uiState.mode == FanMode.TURBO,
            activeColor = AeroTurboAmber,
            onClick = onToggleTurbo
        )

        // Chế Độ Đêm button
        ModeGridButton(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_sleep"),
            title = "Chế Độ Đêm",
            subtitle = "Êm dịu 18dB",
            icon = Icons.Default.Bedtime,
            isActive = uiState.isPowerOn && uiState.mode == FanMode.SLEEP,
            activeColor = AeroSleepIndigo,
            onClick = onToggleSleep
        )
    }
}

@Composable
private fun ModeGridButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(120, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
        label = "modeScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(96.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = activeColor),
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) activeColor.copy(alpha = 0.14f) else AeroSurfaceContainer,
        border = BorderStroke(
            1.5.dp,
            if (isActive) activeColor else AeroOutlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) activeColor else AeroOnSurfaceVariant,
                    modifier = Modifier.size(34.dp)
                )

                Column {
                    Text(
                        text = title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isActive) activeColor else AeroOnSurface
                    )
                    Text(
                        text = subtitle,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = AeroOnSurfaceVariant
                    )
                }
            }

            // LED indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else AeroSurfaceContainerHighest)
            )
        }
    }
}

/**
 * 5. Grid 3 Columns: Quay Ngang - Hẹn Giờ - Quay Dọc
 */
@Composable
private fun MotionAndTimerRow(
    uiState: FujiHomeUiState,
    onToggleHorizontal: () -> Unit,
    onCycleTimer: () -> Unit,
    onToggleVertical: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quay Ngang
        TriActionItem(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_horizontal"),
            title = "Quay Ngang",
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            isActive = uiState.isPowerOn && uiState.isHorizontalSwing,
            activeColor = AeroPrimary,
            onClick = onToggleHorizontal
        )

        // Hẹn Giờ
        TimerActionItem(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_timer"),
            timerHours = uiState.displayTimerHours,
            isSettingMode = uiState.isTimerSettingMode,
            onClick = onCycleTimer
        )

        // Quay Dọc
        TriActionItem(
            modifier = Modifier
                .weight(1f)
                .testTag("btn_vertical"),
            title = "Quay Dọc",
            icon = Icons.Default.SwapVert,
            isActive = uiState.isPowerOn && uiState.isVerticalSwing,
            activeColor = AeroSecondary,
            onClick = onToggleVertical
        )
    }
}

@Composable
private fun TriActionItem(
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
        animationSpec = tween(120, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
        label = "triScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(96.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = activeColor),
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) activeColor.copy(alpha = 0.14f) else AeroSurfaceContainer,
        border = BorderStroke(
            1.5.dp,
            if (isActive) activeColor else AeroOutlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // LED Indicator dot top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(9.dp)
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
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
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

@Composable
private fun TimerActionItem(
    modifier: Modifier = Modifier,
    timerHours: Int,
    isSettingMode: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(120, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
        label = "timerScale"
    )

    val isActive = isSettingMode || timerHours > 0
    val activeColor = if (isSettingMode) AeroTurboAmber else AeroPrimary

    Surface(
        modifier = modifier
            .scale(scale)
            .height(96.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = activeColor),
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) activeColor.copy(alpha = 0.14f) else AeroSurfaceContainer,
        border = BorderStroke(
            1.5.dp,
            if (isActive) activeColor else AeroOutlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Hẹn Giờ",
                tint = if (isActive) activeColor else AeroOnSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hẹn Giờ",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = AeroOnSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = AeroSurfaceContainerHigh
            ) {
                Text(
                    text = when {
                        isSettingMode && timerHours == 0 -> "TẮT (Chờ...)"
                        isSettingMode -> "${timerHours}H (Chờ...)"
                        timerHours == 0 -> "TẮT"
                        else -> "${timerHours}H"
                    },
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isActive) activeColor else AeroPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * 6. Cockpit Acoustic Feedback Banner & IR Ticker:
 * Thông báo trạng thái nhận diện tiếng pip 4020Hz và mã HEX NEC 38kHz.
 */
@Composable
private fun CockpitAcousticFeedbackBanner(
    verificationState: BeepDetectionState,
    longBeepNotification: String?,
    isTimerSettingMode: Boolean,
    timerHours: Int,
    lastResult: com.example.ir.TransmitResult?,
    isFlashing: Boolean,
    onRequestMicPermission: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AeroSurfaceContainerLow,
        border = BorderStroke(1.dp, AeroOutlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when {
                    longBeepNotification != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Text(longBeepNotification, fontFamily = SpaceGrotesk, fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }
                    isTimerSettingMode -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = AeroTurboAmber, modifier = Modifier.size(16.dp))
                            Text("Đang chọn: ${timerHours}H (Chờ xác nhận...)", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroTurboAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                    verificationState is BeepDetectionState.Transmitting -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AeroPrimary, modifier = Modifier.size(16.dp))
                            Text("Đang phát IR NEC 38kHz...", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    verificationState is BeepDetectionState.Listening -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = AeroPrimary, modifier = Modifier.size(16.dp))
                            Text("Đang nghe tiếng pip (4020Hz)...", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    verificationState is BeepDetectionState.BeepDetected -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Text("Quạt đã nhận lệnh (1 tiếng pip) ✓", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }
                    verificationState is BeepDetectionState.NoBeepDetected -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable(onClick = onRetry)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AeroTurboAmber, modifier = Modifier.size(16.dp))
                            Text("Chưa nghe pip • Bấm thử lại ↺", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroTurboAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                    verificationState is BeepDetectionState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable(onClick = onRequestMicPermission)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AeroTurboAmber, modifier = Modifier.size(16.dp))
                            Text("Cấp quyền Micro để đồng bộ âm thanh 🎤", fontFamily = SpaceGrotesk, fontSize = 12.sp, color = AeroTurboAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AeroPrimary, modifier = Modifier.size(16.dp))
                            Text("Hệ thống âm học sẵn sàng (Goertzel 4020Hz)", fontFamily = Manrope, fontSize = 12.sp, color = AeroOnSurfaceVariant)
                        }
                    }
                }

                Text(
                    text = if (lastResult != null) lastResult.command.fullHex else "NEC 38kHz",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (isFlashing) AeroPrimary else AeroOnSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Cánh quạt 4 cánh cong khí động học.
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
