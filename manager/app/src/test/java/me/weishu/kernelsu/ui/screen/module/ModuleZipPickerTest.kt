package me.weishu.kernelsu.ui.screen.module

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleZipPickerTest {
    @Test
    fun namesPreserveSelectionOrderAndNumbering() = runBlocking {
        assertEquals(
            "\n1. first.zip\n2. second.zip",
            resolveModuleInstallNames(listOf("first.zip", "second.zip")) { it },
        )
        assertEquals("", resolveModuleInstallNames(emptyList<String>()) { error("No query expected") })
    }

    @Test
    fun slowDocumentMetadataDoesNotBlockTheCallingThread() = runBlocking {
        val caller = Thread.currentThread()
        val started = CompletableDeferred<Thread>()
        val release = CountDownLatch(1)
        val names = async {
            resolveModuleInstallNames(listOf("module.zip")) {
                started.complete(Thread.currentThread())
                check(release.await(5, TimeUnit.SECONDS)) { "Caller blocked while reading document metadata" }
                it
            }
        }
        try {
            val worker = withTimeout(5_000) { started.await() }
            assertTrue(worker !== caller)
            assertFalse(names.isCompleted)
        } finally {
            release.countDown()
        }
        assertEquals("\n1. module.zip", names.await())
    }

    @Test
    fun launchIsDirectAndRepeatedTapsAreGuardedUntilResultOrFailure() {
        val source = File("src/main/java/me/weishu/kernelsu/ui/screen/module/ModuleZipPicker.kt").readText()
        assertTrue(source.contains("ActivityResultContracts.GetMultipleContents()"))
        assertTrue(source.contains("Intent.EXTRA_MIME_TYPES"))
        assertTrue(source.contains("application/x-zip-compressed"))
        assertFalse(source.contains("ActivityResultContracts.OpenMultipleDocuments()"))
        assertTrue(source.contains("var pickerOpen by remember { mutableStateOf(false) }"))
        assertFalse(source.contains("rememberSaveable"))
        val click = source.substringAfter("return remember(launcher, context)")
        assertTrue(click.contains("if (!pickerOpen)"))
        assertTrue(click.indexOf("pickerOpen = true") < click.indexOf("launcher.launch("))
        assertTrue(click.contains("launcher.launch(\"application/zip\")"))
        assertFalse(click.contains("scope.launch"))
        assertFalse(click.contains("getFileName"))
        assertTrue(click.substringAfter("catch (_: ActivityNotFoundException)").contains("pickerOpen = false"))
        assertTrue(source.contains("0 -> pickerOpen = false"))
        assertTrue(source.contains("finally {\n                        pickerOpen = false"))
        assertTrue(source.contains("confirmDialog.showConfirm"))
    }
}
