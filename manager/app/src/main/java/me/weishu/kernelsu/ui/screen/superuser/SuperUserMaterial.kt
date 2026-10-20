package me.weishu.kernelsu.ui.screen.superuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.ApkeEmptyState
import me.weishu.kernelsu.ui.component.ApkeErrorState
import me.weishu.kernelsu.ui.component.ApkeLoadingState
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.material.rememberExpressivePageScrollBehavior
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.material.InlineSearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedItemContainer
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.ownerNameForUid

@Composable
fun SuperUserPagerMaterial(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    bottomInnerPadding: Dp,
    showRootToggle: Boolean = false,
) {
    val scrollBehavior = rememberExpressivePageScrollBehavior()
    val listState = rememberLazyListState()
    var scrollBeforeSearch by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()

    var localSearchText by remember { mutableStateOf(uiState.searchStatus.searchText) }
    LaunchedEffect(uiState.searchStatus.searchText) {
        localSearchText = uiState.searchStatus.searchText
    }
    LaunchedEffect(localSearchText) {
        if (localSearchText.isNotBlank()) {
            listState.scrollToItem(0)
        } else {
            scrollBeforeSearch?.let { (index, offset) ->
                listState.scrollToItem(index, offset)
                scrollBeforeSearch = null
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        topBar = {
            InlineSearchAppBar(
                title = { Text(stringResource(R.string.superuser)) },
                searchText = localSearchText,
                searchHint = stringResource(R.string.superuser_search_hint),
                onSearchTextChange = {
                    if (localSearchText.isBlank() && it.isNotBlank()) {
                        scrollBeforeSearch = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }
                    localSearchText = it
                    actions.onSearchTextChange(it)
                },
                onClearClick = {
                    localSearchText = ""
                    actions.onClearSearch()
                },
                navigationIcon = {
                    IconButton(onClick = actions.onOpenSulog) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Article,
                            contentDescription = stringResource(R.string.settings_sulog)
                        )
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.menu_sort)
                        )

                        DropdownMenuPopup(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortEntries = listOf(
                                0 to R.string.sort_by_name,
                                1 to R.string.sort_by_package_name,
                                2 to R.string.sort_by_install_time,
                                3 to R.string.sort_by_update_time,
                            )
                            val sortType = uiState.sortOption / 2
                            val reversed = uiState.sortOption % 2 != 0

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                                sortEntries.onEachIndexed { index, (type, resId) ->
                                    SelectableDropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = sortType == type,
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onUpdateSortOption(type * 2 + if (reversed) 1 else 0)
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortEntries.size
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(MenuDefaults.GroupSpacing))

                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                                CheckableDropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_reverse)) },
                                    checked = reversed,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onUpdateSortOption(sortType * 2 + if (reversed) 0 else 1)
                                        showSortMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(
                                        index = 0,
                                        count = 1
                                    ),
                                )
                            }
                        }
                    }

                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(onClick = { showDropdown = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenuPopup(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            val filterCount = if (uiState.userIds.size > 1) 2 else 1
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                CheckableDropdownMenuItem(
                                    text = { Text(stringResource(R.string.show_system_apps)) },
                                    checked = uiState.showSystemApps,
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onToggleShowSystemApps()
                                        showDropdown = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = 0, count = filterCount),
                                )
                                if (uiState.userIds.size > 1) {
                                    CheckableDropdownMenuItem(
                                        text = { Text(stringResource(R.string.show_only_primary_user_apps)) },
                                        checked = uiState.showOnlyPrimaryUserApps,
                                        checkedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                                contentDescription = null,
                                            )
                                        },
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onToggleShowOnlyPrimaryUserApps()
                                            showDropdown = false
                                        },
                                        shapes = MenuDefaults.itemShape(index = 1, count = filterCount),
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                actions.onRefresh()
                refreshTick.intValue++
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = uiState.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            val expandedSearchUids = remember { mutableStateOf(setOf<Int>()) }
            val isSearching = localSearchText.isNotBlank()
            val visibleApps = if (isSearching) uiState.searchResults else uiState.groupedApps

            val latestGroupedApps = rememberUpdatedState(uiState.groupedApps)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortOption,
                uiState.showSystemApps,
                uiState.showOnlyPrimaryUserApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestGroupedApps.value }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 16.dp + bottomInnerPadding
                ),
            ) {
                when {
                    !uiState.hasLoaded && uiState.groupedApps.isEmpty() -> {
                        item(key = "loading") {
                            ApkeLoadingState()
                        }
                    }

                    uiState.error != null -> {
                        item(key = "error") {
                            ApkeErrorState(
                                title = stringResource(R.string.superuser_failed_to_load),
                                supportingText = uiState.error.localizedMessage,
                                onRetry = actions.onRefresh,
                            )
                        }
                    }

                    isSearching && visibleApps.isEmpty() && uiState.searchStatus.resultStatus == SearchStatus.ResultStatus.LOAD -> {
                        item(key = "search-loading") { ApkeLoadingState() }
                    }

                    isSearching && visibleApps.isEmpty() && uiState.searchStatus.resultStatus == SearchStatus.ResultStatus.EMPTY -> {
                        item(key = "search-empty") {
                            ApkeEmptyState(title = stringResource(R.string.superuser_empty))
                        }
                    }

                    uiState.groupedApps.isEmpty() -> {
                        item(key = "empty") {
                            ApkeEmptyState(
                                title = stringResource(R.string.superuser_empty),
                            )
                        }
                    }
                }
                itemsIndexed(visibleApps, key = { _, item -> item.uid }) { index, group ->
                    val expanded = expandedSearchUids.value.contains(group.uid)
                    val onToggleExpand = {
                        if (group.apps.size > 1) {
                            expandedSearchUids.value = if (expandedSearchUids.value.contains(group.uid)) {
                                expandedSearchUids.value - group.uid
                            } else {
                                expandedSearchUids.value + group.uid
                            }
                        }
                    }
                    SegmentedItem(index = index, count = visibleApps.size) {
                        if (isSearching) {
                            SearchGroupItem(
                                group = group,
                                closeSearch = {},
                                onOpenProfile = actions.onOpenProfile,
                                showRootToggle = showRootToggle,
                                rootUpdating = group.uid in uiState.updatingRootUids,
                                onSetRootAccess = actions.onSetRootAccess,
                            )
                        } else {
                            Column {
                                GroupItem(
                                    group = group,
                                    selected = expanded,
                                    onToggleExpand = onToggleExpand,
                                    showRootToggle = showRootToggle,
                                    rootUpdating = group.uid in uiState.updatingRootUids,
                                    onSetRootAccess = { enabled -> actions.onSetRootAccess(group, enabled) },
                                ) {
                                    actions.onOpenProfile(group)
                                }
                                AnimatedVisibility(
                                    visible = expanded && group.apps.size > 1,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        group.apps.forEach { app ->
                                            SimpleAppItem(app = app) {
                                                actions.onOpenProfile(group)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGroupItem(
    group: GroupedApps,
    closeSearch: () -> Unit,
    onOpenProfile: (GroupedApps) -> Unit,
    showRootToggle: Boolean,
    rootUpdating: Boolean,
    onSetRootAccess: (GroupedApps, Boolean) -> Unit,
) {
    val openProfile = {
        closeSearch()
        onOpenProfile(group)
    }
    Column {
        GroupItem(
            group = group,
            selected = false,
            onToggleExpand = openProfile,
            showRootToggle = showRootToggle,
            rootUpdating = rootUpdating,
            onSetRootAccess = { enabled -> onSetRootAccess(group, enabled) },
            onClickPrimary = openProfile,
        )
        AnimatedVisibility(
            visible = group.apps.size > 1,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                group.apps.forEach { app ->
                    SimpleAppItem(
                        app = app,
                        matched = group.matchedIdentifiers.contains(app.displayIdentifier),
                    ) {
                        closeSearch()
                        onOpenProfile(group)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleAppItem(
    app: AppInfo,
    matched: Boolean = false,
    onNavigate: () -> Unit,
) {
    ListItem(
        onClick = onNavigate,
        modifier = Modifier.padding(horizontal = 4.dp),
        colors = ListItemDefaults.colors(
            containerColor = if (matched) {
                colorScheme.secondaryContainer
            } else {
                colorScheme.surfaceColorAtElevation(3.dp)
            }
        ),
        content = { Text(app.label, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        supportingContent = { Text(app.displayIdentifier, overflow = TextOverflow.Ellipsis, maxLines = 1) },
        leadingContent = {
            AppIconImage(
                packageInfo = app.packageInfo,
                label = app.label,
                modifier = Modifier
                    .size(40.dp)
                    .padding(start = 4.dp)
            )
        },
        trailingContent = {
            Icon(
                Icons.Filled.Remove,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    )
}

@Composable
private fun GroupItem(
    group: GroupedApps,
    selected: Boolean,
    onToggleExpand: () -> Unit,
    showRootToggle: Boolean,
    rootUpdating: Boolean,
    onSetRootAccess: (Boolean) -> Unit,
    onClickPrimary: () -> Unit,
) {
    val bg = colorScheme.primary
    val fg = colorScheme.onPrimary
    val umountBg = colorScheme.tertiaryContainer
    val umountFg = colorScheme.onTertiaryContainer
    val customBg = colorScheme.secondaryContainer
    val customFg = colorScheme.onSecondaryContainer
    val otherBg = colorScheme.tertiary
    val otherFg = colorScheme.onTertiary

    val userId = group.uid / 100000
    val tags = buildList {
        if (group.anyAllowSu && !showRootToggle) add(StatusMeta("ROOT", bg, fg))
        if (group.shouldUmount) add(StatusMeta("UMOUNT", umountBg, umountFg))
        if (group.anyCustom) add(StatusMeta("CUSTOM", customBg, customFg))
        if (userId != 0) add(StatusMeta("USER $userId", otherBg, otherFg))
    }
    val summaryText = if (group.apps.size > 1) {
        stringResource(R.string.group_contains_apps, group.apps.size)
    } else {
        group.primary.displayIdentifier
    }
    if (showRootToggle) {
        CompactRootItem(
            group = group,
            summaryText = summaryText,
            tags = tags,
            rootUpdating = rootUpdating,
            onToggleExpand = onToggleExpand,
            onSetRootAccess = onSetRootAccess,
            onOpenProfile = onClickPrimary,
        )
        return
    }
    SegmentedListItem(
        selected = selected,
        onClick = if (showRootToggle && group.apps.size > 1) onToggleExpand else onClickPrimary,
        onLongClick = if (showRootToggle) onClickPrimary else if (group.apps.size > 1) onToggleExpand else null,
        headlineContent = {
            Text(
                text = if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = summaryText,
                color = colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        trailingContent = {
            if (tags.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        StatusTag(
                            label = tag.label,
                            backgroundColor = tag.bg,
                            contentColor = tag.fg
                        )
                    }
                }
            }
        },
        leadingContent = {
            AppIconImage(
                packageInfo = group.primary.packageInfo,
                label = group.primary.label,
                modifier = Modifier.size(48.dp)
            )
        },
    )
}

@Composable
private fun CompactRootItem(
    group: GroupedApps,
    summaryText: String,
    tags: List<StatusMeta>,
    rootUpdating: Boolean,
    onToggleExpand: () -> Unit,
    onSetRootAccess: (Boolean) -> Unit,
    onOpenProfile: () -> Unit,
) {
    var pendingCustomChange by remember(group.uid) { mutableStateOf<Boolean?>(null) }
    val granted = group.anyAllowSu
    val canToggle = !rootUpdating && !group.primary.special
    val stateLabel = stringResource(if (granted) R.string.superuser_root_enabled else R.string.superuser_root_disabled)
    val stateDescription = stringResource(R.string.superuser_root_state_for_app, group.primary.label, stateLabel)
    val requestChange: (Boolean) -> Unit = { enabled ->
        if (canToggle && enabled != granted) {
            if (group.anyCustom) pendingCustomChange = enabled else onSetRootAccess(enabled)
        }
    }

    SegmentedItemContainer {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onClick = {
                            if (group.apps.size > 1) onToggleExpand() else requestChange(!granted)
                        },
                        onLongClickLabel = stringResource(R.string.superuser_open_app_profile),
                        onLongClick = onOpenProfile,
                    )
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconImage(
                    packageInfo = group.primary.packageInfo,
                    label = group.primary.label,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    Text(
                        text = summaryText,
                        color = colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (rootUpdating) {
                    Box(Modifier.size(width = 64.dp, height = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                } else {
                    ExpressiveSwitch(
                        checked = granted,
                        onCheckedChange = requestChange,
                        enabled = canToggle,
                        modifier = Modifier.semantics { contentDescription = stateDescription },
                        showThumbIcon = false,
                        compact = true,
                    )
                }
            }
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = 62.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { tag ->
                        StatusTag(tag.label, backgroundColor = tag.bg, contentColor = tag.fg)
                    }
                }
            }
        }
    }
    if (pendingCustomChange != null) {
        val enable = pendingCustomChange == true
        AlertDialog(
            onDismissRequest = { pendingCustomChange = null },
            title = { Text(stringResource(if (enable) R.string.superuser_custom_enable_title else R.string.superuser_custom_disable_title)) },
            text = { Text(stringResource(if (enable) R.string.superuser_custom_enable_message else R.string.superuser_custom_disable_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingCustomChange = null
                    onSetRootAccess(enable)
                }) { Text(stringResource(if (enable) R.string.superuser_root_enable_action else R.string.superuser_root_disable_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCustomChange = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
