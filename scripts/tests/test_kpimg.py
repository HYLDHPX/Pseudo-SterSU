"""Run the real Rust boot-image patcher tests on the host, without a device."""
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    with tempfile.TemporaryDirectory(prefix="stersu-kpimg-test-") as directory:
        stage = Path(directory)
        (stage / "src").mkdir()
        source = ROOT / "userspace/ksud/src/kpimg.rs"
        (stage / "src/lib.rs").write_text(
            '#![allow(dead_code)]\n'
            'mod assets {\n'
            'pub fn get_asset_data(_: &str) -> anyhow::Result<std::borrow::Cow<\'static, [u8]>> {\n'
            'anyhow::bail!("default extraction is not used in the host fixture")\n}\n}\n'
            f'#[path = {json.dumps(str(source))}] mod kpimg;\n', encoding="utf-8")
        (stage / "Cargo.toml").write_text('''[package]
name = "stersu-kpimg-regression"
version = "0.0.0"
edition = "2024"
[dependencies]
anyhow = "1"
clap = { version = "4", features = ["derive"] }
sha256 = "1"
serde_json = "1"
tempfile = "3"
android-bootimg = { git = "https://github.com/5ec1cff/android_bootimg", rev = "150425b027c76ea104c82e408571651f2181b2c2" }
''', encoding="utf-8")
        subprocess.run(["cargo", "test", "--offline", "--manifest-path", str(stage / "Cargo.toml"),
                        "--target-dir", str(ROOT / "target/kpimg-regression")], check=True)


if __name__ == "__main__":
    main()
