package me.weishu.kernelsu

import org.junit.Assert.assertEquals
import org.junit.Test

class KernelsTest {
    @Test
    fun parseKernelVersionRejectsOutOfRangeComponents() {
        assertEquals(KernelVersion(6, 12, 3), parseKernelVersion("6.12.3-android"))
        assertEquals(KernelVersion(-1, -1, -1), parseKernelVersion("999999999999.12.3"))
        assertEquals(KernelVersion(-1, -1, -1), parseKernelVersion("not-a-kernel-version"))
    }
}
