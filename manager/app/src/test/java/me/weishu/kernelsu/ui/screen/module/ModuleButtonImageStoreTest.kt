package me.weishu.kernelsu.ui.screen.module

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.FULL_CUSTOM_WALLPAPER_CROP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleButtonImageStoreTest {
    @Test
    fun freshSettingsKeepAllBuiltInIcons() {
        val prefs = MemoryPreferences()
        ModuleButtonImageSlot.entries.forEach {
            assertEquals(ModuleButtonImage(), readModuleButtonImage(prefs.value, it))
        }
    }

    @Test
    fun imagesAndCropsPersistIndependentlyAcrossRoles() {
        val prefs = MemoryPreferences()
        val images = ModuleButtonImageSlot.entries.associateWith { slot ->
            ModuleButtonImage("file:///private/${slot.key}.png", CustomWallpaperCrop(0.1f, 0.2f, 0.8f, 0.9f))
        }
        images.forEach { (slot, image) -> assertTrue(saveModuleButtonImage(prefs.value, slot, image)) }
        images.forEach { (slot, image) -> assertEquals(image, readModuleButtonImage(prefs.value, slot)) }
    }

    @Test
    fun restoreDefaultAlsoRemovesCropWithoutChangingOtherButtons() {
        val prefs = MemoryPreferences()
        ModuleButtonImageSlot.entries.forEach { slot ->
            saveModuleButtonImage(prefs.value, slot, ModuleButtonImage("file:///${slot.key}.png"))
        }
        assertTrue(saveModuleButtonImage(prefs.value, ModuleButtonImageSlot.Action, ModuleButtonImage()))
        assertEquals(ModuleButtonImage(), readModuleButtonImage(prefs.value, ModuleButtonImageSlot.Action))
        assertFalse(prefs.values.keys.any { it.startsWith("action_") })
        assertEquals("file:///uninstall.png", readModuleButtonImage(prefs.value, ModuleButtonImageSlot.Uninstall).uriString)
    }

    @Test
    fun replacingPictureStartsWithItsOwnFullCrop() {
        val prefs = MemoryPreferences()
        val slot = ModuleButtonImageSlot.Wallpaper
        saveModuleButtonImage(prefs.value, slot, ModuleButtonImage("file:///old.png", CustomWallpaperCrop(0.3f, 0.3f, 0.6f, 0.6f)))
        saveModuleButtonImage(prefs.value, slot, ModuleButtonImage("file:///new.png"))
        assertEquals(ModuleButtonImage("file:///new.png", FULL_CUSTOM_WALLPAPER_CROP), readModuleButtonImage(prefs.value, slot))
    }

    @Test
    fun missingOrBlankPictureIgnoresStaleCrop() {
        val prefs = MemoryPreferences()
        prefs.values["action_uri"] = "  "
        prefs.values["action_left"] = 0.4f
        assertEquals(ModuleButtonImage(), readModuleButtonImage(prefs.value, ModuleButtonImageSlot.Action))
    }

    private class MemoryPreferences {
        val values = mutableMapOf<String, Any>()
        val value = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString", "getFloat" -> values[args!![0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                when {
                    method.name.startsWith("put") -> {
                        pending[args!![0] as String] = args[1]
                        proxy
                    }
                    method.name == "remove" -> {
                        pending[args!![0] as String] = null
                        proxy
                    }
                    method.name == "commit" -> {
                        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        true
                    }
                    else -> error("Unexpected editor call: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
