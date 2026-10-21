package me.weishu.kernelsu.ui.util

import android.content.SharedPreferences
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.theme.uiBackgroundWithOpacity
import me.weishu.kernelsu.ui.theme.withUiPassthrough
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import top.yukonga.miuix.kmp.theme.darkColorScheme as darkMiuixColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as lightMiuixColorScheme

class UiPassthroughTest {
    @Test
    fun defaultsKeepAllForegroundContentOpaque() {
        val value = UiPassthroughSettings().normalized()
        assertFalse(value.enabled)
        assertEquals(1f, value.textOpacity, 0f)
        assertEquals(1f, value.iconOpacity, 0f)
        assertEquals(1f, value.imageOpacity, 0f)
    }

    @Test
    fun invalidAndExtremeOpacitiesAreNormalizedWithoutHidingRecoveryControls() {
        val value = UiPassthroughSettings(surfaceOpacity = Float.NaN, topBarOpacity = Float.POSITIVE_INFINITY,
            bottomBarOpacity = -1f, textOpacity = 0f, iconOpacity = -1f, imageOpacity = 2f).normalized()
        assertEquals(0.65f, value.surfaceOpacity, 0f)
        assertEquals(0f, value.topBarOpacity, 0f)
        assertEquals(0f, value.bottomBarOpacity, 0f)
        assertEquals(0.2f, value.textOpacity, 0f)
        assertEquals(0.2f, value.iconOpacity, 0f)
        assertEquals(1f, value.imageOpacity, 0f)
        assertEquals(1f, UiPassthroughSettings(textOpacity = Float.NaN).normalized().textOpacity, 0f)
    }

