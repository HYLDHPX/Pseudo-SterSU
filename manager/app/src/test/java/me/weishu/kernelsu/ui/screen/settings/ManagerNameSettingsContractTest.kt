package me.weishu.kernelsu.ui.screen.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerNameSettingsContractTest {
    private fun source(path: String): String =
        File("src/main/java/me/weishu/kernelsu/$path").readText()

    @Test
    fun dialogStagesAllSyncOptionsAndSavesThemWithTheName() {
        val dialog = source("ui/screen/settings/ManagerNameDialog.kt")
        assertTrue(dialog.contains("rememberSaveable(initialSyncLkmCard)"))
        assertTrue(dialog.contains("rememberSaveable(initialSyncSettingsHeader)"))
        assertTrue(dialog.contains("rememberSaveable(initialSyncHomeHeader)"))
        assertTrue(dialog.contains("onConfirm = { onConfirm(it, syncLkmCard, syncSettingsHeader, syncHomeHeader) }"))
        assertTrue(dialog.contains("onConfirm(name.trim())"))
        assertTrue(dialog.contains("TextButton(onClick = onDismissRequest)"))
        assertFalse(dialog.contains("SettingsRepository"))

        val launcher = source("ui/screen/launchericon/LauncherIconScreen.kt")
        assertTrue(launcher.contains("initialSyncLkmCard = uiState.syncManagerNameToLkmCard"))
        assertTrue(launcher.contains("initialSyncSettingsHeader = uiState.syncManagerNameToSettingsHeader"))
        assertTrue(launcher.contains("initialSyncHomeHeader = uiState.syncManagerNameToHomeHeader"))
        assertTrue(launcher.contains("onConfirm = viewModel::setCustomManagerName"))
    }

    @Test
    fun preferencesSaveAtomicallyAndPreserveExistingNameKey() {
        val repository = source("data/repository/SettingsRepositoryImpl.kt")
        assertTrue(repository.contains("CUSTOM_MANAGER_NAME_KEY = \"custom_manager_name\""))
        assertTrue(repository.contains("prefs.getBoolean(SYNC_MANAGER_NAME_LKM_CARD_KEY, false)"))
        assertTrue(repository.contains("prefs.getBoolean(SYNC_MANAGER_NAME_SETTINGS_HEADER_KEY, false)"))
        assertTrue(repository.contains("prefs.getBoolean(SYNC_MANAGER_NAME_HOME_HEADER_KEY, false)"))
        val save = repository.substringAfter("override fun setManagerNameSettings(")
            .substringBefore("override var customWallpaperUri")
        assertEquals(1, Regex("prefs\\.edit").findAll(save).count())
        assertTrue(save.contains("sanitizeCustomManagerName(name)"))
        assertTrue(save.contains("remove(CUSTOM_MANAGER_NAME_KEY)"))
        assertTrue(save.contains("putString(CUSTOM_MANAGER_NAME_KEY, value)"))
        assertTrue(save.contains("putBoolean(SYNC_MANAGER_NAME_LKM_CARD_KEY, syncLkmCard)"))
        assertTrue(save.contains("putBoolean(SYNC_MANAGER_NAME_SETTINGS_HEADER_KEY, syncSettingsHeader)"))
        assertTrue(save.contains("putBoolean(SYNC_MANAGER_NAME_HOME_HEADER_KEY, syncHomeHeader)"))
        assertTrue(save.contains("remove(\"custom_home_title\")"))
        assertFalse(repository.contains("override var customHomeTitle"))
    }

    @Test
    fun retainedSettingsAndHomeViewModelsObserveSavedNames() {
        val settings = source("ui/viewmodel/SettingsViewModel.kt")
        val home = source("ui/viewmodel/HomeViewModel.kt")
        listOf(settings, home).forEach { viewModel ->
            assertTrue(viewModel.contains("key == CUSTOM_MANAGER_NAME_KEY"))
            assertTrue(viewModel.contains("key == SYNC_MANAGER_NAME_LKM_CARD_KEY"))
            assertTrue(viewModel.contains("key == SYNC_MANAGER_NAME_HOME_HEADER_KEY"))
            assertTrue(viewModel.contains("registerOnSharedPreferenceChangeListener("))
            assertTrue(viewModel.contains("unregisterOnSharedPreferenceChangeListener("))
            assertTrue(viewModel.contains("customManagerName = repo.customManagerName"))
            assertTrue(viewModel.contains("syncManagerNameToLkmCard = repo.syncManagerNameToLkmCard"))
        }
        assertTrue(settings.contains("key == SYNC_MANAGER_NAME_SETTINGS_HEADER_KEY"))
        assertTrue(settings.contains("syncManagerNameToHomeHeader = repo.syncManagerNameToHomeHeader"))
        assertTrue(home.contains("homeHeaderName = repo.homeHeaderName"))
        assertTrue(home.contains("customManagerName = runCatching { repo.customManagerName }"))
        assertTrue(home.contains("syncManagerNameToLkmCard = runCatching { repo.syncManagerNameToLkmCard }"))
    }

    @Test
    fun homeHeadersAndStatusCardsUseTheirOwnNameSyncOptions() {
        listOf("HomeMiuix.kt", "HomeMaterial.kt", "HomeSidebarStatus.kt", "HomeAlpha.kt", "HomeDelta.kt", "HomeSkrootpro.kt")
            .forEach { file ->
                assertTrue(file, source("ui/screen/home/$file").contains("state.lkmStatusLabel("))
                assertTrue(file, source("ui/screen/home/$file").contains("state.homeHeaderName.ifBlank"))
            }
        val miuix = source("ui/screen/home/HomeMiuix.kt")
        assertTrue(miuix.contains("customTitle.ifBlank { state.lkmStatusLabel(state.rootRuntimeState.labelRes) }"))
        val repository = source("data/repository/SettingsRepositoryImpl.kt")
        assertTrue(repository.contains("get() = if (syncManagerNameToHomeHeader) customManagerName else \"\""))
        val header = source("ui/screen/settings/SettingsSidebarScreen.kt")
        assertTrue(header.contains("customName = uiState.customManagerName"))
        assertTrue(header.contains("syncEnabled = uiState.syncManagerNameToSettingsHeader"))
    }

    @Test
    fun dialogKeepsScrollableOpaqueControlsAndAccessibleSwitchRows() {
        val dialog = source("ui/screen/settings/ManagerNameDialog.kt")
        assertTrue(dialog.contains("adaptiveDialogContentMaxHeight()"))
        assertTrue(dialog.contains("verticalScroll(rememberScrollState())"))
        assertTrue(dialog.contains("UiPassthroughTheme(UiPassthroughSettings())"))
        assertTrue(dialog.contains("MaterialTheme.colorScheme.surface.copy(alpha = 1f)"))
        assertTrue(dialog.contains("role = Role.Switch"))
        assertTrue(dialog.contains("ExpressiveSwitch(checked = checked, onCheckedChange = null)"))
    }
}
