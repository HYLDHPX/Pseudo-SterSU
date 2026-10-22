package me.weishu.kernelsu.ui.component.overflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import me.weishu.kernelsu.ui.component.bottombar.LocalSidebarGlassBackdrop
import me.weishu.kernelsu.ui.component.liquid.LocalLiquidGlassBackdrop
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

internal class OverflowMenuBackdropState(val backdrop: Backdrop?) {
    var captureActive by mutableStateOf(false)
}

internal val LocalOverflowMenuBackdrop = staticCompositionLocalOf<OverflowMenuBackdropState?> { null }

/** Capture this Compose page only while its popup is open, independently of global blur. */
@Composable
internal fun OverflowMenuBackdrop(content: @Composable () -> Unit) {
    // The wallpaper layer retains image details that an opaque page underlay can cover.
    val inherited = LocalSidebarGlassBackdrop.current ?: LocalLiquidGlassBackdrop.current
    val supported = !LocalInspectionMode.current && LocalView.current.isHardwareAccelerated
    val pageBackdrop = rememberBlurBackdrop(supported && inherited == null)
    val state = remember(inherited, pageBackdrop) { OverflowMenuBackdropState(inherited ?: pageBackdrop) }
    CompositionLocalProvider(LocalOverflowMenuBackdrop provides state) {
        Box(
            Modifier.fillMaxSize().then(
                if (inherited == null && state.captureActive && pageBackdrop != null) {
                    Modifier.layerBackdrop(pageBackdrop)
                } else Modifier,
            ),
        ) { content() }
    }
}
