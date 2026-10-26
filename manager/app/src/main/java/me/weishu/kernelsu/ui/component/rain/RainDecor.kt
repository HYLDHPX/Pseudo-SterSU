package me.weishu.kernelsu.ui.component.rain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.isActive
import me.weishu.kernelsu.ui.component.rememberSystemAnimationsEnabled
import me.weishu.kernelsu.ui.theme.LocalUiPassthroughSettings
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.InterfaceStyleTheme
import top.yukonga.miuix.kmp.basic.CardDefaults
import kotlin.random.Random

@Composable
@ReadOnlyComposable
fun isRainInterfaceStyle(): Boolean = LocalRainSettings.current.active

val LocalRainSceneProgress = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0.37f) }

/** Card droplets only animate when rain, motion and system animations all allow it. */
val LocalRainCardMotionEnabled = staticCompositionLocalOf { false }
@Composable
fun rememberRainSceneProgress(
    enabled: Boolean,
    style: RainStyle,
    theme: InterfaceStyleTheme? = null,
    settings: RainSettings = RainSettings(scene = style),
): State<Float> {
    return rememberRainProgress(enabled, RainSceneSpec.fromSettings(settings).cycleMillis)
}

@Composable
private fun rememberRainProgress(enabled: Boolean, cycleMillis: Int): State<Float> {
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val animations = rememberSystemAnimationsEnabled()
    val progress = remember { RainSceneClock() }
    val currentCycle by rememberUpdatedState(cycleMillis)
    SideEffect { progress.running = enabled && resumed && animations }
    LaunchedEffect(enabled, resumed, animations) {
        if (enabled && resumed && animations) {
            var previous = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    progress.advance((now - previous) / 1_000_000_000.0, currentCycle)
                    previous = now
                }
            }
        }
    }
    return progress
}

/** Ripple seeds are derived from drop positions, so impacts land where streaks fall. */
private data class RainImpact(val x: Float, val baseline: Float, val phase: Float, val scale: Float)

/** Far rain stays behind content; the root weather modifier owns the near layer. */
@Composable
fun RainBackdrop(modifier: Modifier = Modifier, overlayOnly: Boolean = false) {
    val settings = LocalRainSettings.current
    val covered = LocalRainCoveredByWallpaper.current
    if (!settings.drawingEnabled(covered)) return
    val palette = rainPalette(settings, isInDarkTheme())
    val spec = remember(settings) { RainSceneSpec.fromSettings(settings) }
    val progress = LocalRainSceneProgress.current
    Box(modifier.fillMaxSize().drawWithCache {
        val drops = rainParticles(rainViewportDropCount(spec.dropCount, size, density), settings.scene)
        val streaks = drops.mapIndexed { index, drop -> rainStreakPaint(drop, index, spec, palette, false, this) }
        val random = Random(settings.scene.value.hashCode() xor IMPACT_SEED)
        // One impact per visible streak keeps the ripple field proportional to the rain.
        val impacts = drops.map { drop ->
            RainImpact(
                x = drop.x,
                baseline = 0.86f + random.nextFloat() * 0.12f,
                phase = random.nextFloat(),
                scale = 0.7f + random.nextFloat() * 0.6f,
            )
        }
        val sky = Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom))
        val clouds = if (overlayOnly) emptyList() else rainCloudPaints(size, palette, settings.scene)
        val thunderPaint = if (settings.scene == RainStyle.Thunderstorm && settings.thunder &&
            !settings.foregroundDrawingEnabled(covered))
            rainThunderPaint(size, palette) else null
        onDrawBehind {
            if (!overlayOnly) {
                drawRect(sky)
                drawRainClouds(clouds, progress.rainSeconds() / 30f)
            }
            val seconds = progress.rainSeconds()
            val thunder = rainThunderPulse(settings, seconds, progress.rainMotionAllowed())
            if (thunderPaint != null && thunder > 0f) {
                drawRainThunder(thunderPaint, thunder, (seconds / settings.thunderInterval).toInt())
            }
            val margin = rainTravelMarginDp(spec).dp.toPx()
            val wind = rainWindAt(spec.windRatio, seconds)
            drops.forEachIndexed { index, drop ->
                if (settings.foregroundDrawingEnabled(covered) && rainFrontParticle(index)) return@forEachIndexed
                val fieldProgress = rainFieldProgress(progress.value, index, size, density, margin)
                val tip = rainParticleTip(drop, fieldProgress, size, wind, margin)
                drawRainStreak(streaks[index], tip, wind)
            }
            if (settings.intensity > 0f) drawRainImpacts(impacts, palette, spec, progress.rainSeconds() / 14f)
        }
    })
}

