package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.stealth.StealthModeStore
import me.weishu.kernelsu.stealth.StealthCodeValidation

@Composable
internal fun StealthModeCodeDialog(
    show: Boolean,
    currentCode: String,
    enableAfterSave: Boolean,
    busy: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return
    var draft by rememberSaveable(show, currentCode) { mutableStateOf(currentCode) }
    val validation = StealthModeStore.validateCode(draft)
    val valid = validation == StealthCodeValidation.Valid
    AlertDialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        title = {
            Text(
                stringResource(
                    if (enableAfterSave) R.string.stealth_mode_enable_title
                    else R.string.stealth_mode_code_title
                )
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.stealth_mode_dialog_summary))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = !busy,
                    singleLine = false,
                    maxLines = 4,
                    label = { Text(stringResource(R.string.stealth_mode_code_title)) },
                    isError = draft.isNotBlank() && !valid,
                    supportingText = {
                        Text(
                            stringResource(
                                when (validation) {
                                    StealthCodeValidation.Valid -> R.string.stealth_mode_code_format
                                    StealthCodeValidation.Invalid -> R.string.stealth_mode_code_invalid
                                    StealthCodeValidation.TooLong -> R.string.stealth_mode_code_too_long
                                }
                            )
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = valid && !busy,
            ) {
                Text(
                    stringResource(
                        if (enableAfterSave) R.string.stealth_mode_enable_action
                        else R.string.stealth_mode_save_action
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !busy) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
