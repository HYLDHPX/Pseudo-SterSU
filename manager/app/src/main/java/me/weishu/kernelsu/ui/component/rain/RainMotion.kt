package me.weishu.kernelsu.ui.component.rain

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import kotlin.math.sin
import kotlin.math.sqrt

/** Travel and wall time are independent: changing rain speed never restarts either clock. */
internal data class RainTime(val travel: Double = 0.37, val seconds: Double = 0.0)

internal fun advanceRainTime(time: RainTime, deltaSeconds: Double, cycleMillis: Int): RainTime {
    if (!deltaSeconds.isFinite() || deltaSeconds <= 0.0 || cycleMillis <= 0) return time
    // A delayed frame must not teleport droplets across the screen after resume or a stall.
    val delta = deltaSeconds.coerceAtMost(0.05)
    return RainTime(time.travel + delta * 1000.0 / cycleMillis, time.seconds + delta)
}

internal class RainSceneClock : State<Float> {
    private val time = mutableStateOf(RainTime())
    private val allowed = mutableStateOf(false)
    var running: Boolean
        get() = allowed.value
        set(value) { allowed.value = value }
    override val value: Float get() = time.value.travel.toFloat()
    val seconds: Float get() = time.value.seconds.toFloat()
    fun advance(deltaSeconds: Double, cycleMillis: Int) {
        time.value = advanceRainTime(time.value, deltaSeconds, cycleMillis)
    }
}

internal fun State<Float>.rainSeconds(): Float = (this as? RainSceneClock)?.seconds ?: value * 14f
internal fun State<Float>.rainMotionAllowed(): Boolean = (this as? RainSceneClock)?.running == true

/** Counts follow visible dp area, with a hard budget for large tablets. Seeds stay stable. */
internal fun rainViewportDropCount(baseCount: Int, viewport: Size, density: Float): Int {
    if (baseCount <= 0 || density <= 0f || !density.isFinite() ||
        viewport.width <= 0f || viewport.height <= 0f ||
        !viewport.width.isFinite() || !viewport.height.isFinite()) return 0
    val area = viewport.width / density * (viewport.height / density)
    val scale = (area / (400f * 800f)).coerceIn(0.18f, 2.5f)
    return (baseCount * scale).toInt().coerceIn(1, 128)
}

internal fun rainDepthSpeed(index: Int): Float = when (index % 3) {
    0 -> 1.18f
    1 -> 0.68f
    else -> 0.88f
}

/** A dp based fall speed keeps the same rain pace in a small window and on a tablet. */
internal fun rainFieldProgress(travel: Float, index: Int, viewport: Size, density: Float, margin: Float): Float =
    if (density > 0f && viewport.height + margin * 2f > 0f)
        travel * rainDepthSpeed(index) * 840f * density / (viewport.height + margin * 2f) else travel

internal fun rainFieldCycleMillis(cycleMillis: Int, index: Int, viewport: Size, density: Float, margin: Float): Int =
    if (density > 0f && viewport.height + margin * 2f > 0f)
        (cycleMillis * (viewport.height + margin * 2f) / (840f * density * rainDepthSpeed(index))).toInt().coerceAtLeast(1)
    else cycleMillis

internal fun rainWindAt(baseWind: Float, seconds: Float): Float {
    if (!baseWind.isFinite() || !seconds.isFinite()) return 0f
    // Gusts stay on the requested side and do not make still-air rain drift sideways.
    return (baseWind * (1f + 0.12f * sin(seconds * 0.31f) + 0.06f * sin(seconds * 0.73f))).coerceIn(-0.5f, 0.5f)
}

internal fun rainWetness(intensity: Float): Float = 0.35f + 0.65f * sqrt(intensity.coerceIn(0f, 1f))

/** Shared-clock feedback: one pulse on selection, with a quiet settled state. */
internal fun rainSelectionPulse(elapsedSeconds: Float): Float =
    if (elapsedSeconds.isFinite() && elapsedSeconds in 0f..0.85f) 1f - elapsedSeconds / 0.85f else 0f

internal data class RainRunoff(val travel: Float, val alpha: Float, val merge: Float)

/** Slow droplets grow as they merge, then drain away without snapping at the bottom. */
internal fun rainRunoffState(seconds: Float, lane: Int, seed: Float): RainRunoff {
    val phase = ((seconds / (6.5f + lane % 3 * 1.7f) + seed + lane * 0.31f) % 1f + 1f) % 1f
    val alpha = minOf(phase / 0.12f, (1f - phase) / 0.16f, 1f).coerceIn(0f, 1f)
    return RainRunoff(phase, alpha, 0.8f + phase * 0.55f)
}

/** Top-bar water gathers for most of a cycle, then releases a single hanging drop. */
internal fun rainCollectingDrip(seconds: Float, lane: Int, seed: Float): RainRunoff {
    val phase = ((seconds / (4.8f + lane % 3) + seed + lane * 0.27f) % 1f + 1f) % 1f
    val fall = ((phase - 0.65f) / 0.35f).coerceIn(0f, 1f)
    return RainRunoff(fall * fall, (1f - fall * fall).coerceIn(0f, 1f), 0.5f + minOf(phase / 0.65f, 1f) * 0.8f)
}
