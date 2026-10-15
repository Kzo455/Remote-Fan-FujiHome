package com.example.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

data class TransmitResult(
    val command: FujiFanCommand,
    val isHardwareTransmitted: Boolean,
    val patternLength: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class IrTransmitter(private val context: Context) {

    private val irManager: ConsumerIrManager? = try {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    } catch (e: Exception) {
        Log.w(TAG, "ConsumerIrManager not available", e)
        null
    }

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        Log.w(TAG, "Vibrator service not available", e)
        null
    }

    val hasHardwareIrEmitter: Boolean
        get() = try {
            irManager?.hasIrEmitter() == true
        } catch (e: Exception) {
            false
        }

    fun transmit(command: FujiFanCommand): TransmitResult {
        // Trigger Haptic Feedback
        triggerHaptic()

        val pattern = NecProtocol.buildPattern(command.commandByte, NecProtocol.ADDRESS_BYTE)
        var hardwareSuccess = false

        if (hasHardwareIrEmitter && irManager != null) {
            try {
                irManager.transmit(NecProtocol.CARRIER_FREQUENCY_HZ, pattern)
                hardwareSuccess = true
                Log.d(TAG, "Transmitted IR command: ${command.name} (${command.fullHex}) with 38kHz")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transmit IR signal via ConsumerIrManager", e)
                hardwareSuccess = false
            }
        } else {
            Log.d(TAG, "Simulation mode: emitted command ${command.name} (${command.fullHex})")
        }

        return TransmitResult(
            command = command,
            isHardwareTransmitted = hardwareSuccess,
            patternLength = pattern.size
        )
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic vibration failed", e)
        }
    }

    companion object {
        private const val TAG = "FujiHomeIrTransmitter"
    }
}
