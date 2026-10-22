package me.weishu.kernelsu.ui.screen.superuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.AppInfo
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.liquid.globalLiquidGlassSurface
import me.weishu.kernelsu.ui.component.liquid.FrostedGlassCardStyle
import me.weishu.kernelsu.ui.component.snow.snowMiuixCardColors
import me.weishu.kernelsu.ui.component.snow.snowMiuixCardSurface
import me.weishu.kernelsu.ui.component.snow.isSnowInterfaceStyle
import me.weishu.kernelsu.ui.component.miuix.SearchBar
import me.weishu.kernelsu.ui.component.pixel.pixelAwareMiuixCardCornerRadius
import me.weishu.kernelsu.ui.component.pixel.pixelAwareMiuixCardShape
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.immersiveTopBarColor
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.theme.skrootproTopBarColors
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.ownerNameForUid
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import me.weishu.kernelsu.ui.component.passthrough.MiuixIcon as Icon
import me.weishu.kernelsu.ui.component.overflow.OverflowMenuAnchor
import me.weishu.kernelsu.ui.component.overflow.OverflowMenuTarget
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import me.weishu.kernelsu.ui.component.passthrough.MiuixText as Text
import top.yukonga.miuix.kmp.basic.TextButton
import me.weishu.kernelsu.ui.component.AdaptiveTopAppBar as TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SuperUserPagerMiuix(
    uiState: SuperUserUiState,
    actions: SuperUserActions,
    bottomInnerPadding: Dp,
) {
    val searchStatus = uiState.searchStatus
    val enableBlur = LocalEnableBlur.current
    val lazyListState = rememberLazyListState()
    var scrollBeforeSearch by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(searchStatus.searchText) {
        if (searchStatus.searchText.isNotBlank()) {
            lazyListState.scrollToItem(0)
        } else {
            scrollBeforeSearch?.let { (index, offset) ->
                lazyListState.scrollToItem(index, offset)
                scrollBeforeSearch = null
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 8.dp + 4.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = immersiveTopBarColor(
        if (blurActive) Color.Transparent else colorScheme.surface,
    )
    val topBarColors = skrootproTopBarColors(barColor, colorScheme.onSurface)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                        modifier = Modifier.padding(top = 8.dp),
                        color = topBarColors.container,
                        titleColor = topBarColors.content,
                        title = stringResource(R.string.superuser),
                        actions = {
                            OverflowMenuAnchor(
                                target = OverflowMenuTarget.Superuser,
                                icon = {
                                    Icon(
                                        imageVector = MiuixIcons.MoreCircle,
                                        tint = topBarColors.content,
                                        contentDescription = null,
                                    )
                                },
                            ) { expanded, dismiss, wallpaper ->
                                SuperUserOverflowMenu(
                                    expanded = expanded,
                                    onDismissRequest = dismiss,
                                    uiState = uiState,
                                    actions = actions,
                                    showTools = true,
                                    wallpaper = wallpaper,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            SearchBar(
                                searchStatus = searchStatus.copy(label = stringResource(R.string.superuser_search_hint)),
                                onSearchStatusChange = { status ->
                                    if (searchStatus.searchText.isBlank() && status.searchText.isNotBlank()) {
                                        scrollBeforeSearch = lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
                                    }
                                    actions.onSearchStatusChange(status)
                                },
                                searchBarTopPadding = dynamicTopPadding,
                            )
                        }
                    )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val isSearching = searchStatus.searchText.isNotBlank()
        val visibleApps = if (isSearching) uiState.searchResults else uiState.groupedApps
        val expandedSearchUids = remember { mutableStateOf(setOf<Int>()) }
        LaunchedEffect(uiState.searchResults) {
            expandedSearchUids.value = uiState.searchResults
                .filter { it.apps.size > 1 }
                .map { it.uid }
                .toSet()
        }
            var refreshTick by remember { mutableIntStateOf(0) }
            val latestGroupedApps = rememberUpdatedState(uiState.groupedApps)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                lazyListState,
                uiState.sortOption,
                uiState.showSystemApps,
                uiState.showOnlyPrimaryUserApps,
                refreshTick,
                isBusy = { latestRefreshing.value },
            ) { latestGroupedApps.value }
            val pullToRefreshState = rememberPullToRefreshState()
            val refreshTexts = listOf(
                stringResource(R.string.refresh_pulling),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh),
                stringResource(R.string.refresh_complete),
            )

            if (uiState.groupedApps.isEmpty() && !uiState.hasLoaded) {
                SuperUserStateContent(
                    uiState = uiState,
                    bottomInnerPadding = bottomInnerPadding,
                    innerPadding = innerPadding,
                    layoutDirection = layoutDirection,
                    onRetry = actions.onRefresh,
                )
            } else if (uiState.groupedApps.isEmpty()) {
                SuperUserStateContent(
                    uiState = uiState,
                    bottomInnerPadding = bottomInnerPadding,
                    innerPadding = innerPadding,
                    layoutDirection = layoutDirection,
                    onRetry = actions.onRefresh,
                )
            } else {
                val expandedUids = remember { mutableStateOf(setOf<Int>()) }
                PullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    pullToRefreshState = pullToRefreshState,
                    onRefresh = {
                        actions.onRefresh()
                        refreshTick++
                    },
                    refreshTexts = refreshTexts,
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection)
                    ),
                ) {
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding() + 12.dp,
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection)
                            ),
                            overscrollEffect = null,
                        ) {
                            if (isSearching && visibleApps.isEmpty() && searchStatus.resultStatus == SearchStatus.ResultStatus.EMPTY) {
                                item(key = "search-empty") {
                                    Text(
                                        text = stringResource(R.string.superuser_empty),
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            items(visibleApps, key = { it.uid }, contentType = { "group" }) { group ->
                                val expandedSet = if (isSearching) expandedSearchUids else expandedUids
                                val expanded = expandedSet.value.contains(group.uid)
                                Column {
                                    GroupItem(
                                        group = group,
                                        onToggleExpand = {
                                            if (group.apps.size > 1) {
                                                expandedSet.value =
                                                    if (expanded) expandedSet.value - group.uid else expandedSet.value + group.uid
                                            }
                                        }
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
                                                SimpleAppItem(
                                                    app = app,
                                                    matched = isSearching && group.matchedIdentifiers.contains(app.displayIdentifier),
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(bottomInnerPadding))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun SuperUserStateContent(
    uiState: SuperUserUiState,
    bottomInnerPadding: Dp,
    innerPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = bottomInnerPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            !uiState.hasLoaded || uiState.isRefreshing -> InfiniteProgressIndicator()
            uiState.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.superuser_failed_to_load), color = colorScheme.onSurfaceVariantSummary, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    text = stringResource(R.string.network_retry),
                    onClick = onRetry,
                )
            }
            else -> Text(text = stringResource(R.string.superuser_empty), color = colorScheme.onSurfaceVariantSummary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SimpleAppItem(
    app: AppInfo,
    matched: Boolean = false,
) {
    Row {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .width(6.dp)
                .height(24.dp)
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(16.dp))
                .background(if (matched) colorScheme.primary else colorScheme.primaryContainer)
        )
        Card(
            cornerRadius = pixelAwareMiuixCardCornerRadius(18.dp),
            modifier = Modifier
                .padding(start = 6.dp, end = 12.dp, bottom = 6.dp)
                .globalLiquidGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    surfaceAlpha = 0.54f,
                    blurRadius = 9.dp,
                    refractionHeight = 12.dp,
                    refractionAmount = 8.dp,
                    strokeAlpha = 0.60f,
                    cardStyle = FrostedGlassCardStyle.Ice,
                )
                .snowMiuixCardSurface(shape = RoundedCornerShape(18.dp)),
            colors = snowMiuixCardColors(),
        ) {
            BasicComponent(
                title = app.label,
                summary = app.displayIdentifier,
                startAction = {
                    AppIconImage(
                        packageInfo = app.packageInfo,
                        label = app.label,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(40.dp)
                    )
                },
                insideMargin = PaddingValues(
                    start = 9.dp,
                    top = if (isSnowInterfaceStyle()) 7.dp else 0.dp,
                    end = 9.dp,
                    bottom = 0.dp,
                )
            )
        }
    }
}

@Composable
private fun GroupItem(
    group: GroupedApps,
    onToggleExpand: () -> Unit,
    onClickPrimary: () -> Unit,
) {
    val isInDarkTheme = isInDarkTheme()
    val cardShape = pixelAwareMiuixCardShape(RoundedCornerShape(16.dp))

    val rootBg = if (isInDarkTheme) Color(0xFF173A2A) else Color(0xFFE8F7EF)
    val rootFg = if (isInDarkTheme) Color(0xFF8DEDB7) else Color(0xFF0E6D43)
    val unmountBg = if (isInDarkTheme) Color(0xFF30343B) else Color(0xFFF0F3F7)
    val unmountFg = if (isInDarkTheme) Color(0xFFC5CDD8) else Color(0xFF596575)
    val customBg = if (isInDarkTheme) Color(0xFF302845) else Color(0xFFF2ECFF)
    val customFg = if (isInDarkTheme) Color(0xFFD9C8FF) else Color(0xFF6444A9)
    val userBg = if (isInDarkTheme) Color(0xFF203845) else Color(0xFFE8F4FA)
    val userFg = if (isInDarkTheme) Color(0xFF9FDDF2) else Color(0xFF2D6B83)

    val userId = group.uid / 100000
    val tags = remember(group.anyAllowSu, group.shouldUmount, group.anyCustom, userId, isInDarkTheme) {
        buildList {
            if (group.anyAllowSu) add(StatusMeta("ROOT", rootBg, rootFg))
            if (group.shouldUmount) add(StatusMeta("UMOUNT", unmountBg, unmountFg))
            if (group.anyCustom) add(StatusMeta("CUSTOM", customBg, customFg))
            if (userId != 0) add(StatusMeta("USER $userId", userBg, userFg))
        }
    }
    Card(
        cornerRadius = pixelAwareMiuixCardCornerRadius(16.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .globalLiquidGlassSurface(
                shape = cardShape,
                surfaceAlpha = 0.58f,
                blurRadius = 10.dp,
                refractionHeight = 14.dp,
                refractionAmount = 9.dp,
                strokeAlpha = 0.66f,
                cardStyle = FrostedGlassCardStyle.Ice,
            )
            .snowMiuixCardSurface(shape = cardShape),
        colors = snowMiuixCardColors(),
        onClick = onClickPrimary,
        onLongPress = if (group.apps.size > 1) onToggleExpand else null,
        showIndication = true,
        insideMargin = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = if (isSnowInterfaceStyle()) 14.dp else 8.dp,
            bottom = 8.dp,
        )
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                packageInfo = group.primary.packageInfo,
                label = group.primary.label,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(40.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                Text(
                    text = if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (group.apps.size > 1) {
                        stringResource(R.string.group_contains_apps, group.apps.size)
                    } else {
                        group.primary.displayIdentifier
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight(450),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tags.forEach { tag ->
                            SuperUserStatusBadge(tag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperUserStatusBadge(tag: StatusMeta) {
    val isRoot = tag.label == "ROOT"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(tag.bg)
            .padding(
                start = if (isRoot) 7.dp else 8.dp,
                top = 3.dp,
                end = 8.dp,
                bottom = 3.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isRoot) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(tag.fg)
            )
        }
        Text(
            text = tag.label,
            color = tag.fg,
            fontSize = 9.5.sp,
            fontWeight = FontWeight(750),
            maxLines = 1,
            softWrap = false
        )
    }
}
