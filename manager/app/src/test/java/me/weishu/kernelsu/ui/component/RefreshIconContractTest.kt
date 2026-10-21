package me.weishu.kernelsu.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source wiring checks; runtime rendering still requires a device. */
class RefreshIconContractTest {
    private val root = File("src/main/java/me/weishu/kernelsu/ui")

    private fun source(path: String) = File(root, path).readText()

    @Test
    fun refreshAndRetryButtonsUseTheUpstreamOutlinedVector() {
        val expectedCounts = mapOf(
            "component/ApkeSecondaryScaffold.kt" to 1,
            "component/bottombar/SidebarWeatherWidget.kt" to 1,
            "screen/superuser/SuperUserSkrootpro.kt" to 1,
            "screen/superuser/AppFreezeScreen.kt" to 1,
            "screen/superuser/AppIdManagerScreen.kt" to 1,
            "screen/pluginstore/PluginStoreScreen.kt" to 2,
            "screen/themestore/ThemeStoreScreen.kt" to 1,
            "screen/themestore/CloudThemeRankingScreen.kt" to 2,
            "screen/themestore/CloudThemeDiscoverScreen.kt" to 2,
            "screen/themestore/CloudThemeDetailScreen.kt" to 1,
            "screen/themestore/CloudThemeCreatorScreen.kt" to 4,
            "screen/settings/AiChatScreen.kt" to 2,
            "screen/settings/AiModuleStudioScreen.kt" to 2,
            "screen/settings/CpuSpoofScreen.kt" to 1,
            "screen/settings/DeviceIdentityScreen.kt" to 2,
            "screen/settings/DynamicManagerScreen.kt" to 1,
            "screen/settings/ForegroundToolProtectionScreen.kt" to 1,
            "screen/settings/GraphicsRendererScreen.kt" to 1,
            "screen/settings/KpmScreen.kt" to 3,
            "screen/settings/LkmSusfsScreen.kt" to 1,
            "screen/settings/RescueProtectionScreen.kt" to 2,
            "screen/settings/SusfsApplicationsScreen.kt" to 1,
            "screen/settings/SusfsKernelSlotPicker.kt" to 1,
            "screen/settings/SusfsPathConfigScreen.kt" to 1,
        )
        expectedCounts.forEach { (path, expected) ->
            val page = source(path)
            assertTrue(path, page.contains("import androidx.compose.material.icons.outlined.Refresh"))
            assertEquals(path, expected, Regex("Icons\\.Outlined\\.Refresh").findAll(page).count())
        }
    }

    @Test
    fun materialPullRefreshRetainsTheUpstreamLoadingIndicator() {
        listOf(
            "superuser/SuperUserMaterial.kt",
            "module/ModuleMaterial.kt",
            "modulerepo/ModuleRepoMaterial.kt",
            "template/TemplateMaterial.kt",
            "sulog/SulogMaterial.kt",
        ).forEach { path ->
            val page = source("screen/$path")
            assertTrue(path, page.contains("PullToRefreshBox("))
            assertTrue(path, page.contains("PullToRefreshDefaults.LoadingIndicator("))
            assertTrue(path, page.contains("state = pullToRefreshState"))
            assertTrue(path, page.contains("Modifier.align(Alignment.TopCenter)"))
        }
    }

    @Test
    fun miuixPullRefreshRetainsTheUpstreamControlAndLocalizedStates() {
        listOf(
            "superuser/SuperUserMiuix.kt",
            "module/ModuleMiuix.kt",
            "modulerepo/ModuleRepoMiuix.kt",
            "template/TemplateMiuix.kt",
            "sulog/SulogMiuix.kt",
        ).forEach { path ->
            val page = source("screen/$path")
            assertTrue(path, page.contains("import top.yukonga.miuix.kmp.basic.PullToRefresh"))
            assertTrue(path, page.contains("PullToRefresh("))
            assertTrue(path, page.contains("pullToRefreshState = pullToRefreshState"))
            listOf("refresh_pulling", "refresh_release", "refresh_refresh", "refresh_complete").forEach { state ->
                assertTrue("$path: $state", page.contains("R.string.$state"))
            }
        }
    }

    @Test
    fun restartRestoreClearAndStatusIconsAreNotReplaced() {
        assertTrue(source("component/rebootlistpopup/SidebarRebootPopup.kt").contains("Icons.Rounded.Refresh"))
        assertTrue(source("screen/flash/FlashMiuix.kt").contains("Icons.Rounded.Refresh"))
        assertTrue(source("screen/flash/FlashMaterial.kt").contains("Icons.Filled.Refresh"))
        assertTrue(source("screen/module/ModuleMaterial.kt").contains(
            "if (module.remove) Icons.Outlined.Refresh else Icons.Outlined.Delete"
        ))
        assertEquals(1, Regex("Icons\\.Rounded\\.Refresh").findAll(source("screen/settings/AiChatScreen.kt")).count())
        assertEquals(1, Regex("Icons\\.Rounded\\.Refresh").findAll(source("screen/settings/SusfsPathConfigScreen.kt")).count())
        assertTrue(source("screen/themestore/CloudThemeCreatorScreen.kt").contains(
            "CloudThemeCreatorApplicationStatus.RegistryPending -> Icons.Rounded.Refresh"
        ))
    }
}
