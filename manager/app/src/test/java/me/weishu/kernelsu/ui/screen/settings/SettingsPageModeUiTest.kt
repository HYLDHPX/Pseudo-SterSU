package me.weishu.kernelsu.ui.screen.settings

import java.io.File
import me.weishu.kernelsu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contracts supplement policy tests; these do not replace rendered UI tests. */
class SettingsPageModeUiTest {
    @Test
    fun dialogUsesTheExistingLayoutOptionsAndLabels() {
        assertEquals(listOf(SettingsPageMode.Categories, SettingsPageMode.Overview), SettingsPageMode.entries)
        assertEquals(R.string.settings_page_mode_categories, SettingsPageMode.Categories.labelRes)
        assertEquals(R.string.settings_page_mode_overview, SettingsPageMode.Overview.labelRes)
    }

    @Test
    fun oldToolbarButtonAndDropdownAreRemoved() {
        listOf("SettingsSidebarScreen.kt", "SettingsHubScreen.kt", "SettingsOverviewScreen.kt", "SettingsPageMode.kt")
            .forEach { name ->
                assertFalse(name, source(name).contains("SettingsPageModeButton"))
            }
        assertFalse(source("SettingsPageMode.kt").contains("DropdownMenu"))
    }

    @Test
    fun bothLayoutsKeepTheirSelectorAtTheBottom() {
        val home = source("SettingsSidebarScreen.kt")
        val hub = source("SettingsHubScreen.kt")
        val overview = source("SettingsOverviewScreen.kt")
        val shortcuts = home.substringAfter("item(key = \"sidebar_settings_shortcuts\")")
        assertTrue(shortcuts.contains("SettingsShortcut(R.string.settings_page_mode_select"))
        assertTrue(shortcuts.contains("showPageMode = true"))
        assertTrue(hub.indexOf("item(key = \"settings_layout\")") > hub.indexOf("items("))
        assertTrue(hub.contains("SettingsPageModeCard(\n                        currentMode = SettingsPageMode.Categories"))
        assertTrue(overview.indexOf("item(key = \"overview_layout\")") > overview.indexOf("item(key = \"overview_categories\")"))
        assertTrue(overview.contains("SettingsPageModeCard(\n                        currentMode = SettingsPageMode.Overview"))
    }

    @Test
    fun dialogIsScrollableAndClosesBeforeApplyingADifferentLayout() {
        val mode = source("SettingsPageMode.kt")
        assertTrue(mode.contains("AlertDialog("))
        assertTrue(mode.contains("adaptiveDialogContentMaxHeight()"))
        assertTrue(mode.contains(".verticalScroll(rememberScrollState()).selectableGroup()"))
        assertTrue(mode.contains("role = Role.RadioButton"))
        assertTrue(mode.contains("RadioButton(selected = selected, onClick = null)"))
        assertTrue(mode.contains("onDismissRequest()\n                                if (!selected) onModeChange(mode)"))
        assertTrue(mode.contains("TextButton(onClick = onDismissRequest)"))
    }

    @Test
    fun footerKeepsThemeSurfacesAndOriginalPersistence() {
        val mode = source("SettingsPageMode.kt")
        val pager = source("SettingsScreen.kt")
        assertTrue(mode.contains("SettingsHomeCard(modifier = modifier)"))
        assertTrue(mode.contains("rememberSaveable { mutableStateOf(false) }"))
        assertTrue(mode.contains("putString(SETTINGS_PAGE_MODE_KEY, mode.value)"))
        assertTrue(pager.contains("setSettingsPageMode(context, mode)"))
        assertFalse(mode.contains("Color.White"))
        assertFalse(mode.contains(".alpha("))
    }

    private fun source(name: String): String =
        File("src/main/java/me/weishu/kernelsu/ui/screen/settings/$name").readText().replace("\r\n", "\n")
}
