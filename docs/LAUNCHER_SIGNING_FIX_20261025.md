# Installed launcher signing and icon recovery - 2026-10-25

1. [x] Reproduce launcher signing against AndroidKeyStore and the release
   runtime; correct the failing path without exporting or replacing keys.
2. [x] Correct installation/resume verification and launcher-alias state;
   hide only after a trusted entry successfully opens the Manager.
3. [x] Run regression checks, build and verify the release APK.

Publication target: `fixz232/Pseudo-SterSU`, `main`. The uploaded source commit
is recorded in the local combined verification report after checking remote
main; signing keys, scratch archives and build outputs are excluded.

Preserve existing user data, signing identities, unrelated work and the
previous release. Do not install an APK or flash a device. The user cannot
currently reproduce the failure interactively; distinguish host/device
checks from a full on-device installation and launcher acceptance test.

## Findings and changes

- The user's installed Manager has version 3.3.3 / 33300 but its APK hash is
  different from the latest previously delivered APK. Equal version numbers
  alone do not establish that a fix is installed.
- A disposable AndroidKeyStore RSA key signs successfully with the unminified
  implementation on the connected Android 16 device. The actual old release
  DEX emits ASN.1 public-key encoding errors because R8 removed
  `SubjectPublicKeyInfo`'s `Asn1Class` annotation. A stricter device regression
  reproduces this missing annotation in the previous release. This proves a
  release-only reflection defect, not the exact exception from the user's
  existing application key, which could not be retriggered interactively.
- Keep the annotated model classes, reflected fields, constructors and generic
  signatures, rather than only retaining their members.
- Persist an install attempt's time and previous package update time for ten
  minutes. A resumed Manager retries package visibility checks without relying
  on an OEM installer's result callback or a remembered screen instance.
- Do not overwrite an in-flight one-use challenge. Cancellation or an unchanged
  package cannot auto-hide the Manager. Explicit icon restore cancels pending
  verification, including a check already waiting for PackageManager.
- Android 13+ changes all five launcher aliases in one batch; older devices
  keep their compatible API. Read back the disabled states before saving the
  hidden flag. Restore the main icon if changing aliases or saving state fails.
- Icon restore now checks preference persistence instead of reporting success
  after a failed commit.

## Checks

- Manager release compilation and full JVM tests pass: 1,206 tests total,
  1,205 passed, one opt-in network check skipped, zero failures/errors.
- The first JVM run found one obsolete source-contract assertion referring to
  a removed temporary timestamp pair. It now checks the surviving installed
  entry branch; the full suite was rerun successfully.
- `scripts/test-launcher-release-device.ps1` loads signing code from the actual
  minified APK, checks its retained model annotation, creates a disposable key
  in the shell UID, and verifies its signed entry with the separate SDK tool.
  It neither reads the Manager's existing key nor installs an application.
- Evidence and archived test reports: `out/launcher-signing-fix-20261025/`.
- A full installation, OEM installer return, and desktop icon refresh have not
  been exercised interactively; no APK has been installed and no kernel flashed.

## Release verification

- `clean :app:assembleRelease` succeeded in 5m 58s. Version 3.3.3 / 33300,
  package `io.github.fixz.stersu`; the release is not debuggable.
- The actual finished APK passed the device regression: ASN.1 model annotation
  retained, AndroidKeyStore signature generated and verified, no encoding or
  reflection diagnostics. The retrieved entry independently passed the SDK
  verifier. Each disposable test key was removed from the shell UID.
- APK signature and 16 KiB ZIP alignment pass. The certificate remains
  `1c89980c03432844cfe195dab90bfaecbcd987d19309da648014164be78007d1`,
  matching the previous release's existing Android Debug signing identity.
  This is a release build, not a newly introduced production signing key.
- Artifact: `release-local/3.3.3-33300-20261025/SterSU_3.3.3_33300-launcher-sign-hide-fix-release.apk`.
- Size: 18,705,509 bytes. SHA-256:
  `d85ffd4d7c8d7961354050b7a567689b1eb20dfda5bc694adc5d7fd376123e6b`.
- The previous Rain-store APK is unchanged. This APK must be installed by the
  user to exercise the updated install-return and desktop hiding behavior.
