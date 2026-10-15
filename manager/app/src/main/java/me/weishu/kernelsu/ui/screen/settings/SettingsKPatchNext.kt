package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R

internal enum class PathConfigBackend {
    SusfsGki,
    Disabled,
    Unknown,
}

internal fun resolvePathConfigBackend(
    isLkmMode: Boolean,
    isLateLoadMode: Boolean,
    runtimeModeResolved: Boolean,
): PathConfigBackend = when {
    !runtimeModeResolved -> PathConfigBackend.Unknown
    isLateLoadMode -> PathConfigBackend.Disabled
    isLkmMode -> PathConfigBackend.Disabled
    else -> PathConfigBackend.SusfsGki
}

internal val SettingsUiState.pathConfigBackend: PathConfigBackend
    get() = resolvePathConfigBackend(isLkmMode, isLateLoadMode, runtimeModeResolved)

internal val SettingsUiState.isGkiMode: Boolean
    get() = pathConfigBackend == PathConfigBackend.SusfsGki

internal val SettingsUiState.canOpenPathConfig: Boolean
    get() = pathConfigBackend == PathConfigBackend.SusfsGki

internal val SettingsUiState.isPathConfigEntryVisible: Boolean
    get() = !isLkmMode

internal val SettingsUiState.isBuiltinPathConfigEntryVisible: Boolean
    get() = isPathConfigEntryVisible

@Composable
internal fun pathConfigTitle(uiState: SettingsUiState): String = stringResource(
    when (uiState.pathConfigBackend) {
        PathConfigBackend.SusfsGki,
        PathConfigBackend.Disabled,
        PathConfigBackend.Unknown,
        -> R.string.settings_susfs_path_config
    }
)

@Composable
internal fun pathConfigSummary(uiState: SettingsUiState): String = stringResource(
    when (uiState.pathConfigBackend) {
        PathConfigBackend.SusfsGki -> R.string.settings_susfs_path_config_summary
        PathConfigBackend.Disabled -> R.string.settings_hidden_path_jailbreak_disabled_summary
        PathConfigBackend.Unknown -> R.string.settings_runtime_mode_detecting
    }
)
