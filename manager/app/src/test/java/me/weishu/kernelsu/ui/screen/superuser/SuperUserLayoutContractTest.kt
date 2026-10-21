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
    fun bothHeadersExposeTheCurrentSortWithoutTheOldButtonCrowding() {
        for (screen in listOf(miuix, sidebar)) {
            assertTrue(screen.contains("superUserSortLabel(uiState.sortOption)"))
            assertTrue(screen.contains("actions.onOpenSulog()"))
            assertTrue(screen.contains("actions.onOpenAppTools()"))
        }
        assertFalse(miuix.contains("navigationIcon = {"))
        assertFalse(miuix.contains("IconButton(onClick = actions.onOpenAppTools)"))
        assertTrue(sidebar.contains("navigationIcon = if (sidebar) null else"))
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
}
