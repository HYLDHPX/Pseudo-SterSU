package me.weishu.kernelsu.ui.util

import android.content.Context
import android.content.SharedPreferences
import me.weishu.kernelsu.data.repository.ACTIVE_INTERFACE_STYLE_ID_KEY
import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.pixel.PIXEL_STYLE_KEY
import me.weishu.kernelsu.ui.component.pixel.PixelStyle
import me.weishu.kernelsu.ui.component.rain.RAIN_STYLE_KEY
import me.weishu.kernelsu.ui.component.rain.RainStyle
import me.weishu.kernelsu.ui.component.snow.SEASON_STYLE_KEY
import me.weishu.kernelsu.ui.component.snow.SeasonStyle
import org.json.JSONObject

internal data class ThemeStoreInterfaceSelection(
    val mode: String,
    val variant: String?,
    val archive: SharedInterfaceStyleArchive? = null,
) {
    val engine: String get() = if (mode == InterfaceStyle.Delta.value) InterfaceStyle.Alpha.value else mode
}

internal fun exportThemeStoreInterfaceSelection(
    context: Context,
    prefs: SharedPreferences,
    asset: (String, ByteArray) -> JSONObject,
): JSONObject {
    val mode = InterfaceStyle.normalizeValue(prefs.getString("ui_mode", UiMode.DEFAULT_VALUE))
    val variant = when (mode) {
        InterfaceStyle.Snow.value -> SeasonStyle.fromValue(prefs.getString(SEASON_STYLE_KEY, null)).value
        InterfaceStyle.Rain.value -> RainStyle.fromValue(prefs.getString(RAIN_STYLE_KEY, null)).value
        InterfaceStyle.Pixel.value -> PixelStyle.fromValue(prefs.getString(PIXEL_STYLE_KEY, null)).value
        else -> null
    }
    val selection = ThemeStoreInterfaceSelection(mode, variant)
    return JSONObject().put("mode", mode).put("variant", variant).apply {
        if (InterfaceStyle.builtInEntries.none { it.value == mode }) {
            val preferredId = if (mode == InterfaceStyle.Delta.value) "alpha-delta"
                else prefs.getString(ACTIVE_INTERFACE_STYLE_ID_KEY, null)
            val shared = readSharedInterfaceStyleArchive(context, selection.engine, variant, preferredId)
            put("package", JSONObject().put("id", shared.style.id)
                .put("asset", asset("interface_style.ksstyle", shared.bytes)).apply {
                    shared.catalog?.let { put("catalogAsset", asset("interface_catalog.json", it)) }
                    shared.signature?.let { put("signatureAsset", asset("interface_catalog.sig", it)) }
                })
        }
    }
}

internal fun validateThemeStoreInterfaceSelection(json: JSONObject) {
    require(json.keys().asSequence().all { it in setOf("mode", "variant", "package") }) {
        "Unknown interface style setting"
    }
    val mode = json.getString("mode")
    require(InterfaceStyle.entries.any { it.value == mode }) { "Invalid interface style" }
    val variant = json.optString("variant").takeUnless { it.isBlank() || it == "null" }
    val validVariant = when (mode) {
        InterfaceStyle.Snow.value -> SeasonStyle.entries.any { it.value == variant }
        InterfaceStyle.Rain.value -> RainStyle.entries.any { it.value == variant }
        InterfaceStyle.Pixel.value -> PixelStyle.entries.any { it.value == variant }
        else -> variant == null
    }
    require(validVariant) { "Invalid interface style variant" }
    val builtIn = InterfaceStyle.builtInEntries.any { it.value == mode }
    val owner = json.optJSONObject("package")
    require(if (builtIn) !json.has("package") else owner != null) { "Interface style package is missing or unexpected" }
    owner?.let {
        require(it.keys().asSequence().all { key -> key in setOf("id", "asset", "catalogAsset", "signatureAsset") } &&
            it.opt("id") is String && it.opt("asset") is JSONObject &&
            it.has("catalogAsset") == it.has("signatureAsset")) { "Invalid interface style package metadata" }
    }
}

internal fun importThemeStoreInterfaceSelection(
    json: JSONObject,
    asset: (JSONObject, String, Long) -> ByteArray,
): ThemeStoreInterfaceSelection {
    validateThemeStoreInterfaceSelection(json)
    val mode = json.getString("mode")
    val variant = json.optString("variant").takeUnless { it.isBlank() || it == "null" }
    val owner = json.optJSONObject("package")
    val archive = owner?.let {
        verifySharedInterfaceStyleArchive(
            id = it.getString("id"),
            bytes = asset(it, "asset", 8L * 1024 * 1024),
            catalog = if (it.has("catalogAsset")) asset(it, "catalogAsset", 512L * 1024) else null,
            signature = if (it.has("signatureAsset")) asset(it, "signatureAsset", 4L * 1024) else null,
        )
    }
    return ThemeStoreInterfaceSelection(mode, variant, archive).also { selection ->
        if (archive != null) {
            require(archive.style.engine == selection.engine && archive.style.variant == selection.variant &&
                (mode != InterfaceStyle.Delta.value || archive.style.id == "alpha-delta")) {
                "Shared interface style does not match the selected renderer"
            }
        }
    }
}

internal fun SharedPreferences.Editor.putThemeStoreInterfaceSelection(selection: ThemeStoreInterfaceSelection) {
    putString("ui_mode", selection.mode)
    val styleId = selection.archive?.style?.id
    if (styleId == null) remove(ACTIVE_INTERFACE_STYLE_ID_KEY) else putString(ACTIVE_INTERFACE_STYLE_ID_KEY, styleId)
    val variant = selection.variant ?: return
    val key = when (selection.mode) {
        InterfaceStyle.Snow.value -> SEASON_STYLE_KEY
        InterfaceStyle.Rain.value -> RAIN_STYLE_KEY
        InterfaceStyle.Pixel.value -> PIXEL_STYLE_KEY
        else -> return
    }
    putString(key, variant)
}
