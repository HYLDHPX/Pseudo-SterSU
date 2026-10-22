package me.weishu.kernelsu.ui.screen.colorpalette

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteScaleContractTest {
    private val source = File(
        "src/main/java/me/weishu/kernelsu/ui/screen/colorpalette/ColorPaletteScreenMaterial.kt"
    ).readText()

    @Test
    fun pageScaleUsesMiuixSliderWithoutChangingItsRangeOrCommitTiming() {
        assertTrue(source.contains("import top.yukonga.miuix.kmp.basic.Slider"))
        assertFalse(source.contains("import androidx.compose.material3.Slider"))
        assertTrue(source.contains("remember(uiState.pageScale)"))
        assertTrue(source.contains("onValueChange = { sliderValue = it }"))
        assertTrue(source.contains("onValueChangeFinished = { actions.onSetPageScale(sliderValue) }"))
        assertTrue(source.contains("valueRange = 0.8f..1.1f"))
        assertTrue(source.contains("keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f)"))
        assertTrue(source.contains("(sliderValue * 100).roundToInt()"))
    }

    @Test
    fun sliderUsesActiveThemeColorsAndAnAccessibleTouchTarget() {
        val slider = source.substringAfter("                        Slider(")
            .substringBefore("\n                    }")
        assertTrue(slider.contains("foregroundColor = MaterialTheme.colorScheme.primary"))
        assertTrue(slider.contains("backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest"))
        assertTrue(slider.contains("thumbColor = MaterialTheme.colorScheme.onPrimary"))
        assertTrue(slider.contains(".heightIn(min = 48.dp)"))
        assertTrue(slider.contains(".semantics { contentDescription = scaleTitle }"))
        assertFalse(slider.contains(".alpha("))
    }
}
