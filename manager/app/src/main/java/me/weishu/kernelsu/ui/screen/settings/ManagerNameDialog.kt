package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.theme.UiPassthroughTheme
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight

private const val MAX_CUSTOM_NAME_LENGTH = 40

@Composable
fun ManagerNameDialog(
    show: Boolean,
    initialName: String,
    initialSyncLkmCard: Boolean,
    initialSyncSettingsHeader: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String, Boolean, Boolean) -> Unit,
) {
    if (!show) return
    var syncLkmCard by rememberSaveable(initialSyncLkmCard) { mutableStateOf(initialSyncLkmCard) }
    var syncSettingsHeader by rememberSaveable(initialSyncSettingsHeader) { mutableStateOf(initialSyncSettingsHeader) }
    NameEditDialog(
        initialName = initialName,
        titleRes = R.string.settings_manager_name,
        summaryRes = R.string.settings_manager_name_dialog_summary,
        inputLabelRes = R.string.settings_manager_name_input_label,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(it, syncLkmCard, syncSettingsHeader) },
        additionalContent = {
            ManagerNameSyncOption(
                label = stringResource(R.string.settings_manager_name_sync_lkm_card),
                checked = syncLkmCard,
                onCheckedChange = { syncLkmCard = it },
            )
            ManagerNameSyncOption(
                label = stringResource(R.string.settings_manager_name_sync_settings_header),
                checked = syncSettingsHeader,
                onCheckedChange = { syncSettingsHeader = it },
            )
        },
    )
}

@Composable
private fun ManagerNameSyncOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        ExpressiveSwitch(checked = checked, onCheckedChange = null)
    }
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
    @StringRes inputLabelRes: Int? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    additionalContent: @Composable ColumnScope.() -> Unit = {},
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    UiPassthroughTheme(UiPassthroughSettings()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(titleRes)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = adaptiveDialogContentMaxHeight())
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(summaryRes))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        singleLine = true,
                        onValueChange = { name = it.take(MAX_CUSTOM_NAME_LENGTH) },
                        label = if (inputLabelRes != null) {
                            { Text(stringResource(inputLabelRes)) }
                        } else null,
                    )
                    additionalContent()
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
}
