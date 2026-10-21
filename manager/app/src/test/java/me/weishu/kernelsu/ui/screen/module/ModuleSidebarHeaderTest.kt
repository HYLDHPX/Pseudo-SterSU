package me.weishu.kernelsu.ui.screen.module

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSidebarHeaderTest {
    private val screen = File("src/main/java/me/weishu/kernelsu/ui/screen/module/ModuleMaterial.kt").readText().replace("\r\n", "\n")
    private val searchBar = File("src/main/java/me/weishu/kernelsu/ui/component/material/SearchBar.kt").readText().replace("\r\n", "\n")

    @Test
    fun sidebarModuleHeaderOptsIntoTheWallpaperSurface() {
        assertTrue(screen.contains("val sidebar = LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value"))
        assertTrue(screen.contains("useWallpaperSurface = sidebar"))
        assertTrue(searchBar.contains("useWallpaperSurface: Boolean = false"))
        assertTrue(searchBar.contains("if (useWallpaperSurface) {\n        Color.Transparent"))
        assertTrue(searchBar.contains("scrolledContainerColor = topBarContainerColor"))
    }

    @Test
    fun searchFieldUsesTheSharedSurfaceWithoutFadingItsContents() {
        val inlineSearch = searchBar.substringAfter("fun InlineSearchAppBar(").substringBefore("fun SearchAppBar(")
        assertTrue(inlineSearch.contains("immersiveSurfaceColor(MaterialTheme.colorScheme.surfaceContainerLow)"))
        assertTrue(inlineSearch.contains("shape = RoundedCornerShape(if (useWallpaperSurface) 12.dp else 28.dp)"))
        assertTrue(inlineSearch.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertFalse(inlineSearch.contains(".alpha("))
    }

    @Test
    fun moduleSearchRepositorySortingAndInstallationRemainAvailable() {
        assertTrue(screen.contains("onSearchTextChange = {"))
        assertTrue(screen.contains("actions.onSearchTextChange(it)"))
        assertTrue(screen.contains("onClearClick = actions.onClearSearch"))
        assertTrue(screen.contains("onClick = { actions.onOpenRepo() }"))
        assertTrue(screen.contains("actions.onToggleSortActionFirst()"))
        assertTrue(screen.contains("actions.onToggleSortEnabledFirst()"))
        assertTrue(screen.contains("if (showInstallFab)"))
    }
}
