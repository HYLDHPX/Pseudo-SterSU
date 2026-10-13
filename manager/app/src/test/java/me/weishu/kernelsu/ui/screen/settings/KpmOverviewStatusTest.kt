package me.weishu.kernelsu.ui.screen.settings

import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.KpmCaps
import org.junit.Assert.assertEquals
import org.junit.Test

class KpmOverviewStatusTest {
    @Test
    fun disabledPolicyIsNotReportedAsRuntimeUnavailable() {
        val caps = KpmCaps(
            backend = "native-gki",
            managementAvailable = true,
            kernelSupported = true,
            loaderReady = true,
            policyEnabled = false,
            supported = false,
        )

        assertEquals(R.string.kpm_policy_disabled, kpmOverviewStatusRes(caps, loading = false, busy = false))
        assertEquals(
            R.string.kpm_runtime_unavailable,
            kpmOverviewStatusRes(caps.copy(policyEnabled = true), loading = false, busy = false),
        )
    }
}
