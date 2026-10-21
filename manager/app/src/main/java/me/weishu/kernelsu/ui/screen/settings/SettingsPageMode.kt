package me.weishu.kernelsu.ui.screen.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.AlertDialog
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight

private const val SETTINGS_PAGE_MODE_KEY = "settings_page_mode"

enum class SettingsPageMode(val value: String) {
    Categories("categories"),
    Overview("overview");

    companion object {
        fun fromValue(value: String?): SettingsPageMode {
            return entries.firstOrNull { it.value == value } ?: Categories
        }
    }
}

fun readSettingsPageMode(context: Context): SettingsPageMode {
    val preferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val value = preferences.getString(SETTINGS_PAGE_MODE_KEY, null)
    val mode = SettingsPageMode.fromValue(value)
    // Persist the fallback so old collapsed/unknown values do not return.
    if (value != null && value != mode.value) {
        preferences.edit { putString(SETTINGS_PAGE_MODE_KEY, mode.value) }
    }
    return mode
}

fun setSettingsPageMode(context: Context, mode: SettingsPageMode) {
    context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
        putString(SETTINGS_PAGE_MODE_KEY, mode.value)
    }
}

@get:StringRes
internal val SettingsPageMode.labelRes: Int
    get() = when (this) {
        SettingsPageMode.Categories -> R.string.settings_page_mode_categories
        SettingsPageMode.Overview -> R.string.settings_page_mode_overview
    }

@Composable
internal fun SettingsPageModeCard(
    currentMode: SettingsPageMode,
    onModeChange: (SettingsPageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    SettingsHomeCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { showDialog = true }
                .heightIn(min = 76.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Dashboard, null, Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_page_mode_select), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(currentMode.labelRes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
        }
    }
    if (showDialog) {
        SettingsPageModeDialog(
            currentMode = currentMode,
            onModeChange = onModeChange,
            onDismissRequest = { showDialog = false },
        )
    }
}

@Composable
internal fun SettingsPageModeDialog(
    currentMode: SettingsPageMode,
    onModeChange: (SettingsPageMode) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.settings_page_mode_select)) },
        text = {
            Column(
                Modifier.heightIn(max = adaptiveDialogContentMaxHeight())
                    .verticalScroll(rememberScrollState()).selectableGroup(),
            ) {
                SettingsPageMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton) {
                                onDismissRequest()
                                if (!selected) onModeChange(mode)
                            }.heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (mode == SettingsPageMode.Categories) Icons.Rounded.AccountTree else Icons.Rounded.Dashboard,
                            null, Modifier.size(24.dp),
                        )
                        Text(stringResource(mode.labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
