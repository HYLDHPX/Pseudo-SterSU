#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if [[ "${BUILD_FROM_SOURCE:-0}" != 1 ]]; then
    if [[ "${NO_INSTALL:-0}" == 1 || "${KEEP_STAGING:-0}" == 1 || -n "${SOURCE_PATH:-}" ]]; then
        echo "Source-build options require BUILD_FROM_SOURCE=1 and a non-production OUTPUT_DIR." >&2
        exit 2
    fi
    args=()
    if [[ -n "${OUTPUT_DIR:-}" ]]; then args+=(--output "$OUTPUT_DIR"); fi
    exec python3 "$ROOT/scripts/sync_sukisu_runtime.py" "${args[@]}"
fi
if [[ -z "${OUTPUT_DIR:-}" || "$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$OUTPUT_DIR")" == "$ROOT/userspace/ksud/bin/aarch64" ]]; then
    echo "Experimental source builds require a non-production OUTPUT_DIR; release assets stay pinned to SukiSU." >&2
    exit 2
fi
SOURCE=${SOURCE_PATH:-"$ROOT/third_party/kernelpatch"}
OUTPUT=${OUTPUT_DIR:-"$ROOT/userspace/ksud/bin/aarch64"}
NDK=${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}
NO_INSTALL=${NO_INSTALL:-0}
KEEP_STAGING=${KEEP_STAGING:-0}
SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-1792255305}

if [[ -z "$NDK" ]]; then
    echo "Set ANDROID_NDK_HOME/ANDROID_NDK_ROOT before building kernelpatch." >&2
    exit 2
fi
if [[ ! "$SOURCE_DATE_EPOCH" =~ ^[0-9]+$ ]]; then
    echo "SOURCE_DATE_EPOCH must be an unsigned Unix timestamp." >&2
    exit 2
fi
export SOURCE_DATE_EPOCH
export TZ=UTC
export LC_ALL=C

case "$(uname -s)" in
    Linux*) HOST_TAG=linux-x86_64 ;;
    Darwin*) HOST_TAG=darwin-x86_64 ;;
    *) echo "Unsupported host: $(uname -s)" >&2; exit 2 ;;
esac

LLVM_BIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
CLANG="$LLVM_BIN/aarch64-linux-android35-clang"
STRIP="$LLVM_BIN/llvm-strip"
MAKE=${MAKE:-make}
STAGE="$ROOT/out/kernelpatch-build"
KERNEL_STAGE="$STAGE/kernel"
TOOLS_STAGE="$STAGE/tools"
SYSROOT_LIB="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/sysroot/usr/lib/aarch64-linux-android"

cleanup() {
    if [[ "$KEEP_STAGING" != 1 ]]; then
        rm -rf "$STAGE"
    fi
}
trap cleanup EXIT

rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -a "$SOURCE/." "$STAGE/"
find "$STAGE" -type f \( -name '*.o' -o -name '*.elf' -o -name 'kpimg' -o -name 'kpimg-test' -o -name 'kptools' \) -delete
rm -f "$KERNEL_STAGE/patch/common/user_event.c" \
      "$KERNEL_STAGE/patch/android/userd.c" \
      "$STAGE/patch/encrypt"
cp "$KERNEL_STAGE/include/preset.h" "$TOOLS_STAGE/preset.h"

"$MAKE" -C "$KERNEL_STAGE" TARGET_COMPILE="$LLVM_BIN" ANDROID=1 -j"${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}"
"$MAKE" -C "$TOOLS_STAGE" CC="$CLANG" \
    LDFLAGS="-static -L$SYSROOT_LIB -lz" \
    -j"${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}"

if [[ -x "$STRIP" ]]; then
    "$STRIP" --strip-unneeded "$TOOLS_STAGE/kptools"
fi

[[ -s "$KERNEL_STAGE/kpimg" ]] || { echo "kpimg was not produced" >&2; exit 1; }
[[ "$(head -c 6 "$KERNEL_STAGE/kpimg")" == "KP1158" ]] || { echo "invalid kpimg header" >&2; exit 1; }
[[ -s "$TOOLS_STAGE/kptools" ]] || { echo "kptools was not produced" >&2; exit 1; }
"$LLVM_BIN/llvm-readelf" -h "$TOOLS_STAGE/kptools" | grep -q 'AArch64'
if "$LLVM_BIN/llvm-readelf" -l "$TOOLS_STAGE/kptools" | grep -q 'INTERP'; then
    echo "kptools contains PT_INTERP and is not statically linked" >&2
    exit 1
fi

mkdir -p "$OUTPUT"
if [[ "$NO_INSTALL" != 1 ]]; then
    install -m 0755 "$KERNEL_STAGE/kpimg" "$OUTPUT/kpimg"
    install -m 0755 "$TOOLS_STAGE/kptools" "$OUTPUT/kptools"
fi

sha256sum "$KERNEL_STAGE/kpimg" "$TOOLS_STAGE/kptools"
if [[ "$NO_INSTALL" != 1 ]]; then
    echo "installed: $OUTPUT/kpimg"
    echo "installed: $OUTPUT/kptools"
fi
