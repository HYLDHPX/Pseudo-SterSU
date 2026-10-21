package me.weishu.kernelsu.ui.screen.module

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.util.getFileName

private class ModuleZipPickerContract : ActivityResultContracts.GetMultipleContents() {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf("application/zip", "application/x-zip-compressed"),
        )
}

internal suspend fun <T> resolveModuleInstallNames(
    files: List<T>,
    displayName: (T) -> String,
): String = withContext(Dispatchers.IO) {
    files.mapIndexed { index, file -> "\n${index + 1}. ${displayName(file)}" }.joinToString("")
}

/** The same ZIP selection and multi-module confirmation for the module FAB and sidebar. */
@Composable
internal fun rememberModuleZipPicker(onOpenFlash: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.module)
    val prompt = stringResource(R.string.module_install_prompt_with_name, "%s")
    val currentOnOpenFlash by rememberUpdatedState(onOpenFlash)
    val scope = rememberCoroutineScope()
    // In-flight work belongs to this composition; never restore a stale busy flag.
    var pickerOpen by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { currentOnOpenFlash(selectedUris) })
    val contract = remember { ModuleZipPickerContract() }
    val launcher = rememberLauncherForActivityResult(contract) { uris ->
        when (uris.size) {
            0 -> pickerOpen = false
            1 -> {
                pickerOpen = false
                currentOnOpenFlash(uris)
            }
            else -> {
                scope.launch {
                    try {
                        // Document providers may be slow: never query their metadata on the UI thread.
                        val names = resolveModuleInstallNames(uris) { uri ->
                            uri.getFileName(context) ?: uri.lastPathSegment.orEmpty()
                        }
                        selectedUris = uris
                        confirmDialog.showConfirm(title = title, content = prompt.format(names))
                    } finally {
                        pickerOpen = false
                    }
                }
            }
        }
    }
    return remember(launcher, context) {
        {
            if (!pickerOpen) {
                pickerOpen = true
                try {
                    // Use the same immediate GET_CONTENT path as the original module install FAB.
                    launcher.launch("application/zip")
                } catch (_: ActivityNotFoundException) {
                    pickerOpen = false
                    Toast.makeText(context, R.string.module_picker_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
