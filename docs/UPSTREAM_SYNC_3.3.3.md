# SterSU 3.3.3 upstream synchronization

Target: update the existing customized source to the current tiann/KernelSU
upstream, build LKM 33300 for every bundled target, and deliver a signed
Manager 3.3.3 APK. Preserve the current uncommitted UI work.

## Plan and verification gates

- [x] Record the upstream revision and changed components; back up current KO
  payloads, manifests, versions, and a source patch before replacement.
- [x] Integrate upstream changes with three-way comparisons, preserving local
  GKI/LKM SUSFS separation, KPM, manager identity, and userspace ABI wrappers.
- [x] Set Manager version 3.3.3 and LKM version 33300 in authoritative metadata.
- [x] Rebuild all bundled kernel module targets using existing E:/F: build
  resources; verify identity, version, architecture, vermagic, and hashes.
- [x] Run affected component checks, rebuild bundled userspace if needed, build
  the release APK, run Manager tests, and verify the packaged modules and APK
  version, signature, size, and SHA-256.

No device installation or kernel flashing is part of this task. Compilation
and static module verification do not establish device boot compatibility.

## Initial state

- Workspace: E:/KernelSU-main; Windows PowerShell, WSL Ubuntu available.
- Local HEAD: 1e96e70e4; numerous pre-existing Manager edits are preserved.
- Version: 3.3.2 / 33200.
- Previously integrated upstream: 469c4282e72382388b4598fa022fe8ca91dc9ab5.
- Direct GitHub access initially inherited a stale proxy/mirror configuration;
  retry with command-local network overrides before changing persistent config.

## Upstream integration

- Verified 2026-10-25 through the GitHub commit and comparison APIs: upstream
  main is 528bafc5696196f3a9500cee2e7e18497421cd32 (2026-10-24).
- Four commits since the previous synchronization: remove the generated
  dynamic receiver permission, update website dependencies, fix waitsys
  crashes, and remove an unnecessary SELinux status mutex.
- All seven downloaded upstream blobs were checked against the Git object
  SHA-1 values in the comparison response before integration.
- Local kernel and website changes were preserved with three-way merges.
  The Manifest insertion conflicted with local weather provider queries;
  both were preserved through a checked, anchored insertion.
- No upstream IOCTL/profile ABI changes occurred in this interval. Manager
  and driver remain on UAPI 6; LKM build version is independently 33300.
- Upstream CI run 40075385290 succeeded for all eight LKM kernel families.
- Backups: out/backup-before-3.3.3-20261025/original-ko-and-version.zip,
  tracked-wip.patch, status.txt, and the per-file before copies under
  out/upstream-sync-20261025-528bafc569/before.
- Integration audit: out/upstream-sync-20261025-528bafc569/integration.json.
- Eight-target clean build completed in isolated source directories on E:,
  using the existing WSL Docker DDK images. The tiann/main tracking ref was
  also fetched successfully through the existing mirror; its revision and
  commit count match the verified GitHub API results (528bafc569 / 2704).

## Module and component verification

- All eight fresh KernelSU drivers and their eight separate optional SUSFS
  modules compiled successfully. Every driver reports build 33300 / UAPI 6.
- Verified ELF64 AArch64 ET_REL, matching kernel-family vermagic, empty CRC
  patch table, manager package/certificate pin, symbol lookup support, and
  AArch64 preinit runner in the resulting ELF files.
- Signed manager identity remains pinned to the existing certificate; no
  manager registration or GKI/LKM SUSFS contract was changed.
- Android cargo check and clippy passed; rustfmt check passed with no edits.
- GKI boot regression: 4 tests passed. Initial ksud suite: 69 tests passed.
- Website dependencies installed from the updated frozen lockfile. Website
  build fails on exactly the same 48 dead-link occurrences recorded before
  this synchronization; no new dead links. See website-baseline-comparison.json.
  The existing documentation links are outside this APK/KO update.
- Clean Android ksud rebuild completed for arm64-v8a and x86_64 before the
  Manager release build. The verified KO matrix replaced shipping assets only
  after validation. The 69-test userspace suite passed again with these assets.

## Final delivery

- `clean assembleRelease :app:testDebugUnitTest` succeeded in 10m 31s.
- Manager tests: 1,149 total in 146 suites; 1,148 passed, 1 skipped, no failures
  or errors. The existing optional live-network test was skipped.
- Actual APK contents were decompressed and hashed: 8 new KernelSU drivers,
  8 separate optional SUSFS modules, updated arm64/x86_64 waitsys, and the
  retained kpimg/kptools runtime. Both packaged ksud binaries match the rebuilt
  sources after the Android build's normal strip transform.
- APK metadata confirms io.github.fixz.stersu, 3.3.3 / 33300. Packaged Manager
  JNI and all 8 drivers use UAPI 6. The removed upstream dynamic-receiver
  permission is absent, and the local weather provider query is preserved.
- APK signature and 16 KiB ZIP alignment checks passed. It is a non-debuggable
  Release build signed with the existing Android Debug certificate matching
  the kernel's pinned identity. No signing key was changed.
- Pre-existing tracked edits still pass a reverse-apply check against the
  original WIP backup. No device was installed or flashed; boot compatibility
  was not tested on hardware.

Artifacts under `release-local/3.3.3-33300-20261025/`:

- `SterSU_3.3.3_33300-upstream-528bafc569-release.apk` (18,678,769 bytes).
  SHA-256: `656559fbb582e40687a95887f2c59e50960034650d8c16c8b50ccb01c446c15f`.
- `SterSU_LKM_33300_KO_20261025.zip` (2,800,443 bytes). Includes 16 raw modules,
  matching source archive, license, build recipe and verification manifest.
  This ZIP is not a recovery-flashable package.
  SHA-256: `6fc6d64a3df8b778db46d9dcec398842255f721a717093a09d135e99288bbe2e`.
- `verification.json`, `ko-manifest.json`, `packaged-assets-verification.json`.

Detailed logs and source provenance are retained in
`out/upstream-sync-20261025-528bafc569/`. Website deployment remains blocked
by the documented 48 pre-existing dead links, unchanged by this sync.
