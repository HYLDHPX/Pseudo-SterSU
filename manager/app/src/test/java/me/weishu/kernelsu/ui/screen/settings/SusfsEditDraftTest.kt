package me.weishu.kernelsu.ui.screen.settings

import me.weishu.kernelsu.ui.util.SusfsCapabilities
import me.weishu.kernelsu.ui.util.SusfsPathConfigState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SusfsEditDraftTest {
    @Test
    fun restoredDraftKeepsCurrentRuntimeMetadata() {
        val runtime = SusfsPathConfigState(
            available = true,
            toolPath = "/data/adb/ksu/bin/ksud",
            paths = listOf("/system/current"),
            capabilities = SusfsCapabilities(version = "2"),
        )
        val saved = SusfsPathConfigState(
            paths = listOf("/system/draft"),
            logging = true,
        )

        val restored = restoreSusfsEditableDraft(runtime, saved)

        assertEquals(saved.paths, restored.paths)
        assertTrue(restored.logging)
        assertEquals(runtime.toolPath, restored.toolPath)
        assertEquals(runtime.capabilities, restored.capabilities)
        assertTrue(restored.available)
    }
}
