package me.weishu.kernelsu.ui.screen.module

import android.content.SharedPreferences
import androidx.annotation.StringRes
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.FULL_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.sanitizeCustomWallpaperCrop

internal enum class ModuleButtonImageSlot(val key: String, @param:StringRes val label: Int) {
    Action("action", R.string.action),
    Uninstall("uninstall", R.string.uninstall),
    Wallpaper("wallpaper", R.string.module_wallpaper_editor_open),
}

internal data class ModuleButtonImage(
    val uriString: String? = null,
    val crop: CustomWallpaperCrop = FULL_CUSTOM_WALLPAPER_CROP,
)

internal fun readModuleButtonImage(prefs: SharedPreferences, slot: ModuleButtonImageSlot): ModuleButtonImage {
    val prefix = slot.key
    val uri = prefs.getString("${prefix}_uri", null)?.takeIf { it.isNotBlank() }
        ?: return ModuleButtonImage()
    return ModuleButtonImage(
        uriString = uri,
        crop = sanitizeCustomWallpaperCrop(
            CustomWallpaperCrop(
                prefs.getFloat("${prefix}_left", 0f),
                prefs.getFloat("${prefix}_top", 0f),
                prefs.getFloat("${prefix}_right", 1f),
                prefs.getFloat("${prefix}_bottom", 1f),
            ),
        ),
    )
}

/** Each role is independent; clearing a picture also clears its old crop. */
internal fun saveModuleButtonImage(
    prefs: SharedPreferences,
    slot: ModuleButtonImageSlot,
    image: ModuleButtonImage,
): Boolean {
    val prefix = slot.key
    val editor = prefs.edit()
    if (image.uriString.isNullOrBlank()) {
        listOf("uri", "left", "top", "right", "bottom").forEach { editor.remove("${prefix}_$it") }
    } else {
        val crop = sanitizeCustomWallpaperCrop(image.crop)
        editor.putString("${prefix}_uri", image.uriString)
            .putFloat("${prefix}_left", crop.left)
            .putFloat("${prefix}_top", crop.top)
            .putFloat("${prefix}_right", crop.right)
            .putFloat("${prefix}_bottom", crop.bottom)
    }
    return editor.commit()
}
