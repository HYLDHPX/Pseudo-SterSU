package me.weishu.kernelsu.ui.util

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class CustomLauncherIconTest {
    private val screen get() = source("ui/screen/launchericon/LauncherIconScreen.kt")
    private val shortcut get() = source("ui/util/CustomLauncherIcon.kt")
    private fun source(path: String) = File("src/main/java/me/weishu/kernelsu/$path").readText()

    @Test
    fun existingAndUnknownPreferencesKeepTheInstalledEntryAsDefault() {
        listOf(null, "", "unknown", "INSTALLED").forEach {
            assertEquals(CustomLauncherMode.Installed, CustomLauncherMode.fromValue(it))
        }
    }

    @Test
    fun bothModesRoundTripUsingStablePreferenceValues() {
        assertEquals(2, CustomLauncherMode.entries.size)
        assertEquals("custom_launcher_mode", CustomLauncherMode.PREF_KEY)
        CustomLauncherMode.entries.forEach { assertEquals(it, CustomLauncherMode.fromValue(it.value)) }
    }

    @Test
    fun selectingAModeOnlySavesThePreferenceAndDoesNotChangeDesktopEntries() {
        val selection = screen.substringAfter("onCustomLauncherModeChange = { mode ->")
            .substringBefore("preparing = preparing,")
        assertTrue(selection.contains("putString(CustomLauncherMode.PREF_KEY, mode.value).commit()"))
        assertTrue(selection.contains("customLauncherMode = mode"))
        assertFalse(selection.contains("InstalledLauncher."))
        assertFalse(selection.contains("requestManagerIconShortcut("))
        assertFalse(selection.contains("setComponentEnabledSetting"))
    }

    @Test
    fun thePickedImageKeepsItsModeAcrossGalleryAndCropRecreation() {
        assertTrue(screen.contains("customIconMode by rememberSaveable"))
        assertTrue(screen.contains("customIconUri by rememberSaveable"))
        assertTrue(screen.contains("showCustomIconCrop by rememberSaveable"))
        assertTrue(screen.contains("selectingCustomIcon by rememberSaveable"))
        assertTrue(screen.indexOf("customIconMode = customLauncherMode.value") <
            screen.indexOf("customIconPicker.launch("))
        assertTrue(screen.contains("val creationMode = CustomLauncherMode.fromValue(customIconMode)"))
        assertTrue(screen.contains("customIconBusy = preparing || selectingCustomIcon || showCustomIconCrop || pendingInstallPath != null"))
        assertTrue(screen.contains("enabled = !customIconBusy"))
    }

    @Test
    fun shortcutCreationNeverRunsTheApkInstallerOrAutoHideFlow() {
        val branch = screen.substringAfter("if (creationMode == CustomLauncherMode.Shortcut) {")
            .substringBefore("} else {")
        assertTrue(branch.contains("requestManagerIconShortcut(context, bitmap, managerName)"))
        assertTrue(branch.contains("ManagerIconShortcutResult.Requested -> R.string.launcher_shortcut_requested"))
        assertTrue(branch.contains("ManagerIconShortcutResult.Unsupported -> R.string.module_shortcut_not_supported"))
        assertFalse(branch.contains("InstalledLauncher."))
        assertFalse(branch.contains("installLauncher.launch"))
        assertFalse(branch.contains("installPermissionLauncher.launch"))
        assertTrue(screen.contains("InstalledLauncher.create(context, bitmap, managerName)"))
    }

    @Test
    fun shortcutsReuseTheOldIdAndAlwaysOpenThisManagerExplicitly() {
        assertTrue(shortcut.contains("MANAGER_ICON_SHORTCUT_ID = \"manager_custom_icon\""))
        assertTrue(shortcut.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(shortcut.contains("Icon.createWithAdaptiveBitmap(bitmap)"))
        assertFalse(shortcut.contains("getRootShell"))
        assertFalse(shortcut.contains("pushDynamicShortcut"))
        assertFalse(shortcut.contains("InstalledLauncher.create"))
        assertFalse(shortcut.contains("verifyAndHide"))
        assertFalse(shortcut.contains("StealthMode"))
    }

    @Test
    fun unsupportedLaunchersDoNotRestoreIconsAndPinnedUpdatesDoNotRequestDuplicates() {
        val creation = shortcut.substringAfter("internal fun requestManagerIconShortcut(")
            .substringBefore("internal fun synchronizeManagerIconShortcut(")
        assertTrue(creation.indexOf("!manager.isRequestPinShortcutSupported") <
            creation.indexOf("InstalledLauncher.restore(context)"))
        val update = creation.substringAfter("return if (existing != null) {").substringBefore("} else if")
        assertTrue(update.contains("manager.enableShortcuts"))
        assertTrue(update.contains("manager.updateShortcuts(listOf(shortcut))"))
        assertFalse(update.contains("requestPinShortcut"))
        assertTrue(creation.contains("manager.requestPinShortcut(shortcut, null)"))
        assertTrue(creation.contains("ManagerIconShortcutResult.Requested"))
    }

    @Test
    fun switchingBuiltinIconsReassociatesThePinnedShortcutBeforeDisablingOldAliases() {
        val application = source("ui/util/LauncherIconOption.kt")
        assertTrue(application.contains("listOf(option) + LauncherIconOption.entries.filter { it != option }"))
        assertTrue(application.contains("if (candidate == option) synchronizeManagerIconShortcut(context, option)"))
        val synchronization = shortcut.substringAfter("internal fun synchronizeManagerIconShortcut(")
        assertTrue(synchronization.contains("manager.enableShortcuts(listOf(MANAGER_ICON_SHORTCUT_ID))"))
        assertTrue(synchronization.contains(".setActivity(ComponentName(context.packageName, option.aliasClassName))"))
        assertTrue(synchronization.contains("manager.updateShortcuts(listOf(shortcut))"))
        assertFalse(synchronization.contains("requestPinShortcut"))
    }

    @Test
    fun bothModesHaveLocalizedInstructionsAndTruthfulRequestFeedback() {
        val names = listOf("launcher_custom_mode", "launcher_mode_installed", "launcher_mode_installed_summary",
            "launcher_mode_shortcut", "launcher_mode_shortcut_summary", "launcher_shortcut_summary",
            "launcher_shortcut_preparing", "launcher_shortcut_requested", "launcher_shortcut_failed", "launcher_shortcut_help")
        listOf("values", "values-zh-rCN").forEach { directory ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$directory/strings.xml"))
            val strings = document.getElementsByTagName("string")
            val resources = (0 until strings.length).associate {
                strings.item(it).attributes.getNamedItem("name").nodeValue to strings.item(it).textContent
            }
            names.forEach { assertFalse("Missing $directory/$it", resources[it].isNullOrBlank()) }
            assertFalse(resources.getValue("launcher_shortcut_requested").contains("已创建"))
        }
        assertTrue(screen.contains("R.string.launcher_shortcut_help"))
        assertTrue(screen.contains("R.string.launcher_install_help"))
    }
}