    @Test
    fun disablingPassthroughLeavesTheEntireSchemeUnchanged() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { colors ->
            assertSame(colors, colors.withUiPassthrough(UiPassthroughSettings(surfaceOpacity = 0f)))
        }
    }

    @Test
    fun disabledMiuixPassthroughKeepsTheCurrentDayNightPalette() {
        listOf(lightMiuixColorScheme(), darkMiuixColorScheme()).forEach { colors ->
            assertSame(colors, colors.withUiPassthrough(UiPassthroughSettings(surfaceOpacity = 0f)))
        }
    }

    @Test
    fun miuixSurfacesFollowOpacityWithoutFadingTextOrChangingThePalette() {
        listOf(lightMiuixColorScheme(), darkMiuixColorScheme()).forEach { base ->
            listOf(0f, 0.35f, 1f).forEach { opacity ->
                val colors = base.withUiPassthrough(UiPassthroughSettings(enabled = true, surfaceOpacity = opacity))
                assertEquals(0f, colors.background.alpha, 0f)
                listOf(base.surface to colors.surface, base.surfaceVariant to colors.surfaceVariant,
                    base.surfaceContainer to colors.surfaceContainer,
                    base.surfaceContainerHigh to colors.surfaceContainerHigh,
                    base.surfaceContainerHighest to colors.surfaceContainerHighest).forEach { (before, after) ->
                    assertEquals(before.alpha * opacity, after.alpha, 0.005f)
                    assertEquals(before.red, after.red, 0.005f)
                    assertEquals(before.green, after.green, 0.005f)
                    assertEquals(before.blue, after.blue, 0.005f)
                }
                assertEquals(base.onSurface, colors.onSurface)
                assertEquals(base.onBackground, colors.onBackground)
                assertEquals(base.primary, colors.primary)
                assertEquals(base.onPrimary, colors.onPrimary)
                assertEquals(base.error, colors.error)
            }
        }
    }

    @Test
    fun miuixBridgeNormalizesInvalidOpacityWithoutMutatingTheBasePalette() {
        val base = darkMiuixColorScheme()
        val surface = base.surface
        val colors = base.withUiPassthrough(UiPassthroughSettings(enabled = true, surfaceOpacity = Float.NaN))
        assertEquals(surface.alpha * 0.65f, colors.surface.alpha, 0.005f)
        assertEquals(surface, base.surface)
        assertEquals(base.onSurface, colors.onSurface)
    }

    @Test
    fun clearSurfacesNeverFadeForegroundColorsInEitherTheme() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { original ->
            val colors = original.withUiPassthrough(UiPassthroughSettings(enabled = true, surfaceOpacity = 0f))
            assertEquals(0f, colors.background.alpha, 0f)
            listOf(colors.surface, colors.surfaceContainer, colors.surfaceContainerHigh,
                colors.surfaceContainerLow, colors.surfaceVariant, colors.surfaceDim, colors.surfaceBright)
                .forEach { assertEquals(0f, it.alpha, 0f) }
            assertEquals(original.onSurface, colors.onSurface)
            assertEquals(original.onSurfaceVariant, colors.onSurfaceVariant)
            assertEquals(original.onBackground, colors.onBackground)
            assertEquals(original.onPrimary, colors.onPrimary)
            assertEquals(original.primary, colors.primary)
            assertEquals(original.error, colors.error)
        }
    }

    @Test
    fun translucentSurfaceKeepsItsOriginalRgb() {
        val original = lightColorScheme(surface = Color(0xFFFAFAFA))
        val color = original.withUiPassthrough(UiPassthroughSettings(enabled = true, surfaceOpacity = 0.4f)).surface
        assertEquals(0.4f, color.alpha, 0.005f)
        assertEquals(original.surface.red, color.red, 0.005f)
        assertEquals(original.surface.green, color.green, 0.005f)
        assertEquals(original.surface.blue, color.blue, 0.005f)
    }

    @Test
    fun transparentTopAndBottomBarsUseTheDayOrNightSurfaceInsteadOfBlack() {
        listOf(Color.White, Color(0xFF222222)).forEach { surface ->
            val color = uiBackgroundWithOpacity(Color.Transparent, 0.75f, surface)
            assertEquals(surface.red, color.red, 0.005f)
            assertEquals(surface.blue, color.blue, 0.005f)
            assertEquals(0.75f, color.alpha, 0.005f)
            assertEquals(0f, uiBackgroundWithOpacity(surface, 0f, Color.Black).alpha, 0f)
        }
    }

    @Test
    fun legacyWallpaperOverlayMigratesToSurfaceOpacityOnly() {
        val memory = MemoryPreferences(mutableMapOf(
            CUSTOM_WALLPAPER_PASSTHROUGH_ENABLED_KEY to true,
            CUSTOM_WALLPAPER_PASSTHROUGH_OPACITY_KEY to 0.7f))
        val global = memory.prefs.readUiPassthroughScope(null)
        assertTrue(global.settings.enabled)
        // The legacy overlay capped its input at 65%, including stored out-of-range values.
        assertEquals(0.35f, global.settings.surfaceOpacity, 0.0001f)
        assertEquals(1f, global.settings.textOpacity, 0f)
        assertEquals(global.settings, memory.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Store).settings)
    }

    @Test
    fun writingNewGlobalSettingsAlsoSynchronizesLegacyReaders() {
        val memory = MemoryPreferences()
        val editor = memory.prefs.edit()
        editor.putUiPassthroughScope(null, UiPassthroughSettings(enabled = true, surfaceOpacity = 0.25f))
        editor.apply()
        assertEquals(true, memory.values[CUSTOM_WALLPAPER_PASSTHROUGH_ENABLED_KEY])
        assertEquals(0.75f, memory.values[CUSTOM_WALLPAPER_PASSTHROUGH_OPACITY_KEY])
    }

    @Test
    fun pageOverrideIsIsolatedAndCanFollowGlobalAgain() {
        val memory = MemoryPreferences()
        val global = UiPassthroughSettings(enabled = true, surfaceOpacity = 0.7f)
        val local = global.copy(surfaceOpacity = 0.1f, textOpacity = 0.8f)
        val editor = memory.prefs.edit()
        editor.putUiPassthroughScope(null, global)
        editor.putUiPassthroughScope(CustomPageBackgroundTarget.Store, local)
        editor.apply()
        assertEquals(local, memory.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Store).settings)
        assertEquals(global, memory.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Templates).settings)
        val inherit = memory.prefs.edit()
        inherit.putUiPassthroughScope(CustomPageBackgroundTarget.Store, local, overridesGlobal = false)
        inherit.apply()
        val scope = memory.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Store)
        assertFalse(scope.overridesGlobal)
        assertEquals(global, scope.settings)
    }

    @Test
    fun themePackageRoundTripPreservesGlobalAndIndependentPageSettings() {
        val memory = MemoryPreferences()
        val global = UiPassthroughSettings(enabled = true, surfaceOpacity = 0.4f, iconOpacity = 0.9f)
        val about = global.copy(surfaceOpacity = 0f, topBarOpacity = 0.3f, imageOpacity = 0.6f)
        val editor = memory.prefs.edit()
        editor.putUiPassthroughScope(null, global)
        editor.putUiPassthroughScope(CustomPageBackgroundTarget.About, about)
        editor.apply()
        val json = JSONObject(memory.prefs.exportUiPassthrough().toString())
        validateUiPassthroughPackage(json)
        val restored = MemoryPreferences()
        val restore = restored.prefs.edit()
        restore.putUiPassthroughPackage(json)
        restore.apply()
        assertEquals(global, restored.prefs.readUiPassthroughScope(null).settings)
        assertEquals(about, restored.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.About).settings)
        assertTrue(restored.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.About).overridesGlobal)
        assertFalse(restored.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Store).overridesGlobal)
    }

    @Test
    fun missingPageInPackageResetsStaleOverrideToGlobalInheritance() {
        val memory = MemoryPreferences()
        val old = memory.prefs.edit()
        old.putUiPassthroughScope(CustomPageBackgroundTarget.Store, UiPassthroughSettings(enabled = true))
        old.apply()
        val json = JSONObject().put("global", UiPassthroughScope(UiPassthroughSettings()).toJson())
        val editor = memory.prefs.edit()
        editor.putUiPassthroughPackage(json)
        editor.apply()
        assertFalse(memory.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Store).overridesGlobal)
        assertFalse(memory.prefs.readUiPassthroughScope(CustomPageBackgroundTarget.Store).settings.enabled)
    }

    @Test
    fun packageRejectsUnknownScopesMissingFieldsAndInvalidOpacityTypes() {
        val valid = MemoryPreferences().prefs.exportUiPassthrough()
        assertThrows(IllegalArgumentException::class.java) {
            validateUiPassthroughPackage(JSONObject(valid.toString()).put("unexpected", JSONObject()))
        }
        listOf(-1, 2, "0.5", JSONObject.NULL).forEach { invalid ->
            val json = JSONObject(valid.toString())
            json.getJSONObject("global").put("surface", invalid)
            assertThrows(IllegalArgumentException::class.java) { validateUiPassthroughPackage(json) }
        }
        val missing = JSONObject(valid.toString())
        missing.getJSONObject("global").remove("icon")
        assertThrows(IllegalArgumentException::class.java) { validateUiPassthroughPackage(missing) }
    }

    @Test
    fun secondaryPagesNeverResolveFromNegativeMainPageIndex() {
        assertNull(CustomPageBackgroundTarget.fromMainPageIndex(-1))
        assertEquals(CustomPageBackgroundTarget.Settings, CustomPageBackgroundTarget.fromMainPageIndex(3))
    }

    private class MemoryPreferences(val values: MutableMap<String, Any> = mutableMapOf()) {
        val prefs: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean", "getFloat", "getInt", "getString" -> values[args!![0]] ?: args[1]
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args!![0])
                "edit" -> editor()
                else -> null
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any>()
            val removed = mutableSetOf<String>()
            return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
                when {
                    method.name.startsWith("put") -> {
                        pending[args!![0] as String] = args[1]
                        proxy
                    }
                    method.name == "remove" -> { removed.add(args!![0] as String); proxy }
                    method.name == "apply" || method.name == "commit" -> {
                        removed.forEach(values::remove)
                        values.putAll(pending)
                        if (method.name == "commit") true else null
                    }
                    else -> null
                }
            } as SharedPreferences.Editor
        }
    }
}
