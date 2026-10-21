package me.weishu.kernelsu.ui.screen.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source wiring checks supplement model tests; these do not replace device UI tests. */
class AppearanceEditorContractTest {
    private val root = "src/main/java/me/weishu/kernelsu/ui/screen/"
    private val shell = File(root + "settings/AppearanceEditorComponents.kt").readText()
    private val home = File(root + "settings/HomeCardWallpaperScreen.kt").readText()
    private val install = File(root + "settings/InstallCardWallpaperScreen.kt").readText()
    private val navigation = File(root + "navigationicon/NavigationIconScreen.kt").readText()
    private val controls = File(root + "navigationicon/NavigationIconAdjustmentControls.kt").readText()

    @Test
    fun pagesCreateOnlyTheSelectedEditor() {
        assertTrue(home.contains("key(target.name)"))
        assertTrue(install.contains("key(spec.slot.id)"))
        assertTrue(navigation.contains("key(slot.id)"))
        assertFalse(home.contains("HomeCardWallpaperItem("))
        assertFalse(install.contains("InstallCardWallpaperItem("))
        assertFalse(navigation.contains("NavigationIconMiuixPreference("))
    }

    @Test
    fun smallWindowsScrollTheSelectorAndControlsTogether() {
        assertTrue(shell.contains("useBackgroundEditorTwoPane(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)"))
        assertTrue(shell.contains("Modifier.fillMaxSize().verticalScroll(rememberScrollState())"))
        assertTrue(shell.contains("widthIn(max = 1120.dp).fillMaxSize()"))
        assertTrue(shell.contains("WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()"))
        assertTrue(shell.contains("heightIn(min = 48.dp)"))
        assertTrue(shell.contains("FlowRow("))
    }

    @Test
    fun clearingRequiresConfirmationAndRetainsDayNightIsolation() {
        assertTrue(shell.contains("if (confirmClear)"))
        assertTrue(shell.contains("AlertDialog("))
        assertTrue(home.contains("key(editNight)"))
        assertTrue(home.contains("onClear = state.onClearWallpaper"))
        assertTrue(install.contains("key(editNight)"))
        assertTrue(install.contains("if (editNight) setThemeStoreImageSlotNightMedia(context, spec.slot, null"))
        assertTrue(install.contains("else setThemeStoreImageSlot(context, spec.slot, null)"))
    }

    @Test
    fun wallpaperWorkflowsRetainMediaCropVariantsAndPagePreview() {
        listOf(home, install).forEach { editor ->
            listOf("onPickImage", "onPickVideo", "SettingsWallpaperCropDialog(", "MediaVisualControls(",
                "MediaVariantControls(", "onGenerateResponsiveCrops").forEach { action ->
                assertTrue(action, editor.contains(action))
            }
        }
        assertTrue(home.contains("HomeCardWallpaperPagePreviewDialog(showFullPreview"))
        assertTrue(install.contains("InstallCardWallpaperScreenPreviewDialog(showPagePreview"))
        assertTrue(install.contains("val night = pendingNight"))
        assertTrue(install.contains("if (currentUri != uriString) return@launch"))
    }

    @Test
    fun navigationControlsStayInlineAndTheEditorInheritsPassthrough() {
        assertTrue(navigation.contains("NavigationIconAdjustmentControls("))
        assertFalse(navigation.contains("NavigationIconAdjustmentDialog("))
        listOf("labelDraft", "sizeScale", "innerPaddingDp", "verticalOffsetDp", "opacity", "mask", "tintArgb").forEach {
            assertTrue(it, controls.contains(it))
        }
        assertTrue(navigation.contains("settings_navigation_icons_restart_hint"))
        assertTrue(navigation.contains("SettingsWallpaperCropDialog("))
        assertTrue(navigation.contains("SettingsNavigationIconPreviewDialog("))
        assertFalse(shell.contains("UiPassthroughTheme(UiPassthroughSettings())"))
        assertTrue(shell.contains("containerColor = immersivePageColor(MaterialTheme.colorScheme.background)"))
        assertTrue(shell.contains("color = immersiveTopBarColor(MaterialTheme.colorScheme.surface)"))
        assertFalse(shell.contains(".alpha("))
    }

    @Test
    fun materialBridgeKeepsCurrentDayNightPaletteAndForegroundOverrides() {
        assertTrue(shell.contains("remember(dark, accent)"))
        assertTrue(shell.contains("if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light"))
        assertTrue(shell.contains("MiuixTheme.colorScheme.withUiPassthrough(LocalUiPassthroughSettings.current)"))
        listOf(shell, home, install, navigation).forEach { page ->
            assertTrue(page.contains("ui.component.passthrough.MaterialText as Text"))
        }
        assertTrue(shell.contains("ui.component.passthrough.MiuixIcon"))
        assertTrue(navigation.contains("ui.component.passthrough.MaterialIcon as Icon"))
    }

    @Test
    fun installPreviewUsesTheRuntimeOverlayAndThemeTextInsteadOfFixedWhite() {
        assertTrue(install.contains("installCardWallpaperOverlayColor(isInDarkTheme())"))
        assertTrue(install.contains("InstallCardWallpaperSample(spec, MaterialTheme.colorScheme.onSurface)"))
        assertFalse(install.contains("InstallCardWallpaperSample(spec, Color.White)"))
    }
}
