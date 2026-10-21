package me.weishu.kernelsu.ui.screen.superuser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperUserSidebarStyleTest {
    private val screen = File("src/main/java/me/weishu/kernelsu/ui/screen/superuser/SuperUserMaterial.kt").readText().replace("\r\n", "\n")
    private val searchBar = File("src/main/java/me/weishu/kernelsu/ui/component/material/SearchBar.kt").readText().replace("\r\n", "\n")

    @Test
    fun wallpaperHeaderIsOptInForSidebarSuperuserOnly() {
        assertTrue(screen.contains("val sidebar = LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value"))
        assertTrue(screen.contains("useWallpaperSurface = sidebar"))
        assertTrue(searchBar.contains("useWallpaperSurface: Boolean = false"))
        assertTrue(searchBar.contains("if (useWallpaperSurface) {\n        Color.Transparent"))
    }

    @Test
    fun standaloneCardsUseTheSettingsSurfaceWithoutFadingContent() {
        val container = screen.substringAfter("private fun SuperUserGroupContainer(").substringBefore("private fun SearchGroupItem(")
        assertTrue(container.contains("shape = RoundedCornerShape(12.dp)"))
        assertTrue(container.contains("color = immersiveSurfaceColor(colorScheme.surfaceContainerLow)"))
        assertTrue(container.contains("contentColor = colorScheme.onSurface"))
        assertTrue(container.contains("SegmentedItem(index = index, count = count, content = content)"))
        assertFalse(container.contains(".alpha("))
        assertTrue(screen.contains("Arrangement.spacedBy(if (sidebar) 8.dp else 2.dp)"))
    }

    @Test
    fun expandedAppsShareTheirParentCardSurface() {
        assertFalse(screen.contains("SegmentedItemContainer"))
        assertTrue(screen.contains("else if (sidebar) {\n                Color.Transparent"))
        assertTrue(screen.contains("SuperUserGroupContainer(index = index, count = visibleApps.size, sidebar = sidebar)"))
    }

    @Test
    fun authorizationSafeguardsAndLongPressArePreserved() {
        assertTrue(screen.contains("val canToggle = !rootUpdating && !group.primary.special"))
        assertTrue(screen.contains("if (group.anyCustom) pendingCustomChange = enabled else onSetRootAccess(enabled)"))
        assertTrue(screen.contains("onLongClick = onOpenProfile"))
        assertTrue(screen.contains("enabled = canToggle"))
        assertTrue(screen.contains("maxLines = 2"))
        assertTrue(screen.contains("contentMaxWidth = if (sidebar) 760.dp else SidebarUiTokens.ContentMaxWidth"))
    }
}
