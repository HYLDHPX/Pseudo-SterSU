package me.weishu.kernelsu.ui.component.rain

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import kotlin.math.PI
import kotlin.math.sin

enum class RainChromeArea { TopBar, Navigation, Sidebar }

/** Each chrome region has its own strength and water behavior; content alpha is untouched. */
@Composable
fun Modifier.rainChromeDecoration(
    area: RainChromeArea,
    vertical: Boolean = false,
    edgeOnRight: Boolean = true,
    shape: Shape? = null,
): Modifier {
    val settings = LocalRainSettings.current
    if (!settings.active) return this
    val kind = when (area) {
        RainChromeArea.TopBar -> RainSurfaceKind.TopBar
        RainChromeArea.Navigation -> RainSurfaceKind.Navigation
        RainChromeArea.Sidebar -> RainSurfaceKind.Sidebar
    }
    val decoration = when (area) {
        RainChromeArea.TopBar -> settings.topBarDecoration
        RainChromeArea.Navigation -> settings.navigationDecoration
        RainChromeArea.Sidebar -> settings.sidebarDecoration
    }
    val linked = rainInteractionTarget(kind, vertical = vertical || area == RainChromeArea.Sidebar,
        edgeOnRight = edgeOnRight, shape = shape)
    val strength = settings.edgeStrength(kind) * rainWetness(settings.intensity)
    if (decoration == RainDecoration.Off || strength <= 0f) return linked
    val palette = rainPalette(settings, isInDarkTheme())
    val progress = LocalRainSceneProgress.current
    val motion = LocalRainCardMotionEnabled.current && progress.rainMotionAllowed()
    val seed = rememberRainOrnamentSeed()
    val thunderAllowed = motion && settings.drawingEnabled(LocalRainCoveredByWallpaper.current)
    return linked.then(if (shape == null) Modifier else Modifier.clip(shape)).drawWithCache {
        val paints = rainSurfacePaints(size, this, palette, 15.dp.toPx(),
            vertical || area == RainChromeArea.Sidebar, area == RainChromeArea.TopBar, edgeOnRight, seed)
        val frame = if (shape == null) null else rainCardPaints(size, this, layoutDirection, palette, shape)
        onDrawWithContent {
            drawContent()
            val seconds = if (motion) progress.rainSeconds() else 0f
            val thunder = rainThunderPulse(settings, seconds, thunderAllowed)
            if (area == RainChromeArea.Navigation && frame != null) {
                drawRainCardFrame(RainCardBorder.Outline, palette, strength * (0.7f + thunder * 0.15f), frame)
            }
            drawRainSurfaceDecoration(decoration, palette, seconds, strength, paints, area,
                drawRim = area != RainChromeArea.Sidebar || settings.sidebarDivider)
            if (thunder > 0f && paints.band > 0f) {
                drawPath(paints.meniscus, palette.highlight.copy(alpha = thunder * strength * 0.2f),
                    style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

internal fun rainDecorationBand(requested: Float, crossSize: Float): Float =
    if (!requested.isFinite() || !crossSize.isFinite() || requested <= 0f || crossSize <= 0f) 0f
    else minOf(requested, crossSize * 0.19f)

internal data class RainSurfacePaints(
    val band: Float, val length: Float, val size: Size, val bounds: Rect,
    val vertical: Boolean, val bottom: Boolean, val edgeOnRight: Boolean, val seed: Float,
    val anchors: List<Float>, val meniscus: Path, val sheen: Brush, val rim: Brush, val highlight: Brush,
) {
    fun point(along: Float, inset: Float): Offset = if (vertical)
        Offset(if (edgeOnRight) size.width - inset else inset, along)
    else Offset(along, if (bottom) size.height - inset else inset)
}

/** Shape, anchors and paints do not depend on frame time and live in drawWithCache. */
internal fun rainSurfacePaints(
    size: Size, density: Density, palette: RainPalette, requestedBand: Float,
    vertical: Boolean = false, bottom: Boolean = false, edgeOnRight: Boolean = true, seed: Float = 0f,
): RainSurfacePaints = with(density) {
    val band = rainDecorationBand(requestedBand, if (vertical) size.width else size.height)
    val length = if (vertical) size.height else size.width
    val bounds = Rect(if (vertical && edgeOnRight) size.width - band else 0f,
        if (!vertical && bottom) size.height - band else 0f,
        if (vertical && !edgeOnRight) band else size.width, if (!vertical && !bottom) band else size.height)
    fun point(along: Float, inset: Float): Offset = if (vertical)
        Offset(if (edgeOnRight) size.width - inset else inset, along)
    else Offset(along, if (bottom) size.height - inset else inset)
    val ink = rainOrnamentInk(palette)
    val count = (length / (if (vertical) 140.dp.toPx() else 100.dp.toPx())).toInt().coerceIn(2, 7)
    val anchors = List(count) { index -> (index + 0.35f + (seed + index * 0.17f) % 0.35f) / count }
    val meniscus = Path().apply {
        repeat(25) { index ->
            val t = index / 24f
            val point = point(t * length, (0.9f + sin((t * 3f + seed) * PI).toFloat() * 0.3f).dp.toPx())
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }
    val sheen = ink.copy(alpha = 0.22f)
    val highlight = palette.highlight.copy(alpha = 0.18f)
    val colors = if (vertical && !edgeOnRight || !vertical && !bottom)
        listOf(sheen, highlight, Color.Transparent) else listOf(Color.Transparent, highlight, sheen)
    RainSurfacePaints(band, length, size, bounds, vertical, bottom, edgeOnRight, seed, anchors, meniscus,
        if (vertical) Brush.horizontalGradient(colors, bounds.left, bounds.right.coerceAtLeast(bounds.left + 0.01f))
            else Brush.verticalGradient(colors, bounds.top, bounds.bottom.coerceAtLeast(bounds.top + 0.01f)),
        Brush.linearGradient(listOf(Color.Transparent, ink.copy(alpha = 0.72f), Color.Transparent), point(0f, 0f), point(maxOf(length, 1f), 0f)),
        Brush.linearGradient(listOf(Color.Transparent, palette.highlight.copy(alpha = 0.70f), Color.Transparent), point(0f, 0f), point(maxOf(length, 1f), 0f)))
}

internal fun DrawScope.drawRainSurfaceDecoration(
    decoration: RainDecoration, palette: RainPalette, seconds: Float, strength: Float,
    paints: RainSurfacePaints, area: RainChromeArea? = null, drawRim: Boolean = true,
) = with(paints) {
    if (decoration == RainDecoration.Off || band <= 0f || length < 28.dp.toPx() || strength <= 0f) return@with
    val ink = rainOrnamentInk(palette)
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        drawRect(sheen, bounds.topLeft, bounds.size, alpha = strength)
        if (drawRim) {
            drawPath(meniscus, rim, alpha = strength, style = Stroke(1.15.dp.toPx()))
            drawLine(highlight, point(length * 0.08f, 2.dp.toPx()), point(length * 0.92f, 2.dp.toPx()),
                0.65.dp.toPx(), alpha = strength)
        }
        // Quiet condensation remains when motion is disabled or a region is not selected.
        anchors.forEachIndexed { index, anchor ->
            drawRainGlassBead(point(length * anchor, minOf(band * 0.5f, 4.5.dp.toPx())),
                minOf((1.35f + index % 3 * 0.28f).dp.toPx(), band * 0.22f), palette, strength * 0.75f)
        }
        when {
            area == RainChromeArea.Navigation -> Unit // Selected items own one-shot ripples.
            area == RainChromeArea.TopBar -> anchors.forEachIndexed { index, anchor ->
                val drip = rainCollectingDrip(seconds, index, seed)
                val inset = band - 2.dp.toPx() - drip.travel * (band - 3.dp.toPx()).coerceAtLeast(0f)
                val center = point(length * anchor, inset)
                drawLine(ink.copy(alpha = 0.28f * strength * drip.alpha),
                    point(length * anchor, band), center, 0.9.dp.toPx(), StrokeCap.Round)
                drawRainGlassBead(center, minOf(1.7.dp.toPx() * drip.merge, band * 0.2f), palette,
                    strength * drip.alpha, elongation = 1.3f + drip.travel * 0.6f)
            }
            vertical && decoration != RainDecoration.Wet -> {
                repeat(3) { index ->
                    val drop = rainRunoffState(seconds, index, seed)
                    val center = point(length * drop.travel, minOf((3f + index * 3.1f).dp.toPx(), band * 0.8f))
                    val tail = center - Offset(0f, minOf((14f + drop.travel * 26f).dp.toPx(), center.y.coerceAtLeast(0f)))
                    drawLine(ink.copy(alpha = 0.35f * strength * drop.alpha), tail, center,
                        (0.7f + drop.travel * 0.4f).dp.toPx(), StrokeCap.Round)
                    drawRainGlassBead(center, minOf(1.9.dp.toPx() * drop.merge, band * 0.2f), palette,
                        strength * drop.alpha, elongation = 1.65f)
                }
                // A small collection lip connects the long runoffs before they disappear.
                drawLine(ink.copy(alpha = 0.24f * strength), point(length - 2.dp.toPx(), band * 0.2f),
                    point(length - 2.dp.toPx(), band * 0.78f), 1.1.dp.toPx(), StrokeCap.Round)
            }
            decoration == RainDecoration.Wet -> Unit
            decoration == RainDecoration.Ripples -> repeat(2) { index ->
                val drop = rainRunoffState(seconds * 1.4f, index, seed)
                val radius = (5f + drop.travel * 19f).dp.toPx()
                val center = point(length * anchors[index % anchors.size], 1.dp.toPx())
                val oval = Size(radius * 2f, radius * 0.46f)
                drawOval(ink.copy(alpha = 0.55f * strength * drop.alpha * (1f - drop.travel)), center - Offset(oval.width / 2f, oval.height / 2f),
                    oval, style = Stroke(0.9.dp.toPx()))
            }
            else -> anchors.forEachIndexed { index, anchor ->
                val drop = rainRunoffState(seconds * 1.8f, index, seed)
                val center = point(length * anchor, 2.dp.toPx() + drop.travel * (band - 4.dp.toPx()).coerceAtLeast(0f))
                drawLine(ink.copy(alpha = 0.32f * strength * drop.alpha), point(length * anchor, 0f), center,
                    0.9.dp.toPx(), StrokeCap.Round)
                drawRainGlassBead(center, minOf(1.9.dp.toPx() * drop.merge, band * 0.2f), palette, strength * drop.alpha, 1.55f)
            }
        }
    }
}
