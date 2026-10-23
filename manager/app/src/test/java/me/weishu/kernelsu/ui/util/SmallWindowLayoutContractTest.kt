package me.weishu.kernelsu.ui.util

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring checks supplement size-policy tests, not real-device window and IME tests. */
class SmallWindowLayoutContractTest {
    private fun source(path: String): String =
        File("src/main/java/me/weishu/kernelsu/ui/$path").readText()

    @Test
    fun mainWindowSupportsResizeAndKeepsPageCompositionWhenNavigationMoves() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val activity = source("MainActivity.kt")
        assertTrue(manifest.contains("android:resizeableActivity=\"true\""))
        assertTrue(manifest.contains("orientation|screenSize|screenLayout|smallestScreenSize"))
        assertTrue(activity.contains("movableContentOf<Dp>"))
        assertTrue(activity.contains("currentPagerContent.value(bottomInnerPadding)"))
        assertTrue(activity.contains(".windowInsetsPadding(WindowInsets.captionBar)"))
        assertTrue(activity.contains(".imePadding()"))
    }

    @Test
    fun compactNavigationKeepsDestinationIdentityAndCustomOrder() {
        val navigation = source("component/bottombar/CompactBottomBar.kt")
        assertTrue(navigation.contains("orderSidebarDestinations("))
        assertTrue(navigation.contains("pager.destinationForPage() == destination"))
        assertTrue(navigation.contains("pager.animateTo(destination)"))
        assertTrue(navigation.contains("coerceAtLeast(48.dp)"))
        assertTrue(navigation.contains(".horizontalScroll(rememberScrollState())"))
        assertTrue(navigation.contains("uiPassthroughNavigationColor("))
        assertFalse(navigation.contains("pager.animateToPage(index)"))
    }

    @Test
    fun selectedSidebarStyleTakesPriorityOverCompactBottomNavigation() {
        val bottomBar = source("component/bottombar/BottomBar.kt")
            .substringAfter("fun BottomBar(").substringBefore("fun SideRail(")
        val sidebar = bottomBar.indexOf("InterfaceStyle.SidebarWidget.value && window.canShowSidebar")
        val compact = bottomBar.indexOf("if (window.compactNavigation)")
        assertTrue(sidebar >= 0)
        assertTrue(compact > sidebar)
        assertTrue(bottomBar.substring(sidebar, compact).contains("SidebarWidgetRail("))
        assertTrue(bottomBar.substring(sidebar, compact).contains("return"))
    }

    @Test
    fun sidebarHostAndModuleInstallUseTheSameAvailabilityPolicy() {
        assertTrue(source("MainActivity.kt").contains("currentAppWindowSize().canShowSidebar && shouldHostSidebarRail("))
        assertTrue(source("component/bottombar/BottomBar.kt").contains("return currentAppWindowSize().canShowSidebar"))
        assertTrue(source("screen/module/ModuleScreen.kt").contains("rawUiState.installButtonVisible && currentAppWindowSize().canShowSidebar"))
        assertTrue(source("screen/module/ModuleMaterial.kt").contains("!(sidebar && currentAppWindowSize().canShowSidebar)"))
    }

    @Test
    fun collapsingTheSidebarDoesNotRemoveTheRebootMenu() {
        val home = source("screen/home/HomeMaterial.kt")
        assertTrue(home.contains("showReboot && (!sidebarStyle || !me.weishu.kernelsu.ui.util.currentAppWindowSize().canShowSidebar)"))
        assertTrue(home.contains("rebootlistpopup.SidebarRebootPopup()"))
    }

    @Test
    fun embeddedWebUiAndLogSearchDoNotAddTheKeyboardSpaceTwice() {
        val webUi = source("webui/WebUIScreen.kt")
        val logs = source("screen/sulog/SulogMiuix.kt")
        assertTrue(webUi.contains(".windowInsetsPadding(contentInsets)"))
        assertFalse(webUi.contains("imeInsets.asPaddingValues()"))
        assertTrue(logs.contains(".imePadding()"))
        assertFalse(logs.contains("Spacer(Modifier.height(imeBottomPadding))"))
    }

    @Test
    fun primaryMiuixHeadersUseTheWindowAwareAdapter() {
        listOf(
            "superuser/SuperUserMiuix.kt", "module/ModuleMiuix.kt", "settings/SettingsMiuix.kt",
            "install/InstallMiuix.kt", "launchericon/LauncherIconScreen.kt",
            "templateeditor/TemplateEditorMiuix.kt", "modulerepo/ModuleRepoMiuix.kt",
        ).forEach { path ->
            assertTrue(path, source("screen/$path").contains("ui.component.AdaptiveTopAppBar as TopAppBar"))
        }
    }

    @Test
    fun mediaDialogsBoundAndScrollTheirContent() {
        listOf(
            "SettingsWallpaperCropDialog.kt", "SettingsWallpaperPreviewDialog.kt",
            "SettingsVideoBackgroundPreviewDialog.kt", "SettingsNavigationIconPreviewDialog.kt",
            "HomeCardWallpaperScreen.kt", "InstallCardWallpaperScreen.kt",
        ).forEach { path ->
            val dialog = source("screen/settings/$path")
            assertTrue(path, dialog.contains("adaptiveDialogContentMaxHeight()"))
            assertTrue(path, dialog.contains(".verticalScroll(rememberScrollState())"))
        }
    }
}
