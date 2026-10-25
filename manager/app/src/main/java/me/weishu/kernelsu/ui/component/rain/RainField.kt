package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.geometry.CornerRadius
import kotlin.random.Random

internal data class RainParticle(val x: Float, val phase: Float, val length: Float, val alpha: Float)

internal fun rainParticles(count: Int, scene: RainStyle): List<RainParticle> {
    val random = Random(scene.value.hashCode())
    return List(count.coerceIn(0, 160)) {
        RainParticle(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat())
    }
}

internal fun rainFrontParticle(index: Int): Boolean = index % 3 == 0

internal const val RAIN_IMPACT_DURATION_MILLIS = 900f

internal fun rainInteractionStrength(settings: RainSettings): Float {
    val safe = settings.normalized()
    if (!safe.active || !safe.rainThroughUi || !safe.surfaceInteraction || safe.intensity <= 0f) return 0f
    return minOf(safe.decorationOpacity, safe.foregroundOpacity) * (safe.dropOpacity ?: 1f)
}

private fun phase(value: Float): Float = if (value.isFinite()) ((value % 1f) + 1f) % 1f else 0.37f

/** The same field drives background streaks, foreground streaks and surface impacts. */
internal fun rainParticleTip(particle: RainParticle, progress: Float, viewport: Size, wind: Float, margin: Float): Offset {
    val y = phase(particle.phase + progress) * (viewport.height + margin * 2f) - margin
    return Offset(particle.x * viewport.width + (y - viewport.height * 0.5f) * wind, y)
}

internal enum class RainSurfaceKind { Card, TopBar, Navigation, Sidebar }

internal data class RainHitSurface(
    val bounds: Rect,
    val kind: RainSurfaceKind,
    val vertical: Boolean = false,
    val edgeOnRight: Boolean = true,
    val corners: RainCorners? = null,
    val shapeBounds: Rect = bounds,
)

internal data class RainCorners(
    val topLeft: CornerRadius, val topRight: CornerRadius,
    val bottomRight: CornerRadius, val bottomLeft: CornerRadius,
)

internal data class RainSurfaceHit(
    val position: Offset, val age: Float, val vertical: Boolean,
    val kind: RainSurfaceKind = RainSurfaceKind.Card,
    val tangent: Offset = if (vertical) Offset(0f, 1f) else Offset(1f, 0f),
)

/** Index in the unwound x coordinate of the slanted field, once per placement frame. */
internal class RainSurfaceIndex(surfaces: List<RainHitSurface>, viewport: Size, wind: Float) {
    private val bins = List(12) { mutableListOf<RainHitSurface>() }
    private val width = viewport.width.coerceAtLeast(1f)
    init {
        for (surface in surfaces) {
            val b = surface.bounds
            val shiftTop = (b.top - viewport.height * 0.5f) * wind
            val shiftBottom = (b.bottom - viewport.height * 0.5f) * wind
            val left = b.left - maxOf(shiftTop, shiftBottom)
            val right = b.right - minOf(shiftTop, shiftBottom)
            if (right < 0f || left > width) continue
            val first = (left / width * bins.size).toInt().coerceIn(0, bins.lastIndex)
            val last = (right / width * bins.size).toInt().coerceIn(0, bins.lastIndex)
            for (index in first..last) bins[index].add(surface)
        }
    }
    fun candidates(particle: RainParticle): List<RainHitSurface> =
        bins[(particle.x * bins.size).toInt().coerceIn(0, bins.lastIndex)]
}

internal fun visibleRainSurface(
    bounds: Rect,
    unclippedBounds: Rect,
    viewport: Size,
    kind: RainSurfaceKind,
    vertical: Boolean,
    edgeOnRight: Boolean,
): RainHitSurface? {
    if (bounds.width < 20f || bounds.height < 20f || !bounds.overlaps(Rect(Offset.Zero, viewport))) return null
    val clippedEdge = if (!vertical && kind == RainSurfaceKind.TopBar) abs(bounds.bottom - unclippedBounds.bottom)
        else if (!vertical) abs(bounds.top - unclippedBounds.top)
        else if (edgeOnRight) abs(bounds.right - unclippedBounds.right) else abs(bounds.left - unclippedBounds.left)
    if (clippedEdge > 1.5f) return null
    return RainHitSurface(bounds, kind, vertical, edgeOnRight)
}

