package me.weishu.kernelsu.ui.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.json.JSONObject

@Immutable
data class UiPassthroughSettings(
    val enabled: Boolean = false,
    val surfaceOpacity: Float = 0.65f,
    val topBarOpacity: Float = 0f,
    val bottomBarOpacity: Float = 0.65f,
    val textOpacity: Float = 1f,
    val iconOpacity: Float = 1f,
    val imageOpacity: Float = 1f,
) {
    fun normalized(): UiPassthroughSettings = copy(
        surfaceOpacity = sanitizeUiOpacity(surfaceOpacity, 0.65f),
        topBarOpacity = sanitizeUiOpacity(topBarOpacity, 0f),
        bottomBarOpacity = sanitizeUiOpacity(bottomBarOpacity, 0.65f),
        textOpacity = sanitizeUiOpacity(textOpacity, 1f).coerceAtLeast(0.2f),
        iconOpacity = sanitizeUiOpacity(iconOpacity, 1f).coerceAtLeast(0.2f),
        imageOpacity = sanitizeUiOpacity(imageOpacity, 1f),
    )
}

internal fun sanitizeUiOpacity(value: Float, fallback: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

data class UiPassthroughScope(
    val settings: UiPassthroughSettings,
    val overridesGlobal: Boolean = false,
)

private fun prefix(target: CustomPageBackgroundTarget?): String =
    "ui_passthrough_${target?.id ?: "global"}_"

internal fun SharedPreferences.readUiPassthroughScope(target: CustomPageBackgroundTarget?): UiPassthroughScope {
    val key = prefix(target)
    val global = if (target == null) {
        // Migrate the old wallpaper overlay without continuing to cover the foreground.
        UiPassthroughSettings(
            enabled = getBoolean(CUSTOM_WALLPAPER_PASSTHROUGH_ENABLED_KEY, false),
            surfaceOpacity = 1f - sanitizeCustomWallpaperPassthroughOpacity(
                getFloat(CUSTOM_WALLPAPER_PASSTHROUGH_OPACITY_KEY, DEFAULT_CUSTOM_WALLPAPER_PASSTHROUGH_OPACITY),
            ),
        )
    } else readUiPassthroughScope(null).settings
    val overrides = target == null || getBoolean(key + "override", false)
    if (!overrides) return UiPassthroughScope(global)
    return UiPassthroughScope(
        settings = UiPassthroughSettings(
            enabled = getBoolean(key + "enabled", global.enabled),
            surfaceOpacity = getFloat(key + "surface", global.surfaceOpacity),
            topBarOpacity = getFloat(key + "top", global.topBarOpacity),
            bottomBarOpacity = getFloat(key + "bottom", global.bottomBarOpacity),
            textOpacity = getFloat(key + "text", global.textOpacity),
            iconOpacity = getFloat(key + "icon", global.iconOpacity),
            imageOpacity = getFloat(key + "image", global.imageOpacity),
        ).normalized(),
        overridesGlobal = target != null,
    )
}

fun writeUiPassthroughScope(
    context: Context,
    target: CustomPageBackgroundTarget?,
    settings: UiPassthroughSettings,
    overridesGlobal: Boolean = true,
) {
    context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
        putUiPassthroughScope(target, settings, overridesGlobal)
    }
}

internal fun SharedPreferences.Editor.putUiPassthroughScope(
    target: CustomPageBackgroundTarget?,
    settings: UiPassthroughSettings,
    overridesGlobal: Boolean = true,
): SharedPreferences.Editor = apply {
    val value = settings.normalized()
    val key = prefix(target)
    putBoolean(key + "override", overridesGlobal)
    putBoolean(key + "enabled", value.enabled)
    putFloat(key + "surface", value.surfaceOpacity)
    putFloat(key + "top", value.topBarOpacity)
    putFloat(key + "bottom", value.bottomBarOpacity)
    putFloat(key + "text", value.textOpacity)
    putFloat(key + "icon", value.iconOpacity)
    putFloat(key + "image", value.imageOpacity)
    if (target == null) {
        // Keep legacy readers synchronized without painting over the foreground.
        putBoolean(CUSTOM_WALLPAPER_PASSTHROUGH_ENABLED_KEY, value.enabled)
        putFloat(CUSTOM_WALLPAPER_PASSTHROUGH_OPACITY_KEY, 1f - value.surfaceOpacity)
    }
}

