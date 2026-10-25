package me.weishu.kernelsu.ui.component.rain

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors

/** Surface paint only: foreground content and semantic success/error colors stay opaque. */
internal fun ColorScheme.withRainAppearance(settings: RainSettings, dark: Boolean): ColorScheme {
    if (!settings.active) return this
    val palette = rainPalette(settings, dark)
    val surface = palette.surfaceTop.copy(alpha = settings.surfaceOpacity)
    val raised = palette.surfaceBottom.copy(alpha = settings.surfaceOpacity)
    return copy(
        background = Color.Transparent, onBackground = palette.content,
        surface = surface, onSurface = palette.content,
        surfaceDim = raised, surfaceBright = surface,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = raised, surfaceContainerHighest = raised,
        surfaceVariant = raised,
        onSurfaceVariant = lerp(palette.content, palette.surfaceTop, 0.23f),
        outline = palette.outline, outlineVariant = palette.outline.copy(alpha = 0.24f),
    )
}

internal fun Colors.withRainAppearance(settings: RainSettings, dark: Boolean): Colors {
    if (!settings.active) return this
    val palette = rainPalette(settings, dark)
    val surface = palette.surfaceTop.copy(alpha = settings.surfaceOpacity)
    val raised = palette.surfaceBottom.copy(alpha = settings.surfaceOpacity)
    val secondary = lerp(palette.content, palette.surfaceTop, 0.23f)
    return copy(
        background = Color.Transparent, onBackground = palette.content, onBackgroundVariant = secondary,
        surface = surface, surfaceVariant = raised, surfaceContainer = surface,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
        onSurface = palette.content, onSurfaceSecondary = secondary,
        onSurfaceVariantSummary = secondary, onSurfaceVariantActions = palette.content,
        onSurfaceContainer = palette.content, onSurfaceContainerVariant = secondary,
        onSurfaceContainerHigh = palette.content, onSurfaceContainerHighest = palette.content,
        outline = palette.outline,
    )
}
