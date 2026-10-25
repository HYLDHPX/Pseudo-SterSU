package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class RainRefinementTest {
    @Test fun sharedMotionPermissionStopsFeedbackWithoutResettingTheClock() {
        val clock = RainSceneClock()
        assertFalse(clock.rainMotionAllowed())
        clock.running = true
        clock.advance(0.02, 14_000)
        val travel = clock.value
        val seconds = clock.rainSeconds()
        assertTrue(clock.rainMotionAllowed())
        clock.running = false
        assertFalse(clock.rainMotionAllowed())
        assertEquals(travel, clock.value, 0f)
        assertEquals(seconds, clock.rainSeconds(), 0f)
        clock.running = true
        clock.advance(0.02, 7_000)
        assertTrue(clock.value > travel)
        assertTrue(clock.rainSeconds() > seconds)
    }

    @Test fun changingSpeedChangesVelocityWithoutResettingTravelOrIndependentTime() {
        val start = RainTime(travel = 2.99, seconds = 41.0)
        val before = advanceRainTime(start, 0.02, 14_000)
        val after = advanceRainTime(before, 0.02, 7_000)
        assertEquals(2.99 + 0.02 / 14.0, before.travel, 1e-10)
        assertEquals(before.travel + 0.02 / 7.0, after.travel, 1e-10)
        assertEquals(41.04, after.seconds, 1e-10)
        assertEquals(2.0, (after.travel - before.travel) / (before.travel - start.travel), 1e-8)
        assertTrue(after.travel > before.travel)
        val crossing = advanceRainTime(RainTime(0.999, 0.0), 0.05, 7_000)
        assertTrue("Do not wrap the shared clock: depth speeds would jump", crossing.travel > 1.0)
    }

    @Test fun pausedAndInvalidFramesDoNotMoveAndResumeCannotTeleport() {
        val start = RainTime(20.0, 80.0)
        for (delta in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(start, advanceRainTime(start, delta, 14_000))
        }
        assertEquals(start, advanceRainTime(start, 0.02, 0))
        val resumed = advanceRainTime(start, 100.0, 14_000)
        assertEquals(80.05, resumed.seconds, 1e-10)
        assertTrue(resumed.travel - start.travel < 0.004)
    }

    @Test fun densityUsesDpAreaAndKeepsCompactAndTabletBudgets() {
        assertEquals(48, rainViewportDropCount(48, Size(400f, 800f), 1f))
        assertEquals(48, rainViewportDropCount(48, Size(800f, 1600f), 2f))
        assertEquals(48, rainViewportDropCount(48, Size(800f, 400f), 1f))
        assertEquals(12, rainViewportDropCount(48, Size(200f, 400f), 1f))
        assertEquals(120, rainViewportDropCount(48, Size(1000f, 800f), 1f))
        assertEquals(128, rainViewportDropCount(80, Size(6000f, 4000f), 1f))
        assertEquals(0, rainViewportDropCount(0, Size(400f, 800f), 1f))
        for (viewport in listOf(Size.Zero, Size(Float.NaN, 100f), Size(100f, Float.POSITIVE_INFINITY))) {
            assertEquals(0, rainViewportDropCount(48, viewport, 1f))
        }
        assertEquals(0, rainViewportDropCount(48, Size(400f, 800f), Float.NaN))
    }

    @Test fun increasingDensityRetainsExistingParticleSeeds() {
        val scene = RainStyle.Thunderstorm
        assertEquals(rainParticles(15, scene), rainParticles(100, scene).take(15))
    }

    @Test fun fallSpeedIsPhysicalAndDepthLayersMoveAtDifferentRates() {
        fun fall(viewport: Size, density: Float, index: Int): Float {
            val drop = RainParticle(0.5f, 0.25f, 0.5f, 0.5f)
            val margin = 40f * density
            val from = rainParticleTip(drop, rainFieldProgress(0.05f, index, viewport, density, margin), viewport, 0f, margin)
            val to = rainParticleTip(drop, rainFieldProgress(0.06f, index, viewport, density, margin), viewport, 0f, margin)
            return (to.y - from.y) / density
        }
        val near = fall(Size(400f, 800f), 1f, 0)
        assertEquals(near, fall(Size(400f, 400f), 1f, 0), 0.001f)
        assertEquals(near, fall(Size(1200f, 2400f), 3f, 0), 0.001f)
        assertTrue(near > fall(Size(400f, 800f), 1f, 2))
        assertTrue(fall(Size(400f, 800f), 1f, 2) > fall(Size(400f, 800f), 1f, 1))
        for (index in 0..2) {
            val viewport = Size(400f, 800f)
            val phase = rainFieldProgress(1f, index, viewport, 1f, 40f)
            val cycle = rainFieldCycleMillis(14_000, index, viewport, 1f, 40f)
            assertEquals(14_000f, phase * cycle, 2f)
        }
    }

    @Test fun gustsAreSmoothBoundedAndStillAirStaysStill() {
        for (base in listOf(-0.5f, -0.12f, 0f, 0.12f, 0.5f)) {
            var previous = rainWindAt(base, 0f)
            repeat(6001) { frame ->
                val wind = rainWindAt(base, frame / 60f)
                assertTrue(wind in -0.5f..0.5f)
                assertTrue(abs(wind - previous) < 0.002f)
                if (base == 0f) assertEquals(0f, wind, 0f)
                else assertTrue(wind * base > 0f)
                previous = wind
            }
        }
        assertEquals(0f, rainWindAt(Float.NaN, 0f), 0f)
    }

    @Test fun runoffGrowsMergesAndFadesBeforeWrapping() {
        val first = rainRunoffState(0.01f, 0, 0f)
        val middle = rainRunoffState(3.25f, 0, 0f)
        val last = rainRunoffState(6.49f, 0, 0f)
        assertTrue(first.alpha < 0.02f)
        assertEquals(1f, middle.alpha, 0f)
        assertTrue(last.alpha < 0.02f)
        assertTrue(first.merge < middle.merge && middle.merge < last.merge)
        assertNotEquals(rainRunoffState(2f, 0, 0.1f), rainRunoffState(2f, 0, 0.8f))
        assertEquals(rainRunoffState(2f, 1, 0.1f), rainRunoffState(2f, 1, 0.1f))
        assertTrue(rainWetness(1f) > rainWetness(0f))
    }

    @Test fun topBarCollectsWaterBeforeReleasingOneDrip() {
        val collecting = rainCollectingDrip(2.4f, 0, 0f)
        val falling = rainCollectingDrip(4.2f, 0, 0f)
        val leaving = rainCollectingDrip(4.799f, 0, 0f)
        assertEquals(0f, collecting.travel, 0f)
        assertTrue(collecting.merge > rainCollectingDrip(0f, 0, 0f).merge)
        assertTrue(falling.travel > collecting.travel)
        assertTrue(leaving.travel > falling.travel)
        assertTrue(leaving.alpha < 0.01f)
    }

    @Test fun selectedNavigationPulsesOnceAndThenStaysQuiet() {
        assertEquals(1f, rainSelectionPulse(0f), 0f)
        assertTrue(rainSelectionPulse(0.4f) > rainSelectionPulse(0.7f))
        for (elapsed in listOf(-0.1f, 0.85f, 1f, 20f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, rainSelectionPulse(elapsed), 0f)
        }
    }

    @Test fun thunderCadenceIsIndependentOfRainSpeedWithOneSmoothEnvelope() {
        val settings = RainSettings(active = true, scene = RainStyle.Thunderstorm, thunderInterval = 18f)
        val center = settings.thunderInterval * 0.7f + 0.9f
        for (speed in listOf(0.4f, 1f, 2f)) {
            assertEquals(1f, rainThunderPulse(settings.copy(speed = speed), center, true), 0.001f)
            assertEquals(1f, rainThunderPulse(settings.copy(speed = speed), center + 18f, true), 0.001f)
        }
        assertEquals(0f, rainThunderPulse(settings.copy(thunderInterval = 30f), center, true), 0f)
        assertEquals(0f, rainThunderPulse(settings, center, false), 0f)
    }

    private fun corners(radius: Float) = RainCorners(CornerRadius(radius), CornerRadius(radius), CornerRadius(radius), CornerRadius(radius))

    @Test fun roundedCornerImpactsLandOnTheCurveInsteadOfTheBoundingBox() {
        val viewport = Size(400f, 800f)
        val bounds = Rect(100f, 200f, 300f, 400f)
        val surface = RainHitSurface(bounds, RainSurfaceKind.Card, corners = corners(40f))
        val drop = RainParticle(0.275f, 0f, 0f, 0f) // x = 110, inside the upper-left arc.
        val hitY = 240f - kotlin.math.sqrt(40f * 40f - 30f * 30f)
        val hit = rainSurfaceHit(drop, (hitY + 40f) / 880f + 0.001f, viewport, surface, 0f, 40f, 14_000, 12f)!!
        assertEquals(110f, hit.position.x, 0.001f)
        assertEquals(hitY, hit.position.y, 0.001f)
        assertTrue(hit.position.y > bounds.top + 10f)
        assertEquals(1f, hit.tangent.getDistance(), 0.001f)
    }

    @Test fun circularAndEllipticTargetsUseTheirActualOutline() {
        val viewport = Size(400f, 800f)
        val circle = RainHitSurface(Rect(100f, 200f, 200f, 300f), RainSurfaceKind.Navigation, corners = corners(50f))
        val drop = RainParticle(0.275f, 0f, 0f, 0f)
        val circleY = 250f - kotlin.math.sqrt(2500f - 1600f)
        val hit = rainSurfaceHit(drop, (circleY + 40f) / 880f + 0.001f, viewport, circle, 0f, 40f, 14_000, 12f)!!
        assertEquals(Offset(110f, circleY), hit.position)
        assertEquals(RainSurfaceKind.Navigation, hit.kind)
        val ellipse = circle.copy(corners = RainCorners(CornerRadius(50f, 25f), CornerRadius(50f, 25f),
            CornerRadius(50f, 25f), CornerRadius(50f, 25f)))
        val ellipseY = 225f - 25f * kotlin.math.sqrt(1f - 1600f / 2500f)
        val ellipticHit = rainSurfaceHit(drop, (ellipseY + 40f) / 880f + 0.001f, viewport, ellipse, 0f, 40f, 14_000, 12f)!!
        assertEquals(ellipseY, ellipticHit.position.y, 0.001f)
    }

    @Test fun spatialIndexNeverDropsARealHitAndReducesCandidates() {
        val viewport = Size(400f, 800f)
        val surfaces = List(12) { index -> RainHitSurface(Rect(index * 32f, 200f, index * 32f + 28f, 400f), RainSurfaceKind.Card) }
        for (wind in listOf(-0.4f, 0f, 0.4f)) {
            val index = RainSurfaceIndex(surfaces, viewport, wind)
            repeat(40) { seed ->
                val drop = RainParticle((seed + 0.5f) / 40f, 0f, 0f, 0f)
                val candidates = index.candidates(drop)
                for (surface in surfaces) {
                    val hit = rainSurfaceHit(drop, 240f / 880f + 0.001f, viewport, surface, wind, 40f, 14_000, 0f)
                    if (hit != null) assertTrue(candidates.contains(surface))
                }
                if (wind == 0f) assertTrue(candidates.size <= 3)
            }
        }
    }

    @Test fun renderCachesAndAllNavigationFamiliesUseSharedClockFeedback() {
        val root = File("src/main/java/me/weishu/kernelsu/ui/component")
        for (name in listOf("rain/RainCardArtwork.kt", "rain/RainSurfaceDecoration.kt", "rain/RainDecor.kt", "rain/RainInteractions.kt")) {
            assertTrue(File(root, name).readText().contains("drawWithCache"))
            assertTrue(File(root, name).readText().contains("rainMotionAllowed()"))
        }
        for (name in listOf("BottomBarMiuix.kt", "BottomBarMaterial.kt", "NavigationRailMiuix.kt", "NavigationRailMaterial.kt", "SidebarWidgetRail.kt", "CompactBottomBar.kt")) {
            assertTrue(name, File(root, "bottombar/$name").readText().contains("rainNavigationIndicator"))
        }
        val source = File(root, "rain/RainDecor.kt").readText()
        assertFalse(source.contains("remember(cycleMillis)"))
        assertTrue(source.contains("rememberUpdatedState(cycleMillis)"))
        assertTrue(source.contains("LaunchedEffect(enabled, resumed, animations)"))
    }
}
