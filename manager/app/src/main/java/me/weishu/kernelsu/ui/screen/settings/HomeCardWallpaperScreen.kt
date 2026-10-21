package me.weishu.kernelsu.ui.screen.settings

import android.graphics.Bitmap
import me.weishu.kernelsu.ui.component.passthrough.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.rememberCustomVideoFrameBitmap
import me.weishu.kernelsu.ui.component.MediaVisualLayer
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.home.HomeMetricCardWallpaperBackground
import me.weishu.kernelsu.ui.screen.home.HomeMetricCardWallpaperState
import me.weishu.kernelsu.ui.screen.home.HomeMetricCardWallpaperTarget
import me.weishu.kernelsu.ui.screen.home.rememberHomeMetricCardWallpaperBitmap
import me.weishu.kernelsu.ui.screen.home.rememberHomeMetricCardWallpaperState
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

private const val MIUIX_LKM_CARD_WALLPAPER_ASPECT_RATIO = 1.86f

@Composable
fun HomeCardWallpaperScreen() {
    val navigator = LocalNavigator.current
    val onBack = dropUnlessResumed { navigator.pop() }
    AppearanceEditorPage(stringResource(R.string.home_card_wallpapers), onBack) { modifier ->
        HomeCardWallpaperContent(modifier)
    }
}

@Composable
private fun HomeCardWallpaperContent(modifier: Modifier) {
    var targetName by rememberSaveable { mutableStateOf(HomeMetricCardWallpaperTarget.Lkm.name) }
    val target = HomeMetricCardWallpaperTarget.entries.firstOrNull { it.name == targetName }
        ?: HomeMetricCardWallpaperTarget.Lkm
    val targets = HomeMetricCardWallpaperTarget.entries
    val selector: @Composable () -> Unit = {
        AppearanceEditorTargetPicker(
            titleRes = R.string.appearance_editor_choose_card,
            labels = targets.map { stringResource(it.titleRes) },
            selectedIndex = targets.indexOf(target),
            onSelect = { targetName = targets[it].name },
        )
    }
    androidx.compose.runtime.key(target.name) {
        HomeCardWallpaperEditor(target, modifier, selector)
    }
}

