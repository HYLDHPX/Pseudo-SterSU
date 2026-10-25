package me.weishu.kernelsu.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.util.currentAppWindowSize
import me.weishu.kernelsu.ui.component.rain.RainChromeArea
import me.weishu.kernelsu.ui.component.rain.rainChromeDecoration
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Preserve the style's normal header; reserve space for content in a small window. */
@Composable
fun AdaptiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    val effectiveColor = if (me.weishu.kernelsu.ui.component.rain.isRainInterfaceStyle()) {
        me.weishu.kernelsu.ui.theme.immersiveTopBarColor(color)
    } else color
    val decoratedModifier = modifier.rainChromeDecoration(RainChromeArea.TopBar)
    if (currentAppWindowSize().compactNavigation) {
        SmallTopAppBar(
            title = title, modifier = decoratedModifier, color = effectiveColor, titleColor = titleColor,
            subtitle = subtitle, subtitleColor = subtitleColor,
            navigationIcon = navigationIcon, actions = { AdaptiveTopBarActions(actions) },
            scrollBehavior = scrollBehavior, defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = minOf(titlePadding, 12.dp), navigationIconPadding = minOf(navigationIconPadding, 8.dp),
            actionIconPadding = minOf(actionIconPadding, 8.dp), bottomContent = bottomContent,
        )
    } else {
        MiuixTopAppBar(
            title = title, modifier = decoratedModifier, color = effectiveColor, titleColor = titleColor,
            largeTitle = largeTitle, largeTitleColor = largeTitleColor,
            subtitle = subtitle, subtitleColor = subtitleColor,
            navigationIcon = navigationIcon, actions = actions, scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding, titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding, actionIconPadding = actionIconPadding,
            bottomContent = bottomContent,
        )
    }
}

/** Keep actions reachable without letting them consume the entire title/back area. */
@Composable
internal fun AdaptiveTopBarActions(content: @Composable RowScope.() -> Unit) {
    val window = currentAppWindowSize()
    Row(
        modifier = if (window.compactNavigation) Modifier
            .widthIn(max = (window.widthDp * 0.48f).coerceAtLeast(48f).dp)
            .horizontalScroll(rememberScrollState()) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
