package me.weishu.kernelsu.ui.component.rain

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class RainAtmosphericRegressionTest {
    @Test fun drizzleFallsAtAnEvenPaceAndRetainsTheFullSpeedRange() {
        val settings = RainSettings(active = true)
        val cycle = RainSceneSpec.fromSettings(settings).cycleMillis
        assertEquals(6_000, cycle)
        assertEquals(15_000, RainSceneSpec.fromSettings(settings.copy(speed = 0.4f)).cycleMillis)
        assertEquals(3_000, RainSceneSpec.fromSettings(settings.copy(speed = 2f)).cycleMillis)
        val viewport = Size(400f, 800f)
        for (depth in 0..2) {
            val progress = rainFieldProgress(1000f / cycle, depth, viewport, 1f, 40f)
            val distance = progress * 880f
            assertTrue("Steady rain should fall, rather than hang in the air: $distance dp/s", distance in 90f..170f)
        }
    }

    @Test fun disablingNearRainLeavesBackgroundRainAndAnUnobstructedViewThroughPanels() {
        val settings = RainSettings(active = true, rainThroughUi = false)
        assertTrue(settings.drawingEnabled(false))
        assertFalse(settings.foregroundDrawingEnabled(false))
        assertTrue(RainSceneSpec.fromSettings(settings).dropCount > 0)
        for (opacity in listOf(settings.surfaceOpacity, settings.topBarOpacity, settings.navigationOpacity, settings.sidebarOpacity)) {
            assertTrue(settings.backgroundSurfaceOpacity(opacity) <= 0.58f)
        }
        assertEquals(0.2f, settings.backgroundSurfaceOpacity(0.2f), 0f)
        assertEquals(0.94f, settings.surfaceOpacity, 0f) // Keep the saved preference.
        assertEquals(0f, rainInteractionStrength(settings), 0f)
        assertFalse(settings.drawingEnabled(true)) // Wallpaper rain is still opt-in.
    }

    @Test fun backgroundVisibilityChangesOnlyPanelPaintAndRestoresTheRequestedOpacity() {
        val settings = RainSettings(active = true, rainThroughUi = false)
        for (dark in listOf(false, true)) {
            val colors = lightColorScheme().withRainAppearance(settings, dark)
            val palette = rainPalette(settings, dark)
            assertEquals(palette.content, colors.onSurface)
            assertEquals(palette.content, colors.onBackground)
            assertEquals(1f, colors.onSurface.alpha, 0f)
            assertEquals(0.58f, colors.surface.alpha, 0.005f)
        }
        for (restored in listOf(settings.copy(rainThroughUi = true), settings.copy(active = false), settings.copy(intensity = 0f))) {
            assertEquals(0.94f, restored.backgroundSurfaceOpacity(0.94f), 0f)
        }
    }

    @Test fun lightningHasVisibleBranchesAndFitsCompactAndWideWindows() {
        for (viewport in listOf(Size(160f, 240f), Size(400f, 800f), Size(900f, 300f), Size(1200f, 1800f))) {
            for (strike in 0..2) {
                val bolt = rainLightningGeometry(viewport, strike)!!
                assertEquals(10, bolt.trunk.size)
                assertEquals(2, bolt.branches.size)
                assertTrue(bolt.trunk.last().y - bolt.trunk.first().y > viewport.height * 0.6f)
                for (point in bolt.trunk + bolt.branches.flatten()) {
                    assertTrue(point.x in 0f..viewport.width && point.y in 0f..viewport.height)
                }
                assertEquals(bolt, rainLightningGeometry(viewport, strike + 3))
                assertNotEquals(bolt, rainLightningGeometry(viewport, strike + 1))
            }
        }
        for (invalid in listOf(Size.Zero, Size(-1f, 10f), Size(Float.NaN, 10f), Size(10f, Float.POSITIVE_INFINITY))) {
            assertNull(rainLightningGeometry(invalid, 0))
        }
    }

    @Test fun firstLightningArrivesPromptlyWithOneRiseAndOneLongDecay() {
        val settings = RainSettings(active = true, scene = RainStyle.Thunderstorm)
        assertEquals(0f, rainThunderPulse(settings, 0f, true), 0f)
        assertTrue(rainThunderPulse(settings, 2.46f, true) > 0.6f)
        assertEquals(1f, rainThunderPulse(settings, 2.52f, true), 0.001f)
        assertTrue(rainThunderPulse(settings, 2.8f, true) > rainThunderPulse(settings, 3.2f, true))
        assertEquals(0f, rainThunderPulse(settings, 4f, true), 0f)
        for (interval in listOf(8f, 18f, 60f)) {
            val storm = settings.copy(thunderInterval = interval)
            var blooms = 0
            var lit = false
            repeat((interval * 1000).toInt()) { frame ->
                val next = rainThunderPulse(storm, frame / 1000f, true) > 0f
                if (next && !lit) blooms++
                lit = next
            }
            assertEquals("One storm bloom per interval", 1, blooms)
        }
    }
}
