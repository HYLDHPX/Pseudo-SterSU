package me.weishu.kernelsu.ui.util

internal fun managerDisplayName(customName: String, defaultName: String, syncEnabled: Boolean): String =
    if (syncEnabled) customName.trim().ifBlank { defaultName } else defaultName

internal fun managerStatusLabel(
    status: String,
    defaultName: String,
    customName: String,
    syncEnabled: Boolean,
): String {
    val displayName = managerDisplayName(customName, defaultName, syncEnabled)
    if (displayName == defaultName) return status
    return if (defaultName.isNotBlank() && status.contains(defaultName)) {
        status.replace(defaultName, displayName)
    } else {
        "$displayName \u00b7 $status"
    }
}
