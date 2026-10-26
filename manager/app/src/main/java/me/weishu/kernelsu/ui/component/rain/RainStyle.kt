package me.weishu.kernelsu.ui.component.rain

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.InterfaceStyleTheme
import kotlin.math.sqrt

const val RAIN_STYLE_KEY = "rain_style"

enum class RainStyle(
    val value: String,
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int,
    @StringRes val mottoRes: Int,
    @ColorInt val keyColor: Int,
) {
    LightRain("light_rain", R.string.rain_style_light, R.string.rain_style_light_summary, R.string.rain_motto_light, 0xFF5E84A6.toInt()),
    MediumRain("medium_rain", R.string.rain_style_medium, R.string.rain_style_medium_summary, R.string.rain_motto_medium, 0xFF4F7397.toInt()),
    HeavyRain("heavy_rain", R.string.rain_style_heavy, R.string.rain_style_heavy_summary, R.string.rain_motto_heavy, 0xFF3B566F.toInt()),
    Thunderstorm("thunderstorm", R.string.rain_style_thunderstorm, R.string.rain_style_thunderstorm_summary, R.string.rain_motto_thunderstorm, 0xFF666FA8.toInt()),
    AfterRain("after_rain", R.string.rain_style_after_rain, R.string.rain_style_after_rain_summary, R.string.rain_motto_after_rain, 0xFF719C9B.toInt());

    companion object {
        const val DEFAULT_VALUE = "light_rain"

        fun fromValue(value: String?): RainStyle = entries.firstOrNull { it.value == value } ?: LightRain

        fun fromIndex(index: Int): RainStyle = entries.getOrElse(index) { LightRain }

        fun selectedIndex(value: String?): Int = entries.indexOf(fromValue(value)).coerceAtLeast(0)
    }
}

/** Time-of-day band used when [RainSettings.atmosphere] is enabled. */
enum class RainTimeOfDay(val value: String) {
    Dawn("dawn"), Day("day"), Dusk("dusk"), Night("night");

    companion object {
        fun fromFloorMod(floorMod: Int): RainTimeOfDay = when {
            floorMod in 5..8 -> Dawn
            floorMod in 9..16 -> Day
            floorMod in 17..19 -> Dusk
            else -> Night
        }
    }
}

val LocalRainStyle = staticCompositionLocalOf { RainStyle.LightRain }

@Immutable
data class RainPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val fog: Color,
    val cloud: Color,
    val rain: Color,
    val rainAccent: Color,
    val ripple: Color,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val outline: Color,
    val highlight: Color,
    val content: Color,
    val shadow: Color,
    /** Second stop of the wet card edge; keeps glass and droplets distinguishable. */
    val surfaceSheen: Color = surfaceBottom,
)

fun rainPalette(theme: InterfaceStyleTheme?, style: RainStyle, dark: Boolean): RainPalette {
    val source = theme?.takeIf { it.engine == "rain" && it.variant == style.value }
        ?.let { if (dark || it.forceDark) it.darkPalette else it.lightPalette }
    if (source != null) {
        return RainPalette(
            backgroundTop = Color(source.background),
            backgroundBottom = Color(source.backgroundAlt),
            fog = Color(source.muted),
            cloud = Color(source.surfaceAlt),
            rain = Color(source.primary),
            rainAccent = Color(source.secondary),
            ripple = Color(source.outline),
            surfaceTop = Color(source.surface),
            surfaceBottom = Color(source.surfaceAlt),
            outline = Color(source.outline),
            highlight = Color(source.highlight),
            content = Color(source.content),
            shadow = Color(source.shadow),
        )
    }
    return fallbackRainPalette(style, dark)
}

fun rainPalette(style: RainStyle, dark: Boolean): RainPalette = fallbackRainPalette(style, dark)

/**
 * The accent drives both UI and weather: custom day/night colors tint the rain streaks,
 * not just the chrome around them.
 */
