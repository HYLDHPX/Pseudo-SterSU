# Installed launcher entry

This dependency-free Android app is an unsigned build-time template, not a second
Manager. The Manager replaces the template's literal label and PNG on-device,
then signs it with a separate, non-exportable AndroidKeyStore RSA key. It does
not contain a Manager signing key, root access, network permission, or secrets.
APK signing and verification use Google's Apache-2.0 licensed `apksig` library.

If a device's default JCA signer fails after initialization, Manager retries
the available signing providers with the same non-exportable key and certificate.
The V2-only compatibility path reuses apksig's chunked digests and signing-block
encoders; it does not change global provider order, export a private key, replace
the signing identity, or disable verification. Every completed entry must pass
APK V2 verification and match the original certificate before installation.
Failed attempts remove the partial output and leave the current icon unchanged.
The full exception chain is recorded under the `InstalledLauncher` log tag;
the screen shows a localized retry/shortcut hint instead of only `Signer #1 failed`.

Release builds keep apksig's annotated ASN.1 model classes and their generic
field signatures. Keeping only the model members was insufficient under R8
full mode: the actual release APK lost `SubjectPublicKeyInfo`'s class annotation
and its public-key encoding emitted reflection errors on Android. A device
regression uses the finished, minified APK and a disposable shell-UID key:
`scripts/test-launcher-release-device.ps1`. It independently verifies the
resulting entry with the SDK, without installing it or accessing Manager keys.

The system installer asks the user to install the resulting entry. No launcher
alias is disabled by generating an APK, requesting permission, cancelling the
installer, or merely detecting an installed package. A separate user action
sends a short-lived, one-use challenge through the signed entry back to the
Manager before hiding launcher aliases. MainActivity and the bridge stay enabled.
The pending install timestamp and previous entry update time are stored for
up to ten minutes. A resumed Manager checks again when an OEM installer omits
its result callback or publishes the package late; cancellation cannot hide an
unchanged entry. Explicit restore cancels the pending request. Android 13+
disables all launcher aliases in one batch; older versions use the sequential
API. The disabled states are read back before the hidden flag is committed,
and a failure restores the primary icon. A challenge already in flight is
not overwritten by repeated resume callbacks.
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
