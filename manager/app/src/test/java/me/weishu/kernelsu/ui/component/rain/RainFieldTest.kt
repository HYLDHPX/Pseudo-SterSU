package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class RainFieldTest {
    private val viewport = Size(400f, 800f)
    private val margin = 40f
    private val cycle = 14_000
    private fun atY(y: Float) = (y + margin) / (viewport.height + margin * 2f)

    @Test fun particlesAreDeterministicBoundedAndSplitIntoDisjointLayers() {
        val particles = rainParticles(48, RainStyle.LightRain)
        assertEquals(particles, rainParticles(48, RainStyle.LightRain))
        assertNotEquals(particles, rainParticles(48, RainStyle.Thunderstorm))
        assertEquals(160, rainParticles(Int.MAX_VALUE, RainStyle.LightRain).size)
        assertTrue(rainParticles(-1, RainStyle.LightRain).isEmpty())
        val front = particles.indices.filter(::rainFrontParticle)
        assertEquals(16, front.size)
        assertEquals(48, front.size + particles.indices.count { !rainFrontParticle(it) })
        assertTrue(particles.all { it.x in 0f..1f && it.phase in 0f..1f && it.alpha in 0f..1f })
    }

    @Test fun rainTipsAndImpactsUseTheSameWindTrajectory() {
        val particle = RainParticle(0.5f, 0f, 0.5f, 0.5f)
        val target = RainHitSurface(Rect(20f, 200f, 380f, 400f), RainSurfaceKind.Card)
        val progress = atY(200f)
        val tip = rainParticleTip(particle, progress, viewport, 0.12f, margin)
        val hit = rainSurfaceHit(particle, progress + 0.01f, viewport, target, 0.12f, margin, cycle, 12f)!!
        assertEquals(176f, tip.x, 0.001f)
        assertEquals(200f, tip.y, 0.001f)
        assertEquals(tip.x, hit.position.x, 0.001f)
        assertEquals(tip.y, hit.position.y, 0.001f)
        assertEquals(140f / RAIN_IMPACT_DURATION_MILLIS, hit.age, 0.001f)
        assertFalse(hit.vertical)
        assertNull(rainSurfaceHit(particle, progress + 0.1f, viewport, target, 0.12f, margin, cycle, 12f))
    }

    @Test fun impactAgeSurvivesSceneWraparound() {
        val particle = RainParticle(0.5f, 0.92f, 0.5f, 0.5f)
        val target = RainHitSurface(Rect(0f, 20f, 400f, 120f), RainSurfaceKind.Card)
        val progress = (atY(20f) - particle.phase + 1f) % 1f + 0.02f
        val hit = rainSurfaceHit(particle, progress, viewport, target, 0f, margin, cycle, 12f)!!
        assertEquals(280f / RAIN_IMPACT_DURATION_MILLIS, hit.age, 0.001f)
        val tip = rainParticleTip(particle, progress, viewport, 0.2f, margin)
        val wrapped = rainParticleTip(particle, progress + 1f, viewport, 0.2f, margin)
        assertEquals(tip.x, wrapped.x, 0.001f)
        assertEquals(tip.y, wrapped.y, 0.001f)
    }

    @Test fun sidebarEdgesUseTheirActualSideAndWindCrossing() {
        val left = RainHitSurface(Rect(0f, 0f, 80f, 800f), RainSurfaceKind.Sidebar, vertical = true)
        val right = RainHitSurface(Rect(320f, 0f, 400f, 800f), RainSurfaceKind.Sidebar, vertical = true, edgeOnRight = false)
        val leftHit = rainSurfaceHit(RainParticle(0.25f, 0f, 0f, 0f), atY(300f) + 0.01f,
            viewport, left, 0.2f, margin, cycle, 12f)!!
        val rightHit = rainSurfaceHit(RainParticle(0.75f, 0f, 0f, 0f), atY(500f) + 0.01f,
            viewport, right, 0.2f, margin, cycle, 12f)!!
        assertEquals(Offset(80f, 300f), leftHit.position)
        assertEquals(Offset(320f, 500f), rightHit.position)
        assertTrue(leftHit.vertical && rightHit.vertical)
    }

    @Test fun noWindFallsOntoSidebarTopInsteadOfDividingByZero() {
        val sidebar = RainHitSurface(Rect(0f, 100f, 80f, 800f), RainSurfaceKind.Sidebar, vertical = true)
        val hit = rainSurfaceHit(RainParticle(0.1f, 0f, 0f, 0f), atY(100f) + 0.01f,
            viewport, sidebar, 0f, margin, cycle, 12f)!!
        assertEquals(Offset(40f, 100f), hit.position)
        assertFalse(hit.vertical)
    }

    @Test fun topBarRunoffStartsAtItsBottomEdge() {
        val bar = RainHitSurface(Rect(0f, 0f, 400f, 90f), RainSurfaceKind.TopBar)
        val hit = rainSurfaceHit(RainParticle(0.5f, 0f, 0f, 0f), atY(90f) + 0.01f,
            viewport, bar, 0f, margin, cycle, 12f)!!
        assertEquals(90f, hit.position.y, 0.001f)
    }

    @Test fun offscreenCornersAndInvalidGeometryCannotCreatePhantomImpacts() {
        val particle = RainParticle(0.05f, 0f, 0f, 0f)
        val card = RainHitSurface(Rect(15f, 200f, 200f, 400f), RainSurfaceKind.Card)
        assertNull(rainSurfaceHit(particle, atY(200f), viewport, card, 0f, margin, cycle, 12f))
        listOf(Rect(0f, -20f, 400f, 10f), Rect(0f, 900f, 400f, 1000f), Rect(0f, 0f, 0f, 40f),
            Rect(Float.NaN, 0f, 100f, 40f)).forEach { bounds ->
            assertNull(rainSurfaceHit(particle, 0.4f, viewport, card.copy(bounds = bounds), 0f, margin, cycle, 12f))
        }
        assertNull(rainSurfaceHit(particle, Float.NaN, viewport, card, 0f, margin, cycle, 12f))
        assertNull(rainSurfaceHit(particle, 0.2f, Size.Zero, card, 0f, margin, cycle, 12f))
    }

    @Test fun clippedCardsAndSidebarEdgesCannotBecomeFakeRainSurfaces() {
        val card = Rect(20f, 100f, 380f, 300f)
        assertNotNull(visibleRainSurface(card, card, viewport, RainSurfaceKind.Card, false, true))
        assertNull(visibleRainSurface(card.copy(top = 150f), card, viewport, RainSurfaceKind.Card, false, true))
        val sidebar = Rect(0f, 0f, 80f, 800f)
        assertNull(visibleRainSurface(sidebar.copy(right = 40f), sidebar, viewport, RainSurfaceKind.Sidebar, true, true))
        assertNull(visibleRainSurface(sidebar.copy(left = 20f), sidebar, viewport, RainSurfaceKind.Sidebar, true, false))
        assertNotNull(visibleRainSurface(sidebar, sidebar, viewport, RainSurfaceKind.Sidebar, true, true))
        assertNull(visibleRainSurface(Rect(500f, 0f, 600f, 100f), Rect(500f, 0f, 600f, 100f),
            viewport, RainSurfaceKind.Card, false, true))
    }

    @Test fun foregroundRainHasExplicitControlsAndRespectsWallpaperOptIn() {
        val settings = RainSettings(active = true)
        assertTrue(settings.foregroundDrawingEnabled(false))
        assertFalse(settings.foregroundDrawingEnabled(true))
        assertTrue(settings.copy(wallpaperRain = true).foregroundDrawingEnabled(true))
        assertTrue(settings.copy(motion = false).foregroundDrawingEnabled(false)) // still artwork
        assertFalse(settings.copy(active = false).foregroundDrawingEnabled(false))
        assertFalse(settings.copy(rainThroughUi = false).foregroundDrawingEnabled(false))
        assertFalse(settings.copy(foregroundOpacity = 0f).foregroundDrawingEnabled(false))
        assertFalse(settings.copy(intensity = 0f).foregroundDrawingEnabled(false))
    }

    @Test fun exposedTopBarBottomIsTheInteractionEdge() {
        val bar = Rect(0f, 0f, 400f, 90f)
        assertNull(visibleRainSurface(bar.copy(bottom = 60f), bar, viewport, RainSurfaceKind.TopBar, false, true))
        assertNotNull(visibleRainSurface(bar.copy(top = 20f), bar, viewport, RainSurfaceKind.TopBar, false, true))
    }

    @Test fun splashPersistsLongEnoughToReadThenExpiresAtTheSameCrossing() {
        val target = RainHitSurface(Rect(20f, 200f, 380f, 400f), RainSurfaceKind.Card)
        val particle = RainParticle(0.5f, 0f, 0.5f, 0.5f)
        val crossing = atY(200f)
        assertNotNull(rainSurfaceHit(particle, crossing + 800f / cycle, viewport, target, 0f, margin, cycle, 12f))
        assertNull(rainSurfaceHit(particle, crossing + 1000f / cycle, viewport, target, 0f, margin, cycle, 12f))
    }

    @Test fun interactionVisibilityRespectsControlsWithoutDoubleDimmingDefaultSplashes() {
        val settings = RainSettings(active = true)
        assertEquals(0.65f, rainInteractionStrength(settings), 0.001f)
        assertEquals(0.325f, rainInteractionStrength(settings.copy(dropOpacity = 0.5f)), 0.001f)
        for (decoration in listOf(0.01f, 0.1f, 0.65f, 1f)) for (foreground in listOf(0.01f, 0.1f, 0.65f, 1f)) {
            val strength = rainInteractionStrength(settings.copy(decorationOpacity = decoration, foregroundOpacity = foreground))
            assertTrue("Impacts must not exceed either opacity limit", strength <= minOf(decoration, foreground))
        }
        listOf(settings.copy(active = false), settings.copy(rainThroughUi = false),
            settings.copy(surfaceInteraction = false), settings.copy(intensity = 0f),
            settings.copy(decorationOpacity = 0f), settings.copy(foregroundOpacity = 0f)).forEach {
            assertEquals(0f, rainInteractionStrength(it), 0f)
        }
    }

    @Test fun thunderIsOneSmoothBoundedLocalPulseAndHonorsAllMotionControls() {
        val settings = RainSettings(active = true, scene = RainStyle.Thunderstorm)
        var previous = 0f
        var lit = 0
        repeat(1001) { index ->
            val value = rainThunderPulse(settings, index / 1000f * settings.thunderInterval, true)
            assertTrue(value in 0f..1f)
            assertTrue(abs(value - previous) < 0.04f)
            if (value > 0f) lit++
            previous = value
        }
        assertTrue(lit in 90..110)
        val peak = settings.thunderInterval * 0.7f + 0.9f
        assertEquals(1f, rainThunderPulse(settings, peak, true), 0.001f)
        assertEquals(0f, rainThunderPulse(settings, peak, false), 0f)
        listOf(settings.copy(active = false), settings.copy(motion = false), settings.copy(thunder = false),
            settings.copy(scene = RainStyle.LightRain), settings.copy(intensity = 0f)).forEach {
            assertEquals(0f, rainThunderPulse(it, peak, true), 0f)
        }
        assertEquals(0f, rainThunderPulse(settings, Float.NaN, true), 0f)
    }

    @Test fun rootWeatherUsesOneClockWithoutTouchOrAlphaInterception() {
        val root = File("src/main/java/me/weishu/kernelsu/ui")
        val activity = File(root, "MainActivity.kt").readText()
        assertFalse(activity.contains("rememberRainCardMotionProgress"))
        val wallpaper = File(root, "component/CustomWallpaperBackground.kt").readText()
        assertTrue(wallpaper.indexOf("LocalRainSceneProgress provides rainProgress") < wallpaper.indexOf("rain.RainUiLayer("))
        val layer = File(root, "component/rain/RainInteractions.kt").readText()
        assertTrue(layer.indexOf("drawContent()") < layer.indexOf("particles.forEachIndexed"))
        assertFalse(layer.contains("pointerInput"))
        assertFalse(layer.contains("clickable("))
        assertFalse(layer.contains("graphicsLayer"))
        assertTrue(layer.contains("findRootCoordinates() !== root.findRootCoordinates()"))
        assertTrue(layer.contains("onDispose { registry.targets.remove(id) }"))
    }
}
