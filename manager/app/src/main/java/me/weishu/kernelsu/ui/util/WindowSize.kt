package me.weishu.kernelsu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** Use the current app window, including the user's UI scale, not the physical display. */
internal data class AppWindowSize(val widthDp: Float, val heightDp: Float, val fontScale: Float) {
    val narrow: Boolean get() = widthDp < 360f * fontScale.coerceIn(1f, 1.3f)
    val short: Boolean get() = heightDp < 480f
    val compactNavigation: Boolean get() = narrow || short
    val canShowSidebar: Boolean get() = !compactNavigation
}

@Composable
internal fun currentAppWindowSize(): AppWindowSize {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val size = LocalWindowInfo.current.containerSize
    // IME visibility must not move the navigation and recreate the focused editor.
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val bottom = safe.getBottom(density)
    return AppWindowSize(
        widthDp = ((size.width - safe.getLeft(density, layoutDirection) - safe.getRight(density, layoutDirection)) / density.density)
            .coerceAtLeast(0f),
        heightDp = ((size.height - safe.getTop(density) - bottom) / density.density).coerceAtLeast(0f),
        fontScale = density.fontScale,
    )
}

/** Reserve room for a dialog title and outer margins; never impose a phone-sized minimum. */
internal fun dialogContentHeight(availableHeightDp: Float, fontScale: Float): Float =
    (availableHeightDp - 128f * fontScale.coerceIn(1f, 1.5f)).coerceIn(0f, 720f)

@Composable
internal fun adaptiveDialogContentMaxHeight(): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val window = currentAppWindowSize()
    val extraIme = (WindowInsets.ime.getBottom(density) - WindowInsets.systemBars.getBottom(density)).coerceAtLeast(0)
    return dialogContentHeight(window.heightDp - extraIme / density.density, window.fontScale).dp
}

@Composable
fun shouldShowSplitPane(): Boolean {
    val window = currentAppWindowSize()
    return shouldShowSplitPane(window.widthDp / window.fontScale.coerceAtLeast(1f), window.heightDp)
}

internal fun shouldShowSplitPane(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= 600f && heightDp >= 480f
