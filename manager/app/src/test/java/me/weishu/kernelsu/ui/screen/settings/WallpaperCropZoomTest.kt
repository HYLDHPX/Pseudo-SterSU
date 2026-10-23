package me.weishu.kernelsu.ui.screen.settings

import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.DEFAULT_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.FULL_CUSTOM_WALLPAPER_CROP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperCropZoomTest {
    private val tolerance = 0.00001f

    @Test fun minimumZoomRestoresTheWholeDeviceImage() {
        val crop = scaleCropForZoom(DEFAULT_CUSTOM_WALLPAPER_CROP, 1f, null)

        assertEquals(FULL_CUSTOM_WALLPAPER_CROP, crop)
        assertEquals(1f, cropZoom(crop), tolerance)
    }

    @Test fun sliderValueAndReportedZoomStayInSync() {
        var crop = DEFAULT_CUSTOM_WALLPAPER_CROP
        listOf(1.3f, 2f, 4f, 8f, 2f, 1.1f, 1f).forEach { zoom ->
            crop = scaleCropForZoom(crop, zoom, null)
            assertEquals(zoom, cropZoom(crop), tolerance)
        }
        assertEquals(FULL_CUSTOM_WALLPAPER_CROP, crop)
    }

    @Test fun repeatingTheReportedZoomDoesNotChangeTheCrop() {
        val crop = scaleCropForZoom(DEFAULT_CUSTOM_WALLPAPER_CROP, 2f, null)
        val repeated = scaleCropForZoom(crop, cropZoom(crop), null)

        assertEquals(crop, repeated)
    }

    @Test fun freeCropKeepsItsProportionsWhenZoomingOut() {
        val crop = scaleCropForZoom(CustomWallpaperCrop(0.3f, 0.1f, 0.7f, 0.9f), 1f, null)

        assertEquals(1f, cropZoom(crop), tolerance)
        assertEquals(0.5f, crop.width / crop.height, tolerance)
        assertEquals(1f, crop.height, tolerance)
    }

    @Test fun fixedAspectCropsReachBothSliderEndpointsWithoutDistortion() {
        listOf(0.25f, 0.5f, 1f, 2f, 4f).forEach { aspect ->
            val maxZoom = maximumCropZoom(FULL_CUSTOM_WALLPAPER_CROP, aspect)
            listOf(1f, (1f + maxZoom) / 2f, maxZoom).forEach { zoom ->
                val crop = scaleCropForZoom(FULL_CUSTOM_WALLPAPER_CROP, zoom, aspect)
                assertEquals(zoom, cropZoom(crop), tolerance)
                assertEquals(aspect, crop.width / crop.height, tolerance)
                assertTrue(crop.width >= 0.12f - tolerance)
                assertTrue(crop.height >= 0.12f - tolerance)
            }
        }
    }

    @Test fun zoomKeepsOffCenterCropsInsideTheImage() {
        val original = CustomWallpaperCrop(0f, 0.6f, 0.4f, 1f)
        val crop = scaleCropForZoom(original, 1.3f, null)

        assertEquals(1.3f, cropZoom(crop), tolerance)
        assertTrue(crop.left >= 0f && crop.top >= 0f)
        assertTrue(crop.right <= 1f && crop.bottom <= 1f)
    }

    @Test fun devicePhotoSavesAndReopensAtTheSelectedZoom() {
        val photo = SettingsDevicePhoto(
            "file:///private/device.image",
            scaleCropForZoom(DEFAULT_CUSTOM_WALLPAPER_CROP, 1f, null),
        )
        val restored = SettingsDevicePhoto.decode(photo.encode())!!

        assertEquals(FULL_CUSTOM_WALLPAPER_CROP, restored.crop)
        assertEquals(1f, cropZoom(restored.crop), tolerance)
    }
}
