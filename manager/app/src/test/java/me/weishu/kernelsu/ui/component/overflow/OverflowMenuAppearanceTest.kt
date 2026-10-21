package me.weishu.kernelsu.ui.component.overflow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverflowMenuAppearanceTest {
    private fun source(path: String) = File("src/main/java/me/weishu/kernelsu/ui/$path").readText()

    @Test
    fun largeFontsAndShortWindowsStayWithinTheWindow() {
        assertEquals(260f, overflowMenuWidth(840f, 2f), 0.001f)
        assertEquals(176f, overflowMenuWidth(200f, 2f), 0.001f)
        assertEquals(216f, overflowMenuMaxHeight(240f), 0.001f)
        assertEquals(0f, overflowMenuMaxHeight(20f), 0.001f)
        assertEquals(0f, overflowMenuMaxHeight(Float.NaN), 0.001f)
        assertEquals(0f, overflowMenuWidth(Float.NaN, 1f), 0.001f)
        assertEquals(208f, overflowMenuWidth(412f, Float.NaN), 0.001f)
    }

    @Test
    fun glassTextHasReadableContrastOverBothLightAndDarkWallpapers() {
        for (dark in listOf(false, true)) {
            val text = overflowMenuContentColor(dark)
            assertEquals(1f, text.alpha, 0.001f)
            for (wallpaper in listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Green)) {
                val background = overflowMenuTint(dark, true).compositeOver(wallpaper)
                val bright = maxOf(text.luminance(), background.luminance())
                val dim = minOf(text.luminance(), background.luminance())
                assertTrue("dark=$dark wallpaper=$wallpaper", (bright + 0.05f) / (dim + 0.05f) >= 4.5f)
            }
        }
    }

    @Test
    fun unsupportedBlurFallsBackToAnOpaqueThemedSurface() {
        assertEquals(1f, overflowMenuTint(false, false).alpha, 0.001f)
        assertEquals(1f, overflowMenuTint(true, false).alpha, 0.001f)
        assertTrue(overflowMenuTint(true, false).luminance() < 0.05f)
        assertTrue(overflowMenuTint(false, false).luminance() > 0.85f)
    }

    @Test
    fun menuWallpapersHaveIndependentPersistentKeys() {
        assertEquals(2, OverflowMenuTarget.entries.map { it.wallpaperKey }.toSet().size)
        val wallpaper = source("component/overflow/OverflowMenuWallpaper.kt")
        assertTrue(wallpaper.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(wallpaper.contains("persistCustomImageReference(context, uri, target.wallpaperKey"))
        assertTrue(wallpaper.contains("loadCustomImageBitmap(context, newReference"))
        assertTrue(wallpaper.contains("Dispatchers.IO"))
        assertTrue(wallpaper.contains("preferences.registerOnSharedPreferenceChangeListener(listener)"))
        assertTrue(wallpaper.contains("preferences.unregisterOnSharedPreferenceChangeListener(listener)"))
        assertTrue(wallpaper.contains("if (saved) releaseCustomImageReference(context, previous)"))
        assertTrue(wallpaper.contains("if (uri != null && !busy)"))
    }

    @Test
    fun blurAndWallpaperStayBehindOpaqueAccessibleMenuRows() {
        val menu = source("component/overflow/FrostedOverflowMenu.kt")
        assertTrue(menu.contains("UiPassthroughTheme(UiPassthroughSettings())"))
        assertTrue(menu.contains("Modifier.matchParentSize().drawBackdrop("))
        assertTrue(menu.contains(".blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)"))
        assertTrue(menu.contains(".heightIn(min = 48.dp)"))
        assertFalse(menu.contains(".alpha("))
        assertTrue(menu.contains("showWallpaperEditor = true"))
        assertTrue(menu.contains("OverflowMenuWallpaperDialog("))
    }

    @Test
    fun materialAndMiuixModuleHeadersKeepSortingInsideOneSharedMenu() {
        for (file in listOf("ModuleMaterial.kt", "ModuleMiuix.kt")) {
            val header = source("screen/module/$file").substringBefore("floatingActionButton =")
            assertTrue(header.contains("ModuleOverflowMenu("))
            assertFalse(header.contains("CheckableDropdownMenuItem("))
        }
        val menu = source("screen/module/ModuleOverflowMenu.kt")
        assertTrue(menu.contains("OverflowMenuTarget.Module"))
        assertTrue(menu.contains("actions.onToggleSortActionFirst"))
        assertTrue(menu.contains("actions.onToggleSortEnabledFirst"))
        assertTrue(menu.contains("toggleableState = ToggleableState(checked)"))
    }

    @Test
    fun menusHaveAnOnDemandBackdropEvenWithGlobalBlurDisabled() {
        val backdrop = source("component/overflow/OverflowMenuBackdrop.kt")
        assertTrue(backdrop.contains("LocalView.current.isHardwareAccelerated"))
        assertTrue(backdrop.contains("rememberBlurBackdrop(supported && inherited == null)"))
        assertTrue(backdrop.contains("inherited == null && state.captureActive && pageBackdrop != null"))
        assertTrue(backdrop.contains("Modifier.layerBackdrop(pageBackdrop)"))
        assertFalse(backdrop.contains("LocalEnableBlur"))
        for (page in listOf("module/ModuleScreen.kt", "superuser/SuperUserScreen.kt")) {
            assertTrue(source("screen/$page").contains("OverflowMenuBackdrop {"))
        }
        val menu = source("component/overflow/FrostedOverflowMenu.kt")
        assertTrue(menu.contains("pageBackdrop?.captureActive = expanded"))
        assertTrue(menu.contains("onDispose { pageBackdrop?.captureActive = false }"))
    }
}
