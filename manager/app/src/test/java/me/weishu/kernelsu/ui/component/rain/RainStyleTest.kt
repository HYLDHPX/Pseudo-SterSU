package me.weishu.kernelsu.ui.component.rain

import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.util.InterfaceStyleChrome
import me.weishu.kernelsu.ui.util.InterfaceStyleGlass
import me.weishu.kernelsu.ui.util.InterfaceStylePalette
import me.weishu.kernelsu.ui.util.InterfaceStyleScene
import me.weishu.kernelsu.ui.util.InterfaceStyleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RainStyleTest {
    @Test
    fun unknownAndMissingValuesFallBackToLightRain() {
        assertEquals(RainStyle.LightRain, RainStyle.fromValue(null))
        assertEquals(RainStyle.LightRain, RainStyle.fromValue("unknown"))
        assertEquals(RainStyle.LightRain.value, RainStyle.DEFAULT_VALUE)
    }

    @Test
    fun indexMappingUsesStableIntensityOrder() {
        assertEquals(RainStyle.LightRain, RainStyle.fromIndex(0))
        assertEquals(RainStyle.MediumRain, RainStyle.fromIndex(1))
        assertEquals(RainStyle.HeavyRain, RainStyle.fromIndex(2))
        assertEquals(RainStyle.Thunderstorm, RainStyle.fromIndex(3))
        assertEquals(RainStyle.AfterRain, RainStyle.fromIndex(4))
        assertEquals(RainStyle.LightRain, RainStyle.fromIndex(-1))
        assertEquals(RainStyle.LightRain, RainStyle.fromIndex(99))
    }

    @Test
    fun selectedIndexSanitizesStoredValue() {
        RainStyle.entries.forEachIndexed { index, style ->
            assertEquals(index, RainStyle.selectedIndex(style.value))
        }
        assertEquals(0, RainStyle.selectedIndex("invalid"))
    }

    @Test
    fun modesHaveUniqueMetadata() {
        assertEquals(5, RainStyle.entries.size)
        assertEquals(RainStyle.entries.size, RainStyle.entries.map { it.value }.toSet().size)
        assertEquals(RainStyle.entries.size, RainStyle.entries.map { it.keyColor }.toSet().size)
    }

    @Test
    fun sceneSpecComesFromDownloadedTheme() {
        val theme = theme(primaryCount = 142, secondaryCount = 15, cycleMillis = 8_500)
        val spec = RainSceneSpec.fromTheme(theme)

        assertEquals(142, spec.dropCount)
        assertEquals(15, spec.rippleCount)
        assertEquals(8_500, spec.cycleMillis)
        assertEquals(34f, spec.maxLengthDp)
    }

    @Test
    fun downloadedPaletteOverridesMetadataFallback() {
        val theme = theme()
        val palette = rainPalette(theme, RainStyle.LightRain, dark = false)

        assertEquals(Color(0xFF102030), palette.backgroundTop)
        assertEquals(Color(0xFF203040), palette.backgroundBottom)
        assertEquals(Color(0xFF607080), palette.content)
        assertNotEquals(rainPalette(RainStyle.LightRain, false), palette)
    }

    @Test
    fun forcedDarkIsPackageControlledForTheMatchingVariant() {
        val theme = theme(forceDark = true)

        assertTrue(forceRainDarkTheme(RainStyle.LightRain, theme))
        assertFalse(forceRainDarkTheme(RainStyle.LightRain, null))
        // A package for another scene must not force dark on the current one.
        assertFalse(forceRainDarkTheme(RainStyle.MediumRain, theme))
    }

    @Test
    fun heavyRainForcesDarkWithoutAThemePackage() {
        assertTrue(forceRainDarkTheme(RainStyle.HeavyRain, null))
        assertTrue(forceRainDarkTheme(RainStyle.HeavyRain, theme(variant = RainStyle.Thunderstorm.value, forceDark = false)))
        RainStyle.entries.filterNot { it == RainStyle.HeavyRain }.forEach {
            assertFalse(it.value, forceRainDarkTheme(it, null))
        }
    }

    @Test
    fun cardCanopyScalesAndSkipsTinyCards() {
        assertEquals(13f, rainCardDecorationHeight(13f, 320f, 180f), 0.001f)
        assertEquals(9.5f, rainCardDecorationHeight(13f, 320f, 50f), 0.001f)
        assertEquals(0f, rainCardDecorationHeight(13f, 71f, 180f), 0.001f)
        assertEquals(0f, rainCardDecorationHeight(13f, 320f, 47f), 0.001f)
    }

    @Test
    fun disabledCardSurfacePaintsNoCanopy() {
        // A zero cap height is how the "Dry" option removes the wet edge.
        assertEquals(0f, rainCardDecorationHeight(0f, 320f, 180f), 0f)
    }

    private fun theme(
        variant: String = RainStyle.LightRain.value,
        forceDark: Boolean = false,
        lightning: Boolean = false,
        primaryCount: Int = 68,
        secondaryCount: Int = 6,
        cycleMillis: Int = 15_000,
    ): InterfaceStyleTheme {
        val light = InterfaceStylePalette(
            background = 0xFF102030,
            backgroundAlt = 0xFF203040,
            surface = 0xFF304050,
            surfaceAlt = 0xFF405060,
            primary = 0xFF506070,
            secondary = 0xFF708090,
            outline = 0xFF8090A0,
            highlight = 0xFFFFFFFF,
            shadow = 0xFF000000,
            muted = 0xFF90A0B0,
            content = 0xFF607080,
        )
        return InterfaceStyleTheme(
            engine = "rain",
            variant = variant,
            accent = 0xFF5E84A6,
            forceDark = forceDark,
            lightPalette = light,
            darkPalette = light.copy(background = 0xFF010203),
            scene = InterfaceStyleScene(
                cycleMillis = cycleMillis,
                primaryCount = primaryCount,
                secondaryCount = secondaryCount,
                speed = 1f,
                angle = 0.2f,
                minLengthDp = 18f,
                maxLengthDp = 34f,
                minStrokeDp = 0.5f,
                maxStrokeDp = 1.1f,
                minAlpha = 0.2f,
                maxAlpha = 0.54f,
                gridDp = 18f,
                clearOnCycle = false,
                lightning = lightning,
                motifs = emptyList(),
            ),
            chrome = InterfaceStyleChrome(0.76f, 0.6f, 14f, 1.5f, 0.62f, 0.62f),
            glass = InterfaceStyleGlass(0.7f, 12f, 0.55f, false, 0f, 0f, 0f),
        )
    }
}
