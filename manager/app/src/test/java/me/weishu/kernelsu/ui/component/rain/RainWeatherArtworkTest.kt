package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class RainWeatherArtworkTest {
    @Test fun defaultRainContainsVisibleNearAndFarStreaks() {
        val spec = RainSceneSpec.fromSettings(RainSettings(active = true))
        // The former 11 drops left only four in front of otherwise opaque page cards.
        assertTrue(spec.dropCount in 30..36)
        assertTrue((0 until spec.dropCount).count(::rainFrontParticle) >= 10)
        assertTrue(spec.maxLengthDp >= 30f)
        val particle = RainParticle(0.5f, 0.5f, 0.5f, 0.5f)
        val near = rainStreakAppearance(particle, 0, spec, true, 0.65f)
        val far = rainStreakAppearance(particle, 1, spec, false)
        assertTrue(near.length > far.length)
        assertTrue(near.width > far.width)
        assertTrue(near.alpha >= 0.30f)
        assertEquals(0f, rainStreakAppearance(particle, 0, spec, true, 0f).alpha, 0f)
    }

    @Test fun intensityIsMonotonicAndZeroStillRemovesEveryDrop() {
        RainStyle.entries.forEach { scene -> RainQuality.entries.forEach { quality ->
            var previous = 0
            repeat(101) { index ->
                val spec = RainSceneSpec.fromSettings(RainSettings(scene = scene, quality = quality, intensity = index / 100f))
                assertTrue(spec.dropCount in previous..80)
                if (index == 0) assertEquals(0, spec.dropCount)
                previous = spec.dropCount
            }
        } }
    }

    @Test fun everyRainFormHonorsOpacityAndFitsItsLengthBudget() {
        RainStyle.entries.forEach { scene -> RainDropForm.entries.forEach { form ->
            val spec = RainSceneSpec.fromSettings(RainSettings(scene = scene, dropForm = form, dropLength = 2f))
            val dimSpec = RainSceneSpec.fromSettings(RainSettings(scene = scene, dropForm = form, dropLength = 2f, dropOpacity = 0.2f))
            rainParticles(spec.dropCount, scene).forEachIndexed { index, particle ->
                val near = rainFrontParticle(index)
                val streak = rainStreakAppearance(particle, index, spec, near)
                val dim = rainStreakAppearance(particle, index, dimSpec, near)
                assertTrue(streak.length > 0f && streak.length <= spec.maxLengthDp * 1.18f + 0.001f)
                assertTrue("Wrapped streak must leave the viewport completely", streak.length <= rainTravelMarginDp(spec))
                assertTrue(streak.alpha in 0f..0.66f)
                assertTrue(dim.alpha <= streak.alpha)
                assertEquals(streak.length, dim.length, 0f)
            }
        } }
    }

    @Test fun cloudsScaleForSmallWindowsAndTabletsWithoutNonFinitePaints() {
        val palette = rainPalette(RainStyle.LightRain, false)
        for (viewport in listOf(Size(160f, 240f), Size(400f, 800f), Size(1200f, 800f))) {
            val clouds = rainCloudPaints(viewport, palette, RainStyle.LightRain)
            assertEquals(9, clouds.size)
            assertTrue(clouds.all { it.radius.isFinite() && it.radius > 0f && it.flatten in 0.3f..0.5f })
            assertTrue(clouds.all { it.center.x.isFinite() && it.center.y.isFinite() })
        }
        for (viewport in listOf(Size.Zero, Size(-1f, 100f), Size(Float.NaN, 100f), Size(100f, Float.POSITIVE_INFINITY))) {
            assertTrue(rainCloudPaints(viewport, palette, RainStyle.LightRain).isEmpty())
        }
    }

    @Test fun layeredCloudsKeepUncoveredPageTextReadable() {
        val viewport = Size(400f, 800f)
        var minimum = Float.MAX_VALUE
        var worst = ""
        for (scene in RainStyle.entries) for (dark in listOf(false, true))
            for (accent in listOf(null, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt())) {
                val palette = rainPalette(RainSettings(scene = scene, lightAccent = accent, darkAccent = accent), dark)
                val clouds = rainCloudPaints(viewport, palette, scene)
                for (row in 0..20) for (column in 0..10) {
                    val x = column / 10f * viewport.width
                    val y = row / 20f * viewport.height
                    var color = lerp(palette.backgroundTop, palette.backgroundBottom, row / 20f)
                    for (cloud in clouds) {
                        val distance = hypot(x - cloud.center.x, (y - cloud.center.y) / cloud.flatten) / cloud.radius
                        val fade = if (distance < 0.5f) 1f - distance * 0.96f else (1f - distance) * 1.04f
                        color = cloud.tint.copy(alpha = cloud.tint.alpha * fade.coerceIn(0f, 1f)).compositeOver(color)
                    }
                    val contrast = (maxOf(color.luminance(), palette.content.luminance()) + 0.05f) /
                        (minOf(color.luminance(), palette.content.luminance()) + 0.05f)
                    if (contrast < minimum) {
                        minimum = contrast
                        worst = "$scene / $dark / $accent / $column,$row"
                    }
                }
            }
        assertTrue("Cloud text contrast: $worst / $minimum", minimum >= 4.5f)
    }

    @Test fun wetRimAndRainRemainDistinctFromGlassInEveryDayAndNightScene() {
        RainStyle.entries.forEach { scene -> listOf(false, true).forEach { dark ->
            val settings = RainSettings(scene = scene)
            val palette = rainPalette(settings, dark)
            val rim = rainOrnamentInk(palette).copy(alpha = 0.82f * settings.decorationOpacity)
                .compositeOver(palette.surfaceTop)
            val contrast = (maxOf(rim.luminance(), palette.surfaceTop.luminance()) + 0.05f) /
                (minOf(rim.luminance(), palette.surfaceTop.luminance()) + 0.05f)
            assertTrue("Rim too faint: $scene / $dark / $contrast", contrast >= 1.8f)
            assertEquals(1f, palette.content.alpha, 0f)
        } }
    }
}
