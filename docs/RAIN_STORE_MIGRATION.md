# Rain interface style store migration - 2026-10-25

1. [x] Move Rain from the built-in picker to verified downloaded styles; preserve
   its three layouts, design controls, shared themes and existing preferences.
   Show Rain Design only while Rain is active; close a restored Rain Design
   route when another interface style is active.
2. [x] Generate and validate Rain packages and the signed v2 catalog, publish
   them to fixz232/SterSU-ThemeStore, and verify the remote commit/assets.
3. [x] Run Manager checks, build the release APK, and verify its package,
   version, signature, size and SHA-256. Do not install or flash a device.

Use the existing data-only interface style format. Keep signing keys outside
the repositories and preserve unrelated working tree changes.

Publication verified:
- Theme-store commit: `e8b08618ca04c3fdbe10557056b41e691e49bb18`.
- Direct raw catalog, signature and Rain package all returned HTTP 200 with
  exact expected bytes. All other 21 style entries and v1 files are unchanged.
- Manager checks: 1204 passed, 1 opt-in network test skipped; store tests: 50 passed.

Release verified:
- APK: `release-local/3.3.3-33300-20261025/SterSU_3.3.3_33300-rain-store-release.apk`.
- Package: `io.github.fixz.stersu`; version: `3.3.3` (`33300`).
- Size: 18,700,557 bytes; release build is not debuggable.
- SHA-256: `7ecaea481bfba620561aa153940dbc7324cc113754d8afadefd6da6763ad8aed`.
- APK Signature Scheme v2 verified; certificate matches the previous APK.
- Combined evidence: `out/rain-store-migration-20261025/verification.json`.
- Theme-store working tree is clean and remote main matches the published commit.
- No APK was installed and no device was flashed.
