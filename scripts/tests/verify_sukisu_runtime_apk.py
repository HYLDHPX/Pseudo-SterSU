"""Verify the actual compressed upstream runtime inside an APK's arm64 ksud."""
import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
import subprocess
import tempfile
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
COMPRESSOR = r'''
use std::{env, fs, io::Write, path::Path};
fn main() {
    let args: Vec<_> = env::args().collect();
    for name in ["kpimg", "kptools"] {
        let bytes = fs::read(Path::new(&args[1]).join(name)).unwrap();
        let mut encoder = libflate::deflate::Encoder::new(Vec::new());
        encoder.write_all(&bytes).unwrap();
        fs::write(Path::new(&args[2]).join(name), encoder.finish().into_result().unwrap()).unwrap();
    }
}
'''


def embedded_resource(binary, expected, compressed):
    offset = binary.find(compressed)
    if offset >= 0:
        return offset, zlib.decompress(binary[offset:offset + len(compressed)], -15)
    # Different host/target encoder builds can choose different Huffman tables.
    # Validate the real stream, not just the predicted compressed byte sequence.
    view = memoryview(binary)
    offset = -1
    while True:
        offset = binary.find(compressed[:1], offset + 1)
        if offset < 0:
            raise ValueError("expected compressed resource is absent from packaged ksud")
        try:
            decoder = zlib.decompressobj(-15)
            if decoder.decompress(view[offset:offset + 16384], 64) != expected[:64]:
                continue
            decoder = zlib.decompressobj(-15)
            actual = decoder.decompress(view[offset:offset + 2 * len(expected)], len(expected) + 1)
            if decoder.eof and actual == expected:
                return offset, actual
        except zlib.error:
            continue


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--strip-tool", type=Path, help="NDK llvm-strip used by the Android build")
    args = parser.parse_args()
    manifest = json.loads((ROOT / "third_party/sukisu-runtime/SOURCE.json").read_text())
    assets = ROOT / "userspace/ksud/bin/aarch64"
    result = {"apk": str(args.apk.resolve()), "size": args.apk.stat().st_size,
              "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              "runtime_version": manifest["release"], "runtime_upstream": manifest["commit"],
              "jni": {}, "embedded_runtime": {}}
    with zipfile.ZipFile(args.apk) as archive:
        for abi in ("arm64-v8a", "x86_64"):
            packed = archive.read(f"lib/{abi}/libksud.so")
            original = ROOT / f"manager/app/src/main/jniLibs/{abi}/libksud.so"
            expected = original.read_bytes()
            if packed != expected:
                # AGP strips executable section metadata even for Rust's
                # already-stripped ELF; compare against the same NDK transform.
                strip = args.strip_tool
                if strip is None:
                    ndk = Path(os.environ["ANDROID_NDK_HOME"])
                    host = "windows-x86_64" if os.name == "nt" else "darwin-x86_64" if sys.platform == "darwin" else "linux-x86_64"
                    strip = ndk / "toolchains/llvm/prebuilt" / host / "bin" / ("llvm-strip.exe" if os.name == "nt" else "llvm-strip")
                with tempfile.TemporaryDirectory(prefix="stersu-jni-normalize-") as directory:
                    normalized = Path(directory) / "libksud.so"
                    subprocess.run([str(strip), "--strip-unneeded", "-o", str(normalized), str(original)], check=True)
                    expected = normalized.read_bytes()
            if packed != expected:
                raise ValueError(f"{abi}: APK does not contain the newly staged ksud")
            result["jni"][abi] = hashlib.sha256(packed).hexdigest()
        arm64 = archive.read("lib/arm64-v8a/libksud.so")
    with tempfile.TemporaryDirectory(prefix="stersu-runtime-verify-") as directory:
        stage = Path(directory)
        (stage / "src").mkdir()
        (stage / "src/main.rs").write_text(COMPRESSOR, encoding="ascii")
        (stage / "Cargo.toml").write_text('''[package]
name = "stersu-runtime-compressor"
version = "0.0.0"
edition = "2024"
[dependencies]
libflate = "=2.3.2"
''', encoding="ascii")
        subprocess.run(["cargo", "run", "--offline", "--manifest-path", str(stage / "Cargo.toml"),
                        "--target-dir", str(ROOT / "target/runtime-verify"), "--",
                        str(assets), str(stage)], check=True)
        for name, artifact in manifest["artifacts"].items():
            expected = (assets / name).read_bytes()
            compressed = (stage / name).read_bytes()
            if zlib.decompress(compressed, -15) != expected:
                raise ValueError(f"{name}: compression roundtrip failed")
            offset, actual = embedded_resource(arm64, expected, compressed)
            if hashlib.sha256(actual).hexdigest() != artifact["sha256"]:
                raise ValueError(f"{name}: packaged runtime hash mismatch")
            result["embedded_runtime"][name] = {"offset": offset, "size": len(actual),
                                                   "sha256": artifact["sha256"]}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
