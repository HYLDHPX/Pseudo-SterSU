package me.weishu.kernelsu.ui.screen.navigationicon

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.CustomNavigationIconImage
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorAction
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorHeading
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorMediaActions
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorPage
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorTabs
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorTargetPicker
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorWorkspace
import me.weishu.kernelsu.ui.screen.settings.MediaFileInfoSummary
import me.weishu.kernelsu.ui.screen.settings.SettingsNavigationIconPreviewDialog
import me.weishu.kernelsu.ui.screen.settings.SettingsWallpaperCropDialog
import me.weishu.kernelsu.ui.screen.settings.rememberMediaFileInfo
import me.weishu.kernelsu.ui.util.CustomNavigationIconSet
import me.weishu.kernelsu.ui.util.CustomNavigationIconSlot
import me.weishu.kernelsu.ui.util.CustomNavigationIconState
import me.weishu.kernelsu.ui.util.DEFAULT_CUSTOM_NAVIGATION_ICON_CROP
import me.weishu.kernelsu.ui.util.persistCustomImageReference
import me.weishu.kernelsu.ui.util.takePersistableImageReadPermission
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

@Composable
fun NavigationIconScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(CustomNavigationIconSlot.Home.id) }
    var pickTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var cropTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var previewTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val slot = navigationIconSlotFromId(selectedId) ?: CustomNavigationIconSlot.Home
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pickedSlot = pickTarget?.let(::navigationIconSlotFromId)
            ?: return@rememberLauncherForActivityResult
        pickTarget = null
        uri ?: return@rememberLauncherForActivityResult
        val uriString = persistCustomImageReference(context, uri, pickedSlot.uriKey)
            ?: uri.toString().also { takePersistableImageReadPermission(context, uri) }
        viewModel.setCustomNavigationIcon(pickedSlot, uriString)
        selectedId = pickedSlot.id
        cropTarget = pickedSlot.id
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val onBack = dropUnlessResumed { navigator.pop() }
    val actions = NavigationIconActions(
        onPick = {
            pickTarget = it.id
            imageLauncher.launch(arrayOf("image/*"))
        },
        onCrop = { cropTarget = it.id },
        onPreview = { previewTarget = it.id },
        onClear = {
            viewModel.clearCustomNavigationIcon(it)
            if (cropTarget == it.id) cropTarget = null
            if (previewTarget == it.id) previewTarget = null
        },
    )

    AppearanceEditorPage(stringResource(R.string.settings_navigation_icons), onBack) { modifier ->
        key(slot.id) {
            NavigationIconEditor(
                slot = slot,
                icons = uiState.customNavigationIcons,
                actions = actions,
                modifier = modifier,
                onSelect = { selectedId = it.id },
                onValueChange = { viewModel.setCustomNavigationIconPresentation(slot, it) },
            )
        }

        cropTarget?.let(::navigationIconSlotFromId)?.let { cropSlot ->
            val state = uiState.customNavigationIcons[cropSlot]
            SettingsWallpaperCropDialog(
                show = state.hasSelected,
                uriString = state.uriString,
                crop = state.crop,
                onCropChange = {
                    viewModel.setCustomNavigationIconCrop(cropSlot, it)
                    previewTarget = cropSlot.id
                },
                onDismissRequest = { cropTarget = null },
                title = stringResource(cropSlot.cropTitleRes),
                emptyText = stringResource(R.string.settings_navigation_icon_not_selected),
                editorAspectRatio = 1f,
                cropAspectRatio = 1f,
                defaultCrop = DEFAULT_CUSTOM_NAVIGATION_ICON_CROP,
            )
        }
        previewTarget?.let(::navigationIconSlotFromId)?.let { previewSlot ->
            SettingsNavigationIconPreviewDialog(
                show = true,
                slot = previewSlot,
                icons = uiState.customNavigationIcons,
                onDismissRequest = { previewTarget = null },
            )
        }
    }
}