/** Find the last physical crossing, including wraparound of the shared scene clock. */
internal fun rainSurfaceHit(
    particle: RainParticle,
    progress: Float,
    viewport: Size,
    surface: RainHitSurface,
    wind: Float,
    margin: Float,
    cycleMillis: Int,
    cornerInset: Float,
): RainSurfaceHit? {
    val bounds = surface.bounds
    if (viewport.width <= 0f || viewport.height <= 0f || cycleMillis <= 0 ||
        !viewport.width.isFinite() || !viewport.height.isFinite() || !bounds.left.isFinite() ||
        !bounds.top.isFinite() || !bounds.right.isFinite() || !bounds.bottom.isFinite() ||
        !wind.isFinite() || !margin.isFinite() || !progress.isFinite() || bounds.width <= 0f || bounds.height <= 0f) return null
    val travel = viewport.height + margin * 2f
    if (travel <= 0f) return null
    val insetX = minOf(cornerInset, bounds.width * 0.2f)
    val insetY = minOf(cornerInset, bounds.height * 0.2f)
    val verticalHit = surface.vertical && abs(wind) >= 0.025f
    var y: Float
    var x: Float
    if (verticalHit) {
        x = if (surface.edgeOnRight) bounds.right else bounds.left
        y = viewport.height * 0.5f + (x - particle.x * viewport.width) / wind
        if (y !in bounds.top..bounds.bottom) return null
        if (surface.corners == null && y !in (bounds.top + insetY)..(bounds.bottom - insetY)) return null
    } else {
        y = if (surface.kind == RainSurfaceKind.TopBar) bounds.bottom else bounds.top
        x = particle.x * viewport.width + (y - viewport.height * 0.5f) * wind
        if (x !in bounds.left..bounds.right) return null
        if (surface.corners == null && x !in (bounds.left + insetX)..(bounds.right - insetX)) return null
    }
    var tangent = if (verticalHit) Offset(0f, 1f) else Offset(1f, 0f)
    surface.corners?.let { corners ->
        val b = surface.shapeBounds
        val bottom = surface.kind == RainSurfaceKind.TopBar && !verticalHit
        val left = if (verticalHit) !surface.edgeOnRight else x < b.center.x
        val lower = if (verticalHit) y > b.center.y else bottom
        val radius = when {
            lower && left -> corners.bottomLeft
            lower -> corners.bottomRight
            left -> corners.topLeft
            else -> corners.topRight
        }
        if (radius.x > 0f && radius.y > 0f) {
            val cx = if (left) b.left + radius.x else b.right - radius.x
            val cy = if (lower) b.bottom - radius.y else b.top + radius.y
            val curved = if (verticalHit) (if (lower) y > cy else y < cy) else (if (left) x < cx else x > cx)
            if (curved) {
                val dx = particle.x * viewport.width + (cy - viewport.height * 0.5f) * wind - cx
                val a = wind * wind / (radius.x * radius.x) + 1f / (radius.y * radius.y)
                val bb = 2f * dx * wind / (radius.x * radius.x)
                val c = dx * dx / (radius.x * radius.x) - 1f
                val disc = bb * bb - 4f * a * c
                if (disc < 0f) return null
                val roots = listOf((-bb - sqrt(disc)) / (2f * a), (-bb + sqrt(disc)) / (2f * a))
                val root = roots.firstOrNull { dy ->
                    val hitX = particle.x * viewport.width + (cy + dy - viewport.height * 0.5f) * wind
                    (if (left) hitX <= cx + 0.01f else hitX >= cx - 0.01f) &&
                        (if (lower) dy >= -0.01f else dy <= 0.01f)
                } ?: return null
                y = cy + root
                x = particle.x * viewport.width + (y - viewport.height * 0.5f) * wind
                val normal = Offset((x - cx) / (radius.x * radius.x), (y - cy) / (radius.y * radius.y))
                val magnitude = normal.getDistance().coerceAtLeast(0.0001f)
                tangent = Offset(-normal.y / magnitude, normal.x / magnitude)
            }
        }
    }
    if (x !in bounds.left..bounds.right || y !in bounds.top..bounds.bottom) return null
    if (y !in 0f..viewport.height || x !in 0f..viewport.width) return null
    val hitPhase = (y + margin) / travel
    if (hitPhase !in 0f..1f) return null
    val ageMillis = phase(phase(particle.phase + progress) - hitPhase) * cycleMillis
    if (ageMillis > RAIN_IMPACT_DURATION_MILLIS) return null
    return RainSurfaceHit(Offset(x, y), (ageMillis / RAIN_IMPACT_DURATION_MILLIS).coerceIn(0f, 1f), verticalHit, surface.kind, tangent)
}

/** A single slow glow, never alternating flashes; no thunder in still/reduced-motion mode. */
internal fun rainThunderPulse(settings: RainSettings, seconds: Float, motionAllowed: Boolean): Float {
    if (!settings.active || !settings.thunder || !settings.motion || !motionAllowed ||
        settings.scene != RainStyle.Thunderstorm || settings.intensity <= 0f || !seconds.isFinite()) return 0f
    val interval = settings.thunderInterval.takeIf { it.isFinite() }?.coerceIn(8f, 60f) ?: 18f
    val time = ((seconds % interval + interval) % interval - interval * 0.7f) / 1.8f
    if (time !in 0f..1f) return 0f
    val value = sin(time * PI).toFloat()
    return value * value
}
