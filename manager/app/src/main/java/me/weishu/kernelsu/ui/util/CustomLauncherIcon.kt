package me.weishu.kernelsu.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.util.Log
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.MainActivity

internal enum class CustomLauncherMode(val value: String) {
    Installed("installed"),
    Shortcut("shortcut");

    companion object {
        const val PREF_KEY = "custom_launcher_mode"

        fun fromValue(value: String?): CustomLauncherMode =
            entries.firstOrNull { it.value == value } ?: Installed
    }
}

internal enum class ManagerIconShortcutResult {
    Requested, Updated, Unsupported, Failed
}

private const val MANAGER_ICON_SHORTCUT_ID = "manager_custom_icon"

/** Pin directly: no APK, root permission, or dynamic-shortcut quota is needed. */
internal fun requestManagerIconShortcut(
    context: Context,
    bitmap: Bitmap,
    name: String,
): ManagerIconShortcutResult {
    val manager = context.getSystemService(ShortcutManager::class.java)
        ?: return ManagerIconShortcutResult.Unsupported
    val existing = manager.pinnedShortcuts.firstOrNull { it.id == MANAGER_ICON_SHORTCUT_ID }
    if (existing == null && !manager.isRequestPinShortcutSupported) {
        return ManagerIconShortcutResult.Unsupported
    }
    // A pinned shortcut must remain associated with an enabled MAIN/LAUNCHER alias.
    // Restore it only when actually creating/updating, never just when changing mode.
    check(InstalledLauncher.restore(context)) { context.getString(R.string.launcher_install_verify_failed) }
    val option = LauncherIconOption.fromValue(
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(LauncherIconOption.PREF_KEY, null),
    )
    val label = name.trim().ifBlank { context.getString(R.string.app_name) }
    val shortcut = ShortcutInfo.Builder(context, MANAGER_ICON_SHORTCUT_ID)
        .setActivity(ComponentName(context.packageName, option.aliasClassName))
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
        .setIntent(Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        .build()
    return if (existing != null) {
        manager.enableShortcuts(listOf(MANAGER_ICON_SHORTCUT_ID))
        if (manager.updateShortcuts(listOf(shortcut))) ManagerIconShortcutResult.Updated
        else ManagerIconShortcutResult.Failed
    } else if (manager.requestPinShortcut(shortcut, null)) {
        // TRUE only means the launcher accepted the request, not that the user confirmed it.
        ManagerIconShortcutResult.Requested
    } else {
        ManagerIconShortcutResult.Failed
    }
}

/** Move an existing pinned icon before disabling its old launcher alias. */
internal fun synchronizeManagerIconShortcut(context: Context, option: LauncherIconOption) {
    runCatching {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val existing = manager.pinnedShortcuts.firstOrNull { it.id == MANAGER_ICON_SHORTCUT_ID } ?: return
        manager.enableShortcuts(listOf(MANAGER_ICON_SHORTCUT_ID))
        val shortcut = ShortcutInfo.Builder(context, existing.id)
            .setActivity(ComponentName(context.packageName, option.aliasClassName))
            .build()
        check(manager.updateShortcuts(listOf(shortcut)))
    }.onFailure { Log.w("LauncherIcon", "Unable to move the custom shortcut to the selected alias", it) }
}
