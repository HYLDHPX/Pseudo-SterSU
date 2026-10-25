# Theme store repository assets

## Migration plan (2026-10-25)

1. [x] Inventory published themes and Release assets; copy verified packages and
   images into versioned directories in `fixz232/SterSU-ThemeStore`.
2. [x] Update publishing/moderation, catalog validation and Manager submission
   handling so future themes are managed in the repository. Preserve package
   hashes, creator ownership checks and compatibility with existing submissions.
3. [x] Run store and Manager checks, publish the store changes, verify live asset
   hashes, and build/verify the signed 3.3.3 Manager APK without installing it.

Historical Release assets remain available for older catalogs and Managers.

## Implemented layout

- Packages: `theme-store/packages/<id>/v<versionCode>/<id>-v<versionCode>.kstheme`.
- Preview media: `theme-store/assets/<id>/v<versionCode>/`.
- The two published themes retain their original bytes, SHA-256, author metadata,
  licenses, and version codes. Six assets were copied and verified.
- Moderation commits the verified package and catalog in one commit. Manual
  publishing stores a verified package for subsequent catalog review; neither
  workflow creates or overwrites a Release.
- Creator Center accepts creator-owned raw repository files and keeps legacy
  creator Release URLs compatible. Public repository publishing has a 100 MiB
  per-file limit; local import retains its existing 500 MiB limit.
- CI can validate new files from a PR checkout before raw URLs become live. It
  still verifies size, SHA-256, ZIP safety, schema, embedded media, and privacy.
- Raw repository downloads do not expose Release download counters. Existing
  counts are preserved instead of being reset or fabricated.

## Verification

- Theme-store commit: `0499c1e53c43d741035351b7c4f8829788342a98`, pushed to main.
- Store tests: 50 passed. Catalog schema, package/media validation, and five YAML
  documents passed locally. GitHub's catalog validation workflow also passed.
- The official GitHub API tree matches the Git blob hash and size of all six
  verified local assets. Local direct raw-host requests were reset or timed out;
  a successful direct raw download is not claimed.
- Manager: clean release build and unit tests passed (1169 passed, 1 skipped).
  The bundled catalog matches the migrated repository catalog. Theme packages
  remain remote and are not included in the APK.
- APK: `release-local/3.3.3-33300-20261025/SterSU_3.3.3_33300-theme-store-repository-release.apk`.
  Package `io.github.fixz.stersu`, 3.3.3 (33300), 18,688,993 bytes. Signature
  verification passed and the signer matches the previous release.
- SHA-256: `c94aaed208938949b188917a03a2f10be9e6653b0a0cd90a812cf437a37a0bc5`.
- No APK installation or device flashing was performed.

Machine-readable evidence is in
`out/theme-store-repository-migration-20261025/`.
