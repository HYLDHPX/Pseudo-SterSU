package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import me.weishu.kernelsu.ui.component.StyledSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.CustomPageBackgroundTarget
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import me.weishu.kernelsu.ui.util.rememberUiPassthroughScope
import me.weishu.kernelsu.ui.util.writeUiPassthroughScope
import kotlin.math.roundToInt

/** Keep editor controls opaque so a low foreground opacity can always be recovered. */
@Composable
internal fun UiPassthroughOptions(target: CustomPageBackgroundTarget?) {
    val context = LocalContext.current
    val scope = rememberUiPassthroughScope(target)
    val value = scope.settings
    var expanded by rememberSaveable(target) { mutableStateOf(false) }
    var foregroundExpanded by rememberSaveable(target) { mutableStateOf(false) }
    fun update(next: UiPassthroughSettings) = writeUiPassthroughScope(context, target, next)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_passthrough_title), modifier = Modifier.weight(1f))
            Text(stringResource(if (expanded) R.string.ui_passthrough_collapse else R.string.ui_passthrough_expand))
        }
        if (expanded) {
            Text(stringResource(R.string.ui_passthrough_help), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (target != null) {
                UiPassthroughSwitch(
                    title = stringResource(R.string.ui_passthrough_inherit),
                    checked = !scope.overridesGlobal,
                    onCheckedChange = { inherit ->
                        writeUiPassthroughScope(context, target, value, overridesGlobal = !inherit)
                    },
                )
            }
            if (target == null || scope.overridesGlobal) {
                UiPassthroughSwitch(stringResource(R.string.ui_passthrough_enable), value.enabled) {
                    update(value.copy(enabled = it))
                }
                if (value.enabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.85f to R.string.ui_passthrough_readable, 0.65f to R.string.ui_passthrough_soft,
                            0f to R.string.ui_passthrough_clear).forEach { (opacity, label) ->
                            FilterChip(
                                selected = value.surfaceOpacity == opacity,
                                onClick = { update(value.copy(surfaceOpacity = opacity,
                                    topBarOpacity = 0f, bottomBarOpacity = opacity)) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    UiOpacitySlider(R.string.ui_passthrough_surfaces, value.surfaceOpacity) {
                        update(value.copy(surfaceOpacity = it))
                    }
                    UiOpacitySlider(R.string.ui_passthrough_top_bar, value.topBarOpacity) {
                        update(value.copy(topBarOpacity = it))
                    }
                    UiOpacitySlider(R.string.ui_passthrough_bottom_bar, value.bottomBarOpacity) {
                        update(value.copy(bottomBarOpacity = it))
                    }
                    TextButton(onClick = { foregroundExpanded = !foregroundExpanded }) {
                        Text(stringResource(R.string.ui_passthrough_foreground))
                    }
                    if (foregroundExpanded) {
                        Text(stringResource(R.string.ui_passthrough_foreground_help),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        UiOpacitySlider(R.string.ui_passthrough_text, value.textOpacity, 0.2f..1f) {
                            update(value.copy(textOpacity = it))
                        }
                        UiOpacitySlider(R.string.ui_passthrough_icons, value.iconOpacity, 0.2f..1f) {
                            update(value.copy(iconOpacity = it))
                        }
                        UiOpacitySlider(R.string.ui_passthrough_images, value.imageOpacity) {
                            update(value.copy(imageOpacity = it))
                        }
                    }
                    TextButton(onClick = { update(UiPassthroughSettings(enabled = true)) }) {
                        Text(stringResource(R.string.ui_passthrough_reset))
                    }
                }
            }
        }
    }
}

@Composable
internal fun UiPassthroughSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        StyledSwitch(checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun UiOpacitySlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit) {
    val title = stringResource(label)
    var pending by remember(value) { mutableStateOf(value) }
    val state = remember(range) { SliderState(pending, trackRange = range) }
    state.value = pending
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${(pending * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(state = state,
            onValueChange = { pending = (it * 100).roundToInt() / 100f },
            onValueChangeFinished = { onValueChange(pending) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = title })
    }
}
