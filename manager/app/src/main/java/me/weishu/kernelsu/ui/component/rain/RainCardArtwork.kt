package me.weishu.kernelsu.ui.component.rain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random
import me.weishu.kernelsu.ui.theme.isInDarkTheme

/** A blue-gray ink visible on both pale glass and dark surfaces. */
internal fun rainOrnamentInk(palette: RainPalette): Color = lerp(palette.ripple, palette.content, 0.24f)

/** A refracted bead has a shaded belly and a small white rim, rather than looking like a dot. */
internal fun DrawScope.drawRainGlassBead(
    center: Offset, radius: Float, palette: RainPalette, strength: Float, elongation: Float = 1.35f,
) {
    if (radius <= 0f || strength <= 0f) return
    val ink = rainOrnamentInk(palette)
    val beadSize = Size(radius * 2f, radius * 2f * elongation)
    val topLeft = center - Offset(radius, beadSize.height / 2f)
    drawOval(Brush.linearGradient(listOf(palette.highlight.copy(alpha = 0.60f * strength),
        ink.copy(alpha = 0.16f * strength), ink.copy(alpha = 0.44f * strength)), topLeft, topLeft +
        Offset(beadSize.width, beadSize.height)), topLeft, beadSize)
    drawOval(ink.copy(alpha = 0.42f * strength), topLeft, beadSize, style = Stroke(0.6.dp.toPx()))
    drawArc(palette.highlight.copy(alpha = 0.86f * strength), 205f, 105f, false,
        topLeft + Offset(radius * 0.25f, radius * 0.3f), Size(radius * 1.3f, radius * 1.4f),
        style = Stroke(0.65.dp.toPx(), cap = StrokeCap.Round))
}

/** Stable geometry/gradients are built only when size, shape or palette changes. */
internal data class RainCardFramePaint(val outline: Outline, val inset: Float, val width: Float, val brush: Brush)
internal data class RainCardPaints(
    val wash: Brush, val topLight: Brush, val bottomLight: Brush,
    val reflection: Path, val reflectionBrush: Brush,
    val streaks: List<Path>, val streakBrush: Brush, val rippleBrush: Brush,
    val frames: List<RainCardFramePaint>,
)

internal fun rainCardPaints(size: Size, density: Density, direction: LayoutDirection, palette: RainPalette, shape: Shape?): RainCardPaints = with(density) {
    val ink = rainOrnamentInk(palette)
    val reflection = Path().apply {
        moveTo(size.width * 0.80f, 0f); lineTo(size.width * 0.92f, 0f)
        lineTo(size.width * 0.70f, size.height); lineTo(size.width * 0.64f, size.height); close()
    }
    val streaks = List(3) { index ->
        val x = size.width - (5f + index * 6f).dp.toPx()
        Path().apply {
            moveTo(x, -8.dp.toPx())
            cubicTo(x - 2.dp.toPx(), size.height * 0.3f, x + 2.dp.toPx(), size.height * 0.65f,
                x - 3.dp.toPx(), size.height + 8.dp.toPx())
        }
    }
    val frames = if (shape == null || size.width < 24.dp.toPx() || size.height < 20.dp.toPx()) emptyList() else List(2) { index ->
        val inset = (if (index == 0) 0.7f else 1.8f).dp.toPx()
        val innerSize = Size((size.width - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f))
        val alpha = if (index == 0) 0.82f else 0.72f
        RainCardFramePaint(shape.createOutline(innerSize, direction, density), inset,
            (if (index == 0) 1.15f else 0.7f).dp.toPx(),
            Brush.linearGradient(listOf((if (index == 0) ink else palette.highlight).copy(alpha = alpha),
                ink.copy(alpha = alpha * 0.12f), ink.copy(alpha = alpha * 0.65f)), end = Offset(innerSize.width, innerSize.height)))
    }
    RainCardPaints(
        Brush.verticalGradient(listOf(palette.highlight.copy(alpha = 0.24f), Color.Transparent, palette.rain.copy(alpha = 0.12f))),
        Brush.radialGradient(listOf(palette.rain.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.08f, 0f), maxOf(size.width * 0.8f, 100.dp.toPx())),
        Brush.radialGradient(listOf(palette.highlight.copy(alpha = 0.26f), Color.Transparent), Offset(size.width * 0.95f, size.height), maxOf(size.width * 0.65f, 80.dp.toPx())),
        reflection, Brush.horizontalGradient(listOf(Color.Transparent, palette.highlight.copy(alpha = 0.12f), Color.Transparent)),
        streaks, Brush.verticalGradient(listOf(Color.Transparent, ink.copy(alpha = 0.24f), Color.Transparent)),
        Brush.linearGradient(listOf(Color.Transparent, ink.copy(alpha = 0.28f), Color.Transparent)), frames,
    )
}

