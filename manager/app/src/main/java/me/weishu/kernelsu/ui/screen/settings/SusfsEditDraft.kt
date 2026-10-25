package me.weishu.kernelsu.ui.screen.settings

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.SusfsPathConfigState
import me.weishu.kernelsu.ui.util.buildSusfsBackupJson
import me.weishu.kernelsu.ui.util.parseSusfsBackupJson

/** Keep cancellation distinct from an import, export, or configuration failure. */
internal suspend fun <T> runSusfsOperation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

internal class SusfsEditViewModel : ViewModel() {
    val runtime = mutableStateOf(SusfsPathConfigState())
    val draft = mutableStateOf(SusfsPathConfigState())
    val baseline = mutableStateOf<SusfsPathConfigState?>(null)
    var initialized = false
}

/** A draft is separate from the applied SUSFS recovery snapshot and is never applied automatically. */
internal class SusfsEditDraftStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "susfs-edit-draft-v1.json"))

    suspend fun load(): SusfsPathConfigState? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val bytes = file.readFully()
                if (bytes.size > MAX_DRAFT_BYTES) return@runCatching null
                val parsed = parseSusfsBackupJson(bytes.toString(Charsets.UTF_8))
                parsed.config.takeIf { parsed.error.isBlank() && parsed.warnings.isEmpty() }
            }.getOrNull()
        }
    }

    suspend fun save(config: SusfsPathConfigState, revision: Long): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (revision < latestRevision) return@withLock true
            runCatching {
                val bytes = buildSusfsBackupJson(config).toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_DRAFT_BYTES) { "SUSFS draft is too large" }
                val stream = file.startWrite()
                try {
                    stream.write(bytes)
                    file.finishWrite(stream)
                    latestRevision = revision
                } catch (error: Exception) {
                    file.failWrite(stream)
                    throw error
                }
            }.isSuccess
        }
    }

    suspend fun clear() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            latestRevision = System.nanoTime()
            file.delete()
        }
    }

    private companion object {
        const val MAX_DRAFT_BYTES = 512 * 1024
        val mutex = Mutex()
        var latestRevision = Long.MIN_VALUE
    }
}

internal fun restoreSusfsEditableDraft(
    runtime: SusfsPathConfigState,
    saved: SusfsPathConfigState,
): SusfsPathConfigState = runtime.copy(
    paths = saved.paths,
    loopPaths = saved.loopPaths,
    susMaps = saved.susMaps,
    openRedirects = saved.openRedirects,
    kstatEntries = saved.kstatEntries,
    enabled = saved.enabled,
    logging = saved.logging,
    avcLogSpoofing = saved.avcLogSpoofing,
    hideSusMntsForNonSuProcs = saved.hideSusMntsForNonSuProcs,
    unameRelease = saved.unameRelease,
    unameVersion = saved.unameVersion,
    cmdlineOrBootconfig = saved.cmdlineOrBootconfig,
)
