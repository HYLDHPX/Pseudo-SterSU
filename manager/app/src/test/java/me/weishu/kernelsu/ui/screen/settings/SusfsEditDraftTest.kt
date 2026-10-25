package me.weishu.kernelsu.ui.screen.settings

import me.weishu.kernelsu.ui.util.SusfsCapabilities
import me.weishu.kernelsu.ui.util.SusfsPathConfigState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class SusfsEditDraftTest {
    @Test
    fun failedIoPreservesOriginalError() = runBlocking {
        val error = IOException("storage unavailable")
        val result = runSusfsOperation<Unit> { throw error }
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun cancelledOperationIsNotAnImportFailure() = runBlocking {
        val cancellation = CancellationException("page closed")
        val caught = try {
            runSusfsOperation<Unit> { throw cancellation }
            null
        } catch (error: CancellationException) {
            error
        }
        assertEquals(cancellation, caught)
    }

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
