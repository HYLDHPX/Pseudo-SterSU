package me.weishu.kernelsu.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.SIDEBAR_NAV_HOME
import me.weishu.kernelsu.ui.util.SIDEBAR_NAV_KPM
import me.weishu.kernelsu.ui.util.SIDEBAR_NAV_MODULE
import me.weishu.kernelsu.ui.util.SIDEBAR_NAV_SETTINGS
import me.weishu.kernelsu.ui.util.SIDEBAR_NAV_SUPERUSER
import me.weishu.kernelsu.ui.util.SIDEBAR_WIDGET_IMAGE_STORAGE_KEY
import me.weishu.kernelsu.ui.util.SidebarClockStyle
import me.weishu.kernelsu.ui.util.SidebarImageShape
import me.weishu.kernelsu.ui.util.SidebarHomeLayout
import me.weishu.kernelsu.ui.util.SidebarNavigationPosition
import me.weishu.kernelsu.ui.util.SidebarSide
import me.weishu.kernelsu.ui.util.SidebarWidgetConfig
import me.weishu.kernelsu.ui.util.SidebarWidgetType
import me.weishu.kernelsu.ui.util.persistCustomImageReference
import me.weishu.kernelsu.ui.util.readSidebarWidgetConfig
import me.weishu.kernelsu.ui.util.releaseCustomImageReference
import me.weishu.kernelsu.ui.util.takePersistableImageReadPermission
import me.weishu.kernelsu.ui.util.writeSidebarWidgetConfig

@Composable
fun SidebarWidgetSettingsScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    var config by remember { mutableStateOf(readSidebarWidgetConfig(context)) }
    var selectedPageName by rememberSaveable { mutableStateOf<String?>(null) }
    val systemDark = isInDarkTheme()
    var editingDark by rememberSaveable { mutableStateOf(systemDark) }
    val onBack = dropUnlessResumed { navigator.pop() }

    fun update(next: SidebarWidgetConfig) {
        val normalized = next.normalized()
        config = normalized
        writeSidebarWidgetConfig(context, normalized)
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val previous = config.imageUriString
        val stored = persistCustomImageReference(
            context = context,
            sourceUri = uri,
            storageKey = SIDEBAR_WIDGET_IMAGE_STORAGE_KEY,
            maxBytes = 12L * 1024L * 1024L,
        ) ?: uri.toString().also { takePersistableImageReadPermission(context, uri) }
        update(config.copy(imageUriString = stored))
        if (previous != stored) releaseCustomImageReference(context, previous)
    }

    SidebarDesignWorkspace(
        config = config,
        selectedPage = SidebarDesignPage.entries.firstOrNull { it.name == selectedPageName },
        onSelectPage = { selectedPageName = it?.name },
        editingDark = editingDark,
        onEditingDarkChange = { editingDark = it },
        onBack = onBack,
    ) { page ->
        SidebarWidgetControls(
            config = config,
            onUpdate = ::update,
            selectedSection = page,
            editingDark = editingDark,
            onEditingDarkChange = { editingDark = it },
            onEditNavigationIcons = { navigator.push(Route.NavigationIcons) },
            onOpenWeatherOptions = { selectedPageName = SidebarDesignPage.Weather.name },
            onPickImage = {
                imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClearImage = {
                releaseCustomImageReference(context, config.imageUriString)
                update(config.copy(imageUriString = null))
            },
        )
    }
}

