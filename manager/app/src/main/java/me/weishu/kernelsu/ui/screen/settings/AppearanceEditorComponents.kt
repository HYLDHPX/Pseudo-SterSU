package me.weishu.kernelsu.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.theme.LocalUiPassthroughSettings
import me.weishu.kernelsu.ui.theme.immersivePageColor
import me.weishu.kernelsu.ui.theme.immersiveTopBarColor
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.theme.withUiPassthrough
import me.weishu.kernelsu.ui.component.passthrough.MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import me.weishu.kernelsu.ui.component.AdaptiveTopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun AppearanceEditorPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val page: @Composable () -> Unit = {
        MiuixScaffold(
            containerColor = immersivePageColor(MaterialTheme.colorScheme.background),
            popupHost = {},
            contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
            topBar = {
                MiuixTopAppBar(
                    title = title,
                    color = immersiveTopBarColor(MaterialTheme.colorScheme.surface),
                    titleColor = MaterialTheme.colorScheme.onSurface,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            content(Modifier.fillMaxSize().padding(padding).padding(
                bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding(),
            ))
        }
    }
    if (LocalUiMode.current == UiMode.Material) {
        val dark = isInDarkTheme()
        val accent = MaterialTheme.colorScheme.primary
        val controller = remember(dark, accent) {
            ThemeController(
                if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                keyColor = accent,
                isDark = dark,
            )
        }
        MiuixTheme(controller = controller) {
            // Keep the bridge's current day/night palette rather than restoring the outer default.
            MiuixTheme(colors = MiuixTheme.colorScheme.withUiPassthrough(LocalUiPassthroughSettings.current)) {
                page()
            }
        }
    } else {
        page()
    }
}

@Composable
internal fun AppearanceEditorWorkspace(
    modifier: Modifier = Modifier,
    selector: @Composable () -> Unit,
    preview: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 1120.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            val twoPane = useBackgroundEditorTwoPane(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
            if (twoPane) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    selector()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                            preview()
                        }
                        Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                            controls()
                        }
                    }
                }
            } else {
                // The selector scrolls too: short windows must not lose access to controls.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    selector()
                    preview()
                    controls()
                }
            }
        }
    }
}

@Composable
internal fun AppearanceEditorTargetPicker(
    @StringRes titleRes: Int,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    if (me.weishu.kernelsu.ui.util.currentAppWindowSize().compactNavigation) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(labels.getOrNull(selectedIndex).orEmpty(), modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ExpandMore, stringResource(titleRes), Modifier.size(20.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onSelect(index) },
                        trailingIcon = if (index == selectedIndex) { { Icon(Icons.Rounded.Check, null) } } else null)
                }
            }
        }
        return
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(selectedIndex) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            listState.scrollToItem(selectedIndex)
        }
    }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(labels) { index, label ->
                AppearanceEditorChoice(label, index == selectedIndex, { onSelect(index) })
            }
        }
    }
}

@Composable
internal fun AppearanceEditorChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        modifier = Modifier.heightIn(min = 48.dp),
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
        } else null,
    )
}

@Composable
internal fun AppearanceEditorHeading(title: String, summary: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AppearanceEditorVariantPicker(night: Boolean, onSelect: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.appearance_editor_edit_variant), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceEditorChoice(stringResource(R.string.media_variant_day), !night, { onSelect(false) })
            AppearanceEditorChoice(stringResource(R.string.media_variant_night), night, { onSelect(true) })
        }
    }
}

@Composable
internal fun AppearanceEditorTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp,
        containerColor = Color.Transparent, divider = {}) {
        titles.forEachIndexed { index, title ->
            Tab(selected = index == selected, onClick = { onSelect(index) }, text = { Text(title) })
        }
    }
}

@Composable
internal fun AppearanceEditorMediaActions(
    title: String,
    hasMedia: Boolean,
    onPickImage: () -> Unit,
    onPickVideo: (() -> Unit)? = null,
    onCrop: () -> Unit,
    onPreview: () -> Unit,
    onClear: () -> Unit,
    @StringRes clearSummaryRes: Int = R.string.appearance_editor_clear_media_summary,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = onPickImage, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                Text(stringResource(R.string.background_editor_choose_image), Modifier.padding(start = 8.dp))
            }
            if (onPickVideo != null) {
                FilledTonalButton(onClick = onPickVideo, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.background_editor_choose_video), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (hasMedia) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppearanceEditorAction(stringResource(R.string.home_card_wallpaper_crop_action), Icons.Rounded.Crop, onCrop)
                AppearanceEditorAction(stringResource(R.string.home_card_wallpaper_preview_action), Icons.Rounded.Visibility, onPreview)
                AppearanceEditorAction(stringResource(R.string.home_card_wallpaper_clear_action), Icons.Rounded.Close, { confirmClear = true })
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.background_editor_clear_title, title)) },
            text = { Text(stringResource(clearSummaryRes)) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) {
                    Text(stringResource(R.string.home_card_wallpaper_clear_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
internal fun AppearanceEditorAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(text, Modifier.padding(start = 6.dp))
    }
}
