package me.weishu.kernelsu.ui.screen.module

import java.io.File
import me.weishu.kernelsu.ui.component.bottombar.MainDestination
import me.weishu.kernelsu.ui.component.bottombar.shouldShowSidebarModuleInstall
import me.weishu.kernelsu.ui.navigation3.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Policy and source contracts; device interaction still needs rendered verification. */
class ModuleSidebarInstallTest {
    @Test
    fun installIsAvailableOnlyOnTheMainModulesPage() {
        assertTrue(shouldShowSidebarModuleInstall(MainDestination.Module, Route.Main))
        assertTrue(shouldShowSidebarModuleInstall(MainDestination.Module, Route.Module))
        listOf(MainDestination.Home, MainDestination.SuperUser, MainDestination.Settings, MainDestination.Kpm)
            .forEach { assertFalse(shouldShowSidebarModuleInstall(it, Route.Main)) }
        listOf(Route.ModuleRepo, Route.SidebarWidgetSettings, Route.Sulog, null)
            .forEach { assertFalse(shouldShowSidebarModuleInstall(MainDestination.Module, it)) }
    }

    @Test
    fun safeModeAndOtherRootManagersStillBlockInstallation() {
        assertTrue(ModuleUiState().installButtonVisible)
        assertFalse(ModuleUiState(isSafeMode = true).installButtonVisible)
        assertFalse(ModuleUiState(magiskInstalled = true).installButtonVisible)
        assertFalse(ModuleUiState(isSafeMode = true, magiskInstalled = true).installButtonVisible)
    }

    @Test
    fun smallWindowsAndOtherStylesKeepThePageButton() {
        val page = source("screen/module/ModuleMaterial.kt")
        assertTrue(page.contains("val showInstallFab = uiState.installButtonVisible && !(sidebar && currentAppWindowSize().canShowSidebar)"))
        assertTrue(page.contains("if (showInstallFab)"))
        assertTrue(page.contains("actions.onOpenFlash(uris)"))
    }

    @Test
    fun sidebarUsesTheExistingPickerAndClearsStaleCallbacks() {
        val page = source("screen/module/ModuleScreen.kt")
        assertTrue(page.contains("if (LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value)"))
        assertTrue(page.contains("rememberModuleZipPicker(actions.onOpenFlash)"))
        assertTrue(page.contains("isCurrentPage && rawUiState.installButtonVisible && currentAppWindowSize().canShowSidebar"))
        assertTrue(page.contains("DisposableEffect(mainPagerState, installAvailable)"))
        assertTrue(page.contains("rememberUpdatedState(pickModuleZip)"))
        assertTrue(page.contains("if (mainPagerState.moduleInstallAction === action) mainPagerState.moduleInstallAction = null"))
        assertTrue(page.contains("ScriptRepository.peekOneshot(uris.first())"))
        val picker = source("screen/module/ModuleZipPicker.kt")
        assertTrue(picker.contains("ActivityResultContracts.OpenMultipleDocuments()"))
        assertTrue(picker.contains("0 -> Unit"))
        assertTrue(picker.contains("confirmDialog.showConfirm"))
    }

    @Test
    fun sidebarActionKeepsSharedStylingAccessibilityAndHeightBudget() {
        val rail = source("component/bottombar/SidebarWidgetRail.kt")
        assertTrue(rail.contains("navigationCount = orderedDestinations.size + if (showModuleInstall) 1 else 0"))
        val button = rail.substringAfter("if (showModuleInstall) {").substringBefore("internal fun shouldShowSidebarModuleInstall")
        assertTrue(button.contains("SidebarRailItem("))
        assertTrue(button.contains("label = stringResource(R.string.module_install)"))
        assertTrue(button.contains("iconOnly = config.iconOnlyNavigation"))
        assertTrue(button.contains("role = Role.Button"))
        assertTrue(button.contains("onClick = { mainPagerState.moduleInstallAction?.invoke() }"))
        assertTrue(rail.contains("!mainPagerState.stealthModeEnabled"))
        assertFalse(button.contains(".alpha("))
    }

    private fun source(path: String): String =
        File("src/main/java/me/weishu/kernelsu/ui/$path").readText().replace("\r\n", "\n")
}
