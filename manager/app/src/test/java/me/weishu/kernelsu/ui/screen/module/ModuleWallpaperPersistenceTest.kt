package me.weishu.kernelsu.ui.screen.module

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.DEFAULT_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.MediaMotionStyle
import me.weishu.kernelsu.ui.util.MediaVisualSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleWallpaperPersistenceTest {
    @Test
    fun savingAndReloadingPreservesImagesCropsAndBothVariants() {
        val prefs = MemoryPreferences()
        val day = entry("day").copy(
            crop = CustomWallpaperCrop(0.2f, 0.3f, 0.8f, 0.9f),
            visualSettings = MediaVisualSettings(opacity = 0.7f, motionStyle = MediaMotionStyle.SlowPan),
            autoContrast = false,
        )
        val expected = ModuleCardWallpaperSnapshot(emptyList(), false)
            .withCollection(ModuleWallpaperVariant.Day, ModuleWallpaperCollection(entries = listOf(day)))
            .withCollection(
                ModuleWallpaperVariant.Night,
                ModuleWallpaperCollection(
                    entries = listOf(entry("night-1"), entry("night-2")),
                    carouselEnabled = true,
                    carouselOrder = ModuleWallpaperCarouselOrder.Random,
                    intervalMillis = 9_000L,
                    selectedIndex = 1,
                ),
            )

        assertTrue(prefs.value.edit().putModuleCardWallpaperSnapshot("demo", expected).commit())

        assertEquals(expected, readModuleCardWallpaperSnapshot(prefs.value, "demo"))
        assertTrue(readModuleCardWallpaperSnapshot(prefs.value, "another-module").allEntries().isEmpty())
        assertTrue(readModuleCardWallpaperSnapshot(prefs.value, kpmCardWallpaperId("demo")).allEntries().isEmpty())
    }

    @Test
    fun nightOnlyWallpaperSurvivesReloadAndClearingDoesNotReviveLegacyImage() {
        val prefs = MemoryPreferences()
        val both = ModuleCardWallpaperSnapshot(listOf(entry("day")), false, nightEntries = listOf(entry("night")))
        prefs.value.edit().putModuleCardWallpaperSnapshot("demo", both).commit()
        val nightOnly = both.withCollection(ModuleWallpaperVariant.Day, ModuleWallpaperCollection())
        prefs.value.edit().putModuleCardWallpaperSnapshot("demo", nightOnly).commit()
        assertEquals(nightOnly, readModuleCardWallpaperSnapshot(prefs.value, "demo"))

        prefs.value.edit().putModuleCardWallpaperSnapshot("demo", ModuleCardWallpaperSnapshot(emptyList(), false)).commit()
        assertTrue(readModuleCardWallpaperSnapshot(prefs.value, "demo").allEntries().isEmpty())
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun legacyImageCollectionsStillReload() {
        val prefs = MemoryPreferences()
        prefs.values["module_card_wallpaper_demo_entries"] = """[{"uri":"legacy"}]"""
        assertEquals(listOf(entry("legacy")), readModuleCardWallpaperSnapshot(prefs.value, "demo").entries)
    }

    @Test
    fun editorAndCardChooseTheSameActiveDayNightCollection() {
        val day = ModuleCardWallpaperSnapshot(listOf(entry("day")), false)
        val both = day.copy(nightEntries = listOf(entry("night")))
        assertEquals(ModuleWallpaperVariant.Day, activeModuleWallpaperVariant(false, both))
        assertEquals(ModuleWallpaperVariant.Night, activeModuleWallpaperVariant(true, both))
        assertEquals(ModuleWallpaperVariant.Day, activeModuleWallpaperVariant(true, day))
    }

    @Test
    fun importingCommitsBeforeReturningToTheCancellableUiScope() {
        val source = File("src/main/java/me/weishu/kernelsu/ui/screen/module/ModuleCardWallpaper.kt").readText()
        val operation = source.substringAfter("internal fun importAndSaveModuleWallpapers(")
            .substringBefore("internal fun duplicateModuleWallpaperSnapshotForTarget(")
        assertTrue(operation.contains("importModuleWallpaperEntries(context, moduleId, variant, uris).getOrThrow()"))
        assertTrue(operation.contains("val latest = readModuleCardWallpaperSnapshot(context, moduleId)"))
        assertTrue(operation.contains("check(replaceModuleCardWallpaperSnapshot(context, moduleId, next))"))
        assertTrue(operation.contains("releaseCustomImageReference(context, it.uriString)"))
        val editor = File("src/main/java/me/weishu/kernelsu/ui/screen/module/ModuleWallpaperEditorScreen.kt")
            .readText().replace("\r\n", "\n")
        val picker = editor.substringAfter("val imagePicker =").substringBefore("LaunchedEffect(Unit)")
        assertTrue(picker.contains("withContext(Dispatchers.IO) {\n                importAndSaveModuleWallpapers("))
        assertFalse(picker.contains("replaceModuleCardWallpaperSnapshot("))
    }

    @Test
    fun pickerRequestAndTargetVariantSurviveActivityRecreation() {
        val pager = File("src/main/java/me/weishu/kernelsu/ui/screen/module/ModuleScreen.kt").readText()
        val editor = File("src/main/java/me/weishu/kernelsu/ui/screen/module/ModuleWallpaperEditorScreen.kt").readText()
        assertTrue(pager.contains("wallpaperEditorRequest by rememberSaveable"))
        assertTrue(editor.contains("variant by rememberSaveable(moduleId)"))
        assertTrue(editor.contains("importMode by rememberSaveable(moduleId)"))
    }

    private fun entry(id: String) = ModuleCardWallpaperEntry(id, DEFAULT_CUSTOM_WALLPAPER_CROP)

    private class MemoryPreferences {
        val values = mutableMapOf<String, Any>()
        val value: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString", "getBoolean", "getFloat" -> values[args!![0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
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