@Composable
private fun NavigationIconEditor(
    slot: CustomNavigationIconSlot,
    icons: CustomNavigationIconSet,
    actions: NavigationIconActions,
    modifier: Modifier,
    onSelect: (CustomNavigationIconSlot) -> Unit,
    onValueChange: (CustomNavigationIconState) -> Unit,
) {
    val state = icons[slot]
    val title = stringResource(slot.titleRes)
    var section by rememberSaveable { mutableStateOf(0) }
    AppearanceEditorWorkspace(
        modifier = modifier,
        selector = {
            AppearanceEditorTargetPicker(
                titleRes = R.string.appearance_editor_choose_navigation,
                labels = CustomNavigationIconSlot.entries.map { stringResource(it.labelRes) },
                selectedIndex = CustomNavigationIconSlot.entries.indexOf(slot),
                onSelect = { onSelect(CustomNavigationIconSlot.entries[it]) },
            )
        },
        preview = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceEditorHeading(title, stringResource(R.string.appearance_editor_navigation_intro))
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                        key(slot.id, state.uriString, state.crop) {
                            CustomNavigationIconImage(
                                state = state,
                                contentDescription = state.displayLabel(stringResource(slot.labelRes)),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = if (LocalUiMode.current == UiMode.Material) slot.materialFallbackIcon
                                        else slot.miuixFallbackIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                    Text(state.displayLabel(stringResource(slot.labelRes)),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.height(20.dp))
                }
                Text(stringResource(if (state.hasSelected) R.string.settings_navigation_icon_selected_summary
                    else R.string.appearance_editor_default_icon),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MediaFileInfoSummary(rememberMediaFileInfo(state.uriString))
                AppearanceEditorMediaActions(
                    title = title,
                    hasMedia = state.hasSelected,
                    onPickImage = { actions.onPick(slot) },
                    onCrop = { actions.onCrop(slot) },
                    onPreview = { actions.onPreview(slot) },
                    onClear = { actions.onClear(slot) },
                    clearSummaryRes = R.string.appearance_editor_clear_icon_summary,
                )
                if (!state.hasSelected) {
                    AppearanceEditorAction(stringResource(R.string.settings_navigation_icon_preview_action),
                        Icons.Rounded.Visibility, { actions.onPreview(slot) })
                }
                Text(stringResource(R.string.settings_navigation_icons_restart_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        controls = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceEditorTabs(
                    listOf(stringResource(R.string.appearance_editor_icon_layout), stringResource(R.string.appearance_editor_icon_appearance)),
                    section, { section = it },
                )
                NavigationIconAdjustmentControls(state, section, onValueChange)
                Text(stringResource(R.string.appearance_editor_save_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private val CustomNavigationIconSlot.materialFallbackIcon: ImageVector
    get() = when (this) {
        CustomNavigationIconSlot.Home -> Icons.Filled.Home
        CustomNavigationIconSlot.Kpm -> Icons.Filled.Memory
        CustomNavigationIconSlot.Superuser -> Icons.Filled.Shield
        CustomNavigationIconSlot.Module -> Icons.Filled.Extension
        CustomNavigationIconSlot.Settings -> Icons.Filled.Settings
    }

private val CustomNavigationIconSlot.miuixFallbackIcon: ImageVector
    get() = when (this) {
        CustomNavigationIconSlot.Home -> Icons.Rounded.Cottage
        CustomNavigationIconSlot.Kpm -> Icons.Rounded.Memory
        CustomNavigationIconSlot.Superuser -> Icons.Rounded.Security
        CustomNavigationIconSlot.Module -> Icons.Rounded.Extension
        CustomNavigationIconSlot.Settings -> Icons.Rounded.Settings
    }

private fun navigationIconSlotFromId(id: String): CustomNavigationIconSlot? =
    CustomNavigationIconSlot.entries.firstOrNull { it.id == id }

private data class NavigationIconActions(
    val onPick: (CustomNavigationIconSlot) -> Unit,
    val onCrop: (CustomNavigationIconSlot) -> Unit,
    val onPreview: (CustomNavigationIconSlot) -> Unit,
    val onClear: (CustomNavigationIconSlot) -> Unit,
)
