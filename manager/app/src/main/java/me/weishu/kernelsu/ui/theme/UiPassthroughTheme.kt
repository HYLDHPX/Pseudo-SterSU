package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.Colors

val LocalUiPassthroughSettings = staticCompositionLocalOf { UiPassthroughSettings() }
private val LocalOriginalMaterialColors = staticCompositionLocalOf<ColorScheme?> { null }
private val LocalOriginalMiuixColors = staticCompositionLocalOf<Colors?> { null }

internal fun Color.withUiOpacity(opacity: Float): Color = copy(alpha = alpha * opacity)

internal fun uiBackgroundWithOpacity(color: Color, opacity: Float, fallback: Color): Color =
    (if (color == Color.Unspecified || color.alpha == 0f) fallback else color).copy(alpha = opacity)

fun ColorScheme.withUiPassthrough(settings: UiPassthroughSettings): ColorScheme {
    if (!settings.enabled) return this
    val alpha = settings.normalized().surfaceOpacity
    return copy(
        background = Color.Transparent,
        surface = surface.withUiOpacity(alpha),
        surfaceVariant = surfaceVariant.withUiOpacity(alpha),
        surfaceDim = surfaceDim.withUiOpacity(alpha),
        surfaceBright = surfaceBright.withUiOpacity(alpha),
        surfaceContainerLowest = surfaceContainerLowest.withUiOpacity(alpha),
        surfaceContainerLow = surfaceContainerLow.withUiOpacity(alpha),
        surfaceContainer = surfaceContainer.withUiOpacity(alpha),
        surfaceContainerHigh = surfaceContainerHigh.withUiOpacity(alpha),
        surfaceContainerHighest = surfaceContainerHighest.withUiOpacity(alpha),
    )
}

/** Change paint on surfaces, never alpha on a parent containing text or images. */
fun Colors.withUiPassthrough(settings: UiPassthroughSettings): Colors {
    if (!settings.enabled) return this
    val alpha = settings.normalized().surfaceOpacity
    return copy(
        background = Color.Transparent,
        surface = surface.withUiOpacity(alpha),
        surfaceVariant = surfaceVariant.withUiOpacity(alpha),
        surfaceContainer = surfaceContainer.withUiOpacity(alpha),
        surfaceContainerHigh = surfaceContainerHigh.withUiOpacity(alpha),
        surfaceContainerHighest = surfaceContainerHighest.withUiOpacity(alpha),
    )
}

@Composable
fun UiPassthroughTheme(settings: UiPassthroughSettings, content: @Composable () -> Unit) {
    val safe = settings.normalized()
    val base = LocalOriginalMiuixColors.current ?: MiuixTheme.colorScheme
    val materialBase = LocalOriginalMaterialColors.current ?: MaterialTheme.colorScheme
    val colors = base.withUiPassthrough(safe)
    CompositionLocalProvider(
        LocalUiPassthroughSettings provides safe,
        LocalOriginalMaterialColors provides materialBase,
        LocalOriginalMiuixColors provides base,
    ) {
        MaterialTheme(colorScheme = materialBase.withUiPassthrough(safe)) {
            MiuixTheme(colors = colors, textStyles = MiuixTheme.textStyles, content = content)
        }
    }
}

@Composable
fun uiPassthroughSurfaceColor(color: Color): Color {
    val settings = LocalUiPassthroughSettings.current
    return if (settings.enabled) uiBackgroundWithOpacity(color, settings.surfaceOpacity, MaterialTheme.colorScheme.surface)
        else color
}

@Composable
fun uiPassthroughNavigationColor(defaultColor: Color): Color {
    val settings = LocalUiPassthroughSettings.current
    return if (settings.enabled) {
        uiBackgroundWithOpacity(defaultColor, settings.bottomBarOpacity, MaterialTheme.colorScheme.surface)
    } else defaultColor
}
