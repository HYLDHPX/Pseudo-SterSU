package me.weishu.kernelsu.ui.screen.module

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import me.weishu.kernelsu.ui.component.passthrough.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleUpdateInfo
import me.weishu.kernelsu.data.repository.isSoftRebootPreferred
import me.weishu.kernelsu.ui.InterfaceStyle
import me.weishu.kernelsu.ui.LocalInterfaceStyle
import me.weishu.kernelsu.ui.component.ObserveAsEvents
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.dialog.rememberLoadingDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.rememberExpressivePageScrollBehavior
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.material.InlineSearchAppBar
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.reboot
import me.weishu.kernelsu.ui.util.currentAppWindowSize
import top.yukonga.miuix.kmp.nav.gesture.WindowNavigationEventBridge

@SuppressLint("StringFormatInvalid")
@Composable
fun ModulePagerMaterial(
    uiState: ModuleUiState,
    confirmDialogState: ModuleConfirmDialogState?,
    moduleEvent: Flow<ModuleEffect>,
    actions: ModuleActions,
    bottomInnerPadding: Dp,
) {
    val snackBarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val sidebar = LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value

    val context = LocalContext.current
    val resource = LocalResources.current

    val scrollBehavior = rememberExpressivePageScrollBehavior()

    val pullToRefreshState = rememberPullToRefreshState()

    val listState = rememberLazyListState()
    var scrollBeforeSearch by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val searchText = uiState.searchStatus.searchText
    LaunchedEffect(searchText) {
        if (searchText.isNotBlank()) {
            listState.scrollToItem(0)
        } else {
            scrollBeforeSearch?.let { (index, offset) ->
                listState.scrollToItem(index, offset)
                scrollBeforeSearch = null
            }
        }
    }
    val refreshTick = remember { mutableIntStateOf(0) }
    val threshold = with(LocalDensity.current) { 100.dp.toPx() }
    val fabExpanded by remember {
        var lastIndex = 0
        var lastOffset = 0
        var scrollDelta = 0f
        var expanded = true
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            val delta = if (currentIndex == lastIndex) {
                (currentOffset - lastOffset).toFloat()
            } else if (currentIndex > lastIndex) {
                100f
            } else {
                -100f
            }
            scrollDelta = (scrollDelta + delta).coerceIn(-threshold, threshold)
            lastIndex = currentIndex
            lastOffset = currentOffset
            if (currentIndex == 0) {
                expanded = true
                scrollDelta = 0f
            } else if (expanded && scrollDelta >= threshold) {
                expanded = false
                scrollDelta = 0f
            } else if (!expanded && scrollDelta <= -threshold) {
                expanded = true
                scrollDelta = 0f
            }
            expanded
        }
    }
    val showInstallFab = uiState.installButtonVisible && !(sidebar && currentAppWindowSize().canShowSidebar)

    val shortcutState = rememberModuleShortcutState(context)
    val showShortcutDialog = remember { mutableStateOf(false) }
    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            when (val request = confirmDialogState?.request) {
                is ModuleConfirmRequest.Uninstall -> actions.onUninstallModule(request.module)
                is ModuleConfirmRequest.Update -> actions.onConfirmUpdate(request)
                null -> Unit
            }
        },
        onDismiss = actions.onDismissConfirmRequest,
    )

    fun openShortcutDialogForType(type: ShortcutType) {
        shortcutState.selectType(type)
        showShortcutDialog.value = true
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutState.updateIconUri(uri?.toString())
    }

    fun onModuleAddShortcut(module: Module, type: ShortcutType) {
        shortcutState.bindModule(module)
        openShortcutDialogForType(type)
    }

    LaunchedEffect(confirmDialogState) {
        confirmDialogState?.let {
            confirmDialog.showConfirm(
                title = it.title,
                content = it.content,
                markdown = it.markdown,
                html = it.html,
                confirm = it.confirm,
                dismiss = it.dismiss,
                restricted = it.restricted,
            )
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarJob = remember { mutableStateOf<Job?>(null) }
    ObserveAsEvents(moduleEvent) { event ->
        when (event) {
            is ModuleEffect.Toast -> {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }

            is ModuleEffect.SnackBar -> {
                // Cancel the previous reboot snackbar so a new one replaces it instead of queueing
                snackbarJob.value?.cancel()
                snackBarHost.currentSnackbarData?.dismiss()
                // Soft reboot keeps the jailbreak and still applies module changes
                val softReboot = isSoftRebootPreferred()
                snackbarJob.value = scope.launch {
                    val result = snackBarHost.showSnackbar(
                        message = event.message,
                        actionLabel = resource.getString(if (softReboot) R.string.reboot_soft else R.string.reboot),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        reboot(if (softReboot) "soft_reboot" else "")
                    }
                }
            }
        }
    }

    ExpressiveScaffold(
        topBar = {
            InlineSearchAppBar(
                title = { Text(stringResource(R.string.module)) },
                searchText = searchText,
                searchHint = stringResource(R.string.module_search_hint),
                onSearchTextChange = {
                    if (searchText.isBlank() && it.isNotBlank()) {
                        scrollBeforeSearch = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }
                    actions.onSearchTextChange(it)
                },
                onClearClick = actions.onClearSearch,
                navigationIcon = {
                    IconButton(
                        onClick = { actions.onOpenRepo() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = stringResource(id = R.string.module_repos)
                        )
                    }
                },
                actions = {
                    var showDropdown by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showDropdown = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(id = R.string.settings),
                            )
                        }
                        ModuleOverflowMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            uiState = uiState,
                            actions = actions,
                            onToggleFeedback = { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey) },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                useWallpaperSurface = sidebar,
            )
        },
        floatingActionButton = {
            if (showInstallFab) {
                val moduleInstall = stringResource(id = R.string.module_install)
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    if (activityResult.resultCode != RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = activityResult.data ?: return@rememberLauncherForActivityResult
                    val clipData = data.clipData

                    val uris = mutableListOf<Uri>()
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                        }
                    } else {
                        data.data?.let { uris.add(it) }
                    }

                    actions.onOpenFlash(uris)
                }

                SmallExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = bottomInnerPadding),
                    expanded = fabExpanded,
                    onClick = {
                        // Select the zip files to install
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        selectZipLauncher.launch(intent)
                    },
                    icon = { Icon(Icons.Filled.Add, moduleInstall) },
                    text = { Text(text = moduleInstall) },
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = {
            SnackBarHost(hostState = snackBarHost, modifier = Modifier.let {
                if (!showInstallFab) it.padding(
                    bottom =
                        bottomInnerPadding
                ) else it
            })
        }
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
            if (uiState.magiskInstalled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.module_magisk_conflict),
                        textAlign = TextAlign.Center,
                    )
                }
                return@PullToRefreshBox
            }
            val latestModuleList = rememberUpdatedState(uiState.moduleList)
            val latestRefreshing = rememberUpdatedState(uiState.isRefreshing)
            ScrollToTopOnChange(
                listState,
                uiState.sortEnabledFirst,
                uiState.sortActionFirst,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestModuleList.value }
            val isSearching = searchText.isNotBlank()
            val visibleModules = if (isSearching) uiState.searchResults else uiState.moduleList
            if (isSearching && visibleModules.isEmpty() && uiState.searchStatus.resultStatus == SearchStatus.ResultStatus.EMPTY) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.module_search_empty))
                }
            } else ModuleList(
                bottomInnerPadding = bottomInnerPadding,
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                listState = listState,
                displayModules = visibleModules,
                updateInfoMap = uiState.updateInfo,
                actions = actions,
                onClickModule = { module ->
                    if (module.hasWebUi) {
                        actions.onOpenWebUi(module)
                    }
                },
                onModuleAddShortcut = { module, type -> onModuleAddShortcut(module, type) },
            )
        }
    }

    ModuleShortcutSheet(
        show = showShortcutDialog.value,
        shortcutState = shortcutState,
        onDismiss = { showShortcutDialog.value = false },
        onPickShortcutIcon = { pickShortcutIconLauncher.launch("image/*") },
        onDeleteShortcut = {
            shortcutState.deleteShortcut(context)
            showShortcutDialog.value = false
        },
        onConfirmShortcut = {
            shortcutState.createShortcut(context)
            showShortcutDialog.value = false
        },
    )
}

