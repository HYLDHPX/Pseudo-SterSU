package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import kotlin.math.PI
import kotlin.math.sin

internal data class RainStreakAppearance(val length: Float, val width: Float, val alpha: Float)

internal fun rainTravelMarginDp(spec: RainSceneSpec): Float = maxOf(40f, spec.maxLengthDp * 1.18f)

/** Depth stays deterministic and alpha never overrides either of the user's opacity controls. */
internal fun rainStreakAppearance(
    particle: RainParticle, index: Int, spec: RainSceneSpec, foreground: Boolean, opacity: Float = 1f,
): RainStreakAppearance {
    val depth = if (foreground) 1.18f else if (index % 3 == 1) 0.68f else 0.92f
    return RainStreakAppearance(
        (spec.minLengthDp + particle.length * (spec.maxLengthDp - spec.minLengthDp)) * depth,
        (spec.minStrokeDp + particle.alpha * (spec.maxStrokeDp - spec.minStrokeDp)) * depth,
        ((spec.minAlpha + particle.alpha * (spec.maxAlpha - spec.minAlpha)) *
            depth * opacity.coerceIn(0f, 1f)).coerceIn(0f, 0.66f),
    )
}

internal data class RainStreakPaint(val length: Float, val width: Float, val ink: Brush, val highlight: Brush?)

internal fun rainStreakPaint(
    particle: RainParticle, index: Int, spec: RainSceneSpec, palette: RainPalette,
    foreground: Boolean, density: Density, opacity: Float = 1f,
): RainStreakPaint = with(density) {
    val streak = rainStreakAppearance(particle, index, spec, foreground, opacity)
    val length = streak.length.dp.toPx()
    val ink = rainOrnamentInk(palette)
    RainStreakPaint(length, streak.width.dp.toPx(),
        Brush.verticalGradient(listOf(Color.Transparent, ink.copy(alpha = streak.alpha)), -length, 0f),
        if (foreground || index % 3 == 2) Brush.verticalGradient(
            listOf(Color.Transparent, palette.highlight.copy(alpha = streak.alpha * 0.68f)), -length, 0f) else null)
}

internal fun DrawScope.drawRainStreak(paint: RainStreakPaint, tip: Offset, wind: Float) = withTransform({ translate(tip.x, tip.y) }) {
    val start = Offset(-paint.length * wind, -paint.length)
    // The dark refracted edge remains visible on daylight glass; the silver core catches night light.
    drawLine(paint.ink, start, Offset.Zero, paint.width, StrokeCap.Round)
    paint.highlight?.let { brush ->
        val offset = Offset(0.5.dp.toPx(), 0f)
        drawLine(brush, start + offset, offset, paint.width * 0.48f, StrokeCap.Round)
    }
}

/** Cached cloud paints: no bitmap, blur pass, frame-by-frame noise or extra animation. */
internal data class RainCloudPaint(
    val center: Offset,
    val radius: Float,
    val flatten: Float,
    val tint: Color,
) {
    val brush: Brush = Brush.radialGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.52f),
        Color.Transparent), center, radius)
}

internal fun rainCloudPaints(size: Size, palette: RainPalette, scene: RainStyle): List<RainCloudPaint> {
    if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) return emptyList()
    val storm = scene == RainStyle.HeavyRain || scene == RainStyle.Thunderstorm
    // Overlapping flattened lobes build broad cloud banks, rather than outlined cloud icons.
    return List(9) { index ->
        val row = index / 3
        val center = Offset(size.width * ((index % 3) * 0.47f - 0.04f + row * 0.10f),
            size.height * (0.04f + row * 0.21f))
        val radius = size.width * (0.50f + (index % 3) * 0.09f)
        val cloud = if (index % 2 == 0) palette.cloud else lerp(palette.cloud, palette.highlight, 0.55f)
        val alpha = if (storm) 0.30f else if (scene == RainStyle.AfterRain) 0.12f else 0.22f
        RainCloudPaint(center, radius, 0.34f + row * 0.06f, cloud.copy(alpha = alpha))
    }
}

internal fun DrawScope.drawRainClouds(clouds: List<RainCloudPaint>, progress: Float) {
    val drift = sin(progress * 2f * PI).toFloat() * size.width * 0.018f
    clouds.forEachIndexed { index, cloud ->
        withTransform({
            translate(drift * if (index % 2 == 0) 1f else -0.6f, 0f)
            scale(1f, cloud.flatten, cloud.center)
        }) { drawCircle(cloud.brush, cloud.radius, cloud.center) }
    }
}
