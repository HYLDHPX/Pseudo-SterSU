package me.weishu.kernelsu.ui.component.rebootlistpopup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenuPopup
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.bottombar.SidebarPaneShape
import me.weishu.kernelsu.ui.util.currentAppWindowSize
import me.weishu.kernelsu.ui.screen.home.HomeMetricCardWallpaperBackground
import me.weishu.kernelsu.ui.screen.home.HomeMetricCardWallpaperTarget
import me.weishu.kernelsu.ui.screen.home.rememberHomeMetricCardWallpaperBitmap
import me.weishu.kernelsu.ui.screen.home.rememberHomeMetricCardWallpaperState

internal data class SidebarRebootGridLayout(val columns: Int, val tileHeightDp: Float)

internal fun sidebarRebootGridLayout(menuWidthDp: Float, fontScale: Float): SidebarRebootGridLayout {
    val contentWidth = (menuWidthDp - 24f).coerceAtLeast(0f)
    // 28dp icon, 8dp gap, two 14sp label lines and 8dp padding above/below.
    val minimumTileHeight = 52f + 28f * fontScale.coerceAtLeast(1f)
    val columns = (3 downTo 1).firstOrNull { count ->
        (contentWidth - 8f * (count - 1)) / count >= minimumTileHeight
    } ?: 1
    val tileWidth = (contentWidth - 8f * (columns - 1)) / columns
    return SidebarRebootGridLayout(columns, tileWidth.coerceAtLeast(minimumTileHeight))
}

private fun RebootListOption.sidebarLabelRes(): Int = when (reason) {
    "soft_reboot" -> R.string.sidebar_reboot_soft
    "userspace" -> R.string.sidebar_reboot_userspace
    "recovery" -> R.string.sidebar_reboot_recovery
    "bootloader" -> R.string.sidebar_reboot_bootloader
    "download" -> R.string.sidebar_reboot_download
    "edl" -> R.string.sidebar_reboot_edl
    else -> R.string.sidebar_reboot_system
}

@Composable
internal fun SidebarRebootPopup() {
    KsuIsValid {
        var expanded by remember { mutableStateOf(false) }
        val onReboot = rememberRebootAction()
        val options = getRebootListOption()
        val window = currentAppWindowSize()
        val menuWidth = (window.widthDp - 24f).coerceIn(0f, 288f).dp
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val gridLayout = sidebarRebootGridLayout(menuWidth.value, fontScale)
        val columns = gridLayout.columns
        Box {
            Box(
                modifier = Modifier.size(52.dp).clip(SidebarPaneShape)
                    .clickable(role = Role.Button) { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, stringResource(R.string.reboot), Modifier.size(26.dp))
            }
            DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                val wallpaperState = rememberHomeMetricCardWallpaperState(
                    target = HomeMetricCardWallpaperTarget.RebootMenu,
                    onWallpaperSelected = {},
                )
                val wallpaperBitmap = rememberHomeMetricCardWallpaperBitmap(
                    uriString = wallpaperState.uriString,
                    crop = wallpaperState.crop,
                )
                val hasWallpaper = wallpaperBitmap != null || !wallpaperState.videoUriString.isNullOrBlank()
                val tileColor = if (hasWallpaper) {
                    Color.Black.copy(alpha = 0.56f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (hasWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                ) {
                    Box {
                        HomeMetricCardWallpaperBackground(
                            bitmap = wallpaperBitmap,
                            videoUriString = wallpaperState.videoUriString,
                            videoCrop = wallpaperState.crop,
                            visualSettings = wallpaperState.visualSettings,
                        )
                        Column(
                            modifier = Modifier.width(menuWidth)
                                .heightIn(max = (window.heightDp - 40f).coerceAtLeast(0f).dp)
                                .verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.sidebar_reboot_title),
                                modifier = Modifier
                                    .background(
                                        color = if (hasWallpaper) tileColor else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            options.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { option ->
                                        val label = stringResource(option.sidebarLabelRes())
                                        Column(
                                            modifier = Modifier.weight(1f)
                                                .height(gridLayout.tileHeightDp.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(tileColor)
                                                .clickable(role = Role.Button) {
                                                    expanded = false
                                                    onReboot(option.reason)
                                                }
                                                .semantics(mergeDescendants = true) { contentDescription = label }
                                                .padding(horizontal = 4.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                        ) {
                                            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(28.dp))
                                                Text(
                                                    when (option.reason) {
                                                        "soft_reboot" -> "S"
                                                        "userspace" -> "U"
                                                        "recovery" -> "R"
                                                        "bootloader" -> "B"
                                                        "download" -> "D"
                                                        "edl" -> "E"
                                                        else -> ""
                                                    },
                                                    fontSize = (11f / fontScale).sp,
                                                    fontWeight = FontWeight.Black,
                                                )
                                            }
                                            Text(
                                                label,
                                                modifier = Modifier.fillMaxWidth(),
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                textAlign = TextAlign.Center,
                                                minLines = 2,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