@Composable
private fun HomeCardWallpaperEditor(
    target: HomeMetricCardWallpaperTarget,
    modifier: Modifier,
    selector: @Composable () -> Unit,
) {
    var editNight by rememberSaveable { mutableStateOf(false) }
    var showCrop by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showFullPreview by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(0) }
    val title = stringResource(target.titleRes)
    val aspectRatio = homeCardWallpaperAspectRatio(target)
    val state = rememberHomeMetricCardWallpaperState(
        target = target,
        onWallpaperSelected = { showCrop = true },
        forceNight = editNight,
    )
    val bitmap = androidx.compose.runtime.key(editNight, state.uriString, state.crop) {
        rememberHomeMetricCardWallpaperBitmap(state.uriString, state.crop)
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val videoFrameBitmap = androidx.compose.runtime.key(editNight, state.videoUriString) {
        rememberCustomVideoFrameBitmap(state.videoUriString)
    }

    AppearanceEditorWorkspace(
        modifier = modifier,
        selector = selector,
        preview = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceEditorHeading(
                    title,
                    stringResource(when (target) {
                        HomeMetricCardWallpaperTarget.Lkm,
                        HomeMetricCardWallpaperTarget.ClassicMiuixLkm,
                        HomeMetricCardWallpaperTarget.MaterialLkm -> R.string.home_card_wallpapers_main_section_summary
                        HomeMetricCardWallpaperTarget.Superuser,
                        HomeMetricCardWallpaperTarget.Module -> R.string.home_card_wallpapers_metric_section_summary
                        else -> R.string.home_card_wallpapers_system_section_summary
                    }),
                )
                AppearanceEditorVariantPicker(editNight) {
                    editNight = it
                    showCrop = false
                    showPreview = false
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                        HomeCardWallpaperFrame(
                            target, title, imageBitmap, videoFrameBitmap,
                            state.hasSelectedAnyWallpaper, aspectRatio, state.visualSettings,
                        )
                    }
                }
                Text(stringResource(homeCardWallpaperSummaryRes(state)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MediaFileInfoSummary(rememberMediaFileInfo(state.uriString ?: state.videoUriString))
                androidx.compose.runtime.key(editNight) {
                    AppearanceEditorMediaActions(
                        title = title,
                        hasMedia = state.hasSelectedAnyWallpaper,
                        onPickImage = state.onPickWallpaper,
                        onPickVideo = state.onPickVideoWallpaper,
                        onCrop = { showCrop = true },
                        onPreview = { showPreview = true },
                        onClear = state.onClearWallpaper,
                    )
                }
                AppearanceEditorAction(
                    stringResource(R.string.home_card_wallpaper_full_preview),
                    Icons.Rounded.Visibility,
                    { showFullPreview = true },
                )
            }
        },
        controls = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceEditorTabs(
                    listOf(stringResource(R.string.background_editor_effects), stringResource(R.string.appearance_editor_rotation)),
                    section, { section = it },
                )
                if (section == 0) {
                    if (state.hasSelectedAnyWallpaper) {
                        MediaVisualControls(
                            value = state.visualSettings,
                            onValueChange = state.onVisualSettingsChange,
                            showContrast = true,
                            showMotion = true,
                        )
                        Text(stringResource(R.string.media_editor_responsive_crops),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.background_editor_choose_hint),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    MediaVariantControls(value = state.variantSettings, onValueChange = state.onVariantSettingsChange)
                }
                Text(stringResource(R.string.appearance_editor_save_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
    SettingsWallpaperCropDialog(
        show = showCrop && state.hasSelectedAnyWallpaper,
        uriString = state.uriString ?: state.videoUriString,
        crop = state.crop,
        onCropChange = { state.onCropChange(it); showPreview = true },
        onDismissRequest = { showCrop = false },
        title = stringResource(target.cropLabelRes),
        editorAspectRatio = aspectRatio,
        cropAspectRatio = aspectRatio,
        previewBitmap = videoFrameBitmap.takeIf { state.hasSelectedVideoWallpaper },
        transform = state.visualSettings.transform,
        onTransformChange = { state.onVisualSettingsChange(state.visualSettings.copy(transform = it)) },
        onGenerateResponsiveCrops = state.onResponsiveCropsChange,
    )
    HomeCardWallpaperPreviewDialog(
        show = showPreview && state.hasSelectedAnyWallpaper,
        title = title,
        target = target,
        bitmap = bitmap,
        videoUriString = state.videoUriString,
        crop = state.crop,
        aspectRatio = aspectRatio,
        visualSettings = state.visualSettings,
        onDismissRequest = { showPreview = false },
    )
    HomeCardWallpaperPagePreviewDialog(showFullPreview, { showFullPreview = false })
}

@Composable
private fun HomeCardWallpaperFrame(
    target: HomeMetricCardWallpaperTarget,
    title: String,
    imageBitmap: ImageBitmap?,
    videoFrameBitmap: ImageBitmap?,
    hasMedia: Boolean,
    aspectRatio: Float,
    visualSettings: me.weishu.kernelsu.ui.util.MediaVisualSettings,
) {
    val frameBitmap = imageBitmap ?: videoFrameBitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            frameBitmap != null -> MediaVisualLayer(
                settings = visualSettings,
                modifier = Modifier.fillMaxSize(),
            ) { colorFilter ->
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = frameBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                )
            }

            hasMedia -> CircularProgressIndicator()

            else -> Text(
                modifier = Modifier.padding(20.dp),
                text = stringResource(R.string.home_card_wallpaper_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (frameBitmap != null) {
            HomeCardWallpaperSampleContent(
                target = target,
                fallbackTitle = title,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun HomeCardWallpaperSampleContent(
    target: HomeMetricCardWallpaperTarget,
    fallbackTitle: String,
    modifier: Modifier = Modifier,
) {
    val primaryColor = Color.White
    val secondaryColor = Color.White.copy(alpha = 0.82f)
    val accentColor = Color.White
    Box(
        modifier = modifier.padding(14.dp),
    ) {
        when (target) {
            HomeMetricCardWallpaperTarget.Lkm,
            HomeMetricCardWallpaperTarget.ClassicMiuixLkm,
            HomeMetricCardWallpaperTarget.MaterialLkm -> HomeCardWallpaperStatusSample(
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                accentColor = accentColor,
            )

            HomeMetricCardWallpaperTarget.Superuser,
            HomeMetricCardWallpaperTarget.Module -> HomeCardWallpaperMetricSample(
                target = target,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                accentColor = accentColor,
            )

            HomeMetricCardWallpaperTarget.StatusMonitor -> HomeCardWallpaperStatusMonitorSample(
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                accentColor = accentColor,
            )

            HomeMetricCardWallpaperTarget.SystemInfo -> HomeCardWallpaperSystemInfoSample(
                title = fallbackTitle,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
            )

            HomeMetricCardWallpaperTarget.RebootMenu -> HomeCardWallpaperRebootMenuSample(
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun HomeCardWallpaperStatusSample(
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            modifier = Modifier
                .size(112.dp)
                .align(Alignment.BottomEnd),
            imageVector = Icons.Rounded.CheckCircleOutline,
            tint = accentColor.copy(alpha = 0.18f),
            contentDescription = null,
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircleOutline,
                    tint = accentColor,
                    contentDescription = null,
                )
            }
            Text(
                text = stringResource(R.string.home_working),
                color = primaryColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HomeCardWallpaperPreviewTag(label = "LKM")
            Text(
                text = stringResource(R.string.home_working_version, "12345-6"),
                color = secondaryColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeCardWallpaperMetricSample(
    target: HomeMetricCardWallpaperTarget,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
) {
    val titleRes = when (target) {
        HomeMetricCardWallpaperTarget.Superuser -> R.string.superuser
        HomeMetricCardWallpaperTarget.Module -> R.string.module
        else -> target.titleRes
    }
    val value = when (target) {
        HomeMetricCardWallpaperTarget.Superuser -> "5"
        HomeMetricCardWallpaperTarget.Module -> "10"
        else -> "0"
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = homeCardWallpaperTargetIcon(target),
                    tint = accentColor,
                    contentDescription = null,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    color = secondaryColor,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    color = primaryColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeCardWallpaperStatusMonitorSample(
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeCardWallpaperStatusLineSample(
            icon = Icons.Rounded.Security,
            label = stringResource(R.string.home_selinux_status),
            value = stringResource(R.string.selinux_status_enforcing),
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            accentColor = accentColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.20f)),
        )
        HomeCardWallpaperStatusLineSample(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.home_seccomp_status),
            value = stringResource(R.string.seccomp_status_filter),
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun HomeCardWallpaperStatusLineSample(
    icon: ImageVector,
    label: String,
    value: String,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            tint = accentColor,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = secondaryColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = primaryColor,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(accentColor, CircleShape),
        )
    }
}

@Composable
private fun HomeCardWallpaperSystemInfoSample(
    title: String,
    primaryColor: Color,
    secondaryColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = primaryColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HomeCardWallpaperInfoLineSample(
            label = stringResource(R.string.home_manager_version),
            value = "v1.0.0",
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
        )
        HomeCardWallpaperInfoLineSample(
            label = stringResource(R.string.home_kernel),
            value = "5.10.0",
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
        )
    }
}

@Composable
private fun HomeCardWallpaperRebootMenuSample(
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
) {
    val options = listOf(
        R.string.reboot,
        R.string.reboot_soft,
        R.string.reboot_recovery,
        R.string.reboot_bootloader,
        R.string.reboot_download,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = Icons.Rounded.PowerSettingsNew,
                tint = accentColor,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.home_card_reboot_menu),
                color = primaryColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        options.forEach { labelRes ->
            Text(
                text = stringResource(labelRes),
                color = secondaryColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeCardWallpaperInfoLineSample(
    label: String,
    value: String,
    primaryColor: Color,
    secondaryColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = secondaryColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = primaryColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeCardWallpaperPreviewTag(
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.18f),
        contentColor = Color.White,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun homeCardWallpaperTargetIcon(
    target: HomeMetricCardWallpaperTarget,
): ImageVector = when (target) {
    HomeMetricCardWallpaperTarget.Lkm,
    HomeMetricCardWallpaperTarget.ClassicMiuixLkm,
    HomeMetricCardWallpaperTarget.MaterialLkm -> Icons.Rounded.CheckCircleOutline
    HomeMetricCardWallpaperTarget.Superuser -> Icons.Rounded.Shield
    HomeMetricCardWallpaperTarget.Module -> Icons.Rounded.Extension
    HomeMetricCardWallpaperTarget.StatusMonitor -> Icons.Rounded.Security
    HomeMetricCardWallpaperTarget.SystemInfo -> Icons.Rounded.Info
    HomeMetricCardWallpaperTarget.RebootMenu -> Icons.Rounded.PowerSettingsNew
}


@Composable
private fun HomeCardWallpaperPreviewDialog(
    show: Boolean,
    title: String,
    target: HomeMetricCardWallpaperTarget,
    bitmap: Bitmap?,
    videoUriString: String?,
    crop: CustomWallpaperCrop,
    aspectRatio: Float,
    visualSettings: me.weishu.kernelsu.ui.util.MediaVisualSettings,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = stringResource(target.previewLabelRes),
        onDismissRequest = onDismissRequest,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeCardWallpaperLivePreviewFrame(
                    target = target,
                    title = title,
                    bitmap = bitmap,
                    videoUriString = videoUriString,
                    crop = crop,
                    aspectRatio = aspectRatio,
                    visualSettings = visualSettings,
                )
                MiuixTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(android.R.string.ok),
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun HomeCardWallpaperLivePreviewFrame(
    target: HomeMetricCardWallpaperTarget,
    title: String,
    bitmap: Bitmap?,
    videoUriString: String?,
    crop: CustomWallpaperCrop,
    aspectRatio: Float,
    visualSettings: me.weishu.kernelsu.ui.util.MediaVisualSettings,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        HomeMetricCardWallpaperBackground(
            bitmap = bitmap,
            videoUriString = videoUriString,
            videoCrop = crop,
            visualSettings = visualSettings,
        )
        if (bitmap == null && videoUriString.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.home_card_wallpaper_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HomeCardWallpaperSampleContent(
                target = target,
                fallbackTitle = title,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun HomeCardWallpaperPagePreviewDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = stringResource(R.string.home_card_wallpaper_full_preview),
        onDismissRequest = onDismissRequest,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "SterSU",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    HomeCardWallpaperMappedPreview(HomeMetricCardWallpaperTarget.Lkm)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        HomeCardWallpaperMappedPreview(
                            target = HomeMetricCardWallpaperTarget.ClassicMiuixLkm,
                            modifier = Modifier.width(164.dp),
                        )
                    }
                    HomeCardWallpaperMappedPreview(HomeMetricCardWallpaperTarget.MaterialLkm)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeCardWallpaperMappedPreview(
                            target = HomeMetricCardWallpaperTarget.Superuser,
                            modifier = Modifier.weight(1f),
                        )
                        HomeCardWallpaperMappedPreview(
                            target = HomeMetricCardWallpaperTarget.Module,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HomeCardWallpaperMappedPreview(HomeMetricCardWallpaperTarget.StatusMonitor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeCardWallpaperMappedPreview(
                            target = HomeMetricCardWallpaperTarget.SystemInfo,
                            modifier = Modifier.weight(1f),
                        )
                        HomeCardWallpaperMappedPreview(
                            target = HomeMetricCardWallpaperTarget.RebootMenu,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MiuixTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(android.R.string.ok),
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun HomeCardWallpaperMappedPreview(
    target: HomeMetricCardWallpaperTarget,
    modifier: Modifier = Modifier,
) {
    val state = rememberHomeMetricCardWallpaperState(target = target, onWallpaperSelected = {})
    val bitmap = rememberHomeMetricCardWallpaperBitmap(state.uriString, state.crop)
    val ratio = homeCardWallpaperAspectRatio(target).coerceIn(0.72f, 3.6f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        HomeMetricCardWallpaperBackground(
            bitmap = bitmap,
            videoUriString = state.videoUriString,
            videoCrop = state.crop,
            visualSettings = state.visualSettings,
        )
        if (!state.hasSelectedAnyWallpaper) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f))
            )
        }
        HomeCardWallpaperSampleContent(
            target = target,
            fallbackTitle = stringResource(target.titleRes),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun homeCardWallpaperAspectRatio(
    target: HomeMetricCardWallpaperTarget,
): Float {
    return if (target == HomeMetricCardWallpaperTarget.Lkm) {
        MIUIX_LKM_CARD_WALLPAPER_ASPECT_RATIO
    } else {
        target.aspectRatio
    }
}

private fun homeCardWallpaperSummaryRes(
    state: HomeMetricCardWallpaperState,
): Int {
    return when {
        state.hasSelectedVideoWallpaper -> R.string.home_card_wallpaper_video_selected
        state.hasSelectedWallpaper -> R.string.home_card_wallpaper_image_selected
        else -> R.string.home_card_wallpaper_empty
    }
}
