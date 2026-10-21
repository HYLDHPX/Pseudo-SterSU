package me.weishu.kernelsu.ui.component.overflow

import androidx.compose.ui.graphics.Color

internal fun overflowMenuWidth(availableWidthDp: Float, fontScale: Float): Float {
    val width = availableWidthDp.takeIf(Float::isFinite) ?: 0f
    val scale = fontScale.takeIf(Float::isFinite)?.coerceIn(1f, 1.25f) ?: 1f
    return (208f * scale).coerceAtMost((width - 24f).coerceAtLeast(0f))
}

internal fun overflowMenuMaxHeight(availableHeightDp: Float): Float =
    ((availableHeightDp.takeIf(Float::isFinite) ?: 0f) - 24f).coerceAtLeast(0f)

internal fun overflowMenuTint(darkMode: Boolean, hasBlurredBackground: Boolean): Color =
    (if (darkMode) Color(0xFF1C2028) else Color(0xFFF5F7FA))
        .copy(alpha = if (hasBlurredBackground) 0.82f else 1f)

internal fun overflowMenuContentColor(darkMode: Boolean): Color =
    if (darkMode) Color(0xFFF3F5F8) else Color(0xFF191D24)

internal enum class OverflowMenuTarget(val wallpaperKey: String) {
    Superuser("superuser_overflow_menu_wallpaper_uri"),
    Module("module_overflow_menu_wallpaper_uri"),
}