@Composable
private fun ModuleList(
    bottomInnerPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    displayModules: List<Module>,
    updateInfoMap: Map<String, ModuleUpdateInfo>,
    actions: ModuleActions,
    onClickModule: (Module) -> Unit,
    onModuleAddShortcut: (Module, ShortcutType) -> Unit,
    closeSearch: () -> Unit? = {},
) {
    val loadingDialog = rememberLoadingDialog()
    val compact = LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 13.dp),
        contentPadding = PaddingValues(
            start = if (compact) 12.dp else 16.dp,
            end = if (compact) 12.dp else 16.dp,
            bottom = 16.dp + bottomInnerPadding + 56.dp + 16.dp
        ),
    ) {
        items(displayModules, key = { it.id }, contentType = { "module" }) { module ->
            val scope = rememberCoroutineScope()
            val moduleUpdateInfo = updateInfoMap[module.id] ?: ModuleUpdateInfo.Empty

            ModuleItem(
                module = module,
                updateUrl = moduleUpdateInfo.downloadUrl,
                onUninstallClicked = {
                    if (module.remove) {
                        actions.onUndoUninstallModule(module)
                    } else {
                        actions.onRequestUninstallConfirmation(module)
                    }
                },
                onCheckChanged = {
                    actions.onToggleModule(module)
                },
                onUpdate = {
                    scope.launch {
                        loadingDialog.withLoading {
                            actions.onRequestUpdateConfirmation(module, moduleUpdateInfo)
                        }
                    }
                },
                onAddShortcut = { type -> onModuleAddShortcut(module, type) },
                onClick = { onClickModule(module) },
                onExecuteAction = { actions.onExecuteModuleAction(module) },
                onEditWallpaper = { actions.onOpenWallpaperEditor(module) },
                closeSearch = { closeSearch() }
            )
        }
    }
}

