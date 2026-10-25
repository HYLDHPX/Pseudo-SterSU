package me.weishu.kernelsu.ui.screen.launchericon

import android.widget.Toast
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.weishu.kernelsu.ui.component.passthrough.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.skrootpro.skrootproSp
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.settings.ManagerNameDialog
import me.weishu.kernelsu.ui.screen.settings.SettingsWallpaperCropDialog
import me.weishu.kernelsu.ui.theme.immersivePageColor
import me.weishu.kernelsu.ui.theme.immersiveTopBarColor
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.CustomLauncherMode
import me.weishu.kernelsu.ui.util.ManagerIconShortcutResult
import me.weishu.kernelsu.ui.util.requestManagerIconShortcut
import me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight
import me.weishu.kernelsu.ui.util.LauncherIconOption
import me.weishu.kernelsu.ui.util.InstalledLauncher
import me.weishu.kernelsu.ui.util.InstalledLauncherStatus
import me.weishu.kernelsu.ui.util.shouldAutoHideInstalledLauncher
import me.weishu.kernelsu.ui.util.INSTALLED_LAUNCHER_HIDDEN
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import me.weishu.kernelsu.ui.theme.UiPassthroughTheme
import me.weishu.kernelsu.ui.util.loadCustomImageBitmap
import java.io.File
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import me.weishu.kernelsu.ui.component.AdaptiveTopAppBar as TopAppBar

private val FullImageCrop = CustomWallpaperCrop(0f, 0f, 1f, 1f)

private object LauncherIdentityColors {
    val Page: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    val SurfaceMuted: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Border: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val AccentSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val AccentText: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
    val OnAccent: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
}

