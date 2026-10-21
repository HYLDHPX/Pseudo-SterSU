package me.weishu.kernelsu.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.rememberCustomVideoFrameBitmap
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.util.CUSTOM_WALLPAPER_URI_KEY
import me.weishu.kernelsu.ui.util.CUSTOM_VIDEO_BACKGROUND_FRAME_RATE_OPTIONS
import me.weishu.kernelsu.ui.util.CustomBackgroundState
import me.weishu.kernelsu.ui.util.CustomPageBackgroundTarget
import me.weishu.kernelsu.ui.util.persistCustomImageReference
import me.weishu.kernelsu.ui.util.takePersistableImageReadPermission
import me.weishu.kernelsu.ui.util.takePersistableVideoBackgroundReadPermission
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

internal const val GLOBAL_BACKGROUND_SCOPE_KEY = "global"

@Composable
fun BackgroundSettingsScreen() {
    val navigator = LocalNavigator.current
    val onBack = dropUnlessResumed { navigator.pop() }
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImageScopeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVideoScopeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cropScopeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var previewScopeKey by rememberSaveable { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val scopeKey = pendingImageScopeKey ?: return@rememberLauncherForActivityResult
        pendingImageScopeKey = null
        uri ?: return@rememberLauncherForActivityResult
        val storageKey = wallpaperStorageKey(scopeKey) ?: return@rememberLauncherForActivityResult
        val uriString = persistCustomImageReference(context, uri, storageKey)
            ?: uri.toString().also { takePersistableImageReadPermission(context, uri) }
        if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
            viewModel.setCustomWallpaperUri(uriString)
        } else {
            val target = pageTargetFromScopeKey(scopeKey) ?: return@rememberLauncherForActivityResult
            viewModel.setCustomPageBackgroundWallpaper(target, uriString)
        }
        cropScopeKey = scopeKey
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val scopeKey = pendingVideoScopeKey ?: return@rememberLauncherForActivityResult
        pendingVideoScopeKey = null
        uri ?: return@rememberLauncherForActivityResult
        takePersistableVideoBackgroundReadPermission(context, uri)
        if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
            viewModel.setCustomVideoBackgroundUri(uri.toString())
        } else {
            val target = pageTargetFromScopeKey(scopeKey) ?: return@rememberLauncherForActivityResult
            viewModel.setCustomPageBackgroundVideo(target, uri.toString())
        }
        cropScopeKey = scopeKey
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = remember(viewModel) {
        BackgroundSettingsActions(
            onPickImage = { scopeKey ->
                pendingImageScopeKey = scopeKey
                imageLauncher.launch(arrayOf("image/*"))
            },
            onPickVideo = { scopeKey ->
                pendingVideoScopeKey = scopeKey
                videoLauncher.launch(arrayOf("video/*"))
            },
            onCrop = { scopeKey -> cropScopeKey = scopeKey },
            onPreview = { scopeKey -> previewScopeKey = scopeKey },
            onClear = { scopeKey ->
                if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
                    viewModel.clearCustomWallpaper()
                    viewModel.clearCustomVideoBackground()
                } else {
                    pageTargetFromScopeKey(scopeKey)?.let(viewModel::clearCustomPageBackground)
                }
            },
            onOpacityChange = { scopeKey, opacity ->
                if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
                    viewModel.setCustomWallpaperOpacity(opacity)
                } else {
                    pageTargetFromScopeKey(scopeKey)?.let { target ->
                        viewModel.setCustomPageBackgroundOpacity(target, opacity)
                    }
                }
            },
            onDurationChange = { scopeKey, seconds ->
                if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
                    viewModel.setCustomVideoBackgroundDurationSeconds(seconds)
                } else {
                    pageTargetFromScopeKey(scopeKey)?.let { target ->
                        viewModel.setCustomPageBackgroundVideoDurationSeconds(target, seconds)
                    }
                }
            },
            onFrameRateChange = viewModel::setCustomVideoBackgroundFrameRate,
            onVisualSettingsChange = { scopeKey, settings ->
                if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
                    viewModel.setCustomWallpaperVisualSettings(settings)
                } else {
                    pageTargetFromScopeKey(scopeKey)?.let { target ->
                        viewModel.setCustomPageBackgroundVisualSettings(target, settings)
                    }
                }
            },
            onCropChange = { scopeKey, crop ->
                if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
                    viewModel.setCustomWallpaperCrop(crop)
                } else {
                    pageTargetFromScopeKey(scopeKey)?.let { target ->
                        viewModel.setCustomPageBackgroundCrop(target, crop)
                    }
                }
                previewScopeKey = scopeKey
            },
            onScrollFollowEnabledChange = viewModel::setBackgroundScrollFollowEnabled,
        )
    }

    AppearanceEditorPage(stringResource(R.string.background_editor_title), onBack) { modifier ->
        BackgroundSettingsContent(uiState = uiState, actions = actions, modifier = modifier)

        val cropState = cropScopeKey?.let(uiState::backgroundStateForScope)
        val cropVideoFrame = rememberCustomVideoFrameBitmap(cropState?.videoUriString)
        SettingsWallpaperCropDialog(
            show = cropState?.hasMedia == true,
            uriString = cropState?.wallpaperUriString ?: cropState?.videoUriString,
            crop = cropState?.crop ?: CustomBackgroundState().crop,
            onCropChange = { crop ->
                cropScopeKey?.let { actions.onCropChange(it, crop) }
            },
            onDismissRequest = { cropScopeKey = null },
            previewBitmap = cropVideoFrame.takeIf { cropState?.hasVideo == true },
            transform = cropState?.visualSettings?.transform ?: me.weishu.kernelsu.ui.util.MediaTransform(),
            onTransformChange = { transform ->
                cropScopeKey?.let { scopeKey ->
                    val current = uiState.backgroundStateForScope(scopeKey)
                    actions.onVisualSettingsChange(scopeKey, current.visualSettings.copy(transform = transform))
                }
            },
        )

        val previewState = previewScopeKey?.let { scopeKey ->
            uiState.backgroundStateForScope(scopeKey).takeIf { it.hasMedia }
                ?: uiState.backgroundStateForScope(GLOBAL_BACKGROUND_SCOPE_KEY)
        }
        val previewPassthrough = me.weishu.kernelsu.ui.util.rememberUiPassthroughScope(
            previewScopeKey?.let(::pageTargetFromScopeKey),
        ).settings
        SettingsWallpaperPreviewDialog(
            show = previewState?.hasWallpaper == true,
            uriString = previewState?.wallpaperUriString,
            opacity = previewState?.opacity ?: CustomBackgroundState().opacity,
            crop = previewState?.crop ?: CustomBackgroundState().crop,
            visualSettings = previewState?.visualSettings ?: me.weishu.kernelsu.ui.util.MediaVisualSettings(),
            passthroughSettings = previewPassthrough,
            onDismissRequest = { previewScopeKey = null },
        )
        SettingsVideoBackgroundPreviewDialog(
            show = previewState?.hasVideo == true,
            uriString = previewState?.videoUriString,
            durationSeconds = previewState?.videoDurationSeconds ?: CustomBackgroundState().videoDurationSeconds,
            frameRate = previewState?.videoFrameRate ?: CustomBackgroundState().videoFrameRate,
            opacity = previewState?.opacity ?: CustomBackgroundState().opacity,
            crop = previewState?.crop ?: CustomBackgroundState().crop,
            visualSettings = previewState?.visualSettings ?: me.weishu.kernelsu.ui.util.MediaVisualSettings(),
            passthroughSettings = previewPassthrough,
            onDismissRequest = { previewScopeKey = null },
        )
    }
}

