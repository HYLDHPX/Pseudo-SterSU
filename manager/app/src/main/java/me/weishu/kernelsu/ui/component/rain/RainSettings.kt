package me.weishu.kernelsu.ui.component.rain

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.InterfaceStyle

internal const val RAIN_SETTINGS_PREFIX = "rain_v2_"
internal const val RAIN_LAYOUT_KEY = "${RAIN_SETTINGS_PREFIX}layout"

enum class RainLayout(val value: String, val interfaceStyle: String) {
    Miuix("miuix", InterfaceStyle.Miuix.value),
    Material("material", InterfaceStyle.Material.value),
    Sidebar("sidebar", InterfaceStyle.SidebarWidget.value);

    companion object {
        fun fromValue(value: String?): RainLayout = entries.firstOrNull { it.value == value } ?: Sidebar
    }
}

enum class RainQuality(val value: String, val drops: Int) {
    Low("low", 24), Standard("standard", 48), High("high", 72);

    companion object {
        fun fromValue(value: String?): RainQuality = entries.firstOrNull { it.value == value } ?: Standard
    }
}

/** Stroke silhouette of a rain streak; every option stays a single draw call. */
enum class RainDropForm(val value: String, val lengthScale: Float, val strokeScale: Float) {
    Line("line", 1f, 1f),
    Needle("needle", 1.6f, 0.62f),
    Bead("bead", 0.34f, 2.1f);

    companion object {
        fun fromValue(value: String?): RainDropForm = entries.firstOrNull { it.value == value } ?: Line
    }
}

/** Edge ornaments shared by cards and page chrome. */
enum class RainDecoration(val value: String, val animated: Boolean = false) {
    Off("off"), Wet("wet"), Dripping("dripping", true), Ripples("ripples", true), Streams("streams", true);

    companion object {
        fun fromValue(value: String?): RainDecoration = entries.firstOrNull { it.value == value } ?: Wet
    }
}

enum class RainCardBorder(val value: String, @StringRes val labelRes: Int) {
    Off("off", R.string.rain_decoration_none),
    Outline("outline", R.string.rain_card_border_outline),
    Double("double", R.string.rain_card_border_double),
    Dashed("dashed", R.string.rain_card_border_dashed);

    companion object {
        fun fromValue(value: String?): RainCardBorder = entries.firstOrNull { it.value == value } ?: Double
    }
}

enum class RainCardInterior(val value: String, @StringRes val labelRes: Int) {
    Off("off", R.string.rain_decoration_none),
    Mist("mist", R.string.rain_card_interior_mist),
    Streaks("streaks", R.string.rain_card_interior_streaks),
    Ripples("ripples", R.string.rain_decoration_ripples);

    companion object {
        fun fromValue(value: String?): RainCardInterior = entries.firstOrNull { it.value == value } ?: Mist
    }
}

/** Ambient rain audio; intensity always follows the current rain scene. */
enum class RainSound(val value: String, @StringRes val labelRes: Int) {
    Off("off", R.string.rain_sound_off),
    Whisper("whisper", R.string.rain_sound_whisper),
    Shower("shower", R.string.rain_sound_shower),
    Downpour("downpour", R.string.rain_sound_downpour);

    companion object {
        fun fromValue(value: String?): RainSound = entries.firstOrNull { it.value == value } ?: Off
    }
}

