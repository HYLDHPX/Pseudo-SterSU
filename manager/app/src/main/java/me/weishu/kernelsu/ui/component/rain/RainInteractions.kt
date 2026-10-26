package me.weishu.kernelsu.ui.component.rain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.theme.isInDarkTheme

private data class TrackedRainSurface(
    val coordinates: LayoutCoordinates,
    val bounds: Rect,
    val kind: RainSurfaceKind,
    val vertical: Boolean,
    val edgeOnRight: Boolean,
    val outline: Outline?,
)

private class RainSurfaceRegistry {
    val targets = mutableStateMapOf<Any, TrackedRainSurface>()
    var root: LayoutCoordinates? = null

    fun visibleSurfaces(): List<RainHitSurface> {
        val root = root?.takeIf { it.isAttached } ?: return emptyList()
        val viewport = Size(root.size.width.toFloat(), root.size.height.toFloat())
        return targets.values.mapNotNull { target ->
            val coordinates = target.coordinates.takeIf { it.isAttached } ?: return@mapNotNull null
            if (coordinates.findRootCoordinates() !== root.findRootCoordinates()) return@mapNotNull null
            // Use the root's local space so window offsets and parent transforms cannot shift hits.
            if (target.outline is Outline.Generic) return@mapNotNull null
            val bounds = root.localBoundingBoxOf(coordinates, clipBounds = true)
            val original = root.localBoundingBoxOf(coordinates, clipBounds = false)
            val sx = original.width / coordinates.size.width.coerceAtLeast(1)
            val sy = original.height / coordinates.size.height.coerceAtLeast(1)
            fun androidx.compose.ui.geometry.CornerRadius.scaled() =
                androidx.compose.ui.geometry.CornerRadius(x * sx, y * sy)
            val corners = (target.outline as? Outline.Rounded)?.roundRect?.let {
                RainCorners(it.topLeftCornerRadius.scaled(), it.topRightCornerRadius.scaled(),
                    it.bottomRightCornerRadius.scaled(), it.bottomLeftCornerRadius.scaled())
            }
            visibleRainSurface(bounds, original, viewport, target.kind, target.vertical, target.edgeOnRight)
                ?.copy(corners = corners, shapeBounds = original)
        }.take(64)
    }
}

private val LocalRainSurfaces = staticCompositionLocalOf<RainSurfaceRegistry?> { null }

/** Registrations follow lazy-item disposal; no per-card animation jobs or touch handlers. */
@Composable
internal fun Modifier.rainInteractionTarget(
    kind: RainSurfaceKind,
    vertical: Boolean = false,
    edgeOnRight: Boolean = true,
    shape: Shape? = null,
): Modifier {
    val registry = LocalRainSurfaces.current ?: return this
    val settings = LocalRainSettings.current
    if (!settings.foregroundDrawingEnabled(LocalRainCoveredByWallpaper.current) || !settings.surfaceInteraction ||
        settings.edgeStrength(kind) <= 0f || !settings.hasEdgeDecoration(kind)) return this
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val id = remember(registry, kind, vertical, edgeOnRight) { Any() }
    DisposableEffect(registry, id) { onDispose { registry.targets.remove(id) } }
    return onGloballyPositioned { coordinates ->
        if (registry.targets.size < 128 || id in registry.targets) {
            val outline = shape?.createOutline(Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()), direction, density)
            registry.targets[id] = TrackedRainSurface(coordinates, coordinates.boundsInRoot(), kind, vertical, edgeOnRight, outline)
        }
    }
}

