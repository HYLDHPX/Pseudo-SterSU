package me.weishu.kernelsu.ui.screen.superuser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperUserLayoutContractTest {
    private fun source(path: String): String = File("src/main/java/me/weishu/kernelsu/$path").readText().replace("\r\n", "\n")
    private val miuix = source("ui/screen/superuser/SuperUserMiuix.kt")
    private val sidebar = source("ui/screen/superuser/SuperUserMaterial.kt")

    @Test
    fun bothHeadersMoveSortingIntoTheOverflowMenu() {
        for (screen in listOf(miuix, sidebar)) {
            assertTrue(screen.contains("R.string.superuser_sort_menu"))
            assertTrue(screen.contains("superUserSortLabel(uiState.sortOption)"))
            assertTrue(screen.contains("actions.onOpenSulog()"))
            assertTrue(screen.contains("actions.onOpenAppTools()"))
            assertTrue(screen.contains("actions.onToggleShowSystemApps()"))
            assertTrue(screen.contains("actions.onToggleShowOnlyPrimaryUserApps()"))
        }
        val miuixActions = miuix.substringAfter("actions = {").substringBefore("scrollBehavior = scrollBehavior")
        val materialActions = sidebar.substringAfter("actions = {").substringBefore("scrollBehavior = scrollBehavior")
        assertFalse(miuixActions.contains("TextButton("))
        assertFalse(materialActions.contains("TextButton("))
        assertFalse(materialActions.contains("Icons.AutoMirrored.Filled.Sort"))
        assertTrue(miuixActions.contains("showTopPopup.value = false\n                                                    showSortPopup.value = true"))
        assertTrue(materialActions.contains("onClick = { showSortMenu = true }"))
        assertFalse(miuix.contains("navigationIcon = {"))
        assertFalse(miuix.contains("IconButton(onClick = actions.onOpenAppTools)"))
        assertTrue(sidebar.contains("navigationIcon = if (sidebar) null else"))
    }

    @Test
    fun materialMenuHasOneThemedSurfaceInsteadOfOverlappingGroups() {
        val actions = sidebar.substringAfter("actions = {").substringBefore("scrollBehavior = scrollBehavior")
        assertTrue(Regex("\\bDropdownMenu\\(").findAll(actions).count() == 1)
        assertFalse(actions.contains("DropdownMenuPopup("))
        assertFalse(actions.contains("DropdownMenuGroup("))
        assertTrue(actions.contains("shape = RoundedCornerShape(16.dp)"))
        assertTrue(actions.contains("containerColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)"))
        assertTrue(actions.contains("tonalElevation = 0.dp"))
        assertTrue(actions.contains("shadowElevation = 4.dp"))
    }

    @Test
    fun materialSortingReplacesMenuContentAndCanReturnWithoutOpeningAnotherPopup() {
        val actions = sidebar.substringAfter("actions = {").substringBefore("scrollBehavior = scrollBehavior")
        assertTrue(actions.contains("if (showSortMenu) {"))
        assertTrue(actions.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(actions.contains("onClick = { showSortMenu = false }"))
        assertFalse(actions.contains("expanded = showSortMenu"))
        val dismiss = actions.substringAfter("onDismissRequest = {").substringBefore("},")
        assertTrue(dismiss.contains("showDropdown = false"))
        assertFalse(dismiss.contains("showSortMenu = false"))
        assertTrue(actions.contains("LaunchedEffect(showDropdown, showSortMenu)"))
        assertTrue(actions.contains("if (showDropdown) menuScrollState.scrollTo(0)"))
        assertTrue(actions.contains("scrollState = menuScrollState"))
    }

    @Test
    fun materialMenuKeepsCheckableStatesAndFitsSmallWindows() {
        assertTrue(sidebar.contains("(currentAppWindowSize().widthDp - 32f).coerceIn(0f, 280f).dp"))
        assertTrue(sidebar.contains("modifier = Modifier.width(menuWidth)"))
        for (state in listOf("reversed", "uiState.showSystemApps", "uiState.showOnlyPrimaryUserApps")) {
            assertTrue(sidebar.contains("toggleableState = ToggleableState($state)"))
        }
        assertTrue(sidebar.contains("selected = sortType == type"))
        assertTrue(sidebar.contains("maxLines = 2, overflow = TextOverflow.Ellipsis"))
    }

    @Test
    fun titleSearchAndListKeepTopSpacingInBothMiuixLayouts() {
        assertTrue(miuix.contains("modifier = Modifier.padding(top = 8.dp)"))
        assertTrue(miuix.contains("8.dp + 4.dp * (1f - scrollBehavior.state.collapsedFraction)"))
        assertTrue(miuix.contains("top = innerPadding.calculateTopPadding() + 12.dp"))
        assertTrue(sidebar.contains("topContentPadding = if (sidebar) 8.dp else 0.dp"))
        assertTrue(sidebar.contains("top = if (sidebar) 12.dp else 0.dp"))
    }

    @Test
    fun miuixAppRowsWrapStatusBadgesInsteadOfStackingThemAtTheRight() {
        val item = miuix.substringAfter("private fun GroupItem(").substringBefore("private fun SuperUserStatusBadge(")
        assertTrue(item.contains("FlowRow("))
        assertTrue(item.contains("maxLines = 2"))
        assertTrue(item.contains("TextOverflow.Ellipsis"))
        assertTrue(item.contains(".size(40.dp)"))
        assertFalse(item.contains("basicMarquee"))
        assertFalse(item.contains("horizontalAlignment = Alignment.End"))
    }

    @Test
    fun recentOrderUsesTheWholeUidGroupAndPersistsLaterChoices() {
        val viewModel = source("ui/viewmodel/SuperUserViewModel.kt")
        assertTrue(viewModel.contains("group.apps.maxOfOrNull { it.packageInfo.firstInstallTime }"))
        assertTrue(viewModel.contains("group.apps.maxOfOrNull { it.packageInfo.lastUpdateTime }"))
        assertTrue(viewModel.contains("superUserSortComparator(sortOption, base, ::groupRank)"))
        assertTrue(viewModel.contains("if (!recentDefaultApplied)"))
        assertTrue(viewModel.contains("putBoolean(PREFS_RECENT_SORT_DEFAULT, true)"))
    }

    @Test
    fun latestVisibleGroupIsPinnedAfterExistingSortingWithoutChangingPreferences() {
        val viewModel = source("ui/viewmodel/SuperUserViewModel.kt")
        val update = viewModel.substringAfter("private suspend fun updateVisibleApps(").substringBefore("private fun filterAndSort(")
        assertTrue(update.contains("val ordered = if (resort) sortGroups(grouped, sortOption) else grouped"))
        assertTrue(update.contains("moveMostRecentlyInstalledFirst(ordered)"))
        assertTrue(update.contains("group.apps.maxOfOrNull { it.packageInfo.firstInstallTime }"))
        assertTrue(update.contains("filterSearchResults(s, searchText)"))
        assertFalse(update.contains("putInt("))
        assertFalse(update.contains("RECENTLY_INSTALLED_WINDOW_MILLIS"))
    }
}
