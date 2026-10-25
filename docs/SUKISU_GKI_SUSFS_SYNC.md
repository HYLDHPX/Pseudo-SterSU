# SukiSU-Ultra GKI SUSFS synchronization

Scope: synchronize applicable GKI SUSFS changes from the current
SukiSU-Ultra/SukiSU-Ultra upstream into SterSU. Preserve the separate LKM
SUSFS implementation and existing uncommitted work. Do not package an APK,
replace bundled KO files, install an app, or flash a device.

## Plan

- [x] Pin upstream main, identify relevant SUSFS commits and compare complete
  kernel / userspace / Manager interface paths against the local fork.
- [x] Back up affected sources and record LKM boundary hashes before edits.
- [x] Port applicable upstream GKI SUSFS changes with compatible feature
  detection and preserve the existing kernel/userspace ABI contracts.
- [x] Run affected kernel regression checks, Android Rust check / clippy /
  formatting checks when Rust changes, and Manager source checks when needed.
- [x] Audit the final diff and source provenance, document verified coverage
  and any changes requiring a separately built/flashed GKI kernel.

## Initial state

- Workspace: E:/KernelSU-main; Windows PowerShell, WSL Ubuntu.
- Manager 3.3.3 / LKM 33300 and prior upstream synchronization remain intact.
- Many pre-existing UI edits and rebuilt KO payloads are present; preserve them.
- Last separately recorded SukiSU KPM reference: 31255c460cae (2026-10-19).
  This does not establish the SUSFS baseline; determine it from source history.

## Verified upstream references (2026-10-25)

- Main: `72eea67e2307cb9d257a4bbd8dd6e64f10724bc5` (2026-10-24 UTC).
- Built-in GKI branch: `ce75122bec7423e0f1fa06e3e9a174085c5231ab`
  (2026-10-21 UTC). Its SUSFS hooks are in `hook/lsm_hook.c`,
  `feature/sucompat.c`, `runtime/ksud.c` and `supercall/dispatch.c`.
- Also checked `builtin-new` (`c105589f7a64de73d46a910248b25150226e2d69`),
  `builtin-rebase` (`ee35e65428054476c279eae59f7f2bba272040b9`) and
  `susfs_new` (`278d822a4ebd214bcfd774b7910cb11cdc560bb9`, last changed
  2026-07-07). The latter is not the current built-in branch.
- Relevant changes: `873b5c06` (ABI implementation detection and Manager
  improvements), `2d2f3ed1` (nonblocking backup/restore and cancellation),
  `242dbc4d` (saved configuration versus runtime evidence), and `5a2bb7e5`
  (end the boot key-detection window to prevent spurious SUSFS safe mode).

The main branch uses a SUSFS extension advertisement stub. It must not replace
the local built-in GKI reboot dispatcher. The built-in branch still uses the
same command family and zygote / zygote_next handling as the local GKI path.
Existing local seccomp fallback, init-path checks, preinit capability gating,
and return-code handling are retained. No extension-hub ABI, mount-hide
subsystem, or general KernelSU rebase is imported as part of this SUSFS sync.

## Applied compatibility changes

- Add upstream-compatible `ksud susfs implementation` / `show implementation`
  queries. Identify `GKI-LKM` or `SUSFS_LKM_MODULES` via the kernel ABI, with
  built-in as the fallback for a supported implementation. Failed read-only
  syscalls cannot leave buffer contents falsely reporting support.
- Include kernel variant and implementation in the GKI status card. A detected
  LKM implementation disables GKI operations and directs users to the existing
  independent LKM page. Old tools without the new query retain compatibility.
- Preserve per-generation application results and the existing persistent
  draft / checked-save mechanism. Explicitly distinguish command success from
  verification of hiding in an already running app.
- Retain existing IO-dispatched backup/export/import; add cancellation
  propagation and finally-based loading cleanup to GKI page operations.
- Adapt the upstream safe-mode fix to the local direct-input-hook architecture:
  the callback itself checks the static key. This avoids importing the
  upstream input_handler machinery into an already patched GKI source tree.
  A host C regression reproduced false safe-mode activation before the guard;
  boot-volume rescue and stopped callbacks are tested separately.

## Preservation and verification

- Pre-edit source backup: `out/backup-before-sukisu-gki-susfs-20261025/`.
- `boundary-hashes.json` records 124 LKM, KO, JNI, version and existing release
  files before editing. The independent LKM source / policy / Manager UI and
  bundled binaries must remain byte-identical.
- GKI source patch pins in `.github/config/gki-commits.json` are unchanged;
  they refer to simonpunk's source repository, not SukiSU's branch revisions.
- No device install, flash, KO payload replacement or APK packaging is part of
  this task. Kernel callback changes require a future GKI kernel build to take
  effect on a device; source checks do not establish device boot compatibility.

## Validation environment and results

- Windows, PowerShell 7.6.5; WSL Ubuntu, Python 3.14.4 and GCC 15.2.0.
- Rust / Cargo 1.97.1; Android NDK 29.0.14206865. Required sequence completed:
  `cargo ndk -t arm64-v8a check --offline`,
  `cargo ndk -t arm64-v8a clippy --offline -- -D warnings`, then `cargo fmt`.
  Check, lint and formatting exited successfully. Existing host ksud tests:
  69 passed. Since the SUSFS module is Android/AArch64-gated, its two pure ABI
  marker tests were additionally extracted verbatim and executed with host
  `rustc --test`; both passed.
- WSL GKI boot integration: 4 tests passed. Safe-mode callback regression:
  5 tests passed, including the 2 cases that failed before the callback guard.
  Logs: `out/sukisu-susfs-kernel-tests.log`.
- Manager checks use Temurin Java 21.0.10 and the project's offline Gradle
  dependencies. `:app:compileDebugKotlin` and `:app:testDebugUnitTest` succeed;
  final results: 1,158 tests in 147 suites, 1,157 passed, 1 skipped, no failures
  or errors. This includes 7 implementation-detection cases and 3 draft /
  cancellation cases. See `out/sukisu-susfs-manager-verified.log`,
  `out/sukisu-susfs-manager-test-summary.json` and the XML unit-test reports.
- All 124 preservation hashes matched after the final checks. The LKM wrapper
  region inside the shared Manager CLI was also compared with the source
  backup and is unchanged. No pre-existing dirty path disappeared from Git
  status. `git diff --check` passed for the edited source and notice files.
- The initial Manager pass reported pre-existing translated-resource warnings
  for five missing default keys and Gradle deprecation notices. No new SUSFS
  resource warning was reported. All four edited resource XML files parse and
  have no duplicate names.
- No GKI image was built or flashed, and no on-device SUSFS behavior or boot
  compatibility is claimed by the host tests. Existing release APK / KO assets
  are preserved rather than silently replacing them with untested builds.
