package me.weishu.kernelsu.ui.screen.modulerepo

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring contracts supplement compilation; these are not rendered UI tests. */
class ModuleRepoSearchUiTest {
    @Test
    fun materialSearchStaysInTheRepositoryPage() {
        val page = source("screen/modulerepo/ModuleRepoMaterial.kt").substringBefore("private fun RepoBadges")
        assertTrue(page.contains("InlineSearchAppBar("))
        assertFalse(page.contains("searchContent ="))
        assertTrue(page.contains("searchHint = stringResource(R.string.module_repo_search_hint)"))
        assertTrue(page.contains("onSearchTextChange = actions.onSearchTextChange"))
        assertTrue(page.contains("onClearClick = actions.onClearSearch"))
        assertTrue(page.contains("if (isSearching) state.searchResults else state.modules"))
    }

    @Test
    fun clearingSearchKeepsTheUnfilteredListsScrollPosition() {
        val page = source("screen/modulerepo/ModuleRepoMaterial.kt")
        assertTrue(page.contains("if (isSearching) searchListState else listState"))
        val normalScroll = page.substringAfter("ScrollToTopOnChange(").substringBefore(") { latestModules.value }")
        assertTrue(normalScroll.contains("listState,"))
        assertFalse(normalScroll.contains("state.searchStatus.searchText"))
        assertTrue(page.contains("listState = visibleListState"))
    }

    @Test
    fun searchDistinguishesNoMatchesFromLoadingAndPreservesRefreshAndSort() {
        val page = source("screen/modulerepo/ModuleRepoMaterial.kt")
        assertTrue(page.contains("state.searchStatus.resultStatus == SearchStatus.ResultStatus.EMPTY"))
        assertTrue(page.contains("stringResource(R.string.module_search_empty)"))
        assertTrue(page.contains("onModuleClick = actions.onOpenRepoDetail"))
        assertTrue(page.contains("actions.onRefresh()"))
        assertTrue(page.contains("actions.onSetSortOrder(order)"))
    }

    @Test
    fun searchUsesThemeSurfacesAndAnAccessibleSingleLineHint() {
        val page = source("screen/modulerepo/ModuleRepoMaterial.kt").substringBefore("private fun RepoBadges")
        val bar = source("component/material/SearchBar.kt")
            .substringAfter("fun InlineSearchAppBar(").substringBefore("fun SearchAppBar(")
        assertTrue(page.contains("useWallpaperSurface = true"))
        assertTrue(bar.contains("immersiveSurfaceColor(MaterialTheme.colorScheme.surfaceContainerLow)"))
        assertTrue(bar.contains("maxLines = 1"))
        assertTrue(bar.contains("overflow = TextOverflow.Ellipsis"))
        assertTrue(bar.contains("immersiveTopBarColor(defaultTopBarContainerColor)"))
        assertTrue(bar.contains("focusedTextColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(bar.contains("unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(bar.contains("color = if (searchFocused) MaterialTheme.colorScheme.primary else Color.Transparent"))
        assertTrue(bar.contains("stringResource(R.string.cloud_theme_clear_search)"))
        assertTrue(bar.contains("ImeAction.Search"))
        assertTrue(bar.contains("keyboardController?.hide()"))
        assertFalse(bar.contains("Color.White"))
        assertFalse(bar.contains(".alpha("))
    }

    @Test
    fun bothRenderersHaveALocalizedNonEmptySearchHint() {
        assertTrue(source("screen/modulerepo/ModuleRepoMiuix.kt")
            .contains("state.searchStatus.copy(label = stringResource(R.string.module_repo_search_hint))"))
        listOf("values", "values-zh-rCN", "values-zh-rTW").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            val hint = strings.substringAfter("<string name=\"module_repo_search_hint\">").substringBefore("</string>")
            assertTrue(locale, strings.contains("name=\"module_repo_search_hint\""))
            assertTrue(locale, hint.isNotBlank())
        }
    }

    private fun source(path: String): String =
        File("src/main/java/me/weishu/kernelsu/ui/$path").readText().replace("\r\n", "\n")
}