@Composable
private fun ModuleShortcutSheet(
    show: Boolean,
    shortcutState: ModuleShortcutState,
    onDismiss: () -> Unit,
    onPickShortcutIcon: () -> Unit,
    onDeleteShortcut: () -> Unit,
    onConfirmShortcut: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val resources = LocalResources.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        WindowNavigationEventBridge()
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.module_shortcut_title),
                style = MaterialTheme.typography.titleLarge
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 13.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(25.dp))
            ) {
                val preview = shortcutState.previewIcon
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        modifier = Modifier.size(100.dp),
                        contentDescription = null,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = FixedScale(1.5f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPickShortcutIcon,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.module_shortcut_icon_pick)
                    )
                }
                AnimatedVisibility(
                    visible = shortcutState.iconUri != shortcutState.defaultShortcutIconUri,
                    enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                    exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    IconButton(
                        onClick = shortcutState::resetIconToDefault,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = shortcutState.name,
                onValueChange = shortcutState::updateName,
                label = { Text(stringResource(id = R.string.module_shortcut_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp)
            )
            if (shortcutState.hasExistingShortcut) {
                TextButton(
                    onClick = onDeleteShortcut,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                ) {
                    Text(stringResource(id = R.string.module_shortcut_delete))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = android.R.string.cancel))
                }
                Button(
                    onClick = onConfirmShortcut,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (shortcutState.hasExistingShortcut) {
                            stringResource(id = R.string.module_update)
                        } else {
                            stringResource(id = android.R.string.ok)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleItem(
    module: Module,
    updateUrl: String,
    onUninstallClicked: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onAddShortcut: (ShortcutType) -> Unit,
    onClick: () -> Unit,
    onExecuteAction: () -> Unit,
    onEditWallpaper: () -> Unit,
    closeSearch: () -> Unit
) {
    val wallpaperState = rememberModuleCardWallpaperState(module.id)
    val wallpaperEntry = rememberModuleCardWallpaperFrame(wallpaperState, paused = false)
    val wallpaperLoadState = rememberModuleCardWallpaperLoadState(wallpaperEntry)
    val cardBackground = rememberModuleCardBackground(module, wallpaperEntry, wallpaperLoadState)
    val compact = LocalInterfaceStyle.current == InterfaceStyle.SidebarWidget.value
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundContent = {
            ModuleCardWallpaperBackground(
                bitmap = cardBackground.bitmap,
                entry = cardBackground.customEntry,
            )
        },
    ) {
        val haptic = LocalHapticFeedback.current
        val textDecoration = if (!module.remove) null else TextDecoration.LineThrough
        val interactionSource = remember { MutableInteractionSource() }
        val indication = LocalIndication.current
        var expanded by rememberSaveable(module.id) { mutableStateOf(false) }
        var isOverflowing by remember { mutableStateOf(false) }
        val moduleVersion = stringResource(id = R.string.module_version)
        val moduleAuthor = stringResource(id = R.string.module_author)

        Column(
            modifier = Modifier
                .run {
                    if (module.hasWebUi) {
                        toggleable(
                            value = module.enabled,
                            enabled = !module.remove && module.enabled,
                            interactionSource = interactionSource,
                            role = Role.Button,
                            indication = indication,
                            onValueChange = { onClick() }
                        )
                    } else {
                        this
                    }
                }
                .padding(
                    horizontal = if (compact) 12.dp else 16.dp,
                    vertical = if (compact) 10.dp else 14.dp,
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = module.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = textDecoration,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = if (compact) 2 else Int.MAX_VALUE,
                    )

                    if (!compact) {
                        Text(
                            text = "$moduleVersion: ${module.version}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = textDecoration
                        )

                        Text(
                            text = "$moduleAuthor: ${module.author}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = textDecoration
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onEditWallpaper) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = stringResource(R.string.module_wallpaper_editor_open),
                        )
                    }
                    ExpressiveSwitch(
                        enabled = !module.update,
                        checked = module.enabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onCheckChanged(it)
                        },
                        interactionSource = if (!module.hasWebUi) interactionSource else remember { MutableInteractionSource() },
                        showThumbIcon = !compact,
                        compact = compact,
                    )
                }
            }

            if (compact) {
                Text(
                    text = "$moduleVersion: ${module.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = textDecoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$moduleAuthor: ${module.author}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = textDecoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

            Text(
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 250,
                            easing = FastOutSlowInEasing
                        )
                    )
                    .then(
                        if (isOverflowing || expanded) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { expanded = !expanded }
                        } else {
                            Modifier
                        }
                    ),
                text = module.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                maxLines = if (expanded) Int.MAX_VALUE else if (compact) 2 else 4,
                textDecoration = textDecoration,
                onTextLayout = { textLayoutResult ->
                    isOverflowing = if (expanded) {
                        textLayoutResult.lineCount > (if (compact) 2 else 4)
                    } else {
                        textLayoutResult.hasVisualOverflow
                    }
                }
            )

            if (module.metamodule) {
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    StatusTag(
                        "META",
                        modifier = Modifier.padding(bottom = 4.dp),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        backgroundColor = MaterialTheme.colorScheme.primary
                    )
                }
                module.preinitWarning()?.let {
                    StatusTag(
                        stringResource(it),
                        modifier = Modifier.padding(bottom = 4.dp),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        backgroundColor = MaterialTheme.colorScheme.errorContainer
                    )
                }
            }

            HorizontalDivider(thickness = Dp.Hairline)

            Spacer(modifier = Modifier.height(4.dp))

            val hasUpdate = updateUrl.isNotEmpty()
            val actionButtonsEnabled = !module.remove && module.enabled && !module.update
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = actionButtonsEnabled,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
                        if (module.hasActionScript) {
                            CombinedClickableButton(
                                onClick = {
                                    onExecuteAction()
                                    closeSearch()
                                },
                                onLongClick = { onAddShortcut(ShortcutType.Action) },
                                modifier = Modifier.defaultMinSize(48.dp, 48.dp),
                                shape = ButtonDefaults.filledTonalShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = if (compact) stringResource(R.string.action) else null,
                                )
                                if (!compact && !module.hasWebUi && !hasUpdate) {
                                    Text(
                                        modifier = Modifier.padding(start = 7.dp),
                                        text = stringResource(R.string.action),
                                        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    )
                                }
                            }
                        }
                        if (module.hasWebUi) {
                            CombinedClickableButton(
                                onClick = {
                                    onClick()
                                    closeSearch()
                                },
                                onLongClick = { onAddShortcut(ShortcutType.WebUI) },
                                modifier = Modifier.defaultMinSize(48.dp, 48.dp),
                                shape = ButtonDefaults.filledTonalShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = Icons.Outlined.Code,
                                    contentDescription = if (compact) stringResource(R.string.open) else null,
                                )
                                if (!compact && !module.hasActionScript && !hasUpdate) {
                                    Text(
                                        modifier = Modifier.padding(start = 7.dp),
                                        text = stringResource(R.string.open),
                                        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f, true))
                AnimatedVisibility(
                    visible = hasUpdate,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row {
                        Button(
                            modifier = Modifier.defaultMinSize(48.dp, 48.dp),
                            enabled = !module.remove,
                            onClick = onUpdate,
                            shape = ButtonDefaults.textShape,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                imageVector = Icons.Outlined.Download,
                                contentDescription = if (compact) stringResource(R.string.module_update) else null,
                            )
                            if (!compact && (!module.hasActionScript || !module.hasWebUi)) {
                                Text(
                                    modifier = Modifier.padding(start = 7.dp),
                                    text = stringResource(R.string.module_update),
                                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                )
                            }
                        }
                        if (!compact) Spacer(Modifier.width(12.dp))
                    }
                }
                FilledTonalButton(
                    modifier = Modifier.defaultMinSize(48.dp, 48.dp),
                    onClick = onUninstallClicked,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp).then(if (module.remove) Modifier.rotate(180f) else Modifier),
                        imageVector = if (module.remove) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                        contentDescription = if (compact) stringResource(if (module.remove) R.string.undo else R.string.uninstall) else null,
                    )
                    if (!compact && ((!module.hasActionScript && !module.hasWebUi) || !hasUpdate)) {
                        Text(
                            modifier = Modifier.padding(start = 7.dp),
                            text = stringResource(if (module.remove) R.string.undo else R.string.uninstall),
                            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CombinedClickableButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .semantics { role = Role.Button }
            .clip(shape)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = border,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                Modifier
                    .defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight,
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
