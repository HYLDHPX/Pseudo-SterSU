package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCompatibilityTest {
    @Test
    fun resolveCompatiblePluginIdsExcludesMissingAndOldKsud() {
        val compatible = InstalledManagerPlugin(plugin(ManagerPlugin.RescueProtection.id, 10, 20), 1L)
        val oldKsud = InstalledManagerPlugin(plugin(ManagerPlugin.AppFreeze.id, 10, 21), 1L)
        val oldManager = InstalledManagerPlugin(plugin(ManagerPlugin.AppIdManager.id, 11, 20), 1L)

        val ids = resolveCompatiblePluginIds(
            installed = listOf(compatible, oldKsud, oldManager),
            managerVersionCode = 10,
            ksudStatus = InstalledKsudStatus(present = true, versionCode = 20),
        )

        assertEquals(setOf(ManagerPlugin.RescueProtection.id), ids)
        assertTrue(checkManagerPluginCompatibility(
            compatible.plugin,
            managerVersionCode = 10,
            ksudStatus = InstalledKsudStatus(present = true, versionCode = 20),
        ).isCompatible)
        assertFalse(checkManagerPluginCompatibility(
            oldKsud.plugin,
            managerVersionCode = 10,
            ksudStatus = InstalledKsudStatus(present = true, versionCode = 20),
        ).isCompatible)
    }

    private fun plugin(id: String, minManager: Int, minKsud: Int) = ManagerPluginPackage(
        id = id,
        version = 1,
        name = id,
        summary = "summary",
        description = "description",
        instructions = listOf("instruction"),
        slots = emptySet(),
        minManagerVersionCode = minManager,
        minKsudVersionCode = minKsud,
        downloadUrl = "https://raw.githubusercontent.com/fixz232/plugin/$id.ksplugin",
        sha256 = "a".repeat(64),
        sizeBytes = 128L,
    )
}
