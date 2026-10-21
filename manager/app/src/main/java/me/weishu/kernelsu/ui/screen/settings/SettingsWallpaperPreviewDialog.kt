package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.rememberCustomWallpaperPreviewBitmap
import me.weishu.kernelsu.ui.component.MediaVisualLayer
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.MediaVisualSettings
import me.weishu.kernelsu.ui.util.sanitizeCustomWallpaperOpacity
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import me.weishu.kernelsu.ui.theme.UiPassthroughTheme
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.component.passthrough.MaterialText as PreviewText
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

@Composable
fun SettingsWallpaperPreviewDialog(
    show: Boolean,
    uriString: String?,
    opacity: Float,
    crop: CustomWallpaperCrop,
    visualSettings: MediaVisualSettings = MediaVisualSettings(),
    passthroughSettings: UiPassthroughSettings,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val imageBitmap = rememberCustomWallpaperPreviewBitmap(uriString, crop)
    MiuixWallpaperPreviewDialog(
        imageBitmap = imageBitmap,
        uriString = uriString,
        opacity = opacity,
        visualSettings = visualSettings,
        passthroughSettings = passthroughSettings,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun MiuixWallpaperPreviewDialog(
    imageBitmap: ImageBitmap?,
    uriString: String?,
    opacity: Float,
    visualSettings: MediaVisualSettings,
    passthroughSettings: UiPassthroughSettings,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.settings_wallpaper_preview),
        onDismissRequest = onDismissRequest,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WallpaperPreviewFrame(
                    imageBitmap = imageBitmap,
                    uriString = uriString,
                    opacity = opacity,
                    visualSettings = visualSettings,
                    passthroughSettings = passthroughSettings,
                )
                MiuixTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(android.R.string.ok),
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )
}

@Composable
private fun WallpaperPreviewFrame(
    imageBitmap: ImageBitmap?,
    uriString: String?,
    opacity: Float,
    visualSettings: MediaVisualSettings,
    passthroughSettings: UiPassthroughSettings,
) {
    val overlayAlpha = 1f - sanitizeCustomWallpaperOpacity(opacity)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageBitmap != null -> MediaVisualLayer(
                settings = visualSettings,
                modifier = Modifier.fillMaxSize(),
            ) { colorFilter ->
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                )
            }

            uriString.isNullOrBlank() -> Text(
                modifier = Modifier.padding(24.dp),
                text = stringResource(R.string.settings_wallpaper_not_selected),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> CircularProgressIndicator()
        }
        if (imageBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background((if (isInDarkTheme()) Color.Black else Color.White).copy(alpha = overlayAlpha))
            )
            BackgroundPreviewUi(passthroughSettings)
        }
    }
}

/** The media stays behind this sample, just as it does behind the actual page. */
@Composable
internal fun BackgroundPreviewUi(settings: UiPassthroughSettings) {
    UiPassthroughTheme(settings) {
        Column(
            modifier = Modifier.padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewText(
                text = stringResource(R.string.settings_wallpaper_preview_hint),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                PreviewText(
                    text = stringResource(R.string.settings_wallpaper_preview_ui_sample),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