@Composable
fun LauncherIconScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedIndex = LauncherIconOption.selectedIndex(uiState.launcherIcon)
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var customLauncherMode by remember {
        mutableStateOf(CustomLauncherMode.fromValue(preferences.getString(CustomLauncherMode.PREF_KEY, null)))
    }
    var customIconMode by rememberSaveable { mutableStateOf(CustomLauncherMode.Installed.value) }
    var customIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomIconCrop by rememberSaveable { mutableStateOf(false) }
    var selectingCustomIcon by rememberSaveable { mutableStateOf(false) }
    var showManagerNameDialog by remember { mutableStateOf(false) }
    var launcherStatus by remember { mutableStateOf(InstalledLauncherStatus(false, false, false)) }
    var preparing by remember { mutableStateOf(false) }
    var showHideConfirmation by remember { mutableStateOf(false) }
    var pendingInstallPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingInstallPreviousUpdateTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var statusRefreshJob by remember { mutableStateOf<Job?>(null) }
    val customIconFailedMessage = stringResource(R.string.settings_app_icon_custom_failed)
    val defaultManagerName = stringResource(R.string.app_name)
    fun refreshEntry() {
        statusRefreshJob?.cancel()
        statusRefreshJob = scope.launch {
            launcherStatus = withContext(Dispatchers.IO) { InstalledLauncher.status(context) }
        }
    }
    fun showError(error: Throwable) {
        Toast.makeText(context, error.localizedMessage ?: customIconFailedMessage, Toast.LENGTH_LONG).show()
    }
    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val wasInstalling = pendingInstallPath != null
        val previousUpdateTime = pendingInstallPreviousUpdateTime
        pendingInstallPath = null
        pendingInstallPreviousUpdateTime = null
        refreshEntry() // Installer result codes are not reliable on all OEM installers.
        if (wasInstalling) {
            scope.launch {
                try {
                    val installed = withContext(Dispatchers.IO) { InstalledLauncher.status(context) }
                    if (shouldAutoHideInstalledLauncher(previousUpdateTime, installed)) {
                        InstalledLauncher.verifyAndHide(context)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    showError(error)
                }
            }
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pendingInstallPath?.let(::File)
        if (context.packageManager.canRequestPackageInstalls() && file?.isFile == true) {
            runCatching { installLauncher.launch(InstalledLauncher.installationIntent(context, file)) }.onFailure {
                pendingInstallPath = null
                pendingInstallPreviousUpdateTime = null
                showError(it)
            }
        } else {
            pendingInstallPath = null
            pendingInstallPreviousUpdateTime = null
            Toast.makeText(context, R.string.launcher_install_permission, Toast.LENGTH_LONG).show()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshEntry() }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == INSTALLED_LAUNCHER_HIDDEN) refreshEntry()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val customIconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        selectingCustomIcon = false
        uri ?: return@rememberLauncherForActivityResult
        customIconUri = uri.toString()
        showCustomIconCrop = true
    }
    val pickCustomIcon = dropUnlessResumed {
        if (!preparing && !selectingCustomIcon && !showCustomIconCrop && pendingInstallPath == null) {
            customIconMode = customLauncherMode.value
            selectingCustomIcon = true
            runCatching {
                customIconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.onFailure {
                selectingCustomIcon = false
                showError(it)
            }
        }
    }
    LauncherIconScreenMiuix(
        selectedIndex = if (launcherStatus.hidden) -1 else selectedIndex,
        customManagerName = uiState.customManagerName,
        defaultManagerName = defaultManagerName,
        onBack = dropUnlessResumed { navigator.pop() },
        onEditManagerName = { showManagerNameDialog = true },
        onSelect = {
            viewModel.setLauncherIconByIndex(it)
            refreshEntry()
        },
        onPickCustomIcon = pickCustomIcon,
        customLauncherMode = customLauncherMode,
        customIconBusy = preparing || selectingCustomIcon || showCustomIconCrop || pendingInstallPath != null,
        onCustomLauncherModeChange = { mode ->
            if (preferences.edit().putString(CustomLauncherMode.PREF_KEY, mode.value).commit()) {
                customLauncherMode = mode
            } else {
                Toast.makeText(context, customIconFailedMessage, Toast.LENGTH_LONG).show()
            }
        },
        preparing = preparing,
        launcherStatus = launcherStatus,
        onVerifyAndHide = { showHideConfirmation = true },
        onRestoreEntry = {
            if (!InstalledLauncher.restore(context)) {
                Toast.makeText(context, R.string.launcher_install_verify_failed, Toast.LENGTH_LONG).show()
            }
            refreshEntry()
        },
    )
    SettingsWallpaperCropDialog(
        show = showCustomIconCrop,
        uriString = customIconUri,
        crop = FullImageCrop,
        onCropChange = createEntry@{ crop ->
            if (preparing) return@createEntry
            val uri = customIconUri ?: return@createEntry
            val creationMode = CustomLauncherMode.fromValue(customIconMode)
            preparing = true
            showCustomIconCrop = false
            scope.launch {
                try {
                    val managerName = uiState.customManagerName.ifBlank { defaultManagerName }
                    val bitmap = withContext(Dispatchers.IO) {
                        checkNotNull(loadCustomImageBitmap(context, uri, maxSide = 512, crop = crop)) {
                            customIconFailedMessage
                        }
                    }
                    try {
                        if (creationMode == CustomLauncherMode.Shortcut) {
                            val result = withContext(Dispatchers.IO) {
                                requestManagerIconShortcut(context, bitmap, managerName)
                            }
                            val message = when (result) {
                                ManagerIconShortcutResult.Requested -> R.string.launcher_shortcut_requested
                                ManagerIconShortcutResult.Updated -> R.string.settings_app_icon_custom_updated
                                ManagerIconShortcutResult.Unsupported -> R.string.module_shortcut_not_supported
                                ManagerIconShortcutResult.Failed -> R.string.launcher_shortcut_failed
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            refreshEntry()
                        } else {
                            val (file, previousUpdateTime) = withContext(Dispatchers.IO) {
                                val previousUpdateTime = InstalledLauncher.status(context).lastUpdateTime
                                InstalledLauncher.create(context, bitmap, managerName) to previousUpdateTime
                            }
                            pendingInstallPath = file.absolutePath
                            pendingInstallPreviousUpdateTime = previousUpdateTime
                            if (context.packageManager.canRequestPackageInstalls()) {
                                installLauncher.launch(InstalledLauncher.installationIntent(context, file))
                            } else {
                                installPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    "package:${context.packageName}".toUri()))
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    pendingInstallPath = null
                    pendingInstallPreviousUpdateTime = null
                    showError(error)
                } finally {
                    preparing = false
                    customIconUri = null
                }
            }
        },
        onDismissRequest = {
            showCustomIconCrop = false
            customIconUri = null
        },
        title = stringResource(R.string.settings_app_icon_custom_crop),
        emptyText = stringResource(R.string.settings_app_icon_custom_empty),
        editorAspectRatio = 1f,
        cropAspectRatio = 1f,
        defaultCrop = FullImageCrop,
    )
    ManagerNameDialog(
        show = showManagerNameDialog,
        initialName = uiState.customManagerName,
        initialSyncLkmCard = uiState.syncManagerNameToLkmCard,
        initialSyncSettingsHeader = uiState.syncManagerNameToSettingsHeader,
        onDismissRequest = { showManagerNameDialog = false },
        onConfirm = viewModel::setCustomManagerName,
    )
    if (showHideConfirmation) {
        UiPassthroughTheme(UiPassthroughSettings()) {
            AlertDialog(
                onDismissRequest = { showHideConfirmation = false },
                title = { Text(stringResource(R.string.launcher_install_hide)) },
                text = {
                    Text(stringResource(R.string.launcher_install_hide_warning),
                        modifier = Modifier.heightIn(max = adaptiveDialogContentMaxHeight())
                            .verticalScroll(rememberScrollState()))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showHideConfirmation = false
                        runCatching { InstalledLauncher.verifyAndHide(context) }.onFailure(::showError)
                    }) { Text(stringResource(R.string.launcher_install_verify)) }
                },
                dismissButton = {
                    TextButton(onClick = { showHideConfirmation = false }) { Text(stringResource(android.R.string.cancel)) }
                },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            )
        }
    }
}

