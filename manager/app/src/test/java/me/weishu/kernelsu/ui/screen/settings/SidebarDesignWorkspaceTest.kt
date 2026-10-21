package me.weishu.kernelsu.ui.screen.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout decisions and source wiring checks; these do not replace device rendering tests. */
class SidebarDesignWorkspaceTest {
    private val source = "src/main/java/me/weishu/kernelsu/ui/screen/settings/"

    @Test
    fun phonesAndSplitWindowsUseOneEditorAtATime() {
        listOf(320f, 360f, 412f, 600f, 839f).forEach { width ->
            assertFalse(useSidebarDesignTwoPane(width, 900f, 1f))
        }
    }

    @Test
    fun tabletsHaveRoomForNavigationAndEditor() {
        assertTrue(useSidebarDesignTwoPane(840f, 480f, 1f))
        assertTrue(useSidebarDesignTwoPane(1100f, 760f, 1.2f))
    }

    @Test
    fun shortLandscapeStaysSinglePane() {
        assertFalse(useSidebarDesignTwoPane(1100f, 479f, 1f))
        assertFalse(useSidebarDesignTwoPane(1200f, 320f, 1f))
    }

    @Test
    fun largeFontsDoNotSqueezeTheEditor() {
        assertFalse(useSidebarDesignTwoPane(1000f, 900f, 1.3f))
        assertFalse(useSidebarDesignTwoPane(1200f, 900f, 2f))
        assertTrue(useSidebarDesignTwoPane(1260f, 900f, 1.5f))
        assertTrue(useSidebarDesignTwoPane(840f, 480f, 0.85f))
    }

    @Test
    fun allSixSectionsRemainAccessible() {
        assertEquals(listOf("Appearance", "Avatar", "Widget", "Weather", "Navigation", "Home"),
            SidebarDesignPage.entries.map { it.name })
        val workspace = File(source + "SidebarDesignWorkspace.kt").readText()
        val screen = File(source + "SidebarWidgetSettingsScreen.kt").readText()
        SidebarDesignPage.entries.forEach { assertTrue(screen.contains("SidebarDesignPage.${it.name}")) }
        assertTrue(workspace.contains("SidebarDesignPage.entries.forEach"))
        assertTrue(workspace.contains("BackHandler(enabled = !twoPane && selectedPage != null)"))
        assertFalse(screen.contains("SidebarDesignTabs("))
    }

    @Test
    fun sectionStateAndWeatherDraftSurviveSwitchingEditors() {
        val workspace = File(source + "SidebarDesignWorkspace.kt").readText()
        val weather = File(source + "SidebarWeatherOptions.kt").readText()
        assertTrue(workspace.contains("rememberSaveableStateHolder()"))
        assertTrue(workspace.contains("stateHolder.SaveableStateProvider(page.name)"))
        assertTrue(workspace.contains("verticalScroll(rememberScrollState()).imePadding()"))
        assertTrue(weather.contains("stateSaver = Saver("))
        assertTrue(weather.contains("SidebarWeatherConfig.fromJson(it)"))
        assertTrue(weather.contains("showXiaomiDisclosure"))
        assertTrue(weather.contains("showOpenMeteoDisclosure"))
        assertTrue(weather.contains("SidebarWeatherClient().fetch(candidate)"))
    }

    @Test
    fun previewReusesTheRealRailAndNeverRunsAReboot() {
        val workspace = File(source + "SidebarDesignWorkspace.kt").readText()
        listOf("SidebarRailLayout(", "SidebarRailItem(", "NavigationDestinationIcon(",
            "customIcons.labelFor(", "customIcons.stateFor(", "LocalSidebarGlassBackdrop provides null",
            "config.side.isAtStart(layoutDirection)", "tablet = tablet").forEach { assertTrue(it, workspace.contains(it)) }
        assertFalse(workspace.contains("SidebarRebootPopup("))
        assertFalse(workspace.contains("reboot("))
        assertTrue(workspace.contains("val window = me.weishu.kernelsu.ui.util.currentAppWindowSize()"))
        assertTrue(workspace.contains("(window.heightDp - 48f).coerceAtLeast(0f).dp"))
        assertTrue(workspace.contains(".verticalScroll(rememberScrollState()).padding(16.dp)"))
        assertFalse(workspace.contains("coerceIn(200, 540)"))
    }

    @Test
    fun editorChoicesKeepAllExistingConfigurationKinds() {
        val screen = File(source + "SidebarWidgetSettingsScreen.kt").readText()
        val material = File(source + "SidebarMaterialOptions.kt").readText()
        val cards = File(source + "SidebarHomeCardOptions.kt").readText()
        listOf("SidebarClockStyle.entries", "SidebarImageShape.entries", "SidebarWidgetType.entries",
            "SidebarNavigationPosition.entries", "SidebarSide.entries", "navigationOrder.swap(",
            "onEditNavigationIcons").forEach { assertTrue(it, screen.contains(it)) }
        assertFalse(screen.contains("FilterChip("))
        assertFalse(material.contains("FilterChip("))
        assertFalse(cards.contains("FilterChip("))
        assertTrue(material.contains("if (confirmReset) AlertDialog("))
        assertTrue(material.contains("parseSidebarRgb(hex)?.let(::applyColor)"))
        assertTrue(material.contains("var pendingColor by rememberSaveable(material, editingDark, role, savedColor)"))
        assertTrue(material.contains("enabled = parseSidebarRgb(hex)?.let { it != savedColor } == true"))
        assertTrue(cards.contains("SettingsWallpaperCropDialog("))
        assertTrue(cards.contains("pendingImageDark"))
        assertTrue(cards.contains("releaseUnused("))
    }
}