internal fun UiPassthroughScope.toJson(): JSONObject = settings.normalized().let { value ->
    JSONObject().put("override", overridesGlobal).put("enabled", value.enabled)
        .put("surface", value.surfaceOpacity).put("top", value.topBarOpacity)
        .put("bottom", value.bottomBarOpacity).put("text", value.textOpacity)
        .put("icon", value.iconOpacity).put("image", value.imageOpacity)
}

internal fun uiPassthroughScopeFromJson(json: JSONObject): UiPassthroughScope = UiPassthroughScope(
    settings = UiPassthroughSettings(
        enabled = json.getBoolean("enabled"),
        surfaceOpacity = json.getDouble("surface").toFloat(),
        topBarOpacity = json.getDouble("top").toFloat(),
        bottomBarOpacity = json.getDouble("bottom").toFloat(),
        textOpacity = json.getDouble("text").toFloat(),
        iconOpacity = json.getDouble("icon").toFloat(),
        imageOpacity = json.getDouble("image").toFloat(),
    ).normalized(),
    overridesGlobal = json.getBoolean("override"),
)

internal fun SharedPreferences.exportUiPassthrough(): JSONObject = JSONObject().also { json ->
    json.put("global", readUiPassthroughScope(null).toJson())
    CustomPageBackgroundTarget.entries.forEach { target ->
        json.put(target.id, readUiPassthroughScope(target).toJson())
    }
}

internal fun validateUiPassthroughPackage(json: JSONObject) {
    val allowed = CustomPageBackgroundTarget.entries.map { it.id }.toSet() + "global"
    require(json.has("global")) { "UI passthrough settings are missing the global scope" }
    json.keys().forEach { key ->
        require(key in allowed) { "Unknown UI passthrough scope: $key" }
        val scope = json.getJSONObject(key)
        val numbers = setOf("surface", "top", "bottom", "text", "icon", "image")
        require(scope.keys().asSequence().all { it in numbers || it == "override" || it == "enabled" }) {
            "Unknown UI passthrough setting"
        }
        require(scope.opt("enabled") is Boolean && scope.opt("override") is Boolean) { "Invalid UI passthrough switches" }
        numbers.forEach { name ->
            val value = scope.opt(name)
            require(value is Number && value.toDouble().isFinite() && value.toDouble() in 0.0..1.0) {
                "Invalid UI passthrough opacity: $name"
            }
        }
    }
}

internal fun SharedPreferences.Editor.putUiPassthroughPackage(json: JSONObject) {
    validateUiPassthroughPackage(json)
    val global = uiPassthroughScopeFromJson(json.getJSONObject("global"))
    putUiPassthroughScope(null, global.settings)
    CustomPageBackgroundTarget.entries.forEach { target ->
        val scope = json.optJSONObject(target.id)?.let(::uiPassthroughScopeFromJson)
        putUiPassthroughScope(target, scope?.settings ?: global.settings, scope?.overridesGlobal ?: false)
    }
}

@Composable
fun rememberUiPassthroughScope(target: CustomPageBackgroundTarget?): UiPassthroughScope {
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var state by remember(prefs, target) { mutableStateOf(prefs.readUiPassthroughScope(target)) }
    DisposableEffect(prefs, target) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("ui_passthrough_") ||
                key == CUSTOM_WALLPAPER_PASSTHROUGH_ENABLED_KEY || key == CUSTOM_WALLPAPER_PASSTHROUGH_OPACITY_KEY) {
                state = prefs.readUiPassthroughScope(target)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        state = prefs.readUiPassthroughScope(target)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
