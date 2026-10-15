package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LkmSusfsStatusTest {
    @Test
    fun parsesActiveGuard() {
        val status = parseLkmSusfsStatus(
            """
            mode=lkm
            kmi=android14-6.1
            supported=1
            loaded=1
            autoload=1
            version=v2.3.0
            logging=1
            hide_mounts=0
            uname_release=6.1.0-test
            uname_version=#1 SMP PREEMPT
            feature=SUSFS_LKM_MODULES
            feature=CONFIG_KSU_SUSFS_SUS_PATH
            path=/data/local/tmp/example
            path=/data/adb/modules/example
            last_error=
            """.trimIndent(),
        )
        assertEquals("android14-6.1", status.kmi)
        assertTrue(status.supported)
        assertTrue(status.loaded)
        assertTrue(status.autoload)
        assertEquals("v2.3.0", status.version)
        assertTrue(status.logging)
        assertFalse(status.hideMounts)
        assertEquals("6.1.0-test", status.unameRelease)
        assertEquals("#1 SMP PREEMPT", status.unameVersion)
        assertEquals(2, status.features.size)
        assertEquals(listOf("/data/local/tmp/example", "/data/adb/modules/example"), status.paths)
    }

    @Test
    fun rejectsGkiAndMalformedFlags() {
        val gki = "mode=gki\nsupported=1\nloaded=1\nautoload=0"
        assertTrue(runCatching { parseLkmSusfsStatus(gki) }.isFailure)
        val malformed = "mode=lkm\nsupported=yes\nloaded=0\nautoload=0"
        assertTrue(runCatching { parseLkmSusfsStatus(malformed) }.isFailure)
        val invalidPolicy = "mode=lkm\nsupported=1\nloaded=1\nautoload=1\nlogging=yes"
        assertTrue(runCatching { parseLkmSusfsStatus(invalidPolicy) }.isFailure)
    }

    @Test
    fun missingGuardRemainsUnavailable() {
        val status = parseLkmSusfsStatus("mode=lkm\nkmi=android17-6.18\nsupported=0\nloaded=0\nautoload=0")
        assertFalse(status.supported)
        assertFalse(status.loaded)
        assertFalse(status.autoload)
    }
}
