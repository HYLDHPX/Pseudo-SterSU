package me.weishu.kernelsu.ui.component.rain

import android.content.SharedPreferences
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import java.io.File
import java.lang.reflect.Proxy
import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.util.importThemeStoreInterfaceSelection
import me.weishu.kernelsu.ui.util.putThemeStoreInterfaceSelection
import me.weishu.kernelsu.ui.util.validateThemeStoreInterfaceSelection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RainSettingsTest {
    @Test fun layoutsResolveToThreeRealPageFamiliesWithoutChangingOtherStyles() {
        assertEquals(listOf(UiMode.Miuix, UiMode.Material, UiMode.Material), RainLayout.entries.map {
            UiMode.fromValue(resolveRainLayoutStyle("rain", RainSettings(layout = it)))
        })
        RainLayout.entries.forEach { layout ->
            assertEquals(layout.interfaceStyle, resolveRainLayoutStyle("rain", RainSettings(layout = layout)))
            InterfaceStyle.entries.filter { it != InterfaceStyle.Rain }.forEach { style ->
                assertEquals(style.value, resolveRainLayoutStyle(style.value, RainSettings(layout = layout)))
            }
        }
    }

    @Test fun invalidValuesAreBoundedAndUnknownEnumsUseSafeDefaults() {
        val safe = RainSettings(intensity = Float.NaN, speed = Float.POSITIVE_INFINITY, wind = -40f,
            surfaceOpacity = -4f, topBarOpacity = 10f, navigationOpacity = Float.NaN, sidebarOpacity = Float.NEGATIVE_INFINITY).normalized()
        assertEquals(0.45f, safe.intensity, 0f)
        assertEquals(1f, safe.speed, 0f)
        assertEquals(-0.5f, safe.wind, 0f)
        assertEquals(0.35f, safe.surfaceOpacity, 0f)
        assertEquals(1f, safe.topBarOpacity, 0f)
        assertEquals(0.94f, safe.navigationOpacity, 0f)
        assertEquals(0.90f, safe.sidebarOpacity, 0f)
        assertEquals(RainLayout.Sidebar, RainLayout.fromValue("missing"))
        assertEquals(RainQuality.Standard, RainQuality.fromValue(null))
    }

    @Test fun threeProfilesRemainIsolatedAcrossSaveAndReload() {
        val memory = MemoryPreferences()
        val profiles = RainLayout.entries.associateWith { layout ->
            RainSettings(layout = layout, scene = RainStyle.entries[layout.ordinal],
                surfaceOpacity = 0.4f + layout.ordinal * 0.2f,
                lightAccent = 0xFF123456.toInt() + layout.ordinal, motion = layout != RainLayout.Material)
        }
        profiles.values.forEach { memory.prefs.edit().putRainSettings(it).apply() }
        profiles.forEach { (layout, expected) ->
            memory.prefs.edit().putString(RAIN_LAYOUT_KEY, layout.value).apply()
            assertEquals(expected.copy(active = true), readRainSettings(memory.prefs))
        }
        memory.prefs.edit().putRainSettings(RainSettings(layout = RainLayout.Miuix)).apply()
        assertEquals(profiles.getValue(RainLayout.Material).copy(active = true), readRainSettings(memory.prefs, requestedLayout = RainLayout.Material))
        assertEquals(profiles.getValue(RainLayout.Sidebar).copy(active = true), readRainSettings(memory.prefs, requestedLayout = RainLayout.Sidebar))
    }

    @Test fun legacyAndDamagedPreferencesDoNotCrashOrLoseTheSelectedScene() {
        val memory = MemoryPreferences(mutableMapOf(RAIN_STYLE_KEY to "heavy_rain"))
        assertEquals(RainStyle.HeavyRain, readRainSettings(memory.prefs).scene)
        memory.values[rainSettingKey(RainLayout.Sidebar, "intensity")] = "broken"
        memory.values[rainSettingKey(RainLayout.Sidebar, "motion")] = 1
        assertEquals(0.45f, readRainSettings(memory.prefs).intensity, 0f)
        assertTrue(readRainSettings(memory.prefs).motion)
        assertFalse(readRainSettings(memory.prefs, active = false).active)
    }

    @Test fun sceneDensityAndQualityAreBoundedAndSpeedAndWindAreConnected() {
        RainStyle.entries.forEach { scene -> RainQuality.entries.forEach { quality ->
            val spec = RainSceneSpec.fromSettings(RainSettings(scene = scene, quality = quality, intensity = 1f))
            assertTrue(spec.dropCount in 0..80)
            assertTrue(spec.cycleMillis >= 7000)
        } }
        assertEquals(0, RainSceneSpec.fromSettings(RainSettings(intensity = 0f)).dropCount)
        assertEquals(7000, RainSceneSpec.fromSettings(RainSettings(speed = 2f)).cycleMillis)
        assertEquals(-0.4f, RainSceneSpec.fromSettings(RainSettings(wind = -0.4f)).windRatio, 0f)
        assertTrue(RainSceneSpec.fromSettings(RainSettings(scene = RainStyle.HeavyRain)).dropCount >
            RainSceneSpec.fromSettings(RainSettings(scene = RainStyle.LightRain)).dropCount)
    }

    @Test fun themeSharingCarriesAllThreeProfilesWithoutExternalAssets() {
        val source = MemoryPreferences()
        RainLayout.entries.forEach { layout -> source.prefs.edit().putRainSettings(
            RainSettings(layout = layout, scene = RainStyle.HeavyRain, darkAccent = 0xFF112233.toInt(), sidebarDivider = false),
        ).apply() }
        source.prefs.edit().putString(RAIN_LAYOUT_KEY, "material").apply()
        val json = exportRainConfiguration(source.prefs)
        val shared = importRainConfiguration(JSONObject(json.toString()))
        assertEquals(RainLayout.Material, shared.layout)
        assertEquals(3, shared.profiles.size)
        val selection = importThemeStoreInterfaceSelection(JSONObject().put("mode", "rain").put("variant", "heavy_rain").put("rain", json)) { _, _, _ ->
            error("Built-in rain must not access downloads")
        }
        val target = MemoryPreferences()
        val editor = target.prefs.edit()
        editor.putThemeStoreInterfaceSelection(selection)
        editor.apply()
        RainLayout.entries.forEach { layout ->
            assertEquals(readRainSettings(source.prefs, requestedLayout = layout), readRainSettings(target.prefs, requestedLayout = layout))
        }
        assertEquals(RainLayout.Material, readRainSettings(target.prefs).layout)
    }

    @Test fun malformedSharedValuesAndUnexpectedKeysAreRejected() {
        val json = RainSettings().toRainJson()
        listOf("scene" to "bad", "quality" to "bad", "motion" to "true", "surface" to 0.01,
            "intensity" to 9, "wind" to 1, "light_accent" to "#123456", "unknown" to 1).forEach { (key, value) ->
            assertThrows(Exception::class.java) { parseRainSettings(JSONObject(json.toString()).put(key, value), RainLayout.Sidebar) }
        }
        assertThrows(Exception::class.java) { importRainConfiguration(JSONObject().put("layout", "evil").put("profiles", JSONObject())) }
        val rain = exportRainConfiguration(MemoryPreferences().prefs)
        assertThrows(Exception::class.java) { validateThemeStoreInterfaceSelection(JSONObject().put("mode", "material").put("rain", rain)) }
        assertThrows(Exception::class.java) { validateThemeStoreInterfaceSelection(JSONObject().put("mode", "rain").put("variant", "heavy_rain").put("rain", rain)) }
    }

    @Test fun importingLegacyRainThemeOverridesExistingScopedScene() {
        val memory = MemoryPreferences()
        memory.prefs.edit().putRainSettings(RainSettings(scene = RainStyle.Thunderstorm)).apply()
        val legacy = importThemeStoreInterfaceSelection(JSONObject().put("mode", "rain").put("variant", "after_rain")) { _, _, _ -> error("No download") }
        val editor = memory.prefs.edit()
        editor.putThemeStoreInterfaceSelection(legacy)
        editor.apply()
        assertEquals(RainStyle.AfterRain, readRainSettings(memory.prefs).scene)
    }

    @Test fun dayAndNightPalettesKeepReadableForegroundWithCustomColors() {
        for (dark in listOf(false, true)) for (scene in RainStyle.entries) for (accent in listOf(null, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt())) {
            val palette = rainPalette(RainSettings(scene = scene, lightAccent = accent, darkAccent = accent), dark)
            for (surface in listOf(palette.backgroundTop, palette.backgroundBottom, palette.surfaceTop, palette.surfaceBottom)) {
                val contrast = (maxOf(palette.content.luminance(), surface.luminance()) + 0.05f) /
                    (minOf(palette.content.luminance(), surface.luminance()) + 0.05f)
                assertTrue("$dark $scene $accent: $contrast", contrast >= 4.5f)
            }
        }
    }

    @Test fun opacityNeverChangesForegroundOrSemanticColors() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            val next = base.withRainAppearance(RainSettings(active = true, surfaceOpacity = 0.4f), base.background.luminance() < 0.5f)
            assertEquals(0.4f, next.surface.alpha, 0.003f)
            assertEquals(1f, next.onSurface.alpha, 0f)
            assertEquals(base.error, next.error)
            assertEquals(base.errorContainer, next.errorContainer)
            assertEquals(Color.Transparent, next.background)
            assertEquals(base, base.withRainAppearance(RainSettings(active = false), false))
        }
    }

    @Test fun rainBackgroundAndImmersiveLayerShareTheDownloadedRenderer() {
        val activity = File("src/main/java/me/weishu/kernelsu/ui/MainActivity.kt").readText()
        assertTrue(activity.contains("LocalInterfaceStyle provides layoutInterfaceStyle"))
        assertTrue(activity.contains("entry<Route.RainDesign>"))
        assertFalse(activity.contains("RainForegroundOverlay"))
        assertFalse(activity.contains("RainChromeOverlay"))
        assertFalse(InterfaceStyle.Rain in InterfaceStyle.builtInEntries)
        val decor = File("src/main/java/me/weishu/kernelsu/ui/component/rain/RainDecor.kt").readText()
        assertTrue(decor.contains("LifecycleResumeEffect"))
        assertTrue(decor.contains("rememberSystemAnimationsEnabled"))
        assertFalse(decor.contains("drawLightning"))
        val wallpaper = File("src/main/java/me/weishu/kernelsu/ui/component/CustomWallpaperBackground.kt").readText()
        assertTrue(wallpaper.indexOf("rain.RainBackdrop()") < wallpaper.indexOf("if (usePagerBackgrounds)"))
        assertFalse(activity.contains("RainBackdrop("))
        assertTrue(wallpaper.contains("rain.RainUiLayer("))
    }

    @Test fun runtimeOptionsPersistAndShareWithoutChangingOtherLayouts() {
        val memory = MemoryPreferences()
        val settings = RainSettings(active = true, layout = RainLayout.Material, wallpaperRain = true,
            soundWithWallpaper = true)
        memory.prefs.edit().putRainSettings(settings).apply()
        assertEquals(settings, readRainSettings(memory.prefs, requestedLayout = RainLayout.Material))
        assertFalse(readRainSettings(memory.prefs, requestedLayout = RainLayout.Miuix).wallpaperRain)
        assertEquals(settings, parseRainSettings(settings.toRainJson(), RainLayout.Material))
        val legacy = settings.toRainJson().apply {
            listOf("wallpaper_rain", "wallpaper_sound", "auto_power_save").forEach(::remove)
        }
        val imported = parseRainSettings(legacy, RainLayout.Material)
        assertFalse(imported.wallpaperRain)
        assertFalse(imported.soundWithWallpaper)
        assertFalse(settings.toRainJson().has("auto_power_save"))
        assertEquals(imported, parseRainSettings(legacy.put("auto_power_save", true), RainLayout.Material))
        assertThrows(Exception::class.java) { parseRainSettings(settings.toRainJson().put("wallpaper_rain", "true"), RainLayout.Material) }
    }

    @Test fun wallpaperRainIsExplicitAndNeverEnablesInactiveStyles() {
        val settings = RainSettings(active = true)
        assertTrue(settings.drawingEnabled(false))
        assertFalse(settings.drawingEnabled(true))
        assertTrue(settings.copy(wallpaperRain = true).drawingEnabled(true))
        assertFalse(settings.copy(active = false, wallpaperRain = true).drawingEnabled(true))
    }

    @Test fun removedFeaturesHaveNoRuntimeOrDesignEntry() {
        val root = File("src/main/java/me/weishu/kernelsu/ui/component/rain")
        assertFalse(File(root, "RainPreview.kt").exists())
        assertFalse(File(root, "RainRuntime.kt").readText().contains("PowerManager"))
        assertFalse(File(root, "RainDesignScreen.kt").readText().contains("RainPreviewPanel"))
        val memory = MemoryPreferences()
        memory.prefs.edit().putBoolean(rainSettingKey(RainLayout.Sidebar, "auto_power_save"), true).apply()
        val before = readRainSettings(memory.prefs)
        memory.prefs.edit().putRainSettings(before).apply()
        assertFalse(memory.prefs.contains(rainSettingKey(RainLayout.Sidebar, "auto_power_save")))
        assertFalse(before.toRainJson().has("auto_power_save"))
    }

    @Test fun decorationProfilesPersistAndShareEveryStyleIndependently() {
        val memory = MemoryPreferences()
        val untouched = readRainSettings(memory.prefs, requestedLayout = RainLayout.Material)
        RainDecoration.entries.forEach { style ->
            val settings = RainSettings(active = true, cardSurface = style, topBarDecoration = RainDecoration.Streams,
                navigationDecoration = RainDecoration.Ripples, sidebarDecoration = RainDecoration.Dripping,
                decorationOpacity = 0.37f)
            memory.prefs.edit().putRainSettings(settings).apply()
            assertEquals(settings, readRainSettings(memory.prefs))
            assertEquals(settings, parseRainSettings(settings.toRainJson(), RainLayout.Sidebar))
            assertEquals(untouched, readRainSettings(memory.prefs, requestedLayout = RainLayout.Material))
        }
    }

    @Test fun cardFramesAndInteriorsPersistIndependentlyOfTheTopOrnament() {
        val memory = MemoryPreferences()
        RainLayout.entries.forEach { layout ->
            val otherLayouts = RainLayout.entries.filter { it != layout }.associateWith {
                readRainSettings(memory.prefs, requestedLayout = it)
            }
            RainCardBorder.entries.forEach { border -> RainCardInterior.entries.forEach { interior ->
                val settings = RainSettings(active = true, layout = layout, cardSurface = RainDecoration.Off,
                    cardBorder = border, cardInterior = interior)
                memory.prefs.edit().putRainSettings(settings).apply()
                assertEquals(settings, readRainSettings(memory.prefs, requestedLayout = layout))
                assertEquals(settings, parseRainSettings(settings.toRainJson(), layout))
                val shared = importRainConfiguration(exportRainConfiguration(memory.prefs))
                assertEquals(settings, shared.profiles.getValue(layout))
                otherLayouts.forEach { (other, expected) ->
                    assertEquals(expected, readRainSettings(memory.prefs, requestedLayout = other))
                }
            } }
        }
    }

    @Test fun immersiveRainControlsPersistAndShareInIndependentLayouts() {
        val memory = MemoryPreferences()
        RainLayout.entries.forEach { layout ->
            val settings = RainSettings(active = true, layout = layout, rainThroughUi = layout != RainLayout.Miuix,
                foregroundOpacity = 0.25f + layout.ordinal * 0.2f, surfaceInteraction = false, thunder = false)
            memory.prefs.edit().putRainSettings(settings).apply()
            assertEquals(settings, readRainSettings(memory.prefs, requestedLayout = layout))
            assertEquals(settings, parseRainSettings(settings.toRainJson(), layout))
            assertEquals(settings, importRainConfiguration(exportRainConfiguration(memory.prefs)).profiles.getValue(layout))
        }
        val legacy = RainSettings(active = true).toRainJson().apply {
            listOf("rain_through_ui", "foreground_opacity", "surface_interaction", "thunder").forEach(::remove)
        }
        assertEquals(RainSettings(active = true), parseRainSettings(legacy, RainLayout.Sidebar))
        listOf("rain_through_ui", "surface_interaction", "thunder").forEach { key ->
            assertThrows(Exception::class.java) { parseRainSettings(legacy.put(key, "true"), RainLayout.Sidebar) }
            legacy.remove(key)
        }
        assertThrows(Exception::class.java) { parseRainSettings(legacy.put("foreground_opacity", 1.2f), RainLayout.Sidebar) }
        assertEquals(0.65f, RainSettings(foregroundOpacity = Float.NaN).normalized().foregroundOpacity, 0f)
    }

    @Test fun legacyAndDamagedCardArtworkSettingsUseDefaultsWhileInvalidSharesAreRejected() {
        val settings = RainSettings(active = true)
        val legacy = settings.toRainJson().apply { remove("card_border"); remove("card_interior") }
        assertEquals(settings, parseRainSettings(legacy, RainLayout.Sidebar))
        val memory = MemoryPreferences(mutableMapOf(
            rainSettingKey(RainLayout.Sidebar, "card_border") to "unknown",
            rainSettingKey(RainLayout.Sidebar, "card_interior") to 3,
        ))
        assertEquals(RainCardBorder.Double, readRainSettings(memory.prefs).cardBorder)
        assertEquals(RainCardInterior.Mist, readRainSettings(memory.prefs).cardInterior)
        listOf("card_border", "card_interior").forEach { key ->
            listOf("unknown", true, 3).forEach { value ->
                assertThrows(Exception::class.java) { parseRainSettings(settings.toRainJson().put(key, value), RainLayout.Sidebar) }
            }
        }
    }

    @Test fun defaultFrameInkRemainsVisibleInDayAndNightPalettes() {
        RainStyle.entries.forEach { scene -> listOf(false, true).forEach { dark ->
            val settings = RainSettings(scene = scene)
            val palette = rainPalette(settings, dark)
            val painted = rainOrnamentInk(palette).copy(alpha = 0.58f * settings.decorationOpacity)
                .compositeOver(palette.surfaceTop)
            val luminances = listOf(painted.luminance(), palette.surfaceTop.luminance()).sorted()
            val contrast = (luminances[1] + 0.05f) / (luminances[0] + 0.05f)
            assertTrue("Decorative frame is too faint for $scene / dark=$dark: $contrast", contrast >= 1.5f)
            assertEquals(1f, palette.content.alpha, 0f)
        } }
    }

    @Test fun cardArtworkIsInsideMaterialContainersAndMiuixHomeHasChrome() {
        val root = File("src/main/java/me/weishu/kernelsu/ui")
        val material = File(root, "component/material/TonalCard.kt").readText()
        assertTrue(material.contains("drawInterior = false"))
        assertTrue(material.contains("Column(Modifier.fillMaxWidth().rainCardInterior(shape))"))
        assertTrue(material.contains("Box(modifier = Modifier.fillMaxWidth().rainCardInterior(shape))"))
        val home = File(root, "screen/home/HomeMiuix.kt").readText()
        assertTrue(home.contains(".rainChromeDecoration(RainChromeArea.TopBar)"))
        val snow = File(root, "component/snow/SnowDecor.kt").readText()
        assertTrue(snow.substringAfter("return rainMiuixCardSurface(").substringBefore(")").contains("shape = shape"))
    }

    @Test fun decorationMotionRespectsAllFourAreasAndExplicitControls() {
        val still = RainSettings(active = true, cardSurface = RainDecoration.Off, topBarDecoration = RainDecoration.Off,
            navigationDecoration = RainDecoration.Off, sidebarDecoration = RainDecoration.Off,
            cardBorder = RainCardBorder.Off, cardInterior = RainCardInterior.Off)
        assertFalse(still.decorationMotionEnabled())
        RainDecoration.entries.forEach { style ->
            val variants = listOf(still.copy(cardSurface = style), still.copy(topBarDecoration = style),
                still.copy(navigationDecoration = style), still.copy(sidebarDecoration = style))
            variants.forEach { settings ->
                assertEquals(style != RainDecoration.Off, settings.decorationMotionEnabled())
                assertFalse(settings.copy(motion = false).decorationMotionEnabled())
                assertFalse(settings.copy(active = false).decorationMotionEnabled())
                assertFalse(settings.copy(decorationOpacity = 0f).decorationMotionEnabled())
            }
        }
    }

    @Test fun decorationImportsValidateValuesAndKeepLegacyThemeDefaults() {
        val settings = RainSettings(active = true)
        val legacy = settings.toRainJson().apply {
            listOf("top_bar_decoration", "navigation_decoration", "sidebar_decoration", "decoration_opacity").forEach(::remove)
        }
        assertEquals(settings, parseRainSettings(legacy, RainLayout.Sidebar))
        listOf("card_surface", "top_bar_decoration", "navigation_decoration", "sidebar_decoration").forEach { key ->
            assertThrows(Exception::class.java) { parseRainSettings(settings.toRainJson().put(key, "unknown"), RainLayout.Sidebar) }
        }
        assertThrows(Exception::class.java) { parseRainSettings(settings.toRainJson().put("decoration_opacity", 1.1), RainLayout.Sidebar) }
        assertThrows(Exception::class.java) { parseRainSettings(legacy.put("auto_power_save", "true"), RainLayout.Sidebar) }
        assertEquals(0.65f, settings.copy(decorationOpacity = Float.NaN).normalized().decorationOpacity, 0f)
        assertEquals(0f, settings.copy(decorationOpacity = -1f).normalized().decorationOpacity, 0f)
        assertEquals(1f, settings.copy(decorationOpacity = 2f).normalized().decorationOpacity, 0f)
        assertEquals(0f, rainDecorationBand(Float.NaN, 100f), 0f)
        assertEquals(0f, rainDecorationBand(7f, 0f), 0f)
        assertEquals(7f, rainDecorationBand(7f, 100f), 0f)
        assertEquals(1.9f, rainDecorationBand(7f, 10f), 0.0001f)
    }

    @Test fun presetsSaveReplaceApplyDeleteAndKeepTheSelectedLayout() {
        val memory = MemoryPreferences()
        memory.prefs.edit().putRainSettings(RainSettings(layout = RainLayout.Sidebar, scene = RainStyle.Thunderstorm,
            wallpaperRain = true, darkAccent = 0xFF552277.toInt())).apply()
        assertTrue(saveRainPreset(memory.prefs, " Evening rain "))
        val original = readRainPresets(memory.prefs).single()
        assertEquals("Evening rain", original.name)
        memory.prefs.edit().putRainSettings(original.settings.copy(scene = RainStyle.AfterRain)).apply()
        assertTrue(saveRainPreset(memory.prefs, "Evening rain"))
        val updated = readRainPresets(memory.prefs).single()
        assertEquals(original.id, updated.id)
        assertEquals(RainStyle.AfterRain, updated.settings.scene)
        memory.prefs.edit().putString(RAIN_LAYOUT_KEY, RainLayout.Material.value).apply()
        applyRainPreset(memory.prefs, updated)
        val current = readRainSettings(memory.prefs)
        assertEquals(RainLayout.Material, current.layout)
        assertEquals(RainStyle.AfterRain, current.scene)
        assertTrue(current.wallpaperRain)
        assertEquals(RainStyle.AfterRain, readRainSettings(memory.prefs, requestedLayout = RainLayout.Sidebar).scene)
        deleteRainPreset(memory.prefs, updated.id)
        assertTrue(readRainPresets(memory.prefs).isEmpty())
    }

    @Test fun presetLimitsAndMalformedStorageAreHandled() {
        val memory = MemoryPreferences()
        assertFalse(saveRainPreset(memory.prefs, " \n "))
        repeat(MAX_RAIN_PRESETS) { assertTrue(saveRainPreset(memory.prefs, "Preset $it")) }
        assertFalse(saveRainPreset(memory.prefs, "Overflow"))
        assertTrue(saveRainPreset(memory.prefs, "Preset 0"))
        assertEquals(MAX_RAIN_PRESETS, readRainPresets(memory.prefs).size)
        memory.prefs.edit().putString(RAIN_PRESETS_KEY, "not json").apply()
        assertTrue(readRainPresets(memory.prefs).isEmpty())
        assertTrue(saveRainPreset(memory.prefs, "Recovered"))
        assertEquals("Recovered", readRainPresets(memory.prefs).single().name)
    }

    @Test fun profileCopyOverwritesOnlyTheTargetAndNeverSwitchesLayout() {
        val memory = MemoryPreferences()
        val source = RainSettings(active = true, layout = RainLayout.Sidebar, scene = RainStyle.Thunderstorm,
            sound = RainSound.Whisper, wallpaperRain = true)
        memory.prefs.edit().putRainSettings(source).putString(RAIN_LAYOUT_KEY, RainLayout.Sidebar.value).apply()
        val untouched = readRainSettings(memory.prefs, requestedLayout = RainLayout.Miuix)
        copyRainProfile(memory.prefs, RainLayout.Sidebar, RainLayout.Material)
        assertEquals(source.copy(layout = RainLayout.Material), readRainSettings(memory.prefs, requestedLayout = RainLayout.Material))
        assertEquals(source, readRainSettings(memory.prefs))
        assertEquals(untouched, readRainSettings(memory.prefs, requestedLayout = RainLayout.Miuix))
    }

    @Test fun regionalStrengthsRoundTripThroughPreferencesAndThemeSharing() {
        val source = MemoryPreferences()
        val expected = RainLayout.entries.associateWith { layout ->
            RainSettings(active = true, layout = layout, cardEdgeStrength = 0.15f + layout.ordinal * 0.1f,
                cardInteriorStrength = 0.24f, topBarStrength = 0.35f, navigationStrength = 0.46f,
                sidebarStrength = 0.57f, thunderInterval = 12f + layout.ordinal * 4f)
        }
        expected.values.forEach { source.prefs.edit().putRainSettings(it).apply() }
        val shared = importRainConfiguration(JSONObject(exportRainConfiguration(source.prefs).toString()))
        val target = MemoryPreferences()
        target.prefs.edit().putRainConfiguration(shared).apply()
        for ((layout, settings) in expected) {
            assertEquals(settings, readRainSettings(source.prefs, requestedLayout = layout))
            assertEquals(settings, readRainSettings(target.prefs, requestedLayout = layout))
            assertEquals(settings, shared.profiles.getValue(layout))
        }
    }

    @Test fun legacyThemesAndDamagedRegionPreferencesHaveSafeDefaults() {
        val keys = listOf("card_edge_strength", "card_interior_strength", "top_bar_strength", "navigation_strength", "sidebar_strength", "thunder_interval")
        val defaults = RainSettings(active = true)
        val legacy = defaults.toRainJson().apply { keys.forEach(::remove) }
        assertEquals(defaults, parseRainSettings(legacy, RainLayout.Sidebar))
        val memory = MemoryPreferences()
        for (key in keys) memory.values[rainSettingKey(RainLayout.Sidebar, key)] = "corrupt"
        assertEquals(defaults, readRainSettings(memory.prefs))
        assertEquals(1f, defaults.copy(cardEdgeStrength = Float.NaN).normalized().cardEdgeStrength, 0f)
        assertEquals(0f, defaults.copy(cardInteriorStrength = -1f).normalized().cardInteriorStrength, 0f)
        assertEquals(1f, defaults.copy(topBarStrength = 2f).normalized().topBarStrength, 0f)
        assertEquals(18f, defaults.copy(thunderInterval = Float.POSITIVE_INFINITY).normalized().thunderInterval, 0f)
        assertEquals(8f, defaults.copy(thunderInterval = 0f).normalized().thunderInterval, 0f)
        assertEquals(60f, defaults.copy(thunderInterval = 100f).normalized().thunderInterval, 0f)
    }

    @Test fun regionImportsRejectWrongTypesOutOfRangeAndUnexpectedFields() {
        val defaults = RainSettings().toRainJson()
        for (key in listOf("card_edge_strength", "card_interior_strength", "top_bar_strength", "navigation_strength", "sidebar_strength")) {
            for (value in listOf<Any>(-0.01, 1.01, "0.5", true)) {
                assertThrows(Exception::class.java) { parseRainSettings(JSONObject(defaults.toString()).put(key, value), RainLayout.Sidebar) }
            }
        }
        for (value in listOf<Any>(7, 61, "18", true)) {
            assertThrows(Exception::class.java) { parseRainSettings(JSONObject(defaults.toString()).put("thunder_interval", value), RainLayout.Sidebar) }
        }
        assertThrows(Exception::class.java) { parseRainSettings(JSONObject(defaults.toString()).put("region_key", 1), RainLayout.Sidebar) }
    }

    @Test fun regionalStrengthsDoNotChangeTextIconsImagesOrSurfaceOpacity() {
        val settings = RainSettings(active = true, decorationOpacity = 0.8f,
            cardEdgeStrength = 0.5f, cardInteriorStrength = 0.25f, topBarStrength = 0f,
            navigationStrength = 0.75f, sidebarStrength = 1f)
        assertEquals(0.4f, settings.edgeStrength(RainSurfaceKind.Card), 0f)
        assertEquals(0.2f, settings.interiorStrength(), 0f)
        assertEquals(0f, settings.edgeStrength(RainSurfaceKind.TopBar), 0f)
        assertEquals(0.6f, settings.edgeStrength(RainSurfaceKind.Navigation), 0.001f)
        assertEquals(0.8f, settings.edgeStrength(RainSurfaceKind.Sidebar), 0f)
        assertEquals(RainSettings().surfaceOpacity, settings.surfaceOpacity, 0f)
        assertEquals(RainSettings().topBarOpacity, settings.topBarOpacity, 0f)
        assertEquals(RainSettings().navigationOpacity, settings.navigationOpacity, 0f)
        assertEquals(RainSettings().sidebarOpacity, settings.sidebarOpacity, 0f)
        assertEquals(0f, settings.copy(decorationOpacity = 0f).interiorStrength(), 0f)
    }

    @Test fun allDisabledRegionsStopOrnamentMotionIncludingInteriorAndRim() {
        val settings = RainSettings(active = true, cardEdgeStrength = 0f, cardInteriorStrength = 0f,
            topBarStrength = 0f, navigationStrength = 0f, sidebarStrength = 0f)
        assertFalse(settings.decorationMotionEnabled())
        assertTrue(settings.copy(cardInteriorStrength = 1f).decorationMotionEnabled())
        assertTrue(settings.copy(cardEdgeStrength = 1f, cardSurface = RainDecoration.Off).decorationMotionEnabled())
        assertFalse(settings.copy(cardEdgeStrength = 1f, cardSurface = RainDecoration.Off, cardBorder = RainCardBorder.Off).decorationMotionEnabled())
        assertFalse(settings.copy(cardInteriorStrength = 1f, motion = false).decorationMotionEnabled())
    }

    @Test fun newOptionsSurvivePresetCopyAndApplyingIntoAnotherLayout() {
        val memory = MemoryPreferences()
        val original = RainSettings(active = true, cardEdgeStrength = 0.2f, cardInteriorStrength = 0.3f,
            topBarStrength = 0.4f, navigationStrength = 0.5f, sidebarStrength = 0.6f, thunderInterval = 44f)
        memory.prefs.edit().putRainSettings(original).putString(RAIN_LAYOUT_KEY, RainLayout.Sidebar.value).apply()
        assertTrue(saveRainPreset(memory.prefs, "Wet glass"))
        val preset = readRainPresets(memory.prefs).single()
        assertEquals(original, preset.settings)
        copyRainProfile(memory.prefs, RainLayout.Sidebar, RainLayout.Miuix)
        assertEquals(original.copy(layout = RainLayout.Miuix), readRainSettings(memory.prefs, requestedLayout = RainLayout.Miuix))
        memory.prefs.edit().putString(RAIN_LAYOUT_KEY, RainLayout.Material.value).apply()
        applyRainPreset(memory.prefs, preset)
        assertEquals(original.copy(layout = RainLayout.Material), readRainSettings(memory.prefs))
    }

    private class MemoryPreferences(val values: MutableMap<String, Any> = mutableMapOf()) {
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString", "getFloat", "getInt", "getBoolean" -> values[args!![0]] ?: args[1]
                "contains" -> values.containsKey(args!![0])
                "edit" -> editor()
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when {
                method.name.startsWith("put") -> { values[args!![0] as String] = args[1]; proxy }
                method.name == "remove" -> { values.remove(args!![0] as String); proxy }
                method.name == "apply" -> null
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
    }
}
