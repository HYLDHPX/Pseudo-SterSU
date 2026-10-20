# ApkeSU Native KPM image path

ApkeSU Native KPM is a GKI early-boot path. It is separate from the existing
LKM and KPatch-Next path:

```text
boot.img kernel Image
    -> kptools -p appends kpimg and rewrites the kernel entry
    -> kpimg resolves early kernel symbols and hooks sukisu_kpm_*
    -> the original kernel entry continues
```

The hook bridge is the Native KPM ABI already exported by ApkeSU's built-in
kernel implementation. The early runtime is not loaded by `ksud` and must be
present before the kernel reaches the normal Android userspace.

The boot kernel must be AArch64 and built with `CONFIG_KSU=y` and
`CONFIG_KPM=y`. LKM (`CONFIG_KSU=m`) and late-load builds do not expose this
Native GKI path; they remain separate from KPatch-Next.

For upstream attribution, interface scope, licensing boundaries, and the
distinction between the Native GKI and LKM backends, see the
[SukiSU-compatible GKI KPM notice](SUKISU_KPM_NOTICE.md).

## Build

The source snapshot is recorded in `third_party/kernelpatch/SOURCE.json`.
The build is isolated under `out/` and installs two AArch64 Android assets:

```powershell
$env:ANDROID_NDK_HOME = 'F:\ApkeSU-Toolchain\AndroidNDK\29.0.14206865'
pwsh -File scripts/build_kernelpatch.ps1
```

On Linux:

```sh
export ANDROID_NDK_HOME=/opt/android-ndk
scripts/build_kernelpatch.sh
```

The scripts print SHA-256 values and install:

```text
userspace/ksud/bin/aarch64/kpimg
userspace/ksud/bin/aarch64/kptools
```

`kpimg` is a raw binary with the `KP1158` header. `kptools` is a static
AArch64 ELF executable and is only executable on an AArch64 Android device.
Both scripts set `SOURCE_DATE_EPOCH` to the vendored source commit time by
default, so identical source and toolchains produce stable assets. Set the
environment variable explicitly when a release requires another timestamp.

## Patch a boot image

The userspace CLI has an explicit command so the existing ramdisk/LKM patcher
cannot be selected accidentally:

```text
ksud boot-patch-kpimg --boot stock-boot.img --output apkesu-kpimg-boot.img
```

Use `--kpimg` and `--kptools` to override the embedded/device assets. On
Android the embedded defaults are extracted into a unique private temporary
directory beside the requested output and removed when the command exits. The
command:

1. parses an Android boot image and requires a kernel block;
2. decompresses the kernel and verifies an uncompressed ARM64 `Image` header;
3. validates both tools before invoking `kptools` with argument arrays;
4. verifies the patched kernel contains `KP1158`;
5. recompresses the kernel using the original boot-image compression;
6. reparses the output and atomically publishes it only after verification.

`init_boot.img` and `vendor_boot.img` are rejected because they do not carry
the kernel entry that this early takeover requires. The output is not AVB
signed and no flashing is performed. Always start from the device's original,
matching `boot.img`; an already injected image is rejected.

To inspect an image without modifying it:

```text
ksud boot-info-kpimg --boot apkesu-kpimg-boot.img
```

The command reports whether a kernel exists, its uncompressed size, ARM64
validity, and the `kpimg` marker offset. A successful host-side patch still
does not prove a device will boot: AVB signing, vendor boot layout, kernel
KMI, page size, and OEM boot-chain policy must be verified separately.

## Source provenance

The SukiSU-Ultra integration reference and the vendored KernelPatch source are
pinned rather than fetched during a release build:

```text
SukiSU-Ultra reference: 9fbe8fe8ca90c62c259c5894bf96d02ac31209b9
KernelPatch 0.13.9:     72bf62025e9c72e07b64c77db0801891f1088429
```

`third_party/kernelpatch/SOURCE.json` records the source, license, excluded
files, and local hardening applied to the runtime and image patcher.

## Runtime updates and load errors

Version audit on 2026-10-20: SterSU previously bundled KernelPatch 0.13.2.
The SukiSU-Ultra `main` and `v4.2.1` Manager asset headers report 0.13.0,
while the separate SukiSU_KernelPatch_patch release is 0.13.9. The Manager
release number is not the KernelPatch runtime version; the previous SterSU
asset was not older than that Manager asset. This update uses the separately
released 0.13.9 source pinned above, with SterSU's integration checks retained.

Audit sources:

- https://github.com/SukiSU-Ultra/SukiSU-Ultra/blob/v4.2.1/manager/app/src/main/assets/kpimg
- https://github.com/SukiSU-Ultra/SukiSU_KernelPatch_patch/releases/tag/0.13.9

The Manager reports both the running KernelPatch version and the version
bundled in this APK. Installing an APK does not replace the runtime already
in a device's boot image. Updating that runtime requires generating a new
image from the device's original, matching, KPM-enabled GKI boot image and
following the device's verified recovery/installation procedure. No image
is flashed automatically by this update.

The 0.13.9 snapshot adds upstream KPM event/BTF/configuration support and
exports including `symbol_lookup_name` and the feature/symbol-list APIs.
SterSU retains its local ELF/file/storage limits, bridge rollback, bounded
output, and operation safety checks. The optional SELinux hide/policy sources
are not compiled or initialized in this KPM-only integration. The relocated
map is compiled for size to retain the upstream 4 KiB linker safety bound.
Upstream task-hook file logging is disabled to avoid filesystem I/O from
those kernel execution contexts.

Undefined module imports now return `ENOLINK`, separately from file-open
`ENOENT`. Legacy runtimes can still return `ENOENT` for either condition;
ksud rechecks the local image and reports the ambiguity rather than claiming
that an already-validated module file is missing. The kernel log contains
the actual `unknown symbol` name. Callback pointer checks happen after ELF
relocation, when their values are populated. Quarantine and boot recovery
markers remain enabled. A newer runtime cannot guarantee that a third-party
KPM supports a particular device, kernel, or KMI.
