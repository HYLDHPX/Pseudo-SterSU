package me.weishu.kernelsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

val LocalImmersiveBackgroundActive = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun immersiveTopBarColor(defaultColor: Color): Color {
    val passthrough = LocalUiPassthroughSettings.current
    if (passthrough.enabled) return uiBackgroundWithOpacity(defaultColor, passthrough.topBarOpacity, MaterialTheme.colorScheme.surface)
    if (me.weishu.kernelsu.ui.component.rain.isRainInterfaceStyle()) return me.weishu.kernelsu.ui.component.rain.rainTopBarContainerColor()
    return if (LocalImmersiveBackgroundActive.current) Color.Transparent else defaultColor
}

@Composable
@ReadOnlyComposable
fun immersivePageColor(defaultColor: Color): Color {
    return if (LocalImmersiveBackgroundActive.current) Color.Transparent else defaultColor
}

@Composable
@ReadOnlyComposable
fun immersiveSurfaceColor(
    defaultColor: Color,
    darkAlpha: Float = 0.58f,
    lightAlpha: Float = 0.68f,
): Color {
    val passthrough = LocalUiPassthroughSettings.current
    if (passthrough.enabled) return uiBackgroundWithOpacity(defaultColor, passthrough.surfaceOpacity, MaterialTheme.colorScheme.surface)
    val rain = me.weishu.kernelsu.ui.component.rain.LocalRainSettings.current
    if (rain.active) return uiBackgroundWithOpacity(defaultColor, rain.surfaceOpacity, MaterialTheme.colorScheme.surface)
    if (!LocalImmersiveBackgroundActive.current) return defaultColor
    return defaultColor.copy(alpha = if (isInDarkTheme()) darkAlpha else lightAlpha)
}

@Composable
@ReadOnlyComposable
fun immersiveScrolledTopBarColor(defaultColor: Color): Color {
    return if (LocalUiPassthroughSettings.current.enabled) immersiveTopBarColor(defaultColor) else immersiveSurfaceColor(
        defaultColor = defaultColor,
        darkAlpha = 0.52f,
        lightAlpha = 0.60f,
    )
}