@Composable
private fun SidebarWidgetControls(
    config: SidebarWidgetConfig,
    onUpdate: (SidebarWidgetConfig) -> Unit,
    selectedSection: SidebarDesignPage,
    editingDark: Boolean,
    onEditingDarkChange: (Boolean) -> Unit,
    onEditNavigationIcons: () -> Unit,
    onOpenWeatherOptions: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (selectedSection == SidebarDesignPage.Home) {
            SettingsSection {
                Text(
                    stringResource(R.string.sidebar_home_layout_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SidebarDesignChoice(
                    title = stringResource(R.string.sidebar_home_layout_title),
                    options = SidebarHomeLayout.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = SidebarHomeLayout.entries.indexOf(config.homeLayout),
                    onSelected = { onUpdate(config.copy(homeLayout = SidebarHomeLayout.entries[it])) },
                )
            }
            SettingsSection(title = stringResource(R.string.sidebar_home_cards_title)) {
                SidebarHomeCardOptions(config, onUpdate)
            }
        }
        if (selectedSection == SidebarDesignPage.Appearance) {
            SettingsSection {
                if (me.weishu.kernelsu.ui.component.rain.isRainInterfaceStyle()) {
                    val navigator = LocalNavigator.current
                    Text(stringResource(R.string.rain_sidebar_material_note), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { navigator.push(Route.RainDesign) }) { Text(stringResource(R.string.rain_design_title)) }
                } else {
                    SidebarMaterialOptions(config, editingDark, onEditingDarkChange, onUpdate)
                }
            }
        }
        if (selectedSection == SidebarDesignPage.Avatar) {
            SettingsSection {
                AvatarOptions(config, onUpdate, onPickImage, onClearImage)
            }
        }
        if (selectedSection == SidebarDesignPage.Weather) {
            SidebarWeatherOptions(config, onUpdate)
        }
        if (selectedSection == SidebarDesignPage.Widget) {
            SettingsSection {
                SidebarDesignChoice(
                    title = stringResource(R.string.sidebar_widget_component_title),
                    options = SidebarWidgetType.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = SidebarWidgetType.entries.indexOf(config.widgetType),
                    onSelected = { onUpdate(config.copy(widgetType = SidebarWidgetType.entries[it])) },
                )
                when (config.widgetType) {
                    SidebarWidgetType.Clock -> ClockOptions(config, onUpdate, onOpenWeatherOptions)
                    SidebarWidgetType.Weather -> OutlinedButton(
                        onClick = onOpenWeatherOptions,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.sidebar_editor_weather_title)) }
                    SidebarWidgetType.Alarm -> Text(
                        text = stringResource(R.string.sidebar_widget_alarm_system_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (selectedSection == SidebarDesignPage.Navigation) {
            SettingsSection {
                val iconOnlyTitle = stringResource(R.string.sidebar_navigation_icon_only_title)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(iconOnlyTitle, style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.sidebar_navigation_icon_only_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExpressiveSwitch(
                        checked = config.iconOnlyNavigation,
                        onCheckedChange = { onUpdate(config.copy(iconOnlyNavigation = it)) },
                        modifier = Modifier.semantics { contentDescription = iconOnlyTitle },
                    )
                }
                SidebarDesignChoice(
                    title = stringResource(R.string.sidebar_widget_side_title),
                    options = SidebarSide.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = SidebarSide.entries.indexOf(config.side),
                    onSelected = { onUpdate(config.copy(side = SidebarSide.entries[it])) },
                )
                SidebarDesignChoice(
                    title = stringResource(R.string.sidebar_widget_navigation_position_title),
                    options = SidebarNavigationPosition.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = SidebarNavigationPosition.entries.indexOf(config.navigationPosition),
                    onSelected = { onUpdate(config.copy(navigationPosition = SidebarNavigationPosition.entries[it])) },
                )
                OutlinedButton(onClick = onEditNavigationIcons, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sidebar_design_icons))
                }
            }
            SettingsSection(
                title = stringResource(R.string.sidebar_widget_navigation_order_title),
            ) {
                config.navigationOrder.forEachIndexed { index, id ->
                    val spec = navigationSpec(id) ?: return@forEachIndexed
                    val itemLabel = stringResource(spec.labelRes)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(spec.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = itemLabel,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onUpdate(config.copy(navigationOrder = config.navigationOrder.swap(index, index - 1))) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "$itemLabel: ${stringResource(R.string.sidebar_widget_move_up)}")
                        }
                        IconButton(
                            onClick = { onUpdate(config.copy(navigationOrder = config.navigationOrder.swap(index, index + 1))) },
                            enabled = index < config.navigationOrder.lastIndex,
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "$itemLabel: ${stringResource(R.string.sidebar_widget_move_down)}")
                        }
                    }
                    if (index < config.navigationOrder.lastIndex) HorizontalDivider()
                }
            }
        }
        Text(
            text = stringResource(when (selectedSection) {
                SidebarDesignPage.Appearance -> R.string.sidebar_material_saved_hint
                SidebarDesignPage.Home -> R.string.sidebar_home_cards_saved_hint
                else -> R.string.sidebar_editor_save_help
            }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClockOptions(config: SidebarWidgetConfig, onUpdate: (SidebarWidgetConfig) -> Unit, onOpenWeatherOptions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SidebarDesignChoice(
            title = stringResource(R.string.sidebar_widget_clock_style_title),
            options = SidebarClockStyle.entries.map { stringResource(it.labelRes()) },
            selectedIndex = SidebarClockStyle.entries.indexOf(config.clockStyle),
            onSelected = { onUpdate(config.copy(clockStyle = SidebarClockStyle.entries[it])) },
        )
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sidebar_widget_clock_weather_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.sidebar_widget_clock_weather_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpressiveSwitch(
                checked = config.clockWeatherEnabled,
                onCheckedChange = { onUpdate(config.copy(clockWeatherEnabled = it)) },
            )
        }
        if (config.clockWeatherEnabled) {
            OutlinedButton(onClick = onOpenWeatherOptions, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sidebar_widget_clock_weather_settings))
            }
        }
    }
}