@Composable
internal fun BackgroundSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: @Composable (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = valueLabel(value),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
    }
}

@Composable
internal fun VideoFrameRateSelector(
    frameRate: Int,
    onFrameRateChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.settings_video_background_frame_rate),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.settings_video_background_frame_rate_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CUSTOM_VIDEO_BACKGROUND_FRAME_RATE_OPTIONS.forEach { option ->
                FilterChip(
                    modifier = Modifier.heightIn(min = 48.dp),
                    selected = frameRate == option,
                    onClick = { onFrameRateChange(option) },
                    leadingIcon = if (frameRate == option) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_video_background_frame_rate_value, option),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SharedBackgroundOptions(
    uiState: SettingsUiState,
    actions: BackgroundSettingsActions,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UiPassthroughSwitch(
                title = stringResource(R.string.settings_background_scroll_follow),
                checked = uiState.backgroundScrollFollowEnabled,
                onCheckedChange = actions.onScrollFollowEnabledChange,
            )
            Text(
                text = stringResource(R.string.settings_background_scroll_follow_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal data class BackgroundSettingsActions(
    val onPickImage: (String) -> Unit,
    val onPickVideo: (String) -> Unit,
    val onCrop: (String) -> Unit,
    val onPreview: (String) -> Unit,
    val onClear: (String) -> Unit,
    val onOpacityChange: (String, Float) -> Unit,
    val onDurationChange: (String, Int) -> Unit,
    val onFrameRateChange: (Int) -> Unit,
    val onVisualSettingsChange: (String, me.weishu.kernelsu.ui.util.MediaVisualSettings) -> Unit,
    val onCropChange: (String, me.weishu.kernelsu.ui.util.CustomWallpaperCrop) -> Unit,
    val onScrollFollowEnabledChange: (Boolean) -> Unit,
)

internal fun SettingsUiState.backgroundStateForScope(scopeKey: String): CustomBackgroundState {
    if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) {
        return CustomBackgroundState(
            wallpaperUriString = customWallpaperUri,
            videoUriString = customVideoBackgroundUri,
            opacity = customWallpaperOpacity,
            crop = customWallpaperCrop,
            videoDurationSeconds = customVideoBackgroundDurationSeconds,
            videoFrameRate = customVideoBackgroundFrameRate,
            visualSettings = customWallpaperVisualSettings,
        )
    }
    val target = pageTargetFromScopeKey(scopeKey) ?: return CustomBackgroundState()
    return customPageBackgrounds[target]
}

@Composable
internal fun backgroundSelectionSummary(state: CustomBackgroundState): String {
    return stringResource(
        when {
            state.hasVideo -> R.string.settings_video_background_selected_summary
            state.hasWallpaper -> R.string.settings_wallpaper_selected_summary
            else -> R.string.settings_background_summary
        }
    )
}

internal fun pageScopeKey(target: CustomPageBackgroundTarget): String {
    return "page:${target.id}"
}

internal fun pageTargetFromScopeKey(scopeKey: String): CustomPageBackgroundTarget? {
    return CustomPageBackgroundTarget.entries.firstOrNull { scopeKey == pageScopeKey(it) }
}

private fun wallpaperStorageKey(scopeKey: String): String? {
    if (scopeKey == GLOBAL_BACKGROUND_SCOPE_KEY) return CUSTOM_WALLPAPER_URI_KEY
    return pageTargetFromScopeKey(scopeKey)?.wallpaperUriKey
}
