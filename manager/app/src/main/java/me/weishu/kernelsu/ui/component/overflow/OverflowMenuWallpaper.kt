package me.weishu.kernelsu.ui.component.overflow

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.rememberCustomImageBitmap
import me.weishu.kernelsu.ui.util.FULL_CUSTOM_WALLPAPER_CROP
import me.weishu.kernelsu.ui.util.adaptiveDialogContentMaxHeight
import me.weishu.kernelsu.ui.util.loadCustomImageBitmap
import me.weishu.kernelsu.ui.util.persistCustomImageReference
import me.weishu.kernelsu.ui.util.releaseCustomImageReference

internal data class OverflowMenuWallpaperState(
    val uriString: String?,
    val bitmap: ImageBitmap?,
    val busy: Boolean,
    val onPick: () -> Unit,
    val onClear: () -> Unit,
)

@Composable
internal fun rememberOverflowMenuWallpaper(target: OverflowMenuTarget): OverflowMenuWallpaperState {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("overflow_menu_appearance", Context.MODE_PRIVATE) }
    var uriString by remember(preferences, target) { mutableStateOf(preferences.getString(target.wallpaperKey, null)) }
    var busy by remember(target) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(preferences, target) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == target.wallpaperKey) uriString = prefs.getString(target.wallpaperKey, null)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        uriString = preferences.getString(target.wallpaperKey, null)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        // Own a local copy: gallery permissions and deleted source photos cannot break it.
                        val newReference = persistCustomImageReference(context, uri, target.wallpaperKey, maxBytes = 32L * 1024 * 1024)
                            ?: return@withContext false
                        val validation = loadCustomImageBitmap(context, newReference, maxSide = 64)
                        if (validation == null) {
                            releaseCustomImageReference(context, newReference)
                            return@withContext false
                        }
                        validation.recycle()
                        val previous = preferences.getString(target.wallpaperKey, null)
                        if (preferences.edit().putString(target.wallpaperKey, newReference).commit()) {
                            releaseCustomImageReference(context, previous)
                            true
                        } else {
                            // A failed commit still updates SharedPreferences in memory.
                            if (preferences.edit().putString(target.wallpaperKey, previous).commit()) {
                                releaseCustomImageReference(context, newReference)
                            }
                            false
                        }
                    }
                    if (!saved) Toast.makeText(context, R.string.overflow_menu_wallpaper_error, Toast.LENGTH_LONG).show()
                } finally {
                    busy = false
                }
            }
        }
    }
    return OverflowMenuWallpaperState(
        uriString = uriString,
        bitmap = rememberCustomImageBitmap(uriString, maxSide = 800, crop = FULL_CUSTOM_WALLPAPER_CROP),
        busy = busy,
        onPick = { if (!busy) picker.launch(arrayOf("image/*")) },
        onClear = {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val cleared = withContext(Dispatchers.IO) {
                            val previous = preferences.getString(target.wallpaperKey, null)
                            preferences.edit().remove(target.wallpaperKey).commit().also { saved ->
                                if (saved) releaseCustomImageReference(context, previous)
                                else preferences.edit().putString(target.wallpaperKey, previous).commit()
                            }
                        }
                        if (!cleared) Toast.makeText(context, R.string.overflow_menu_wallpaper_error, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            }
        },
    )
}

@Composable
internal fun OverflowMenuWallpaperDialog(state: OverflowMenuWallpaperState, onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismissRequest() },
        title = { Text(stringResource(R.string.overflow_menu_wallpaper)) },
        text = {
            Column(
                Modifier.heightIn(max = adaptiveDialogContentMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.overflow_menu_wallpaper_summary))
                state.bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = stringResource(R.string.overflow_menu_wallpaper),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)),
                    )
                }
                if (state.busy) CircularProgressIndicator()
                TextButton(onClick = state.onPick, enabled = !state.busy) {
                    Text(stringResource(R.string.overflow_menu_wallpaper_pick))
                }
                TextButton(onClick = state.onClear, enabled = !state.busy && state.uriString != null) {
                    Text(stringResource(R.string.overflow_menu_wallpaper_clear))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest, enabled = !state.busy) { Text(stringResource(android.R.string.ok)) } },
    )
}
