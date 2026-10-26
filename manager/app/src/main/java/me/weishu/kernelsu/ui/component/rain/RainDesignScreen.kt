package me.weishu.kernelsu.ui.component.rain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.AdaptiveTopAppBar
import me.weishu.kernelsu.ui.component.StyledSwitch
import me.weishu.kernelsu.ui.component.bottombar.useNavigationRail
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveTopAppBar
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.formatSidebarRgb
import me.weishu.kernelsu.ui.util.parseSidebarRgb
import me.weishu.kernelsu.ui.util.SidebarWeatherRuntime
import me.weishu.kernelsu.ui.util.readSidebarWeatherConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RainDesignScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val rainActive = isRainInterfaceStyle()
    LaunchedEffect(rainActive) {
        if (!rainActive) navigator.pop()
    }
    if (!rainActive) return
    val settings = rememberRainSettings()
    val dark = isInDarkTheme()
    val sidebarRequested = settings.layout == RainLayout.Sidebar ||
        settings.layout == RainLayout.Miuix && settings.miuixSidebar
    val sidebarVisible = useNavigationRail()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var colorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var savePreset by rememberSaveable { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }
    var copyTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val presets = rememberRainPresets()
    val preferences = remember(context) { rainPreferences(context) }
    val scope = rememberCoroutineScope()
    var refreshingWeather by remember { mutableStateOf(false) }
    val update: ((RainSettings) -> RainSettings) -> Unit = { updateRainSettings(context, it) }
    val timeOfDay = rememberRainTimeOfDay(settings.atmosphere)
    val weather = rememberRainWeather(settings)
    val resolvedScene = resolveRainScene(settings, timeOfDay, weather.condition)
    val back: @Composable () -> Unit = {
        IconButtonCompat(onClick = { navigator.pop() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
        }
    }
    ExpressiveScaffold(topBar = {
        if (LocalUiMode.current == UiMode.Miuix) {
            AdaptiveTopAppBar(title = stringResource(R.string.rain_design_title), navigationIcon = back)
        } else {
            ExpressiveTopAppBar(title = { Text(stringResource(R.string.rain_design_title)) }, navigationIcon = back)
        }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rain_design_intro), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_layout)) {
                    RainChoices(RainLayout.entries.map { stringResource(it.label()) }, settings.layout.ordinal) {
                        selectRainLayout(context, RainLayout.entries[it])
                    }
                    Text(stringResource(R.string.rain_layout_saved), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.rain_copy_profile_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RainLayout.entries.filter { it != settings.layout }.forEach { target ->
                            TextButton(onClick = { copyTarget = target.value }) {
                                Text(stringResource(R.string.rain_copy_profile, stringResource(target.label())))
                            }
                        }
                    }
                    if (settings.layout == RainLayout.Miuix) {
                        RainToggle(stringResource(R.string.rain_miuix_sidebar), settings.miuixSidebar) { value ->
                            update { it.copy(miuixSidebar = value) }
                        }
                    }
                    if (settings.layout == RainLayout.Sidebar) {
                        TextButton(onClick = { navigator.push(Route.SidebarWidgetSettings) }) {
                            Text(stringResource(R.string.rain_sidebar_components))
                        }
                    }
                }
            }
            item {
                RainSection(stringResource(R.string.rain_scene)) {
                    RainSceneChoices(settings.scene, dark) { scene -> update { it.copy(scene = scene) } }
                    Text(stringResource(settings.scene.mottoRes), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(settings.scene.summaryRes), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (settings.atmosphere) {
                        Text(stringResource(R.string.rain_atmosphere_overrides, stringResource(resolvedScene.labelRes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RainToggle(stringResource(R.string.rain_motion), settings.motion) { value -> update { it.copy(motion = value) } }
                    Text(stringResource(R.string.rain_motion_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_atmosphere)) {
                    RainToggle(stringResource(R.string.rain_atmosphere_enable), settings.atmosphere) { value ->
                        update { it.copy(atmosphere = value) }
                    }
                    Text(stringResource(R.string.rain_atmosphere_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (settings.atmosphere) {
                        RainToggle(stringResource(R.string.rain_atmosphere_weather), settings.atmosphereFollowsWeather) { value ->
                            update { it.copy(atmosphereFollowsWeather = value) }
                        }
                        Text(stringResource(R.string.rain_atmosphere_now, stringResource(timeOfDay.labelRes())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (settings.atmosphereFollowsWeather) {
                            Text(rainWeatherStatusText(weather), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.rain_atmosphere_weather_note), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !refreshingWeather && readSidebarWeatherConfig(context).canFetch(), onClick = {
                                    scope.launch {
                                        refreshingWeather = true
                                        try { SidebarWeatherRuntime.repository(context).refresh(readSidebarWeatherConfig(context), force = true) }
                                        finally { refreshingWeather = false }
                                    }
                                }) { Text(stringResource(if (refreshingWeather) R.string.rain_weather_refreshing else R.string.rain_weather_refresh)) }
                                TextButton(onClick = { navigator.push(Route.SidebarWidgetSettings) }) {
                                    Text(stringResource(R.string.rain_weather_settings))
                                }
                            }
                        }
                    }
                }
            }
            item {
                RainSection(stringResource(R.string.rain_palette)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { colorTarget = "light" }) { Text(stringResource(R.string.rain_light_color)) }
                        TextButton(onClick = { colorTarget = "dark" }) { Text(stringResource(R.string.rain_dark_color)) }
                    }
                    Text(stringResource(R.string.rain_accent_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.rain_wallpaper_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { navigator.push(Route.Backgrounds) }) { Text(stringResource(R.string.rain_wallpaper)) }
                    RainToggle(stringResource(R.string.rain_wallpaper_overlay), settings.wallpaperRain) { value ->
                        update { it.copy(wallpaperRain = value) }
                    }
                    Text(stringResource(R.string.rain_wallpaper_overlay_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_decorations)) {
                    Text(stringResource(R.string.rain_decoration_card_border), style = MaterialTheme.typography.labelLarge)
                    RainChoices(RainCardBorder.entries.map { stringResource(it.labelRes) }, settings.cardBorder.ordinal) { index ->
                        update { it.copy(cardBorder = RainCardBorder.entries[index]) }
                    }
                    Text(stringResource(R.string.rain_decoration_card_interior), style = MaterialTheme.typography.labelLarge)
                    RainChoices(RainCardInterior.entries.map { stringResource(it.labelRes) }, settings.cardInterior.ordinal) { index ->
                        update { it.copy(cardInterior = RainCardInterior.entries[index]) }
                    }
                    RainDecorationChoices(stringResource(R.string.rain_decoration_card), settings.cardSurface) { value ->
                        update { it.copy(cardSurface = value) }
                    }
                    RainDecorationChoices(stringResource(R.string.rain_decoration_top_bar), settings.topBarDecoration) { value ->
                        update { it.copy(topBarDecoration = value) }
                    }
                    RainDecorationChoices(stringResource(R.string.rain_decoration_navigation), settings.navigationDecoration) { value ->
                        update { it.copy(navigationDecoration = value) }
                    }
                    RainDecorationChoices(stringResource(R.string.rain_decoration_sidebar), settings.sidebarDecoration) { value ->
                        update { it.copy(sidebarDecoration = value) }
                    }
                    RainSlider(stringResource(R.string.rain_decoration_opacity), settings.decorationOpacity, 0f..1f) { value ->
                        update { it.copy(decorationOpacity = value) }
                    }
                    Text(stringResource(R.string.rain_decorations_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_interaction_title)) {
                    RainToggle(stringResource(R.string.rain_through_ui), settings.rainThroughUi) { value ->
                        update { it.copy(rainThroughUi = value) }
                    }
                    if (settings.rainThroughUi) {
                        RainSlider(stringResource(R.string.rain_foreground_opacity), settings.foregroundOpacity, 0f..1f) { value ->
                            update { it.copy(foregroundOpacity = value) }
                        }
                        RainToggle(stringResource(R.string.rain_surface_interaction), settings.surfaceInteraction) { value ->
                            update { it.copy(surfaceInteraction = value) }
                        }
                    }
                    RainToggle(stringResource(R.string.rain_thunder), settings.thunder) { value ->
                        update { it.copy(thunder = value) }
                    }
                    Text(stringResource(R.string.rain_interaction_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_sound)) {
                    RainChoices(RainSound.entries.map { stringResource(it.labelRes) }, settings.sound.ordinal) { index ->
                        update { it.copy(sound = RainSound.entries[index]) }
                    }
                    if (settings.sound != RainSound.Off) {
                        RainSlider(stringResource(R.string.rain_sound_volume), settings.soundVolume, 0f..1f) { value ->
                            update { it.copy(soundVolume = value) }
                        }
                        RainToggle(stringResource(R.string.rain_wallpaper_sound), settings.soundWithWallpaper) { value ->
                            update { it.copy(soundWithWallpaper = value) }
                        }
                    }
                    Text(stringResource(R.string.rain_sound_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_surfaces)) {
                    RainSlider(stringResource(R.string.rain_cards_opacity), settings.surfaceOpacity, 0.35f..1f) { value -> update { it.copy(surfaceOpacity = value) } }
                    RainSlider(stringResource(R.string.rain_topbar_opacity), settings.topBarOpacity, 0.35f..1f) { value -> update { it.copy(topBarOpacity = value) } }
                    if (!sidebarRequested || !sidebarVisible) {
                        RainSlider(stringResource(R.string.rain_navigation_opacity), settings.navigationOpacity, 0.35f..1f) { value -> update { it.copy(navigationOpacity = value) } }
                    }
                    if (sidebarRequested) {
                        RainSlider(stringResource(R.string.rain_sidebar_opacity), settings.sidebarOpacity, 0f..1f) { value -> update { it.copy(sidebarOpacity = value) } }
                        RainToggle(stringResource(R.string.rain_sidebar_divider), settings.sidebarDivider) { value -> update { it.copy(sidebarDivider = value) } }
                        if (!sidebarVisible) Text(stringResource(R.string.rain_compact_navigation_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.rain_passthrough_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                RainSection(stringResource(R.string.rain_advanced)) {
                    RainToggle(stringResource(R.string.rain_advanced_show), advanced) { advanced = it }
                    if (advanced) {
                        Text(stringResource(R.string.rain_region_strength_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RainSlider(stringResource(R.string.rain_card_edge_strength), settings.cardEdgeStrength, 0f..1f) { value -> update { it.copy(cardEdgeStrength = value) } }
                        RainSlider(stringResource(R.string.rain_card_interior_strength), settings.cardInteriorStrength, 0f..1f) { value -> update { it.copy(cardInteriorStrength = value) } }
                        RainSlider(stringResource(R.string.rain_top_bar_strength), settings.topBarStrength, 0f..1f) { value -> update { it.copy(topBarStrength = value) } }
                        RainSlider(stringResource(R.string.rain_navigation_strength), settings.navigationStrength, 0f..1f) { value -> update { it.copy(navigationStrength = value) } }
                        if (sidebarRequested) {
                            RainSlider(stringResource(R.string.rain_sidebar_strength), settings.sidebarStrength, 0f..1f) { value -> update { it.copy(sidebarStrength = value) } }
                        }
                        if (settings.thunder) {
                            RainSlider(stringResource(R.string.rain_thunder_interval), settings.thunderInterval, 8f..60f,
                                percent = false, valueText = { it.roundToInt().toString() }) { value -> update { it.copy(thunderInterval = value) } }
                        }
                        RainChoices(listOf(stringResource(R.string.rain_quality_low), stringResource(R.string.rain_quality_standard),
                            stringResource(R.string.rain_quality_high)), settings.quality.ordinal) { index -> update { it.copy(quality = RainQuality.entries[index]) } }
                        RainSlider(stringResource(R.string.rain_intensity), settings.intensity, 0f..1f) { value -> update { it.copy(intensity = value) } }
                        RainSlider(stringResource(R.string.rain_speed), settings.speed, 0.4f..2f, percent = false) { value -> update { it.copy(speed = value) } }
                        RainSlider(stringResource(R.string.rain_wind), settings.wind, -0.5f..0.5f) { value -> update { it.copy(wind = value) } }
                        RainChoices(RainDropForm.entries.map { stringResource(it.labelRes()) }, settings.dropForm.ordinal) { index ->
                            update { it.copy(dropForm = RainDropForm.entries[index]) }
                        }
                        RainSlider(stringResource(R.string.rain_drop_length), settings.dropLength ?: 1f, 0.4f..2f, percent = false) { value ->
                            update { it.copy(dropLength = value) }
                        }
                        RainSlider(stringResource(R.string.rain_drop_opacity), settings.dropOpacity ?: 1f, 0.2f..1f) { value ->
                            update { it.copy(dropOpacity = value) }
                        }
                    }
                    TextButton(onClick = { reset = true }) { Text(stringResource(R.string.rain_reset)) }
                }
            }
            item {
                RainSection(stringResource(R.string.rain_presets)) {
                    Text(stringResource(R.string.rain_presets_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { presetName = ""; savePreset = true }) { Text(stringResource(R.string.rain_preset_save)) }
                    if (presets.isEmpty()) Text(stringResource(R.string.rain_presets_empty), style = MaterialTheme.typography.bodySmall)
                    presets.forEach { preset ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { applyRainPreset(preferences, preset) }) { Text(stringResource(R.string.rain_preset_apply)) }
                            IconButtonCompat(onClick = { deleteRainPreset(preferences, preset.id) }) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.rain_preset_delete, preset.name))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (savePreset) AlertDialog(onDismissRequest = { savePreset = false },
        title = { Text(stringResource(R.string.rain_preset_save)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = presetName, onValueChange = { presetName = it.filterNot(Char::isISOControl).take(32) },
                    label = { Text(stringResource(R.string.rain_preset_name)) }, singleLine = true)
                Text(stringResource(R.string.rain_presets_note), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(enabled = presetName.isNotBlank() && (presets.size < MAX_RAIN_PRESETS || presets.any { it.name == rainPresetName(presetName) }),
                onClick = { if (saveRainPreset(preferences, presetName)) savePreset = false }) {
                Text(stringResource(android.R.string.ok))
            }
        }, dismissButton = { TextButton(onClick = { savePreset = false }) { Text(stringResource(android.R.string.cancel)) } })
    copyTarget?.let { value ->
        val target = RainLayout.fromValue(value)
        AlertDialog(onDismissRequest = { copyTarget = null }, title = { Text(stringResource(R.string.rain_copy_profile, stringResource(target.label()))) },
            text = { Text(stringResource(R.string.rain_copy_profile_confirm)) },
            confirmButton = { TextButton(onClick = { copyRainProfile(preferences, settings.layout, target); copyTarget = null }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { copyTarget = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    colorTarget?.let { target ->
        RainColorDialog(
            title = stringResource(if (target == "dark") R.string.rain_dark_color else R.string.rain_light_color),
            initial = (if (target == "dark") settings.darkAccent else settings.lightAccent) ?: settings.scene.keyColor,
            onDismiss = { colorTarget = null },
            onApply = { color ->
                update { if (target == "dark") it.copy(darkAccent = color) else it.copy(lightAccent = color) }
                colorTarget = null
            },
        )
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false },
        title = { Text(stringResource(R.string.rain_reset)) }, text = { Text(stringResource(R.string.rain_reset_note)) },
        confirmButton = { TextButton(onClick = { update { RainSettings(active = true, layout = it.layout) }; reset = false }) {
            Text(stringResource(android.R.string.ok))
        } }, dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun rainWeatherStatusText(weather: RainWeatherSnapshot): String = when (weather.status) {
    RainWeatherStatus.Ready -> stringResource(R.string.rain_weather_ready, weather.location.ifBlank { stringResource(R.string.rain_weather_source) })
    RainWeatherStatus.Disabled -> stringResource(R.string.rain_weather_disabled)
    RainWeatherStatus.ConsentRequired -> stringResource(R.string.rain_weather_consent)
    RainWeatherStatus.Waiting -> stringResource(R.string.rain_weather_waiting)
    RainWeatherStatus.Stale -> stringResource(R.string.rain_weather_stale)
    RainWeatherStatus.Failed -> stringResource(R.string.rain_weather_failed)
    RainWeatherStatus.Unsupported -> stringResource(R.string.rain_weather_unsupported)
}

internal fun RainLayout.label(): Int = when (this) {
    RainLayout.Miuix -> R.string.interface_style_miuix
    RainLayout.Material -> R.string.interface_style_material
    RainLayout.Sidebar -> R.string.interface_style_sidebar_widget
}

internal fun RainDecoration.labelRes(): Int = when (this) {
    RainDecoration.Off -> R.string.rain_decoration_none
    RainDecoration.Wet -> R.string.rain_card_surface_wet
    RainDecoration.Dripping -> R.string.rain_card_surface_dripping
    RainDecoration.Ripples -> R.string.rain_decoration_ripples
    RainDecoration.Streams -> R.string.rain_decoration_streams
}

internal fun RainDropForm.labelRes(): Int = when (this) {
    RainDropForm.Line -> R.string.rain_drop_form_line
    RainDropForm.Needle -> R.string.rain_drop_form_needle
    RainDropForm.Bead -> R.string.rain_drop_form_bead
}

internal fun RainTimeOfDay.labelRes(): Int = when (this) {
    RainTimeOfDay.Dawn -> R.string.rain_time_dawn
    RainTimeOfDay.Day -> R.string.rain_time_day
    RainTimeOfDay.Dusk -> R.string.rain_time_dusk
    RainTimeOfDay.Night -> R.string.rain_time_night
}

@Composable
private fun RainDecorationChoices(label: String, selected: RainDecoration, onSelect: (RainDecoration) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        RainChoices(RainDecoration.entries.map { stringResource(it.labelRes()) }, selected.ordinal) {
            onSelect(RainDecoration.entries[it])
        }
    }
}

/** Rain-styled container: wet edge, sheen and border painted over the shared surface. */
@Composable
private fun RainSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = rainPalette(LocalRainSettings.current.copy(active = true), isInDarkTheme())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(listOf(palette.surfaceTop, palette.surfaceSheen)),
                    RoundedCornerShape(16.dp),
                )
                .rainCardDecoration(shape = RoundedCornerShape(16.dp)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun RainChoices(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
        .selectableGroup().padding(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(index) })
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * Scene picker with a live thumbnail per scene: the swatch paints the scene's own backdrop,
 * accent and streak angle so intensity differences are visible before selecting.
 */
@Composable
private fun RainSceneChoices(selected: RainStyle, dark: Boolean, onSelected: (RainStyle) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .selectableGroup().padding(vertical = 2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RainStyle.entries.forEach { scene ->
                val isSelected = scene == selected
                val palette = rainPalette(scene, dark)
                val label = stringResource(scene.labelRes)
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(12.dp))
                        .border(
                            1.4.dp,
                            if (isSelected) palette.rain else palette.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp),
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(scene) })
                        .clearAndSetSemantics {
                            contentDescription = label
                            this.selected = isSelected
                        }
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RainSceneSwatch(scene, palette)
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Static miniature: gradient sky plus streaks driven by the scene spec, no animation. */
@Composable
private fun RainSceneSwatch(scene: RainStyle, palette: RainPalette) {
    val spec = remember(scene) {
        RainSceneSpec.fromSettings(RainSettings(scene = scene, intensity = 0.85f))
    }
    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
            val count = (spec.dropCount / 3).coerceIn(2, 14)
            val step = size.width / count
            repeat(count) { index ->
                val x = step * index + step * 0.4f
                val length = (spec.maxLengthDp * 0.9f).dp.toPx().coerceAtMost(size.height * 0.7f)
                drawLine(
                    palette.rain.copy(alpha = 0.55f),
                    Offset(x, size.height * 0.15f),
                    Offset(x + length * spec.windRatio, size.height * 0.15f + length),
                    1.dp.toPx(),
                )
            }
            if (scene == RainStyle.AfterRain) {
                repeat(2) { index ->
                    val radius = (5f + index * 4f).dp.toPx()
                    drawOval(
                        palette.ripple.copy(alpha = 0.4f),
                        Offset(size.width * (0.3f + index * 0.4f) - radius, size.height * 0.82f),
                        androidx.compose.ui.geometry.Size(radius * 2f, radius * 0.5f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun RainToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        StyledSwitch(checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun RainSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, percent: Boolean = true,
    valueText: ((Float) -> String)? = null, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    val state = remember(range) { SliderState(value, trackRange = range) }
    state.value = draft
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText?.invoke(draft) ?: if (percent) "${(draft * 100).roundToInt()}%" else "${(draft * 10).roundToInt() / 10f}×",
                style = MaterialTheme.typography.labelLarge)
        }
        Slider(state = state, onValueChange = { draft = it }, onValueChangeFinished = { onCommit(draft) },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label })
    }
}

/**
 * Hex entry plus HSV picker. The picker drives the text field, never the reverse, so a
 * partially typed value cannot jump the cursor or overwrite what the user is writing.
 */
@Composable
private fun RainColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onApply: (Int?) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var hex by rememberSaveable(title, initial) { mutableStateOf(formatSidebarRgb(initial.toLong() and 0xFFFFFFFFL)) }
    var picker by rememberSaveable { mutableStateOf(false) }
    val color = parseSidebarRgb(hex)
    val resolved = color ?: (initial.toLong() and 0xFFFFFFFFL)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = it.trim().uppercase().take(7) },
                singleLine = true,
                label = { Text(stringResource(R.string.sidebar_palette_hex)) },
                isError = color == null,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Characters,
                ),
                leadingIcon = { Box(Modifier.size(24.dp).clip(CircleShape).background(Color(resolved.toInt()))) },
                trailingIcon = {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(hex)) }) {
                        Text(stringResource(R.string.rain_color_copy))
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { picker = !picker }) {
                    Text(stringResource(if (picker) R.string.rain_color_picker_hide else R.string.rain_color_picker_show))
                }
                TextButton(onClick = {
                    clipboard.getText()?.text?.let { pasted ->
                        // Accept only a complete #RRGGBB value from the clipboard.
                        val candidate = pasted.trim().uppercase()
                        if (candidate.startsWith("#") && candidate.length == 7) hex = candidate
                    }
                }) { Text(stringResource(R.string.rain_color_paste)) }
            }
            if (picker) {
                RainHsvPicker(resolved) { picked -> hex = formatSidebarRgb(picked) }
            }
            TextButton(onClick = { onApply(null) }) { Text(stringResource(R.string.rain_color_default)) }
        }
    }, confirmButton = {
        TextButton(enabled = color != null, onClick = { color?.let { onApply(it.toInt()) } }) {
            Text(stringResource(android.R.string.ok))
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun IconButtonCompat(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Hue strip, saturation strip and a live swatch. Slider thumbs keep 48dp touch targets at
 * any font scale, and every change is emitted as a complete opaque color.
 */
@Composable
private fun RainHsvPicker(color: Long, onChange: (Long) -> Unit) {
    val initial = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) } }
    var hue by remember(color) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(color) { mutableFloatStateOf(initial[1].coerceAtLeast(0.15f)) }
    var brightness by remember(color) { mutableFloatStateOf(initial[2].coerceAtLeast(0.15f)) }

    fun emit() {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        onChange(rgb.toLong() and 0xFFFFFFFFL)
    }

    val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))
            .background(Color(current)), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(1.dp))
        }
        RainSlider(
            stringResource(R.string.rain_color_hue),
            hue / 360f, 0f..1f, percent = false,
        ) { value -> hue = value * 360f; emit() }
        RainSlider(stringResource(R.string.rain_color_saturation), saturation, 0f..1f) { value ->
            saturation = value; emit()
        }
        RainSlider(stringResource(R.string.rain_color_brightness), brightness, 0.15f..1f) { value ->
            brightness = value; emit()
        }
    }
}
