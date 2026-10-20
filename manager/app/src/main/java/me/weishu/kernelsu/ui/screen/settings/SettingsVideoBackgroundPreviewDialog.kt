package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.CustomVideoBackground
import me.weishu.kernelsu.ui.component.MediaVisualLayer
import me.weishu.kernelsu.ui.util.CustomWallpaperCrop
import me.weishu.kernelsu.ui.util.DEFAULT_CUSTOM_VIDEO_BACKGROUND_FRAME_RATE
import me.weishu.kernelsu.ui.util.MediaVisualSettings
import me.weishu.kernelsu.ui.util.sanitizeCustomWallpaperOpacity
import me.weishu.kernelsu.ui.util.UiPassthroughSettings
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

@Composable
fun SettingsVideoBackgroundPreviewDialog(
    show: Boolean,
    uriString: String?,
    durationSeconds: Int,
    frameRate: Int = DEFAULT_CUSTOM_VIDEO_BACKGROUND_FRAME_RATE,
    opacity: Float,
    crop: CustomWallpaperCrop = CustomWallpaperCrop(),
    visualSettings: MediaVisualSettings = MediaVisualSettings(),
    passthroughSettings: UiPassthroughSettings,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    MiuixVideoBackgroundPreviewDialog(
        uriString = uriString,
        durationSeconds = durationSeconds,
        frameRate = frameRate,
        opacity = opacity,
        crop = crop,
        visualSettings = visualSettings,
        passthroughSettings = passthroughSettings,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun MiuixVideoBackgroundPreviewDialog(
    uriString: String?,
    durationSeconds: Int,
    frameRate: Int,
    opacity: Float,
    crop: CustomWallpaperCrop,
    visualSettings: MediaVisualSettings,
    passthroughSettings: UiPassthroughSettings,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.settings_video_background_preview),
        onDismissRequest = onDismissRequest,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VideoPreviewFrame(
                    uriString = uriString,
                    durationSeconds = durationSeconds,
                    frameRate = frameRate,
                    opacity = opacity,
                    crop = crop,
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
        },
    )
}

@Composable
private fun VideoPreviewFrame(
    uriString: String?,
    durationSeconds: Int,
    frameRate: Int,
    opacity: Float,
    crop: CustomWallpaperCrop,
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
        if (uriString.isNullOrBlank()) {
            Text(
                modifier = Modifier.padding(24.dp),
                text = stringResource(R.string.settings_video_background_not_selected),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        MediaVisualLayer(settings = visualSettings, modifier = Modifier.fillMaxSize()) {
            CustomVideoBackground(
                uriString = uriString,
                durationSeconds = durationSeconds,
                frameRate = frameRate,
                crop = crop,
                drawOverlay = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha))
        )
        BackgroundPreviewUi(passthroughSettings)
    }
}
