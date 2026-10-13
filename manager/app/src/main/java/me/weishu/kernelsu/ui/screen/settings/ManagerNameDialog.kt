package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R

private const val MAX_CUSTOM_NAME_LENGTH = 40

@Composable
fun ManagerNameDialog(
    show: Boolean,
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return
    NameEditDialog(
        initialName = initialName,
        titleRes = R.string.settings_manager_name,
        summaryRes = R.string.settings_manager_name_dialog_summary,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}

@Composable
fun HomeTitleDialog(
    show: Boolean,
    initialTitle: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return
    NameEditDialog(
        initialName = initialTitle,
        titleRes = R.string.settings_home_title,
        summaryRes = R.string.settings_home_title_dialog_summary,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}

@Composable
private fun NameEditDialog(
    initialName: String,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(summaryRes))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    singleLine = true,
                    onValueChange = { name = it.take(MAX_CUSTOM_NAME_LENGTH) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name.trim())
                onDismissRequest()
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
