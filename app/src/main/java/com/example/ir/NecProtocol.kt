package com.example.ir

import com.example.R

enum class ButtonType {
    DANGER,
    PRIMARY,
    ACCENT,
    SECONDARY
}

/**
 * Bảng đặc tả mã lệnh quạt FujiHome theo chuẩn NEC Protocol
 * Tần số sóng mang: 38,000 Hz
 * Mã địa chỉ (Address): 0x00 (~Addr: 0xFF)
 */
enum class FujiFanCommand(
    val commandByte: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val fullHex: String,
    val buttonType: ButtonType
) {
    POWER_OFF(
        commandByte = 0x40,
        titleRes = R.string.btn_power_off,
        descriptionRes = R.string.desc_power_off,
        fullHex = "0x00FF40BF",
        buttonType = ButtonType.DANGER
    ),
    SPEED_PLUS(
        commandByte = 0x46,
        titleRes = R.string.btn_speed_plus,
        descriptionRes = R.string.desc_speed_plus,
        fullHex = "0x00FF46B9",
        buttonType = ButtonType.PRIMARY
    ),
    SPEED_MINUS(
        commandByte = 0x0C,
        titleRes = R.string.btn_speed_minus,
        descriptionRes = R.string.desc_speed_minus,
        fullHex = "0x00FF0CF3",
        buttonType = ButtonType.PRIMARY
    ),
    TURBO(
        commandByte = 0x45,
        titleRes = R.string.btn_turbo,
        descriptionRes = R.string.desc_turbo,
        fullHex = "0x00FF45BA",
        buttonType = ButtonType.ACCENT
    ),
    SLEEP(
        commandByte = 0x47,
        titleRes = R.string.btn_sleep,
        descriptionRes = R.string.desc_sleep,
        fullHex = "0x00FF47B8",
        buttonType = ButtonType.SECONDARY
    ),
    HORIZONTAL(
        commandByte = 0x16,
        titleRes = R.string.btn_horizontal,
        descriptionRes = R.string.desc_horizontal,
        fullHex = "0x00FF16E9",
        buttonType = ButtonType.SECONDARY
    ),
    VERTICAL(
        commandByte = 0x4A,
        titleRes = R.string.btn_vertical,
        descriptionRes = R.string.desc_vertical,
        fullHex = "0x00FF4AB5",
        buttonType = ButtonType.SECONDARY
    ),
    TIMER(
        commandByte = 0x43,
        titleRes = R.string.btn_timer,
        descriptionRes = R.string.desc_timer,
        fullHex = "0x00FF43BC",
        buttonType = ButtonType.SECONDARY
    )
}

object NecProtocol {
    const val CARRIER_FREQUENCY_HZ = 38000
    const val ADDRESS_BYTE = 0x00

    private const val LEADER_MARK_US = 9000
    private const val LEADER_SPACE_US = 4500
    private const val BIT_MARK_US = 560
    private const val BIT_0_SPACE_US = 560
    private const val BIT_1_SPACE_US = 1690
    private const val STOP_MARK_US = 560

    /**
     * Xây dựng mảng xung thời gian thực (Microseconds array) chuẩn NEC 38kHz.
     * Cấu trúc: [Leader 9ms/4.5ms] + [32-bit Data (Addr + ~Addr + Cmd + ~Cmd)] + [Stop bit 560µs]
     * Tổng số phần tử: 2 + (32 * 2) + 1 = 67
     */
    fun buildPattern(commandByte: Int, addressByte: Int = ADDRESS_BYTE): IntArray {
        val pattern = ArrayList<Int>(67)

        // 1. Leader code
        pattern.add(LEADER_MARK_US)
        pattern.add(LEADER_SPACE_US)

        val addr = addressByte and 0xFF
        val addrInv = (addr.inv()) and 0xFF
        val cmd = commandByte and 0xFF
        val cmdInv = (cmd.inv()) and 0xFF

        val dataBytes = intArrayOf(addr, addrInv, cmd, cmdInv)

        // 2. 32-bit Data transmitted LSB-first per byte
        for (b in dataBytes) {
            for (i in 0 until 8) {
                val bit = (b shr i) and 1
                pattern.add(BIT_MARK_US)
                if (bit == 1) {
                    pattern.add(BIT_1_SPACE_US)
                } else {
                    pattern.add(BIT_0_SPACE_US)
                }
            }
        }

        // 3. Stop bit
        pattern.add(STOP_MARK_US)

        return pattern.toIntArray()
    }
}
