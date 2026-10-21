package me.weishu.kernelsu.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperUserSortOrderTest {
    private data class App(val name: String, val timestamp: Long, val rank: Int)

    @Test
    fun newDefaultIsNewestInstallationFirst() {
        assertEquals(5, DEFAULT_SUPERUSER_SORT_OPTION)
        assertEquals(5, resolveSuperUserSortOption(null, recentDefaultApplied = false))
    }

    @Test
    fun legacySortMigratesToRecentFirstOnlyOnce() {
        assertEquals(5, resolveSuperUserSortOption(0, recentDefaultApplied = false))
        assertEquals(0, resolveSuperUserSortOption(0, recentDefaultApplied = true))
        assertEquals(6, resolveSuperUserSortOption(6, recentDefaultApplied = true))
    }

    @Test
    fun invalidOrMissingSavedOrderUsesTheDefault() {
        for (option in listOf(null, -1, 8, Int.MAX_VALUE)) {
            assertEquals(5, resolveSuperUserSortOption(option, recentDefaultApplied = true))
        }
    }

    @Test
    fun newestUnprivilegedAppPrecedesAnOlderRootApp() {
        val apps = listOf(App("Root", 100, 0), App("Recent", 300, 3), App("Custom", 200, 1))
        val order = superUserSortComparator(DEFAULT_SUPERUSER_SORT_OPTION, compareBy<App> { it.timestamp }) { it.rank }
        assertEquals(listOf("Recent", "Custom", "Root"), apps.sortedWith(order).map { it.name })
    }

    @Test
    fun reversingInstallationOrderShowsOldestFirst() {
        val apps = listOf(App("Recent", 300, 0), App("Old", 100, 3), App("Middle", 200, 1))
        val order = superUserSortComparator(SORT_BY_INSTALL_TIME * 2, compareBy<App> { it.timestamp }) { it.rank }
        assertEquals(listOf("Old", "Middle", "Recent"), apps.sortedWith(order).map { it.name })
    }

    @Test
    fun updateTimeAlsoHasPriorityOverAuthorizationGroups() {
        val apps = listOf(App("Root", 100, 0), App("Updated", 200, 3))
        val order = superUserSortComparator(SORT_BY_UPDATE_TIME * 2 + 1, compareBy<App> { it.timestamp }) { it.rank }
        assertEquals(listOf("Updated", "Root"), apps.sortedWith(order).map { it.name })
    }

    @Test
    fun equalTimesKeepAuthorizationAsATieBreaker() {
        val apps = listOf(App("Normal", 100, 3), App("Root", 100, 0))
        val order = superUserSortComparator(DEFAULT_SUPERUSER_SORT_OPTION, compareBy<App> { it.timestamp }) { it.rank }
        assertEquals(listOf("Root", "Normal"), apps.sortedWith(order).map { it.name })
    }

    @Test
    fun nameOrderStillGroupsRootAndCustomProfilesFirst() {
        val apps = listOf(App("A", 100, 3), App("Z", 100, 0), App("B", 100, 3))
        val order = superUserSortComparator(SORT_BY_NAME * 2, compareBy<App> { it.name }) { it.rank }
        assertEquals(listOf("Z", "A", "B"), apps.sortedWith(order).map { it.name })
    }

    @Test
    fun reverseNameOrderStillPreservesAuthorizationGroups() {
        val apps = listOf(App("A", 100, 3), App("Z", 100, 0), App("B", 100, 3))
        val order = superUserSortComparator(SORT_BY_NAME * 2 + 1, compareBy<App> { it.name }) { it.rank }
        assertEquals(listOf("Z", "B", "A"), apps.sortedWith(order).map { it.name })
    }

    @Test
    fun recentOrderWorksForEmptyAndSingleItemLists() {
        val order = superUserSortComparator(DEFAULT_SUPERUSER_SORT_OPTION, compareBy<App> { it.timestamp }) { it.rank }
        assertEquals(emptyList<App>(), emptyList<App>().sortedWith(order))
        val single = listOf(App("Single", 100, 0))
        assertEquals(single, single.sortedWith(order))
    }
}
