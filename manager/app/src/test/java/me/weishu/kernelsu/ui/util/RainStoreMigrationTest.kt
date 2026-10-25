package me.weishu.kernelsu.ui.util

import java.io.File
import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.component.rain.RainLayout
import me.weishu.kernelsu.ui.component.rain.RainSettings
import me.weishu.kernelsu.ui.component.rain.RainStyle
import me.weishu.kernelsu.ui.component.rain.toRainJson
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RainStoreMigrationTest {
    private val assets = File("src/main/assets/interface-style")
    private val catalog = File(assets, "catalog-v2.json").readBytes()
    private val signature = File(assets, "catalog-v2.sig").readBytes()
    private val style = parseInterfaceStyleCatalog(catalog.toString(Charsets.UTF_8)).styles.single {
        it.id == "rain-environment"
    }
    private val packageBytes = File("../../interface-styles/packages/rain-environment-20261025.ksstyle").readBytes()

    @Test
    fun rainIsAStoreDownloadWithASignedDataOnlyPackage() {
        assertFalse(InterfaceStyle.builtInEntries.contains(InterfaceStyle.Rain))
        val archive = verifySharedInterfaceStyleArchive(style.id, packageBytes, catalog, signature)
        assertEquals(style, archive.style)
        assertEquals(setOf("theme.json"), archive.bundle.resources.keys)
        assertEquals("rain", parseInterfaceStyleTheme(archive.bundle.resources.getValue("theme.json"), style).engine)
        assertTrue(style.downloadUrl.endsWith("/rain-environment-20261025.ksstyle"))
    }

    @Test
    fun oneCollectionSupportsEveryEditableRainSceneWithoutUnlockingInvalidVariants() {
        RainStyle.entries.forEach { assertTrue(style.supportsVariant(it.value)) }
        assertFalse(style.supportsVariant(null))
        assertFalse(style.supportsVariant("unknown"))
        assertFalse(style.copy(id = "rain-light").supportsVariant("thunderstorm"))
        assertFalse(style.copy(engine = "pixel").supportsVariant("thunderstorm"))
    }

    @Test
    fun sharedRainKeepsEveryLayoutProfileAndCanRestoreDifferentScenesOffline() {
        for (layout in RainLayout.entries) for (scene in RainStyle.entries) {
            val selection = selection(layout, scene).put("package", JSONObject()
                .put("id", style.id)
                .put("asset", JSONObject().put("path", "assets/rain.ksstyle"))
                .put("catalogAsset", JSONObject().put("path", "assets/catalog.json"))
                .put("signatureAsset", JSONObject().put("path", "assets/catalog.sig")))
            val imported = importThemeStoreInterfaceSelection(selection) { _, key, _ ->
                when (key) { "asset" -> packageBytes; "catalogAsset" -> catalog; else -> signature }
            }
            assertEquals(style.id, imported.archive?.style?.id)
            assertEquals(layout, imported.rain?.layout)
            assertEquals(RainLayout.entries.toSet(), imported.rain?.profiles?.keys)
            for (profile in requireNotNull(imported.rain).profiles.values) {
                assertEquals(scene, profile.scene)
                assertEquals(0.73f, profile.cardEdgeStrength, 0f)
                assertEquals(0.42f, profile.sidebarStrength, 0f)
                assertTrue(profile.rainThroughUi)
            }
        }
    }

    @Test
    fun oldBuiltInRainThemesRemainReadableWithoutDiscardingTheirProfiles() {
        val imported = importThemeStoreInterfaceSelection(selection(RainLayout.Sidebar, RainStyle.AfterRain)) { _, _, _ ->
            error("Legacy Rain themes have no external package")
        }
        assertNull(imported.archive)
        assertEquals(RainStyle.AfterRain, imported.rain?.profiles?.get(RainLayout.Sidebar)?.scene)
        assertThrows(IllegalArgumentException::class.java) {
            validateThemeStoreInterfaceSelection(selection(RainLayout.Sidebar, RainStyle.AfterRain).put("package", "invalid"))
        }
    }

    @Test
    fun tamperedRainPackageCannotBeSharedEvenWithTheCorrectCatalog() {
        val tampered = packageBytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            verifySharedInterfaceStyleArchive(style.id, tampered, catalog, signature)
        }
    }

    private fun selection(layout: RainLayout, scene: RainStyle): JSONObject = JSONObject()
        .put("mode", "rain").put("variant", scene.value)
        .put("rain", JSONObject().put("layout", layout.value).put("profiles", JSONObject().apply {
            RainLayout.entries.forEach { profile ->
                put(profile.value, RainSettings(layout = profile, scene = scene,
                    cardEdgeStrength = 0.73f, sidebarStrength = 0.42f).toRainJson())
            }
        }))
}
