# Installed launcher entry

This dependency-free Android app is an unsigned build-time template, not a second
Manager. The Manager replaces the template's literal label and PNG on-device,
then signs it with a separate, non-exportable AndroidKeyStore RSA key. It does
not contain a Manager signing key, root access, network permission, or secrets.
APK signing and verification use Google's Apache-2.0 licensed `apksig` library.

The system installer asks the user to install the resulting entry. No launcher
alias is disabled by generating an APK, requesting permission, cancelling the
installer, or merely detecting an installed package. A separate user action
sends a short-lived, one-use challenge through the signed entry back to the
Manager before hiding launcher aliases. MainActivity and the bridge stay enabled.
This feature does not change stealth mode or kernel/userspace state.

Recovery is available in Manager settings and the entry's long-press shortcut.
An entry uninstall broadcast and Manager startup also restore the original icon
when possible. Restore the icon before uninstalling the entry: Android can
suppress broadcasts while the Manager is force-stopped. If needed, open it with:

```
adb shell am start -n io.github.fixz.stersu/me.weishu.kernelsu.ui.MainActivity
```

Updating the entry's name/image requires installing the newly generated APK.
Clearing Manager data or reinstalling the Manager loses its local entry key;
uninstall the old entry before generating a new one. OEM launchers may still
apply their normal application icon mask. Old pinned shortcuts are not removed.

Build the template with `:launcher:assembleRelease`. Manager `preBuild` copies
it to generated assets automatically. Do not commit generated APKs or keys.