@Composable
private fun LauncherIconScreenMiuix(
    selectedIndex: Int,
    customManagerName: String,
    defaultManagerName: String,
    onBack: () -> Unit,
    onEditManagerName: () -> Unit,
    onSelect: (Int) -> Unit,
    onPickCustomIcon: () -> Unit,
    customLauncherMode: CustomLauncherMode,
    customIconBusy: Boolean,
    onCustomLauncherModeChange: (CustomLauncherMode) -> Unit,
    preparing: Boolean,
    launcherStatus: InstalledLauncherStatus,
    onVerifyAndHide: () -> Unit,
    onRestoreEntry: () -> Unit,
) {
    Scaffold(
        containerColor = immersivePageColor(LauncherIdentityColors.Page),
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_manager_identity),
                color = immersiveTopBarColor(LauncherIdentityColors.Page),
                titleColor = LauncherIdentityColors.Ink,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            tint = LauncherIdentityColors.Ink,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LauncherIconPickerContent(
            selectedIndex = selectedIndex,
            customManagerName = customManagerName,
            defaultManagerName = defaultManagerName,
            onEditManagerName = onEditManagerName,
            onSelect = onSelect,
            onPickCustomIcon = onPickCustomIcon,
            customLauncherMode = customLauncherMode,
            customIconBusy = customIconBusy,
            onCustomLauncherModeChange = onCustomLauncherModeChange,
            preparing = preparing,
            launcherStatus = launcherStatus,
            onVerifyAndHide = onVerifyAndHide,
            onRestoreEntry = onRestoreEntry,
            onRestore = { onSelect(0) },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun LauncherIconPickerContent(
    selectedIndex: Int,
    customManagerName: String,
    defaultManagerName: String,
    onEditManagerName: () -> Unit,
    onSelect: (Int) -> Unit,
    onPickCustomIcon: () -> Unit,
    customLauncherMode: CustomLauncherMode,
    customIconBusy: Boolean,
    onCustomLauncherModeChange: (CustomLauncherMode) -> Unit,
    preparing: Boolean,
    launcherStatus: InstalledLauncherStatus,
    onVerifyAndHide: () -> Unit,
    onRestoreEntry: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(immersivePageColor(LauncherIdentityColors.Page)),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
        columns = GridCells.Fixed(launcherIconColumnCount(maxWidth)),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_manager_name),
                    color = LauncherIdentityColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = skrootproSp(18f, maxScale = 1f),
                )
                ManagerNameCard(
                    customManagerName = customManagerName,
                    defaultManagerName = defaultManagerName,
                    onClick = onEditManagerName,
                )
                Text(
                    text = stringResource(R.string.settings_app_icon_picker_header),
                    color = LauncherIdentityColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = skrootproSp(18f, maxScale = 1f),
                )
                Text(
                    text = stringResource(R.string.settings_app_icon_picker_hint),
                    color = LauncherIdentityColors.Muted,
                    fontSize = skrootproSp(13f, maxScale = 1f),
                    lineHeight = skrootproSp(18f, maxScale = 1f),
                )
            }
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedColumn(title = stringResource(R.string.launcher_custom_mode)) {
                    item {
                        SegmentedRadioItem(
                            title = stringResource(R.string.launcher_mode_installed),
                            summary = stringResource(R.string.launcher_mode_installed_summary),
                            selected = customLauncherMode == CustomLauncherMode.Installed,
                            enabled = !customIconBusy,
                            onClick = { onCustomLauncherModeChange(CustomLauncherMode.Installed) },
                        )
                    }
                    item {
                        SegmentedRadioItem(
                            title = stringResource(R.string.launcher_mode_shortcut),
                            summary = stringResource(R.string.launcher_mode_shortcut_summary),
                            selected = customLauncherMode == CustomLauncherMode.Shortcut,
                            enabled = !customIconBusy,
                            onClick = { onCustomLauncherModeChange(CustomLauncherMode.Shortcut) },
                        )
                    }
                }
                LauncherIconCustomCard(
                    onClick = onPickCustomIcon,
                    preparing = preparing,
                    enabled = !customIconBusy,
                    mode = customLauncherMode,
                )
                if (customLauncherMode == CustomLauncherMode.Installed) {
                    val statusText = when {
                        launcherStatus.hidden -> R.string.launcher_install_hidden
                        launcherStatus.trusted -> R.string.launcher_install_ready
                        launcherStatus.installed -> R.string.launcher_install_conflict
                        else -> R.string.launcher_install_not_ready
                    }
                    Text(stringResource(statusText), color = LauncherIdentityColors.Muted,
                        modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall)
                    if (launcherStatus.trusted && !launcherStatus.hidden) {
                        TextButton(onClick = onVerifyAndHide, enabled = !customIconBusy) {
                            Text(stringResource(R.string.launcher_install_hide))
                        }
                    }
                }
                if (launcherStatus.hidden) {
                    TextButton(onClick = onRestoreEntry, enabled = !customIconBusy) {
                        Text(stringResource(R.string.launcher_install_restore))
                    }
                }
            }
        }

        itemsIndexed(LauncherIconOption.entries) { index, option ->
            LauncherIconOptionCard(
                option = option,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
            )
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(LauncherIdentityColors.Surface)
                        .border(1.dp, LauncherIdentityColors.Border, RoundedCornerShape(18.dp))
                        .clickable(onClick = onRestore)
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.settings_app_icon_restore),
                            color = LauncherIdentityColors.Ink,
                            fontSize = skrootproSp(17f, maxScale = 1f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings_app_icon_restore_summary),
                            color = LauncherIdentityColors.Muted,
                            fontSize = skrootproSp(14f, maxScale = 1f),
                        )
                    }
                }
                Text(
                    text = stringResource(if (customLauncherMode == CustomLauncherMode.Installed) {
                        R.string.launcher_install_help
                    } else {
                        R.string.launcher_shortcut_help
                    }),
                    color = LauncherIdentityColors.Muted,
                    fontSize = skrootproSp(12.5f, maxScale = 1f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        }
    }
}