data class RainSettings(
    val active: Boolean = false,
    val layout: RainLayout = RainLayout.Sidebar,
    val scene: RainStyle = RainStyle.LightRain,
    val motion: Boolean = true,
    val quality: RainQuality = RainQuality.Standard,
    val intensity: Float = 0.45f,
    val speed: Float = 1f,
    val wind: Float = 0.12f,
    val surfaceOpacity: Float = 0.94f,
    val topBarOpacity: Float = 0.96f,
    val navigationOpacity: Float = 0.94f,
    val sidebarOpacity: Float = 0.90f,
    val sidebarDivider: Boolean = true,
    val miuixSidebar: Boolean = false,
    val lightAccent: Int? = null,
    val darkAccent: Int? = null,
    // Scene shape controls. Null keeps the per-scene default so new options never
    // depend on the scene the user happened to save first.
    val dropForm: RainDropForm = RainDropForm.Line,
    val dropLength: Float? = null,
    val dropOpacity: Float? = null,
    val atmosphere: Boolean = false,
    val atmosphereFollowsWeather: Boolean = false,
    val cardSurface: RainDecoration = RainDecoration.Wet,
    val cardBorder: RainCardBorder = RainCardBorder.Double,
    val cardInterior: RainCardInterior = RainCardInterior.Mist,
    val topBarDecoration: RainDecoration = RainDecoration.Wet,
    val navigationDecoration: RainDecoration = RainDecoration.Ripples,
    val sidebarDecoration: RainDecoration = RainDecoration.Dripping,
    val decorationOpacity: Float = 0.65f,
    val cardEdgeStrength: Float = 1f,
    val cardInteriorStrength: Float = 1f,
    val topBarStrength: Float = 1f,
    val navigationStrength: Float = 1f,
    val sidebarStrength: Float = 1f,
    val rainThroughUi: Boolean = true,
    val foregroundOpacity: Float = 0.65f,
    val surfaceInteraction: Boolean = true,
    val thunder: Boolean = true,
    val thunderInterval: Float = 18f,
    val sound: RainSound = RainSound.Off,
    val soundVolume: Float = 0.45f,
    val wallpaperRain: Boolean = false,
    val soundWithWallpaper: Boolean = false,
) {
    fun normalized(): RainSettings = copy(
        intensity = intensity.valid(0f, 1f, 0.45f),
        speed = speed.valid(0.4f, 2f, 1f),
        wind = wind.valid(-0.5f, 0.5f, 0.12f),
        surfaceOpacity = surfaceOpacity.valid(0.35f, 1f, 0.94f),
        topBarOpacity = topBarOpacity.valid(0.35f, 1f, 0.96f),
        navigationOpacity = navigationOpacity.valid(0.35f, 1f, 0.94f),
        sidebarOpacity = sidebarOpacity.valid(0f, 1f, 0.90f),
        lightAccent = lightAccent?.or(OPAQUE_ALPHA),
        darkAccent = darkAccent?.or(OPAQUE_ALPHA),
        dropLength = dropLength?.valid(0.4f, 2f, 1f),
        dropOpacity = dropOpacity?.valid(0.2f, 1f, 1f),
        soundVolume = soundVolume.valid(0f, 1f, 0.45f),
        decorationOpacity = decorationOpacity.valid(0f, 1f, 0.65f),
        cardEdgeStrength = cardEdgeStrength.valid(0f, 1f, 1f),
        cardInteriorStrength = cardInteriorStrength.valid(0f, 1f, 1f),
        topBarStrength = topBarStrength.valid(0f, 1f, 1f),
        navigationStrength = navigationStrength.valid(0f, 1f, 1f),
        sidebarStrength = sidebarStrength.valid(0f, 1f, 1f),
        thunderInterval = thunderInterval.valid(8f, 60f, 18f),
        foregroundOpacity = foregroundOpacity.valid(0f, 1f, 0.65f),
    )

    /** A wallpaper only opts into rain when the existing wallpaper-rain switch allows it. */
    fun drawingEnabled(hasCustomBackground: Boolean): Boolean = active && (!hasCustomBackground || wallpaperRain)

    fun foregroundDrawingEnabled(hasCustomBackground: Boolean): Boolean = drawingEnabled(hasCustomBackground) &&
        rainThroughUi && foregroundOpacity > 0f && intensity > 0f

    /** Leave a window onto background rain without fading foreground content or rewriting preferences. */
    fun backgroundSurfaceOpacity(requested: Float): Float =
        if (active && !rainThroughUi && intensity > 0f) minOf(requested, 0.58f) else requested

    fun decorationMotionEnabled(): Boolean = active && motion && decorationOpacity > 0f && (
        cardEdgeStrength > 0f && (cardSurface != RainDecoration.Off || cardBorder != RainCardBorder.Off) ||
        cardInteriorStrength > 0f && cardInterior != RainCardInterior.Off ||
        topBarStrength > 0f && topBarDecoration != RainDecoration.Off ||
        navigationStrength > 0f && navigationDecoration != RainDecoration.Off ||
        sidebarStrength > 0f && sidebarDecoration != RainDecoration.Off)
}

internal fun RainSettings.edgeStrength(kind: RainSurfaceKind): Float = decorationOpacity * when (kind) {
    RainSurfaceKind.Card -> cardEdgeStrength
    RainSurfaceKind.TopBar -> topBarStrength
    RainSurfaceKind.Navigation -> navigationStrength
    RainSurfaceKind.Sidebar -> sidebarStrength
}

internal fun RainSettings.interiorStrength(): Float = decorationOpacity * cardInteriorStrength

