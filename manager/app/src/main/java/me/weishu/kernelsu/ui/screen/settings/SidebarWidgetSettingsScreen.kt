package me.weishu.kernelsu.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import androidx.compose.material3.Tab
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.LocalInterfaceStyle
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.SIDEBAR_NAVIGATION_IDS
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SidebarWidgetSettingsScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    var config by remember { mutableStateOf(readSidebarWidgetConfig(context)) }
    var selectedSection by rememberSaveable { mutableStateOf(0) }
    val systemDark = isInDarkTheme()
    var editingDark by rememberSaveable { mutableStateOf(systemDark) }
    val controlsScrollState = rememberScrollState()
    LaunchedEffect(selectedSection) { controlsScrollState.scrollTo(0) }
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

    ExpressiveScaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sidebar_widget_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value) {
                        MaterialTheme.colorScheme.surface
                    } else Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            SidebarDesignTabs(selectedSection) { selectedSection = it }
            SidebarWidgetControls(
                config = config,
                onUpdate = ::update,
                selectedSection = selectedSection,
                editingDark = editingDark,
                onEditingDarkChange = { editingDark = it },
                onEditNavigationIcons = { navigator.push(Route.NavigationIcons) },
                onPickImage = {
                    imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClearImage = {
                    releaseCustomImageReference(context, config.imageUriString)
                    update(config.copy(imageUriString = null))
                },
                modifier = Modifier.weight(1f).verticalScroll(controlsScrollState).padding(top = 20.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SidebarDesignTabs(selectedSection: Int, onSelectSection: (Int) -> Unit) {
    val labels = listOf(
        R.string.sidebar_design_appearance_tab,
        R.string.sidebar_design_widgets_tab,
        R.string.sidebar_design_navigation_tab,
        R.string.sidebar_design_home_tab,
    )
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        val tabs: @Composable () -> Unit = {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { onSelectSection(index) },
                    text = { Text(stringResource(label), maxLines = 2) },
                )
            }
        }
        if (maxWidth < 440.dp * fontScale.coerceAtLeast(1f)) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedSection,
                edgePadding = 8.dp,
                minTabWidth = 104.dp * fontScale.coerceAtLeast(1f),
                containerColor = Color.Transparent,
                tabs = tabs,
            )
        } else {
            PrimaryTabRow(
                selectedTabIndex = selectedSection,
                containerColor = Color.Transparent,
                tabs = tabs,
            )
        }
    }
}

