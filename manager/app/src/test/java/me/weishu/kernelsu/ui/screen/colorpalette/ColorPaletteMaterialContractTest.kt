package me.weishu.kernelsu.ui.screen.colorpalette

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteMaterialContractTest {
    private val base = "src/main/java/me/weishu/kernelsu/ui/"
    private val source = File("${base}screen/colorpalette/ColorPaletteScreenMaterial.kt").readText()

    @Test
    fun missingControlsUseExistingStateAndActions() {
        for ((state, action) in listOf(
            "enableBlur" to "onSetEnableBlur",
            "enableFloatingBottomBar" to "onSetEnableFloatingBottomBar",
            "enableFloatingBottomBarBlur" to "onSetEnableFloatingBottomBarBlur",
            "autoHideNavigationBar" to "onSetAutoHideNavigationBar",
            "scrollHideNavigationBar" to "onSetScrollHideNavigationBar",
            "moduleTopBarAutoHideEnabled" to "onSetModuleTopBarAutoHideEnabled",
        )) {
            assertTrue(source.contains("checked = uiState.$state"))
            assertTrue(source.contains("onCheckedChange = actions.$action"))
        }
        for (action in listOf("onSetFontScale", "onSetBlurIntensity", "onSetMonetSurfaceOpacity")) {
            assertTrue(source.contains("onValueChangeFinished = actions.$action"))
        }
    }

    @Test
    fun blurOptionsAreGatedByAndroidVersionAndFloatingBarState() {
        assertTrue(source.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU"))
        assertTrue(source.contains("item(visible = supportsBlur)"))
        assertTrue(source.contains("item(visible = supportsBlur && uiState.enableFloatingBottomBar)"))
        assertTrue(source.contains("if (supportsBlur)"))
    }

    @Test
    fun materialWallpaperColorUsesItsRealKeyColorSettingNotTheMiuixOnlyFlag() {
        assertTrue(source.contains("checked = currentKeyColor == 0"))
        assertTrue(source.contains("actions.onSetKeyColor(if (enabled) 0 else currentAccent)"))
        assertFalse(source.contains("onCheckedChange = actions.onSetMiuixMonet"))
        assertTrue(source.contains("ColorMode.fromValue(state.currentColorMode.toNonMonetMode())"))
        assertTrue(source.contains("ColorMode.DARK_AMOLED) to stringResource(R.string.settings_theme_mode_amoled)"))
    }

    @Test
    fun presetsExposeAllExistingOperationsAndRestoreDialogState() {
        for (action in listOf(
            "onApplyThemePreset", "onSaveCustomThemePreset", "onApplyCustomThemePreset",
            "onRenameCustomThemePreset", "onDeleteCustomThemePreset", "onSetThemeSyncStrategy",
            "onResetThemeToDefault",
        )) {
            assertTrue(source.contains("actions.$action"))
        }
        assertTrue(source.contains("showSavePresetDialog by rememberSaveable"))
        assertTrue(source.contains("renamePresetId by rememberSaveable"))
        assertTrue(source.contains("it.isCompatibleWith(uiState.uiMode)"))
        val dialog = File("${base}screen/colorpalette/ThemePresetNameDialog.kt").readText()
        assertTrue(dialog.contains("LocalUiMode.current == UiMode.Material"))
        assertTrue(dialog.contains("AlertDialog("))
        assertTrue(dialog.contains("enabled = trimmed.isNotBlank()"))
    }

    @Test
    fun bottomBarConsumesFloatingAndBlurSettingsWithoutFadingItsContent() {
        val router = File("${base}component/bottombar/BottomBar.kt").readText()
        val bar = File("${base}component/bottombar/BottomBarMaterial.kt").readText()
        assertTrue(router.contains("BottomBarMaterial(navigationBadge, destinations, blurBackdrop, backdrop, modifier)"))
        for (setting in listOf("LocalEnableFloatingBottomBar", "LocalEnableFloatingBottomBarBlur", "LocalBlurIntensity")) {
            assertTrue(bar.contains("$setting.current"))
        }
        assertTrue(bar.contains("Modifier.drawBackdrop("))
        assertTrue(bar.contains("windowInsetsPadding(insets)"))
        assertTrue(bar.contains("!immersive && !passthrough.enabled"))
        assertTrue(bar.contains("uiPassthroughNavigationColor("))
        assertFalse(bar.contains(".alpha("))
    }

    @Test
    fun topBarHasACaptureLayerAndDoesNotOverrideWallpaperOrPassthrough() {
        val scaffold = File("${base}component/material/ExpressiveScaffold.kt").readText()
        val search = File("${base}component/material/SearchBar.kt").readText()
        assertTrue(scaffold.contains("LocalEnableBlur.current"))
        assertTrue(scaffold.contains("!LocalImmersiveBackgroundActive.current && !LocalUiPassthroughSettings.current.enabled"))
        assertTrue(scaffold.contains("Modifier.layerBackdrop(backdrop)"))
        assertTrue(scaffold.contains("BlurredBar(backdrop)"))
        assertTrue(search.contains("if (LocalMaterialBarBlurActive.current)"))
        val theme = File("${base}theme/MaterialTheme.kt").readText()
        assertTrue(theme.contains("colorScheme.withMonetSurfaceOpacity(appSettings.monetSurfaceOpacity)"))
    }

    @Test
    fun slidersKeepBoundedValuesAndCommitOnlyAfterDragging() {
        assertTrue(source.contains("valueRange = 0.85f..1.2f"))
        assertTrue(source.contains("valueRange = 0.5f..1.5f"))
        val slider = source.substringAfter("private fun ThemeSliderMaterial(")
            .substringBefore("@SuppressLint")
        assertTrue(slider.contains("remember(value)"))
        assertTrue(slider.contains("onValueChange = { sliderValue = it }"))
        assertTrue(slider.contains("onValueChangeFinished = { onValueChangeFinished(sliderValue) }"))
        assertTrue(slider.contains(".heightIn(min = 48.dp)"))
        assertTrue(slider.contains("contentDescription = title"))
        assertFalse(slider.contains(".alpha("))
    }
}
