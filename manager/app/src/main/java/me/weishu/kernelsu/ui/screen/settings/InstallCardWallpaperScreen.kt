package me.weishu.kernelsu.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import me.weishu.kernelsu.ui.component.passthrough.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.rememberCustomImageBitmap
import me.weishu.kernelsu.ui.component.rememberCustomVideoFrameBitmap
import me.weishu.kernelsu.ui.component.MediaVisualLayer
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.screen.install.InstallCardWallpaperBackground
import me.weishu.kernelsu.ui.screen.install.installCardWallpaperOverlayColor
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.DEFAULT_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.MediaVariantSettings
import me.weishu.kernelsu.ui.util.ThemeStoreImageSlot
import me.weishu.kernelsu.ui.util.ThemeStoreImageState
import me.weishu.kernelsu.ui.util.persistCustomImageReference
import me.weishu.kernelsu.ui.util.generateResponsiveCrops
import me.weishu.kernelsu.ui.util.inspectMediaFile
import me.weishu.kernelsu.ui.util.rememberThemeStoreImageState
import me.weishu.kernelsu.ui.util.sanitizeCustomWallpaperCrop
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlot
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotCrop
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotNightCrop
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotNightMedia
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotResponsiveCrops
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotVariantSettings
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotVisualSettings
import me.weishu.kernelsu.ui.util.setThemeStoreImageSlotVideo
import me.weishu.kernelsu.ui.util.takePersistableImageReadPermission
import me.weishu.kernelsu.ui.util.takePersistableVideoBackgroundReadPermission
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

private data class InstallCardWallpaperSpec(
    val slot: ThemeStoreImageSlot,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val previewAspectRatio: Float,
)

private val installCardWallpaperSpecs = listOf(
    InstallCardWallpaperSpec(
        slot = ThemeStoreImageSlot.InstallImage,
        titleRes = R.string.install_card_wallpaper_image_title,
        summaryRes = R.string.install_card_wallpaper_image_summary,
        previewAspectRatio = 1.62f,
    ),
    InstallCardWallpaperSpec(
        slot = ThemeStoreImageSlot.InstallMethods,
        titleRes = R.string.install_card_wallpaper_methods_title,
        summaryRes = R.string.install_card_wallpaper_methods_summary,
        previewAspectRatio = 1.48f,
    ),
    InstallCardWallpaperSpec(
        slot = ThemeStoreImageSlot.InstallOptions,
        titleRes = R.string.install_card_wallpaper_options_title,
        summaryRes = R.string.install_card_wallpaper_options_summary,
        previewAspectRatio = 1.36f,
    ),
)

@Composable
fun InstallCardWallpaperScreen() {
    val navigator = LocalNavigator.current
    val onBack = dropUnlessResumed { navigator.pop() }
    AppearanceEditorPage(stringResource(R.string.install_card_wallpapers), onBack) { modifier ->
        var selectedId by rememberSaveable { mutableStateOf(ThemeStoreImageSlot.InstallImage.id) }
        val spec = installCardWallpaperSpecs.firstOrNull { it.slot.id == selectedId }
            ?: installCardWallpaperSpecs.first()
        val selector: @Composable () -> Unit = {
            AppearanceEditorTargetPicker(
                titleRes = R.string.appearance_editor_choose_card,
                labels = installCardWallpaperSpecs.map { stringResource(it.titleRes) },
                selectedIndex = installCardWallpaperSpecs.indexOf(spec),
                onSelect = { selectedId = installCardWallpaperSpecs[it].slot.id },
            )
        }
        androidx.compose.runtime.key(spec.slot.id) {
            InstallCardWallpaperEditor(spec, modifier, selector)
        }
    }
}

internal fun installCardWallpaperEditingState(
    stored: ThemeStoreImageState,
    night: Boolean,
    aspectRatio: Float,
): ThemeStoreImageState = if (night) {
    stored.copy(
        uriString = stored.nightUriString,
        videoUriString = stored.nightVideoUriString,
        crop = stored.nightResponsiveCrops.forAspectRatio(aspectRatio),
        visualSettings = stored.nightVisualSettings,
        responsiveCrops = stored.nightResponsiveCrops,
        nightUriString = null,
        nightVideoUriString = null,
        variantSettings = MediaVariantSettings(),
    )
} else {
    stored.copy(
        crop = stored.responsiveCrops.forAspectRatio(aspectRatio),
        nightUriString = null,
        nightVideoUriString = null,
        variantSettings = MediaVariantSettings(),
    )
}