internal fun launcherIconColumnCount(width: Dp): Int = when {
    width < 280.dp -> 2
    width < 360.dp -> 3
    width < 600.dp -> 4
    width < 840.dp -> 5
    else -> 6
}

@Composable
private fun LauncherIconCustomCard(
    onClick: () -> Unit,
    preparing: Boolean,
    enabled: Boolean,
    mode: CustomLauncherMode,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LauncherIdentityColors.Surface)
            .border(1.dp, LauncherIdentityColors.Border, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LauncherIdentityColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AddPhotoAlternate,
                contentDescription = null,
                tint = LauncherIdentityColors.AccentText,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(if (mode == CustomLauncherMode.Installed) {
                    R.string.launcher_install_title
                } else {
                    R.string.settings_app_icon_custom
                }),
                color = LauncherIdentityColors.Ink,
                fontSize = skrootproSp(16f, maxScale = 1f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(when {
                    preparing && mode == CustomLauncherMode.Installed -> R.string.launcher_install_preparing
                    preparing -> R.string.launcher_shortcut_preparing
                    mode == CustomLauncherMode.Installed -> R.string.launcher_install_summary
                    else -> R.string.launcher_shortcut_summary
                }),
                color = LauncherIdentityColors.Muted,
                fontSize = skrootproSp(13f, maxScale = 1f),
            )
        }
    }
}