internal fun RainSettings.hasEdgeDecoration(kind: RainSurfaceKind): Boolean = when (kind) {
    RainSurfaceKind.Card -> cardSurface != RainDecoration.Off || cardBorder != RainCardBorder.Off
    RainSurfaceKind.TopBar -> topBarDecoration != RainDecoration.Off
    RainSurfaceKind.Navigation -> navigationDecoration != RainDecoration.Off
    RainSurfaceKind.Sidebar -> sidebarDecoration != RainDecoration.Off
}

private const val OPAQUE_ALPHA = 0xFF000000.toInt()

private fun Float.valid(min: Float, max: Float, fallback: Float): Float =
    if (isFinite()) coerceIn(min, max) else fallback

val LocalRainSettings = staticCompositionLocalOf { RainSettings() }

/** True when a custom wallpaper replaces the rain backdrop, so rain must stop drawing. */
val LocalRainCoveredByWallpaper = staticCompositionLocalOf { false }

fun resolveRainLayoutStyle(interfaceStyle: String, settings: RainSettings): String =
    if (interfaceStyle == InterfaceStyle.Rain.value) settings.layout.interfaceStyle else interfaceStyle

internal fun rainSettingKey(layout: RainLayout, field: String): String =
    "$RAIN_SETTINGS_PREFIX${layout.value}_$field"

internal fun rainPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)

internal fun readRainSettings(preferences: SharedPreferences, active: Boolean = true, requestedLayout: RainLayout? = null): RainSettings {
    fun text(key: String): String? = runCatching { preferences.getString(key, null) }.getOrNull()
    val layout = requestedLayout ?: RainLayout.fromValue(text(RAIN_LAYOUT_KEY))
    fun key(field: String) = rainSettingKey(layout, field)
    fun number(field: String, fallback: Float) = runCatching { preferences.getFloat(key(field), fallback) }.getOrDefault(fallback)
    fun optionalNumber(field: String): Float? = if (preferences.contains(key(field)))
        number(field, 0f).takeIf { it.isFinite() } else null
    fun flag(field: String, fallback: Boolean) = runCatching { preferences.getBoolean(key(field), fallback) }.getOrDefault(fallback)
    fun color(field: String): Int? = if (preferences.contains(key(field)))
        runCatching { preferences.getInt(key(field), 0) }.getOrNull() else null
    return RainSettings(
        active = active,
        layout = layout,
        scene = RainStyle.fromValue(text(key("scene")) ?: text(RAIN_STYLE_KEY)),
        motion = flag("motion", true),
        quality = RainQuality.fromValue(text(key("quality"))),
        intensity = number("intensity", 0.45f),
        speed = number("speed", 1f),
        wind = number("wind", 0.12f),
        surfaceOpacity = number("surface", 0.94f),
        topBarOpacity = number("top_bar", 0.96f),
        navigationOpacity = number("navigation", 0.94f),
        sidebarOpacity = number("sidebar", 0.90f),
        sidebarDivider = flag("divider", true),
        miuixSidebar = flag("miuix_sidebar", false),
        lightAccent = color("light_accent"),
        darkAccent = color("dark_accent"),
        dropForm = RainDropForm.fromValue(text(key("drop_form"))),
        dropLength = optionalNumber("drop_length"),
        dropOpacity = optionalNumber("drop_opacity"),
        atmosphere = flag("atmosphere", false),
        atmosphereFollowsWeather = flag("atmosphere_weather", false),
        cardSurface = RainDecoration.fromValue(text(key("card_surface"))),
        cardBorder = RainCardBorder.fromValue(text(key("card_border"))),
        cardInterior = RainCardInterior.fromValue(text(key("card_interior"))),
        topBarDecoration = RainDecoration.fromValue(text(key("top_bar_decoration"))),
        navigationDecoration = RainDecoration.fromValue(text(key("navigation_decoration")) ?: RainDecoration.Ripples.value),
        sidebarDecoration = RainDecoration.fromValue(text(key("sidebar_decoration")) ?: RainDecoration.Dripping.value),
        decorationOpacity = number("decoration_opacity", 0.65f),
        cardEdgeStrength = number("card_edge_strength", 1f),
        cardInteriorStrength = number("card_interior_strength", 1f),
        topBarStrength = number("top_bar_strength", 1f),
        navigationStrength = number("navigation_strength", 1f),
        sidebarStrength = number("sidebar_strength", 1f),
        rainThroughUi = flag("rain_through_ui", true),
        foregroundOpacity = number("foreground_opacity", 0.65f),
        surfaceInteraction = flag("surface_interaction", true),
        thunder = flag("thunder", true),
        thunderInterval = number("thunder_interval", 18f),
        sound = RainSound.fromValue(text(key("sound"))),
        soundVolume = number("sound_volume", 0.45f),
        wallpaperRain = flag("wallpaper_rain", false),
        soundWithWallpaper = flag("wallpaper_sound", false),
    ).normalized()
}

