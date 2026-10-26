package me.weishu.kernelsu.ui.screen.module

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.passthrough.Image
import me.weishu.kernelsu.ui.component.passthrough.MaterialIcon
import me.weishu.kernelsu.ui.component.passthrough.MiuixText
import me.weishu.kernelsu.ui.component.rememberCustomImageBitmap
import me.weishu.kernelsu.ui.screen.settings.SettingsWallpaperCropDialog
import me.weishu.kernelsu.ui.util.FULL_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight
import me.weishu.kernelsu.ui.util.loadCustomImageBitmap
import me.weishu.kernelsu.ui.util.persistCustomImageReference
import me.weishu.kernelsu.ui.util.releaseCustomImageReference
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

private data class ModuleButtonImagesState(
    val bitmaps: Map<ModuleButtonImageSlot, ImageBitmap?> = emptyMap(),
    val onEdit: () -> Unit = {},
)

private val LocalModuleButtonImages = staticCompositionLocalOf { ModuleButtonImagesState() }

@Composable
internal fun moduleButtonImageEditor(): () -> Unit = LocalModuleButtonImages.current.onEdit

/** Decode the three shared pictures once for the list, rather than once per module card. */
@Composable
internal fun ModuleButtonImages(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("module_button_images", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var pendingSlot by rememberSaveable { mutableStateOf(ModuleButtonImageSlot.Action) }
    var cropSlot by rememberSaveable { mutableStateOf<ModuleButtonImageSlot?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val entries = remember(prefs, revision) {
        ModuleButtonImageSlot.entries.associateWith { readModuleButtonImage(prefs, it) }
    }
    val bitmaps = ModuleButtonImageSlot.entries.associateWith { slot ->
        val image = entries.getValue(slot)
        rememberCustomImageBitmap(image.uriString, maxSide = 160, crop = image.crop)
    }
    fun update(operation: () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (!withContext(Dispatchers.IO) { runCatching(operation).getOrDefault(false) }) {
                    Toast.makeText(context, R.string.module_button_images_error, Toast.LENGTH_LONG).show()
                }
            } finally {
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            val slot = pendingSlot
            update {
                val previous = readModuleButtonImage(prefs, slot)
                val reference = persistCustomImageReference(
                    context, uri, "module_button_${slot.key}_${UUID.randomUUID()}", maxBytes = 8L * 1024 * 1024,
                ) ?: return@update false
                val bitmap = loadCustomImageBitmap(context, reference, maxSide = 160)
                if (bitmap == null) {
                    releaseCustomImageReference(context, reference)
                    return@update false
                }
                bitmap.recycle()
                if (saveModuleButtonImage(prefs, slot, ModuleButtonImage(reference))) {
                    releaseCustomImageReference(context, previous.uriString)
                    true
                } else {
                    if (saveModuleButtonImage(prefs, slot, previous)) releaseCustomImageReference(context, reference)
                    false
                }
            }
        }
    }
    CompositionLocalProvider(LocalModuleButtonImages provides ModuleButtonImagesState(bitmaps) { showEditor = true }) {
        content()
        if (showEditor && cropSlot == null) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.module_button_images_title),
                onDismissRequest = { if (!busy) showEditor = false },
                content = {
                    Column(
                        modifier = Modifier.heightIn(max = adaptiveDialogContentMaxHeight())
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MiuixText(
                            text = stringResource(R.string.module_button_images_summary),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        ModuleButtonImageSlot.entries.forEach { slot ->
                            val image = entries.getValue(slot)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ModuleButtonIcon(
                                        slot = slot,
                                        fallback = when (slot) {
                                            ModuleButtonImageSlot.Action -> Icons.Rounded.PlayArrow
                                            ModuleButtonImageSlot.Uninstall -> Icons.Rounded.DeleteOutline
                                            ModuleButtonImageSlot.Wallpaper -> Icons.Rounded.Image
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                            .background(MiuixTheme.colorScheme.secondaryContainer),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                    MiuixText(text = stringResource(slot.label), modifier = Modifier.weight(1f))
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        text = stringResource(R.string.module_button_image_pick),
                                        enabled = !busy,
                                        onClick = {
                                            pendingSlot = slot
                                            runCatching { picker.launch(arrayOf("image/*")) }.onFailure {
                                                Toast.makeText(context, R.string.module_button_images_error, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                    )
                                    if (image.uriString != null) {
                                        TextButton(text = stringResource(R.string.settings_wallpaper_crop), enabled = !busy, onClick = { cropSlot = slot })
                                        TextButton(
                                            text = stringResource(R.string.module_button_image_reset), enabled = !busy,
                                            onClick = {
                                                update {
                                                    if (saveModuleButtonImage(prefs, slot, ModuleButtonImage())) {
                                                        releaseCustomImageReference(context, image.uriString)
                                                        true
                                                    } else {
                                                        saveModuleButtonImage(prefs, slot, image)
                                                        false
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (busy) CircularProgressIndicator()
                        TextButton(
                            modifier = Modifier.fillMaxWidth(), text = stringResource(android.R.string.ok),
                            colors = ButtonDefaults.textButtonColorsPrimary(), enabled = !busy, onClick = { showEditor = false },
                        )
                    }
                },
            )
        }
        cropSlot?.let { slot ->
            val image = entries.getValue(slot)
            SettingsWallpaperCropDialog(
                show = image.uriString != null,
                uriString = image.uriString,
                crop = image.crop,
                defaultCrop = FULL_CUSTOM_WALLPAPER_CROP,
                editorAspectRatio = 1f,
                cropAspectRatio = 1f,
                allowTransforms = false,
                onCropChange = { crop ->
                    update {
                        if (saveModuleButtonImage(prefs, slot, image.copy(crop = crop))) true
                        else { saveModuleButtonImage(prefs, slot, image); false }
                    }
                },
                onDismissRequest = { cropSlot = null },
            )
        }
    }
}

@Composable
internal fun ModuleButtonIcon(
    slot: ModuleButtonImageSlot,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    customImageEnabled: Boolean = true,
) {
    val bitmap = LocalModuleButtonImages.current.bitmaps[slot].takeIf { customImageEnabled }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        MaterialIcon(imageVector = fallback, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}
