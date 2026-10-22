package me.weishu.kernelsu.ui.screen.module

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.overflow.CompactOverflowMenuItem
import me.weishu.kernelsu.ui.component.overflow.FrostedOverflowMenu
import me.weishu.kernelsu.ui.component.overflow.OverflowMenuWallpaperState

@Composable
internal fun ModuleOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    uiState: ModuleUiState,
    actions: ModuleActions,
    wallpaper: OverflowMenuWallpaperState,
    onToggleFeedback: () -> Unit = {},
) {
    FrostedOverflowMenu(expanded, onDismissRequest, wallpaper) {
        listOf(
            Triple(R.string.module_sort_action_first, uiState.sortActionFirst, actions.onToggleSortActionFirst),
            Triple(R.string.module_sort_enabled_first, uiState.sortEnabledFirst, actions.onToggleSortEnabledFirst),
        ).forEach { (label, checked, toggle) ->
            CompactOverflowMenuItem(
                text = { Text(stringResource(label), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(checked)
                },
                trailingIcon = {
                    if (checked) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                onClick = {
                    onToggleFeedback()
                    toggle()
                    onDismissRequest()
                },
            )
        }
    }
}
