package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SusfsImplementationTest {
    private fun capabilities(
        variant: String = "GKI",
        features: String = "CONFIG_KSU_SUSFS_SUS_PATH",
        version: String = "v2.1.0",
        toolAvailable: Boolean = true,
    ) = buildSusfsCapabilities(toolAvailable, version, features, true, variant)

    @Test
    fun reportsBuiltinVersionAndVariant() {
        val result = capabilities(variant = " GKI ")
        assertEquals("v2.1.0", result.version)
        assertEquals("GKI", result.variant)
        assertEquals(SusfsImplementation.Builtin, result.implementation)
        assertTrue(result.supportsAddSusPath)
    }

    @Test
    fun lkmVariantCannotEnableGkiOperations() {
        for (variant in listOf("GKI-LKM", " gki_lkm\n", "NON-GKI-LKM")) {
            val result = capabilities(variant = variant)
            assertEquals(SusfsImplementation.Lkm, result.implementation)
            assertFalse(result.supportsAddSusPath)
            assertFalse(result.supportsPathLoop)
            assertFalse(result.supportsKstat)
            assertFalse(result.supportsUnameSpoof)
            assertFalse(result.supportsAvcLogSpoofing)
        }
    }

    @Test
    fun exactAbiFeatureDetectsLkmEvenWithoutVariant() {
        val result = capabilities(
            variant = "",
            features = "CONFIG_KSU_SUSFS_SUS_PATH\n SUSFS_LKM_MODULES \n",
        )
        assertEquals(SusfsImplementation.Lkm, result.implementation)
        val config = parseSusfsConfigOutput(listOf("__TOOL__=/system/bin/ksu_susfs"), result)
        assertFalse(config.available)
        assertEquals("lkm_implementation_detected", config.error)
    }

    @Test
    fun explicitLkmMarkerStillBlocksGkiWhenVersionQueryFails() {
        val result = capabilities(variant = "GKI-LKM", version = "")
        assertEquals(SusfsImplementation.Lkm, result.implementation)
        assertFalse(result.supportsAddSusPath)
    }

    @Test
    fun similarMarkerNamesDoNotMisclassifyBuiltinKernel() {
        assertEquals(
            SusfsImplementation.Builtin,
            capabilities(variant = "GKI-LKM-extra", features = "NOT_SUSFS_LKM_MODULES").implementation,
        )
    }

    @Test
    fun missingToolCannotReportSupportedOperations() {
        val result = capabilities(toolAvailable = false)
        assertEquals(SusfsImplementation.Unknown, result.implementation)
        assertFalse(result.supportsAddSusPath)
        assertFalse(result.supportsPathLoop)
        assertFalse(result.supportsUnameSpoof)
        assertFalse(result.supportsAvcLogSpoofing)
    }

    @Test
    fun legacyToolWithoutVersionKeepsCompatibilityWithoutClaimingBuiltin() {
        val result = buildSusfsCapabilities(true, "", "", false)
        assertEquals(SusfsImplementation.Unknown, result.implementation)
        assertTrue(result.supportsAddSusPath)
    }
}