@Composable
private fun SidebarWidgetControls(
    config: SidebarWidgetConfig,
    onUpdate: (SidebarWidgetConfig) -> Unit,
    selectedSection: Int,
    editingDark: Boolean,
    onEditingDarkChange: (Boolean) -> Unit,
    onEditNavigationIcons: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var homeCardsExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (selectedSection == 3) {
            SettingsSection(
                title = stringResource(R.string.sidebar_home_layout_title),
            ) {
                Text(
                    stringResource(R.string.sidebar_home_layout_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidebarHomeLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = config.homeLayout == layout,
                            onClick = { onUpdate(config.copy(homeLayout = layout)) },
                            label = { Text(stringResource(layout.labelRes())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            SettingsSection(title = stringResource(R.string.sidebar_home_cards_title)) {
                OutlinedButton(onClick = { homeCardsExpanded = !homeCardsExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (homeCardsExpanded) R.string.sidebar_home_cards_customize_hide else R.string.sidebar_home_cards_customize_show))
                }
                if (homeCardsExpanded) SidebarHomeCardOptions(config, onUpdate)
            }
        }
        if (selectedSection == 0) {
            SettingsSection(
                title = stringResource(R.string.sidebar_material_title),
            ) {
                SidebarMaterialOptions(config, editingDark, onEditingDarkChange, onUpdate)
            }
        }
        if (selectedSection == 1) {
            SettingsSection(
                title = stringResource(R.string.sidebar_avatar_title),
            ) {
                Text(
                    stringResource(R.string.sidebar_avatar_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AvatarOptions(config, onUpdate, onPickImage, onClearImage)
            }
            SettingsSection(
                title = stringResource(R.string.sidebar_widget_component_title),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidebarWidgetType.entries.forEach { type ->
                        FilterChip(
                            selected = config.widgetType == type,
                            onClick = { onUpdate(config.copy(widgetType = type)) },
                            label = { Text(stringResource(type.labelRes())) },
                            leadingIcon = { Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                when (config.widgetType) {
                    SidebarWidgetType.Clock -> ClockOptions(config, onUpdate)
                    SidebarWidgetType.Weather -> SidebarWeatherOptions(config, onUpdate)
                    SidebarWidgetType.Alarm -> Text(
                        text = stringResource(R.string.sidebar_widget_alarm_system_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (selectedSection == 2) {
            SettingsSection(
                title = stringResource(R.string.sidebar_navigation_layout_title),
            ) {
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
                Text(stringResource(R.string.sidebar_widget_side_title), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidebarSide.entries.forEach { side ->
                        FilterChip(
                            selected = config.side == side,
                            onClick = { onUpdate(config.copy(side = side)) },
                            label = { Text(stringResource(side.labelRes())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Text(stringResource(R.string.sidebar_widget_navigation_position_title), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidebarNavigationPosition.entries.forEach { position ->
                        FilterChip(
                            selected = config.navigationPosition == position,
                            onClick = { onUpdate(config.copy(navigationPosition = position)) },
                            label = { Text(stringResource(position.labelRes())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
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
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(spec.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = stringResource(spec.labelRes),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onUpdate(config.copy(navigationOrder = config.navigationOrder.swap(index, index - 1))) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.sidebar_widget_move_up))
                        }
                        IconButton(
                            onClick = { onUpdate(config.copy(navigationOrder = config.navigationOrder.swap(index, index + 1))) },
                            enabled = index < config.navigationOrder.lastIndex,
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.sidebar_widget_move_down))
                        }
                    }
                    if (index < config.navigationOrder.lastIndex) HorizontalDivider()
                }
            }
        }
        Text(
            text = stringResource(when (selectedSection) {
                0 -> R.string.sidebar_material_saved_hint
                3 -> R.string.sidebar_home_cards_saved_hint
                else -> R.string.sidebar_design_saved_hint
            }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClockOptions(config: SidebarWidgetConfig, onUpdate: (SidebarWidgetConfig) -> Unit) {
    var weatherSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var gradientStyles by rememberSaveable { mutableStateOf(config.clockStyle.gradient) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sidebar_widget_clock_style_title),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false, true).forEach { gradient ->
                FilterChip(
                    selected = gradientStyles == gradient,
                    onClick = { gradientStyles = gradient },
                    label = { Text(stringResource(if (gradient) R.string.sidebar_widget_clock_gradient_group else R.string.sidebar_widget_clock_classic_group)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SidebarClockStyle.entries.filter { it.gradient == gradientStyles }.forEach { style ->
                FilterChip(
                    selected = config.clockStyle == style,
                    onClick = { onUpdate(config.copy(clockStyle = style)) },
                    label = { Text(stringResource(style.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
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
            TextButton(onClick = { weatherSettingsExpanded = !weatherSettingsExpanded }) {
                Text(stringResource(if (weatherSettingsExpanded) R.string.sidebar_widget_clock_weather_hide_settings else R.string.sidebar_widget_clock_weather_settings))
            }
            if (weatherSettingsExpanded) SidebarWeatherOptions(config, onUpdate)
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
        Text(
            text = stringResource(R.string.sidebar_avatar_shape),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SidebarImageShape.entries.forEach { shape ->
                FilterChip(
                    selected = config.imageShape == shape,
                    onClick = { onUpdate(config.copy(imageShape = shape)) },
                    label = { Text(stringResource(shape.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

private fun SidebarWidgetType.icon(): ImageVector = when (this) {
    SidebarWidgetType.Clock -> Icons.Rounded.Schedule
    SidebarWidgetType.Weather -> Icons.Rounded.Cloud
    SidebarWidgetType.Alarm -> Icons.Rounded.Alarm
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
