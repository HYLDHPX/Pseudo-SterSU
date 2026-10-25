package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.InterfaceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialThemeSettingsTest {
    private val generalPresets = listOf(
        ThemePreset.CLEAN_TOOL,
        ThemePreset.DYNAMIC_COLOR,
        ThemePreset.PREMIUM_GLOSS,
        ThemePreset.GEEK_DARK,
    )

    @Test
    fun generalPresetsKeepMaterialAndSidebarStylesInsteadOfSwitchingToMiuix() {
        for (style in listOf(InterfaceStyle.Material, InterfaceStyle.SidebarWidget)) {
            for (preset in generalPresets) {
                assertEquals(style.value, preset.targetUiMode(style.value))
                assertTrue(preset.isCompatibleWith(style.value))
                assertTrue(preset in ThemePreset.workshopPresets)
            }
        }
    }

    @Test
    fun generalPresetsKeepTheirExistingMiuixBehavior() {
        for (preset in generalPresets) {
            assertEquals(InterfaceStyle.Miuix.value, preset.targetUiMode(InterfaceStyle.Miuix.value))
            assertEquals(InterfaceStyle.Miuix.value, preset.targetUiMode(InterfaceStyle.Snow.value))
        }
    }

    @Test
    fun styleSpecificPresetsAreNotOfferedAsMaterialPresets() {
        for (preset in ThemePreset.entries.filterNot { it in generalPresets || it == ThemePreset.CUSTOM }) {
            assertFalse(preset.isCompatibleWith(InterfaceStyle.Material.value))
        }
    }

    @Test
    fun opacityAffectsSurfacesWithoutFadingTextIconsOrSemanticColors() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            val result = base.withMonetSurfaceOpacity(0.6f)
            for (surface in listOf(
                result.surface, result.surfaceDim, result.surfaceBright, result.surfaceVariant,
                result.surfaceContainerLowest, result.surfaceContainerLow, result.surfaceContainer,
                result.surfaceContainerHigh, result.surfaceContainerHighest,
            )) {
                assertEquals(0.6f, surface.alpha, 0.001f)
            }
            assertEquals(base.onSurface, result.onSurface)
            assertEquals(base.onSurfaceVariant, result.onSurfaceVariant)
            assertEquals(base.onBackground, result.onBackground)
            assertEquals(base.primary, result.primary)
            assertEquals(base.onPrimary, result.onPrimary)
            assertEquals(base.error, result.error)
            assertEquals(base.onError, result.onError)
            assertEquals(base.background, result.background)
        }
    }

    @Test
    fun defaultAndInvalidOpacityPreserveTheOriginalScheme() {
        val base = darkColorScheme()
        for (opacity in listOf(1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(base, base.withMonetSurfaceOpacity(opacity))
        }
        assertEquals(0.45f, base.withMonetSurfaceOpacity(-1f).surface.alpha, 0.001f)
    }

    @Test
    fun amoledAndMonetModesKeepTheirExistingDayNightMeaning() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(ColorMode.MONET_SYSTEM.toNonMonetMode()))
        assertEquals(ColorMode.LIGHT, ColorMode.fromValue(ColorMode.MONET_LIGHT.toNonMonetMode()))
        assertEquals(ColorMode.DARK, ColorMode.fromValue(ColorMode.MONET_DARK.toNonMonetMode()))
        assertEquals(ColorMode.DARK_AMOLED, ColorMode.fromValue(ColorMode.DARK_AMOLED.toNonMonetMode()))
        val amoled = darkColorScheme().amoledBackground(true).withMonetSurfaceOpacity(1f)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
    }
}
