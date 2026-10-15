package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ir.FujiFanCommand
import com.example.ir.NecProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FujiHome Remote", appName)
    }

    @Test
    fun `test nec protocol pattern generation`() {
        val pattern = NecProtocol.buildPattern(FujiFanCommand.POWER_OFF.commandByte)
        // 2 (Leader) + 32 * 2 (Bits) + 1 (Stop bit) = 67
        assertEquals(67, pattern.size)
        assertEquals(9000, pattern[0]) // Leader mark
        assertEquals(4500, pattern[1]) // Leader space
        assertEquals(560, pattern[66]) // Stop mark
    }
}
