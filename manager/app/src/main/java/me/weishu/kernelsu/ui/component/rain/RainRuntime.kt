package me.weishu.kernelsu.ui.component.rain

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.ui.util.SidebarWeatherCondition
import me.weishu.kernelsu.ui.util.SidebarWeatherConfig
import me.weishu.kernelsu.ui.util.SidebarWeatherError
import me.weishu.kernelsu.ui.util.SidebarWeatherState
import me.weishu.kernelsu.ui.util.readSidebarWeatherConfig
import me.weishu.kernelsu.ui.util.rememberSidebarWeatherState
import me.weishu.kernelsu.ui.util.sidebarWeatherPreferences

internal enum class RainWeatherStatus { Ready, Disabled, ConsentRequired, Waiting, Stale, Failed, Unsupported }

internal data class RainWeatherSnapshot(
    val status: RainWeatherStatus,
    val condition: SidebarWeatherCondition? = null,
    val location: String = "",
)

/** Never use another provider's cache, a revoked consent, or an expired reading. */
internal fun rainWeatherSnapshot(
    config: SidebarWeatherConfig, state: SidebarWeatherState, now: Long,
): RainWeatherSnapshot {
    if (!config.enabled) return RainWeatherSnapshot(RainWeatherStatus.Disabled)
    if (!config.canFetch()) return RainWeatherSnapshot(RainWeatherStatus.ConsentRequired)
    if (state.key != config.cacheKey()) return RainWeatherSnapshot(RainWeatherStatus.Waiting)
    if (state.error == SidebarWeatherError.Permission) return RainWeatherSnapshot(RainWeatherStatus.Failed)
    val reading = state.reading ?: return RainWeatherSnapshot(
        if (state.error != null) RainWeatherStatus.Failed else RainWeatherStatus.Waiting,
    )
    val maxAge = maxOf(60L, config.normalized().refreshMinutes * 2L) * 60_000L
    if (reading.fetchedAt <= 0L || now - reading.fetchedAt !in 0..maxAge) return RainWeatherSnapshot(RainWeatherStatus.Stale)
    val condition = reading.condition
    if (atmosphereSceneForWeather(condition) == null) return RainWeatherSnapshot(RainWeatherStatus.Unsupported)
    return RainWeatherSnapshot(RainWeatherStatus.Ready, condition, reading.location)
}

/** Reuses the consent-gated, rate-limited sidebar repository; never requests location access. */
@Composable
internal fun rememberRainWeather(settings: RainSettings): RainWeatherSnapshot {
    val context = LocalContext.current
    val preferences = remember(context) { sidebarWeatherPreferences(context) }
    var config by remember(preferences) { mutableStateOf(readSidebarWeatherConfig(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            config = readSidebarWeatherConfig(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled = settings.active && settings.atmosphere && settings.atmosphereFollowsWeather
    val state = rememberSidebarWeatherState(config.copy(enabled = enabled && config.enabled))
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled, owner) {
        if (enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(60_000L)
            }
        }
    }
    return rainWeatherSnapshot(config, state, now)
}
