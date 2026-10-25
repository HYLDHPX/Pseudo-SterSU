package me.weishu.kernelsu.ui.component.rain

import android.content.SharedPreferences
import org.json.JSONObject

internal data class SharedRainConfiguration(val layout: RainLayout, val profiles: Map<RainLayout, RainSettings>)

internal fun exportRainConfiguration(preferences: SharedPreferences): JSONObject = JSONObject()
    .put("layout", readRainSettings(preferences).layout.value)
    .put("profiles", JSONObject().apply {
        RainLayout.entries.forEach { layout ->
            put(layout.value, readRainSettings(preferences, requestedLayout = layout).toRainJson())
        }
    })

internal fun RainSettings.toRainJson(): JSONObject = JSONObject()
    .put("scene", scene.value).put("motion", motion).put("quality", quality.value)
    .put("intensity", intensity).put("speed", speed).put("wind", wind)
    .put("surface", surfaceOpacity).put("top_bar", topBarOpacity).put("navigation", navigationOpacity)
    .put("sidebar", sidebarOpacity).put("divider", sidebarDivider).put("miuix_sidebar", miuixSidebar)
    .put("drop_form", dropForm.value).put("card_surface", cardSurface.value)
    .put("card_border", cardBorder.value).put("card_interior", cardInterior.value)
    .put("top_bar_decoration", topBarDecoration.value).put("navigation_decoration", navigationDecoration.value)
    .put("sidebar_decoration", sidebarDecoration.value).put("decoration_opacity", decorationOpacity)
    .put("card_edge_strength", cardEdgeStrength).put("card_interior_strength", cardInteriorStrength)
    .put("top_bar_strength", topBarStrength).put("navigation_strength", navigationStrength)
    .put("sidebar_strength", sidebarStrength).put("thunder_interval", thunderInterval)
    .put("rain_through_ui", rainThroughUi).put("foreground_opacity", foregroundOpacity)
    .put("surface_interaction", surfaceInteraction).put("thunder", thunder)
    .put("atmosphere", atmosphere).put("atmosphere_weather", atmosphereFollowsWeather)
    .put("sound", sound.value).put("sound_volume", soundVolume)
    .put("wallpaper_rain", wallpaperRain).put("wallpaper_sound", soundWithWallpaper)
    .apply {
        dropLength?.let { put("drop_length", it) }
        dropOpacity?.let { put("drop_opacity", it) }
        lightAccent?.let { put("light_accent", it) }
        darkAccent?.let { put("dark_accent", it) }
    }

/**
 * Optional profile fields. A shared configuration written by an older build simply omits
 * them and keeps the defaults, so new knobs never reject previously exported themes.
 */
private val RAIN_OPTIONAL_FIELDS = setOf(
    "light_accent", "dark_accent", "drop_length", "drop_opacity",
    "drop_form", "card_surface", "atmosphere", "atmosphere_weather", "sound", "sound_volume",
    "wallpaper_rain", "wallpaper_sound", "auto_power_save",
    "top_bar_decoration", "navigation_decoration", "sidebar_decoration", "decoration_opacity",
    "card_border", "card_interior",
    "rain_through_ui", "foreground_opacity", "surface_interaction", "thunder",
    "card_edge_strength", "card_interior_strength", "top_bar_strength", "navigation_strength", "sidebar_strength", "thunder_interval",
)

private val RAIN_REQUIRED_FIELDS = setOf(
    "scene", "motion", "quality", "intensity", "speed", "wind",
    "surface", "top_bar", "navigation", "sidebar", "divider", "miuix_sidebar",
)

internal fun importRainConfiguration(json: JSONObject): SharedRainConfiguration {
    require(json.keys().asSequence().toSet() == setOf("layout", "profiles")) { "Invalid rain configuration" }
    val layout = RainLayout.entries.firstOrNull { it.value == json.getString("layout") }
        ?: error("Invalid rain layout")
    val profiles = json.getJSONObject("profiles")
    require(profiles.keys().asSequence().toSet() == RainLayout.entries.map { it.value }.toSet()) { "Invalid rain profiles" }
    return SharedRainConfiguration(layout, RainLayout.entries.associateWith { parseRainSettings(profiles.getJSONObject(it.value), it) })
}