private const val IMPACT_SEED = 0x3C1F

/**
 * Expanding ground ring per streak. Alpha and radius key off the drop's own phase so the
 * field never pulses in lockstep, which would read as a UI animation instead of weather.
 */
private fun DrawScope.drawRainImpacts(
    impacts: List<RainImpact>,
    palette: RainPalette,
    spec: RainSceneSpec,
    progress: Float,
) {
    val strength = if (spec.dropCount == 0) 0f else (spec.dropCount / 72f).coerceIn(0.15f, 1f)
    impacts.forEach { impact ->
        val phase = (progress * 2f + impact.phase) % 1f
        val radius = (6f + phase * 22f * impact.scale).dp.toPx()
        val fade = (1f - phase) * 0.16f * strength
        if (fade <= 0.002f) return@forEach
        drawOval(
            palette.ripple.copy(alpha = fade),
            Offset(size.width * impact.x - radius, size.height * impact.baseline - radius * 0.25f),
            Size(radius * 2f, radius * 0.5f),
            style = Stroke(0.6.dp.toPx()),
        )
    }
}

@Composable
fun Modifier.rainMiuixCardSurface(enabled: Boolean = true, capHeight: Dp = 13.dp, shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    if (!enabled || !isRainInterfaceStyle()) return this
    val settings = LocalRainSettings.current
    val palette = rainPalette(settings, isInDarkTheme())
    val alpha = settings.backgroundSurfaceOpacity(
        if (LocalUiPassthroughSettings.current.enabled) LocalUiPassthroughSettings.current.surfaceOpacity
        else settings.surfaceOpacity,
    )
    return clip(shape)
        .background(palette.surfaceTop.copy(alpha = alpha), shape)
        .rainCardDecoration(capHeight, shape = shape)
}

/** Decoration only, so Material cards keep their warning/error container colors. */
@Composable
fun Modifier.rainCardDecoration(
    capHeight: Dp = 13.dp,
    shape: Shape? = null,
    drawInterior: Boolean = true,
): Modifier {
    val settings = LocalRainSettings.current
    if (!settings.active) return this
    val cardShape = shape ?: RoundedCornerShape(16.dp)
    val linked = rainInteractionTarget(RainSurfaceKind.Card, shape = cardShape)
    if (settings.decorationOpacity <= 0f) return linked
    if (settings.cardSurface == RainDecoration.Off && settings.cardBorder == RainCardBorder.Off &&
        (!drawInterior || settings.cardInterior == RainCardInterior.Off)) return linked
    val palette = rainPalette(settings, isInDarkTheme())
    val progress = LocalRainSceneProgress.current
    val motionEnabled = LocalRainCardMotionEnabled.current && progress.rainMotionAllowed()
    val seed = rememberRainOrnamentSeed()
    val edge = settings.edgeStrength(RainSurfaceKind.Card) * rainWetness(settings.intensity)
    val interior = settings.interiorStrength() * rainWetness(settings.intensity)
    val thunderAllowed = motionEnabled && settings.drawingEnabled(LocalRainCoveredByWallpaper.current)
    return linked.clip(cardShape)
        .drawWithCache {
            val paints = rainCardPaints(size, this, layoutDirection, palette, cardShape)
            val height = rainCardDecorationHeight(capHeight.toPx(), size.width, size.height)
            val edgePaints = rainSurfacePaints(size, this, palette, height, seed = seed)
            onDrawWithContent {
                val seconds = if (motionEnabled) progress.rainSeconds() else 0f
                if (drawInterior) drawRainCardInterior(settings.cardInterior, palette, interior, paints, seconds, seed)
                drawContent()
                val thunder = rainThunderPulse(settings, seconds, thunderAllowed)
                drawRainCardFrame(settings.cardBorder, palette, edge * (0.85f + thunder * 0.15f), paints)
                if (height > 0f) drawRainSurfaceDecoration(settings.cardSurface, palette, seconds, edge, edgePaints)
            }
        }
}

