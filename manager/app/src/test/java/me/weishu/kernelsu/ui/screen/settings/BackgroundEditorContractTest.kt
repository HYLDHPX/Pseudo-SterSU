package me.weishu.kernelsu.ui.screen.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contracts complement layout logic tests; they are not device/UI tests. */
class BackgroundEditorContractTest {
    private val root = "src/main/java/me/weishu/kernelsu/ui/screen/settings/"
    private val editor = File(root + "BackgroundEditorContent.kt").readText()
    private val screen = File(root + "BackgroundSettingsScreen.kt").readText()

    @Test
    fun onlyOneScopeOwnsAnEditorAndMediaPreview() {
        assertTrue(editor.contains("key(scopeKey)"))
        assertTrue(editor.contains("items(scopes, key = { it })"))
        assertFalse(screen.contains("BackgroundEditorCard("))
        assertTrue(editor.contains("rememberSaveable { mutableStateOf(GLOBAL_BACKGROUND_SCOPE_KEY) }"))
    }

    @Test
    fun inheritedMediaPreviewsKeepTheSelectedPagesPassthroughSettings() {
        assertTrue(editor.contains("if (inherited) globalState else state"))
        assertTrue(editor.contains("rememberUiPassthroughScope(pageTargetFromScopeKey(scopeKey)).settings"))
        assertTrue(screen.contains("takeIf { it.hasMedia }"))
        assertTrue(screen.contains("?: uiState.backgroundStateForScope(GLOBAL_BACKGROUND_SCOPE_KEY)"))
        assertTrue(screen.contains("previewScopeKey?.let(::pageTargetFromScopeKey)"))
    }

    @Test
    fun clearingIsConfirmedAgainstTheOriginallySelectedScope() {
        assertTrue(editor.contains("clearScopeKey?.let { pendingScope ->"))
        assertTrue(editor.contains("actions.onClear(pendingScope)"))
        assertFalse(editor.contains("actions.onClear(scopeKey)"))
        assertTrue(editor.contains("background_editor_clear_global"))
        assertTrue(editor.contains("background_editor_clear_page"))
    }

    @Test
    fun mediaAndTransparencySettingsRemainAvailable() {
        listOf("onPickImage", "onPickVideo", "onCrop", "onPreview", "onOpacityChange",
            "onDurationChange", "onFrameRateChange", "onVisualSettingsChange").forEach { action ->
            assertTrue(action, editor.contains("actions.$action"))
        }
        assertTrue(editor.contains("showHeader = false"))
        assertFalse(screen.contains("UiPassthroughSettings()"))
        assertTrue(screen.contains("AppearanceEditorPage(stringResource(R.string.background_editor_title), onBack)"))
        assertTrue(screen.indexOf("AppearanceEditorPage(") < screen.indexOf("SettingsWallpaperCropDialog("))
        assertTrue(editor.contains("ui.component.passthrough.MaterialText as Text"))
        assertTrue(editor.contains("ui.component.passthrough.MaterialIcon as Icon"))
        assertFalse(editor.contains(".alpha("))
    }

    @Test
    fun imageAndVideoPreviewsUseTheSameDayNightVeilAsTheBackground() {
        listOf("SettingsWallpaperPreviewDialog.kt", "SettingsVideoBackgroundPreviewDialog.kt").forEach { name ->
            val preview = File(root + name).readText()
            assertTrue(preview.contains("if (isInDarkTheme()) Color.Black else Color.White"))
            assertTrue(preview.contains("BackgroundPreviewUi(passthroughSettings)"))
        }
    }
}
