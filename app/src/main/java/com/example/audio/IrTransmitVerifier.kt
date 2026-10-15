package com.example.audio

import android.content.Context
import com.example.ir.FujiFanCommand
import com.example.ir.IrTransmitter
import com.example.ir.TransmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

interface IrVerificationSession {
    suspend fun transmitAndVerify(command: FujiFanCommand): Pair<TransmitResult, BeepDetectionState>
}

/**
 * Điều phối phát tín hiệu hồng ngoại và lắng nghe phản hồi âm thanh (Beep) từ quạt.
 * Hỗ trợ cả chế độ Single-shot và Streaming Session (khi người dùng bấm nhiều phím liên tiếp).
 */
class IrTransmitVerifier(
    context: Context,
    private val config: BeepConfig = BeepConfig()
) {
    val irTransmitter = IrTransmitter(context)
    val beepDetector = BeepDetector(context, config)

    val verificationState: StateFlow<BeepDetectionState> = beepDetector.state

    fun hasRecordPermission(): Boolean = beepDetector.hasRecordPermission()

    /**
     * Chạy phiên xác thực liên tục (Session) cho hàng đợi lệnh liên tiếp.
     * AudioRecord được giữ mở xuyên suốt các lệnh, loại bỏ độ trễ và mất tiếng pip khi bấm nhanh.
     */
    suspend fun <R> runVerificationSession(
        block: suspend (session: IrVerificationSession) -> R
    ): R = withContext(Dispatchers.IO) {
        beepDetector.runSession { beepSession ->
            val irSession = object : IrVerificationSession {
                override suspend fun transmitAndVerify(command: FujiFanCommand): Pair<TransmitResult, BeepDetectionState> {
                    var transmitResult: TransmitResult? = null
                    val detectResult = beepSession.verifyCommand {
                        val res = irTransmitter.transmit(command)
                        transmitResult = res
                        true
                    }
                    val finalTransmitResult = transmitResult ?: irTransmitter.transmit(command)
                    return Pair(finalTransmitResult, detectResult)
                }
            }
            block(irSession)
        }
    }

    /**
     * Phương thức xác thực đơn lẻ cho một lệnh.
     */
    suspend fun transmitAndVerify(command: FujiFanCommand): Pair<TransmitResult, BeepDetectionState> =
        withContext(Dispatchers.IO) {
            // Nếu chưa có quyền mic, chỉ phát IR
            if (!beepDetector.hasRecordPermission()) {
                val transmitResult = irTransmitter.transmit(command)
                val state = BeepDetectionState.Error("Cần quyền Micro để nghe tiếng pip")
                return@withContext Pair(transmitResult, state)
            }

            var transmitResult: TransmitResult? = null

            val detectResult = beepDetector.listenForBeep {
                val res = irTransmitter.transmit(command)
                transmitResult = res
                true
            }

            val finalTransmitResult = transmitResult ?: irTransmitter.transmit(command)
            return@withContext Pair(finalTransmitResult, detectResult)
        }
}
