package me.weishu.kernelsu.ui.component.overflow

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.liquid.LocalLiquidGlassBackdrop
import me.weishu.kernelsu.ui.theme.UiPassthroughTheme
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import me.weishu.kernelsu.ui.util.currentAppWindowSize
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** A single shared surface: blur the background, never the menu's text or icons. */
@Composable
internal fun FrostedOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    target: OverflowMenuTarget,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val wallpaper = rememberOverflowMenuWallpaper(target)
    var showWallpaperEditor by remember(target) { mutableStateOf(false) }
    val window = currentAppWindowSize()
    val darkMode = isInDarkTheme()
    val contentColor = overflowMenuContentColor(darkMode)
    val pageBackdrop = LocalOverflowMenuBackdrop.current
    val backdrop = pageBackdrop?.backdrop ?: LocalLiquidGlassBackdrop.current
    val shape = RoundedCornerShape(16.dp)
    DisposableEffect(pageBackdrop, expanded) {
        pageBackdrop?.captureActive = expanded
        onDispose { pageBackdrop?.captureActive = false }
    }

    // Restore original theme colors and opaque foregrounds even with global UI passthrough.
    UiPassthroughTheme(UiPassthroughSettings()) {
        DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest) {
            Surface(
                shape = shape,
                color = Color.Transparent,
                contentColor = contentColor,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
            ) {
                Box(
                    Modifier.width(overflowMenuWidth(window.widthDp, window.fontScale).dp)
                        .heightIn(max = overflowMenuMaxHeight(window.heightDp).dp)
                        .clip(shape),
                ) {
                    if (backdrop != null && wallpaper.bitmap == null) {
                        Box(Modifier.matchParentSize().drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = { blur(20.dp.toPx(), 20.dp.toPx()) },
                        ))
                    }
                    wallpaper.bitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                                .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                        )
                    }
                    Box(Modifier.matchParentSize().background(overflowMenuTint(
                        darkMode, backdrop != null || wallpaper.bitmap != null,
                    )))
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(vertical = 8.dp)) {
                            content()
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = contentColor.copy(alpha = 0.12f),
                            )
                            CompactOverflowMenuItem(
                                text = { Text(stringResource(R.string.overflow_menu_wallpaper), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    onDismissRequest()
                                    showWallpaperEditor = true
                                },
                            )
                        }
                    }
                }
            }
        }
        if (showWallpaperEditor) {
            OverflowMenuWallpaperDialog(wallpaper, onDismissRequest = { showWallpaperEditor = false })
        }
    }
}

@Composable
internal fun CompactOverflowMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val color = LocalContentColor.current
    DropdownMenuItem(
        text = { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() } },
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).heightIn(min = 48.dp),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color, trailingIconColor = color),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    )
}
