package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BootPatchModeTest {
    @Test
    fun normalPatchAddsNoSpecialArguments() {
        assertEquals("", BootPatchMode.Normal.cliArguments())
    }

    @Test(expected = IllegalStateException::class)
    fun nativeKpmCannotUseTheLkmCommand() {
        BootPatchMode.NativeKpm.cliArguments()
    }
}