internal fun updateRainSettings(context: Context, transform: (RainSettings) -> RainSettings) {
    val preferences = rainPreferences(context)
    val settings = transform(readRainSettings(preferences)).normalized()
    preferences.edit {
        putRainSettings(settings)
        putString(RAIN_LAYOUT_KEY, settings.layout.value)
    }
}

internal fun SharedPreferences.Editor.putRainSettings(settings: RainSettings): SharedPreferences.Editor = apply {
    fun key(field: String) = rainSettingKey(settings.layout, field)
    putString(key("scene"), settings.scene.value)
    putBoolean(key("motion"), settings.motion)
    putString(key("quality"), settings.quality.value)
    putFloat(key("intensity"), settings.intensity)
    putFloat(key("speed"), settings.speed)
    putFloat(key("wind"), settings.wind)
    putFloat(key("surface"), settings.surfaceOpacity)
    putFloat(key("top_bar"), settings.topBarOpacity)
    putFloat(key("navigation"), settings.navigationOpacity)
    putFloat(key("sidebar"), settings.sidebarOpacity)
    putBoolean(key("divider"), settings.sidebarDivider)
    putBoolean(key("miuix_sidebar"), settings.miuixSidebar)
    putString(key("drop_form"), settings.dropForm.value)
    settings.dropLength?.let { putFloat(key("drop_length"), it) } ?: remove(key("drop_length"))
    settings.dropOpacity?.let { putFloat(key("drop_opacity"), it) } ?: remove(key("drop_opacity"))
    putBoolean(key("atmosphere"), settings.atmosphere)
    putBoolean(key("atmosphere_weather"), settings.atmosphereFollowsWeather)
    putString(key("card_surface"), settings.cardSurface.value)
    putString(key("card_border"), settings.cardBorder.value)
    putString(key("card_interior"), settings.cardInterior.value)
    putString(key("top_bar_decoration"), settings.topBarDecoration.value)
    putString(key("navigation_decoration"), settings.navigationDecoration.value)
    putString(key("sidebar_decoration"), settings.sidebarDecoration.value)
    putFloat(key("decoration_opacity"), settings.decorationOpacity)
    putFloat(key("card_edge_strength"), settings.cardEdgeStrength)
    putFloat(key("card_interior_strength"), settings.cardInteriorStrength)
    putFloat(key("top_bar_strength"), settings.topBarStrength)
    putFloat(key("navigation_strength"), settings.navigationStrength)
    putFloat(key("sidebar_strength"), settings.sidebarStrength)
    putBoolean(key("rain_through_ui"), settings.rainThroughUi)
    putFloat(key("foreground_opacity"), settings.foregroundOpacity)
    putBoolean(key("surface_interaction"), settings.surfaceInteraction)
    putBoolean(key("thunder"), settings.thunder)
    putFloat(key("thunder_interval"), settings.thunderInterval)
    remove(key("auto_power_save"))
    putString(key("sound"), settings.sound.value)
    putFloat(key("sound_volume"), settings.soundVolume)
    putBoolean(key("wallpaper_rain"), settings.wallpaperRain)
    putBoolean(key("wallpaper_sound"), settings.soundWithWallpaper)
    settings.lightAccent?.let { putInt(key("light_accent"), it) } ?: remove(key("light_accent"))
    settings.darkAccent?.let { putInt(key("dark_accent"), it) } ?: remove(key("dark_accent"))
}

internal fun selectRainLayout(context: Context, layout: RainLayout) {
    rainPreferences(context).edit { putString(RAIN_LAYOUT_KEY, layout.value) }
}

@Composable
fun rememberRainSettings(active: Boolean = true): RainSettings {
    val context = LocalContext.current
    val preferences = remember(context) { rainPreferences(context) }
    var settings by remember(preferences, active) { mutableStateOf(readRainSettings(preferences, active)) }
    DisposableEffect(preferences, active) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(RAIN_SETTINGS_PREFIX) || key == RAIN_STYLE_KEY) {
                settings = readRainSettings(preferences, active)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