@Composable
private fun ManagerNameCard(
    customManagerName: String,
    defaultManagerName: String,
    onClick: () -> Unit,
) {
    val displayName = customManagerName.ifBlank { defaultManagerName }
    val summary = if (customManagerName.isBlank()) {
        stringResource(R.string.settings_manager_name_default_summary, defaultManagerName)
    } else {
        stringResource(R.string.settings_manager_name_custom_summary, customManagerName)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LauncherIdentityColors.Surface)
            .border(1.dp, LauncherIdentityColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(LauncherIdentityColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayName.take(1).ifBlank { "A" },
                    color = LauncherIdentityColors.AccentText,
                    fontSize = skrootproSp(22f, maxScale = 1f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayName,
                    color = LauncherIdentityColors.Ink,
                    fontSize = skrootproSp(17f, maxScale = 1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    color = LauncherIdentityColors.Muted,
                    fontSize = skrootproSp(12.5f, maxScale = 1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.settings_manager_name_edit),
                color = LauncherIdentityColors.Accent,
                fontSize = skrootproSp(13f, maxScale = 1f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LauncherIconOptionCard(
    option: LauncherIconOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val cardColor = if (selected) LauncherIdentityColors.AccentSoft else LauncherIdentityColors.Surface
    val accentColor = if (selected) LauncherIdentityColors.Accent else LauncherIdentityColors.Border

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(shape)
            .background(cardColor)
            .border(if (selected) 2.dp else 1.dp, accentColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .border(
                        width = if (selected) 2.5.dp else 1.5.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(4.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(LauncherIdentityColors.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(option.foregroundRes),
                    contentDescription = stringResource(option.labelRes),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(option.labelRes),
                    color = if (selected) LauncherIdentityColors.AccentText else LauncherIdentityColors.Ink,
                    fontSize = skrootproSp(13.5f, maxScale = 1f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(LauncherIdentityColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.settings_app_icon_picker_selected),
                    tint = LauncherIdentityColors.OnAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
