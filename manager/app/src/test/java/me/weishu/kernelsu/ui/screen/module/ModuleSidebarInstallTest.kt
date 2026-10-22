package me.weishu.kernelsu.ui.screen.module

import java.io.File
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.mutableIntStateOf
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import me.weishu.kernelsu.ui.component.bottombar.MainDestination
import me.weishu.kernelsu.ui.component.bottombar.MainPagerState
import me.weishu.kernelsu.ui.component.bottombar.shouldShowSidebarModuleInstall
import me.weishu.kernelsu.ui.navigation3.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun sidebarPreparesTheExistingPickerWithoutWaitingForPagerSettlement() {
        val page = source("screen/module/ModuleScreen.kt")
        assertTrue(page.contains("if (LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value)"))
        assertTrue(page.contains("rememberModuleZipPicker(actions.onOpenFlash)"))
        val registration = page.substringAfter("val installAvailable =").substringBefore("wallpaperEditorRequest?.let")
        assertTrue(registration.contains("rawUiState.installButtonVisible && currentAppWindowSize().canShowSidebar"))
        assertFalse(registration.contains("isCurrentPage"))
        assertFalse(registration.contains("hasLoaded"))
        assertFalse(registration.contains("isRefreshing"))
        assertFalse(registration.contains("LaunchedEffect"))
        assertTrue(page.contains("DisposableEffect(mainPagerState, installAvailable)"))
        assertTrue(page.contains("rememberUpdatedState(pickModuleZip)"))
        assertTrue(page.contains("if (mainPagerState.moduleInstallAction === action) mainPagerState.moduleInstallAction = null"))
        assertTrue(page.contains("ScriptRepository.peekOneshot(uris.first())"))
        val picker = source("screen/module/ModuleZipPicker.kt")
        assertTrue(picker.contains("ActivityResultContracts.GetMultipleContents()"))
        assertTrue(picker.contains("0 -> pickerOpen = false"))
        assertTrue(picker.contains("confirmDialog.showConfirm"))
    }

    @Test
    fun preparedInstallActionIsAvailableDuringNavigationAndRapidRetargeting() {
        // Hold animation work so assertions run before the pager has settled.
        val pending = ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                pending.addLast(block)
            }
        }
        val scope = CoroutineScope(Job() + dispatcher)
        val pageCount = mutableIntStateOf(4)
        val pager = object : PagerState() {
            override val pageCount: Int get() = pageCount.intValue
        }
        val state = MainPagerState(pager, scope, pageCount)
        var pickerLaunches = 0
        val preparedAction: () -> Unit = { pickerLaunches++ }
        state.moduleInstallAction = preparedAction
        state.updateFeatureAvailability(true)
        try {
            repeat(3) {
                state.animateTo(MainDestination.Module)
                assertTrue(state.isNavigating)
                assertEquals(0, pager.settledPage)
                assertTrue(shouldShowSidebarModuleInstall(state.destinationForPage(), Route.Main))
                assertSame(preparedAction, state.moduleInstallAction)
                state.moduleInstallAction?.invoke()

                state.animateTo(MainDestination.Settings)
                assertFalse(shouldShowSidebarModuleInstall(state.destinationForPage(), Route.Main))
                assertSame(preparedAction, state.moduleInstallAction)
            }
            assertEquals(3, pickerLaunches)
        } finally {
            scope.cancel()
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }

    @Test
    fun moduleInstallationReplacesRebootInTheFixedFooter() {
        val rail = source("component/bottombar/SidebarWidgetRail.kt")
        assertTrue(rail.contains("navigationCount = orderedDestinations.size,"))
        assertFalse(rail.contains("navigationCount = orderedDestinations.size +"))
        val footer = rail.substringAfter("footer = {").substringBefore("windowInsets =")
        assertTrue(footer.contains("if (isModulePage) {"))
        assertTrue(footer.contains("if (showModuleInstall) {"))
        assertTrue(footer.contains("IconButton("))
        assertTrue(footer.contains("contentDescription = stringResource(R.string.module_install)"))
        assertTrue(footer.contains("modifier = Modifier.size(52.dp)"))
        assertTrue(footer.contains("onClick = { mainPagerState.moduleInstallAction?.invoke() }"))
        assertTrue(footer.substringAfter("} else {").contains("SidebarRebootPopup()"))
        val navigation = rail.substringAfter("orderedDestinations.forEach").substringBefore("internal fun shouldShowSidebarModuleInstall")
        assertFalse(navigation.contains("moduleInstallAction"))
        assertTrue(rail.contains("!mainPagerState.stealthModeEnabled"))
        assertFalse(footer.contains(".alpha("))
    }

    @Test
    fun unavailableInstallationDoesNotBringRebootBackOnTheModulePage() {
        val rail = source("component/bottombar/SidebarWidgetRail.kt")
        val modulePageCheck = rail.substringAfter("val isModulePage =").substringBefore("val showModuleInstall =")
        assertTrue(modulePageCheck.contains("shouldShowSidebarModuleInstall("))
        assertFalse(modulePageCheck.contains("moduleInstallAction"))
        val footer = rail.substringAfter("footer = {").substringBefore("windowInsets =")
        assertTrue(footer.indexOf("if (isModulePage)") < footer.indexOf("if (showModuleInstall)"))
        assertFalse(footer.substringBefore("} else {").contains("SidebarRebootPopup()"))
    }

    private fun source(path: String): String =
        File("src/main/java/me/weishu/kernelsu/ui/$path").readText().replace("\r\n", "\n")
}
