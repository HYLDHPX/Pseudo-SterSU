package me.weishu.kernelsu.ui.component.rain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.ui.util.SidebarWeatherCondition
import java.util.Calendar

/**
 * Resolves the rain scene for the wall clock. Only the scene changes; palette, opacity and
 * layout stay under the user's control so the atmosphere never fights the design screen.
 */
internal fun atmosphereScene(timeOfDay: RainTimeOfDay): RainStyle = when (timeOfDay) {
    RainTimeOfDay.Dawn -> RainStyle.LightRain
    RainTimeOfDay.Day -> RainStyle.MediumRain
    RainTimeOfDay.Dusk -> RainStyle.AfterRain
    RainTimeOfDay.Night -> RainStyle.HeavyRain
}

/** Hours are always 0..23; negative or oversized input is folded back into a day. */
internal fun rainTimeOfDay(hourOfDay: Int): RainTimeOfDay =
    RainTimeOfDay.fromFloorMod(Math.floorMod(hourOfDay, 24))

/** Weather overrides the clock only for conditions that visibly change the rain. */
internal fun atmosphereSceneForWeather(condition: SidebarWeatherCondition): RainStyle? = when (condition) {
    SidebarWeatherCondition.Storm -> RainStyle.Thunderstorm
    SidebarWeatherCondition.Rain -> RainStyle.MediumRain
    SidebarWeatherCondition.Clear, SidebarWeatherCondition.Night -> RainStyle.AfterRain
    SidebarWeatherCondition.Cloud, SidebarWeatherCondition.Snow,
    SidebarWeatherCondition.Fog, SidebarWeatherCondition.Unknown -> null
}

/**
 * Single resolution point for "which scene is on screen right now".
 * Without atmosphere the stored scene is returned unchanged; weather only contributes when
 * the user opted into it.
 */
internal fun resolveRainScene(
    settings: RainSettings,
    timeOfDay: RainTimeOfDay,
    weather: SidebarWeatherCondition?,
): RainStyle {
    if (!settings.atmosphere) return settings.scene
    val byWeather = if (settings.atmosphereFollowsWeather) weather?.let(::atmosphereSceneForWeather) else null
    return byWeather ?: atmosphereScene(timeOfDay)
}

private const val ATMOSPHERE_REFRESH_MILLIS = 10 * 60 * 1000L

internal fun rainTimeOfDayNow(now: Long = System.currentTimeMillis()): RainTimeOfDay =
    rainTimeOfDay(Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY))

/** Recomputes the hour a few times an hour; never per frame. */
@Composable
internal fun rememberRainTimeOfDay(enabled: Boolean): RainTimeOfDay {
    var timeOfDay by remember { mutableStateOf(rainTimeOfDayNow()) }
    val owner = LocalLifecycleOwner.current
    if (enabled) {
        LaunchedEffect(owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    timeOfDay = rainTimeOfDayNow()
                    delay(ATMOSPHERE_REFRESH_MILLIS)
                }
            }
        }
    }
    return timeOfDay
}

/** Per-scene ambient loudness multiplier, so the sound follows the scene instead of a slider. */
internal fun rainSoundGain(scene: RainStyle): Float = when (scene) {
    RainStyle.LightRain -> 0.72f
    RainStyle.MediumRain -> 1f
    RainStyle.HeavyRain -> 1.28f
    RainStyle.Thunderstorm -> 1.18f
    RainStyle.AfterRain -> 0.5f
}

private fun RainSound.presetGain(): Float = when (this) {
    RainSound.Off -> 0f
    RainSound.Whisper -> 0.55f
    RainSound.Shower -> 1f
    RainSound.Downpour -> 1.35f
}

internal fun rainSoundVolume(sound: RainSound, volume: Float, scene: RainStyle): Float {
    if (sound == RainSound.Off) return 0f
    val safe = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0.45f
    return (safe * sound.presetGain() * rainSoundGain(scene)).coerceIn(0f, 1f)
}