@Composable
private fun InstallCardWallpaperEditor(
    spec: InstallCardWallpaperSpec,
    modifier: Modifier,
    selector: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storedState = rememberThemeStoreImageState(spec.slot)
    var editNight by rememberSaveable { mutableStateOf(false) }
    var pendingNight by rememberSaveable { mutableStateOf(false) }
    var showCrop by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showPagePreview by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(0) }
    val state = remember(storedState, editNight, spec.previewAspectRatio) {
        installCardWallpaperEditingState(storedState, editNight, spec.previewAspectRatio)
    }
    val imageBitmap = androidx.compose.runtime.key(editNight, state.uriString, state.crop) {
        rememberCustomImageBitmap(state.uriString, crop = state.crop)
    }
    val videoFrameBitmap = androidx.compose.runtime.key(editNight, state.videoUriString) {
        rememberCustomVideoFrameBitmap(state.videoUriString)
    }
    val title = stringResource(spec.titleRes)

    fun saveResponsiveCrops(uriString: String, night: Boolean) {
        scope.launch {
            val info = inspectMediaFile(context, uriString) ?: return@launch
            val width = info.width ?: return@launch
            val height = info.height ?: return@launch
            val current = me.weishu.kernelsu.ui.util.readThemeStoreImageState(context, spec.slot)
            val currentUri = if (night) current.nightUriString ?: current.nightVideoUriString
                else current.uriString ?: current.videoUriString
            if (currentUri != uriString) return@launch
            setThemeStoreImageSlotResponsiveCrops(context, spec.slot, generateResponsiveCrops(width, height), night = night)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val night = pendingNight
        val storageKey = if (night) spec.slot.nightUriKey else spec.slot.uriKey
        val storedUri = persistCustomImageReference(context, uri, storageKey)
            ?: uri.toString().also { takePersistableImageReadPermission(context, uri) }
        if (night) setThemeStoreImageSlotNightMedia(context, spec.slot, storedUri, video = false)
        else setThemeStoreImageSlot(context, spec.slot, storedUri)
        saveResponsiveCrops(storedUri, night)
        editNight = night
        showCrop = true
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val night = pendingNight
        takePersistableVideoBackgroundReadPermission(context, uri)
        if (night) setThemeStoreImageSlotNightMedia(context, spec.slot, uri.toString(), video = true)
        else setThemeStoreImageSlotVideo(context, spec.slot, uri.toString())
        saveResponsiveCrops(uri.toString(), night)
        editNight = night
        showCrop = true
    }

    AppearanceEditorWorkspace(
        modifier = modifier,
        selector = selector,
        preview = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceEditorHeading(title, stringResource(spec.summaryRes))
                AppearanceEditorVariantPicker(editNight) {
                    editNight = it
                    showCrop = false
                    showPreview = false
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                        InstallCardWallpaperFrame(spec, state, imageBitmap, videoFrameBitmap)
                    }
                }
                Text(stringResource(installCardWallpaperStatusRes(state)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MediaFileInfoSummary(rememberMediaFileInfo(state.uriString ?: state.videoUriString))
                androidx.compose.runtime.key(editNight) {
                    AppearanceEditorMediaActions(
                        title = title,
                        hasMedia = state.hasSelected,
                        onPickImage = { pendingNight = editNight; imageLauncher.launch(arrayOf("image/*")) },
                        onPickVideo = { pendingNight = editNight; videoLauncher.launch(arrayOf("video/*")) },
                        onCrop = { showCrop = true },
                        onPreview = { showPreview = true },
                        onClear = {
                            if (editNight) setThemeStoreImageSlotNightMedia(context, spec.slot, null, video = false)
                            else setThemeStoreImageSlot(context, spec.slot, null)
                        },
                    )
                }
                AppearanceEditorAction(stringResource(R.string.install_card_wallpaper_screen_preview),
                    Icons.Rounded.Visibility, { showPagePreview = true })
            }
        },
        controls = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceEditorTabs(
                    listOf(stringResource(R.string.background_editor_effects), stringResource(R.string.appearance_editor_rotation)),
                    section, { section = it },
                )
                if (section == 0) {
                    if (state.hasSelected) {
                        MediaVisualControls(
                            value = state.visualSettings,
                            onValueChange = { setThemeStoreImageSlotVisualSettings(context, spec.slot, it, night = editNight) },
                            showContrast = true,
                            showMotion = true,
                        )
                    } else {
                        Text(stringResource(R.string.background_editor_choose_hint),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    MediaVariantControls(
                        value = storedState.variantSettings,
                        onValueChange = { setThemeStoreImageSlotVariantSettings(context, spec.slot, it) },
                    )
                }
                Text(stringResource(R.string.appearance_editor_save_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )

    SettingsWallpaperCropDialog(
        show = showCrop && state.hasSelected,
        uriString = state.uriString ?: state.videoUriString,
        crop = state.crop,
        onCropChange = { crop ->
            val safeCrop = sanitizeCustomWallpaperCrop(crop)
            if (editNight) setThemeStoreImageSlotNightCrop(context, spec.slot, safeCrop)
            else setThemeStoreImageSlotCrop(context, spec.slot, safeCrop)
            setThemeStoreImageSlotResponsiveCrops(
                context, spec.slot,
                state.responsiveCrops.withCropForAspectRatio(spec.previewAspectRatio, safeCrop),
                night = editNight,
            )
            showPreview = true
        },
        onDismissRequest = { showCrop = false },
        title = stringResource(R.string.install_card_wallpaper_crop_title, title),
        editorAspectRatio = spec.previewAspectRatio,
        cropAspectRatio = spec.previewAspectRatio,
        defaultCrop = DEFAULT_CUSTOM_WALLPAPER_CROP,
        previewBitmap = videoFrameBitmap.takeIf { state.hasVideoSelected },
        transform = state.visualSettings.transform,
        onTransformChange = {
            setThemeStoreImageSlotVisualSettings(context, spec.slot, state.visualSettings.copy(transform = it), night = editNight)
        },
        onGenerateResponsiveCrops = { setThemeStoreImageSlotResponsiveCrops(context, spec.slot, it, night = editNight) },
    )
    InstallCardWallpaperPreviewDialog(showPreview && state.hasSelected, title, spec, state, { showPreview = false })
    InstallCardWallpaperScreenPreviewDialog(showPagePreview, { showPagePreview = false })
}

@Composable
private fun InstallCardWallpaperFrame(
    spec: InstallCardWallpaperSpec,
    state: ThemeStoreImageState,
    imageBitmap: ImageBitmap?,
    videoFrameBitmap: ImageBitmap?,
) {
    val frameBitmap = imageBitmap ?: videoFrameBitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(spec.previewAspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            frameBitmap != null -> MediaVisualLayer(
                settings = state.visualSettings,
                modifier = Modifier.fillMaxSize(),
            ) { colorFilter ->
                Image(
                    bitmap = frameBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.hasSelected -> CircularProgressIndicator()
            else -> Text(
                text = stringResource(R.string.home_card_wallpaper_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (frameBitmap != null) {
            Box(Modifier.matchParentSize().background(installCardWallpaperOverlayColor(isInDarkTheme())))
            InstallCardWallpaperSample(spec, MaterialTheme.colorScheme.onSurface)
            InstallCardWallpaperSafeArea()
        }
    }
}

@Composable
private fun InstallCardWallpaperSafeArea() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.24f)
                .background(Color.Black.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.weight(0.50f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.26f)
                .background(Color.Black.copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun InstallCardWallpaperSample(
    spec: InstallCardWallpaperSpec,
    contentColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = stringResource(spec.titleRes),
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 2) 0.68f else 1f)
                    .background(contentColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(vertical = 7.dp, horizontal = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 0) 0.72f else 0.52f)
                        .background(contentColor.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}


@Composable
private fun InstallCardWallpaperPreviewDialog(
    show: Boolean,
    title: String,
    spec: InstallCardWallpaperSpec,
    state: ThemeStoreImageState,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = stringResource(R.string.install_card_wallpaper_preview_title, title),
        onDismissRequest = onDismissRequest,
        content = {
            Column(modifier = Modifier.heightIn(max = me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight())
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(spec.previewAspectRatio)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    InstallCardWallpaperBackground(state)
                    InstallCardWallpaperSample(
                        spec = spec,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
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
private fun InstallCardWallpaperScreenPreviewDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    OverlayDialog(
        show = true,
        title = stringResource(R.string.install_card_wallpaper_screen_preview),
        onDismissRequest = onDismissRequest,
        content = {
            Column(modifier = Modifier.heightIn(max = me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.install),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    installCardWallpaperSpecs.forEach { spec ->
                        InstallCardWallpaperMappedPreview(spec)
                    }
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {},
                    ) {
                        Text(stringResource(R.string.install_next))
                    }
                    Text(
                        text = stringResource(R.string.install_card_wallpaper_language_check_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
private fun InstallCardWallpaperMappedPreview(spec: InstallCardWallpaperSpec) {
    val state = rememberThemeStoreImageState(spec.slot)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(spec.previewAspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        InstallCardWallpaperBackground(state)
        InstallCardWallpaperSample(
            spec = spec,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun installCardWallpaperStatusRes(state: ThemeStoreImageState): Int {
    return when {
        state.hasVideoSelected -> R.string.home_card_wallpaper_video_selected
        state.hasImageSelected -> R.string.home_card_wallpaper_image_selected
        else -> R.string.home_card_wallpaper_empty
    }
}