@Composable
fun rainMiuixCardColors(color: Color) = CardDefaults.defaultColors(
    color = if (isRainInterfaceStyle()) Color.Transparent else color,
)

internal fun rainCardDecorationHeight(requestedHeight: Float, width: Float, height: Float): Float =
    if (requestedHeight <= 0f || width < 72f || height < 48f) 0f else minOf(requestedHeight, height * 0.19f)

@Composable
fun rainNavigationContainerColor(): Color = rainPalette(LocalRainSettings.current, isInDarkTheme())
    .surfaceBottom.copy(alpha = LocalRainSettings.current.backgroundSurfaceOpacity(LocalRainSettings.current.navigationOpacity))

@Composable
fun Modifier.rainNavigationSurface(shape: Shape, paintBackground: Boolean = true): Modifier {
    if (!isRainInterfaceStyle()) return this
    return clip(shape).then(if (paintBackground) Modifier.background(rainNavigationContainerColor(), shape) else Modifier)
        .rainChromeDecoration(RainChromeArea.Navigation, shape = shape)
}

@Composable
fun Modifier.rainNavigationIndicator(
    shape: Shape, paintBackground: Boolean = true, interactionKey: Any? = Unit,
    selected: Boolean = true,
): Modifier {
    if (!isRainInterfaceStyle()) return this
    val settings = LocalRainSettings.current
    val palette = rainPalette(settings, isInDarkTheme())
    val progress = LocalRainSceneProgress.current
    val motion = LocalRainCardMotionEnabled.current && progress.rainMotionAllowed()
    // One shared clock, no animation job for any navigation item.
    val selectedAt = remember(interactionKey, selected) { if (selected) progress.rainSeconds() else Float.NEGATIVE_INFINITY }
    if (!selected) return this
    val strength = settings.edgeStrength(RainSurfaceKind.Navigation)
    return then(if (paintBackground) Modifier.clip(shape).background(palette.ripple.copy(alpha = 0.12f), shape) else Modifier)
        .then(if (paintBackground && strength > 0f && settings.navigationDecoration != RainDecoration.Off)
            Modifier.border(0.7.dp, palette.outline.copy(alpha = 0.3f * strength), shape) else Modifier)
        .drawWithCache {
            val maxRadius = minOf(size.width, size.height) * 0.65f
            // Clip the water only. A whole navigation item may also contain a long label.
            val rippleClip = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
            onDrawWithContent {
                drawContent()
                val pulse = if (motion && settings.navigationDecoration != RainDecoration.Off)
                    rainSelectionPulse(progress.rainSeconds() - selectedAt) else 0f
                if (pulse > 0f && strength > 0f) {
                    val radius = (0.18f + 0.82f * (1f - pulse)) * maxRadius
                    clipPath(rippleClip) {
                        drawCircle(palette.ripple.copy(alpha = pulse * strength * 0.55f), radius, center,
                            style = Stroke(1.25.dp.toPx()))
                        drawCircle(palette.highlight.copy(alpha = pulse * strength * 0.55f), radius * 0.8f, center,
                            style = Stroke(0.7.dp.toPx()))
                    }
                }
            }
        }
}

@Composable
@ReadOnlyComposable
fun rainTopBarContainerColor(): Color = rainPalette(LocalRainSettings.current, isInDarkTheme())
    .surfaceBottom.copy(alpha = LocalRainSettings.current.backgroundSurfaceOpacity(LocalRainSettings.current.topBarOpacity))

@Composable
@ReadOnlyComposable
fun rainTopBarContentColor(): Color = rainPalette(LocalRainSettings.current, isInDarkTheme()).content