@Composable
private fun AvatarOptions(
    config: SidebarWidgetConfig,
    onUpdate: (SidebarWidgetConfig) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPickImage) {
                Icon(Icons.Rounded.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sidebar_avatar_choose))
            }
            if (config.imageUriString != null) {
                OutlinedButton(onClick = onClearImage) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sidebar_widget_clear_image))
                }
            }
        }
        SidebarDesignChoice(
            title = stringResource(R.string.sidebar_avatar_shape),
            options = SidebarImageShape.entries.map { stringResource(it.labelRes()) },
            selectedIndex = SidebarImageShape.entries.indexOf(config.imageShape),
            onSelected = { onUpdate(config.copy(imageShape = SidebarImageShape.entries[it])) },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@StringRes
private fun SidebarHomeLayout.labelRes(): Int = when (this) {
    SidebarHomeLayout.Material -> R.string.sidebar_home_layout_material
    SidebarHomeLayout.StatusCards -> R.string.sidebar_home_layout_cards
}

private data class NavigationSpec(@StringRes val labelRes: Int, val icon: ImageVector)

private fun navigationSpec(id: String): NavigationSpec? = when (id) {
    SIDEBAR_NAV_HOME -> NavigationSpec(R.string.home, Icons.Rounded.Home)
    SIDEBAR_NAV_KPM -> NavigationSpec(R.string.kpm_short_title, Icons.Rounded.Memory)
    SIDEBAR_NAV_SUPERUSER -> NavigationSpec(R.string.superuser, Icons.Rounded.Security)
    SIDEBAR_NAV_MODULE -> NavigationSpec(R.string.module, Icons.Rounded.Extension)
    SIDEBAR_NAV_SETTINGS -> NavigationSpec(R.string.settings, Icons.Rounded.Settings)
    else -> null
}

private fun List<String>.swap(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply {
        val value = removeAt(from)
        add(to, value)
    }
}

@StringRes
private fun SidebarWidgetType.labelRes(): Int = when (this) {
    SidebarWidgetType.Clock -> R.string.sidebar_widget_type_clock
    SidebarWidgetType.Weather -> R.string.sidebar_widget_type_weather
    SidebarWidgetType.Alarm -> R.string.sidebar_widget_type_alarm
}

@StringRes
private fun SidebarClockStyle.labelRes(): Int = when (this) {
    SidebarClockStyle.Stacked -> R.string.sidebar_widget_clock_stacked
    SidebarClockStyle.Compact -> R.string.sidebar_widget_clock_compact
    SidebarClockStyle.DateFirst -> R.string.sidebar_widget_clock_date_first
    SidebarClockStyle.Minimal -> R.string.sidebar_widget_clock_minimal
    SidebarClockStyle.Calendar -> R.string.sidebar_widget_clock_calendar
    SidebarClockStyle.Dial -> R.string.sidebar_widget_clock_dial
    SidebarClockStyle.AuroraDigits -> R.string.sidebar_widget_clock_aurora_digits
    SidebarClockStyle.SunsetDigits -> R.string.sidebar_widget_clock_sunset_digits
    SidebarClockStyle.DualTiles -> R.string.sidebar_widget_clock_dual_tiles
}

@StringRes
private fun SidebarImageShape.labelRes(): Int = when (this) {
    SidebarImageShape.Circle -> R.string.sidebar_widget_shape_circle
    SidebarImageShape.Square -> R.string.sidebar_widget_shape_square
    SidebarImageShape.Diamond -> R.string.sidebar_widget_shape_diamond
    SidebarImageShape.Triangle -> R.string.sidebar_widget_shape_triangle
}

@StringRes
private fun SidebarNavigationPosition.labelRes(): Int = when (this) {
    SidebarNavigationPosition.Top -> R.string.sidebar_widget_position_top
    SidebarNavigationPosition.Center -> R.string.sidebar_widget_position_center
    SidebarNavigationPosition.Bottom -> R.string.sidebar_widget_position_bottom
}

@StringRes
private fun SidebarSide.labelRes(): Int = when (this) {
    SidebarSide.Left -> R.string.sidebar_widget_side_left
    SidebarSide.Right -> R.string.sidebar_widget_side_right
}
