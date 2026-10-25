package me.weishu.kernelsu.ui.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import me.weishu.kernelsu.R

private const val LAUNCHER_ALIAS_PACKAGE = "me.weishu.kernelsu.ui"
private const val SETTINGS_PREFERENCES = "settings"

enum class LauncherIconOption(
    val value: String,
    val aliasClassName: String,
    @StringRes val labelRes: Int,
    @DrawableRes val foregroundRes: Int,
) {
    Default(
        value = "default",
        aliasClassName = "${LAUNCHER_ALIAS_PACKAGE}.LauncherDefault",
        labelRes = R.string.settings_app_icon_default,
        foregroundRes = R.mipmap.ic_launcher_foreground,
    ),
    FoxMask(
        value = "fox_mask",
        aliasClassName = "${LAUNCHER_ALIAS_PACKAGE}.LauncherFoxMask",
        labelRes = R.string.settings_app_icon_fox_mask,
        foregroundRes = R.mipmap.fox_mask_foreground,
    ),
    OperaMask(
        value = "opera_mask",
        aliasClassName = "${LAUNCHER_ALIAS_PACKAGE}.LauncherOperaMask",
        labelRes = R.string.settings_app_icon_opera_mask,
        foregroundRes = R.mipmap.opera_mask_foreground,
    ),
    SkRoot(
        value = "sk_root",
        aliasClassName = "${LAUNCHER_ALIAS_PACKAGE}.LauncherSkRoot",
        labelRes = R.string.settings_app_icon_sk_root,
        foregroundRes = R.mipmap.sk_root_foreground,
    ),
    GridMark(
        value = "grid_mark",
        aliasClassName = "${LAUNCHER_ALIAS_PACKAGE}.LauncherGridMark",
        labelRes = R.string.settings_app_icon_grid_mark,
        foregroundRes = R.mipmap.grid_mark_foreground,
    );

    companion object {
        const val PREF_KEY = "launcher_icon"
        const val DEFAULT_VALUE = "default"

        fun fromValue(value: String?): LauncherIconOption {
            return entries.firstOrNull { it.value == value } ?: Default
        }

        fun selectedIndex(value: String?): Int {
            return entries.indexOf(fromValue(value)).coerceAtLeast(0)
        }
    }
}

fun applyLauncherIcon(context: Context, option: LauncherIconOption): Boolean {
    val packageManager = context.packageManager
    val orderedOptions = listOf(option) + LauncherIconOption.entries.filter { it != option }
    return runCatching {
        orderedOptions.forEach { candidate ->
            val enabledState = if (candidate == option) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, candidate.aliasClassName),
                enabledState,
                PackageManager.DONT_KILL_APP,
            )
            if (candidate == option) synchronizeManagerIconShortcut(context, option)
        }
        context.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE).edit()
            .remove(INSTALLED_LAUNCHER_HIDDEN)
            .remove("installed_launcher_challenge")
            .remove("installed_launcher_challenge_time")
            .commit()
        true
    }.onFailure {
        Log.e("LauncherIcon", "failed to apply launcher icon ${option.value}", it)
    }.getOrDefault(false)
}

fun reconcileLauncherIcon(context: Context): Boolean {
    val appContext = context.applicationContext
    val preferences = appContext.getSharedPreferences(SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
    if (preferences.getBoolean(INSTALLED_LAUNCHER_HIDDEN, false)) {
        if (InstalledLauncher.status(appContext).trusted) return true
        // Includes entry uninstall/disable, Manager reinstall, and unavailable signing keys.
        return InstalledLauncher.restore(appContext)
    }
    val storedValue = preferences.getString(
        LauncherIconOption.PREF_KEY,
        LauncherIconOption.DEFAULT_VALUE,
    )
    val normalizedOption = LauncherIconOption.fromValue(storedValue)
    val legacyHidden = preferences.getBoolean("web_manager_launcher_hidden", false)
    val componentState = appContext.packageManager.getComponentEnabledSetting(
        ComponentName(appContext.packageName, normalizedOption.aliasClassName),
    )
    val selectedEnabled = componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
        (componentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && normalizedOption == LauncherIconOption.Default)
    // Clearing app data loses our preference/key but may leave alias overrides disabled.
    if (storedValue == normalizedOption.value && !legacyHidden && selectedEnabled) return true

    val iconApplied = applyLauncherIcon(appContext, normalizedOption)
    return iconApplied && preferences.edit()
        .putString(LauncherIconOption.PREF_KEY, normalizedOption.value)
        .putBoolean("web_manager_launcher_hidden", false)
        .commit()
}

class LauncherIconMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            reconcileLauncherIcon(context)
        }
    }
}