internal fun parseRainSettings(json: JSONObject, layout: RainLayout): RainSettings {
    require(json.keys().asSequence().all { it in RAIN_REQUIRED_FIELDS || it in RAIN_OPTIONAL_FIELDS } &&
        RAIN_REQUIRED_FIELDS.all(json::has)) { "Invalid rain profile fields" }
    fun number(key: String, range: ClosedFloatingPointRange<Float>): Float {
        val raw = json.get(key)
        require(raw is Number && raw.toDouble().isFinite()) { "Invalid rain number" }
        return raw.toFloat().also { require(it in range) { "Rain value out of range" } }
    }
    fun flag(key: String, fallback: Boolean = false): Boolean =
        if (!json.has(key)) fallback else (json.get(key) as? Boolean) ?: error("Invalid rain flag")
    fun text(key: String, allowed: Set<String>, fallback: String): String {
        if (!json.has(key)) return fallback
        val raw = json.getString(key)
        require(raw in allowed) { "Invalid rain option" }
        return raw
    }
    fun color(key: String): Int? {
        if (!json.has(key)) return null
        val raw = json.get(key)
        require(raw is Number && raw.toDouble() == raw.toLong().toDouble() && raw.toLong() in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Invalid rain color" }
        return raw.toInt() or 0xFF000000.toInt()
    }
    // Accept the retired field in old themes, but never restore or export it.
    if (json.has("auto_power_save")) flag("auto_power_save")
    return RainSettings(
        active = true, layout = layout,
        scene = RainStyle.entries.firstOrNull { it.value == json.getString("scene") } ?: error("Invalid rain scene"),
        quality = RainQuality.entries.firstOrNull { it.value == json.getString("quality") } ?: error("Invalid rain quality"),
        motion = flag("motion"), intensity = number("intensity", 0f..1f), speed = number("speed", 0.4f..2f), wind = number("wind", -0.5f..0.5f),
        surfaceOpacity = number("surface", 0.35f..1f), topBarOpacity = number("top_bar", 0.35f..1f),
        navigationOpacity = number("navigation", 0.35f..1f), sidebarOpacity = number("sidebar", 0f..1f),
        sidebarDivider = flag("divider"), miuixSidebar = flag("miuix_sidebar"),
        lightAccent = color("light_accent"), darkAccent = color("dark_accent"),
        dropForm = RainDropForm.fromValue(
            text("drop_form", RainDropForm.entries.map { it.value }.toSet(), RainDropForm.Line.value),
        ),
        dropLength = if (json.has("drop_length")) number("drop_length", 0.4f..2f) else null,
        dropOpacity = if (json.has("drop_opacity")) number("drop_opacity", 0.2f..1f) else null,
        atmosphere = flag("atmosphere"),
        atmosphereFollowsWeather = flag("atmosphere_weather"),
        cardSurface = RainDecoration.fromValue(
            text("card_surface", RainDecoration.entries.map { it.value }.toSet(), RainDecoration.Wet.value),
        ),
        cardBorder = RainCardBorder.fromValue(text("card_border", RainCardBorder.entries.map { it.value }.toSet(), RainCardBorder.Double.value)),
        cardInterior = RainCardInterior.fromValue(text("card_interior", RainCardInterior.entries.map { it.value }.toSet(), RainCardInterior.Mist.value)),
        topBarDecoration = RainDecoration.fromValue(text("top_bar_decoration", RainDecoration.entries.map { it.value }.toSet(), RainDecoration.Wet.value)),
        navigationDecoration = RainDecoration.fromValue(text("navigation_decoration", RainDecoration.entries.map { it.value }.toSet(), RainDecoration.Ripples.value)),
        sidebarDecoration = RainDecoration.fromValue(text("sidebar_decoration", RainDecoration.entries.map { it.value }.toSet(), RainDecoration.Dripping.value)),
        decorationOpacity = if (json.has("decoration_opacity")) number("decoration_opacity", 0f..1f) else 0.65f,
        cardEdgeStrength = if (json.has("card_edge_strength")) number("card_edge_strength", 0f..1f) else 1f,
        cardInteriorStrength = if (json.has("card_interior_strength")) number("card_interior_strength", 0f..1f) else 1f,
        topBarStrength = if (json.has("top_bar_strength")) number("top_bar_strength", 0f..1f) else 1f,
        navigationStrength = if (json.has("navigation_strength")) number("navigation_strength", 0f..1f) else 1f,
        sidebarStrength = if (json.has("sidebar_strength")) number("sidebar_strength", 0f..1f) else 1f,
        thunderInterval = if (json.has("thunder_interval")) number("thunder_interval", 8f..60f) else 18f,
        rainThroughUi = flag("rain_through_ui", true),
        foregroundOpacity = if (json.has("foreground_opacity")) number("foreground_opacity", 0f..1f) else 0.65f,
        surfaceInteraction = flag("surface_interaction", true),
        thunder = flag("thunder", true),
        sound = RainSound.fromValue(
            text("sound", RainSound.entries.map { it.value }.toSet(), RainSound.Off.value),
        ),
        soundVolume = if (json.has("sound_volume")) number("sound_volume", 0f..1f) else 0.45f,
        wallpaperRain = flag("wallpaper_rain"), soundWithWallpaper = flag("wallpaper_sound"),
    ).normalized()
}

internal fun SharedPreferences.Editor.putRainConfiguration(configuration: SharedRainConfiguration): SharedPreferences.Editor = apply {
    configuration.profiles.values.forEach { putRainSettings(it) }
    putString(RAIN_LAYOUT_KEY, configuration.layout.value)
}
