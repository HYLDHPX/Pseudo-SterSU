package me.weishu.kernelsu.ui.screen.superuser

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.viewmodel.DEFAULT_SUPERUSER_SORT_OPTION

internal fun superUserSortLabel(sortOption: Int): Int = when {
    sortOption == DEFAULT_SUPERUSER_SORT_OPTION -> R.string.recently_installed
    sortOption / 2 == 1 -> R.string.sort_by_package_name
    sortOption / 2 == 2 -> R.string.sort_by_install_time
    sortOption / 2 == 3 -> R.string.sort_by_update_time
    else -> R.string.sort_by_name
}

@Immutable
data class GroupedApps(
    val uid: Int,
    val apps: List<AppInfo>,
    val primary: AppInfo,
    val anyAllowSu: Boolean,
    val anyCustom: Boolean,
    val shouldUmount: Boolean,
    val ownerName: String? = null,
    val matchedIdentifiers: Set<String> = emptySet(),
)

@Stable
data class SuperUserUiState(
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val groupedApps: List<GroupedApps> = emptyList(),
    val recentlyInstalledResults: List<GroupedApps> = emptyList(),
    val userIds: List<Int> = emptyList(),
    val searchStatus: SearchStatus = SearchStatus(""),
    val searchResults: List<GroupedApps> = emptyList(),
    val showSystemApps: Boolean = false,
    val showOnlyPrimaryUserApps: Boolean = false,
    val sortOption: Int = DEFAULT_SUPERUSER_SORT_OPTION,
    val updatingRootUids: Set<Int> = emptySet(),
    val error: Throwable? = null
)

@Immutable
data class SuperUserActions(
    val onRefresh: () -> Unit,
    val onOpenSulog: () -> Unit,
    val onOpenAppTools: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onSearchStatusChange: (SearchStatus) -> Unit,
    val onClearSearch: () -> Unit,
    val onToggleShowSystemApps: () -> Unit,
    val onToggleShowOnlyPrimaryUserApps: () -> Unit,
    val onUpdateSortOption: (Int) -> Unit,
    val onOpenProfile: (GroupedApps) -> Unit,
    val onSetRootAccess: (GroupedApps, Boolean) -> Unit,
)

@Immutable
data class StatusMeta(
    val label: String,
    val bg: Color,
    val fg: Color
)
