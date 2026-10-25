package me.weishu.kernelsu.ui.component.rain

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal const val RAIN_PRESETS_KEY = "${RAIN_SETTINGS_PREFIX}presets"
internal const val MAX_RAIN_PRESETS = 12
internal data class RainPreset(val id: String, val name: String, val settings: RainSettings)

internal fun rainPresetName(value: String): String = value.trim().filterNot(Char::isISOControl).take(32)

internal fun readRainPresets(preferences: SharedPreferences): List<RainPreset> = runCatching {
    val raw = preferences.getString(RAIN_PRESETS_KEY, null) ?: return emptyList()
    if (raw.length > 256_000) return emptyList()
    val array = JSONArray(raw)
    (0 until minOf(array.length(), MAX_RAIN_PRESETS)).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            val layout = RainLayout.entries.first { it.value == item.getString("layout") }
            val id = item.getString("id").also { require(it.matches(Regex("[A-Za-z0-9-]{1,64}"))) }
            val name = rainPresetName(item.getString("name")).also { require(it.isNotEmpty()) }
            RainPreset(id, name, parseRainSettings(item.getJSONObject("profile"), layout))
        }.getOrNull()
    }.distinctBy { it.id }
}.getOrDefault(emptyList())

private fun writeRainPresets(preferences: SharedPreferences, presets: List<RainPreset>) {
    val array = JSONArray()
    presets.take(MAX_RAIN_PRESETS).forEach { preset ->
        array.put(JSONObject().put("id", preset.id).put("name", preset.name)
            .put("layout", preset.settings.layout.value).put("profile", preset.settings.toRainJson()))
    }
    preferences.edit { putString(RAIN_PRESETS_KEY, array.toString()) }
}

/** A repeated name updates its preset; the active layout is never changed by saving. */
internal fun saveRainPreset(preferences: SharedPreferences, name: String): Boolean {
    val safeName = rainPresetName(name)
    if (safeName.isEmpty()) return false
    val presets = readRainPresets(preferences).toMutableList()
    val index = presets.indexOfFirst { it.name == safeName }
    if (index < 0 && presets.size >= MAX_RAIN_PRESETS) return false
    val preset = RainPreset(if (index < 0) UUID.randomUUID().toString() else presets[index].id,
        safeName, readRainSettings(preferences))
    if (index < 0) presets.add(preset) else presets[index] = preset
    writeRainPresets(preferences, presets)
    return true
}

internal fun deleteRainPreset(preferences: SharedPreferences, id: String) =
    writeRainPresets(preferences, readRainPresets(preferences).filterNot { it.id == id })

internal fun applyRainPreset(preferences: SharedPreferences, preset: RainPreset) {
    val current = readRainSettings(preferences)
    preferences.edit { putRainSettings(preset.settings.copy(layout = current.layout).normalized()) }
}

/** Copies into an inactive profile without navigating or losing the current selection. */
internal fun copyRainProfile(preferences: SharedPreferences, source: RainLayout, target: RainLayout) {
    if (source == target) return
    preferences.edit { putRainSettings(readRainSettings(preferences, requestedLayout = source).copy(layout = target)) }
}

@Composable
internal fun rememberRainPresets(): List<RainPreset> {
    val context = LocalContext.current
    val preferences = remember(context) { rainPreferences(context) }
    var presets by remember(preferences) { mutableStateOf(readRainPresets(preferences)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RAIN_PRESETS_KEY || key == null) presets = readRainPresets(preferences)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return presets
}
