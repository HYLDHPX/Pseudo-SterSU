package me.weishu.kernelsu.ui.component.rebootlistpopup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source wiring checks supplement compilation; they do not replace device rendering tests. */
class SidebarRebootWallpaperTest {
    private val popup = File(
        "src/main/java/me/weishu/kernelsu/ui/component/rebootlistpopup/SidebarRebootPopup.kt"
    ).readText()
    private val wallpaper = File(
        "src/main/java/me/weishu/kernelsu/ui/screen/home/HomeMetricCardWallpaper.kt"
    ).readText()

    @Test
    fun sidebarUsesTheExistingRebootMenuWallpaperInsteadOfSeparatePreferences() {
        assertTrue(popup.contains("target = HomeMetricCardWallpaperTarget.RebootMenu"))
        assertTrue(popup.contains("rememberHomeMetricCardWallpaperState("))
        assertFalse(popup.contains("getSharedPreferences("))
        assertFalse(popup.contains("forceNight ="))
    }

    @Test
    fun imageVideoCropAndVisualSettingsReachTheSharedRenderer() {
        assertTrue(popup.contains("rememberHomeMetricCardWallpaperBitmap("))
        assertTrue(popup.contains("uriString = wallpaperState.uriString"))
        assertTrue(popup.contains("crop = wallpaperState.crop"))
        assertTrue(popup.contains("HomeMetricCardWallpaperBackground("))
        assertTrue(popup.contains("bitmap = wallpaperBitmap"))
        assertTrue(popup.contains("videoUriString = wallpaperState.videoUriString"))
        assertTrue(popup.contains("videoCrop = wallpaperState.crop"))
        assertTrue(popup.contains("visualSettings = wallpaperState.visualSettings"))
    }

    @Test
    fun sharedStateRetainsDayNightUpdatesAndClearedMediaFallback() {
        assertTrue(wallpaper.contains("val darkTheme = isInDarkTheme()"))
        assertTrue(wallpaper.contains("storedState.activeVariant(darkTheme"))
        assertTrue(wallpaper.contains("registerOnSharedPreferenceChangeListener(listener)"))
        assertTrue(wallpaper.contains("return if (uriString.isNullOrBlank()) null else decodedBitmap ?: lastBitmap"))
        assertTrue(popup.contains("wallpaperBitmap != null || !wallpaperState.videoUriString.isNullOrBlank()"))
        assertTrue(popup.contains("contentColor = if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurface"))
        assertTrue(popup.contains("MaterialTheme.colorScheme.surfaceContainerHigh"))
    }

    @Test
    fun wallpaperDoesNotFadeContentOrReplaceTheGridAndRebootActions() {
        assertFalse(popup.contains(".alpha("))
        assertTrue(popup.contains("Color.Black.copy(alpha = 0.56f)"))
        assertTrue(popup.contains(".background(tileColor)"))
        assertTrue(popup.contains("options.chunked(columns)"))
        assertTrue(popup.contains(".aspectRatio(1f)"))
        assertTrue(popup.contains(".heightIn(max = (screen.screenHeightDp - 40)"))
        assertTrue(popup.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(popup.contains("onReboot(option.reason)"))
    }

    @Test
    fun wallpaperIsCreatedInsidePopupContentNotTheAlwaysVisibleRail() {
        val popupContentStart = popup.indexOf("DropdownMenuPopup(expanded = expanded")
        assertTrue(popupContentStart >= 0)
        assertTrue(popup.indexOf("val wallpaperState =") > popupContentStart)
    }
}
