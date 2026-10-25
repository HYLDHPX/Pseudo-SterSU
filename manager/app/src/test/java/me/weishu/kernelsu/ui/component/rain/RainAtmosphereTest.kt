package me.weishu.kernelsu.ui.component.rain

import me.weishu.kernelsu.ui.util.SidebarWeatherCondition
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RainAtmosphereTest {
    @Test fun hoursMapToFourBandsWithStableEdges() {
        assertTrue((5..8).all { rainTimeOfDay(it) == RainTimeOfDay.Dawn })
        assertTrue((9..16).all { rainTimeOfDay(it) == RainTimeOfDay.Day })
        assertTrue((17..19).all { rainTimeOfDay(it) == RainTimeOfDay.Dusk })
        assertTrue((listOf(20, 21, 22, 23, 0, 1, 2, 3, 4)).all { rainTimeOfDay(it) == RainTimeOfDay.Night })
        // Out-of-range hours fold back into the same day instead of throwing.
        assertEquals(rainTimeOfDay(23), rainTimeOfDay(-1))
        assertEquals(rainTimeOfDay(2), rainTimeOfDay(26))
    }

    @Test fun everyBandSelectsADistinctScene() {
        val scenes = RainTimeOfDay.entries.map(::atmosphereScene)
        assertEquals(4, scenes.toSet().size)
    }

    @Test fun weatherOnlyOverridesConditionsThatChangeTheRain() {
        assertEquals(RainStyle.Thunderstorm, atmosphereSceneForWeather(SidebarWeatherCondition.Storm))
        assertEquals(RainStyle.MediumRain, atmosphereSceneForWeather(SidebarWeatherCondition.Rain))
        assertEquals(RainStyle.AfterRain, atmosphereSceneForWeather(SidebarWeatherCondition.Clear))
        assertNull(atmosphereSceneForWeather(SidebarWeatherCondition.Cloud))
        assertNull(atmosphereSceneForWeather(SidebarWeatherCondition.Snow))
        assertNull(atmosphereSceneForWeather(SidebarWeatherCondition.Unknown))
    }

    @Test fun disabledAtmosphereKeepsTheChosenScene() {
        val settings = RainSettings(scene = RainStyle.Thunderstorm, atmosphere = false)
        RainTimeOfDay.entries.forEach {
            assertEquals(RainStyle.Thunderstorm, resolveRainScene(settings, it, SidebarWeatherCondition.Clear))
        }
    }

    @Test fun enabledAtmosphereFollowsTheClockThenTheWeather() {
        val settings = RainSettings(scene = RainStyle.LightRain, atmosphere = true, atmosphereFollowsWeather = true)
        assertEquals(RainStyle.HeavyRain, resolveRainScene(settings, RainTimeOfDay.Night, null))
        assertEquals(RainStyle.Thunderstorm, resolveRainScene(settings, RainTimeOfDay.Night, SidebarWeatherCondition.Storm))
        // A condition that does not change the rain falls through to the clock.
        assertEquals(RainStyle.HeavyRain, resolveRainScene(settings, RainTimeOfDay.Night, SidebarWeatherCondition.Cloud))
    }

    @Test fun weatherIsIgnoredUntilTheUserOptsIn() {
        val clockOnly = RainSettings(scene = RainStyle.LightRain, atmosphere = true, atmosphereFollowsWeather = false)
        // Dawn maps to light rain, so a storm must not override it while weather following is off.
        assertEquals(RainStyle.LightRain, resolveRainScene(clockOnly, RainTimeOfDay.Dawn, SidebarWeatherCondition.Storm))
        assertEquals(RainStyle.HeavyRain, resolveRainScene(clockOnly, RainTimeOfDay.Night, SidebarWeatherCondition.Storm))
    }

    @Test fun soundGainIsOrderedBySceneAndBounded() {
        val gains = RainStyle.entries.map(::rainSoundGain)
        assertTrue(gains.all { it in 0.1f..2f })
        assertTrue(rainSoundGain(RainStyle.HeavyRain) > rainSoundGain(RainStyle.LightRain))
        assertEquals(0f, rainSoundVolume(RainSound.Off, 1f, RainStyle.HeavyRain), 0f)
        assertEquals(1f, rainSoundVolume(RainSound.Downpour, 1f, RainStyle.HeavyRain), 0f)
        assertEquals(0f, rainSoundVolume(RainSound.Shower, 0f, RainStyle.HeavyRain), 0f)
        // Invalid volume never leaks NaN into the mixer.
        assertEquals(0.45f * rainSoundGain(RainStyle.MediumRain), rainSoundVolume(RainSound.Shower, Float.NaN, RainStyle.MediumRain), 0.001f)
    }

    @Test fun dropFormChangesTheStreakGeometry() {
        val base = RainSettings(scene = RainStyle.MediumRain, quality = RainQuality.High, intensity = 1f)
        val line = RainSceneSpec.fromSettings(base.copy(dropForm = RainDropForm.Line))
        val needle = RainSceneSpec.fromSettings(base.copy(dropForm = RainDropForm.Needle))
        val bead = RainSceneSpec.fromSettings(base.copy(dropForm = RainDropForm.Bead))
        assertTrue("needle is longer", needle.maxLengthDp > line.maxLengthDp)
        assertTrue("needle is thinner", needle.maxStrokeDp < line.maxStrokeDp)
        assertTrue("bead is shorter", bead.maxLengthDp < line.maxLengthDp)
        assertTrue("bead is wider", bead.maxStrokeDp > line.maxStrokeDp)
        // Density is independent of the silhouette.
        assertEquals(line.dropCount, bead.dropCount)
    }

    @Test fun lengthAndOpacityOverridesStayInRenderableBounds() {
        val base = RainSettings(scene = RainStyle.HeavyRain, quality = RainQuality.High, intensity = 1f)
        val tiny = RainSceneSpec.fromSettings(base.copy(dropLength = 0.4f, dropOpacity = 0.2f))
        val huge = RainSceneSpec.fromSettings(base.copy(dropLength = 2f, dropOpacity = 1f))
        assertTrue(tiny.minLengthDp <= tiny.maxLengthDp)
        assertTrue(huge.minLengthDp <= huge.maxLengthDp)
        assertTrue(tiny.maxLengthDp < huge.maxLengthDp)
        assertTrue(tiny.maxAlpha < huge.maxAlpha)
        assertTrue(RainSceneSpec.fromSettings(base).dropCount in 0..160)
    }

    @Test fun newOptionsRoundTripThroughSharing() {
        val settings = RainSettings(
            layout = RainLayout.Miuix, dropForm = RainDropForm.Bead, dropLength = 1.4f, dropOpacity = 0.7f,
            atmosphere = true, atmosphereFollowsWeather = true, cardSurface = RainDecoration.Dripping,
            sound = RainSound.Downpour, soundVolume = 0.8f,
        )
        val parsed = parseRainSettings(settings.toRainJson(), RainLayout.Miuix)
        assertEquals(settings.copy(active = true), parsed)
    }

    @Test fun olderSharedProfilesStillImport() {
        // A profile written before the new knobs existed must keep its values and defaults.
        val legacy = JSONObject(
            RainSettings(layout = RainLayout.Sidebar, scene = RainStyle.HeavyRain).toRainJson().toString(),
        ).apply {
            listOf(
                "drop_form", "drop_length", "drop_opacity", "atmosphere",
                "atmosphere_weather", "card_surface", "sound", "sound_volume",
            ).forEach(::remove)
        }
        val parsed = parseRainSettings(legacy, RainLayout.Sidebar)
        assertEquals(RainStyle.HeavyRain, parsed.scene)
        assertEquals(RainDropForm.Line, parsed.dropForm)
        assertEquals(RainDecoration.Wet, parsed.cardSurface)
        assertEquals(RainSound.Off, parsed.sound)
        assertNull(parsed.dropLength)
        assertFalse(parsed.atmosphere)
    }

    @Test fun invalidOptionsAreRejected() {
        val json = RainSettings().toRainJson()
        listOf(
            "drop_form" to "spiral", "card_surface" to "soaked", "sound" to "thunder",
            "drop_length" to 9, "drop_opacity" to 0.01, "sound_volume" to 2, "atmosphere" to "yes",
        ).forEach { (key, value) ->
            var failed = false
            try {
                parseRainSettings(JSONObject(json.toString()).put(key, value), RainLayout.Sidebar)
            } catch (_: Exception) {
                failed = true
            }
            assertTrue("$key should be rejected", failed)
        }
    }

    @Test fun customAccentTintsTheRainNotJustTheChrome() {
        val plain = rainPalette(RainSettings(scene = RainStyle.LightRain), dark = false)
        val tinted = rainPalette(
            RainSettings(scene = RainStyle.LightRain, lightAccent = 0xFFB02020.toInt()), dark = false,
        )
        assertNotEquals(plain.rain, tinted.rain)
        // The backdrop also shifts, so streaks are still read against a matching sky.
        assertNotEquals(plain.backgroundTop, tinted.backgroundTop)
        assertNotEquals(plain.surfaceSheen, tinted.surfaceSheen)
        // Dark mode stays legible against the backdrop it is drawn over.
        val darkTinted = rainPalette(
            RainSettings(scene = RainStyle.LightRain, darkAccent = 0xFF102A40.toInt()), dark = true,
        )
        assertTrue(darkTinted.rain.luminanceApprox() > darkTinted.backgroundTop.luminanceApprox())
    }

    @Test fun wallpaperDisablesRainPainting() {
        val active = RainSettings()
        assertTrue(active.copy(active = true).drawingEnabled(hasCustomBackground = false))
        assertFalse(active.copy(active = true).drawingEnabled(hasCustomBackground = true))
        assertFalse(active.copy(active = false).drawingEnabled(hasCustomBackground = false))
    }
}

private fun androidx.compose.ui.graphics.Color.luminanceApprox(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
