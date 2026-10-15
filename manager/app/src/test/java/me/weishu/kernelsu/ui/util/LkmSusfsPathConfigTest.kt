package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LkmSusfsPathConfigTest {
    @Test
    fun roundTripKeepsIndependentPaths() {
        val paths = listOf("/data/adb/modules/example", "/data/local/tmp/test")
        assertEquals(paths, decodeLkmSusfsPathConfig(encodeLkmSusfsPathConfig(paths)))
    }

    @Test
    fun rejectsOtherSchemasAndInvalidPaths() {
        val examples = listOf(
            """{"kind":"gki","schemaVersion":1,"paths":["/data/test"]}""",
            """{"kind":"stersu.lkm-susfs-paths","schemaVersion":2,"paths":["/data/test"]}""",
            """{"kind":"stersu.lkm-susfs-paths","schemaVersion":1,"paths":["../data/test"]}""",
            """{"kind":"stersu.lkm-susfs-paths","schemaVersion":1,"paths":["/data/../test"]}""",
            """{"kind":"stersu.lkm-susfs-paths","schemaVersion":1,"paths":["/data/test\n"]}""",
        )
        examples.forEach { assertTrue(runCatching { decodeLkmSusfsPathConfig(it) }.isFailure) }
    }

    @Test
    fun importOnlyAddsNewPathsWithoutDeletingCurrentRules() {
        val imported = decodeLkmSusfsPathConfig(
            """{"kind":"stersu.lkm-susfs-paths","schemaVersion":1,"paths":["/new/", "/old", "/new"]}""",
        )
        assertEquals(listOf("/new"), planLkmSusfsPathImport(imported, listOf("/old")))
    }

    @Test
    fun importRejectsCapacityOverflow() {
        val current = (0 until LKM_SUSFS_PATH_CONFIG_MAX_PATHS).map { "/old/$it" }
        assertTrue(runCatching { planLkmSusfsPathImport(listOf("/new"), current) }.isFailure)
    }
}