private fun fallbackRainPalette(style: RainStyle, dark: Boolean, accent: Color = Color(style.keyColor)): RainPalette {
    val backgroundTop = if (dark) lerp(Color(0xFF172938), accent, 0.10f) else lerp(Color(0xFFB6C8D7), accent, 0.12f)
    val backgroundBottom = if (dark) lerp(Color(0xFF0C1924), accent, 0.06f) else lerp(Color(0xFFD7E1E8), accent, 0.08f)
    val surfaceTop = if (dark) lerp(Color(0xFF243A49), accent, 0.08f) else lerp(Color(0xFFF3F8FB), accent, 0.03f)
    val surfaceBottom = if (dark) lerp(Color(0xFF192D3B), accent, 0.08f) else lerp(Color(0xFFDCE9F0), accent, 0.04f)
    val content = if (dark) Color(0xFFE8F2F7) else Color(0xFF172F39)
    // Keep streaks visible against the backdrop: lighten on dark, darken on light.
    val rain = if (dark) lerp(accent, Color(0xFFE4EFF7), 0.62f) else lerp(accent, Color(0xFF243645), 0.24f)
    return RainPalette(
        backgroundTop = backgroundTop,
        backgroundBottom = backgroundBottom,
        fog = lerp(backgroundBottom, content, if (dark) 0.28f else 0.18f),
        cloud = if (dark) Color(0xFF748C9E) else lerp(Color(0xFF6F879A), accent, 0.16f),
        rain = rain,
        rainAccent = lerp(accent, Color(0xFFBDF4EA), 0.55f),
        ripple = lerp(accent, content, 0.32f),
        surfaceTop = surfaceTop,
        surfaceBottom = surfaceBottom,
        outline = lerp(accent, content, if (dark) 0.42f else 0.24f),
        highlight = if (dark) Color(0xFFF2FAFF) else Color.White,
        content = content,
        shadow = if (dark) Color.Black else lerp(accent, Color.Black, 0.58f),
        surfaceSheen = lerp(surfaceTop, rain, if (dark) 0.16f else 0.10f),
    )
}

fun rainPalette(settings: RainSettings, dark: Boolean): RainPalette = fallbackRainPalette(
    settings.scene, dark,
    Color((if (dark) settings.darkAccent else settings.lightAccent) ?: settings.scene.keyColor),
)

internal data class RainSceneSpec(
    val dropCount: Int,
    val rippleCount: Int,
    val cycleMillis: Int,
    val minLengthDp: Float,
    val maxLengthDp: Float,
    val minStrokeDp: Float,
    val maxStrokeDp: Float,
    val windRatio: Float,
    val minAlpha: Float,
    val maxAlpha: Float,
) {
    companion object {
        /** Per-scene base density; atmosphere scales this rather than replacing it. */
        internal fun densityOf(scene: RainStyle): Float = when (scene) {
            RainStyle.LightRain -> 1f
            RainStyle.MediumRain -> 1.4f
            RainStyle.HeavyRain -> 1.85f
            RainStyle.Thunderstorm -> 1.75f
            RainStyle.AfterRain -> 0.32f
        }

        fun fromSettings(settings: RainSettings): RainSceneSpec {
            val safe = settings.normalized()
            val form = safe.dropForm
            val lengthScale = (safe.dropLength ?: 1f) * form.lengthScale
            val baseMinLength = when (safe.scene) {
                RainStyle.HeavyRain, RainStyle.Thunderstorm -> 44f
                RainStyle.AfterRain -> 18f
                else -> 32f
            }
            val minLength = (12f * lengthScale).coerceIn(2f, 48f)
            val maxLength = (baseMinLength * lengthScale).coerceIn(minLength, 96f)
            val strokeScale = form.strokeScale
            val alphaScale = safe.dropOpacity ?: 1f
            return RainSceneSpec(
                (safe.quality.drops * densityOf(safe.scene) * sqrt(safe.intensity)).toInt().coerceIn(0, 80),
                // Ripples follow the drop layout, so a denser scene earns more impacts.
                if (safe.scene == RainStyle.AfterRain) 4 else 0,
                (6_000 / safe.speed).toInt().coerceIn(3_000, 15_000),
                minLength,
                maxLength,
                (0.55f * strokeScale).coerceIn(0.1f, 4f),
                (1.15f * strokeScale).coerceIn(0.1f, 6f),
                safe.wind,
                (0.28f * alphaScale).coerceIn(0f, 1f),
                (0.62f * alphaScale).coerceIn(0f, 1f),
            )
        }

        fun fromTheme(theme: InterfaceStyleTheme?): RainSceneSpec {
            val scene = theme?.takeIf { it.engine == "rain" }?.scene
            return if (scene != null) {
                RainSceneSpec(
                    scene.primaryCount,
                    scene.secondaryCount,
                    scene.cycleMillis,
                    scene.minLengthDp,
                    scene.maxLengthDp,
                    scene.minStrokeDp,
                    scene.maxStrokeDp,
                    scene.angle,
                    scene.minAlpha,
                    scene.maxAlpha,
                )
            } else {
                RainSceneSpec(48, 6, 14_000, 8f, 18f, 0.4f, 0.8f, 0.08f, 0.16f, 0.38f)
            }
        }
    }
}

internal fun forceRainDarkTheme(style: RainStyle, theme: InterfaceStyleTheme? = null): Boolean =
    style == RainStyle.HeavyRain || (theme?.takeIf { it.engine == "rain" && it.variant == style.value }?.forceDark == true)
