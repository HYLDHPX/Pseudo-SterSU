package me.weishu.kernelsu.ui.screen.navigationicon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.screen.settings.AppearanceEditorChoice
import me.weishu.kernelsu.ui.screen.settings.MediaEditorSlider
import me.weishu.kernelsu.ui.screen.settings.rememberMediaFileInfo
import me.weishu.kernelsu.ui.util.CustomNavigationIconMask
import me.weishu.kernelsu.ui.util.CustomNavigationIconState

@Composable
internal fun NavigationIconAdjustmentControls(
    state: CustomNavigationIconState,
    section: Int,
    onValueChange: (CustomNavigationIconState) -> Unit,
) {
    val value = state.normalized()
    val info = rememberMediaFileInfo(value.uriString)
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xFFFFFFFFL
    var labelDraft by rememberSaveable { mutableStateOf(value.labelOverride.orEmpty()) }
    LaunchedEffect(value.labelOverride) {
        if (labelDraft.trim() != value.labelOverride.orEmpty()) {
            labelDraft = value.labelOverride.orEmpty()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (section == 0) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = labelDraft,
                onValueChange = {
                    labelDraft = it.take(20)
                    onValueChange(value.copy(labelOverride = labelDraft).normalized())
                },
                label = { Text(stringResource(R.string.settings_navigation_icon_name)) },
                supportingText = { Text(stringResource(R.string.appearance_editor_icon_name_hint)) },
                singleLine = true,
            )
            MediaEditorSlider(
                title = stringResource(R.string.settings_navigation_icon_size),
                value = value.sizeScale,
                valueRange = 0.6f..1.4f,
                valueLabel = { "${(it * 100).toInt()}%" },
                onValueChange = { onValueChange(value.copy(sizeScale = it).normalized()) },
            )
            MediaEditorSlider(
                title = stringResource(R.string.settings_navigation_icon_padding),
                value = value.innerPaddingDp,
                valueRange = 0f..8f,
                valueLabel = { "${it.toInt()} dp" },
                onValueChange = { onValueChange(value.copy(innerPaddingDp = it).normalized()) },
            )
            MediaEditorSlider(
                title = stringResource(R.string.settings_navigation_icon_vertical),
                value = value.verticalOffsetDp,
                valueRange = -8f..8f,
                valueLabel = { "${if (it > 0) "+" else ""}${it.toInt()} dp" },
                onValueChange = { onValueChange(value.copy(verticalOffsetDp = it).normalized()) },
            )

        } else {
            MediaEditorSlider(
                title = stringResource(R.string.settings_navigation_icon_opacity),
                value = value.opacity,
                valueRange = 0.2f..1f,
                valueLabel = { "${(it * 100).toInt()}%" },
                onValueChange = { onValueChange(value.copy(opacity = it).normalized()) },
            )
            if (value.hasSelected) {
                Text(
                    text = stringResource(R.string.settings_navigation_icon_mask),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomNavigationIconMask.entries.forEach { mask ->
                        AppearanceEditorChoice(
                            label = stringResource(mask.labelRes),
                            selected = value.mask == mask,
                            onClick = { onValueChange(value.copy(mask = mask)) },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_navigation_icon_mask_requires_image),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.settings_navigation_icon_tint),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to R.string.settings_navigation_icon_tint_original,
                    primaryArgb to R.string.settings_navigation_icon_tint_primary,
                    0xFFFFFFFFL to R.string.settings_navigation_icon_tint_white,
                    0xFF000000L to R.string.settings_navigation_icon_tint_black,
                ).forEach { (argb, labelRes) ->
                    AppearanceEditorChoice(
                        label = stringResource(labelRes),
                        selected = value.tintArgb == argb,
                        onClick = { onValueChange(value.copy(tintArgb = argb)) },
                    )
                }
            }
            if (info?.hasAlpha == false) {
                Text(
                    text = stringResource(R.string.settings_navigation_icon_no_alpha_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            value.tintArgb?.let { argb ->
                val contrast = contrastRatio(Color(argb.toInt()), Color(0xFF121212))
                Text(
                    text = stringResource(
                        if (contrast >= 3f) R.string.settings_navigation_icon_night_contrast_ok
                        else R.string.settings_navigation_icon_night_contrast_warning
                    ),
                    color = if (contrast >= 3f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

        }
    }
}

private val CustomNavigationIconMask.labelRes: Int
    get() = when (this) {
        CustomNavigationIconMask.Original -> R.string.settings_navigation_icon_mask_original
        CustomNavigationIconMask.Circle -> R.string.settings_navigation_icon_mask_circle
        CustomNavigationIconMask.Square -> R.string.settings_navigation_icon_mask_square
        CustomNavigationIconMask.RoundedSquare -> R.string.settings_navigation_icon_mask_rounded
    }

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
