package me.weishu.kernelsu

/** Additive UAPI upgrades retain the previous version's guarded code paths. */
internal fun isCompatibleUapiVersion(kernelVersion: Int, managerVersion: Int): Boolean =
    kernelVersion == managerVersion ||
        (kernelVersion == 4 && managerVersion == 5) ||
        (kernelVersion == 5 && managerVersion == 6)