@Composable
internal fun rememberRainOrnamentSeed(): Float = remember { Random.nextFloat() }

/** Install inside a Material Card so its semantic container color remains visible. */
@Composable
fun Modifier.rainCardInterior(shape: Shape? = null): Modifier {
    val settings = LocalRainSettings.current
    if (!settings.active || settings.cardInterior == RainCardInterior.Off || settings.interiorStrength() <= 0f) return this
    val palette = rainPalette(settings, isInDarkTheme())
    val progress = LocalRainSceneProgress.current
    val motion = LocalRainCardMotionEnabled.current && progress.rainMotionAllowed()
    val seed = rememberRainOrnamentSeed()
    return then(if (shape == null) Modifier else Modifier.clip(shape)).drawWithCache {
        val paints = rainCardPaints(size, this, layoutDirection, palette, shape)
        onDrawBehind {
            drawRainCardInterior(settings.cardInterior, palette, settings.interiorStrength() * rainWetness(settings.intensity),
                paints, if (motion) progress.rainSeconds() else 0f, seed)
        }
    }
}

/** Condensation and runoff stay near the edge, leaving labels and controls undimmed. */
internal fun DrawScope.drawRainCardInterior(
    style: RainCardInterior, palette: RainPalette, strength: Float, paints: RainCardPaints,
    seconds: Float = 0f, seed: Float = 0f,
) {
    if (style == RainCardInterior.Off || strength <= 0f || size.width <= 0f || size.height <= 0f) return
    drawRect(paints.wash, alpha = strength)
    drawRect(paints.topLight, alpha = strength)
    drawRect(paints.bottomLight, alpha = strength)
    drawPath(paints.reflection, paints.reflectionBrush, alpha = strength)
    if (size.width >= 100.dp.toPx() && size.height >= 52.dp.toPx()) {
        repeat(3) { index ->
            val drop = rainRunoffState(seconds, index, seed)
            val x = size.width - (6f + index % 2 * 3f).dp.toPx()
            val center = Offset(x, size.height * drop.travel)
            val radius = (1.1f + index * 0.32f).dp.toPx() * drop.merge
            if (style == RainCardInterior.Streaks) {
                drawLine(rainOrnamentInk(palette).copy(alpha = 0.18f * strength * drop.alpha),
                    center - Offset(0f, minOf(18.dp.toPx(), size.height * drop.travel)), center,
                    0.7.dp.toPx(), StrokeCap.Round)
            }
            drawRainGlassBead(center, radius, palette, strength * drop.alpha * 0.85f)
        }
    }
    when (style) {
        RainCardInterior.Off, RainCardInterior.Mist -> Unit
        RainCardInterior.Streaks -> paints.streaks.forEach { path ->
            drawPath(path, paints.streakBrush, alpha = strength,
                style = Stroke(0.85.dp.toPx(), cap = StrokeCap.Round))
            val refractionOffset = 0.8.dp.toPx()
            withTransform({ translate(refractionOffset, 0f) }) {
                drawPath(path, palette.highlight.copy(alpha = 0.20f * strength),
                    style = Stroke(0.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        RainCardInterior.Ripples -> {
            val unit = minOf(size.width * 0.27f, size.height * 0.65f, 72.dp.toPx())
            repeat(2) { index ->
                val phase = rainRunoffState(seconds * 1.5f, index, seed).travel
                val radius = unit * (0.25f + phase * 0.8f)
                val center = Offset(size.width * 0.96f, size.height * 0.94f)
                drawOval(paints.rippleBrush, Offset(center.x - radius, center.y - radius * 0.28f),
                    Size(radius * 2f, radius * 0.56f), alpha = (1f - phase) * strength,
                    style = Stroke(0.8.dp.toPx()))
            }
        }
    }
}

internal fun DrawScope.drawRainCardFrame(style: RainCardBorder, palette: RainPalette, strength: Float, paints: RainCardPaints) {
    if (style == RainCardBorder.Off || strength <= 0f) return
    paints.frames.forEachIndexed { index, frame ->
        if (index > 0 && style != RainCardBorder.Double) return@forEachIndexed
        withTransform({ translate(frame.inset, frame.inset) }) {
            drawOutline(frame.outline, frame.brush, alpha = strength.coerceIn(0f, 1f), style = Stroke(frame.width))
        }
    }
    if (style == RainCardBorder.Dashed) repeat(3) { index ->
        val x = when (index) { 0 -> 0.17f; 1 -> 0.59f; else -> 0.88f }
        drawLine(rainOrnamentInk(palette).copy(alpha = 0.58f * strength), Offset(size.width * x, 0.8.dp.toPx()),
            Offset(size.width * x + (6f + index * 2f).dp.toPx(), 0.8.dp.toPx()), 1.1.dp.toPx(), StrokeCap.Round)
    }
}
