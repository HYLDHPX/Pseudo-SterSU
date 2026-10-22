package me.weishu.kernelsu.ui.util

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreInterfaceStyleTest {
    private val assets = File("src/main/assets/interface-style")
    private val catalog = File(assets, "catalog-v2.json").readBytes()
    private val signature = File(assets, "catalog-v2.sig").readBytes()
    private val skroot = File("../../interface-styles/packages/skrootpro.ksstyle").readBytes()

    @Test
    fun signedInterfaceArchiveCanBeSharedAndVerifiedOffline() {
        val shared = verifySharedInterfaceStyleArchive("skrootpro", skroot, catalog, signature)
        assertEquals("skrootpro", shared.style.engine)
        assertTrue(shared.bundle.resources.containsKey("theme.json"))
        assertArrayEquals(skroot, shared.bytes)
        val imported = importThemeStoreInterfaceSelection(selection("skrootpro", "skrootpro")) { _, key, _ ->
            when (key) {
                "asset" -> skroot
                "catalogAsset" -> catalog
                "signatureAsset" -> signature
                else -> error("Unexpected resource")
            }
        }
        assertEquals("skrootpro", imported.mode)
        assertEquals("skrootpro", imported.archive?.style?.id)
    }

    @Test
    fun apkPinnedArchivesKeepTheirHashChecksWithoutACatalogProof() {
        val shared = verifySharedInterfaceStyleArchive("skrootpro", skroot, null, null)
        assertEquals("skrootpro", shared.style.id)
        val tampered = skroot.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            verifySharedInterfaceStyleArchive("skrootpro", tampered, null, null)
        }
    }

    @Test
    fun tamperedSignedCatalogAndIncompleteProofAreRejected() {
        val tamperedCatalog = catalog.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            verifySharedInterfaceStyleArchive("skrootpro", skroot, tamperedCatalog, signature)
        }
        assertThrows(IllegalArgumentException::class.java) {
            verifySharedInterfaceStyleArchive("skrootpro", skroot, catalog, null)
        }
    }

    @Test
    fun allBuiltInStylesRestoreWithoutDownloadingAnything() {
        listOf("miuix", "material", "sidebar_widget").forEach { mode ->
            val restored = importThemeStoreInterfaceSelection(JSONObject().put("mode", mode)) { _, _, _ ->
                error("Built-in styles must not access external resources")
            }
            assertEquals(mode, restored.mode)
            assertEquals(null, restored.archive)
        }
    }

    @Test
    fun rendererMismatchAndUnsupportedSelectionCannotBeImported() {
        assertThrows(IllegalArgumentException::class.java) {
            importThemeStoreInterfaceSelection(selection("alpha", "skrootpro")) { _, key, _ ->
                when (key) { "asset" -> skroot; "catalogAsset" -> catalog; else -> signature }
            }
        }
        listOf(
            JSONObject().put("mode", "unknown"),
            JSONObject().put("mode", "pixel").put("variant", "unknown"),
            JSONObject().put("mode", "alpha"),
            JSONObject().put("mode", "miuix").put("package", JSONObject()),
            JSONObject().put("mode", "miuix").put("secret", "private"),
        ).forEach { invalid ->
            assertThrows(Exception::class.java) { validateThemeStoreInterfaceSelection(invalid) }
        }
    }

    private fun selection(mode: String, id: String): JSONObject = JSONObject().put("mode", mode)
        .put("package", JSONObject().put("id", id)
            .put("asset", JSONObject().put("path", "assets/interface_style.ksstyle"))
            .put("catalogAsset", JSONObject().put("path", "assets/interface_catalog.json"))
            .put("signatureAsset", JSONObject().put("path", "assets/interface_catalog.sig")))
}
