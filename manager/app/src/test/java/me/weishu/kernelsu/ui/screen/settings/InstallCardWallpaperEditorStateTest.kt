package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.screen.install.installCardWallpaperOverlayColor
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.DEFAULT_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.MediaVariantMode
import me.weishu.kernelsu.ui.util.MediaVariantSettings
import me.weishu.kernelsu.ui.util.MediaVisualSettings
import me.weishu.kernelsu.ui.util.ResponsiveCropSet
import me.weishu.kernelsu.ui.util.ThemeStoreImageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallCardWallpaperEditorStateTest {
    @Test
    fun previewAndRuntimeShareTheDayNightOverlay() {
        assertEquals(Color.White.copy(alpha = 0.42f), installCardWallpaperOverlayColor(false))
        assertEquals(Color.Black.copy(alpha = 0.10f), installCardWallpaperOverlayColor(true))
    }

    private val dayCrop = CustomWallpaperCrop(0.1f, 0.1f, 0.9f, 0.9f)
    private val nightCrop = CustomWallpaperCrop(0.2f, 0.2f, 0.8f, 0.8f)
    private val stored = ThemeStoreImageState(
        uriString = "content://wallpaper/day.png",
        videoUriString = null,
        crop = dayCrop,
        visualSettings = MediaVisualSettings(opacity = 0.8f),
        responsiveCrops = ResponsiveCropSet(landscape = dayCrop),
        nightUriString = null,
        nightVideoUriString = "content://wallpaper/night.mp4",
        nightCrop = nightCrop,
        nightVisualSettings = MediaVisualSettings(blurRadius = 8f),
        nightResponsiveCrops = ResponsiveCropSet(landscape = nightCrop),
        variantSettings = MediaVariantSettings(mode = MediaVariantMode.Random),
    )

    @Test
    fun dayEditorUsesOnlyDayMediaAndEffects() {
        val editing = installCardWallpaperEditingState(stored, night = false, aspectRatio = 1.62f)
        assertEquals(stored.uriString, editing.uriString)
        assertNull(editing.videoUriString)
        assertEquals(dayCrop, editing.crop)
        assertEquals(stored.visualSettings, editing.visualSettings)
        assertFalse(editing.hasNightSelected)
    }

    @Test
    fun nightEditorUsesOnlyNightMediaAndEffects() {
        val editing = installCardWallpaperEditingState(stored, night = true, aspectRatio = 1.48f)
        assertNull(editing.uriString)
        assertEquals(stored.nightVideoUriString, editing.videoUriString)
        assertEquals(nightCrop, editing.crop)
        assertEquals(stored.nightVisualSettings, editing.visualSettings)
        assertEquals(stored.nightResponsiveCrops, editing.responsiveCrops)
        assertFalse(editing.hasNightSelected)
    }

    @Test
    fun emptyNightDoesNotAccidentallyEditTheDayWallpaper() {
        val dayOnly = stored.copy(nightUriString = null, nightVideoUriString = null)
        assertFalse(installCardWallpaperEditingState(dayOnly, true, 1.36f).hasSelected)
        assertTrue(installCardWallpaperEditingState(dayOnly, false, 1.36f).hasSelected)
    }

    @Test
    fun emptyDayDoesNotAccidentallyEditTheNightWallpaper() {
        val nightOnly = stored.copy(uriString = null, videoUriString = null)
        assertFalse(installCardWallpaperEditingState(nightOnly, false, 1.36f).hasSelected)
        assertTrue(installCardWallpaperEditingState(nightOnly, true, 1.36f).hasSelected)
    }

    @Test
    fun previewIsPinnedToEditedVariantRegardlessOfThemeOrRotation() {
        listOf(false, true).forEach { night ->
            val editing = installCardWallpaperEditingState(stored, night, 1.62f)
            listOf(false, true).forEach { dark ->
                val preview = editing.activeVariant(dark, nowMillis = 0L)
                assertEquals(editing.uriString, preview.uriString)
                assertEquals(editing.videoUriString, preview.videoUriString)
                assertEquals(editing.visualSettings, preview.visualSettings)
            }
        }
        assertEquals(MediaVariantMode.Random, stored.variantSettings.mode)
        assertEquals("content://wallpaper/day.png", stored.uriString)
        assertEquals("content://wallpaper/night.mp4", stored.nightVideoUriString)
    }

    @Test
    fun selectedCardsKeepTheirResponsiveCrops() {
        listOf(1.62f, 1.48f, 1.36f).forEach { ratio ->
            assertEquals(dayCrop, installCardWallpaperEditingState(stored, false, ratio).crop)
            assertEquals(nightCrop, installCardWallpaperEditingState(stored, true, ratio).crop)
        }
        assertEquals(DEFAULT_CUSTOM_WALLPAPER_CROP, installCardWallpaperEditingState(stored, false, 1f).crop)
    }
}
