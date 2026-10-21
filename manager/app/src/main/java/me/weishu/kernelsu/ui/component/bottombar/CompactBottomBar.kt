package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.LocalInterfaceStyle
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import me.weishu.kernelsu.ui.theme.uiPassthroughNavigationColor
import me.weishu.kernelsu.ui.util.LocalCustomNavigationIcons
import me.weishu.kernelsu.ui.util.currentAppWindowSize
import me.weishu.kernelsu.ui.util.rememberSidebarWidgetConfig

/** A bounded, scrollable fallback for every style in narrow or short windows. */
@Composable
internal fun CompactBottomBar(
    navigationBadge: NavigationBadgeState,
    destinations: List<MainDestination>,
    modifier: Modifier = Modifier,
) {
    val pager = LocalMainPagerState.current
    val icons = LocalCustomNavigationIcons.current
    val window = currentAppWindowSize()
    val sidebarConfig = if (LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value) {
        rememberSidebarWidgetConfig()
    } else null
    val orderedDestinations = sidebarConfig?.let {
        orderSidebarDestinations(it.navigationOrder, destinations)
    } ?: destinations
    Surface(modifier = modifier.fillMaxWidth(), color = uiPassthroughNavigationColor(MaterialTheme.colorScheme.surfaceContainer)) {
        BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        )).padding(horizontal = 4.dp, vertical = 4.dp)) {
            val itemWidth = (maxWidth / destinations.size.coerceAtLeast(1)).coerceAtLeast(48.dp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()) {
                orderedDestinations.forEach { destination ->
                    val selected = pager.destinationForPage() == destination
                    val label = icons.labelFor(destination, stringResource(destination.label))
                    val foreground = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        modifier = Modifier.width(itemWidth).heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab, onClick = { pager.animateTo(destination) })
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides foreground) {
                            NavigationIconWithBadge(destination, icons.stateFor(destination), label, badgeFor(destination, navigationBadge))
                        }
                        if (!window.short && itemWidth >= 60.dp && sidebarConfig?.iconOnlyNavigation != true) {
                            Text(label, color = foreground, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