/** A draw modifier over the existing content: it adds no hit target or accessibility node. */
@Composable
fun RainUiLayer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settings = LocalRainSettings.current
    val covered = LocalRainCoveredByWallpaper.current
    val foreground = settings.foregroundDrawingEnabled(covered)
    val thunderEnabled = settings.drawingEnabled(covered) && settings.thunder &&
        settings.motion && settings.intensity > 0f && settings.scene == RainStyle.Thunderstorm
    if (!foreground && !thunderEnabled) {
        Box(modifier, content = content)
        return
    }
    val registry = remember { RainSurfaceRegistry() }
    val rootCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val palette = rainPalette(settings, isInDarkTheme())
    val spec = remember(settings) { RainSceneSpec.fromSettings(settings) }
    val progress = LocalRainSceneProgress.current
    val motionAllowed = settings.motion && progress.rainMotionAllowed()
    val interactionStrength = remember(settings) { rainInteractionStrength(settings) }
    CompositionLocalProvider(LocalRainSurfaces provides registry) {
        Box(modifier.clipToBounds().onGloballyPositioned {
            registry.root = it
            rootCoordinates.value = it
        }.drawWithCache {
            val particles = if (foreground)
                rainParticles(rainViewportDropCount(spec.dropCount, size, density), settings.scene) else emptyList()
            val streaks = particles.mapIndexed { index, drop ->
                if (rainFrontParticle(index)) rainStreakPaint(drop, index, spec, palette, true, this, settings.foregroundOpacity) else null
            }
            val thunderPaint = if (thunderEnabled) rainThunderPaint(size, palette) else null
            onDrawWithContent {
                drawContent()
                // Observe placement once; the shared clock invalidates drawing, never page composition.
                if (rootCoordinates.value == null || size.width <= 0f || size.height <= 0f) return@onDrawWithContent
                val sceneProgress = progress.value
                val seconds = progress.rainSeconds()
                val wind = rainWindAt(spec.windRatio, seconds)
                val margin = rainTravelMarginDp(spec).dp.toPx()
                val surfaces = if (settings.surfaceInteraction && motionAllowed && settings.decorationOpacity > 0f)
                    registry.visibleSurfaces() else emptyList()
                val surfaceIndex = if (surfaces.isEmpty()) null else RainSurfaceIndex(surfaces, size, wind)
                particles.forEachIndexed { index, particle ->
                    if (!rainFrontParticle(index)) return@forEachIndexed
                    val fieldProgress = rainFieldProgress(sceneProgress, index, size, density, margin)
                    val tip = rainParticleTip(particle, fieldProgress, size, wind, margin)
                    streaks[index]?.let { drawRainStreak(it, tip, wind) }
                    var hit: RainSurfaceHit? = null
                    for (surface in surfaceIndex?.candidates(particle).orEmpty()) {
                        val candidate = rainSurfaceHit(particle, fieldProgress, size, surface, wind,
                            margin, rainFieldCycleMillis(spec.cycleMillis, index, size, density, margin), 12.dp.toPx()) ?: continue
                        if (hit == null || candidate.age < hit.age) hit = candidate
                    }
                    if (hit != null) {
                        val strength = interactionStrength * settings.edgeStrength(hit.kind) / settings.decorationOpacity.coerceAtLeast(0.001f)
                        drawRainSurfaceHit(hit, palette, strength)
                    }
                }
                val thunder = rainThunderPulse(settings, seconds, motionAllowed)
                if (thunderPaint != null && thunder > 0f) {
                    if (foreground) {
                        drawRainThunder(thunderPaint, thunder, (seconds / settings.thunderInterval).toInt())
                    } else {
                        // With near rain disabled, only reflected cloud light crosses content;
                        // the actual bolt and every streak remain behind the panels.
                        drawRect(thunderPaint.reflection, alpha = thunder)
                    }
                }
            }
        }, content = content)
    }
}

private fun DrawScope.drawRainSurfaceHit(hit: RainSurfaceHit, palette: RainPalette, strength: Float) {
    val ink = rainOrnamentInk(palette)
    val fade = (1f - hit.age) * strength
    val radius = (3f + hit.age * 18f).dp.toPx()
    // A short bright wet edge follows the real tangent of a rounded impact surface.
    val half = (4f + hit.age * 10f).dp.toPx()
    drawLine(palette.highlight.copy(alpha = 0.7f * fade),
        hit.position - hit.tangent * half, hit.position + hit.tangent * half,
        1.6.dp.toPx(), StrokeCap.Round)
    val oval = if (hit.vertical) Size(radius * 0.46f, radius * 2f) else Size(radius * 2f, radius * 0.46f)
    drawOval(ink.copy(alpha = 0.76f * fade), hit.position - Offset(oval.width / 2f, oval.height / 2f), oval,
        style = Stroke(1.1.dp.toPx()))
    drawOval(palette.highlight.copy(alpha = 0.78f * fade),
        hit.position - Offset(oval.width / 2f, oval.height / 2f - 1.dp.toPx()), oval,
        style = Stroke(0.55.dp.toPx()))
    // A short runoff thread and two fading spray grains stay close to the actual crossing.
    val runoff = hit.position + Offset(0f, (4f + hit.age * 17f).dp.toPx())
    drawLine(ink.copy(alpha = 0.40f * fade), hit.position, runoff, 0.95.dp.toPx(), StrokeCap.Round)
    drawRainGlassBead(runoff, 1.25.dp.toPx(), palette, fade)
    if (hit.age < 0.6f) repeat(3) { index ->
        val side = if (index == 0) -1f else 1f
        val offset = Offset(side * radius * (0.50f + index * 0.17f), -radius * (0.30f + index * 0.1f))
        drawRainGlassBead(hit.position + offset, 1.dp.toPx(), palette, fade * 0.8f)
    }
}
