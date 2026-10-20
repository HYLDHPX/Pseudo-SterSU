"""Install the pinned, unmodified SukiSU Manager runtime pair (stdlib only)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "third_party/sukisu-runtime/SOURCE.json"


def validate(name, data, manifest):
    artifact = manifest["artifacts"][name]
    if len(data) != artifact["size"] or hashlib.sha256(data).hexdigest() != artifact["sha256"]:
        raise ValueError(f"{name}: pinned upstream size/SHA-256 mismatch")
    blob = b"blob " + str(len(data)).encode() + b"\0" + data
    if hashlib.sha1(blob).hexdigest() != artifact["git_blob"]:
        raise ValueError(f"{name}: pinned Git blob mismatch")
    if name == "kpimg":
        version = f"{data[11]}.{data[10]}.{data[9]}"
        if data[:8] != b"KP1158\0\0" or version != manifest["release"]:
            raise ValueError("kpimg: unexpected header/version")
        if not int.from_bytes(data[16:24], "little") & 2:
            raise ValueError("kpimg: Android flag is absent")
    elif data[:7] != b"\x7fELF\x02\x01\x01" or data[18:20] != b"\xb7\0":
        raise ValueError("kptools: expected little-endian AArch64 ELF64")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "userspace/ksud/bin/aarch64")
    parser.add_argument("--check", action="store_true", help="Verify installed files offline; never download")
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    try:
        for name in manifest["artifacts"]:
            validate(name, (args.output / name).read_bytes(), manifest)
    except (OSError, ValueError):
        if args.check:
            raise
        args.output.mkdir(parents=True, exist_ok=True)
        # Validate BOTH downloads before replacing either production asset.
        with tempfile.TemporaryDirectory(prefix=".sukisu-runtime-", dir=args.output) as temporary:
            stage = Path(temporary)
            for name in manifest["artifacts"]:
                url = (f"https://raw.githubusercontent.com/SukiSU-Ultra/SukiSU-Ultra/"
                       f"{manifest['commit']}/{manifest['asset_directory']}/{name}")
                request = urllib.request.Request(url, headers={"User-Agent": "SterSU-runtime-sync"})
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read(manifest["artifacts"][name]["size"] + 1)
                validate(name, data, manifest)
                (stage / name).write_bytes(data)
                (stage / name).chmod(0o755)
            for name in manifest["artifacts"]:
                os.replace(stage / name, args.output / name)
    for name in manifest["artifacts"]:
        validate(name, (args.output / name).read_bytes(), manifest)
        print(f"{name}: {manifest['artifacts'][name]['sha256']}")
    print(f"SukiSU bundled KernelPatch {manifest['release']} verified ({manifest['commit']})")


if __name__ == "__main__":
    main()
