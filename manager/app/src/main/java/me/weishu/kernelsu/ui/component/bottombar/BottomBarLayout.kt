package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep all tabs in the same bounded panel, including when KPM adds a fifth tab. */
internal fun floatingBottomBarWidth(availableWidth: Dp, tabsCount: Int): Dp =
    minOf(availableWidth.coerceAtLeast(0.dp), 76.dp * tabsCount.coerceAtLeast(1) + 8.dp)
