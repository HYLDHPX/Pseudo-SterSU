"""Host state-machine tests of real GKI code, with mocked kernel/Android glue.

Only directory constants are redirected into a temporary test directory. The
real ELF parser, record validation, migration/import/enable/recovery functions
and flat-file store execute on the host. No device or production /data is used.
"""
import json
import os
import pathlib
import re
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "userspace/ksud/src"

GLUE = r'''
#![allow(dead_code, unused_imports)]
use anyhow::{Context, Result, bail, ensure};
use goblin::elf::{Elf, header, section_header};
use serde_json::{Map, Value, json};
use sha256::digest;
use std::collections::{HashMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};
static FAIL_WRITE: AtomicBool = AtomicBool::new(false);
mod defs { pub const KPM_DIR: &str = LEGACY_ROOT; }
mod utils {
    pub static SAFE: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
    pub fn is_safe_mode() -> bool { SAFE.load(std::sync::atomic::Ordering::Relaxed) }
}
mod kpm_backend { pub enum KpmBackend { NativeGki } }
mod ksucalls {
    use super::*;
    pub static LOADED: std::sync::Mutex<Vec<String>> = std::sync::Mutex::new(Vec::new());
    pub static FAIL_LOAD: AtomicBool = AtomicBool::new(false);
    pub fn native_kpm_list() -> std::io::Result<String> { Ok(LOADED.lock().unwrap().join("\n")) }
    pub fn native_kpm_num() -> std::io::Result<i32> { Ok(LOADED.lock().unwrap().len() as i32) }
    pub fn native_kpm_info(_: &str) -> std::io::Result<String> { Ok("runtime fixture".into()) }
    pub fn native_kpm_load(path: &str, _: &str) -> std::io::Result<()> {
        if FAIL_LOAD.load(Ordering::Relaxed) { return Err(std::io::Error::other("injected kernel load failure")); }
        let metadata = parse_kpm(&fs::read(path)?).map_err(std::io::Error::other)?;
        LOADED.lock().unwrap().push(metadata.name); Ok(())
    }
    pub fn native_kpm_unload(name: &str) -> std::io::Result<()> {
        LOADED.lock().unwrap().retain(|id| id != name); Ok(())
    }
}
fn backend_name(_: kpm_backend::KpmBackend) -> &'static str { "native-gki" }
fn ensure_native_loader_ready() -> Result<()> { Ok(()) }
fn native_loader_ready() -> bool { true }
fn kernel_page_size() -> u64 { 4096 }
fn ensure_private_dir(path: &Path) -> Result<()> { fs::create_dir_all(path)?; Ok(()) }
fn current_boot_id() -> Result<String> { Ok("current-boot".into()) }
fn write_atomic(path: &Path, bytes: &[u8]) -> Result<()> {
    ensure!(!FAIL_WRITE.swap(false, Ordering::Relaxed), "injected disk write failure");
    fs::write(path, bytes)?; Ok(())
}
fn remove_marker(path: &Path, _: &str) -> Result<()> {
    match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error.into()),
    }
}
fn kpm_root() -> &'static Path { Path::new(defs::KPM_DIR) }
fn manifest_path(id: &str) -> PathBuf { kpm_root().join(id).join("manifest.json") }
fn hash_and_validate_image(id: &str, manifest: &KpmManifest) -> Result<Vec<u8>> {
    let bytes = fs::read(kpm_root().join(id).join("module.kpm"))?;
    ensure!(digest(&bytes) == manifest.sha256, "legacy image hash mismatch");
    ensure!(parse_kpm(&bytes)?.name == id, "legacy image name mismatch"); Ok(bytes)
}
'''

TESTS = r'''
#[cfg(test)] mod regression {
    use super::*;
    #[test]
    fn migration_management_and_boot_recovery() -> Result<()> {
        let old = Path::new(defs::KPM_DIR).join("legacy");
        fs::create_dir_all(&old)?;
        let legacy = include_bytes!("../legacy.kpm");
        fs::write(old.join("module.kpm"), legacy)?;
        let metadata = parse_kpm(legacy)?;
        let manifest = KpmManifest { metadata, sha256: digest(legacy), args: String::new(),
            enabled: true, quarantined: false, quarantine_reason: String::new(),
            imported_at: "test".into(), source_name: "legacy.kpm".into() };
        fs::write(old.join("manifest.json"), serde_json::to_vec(&manifest_value(&manifest))?)?;
        native::prepare()?;
        assert_eq!(fs::read(Path::new(FLAT_ROOT).join("legacy.kpm"))?, legacy);
        assert!(old.join("module.kpm").exists());
        native::set_enabled("legacy", false)?;
        native::prepare()?; // Never re-enable a migrated disabled image.
        assert!(!Path::new(FLAT_ROOT).join("legacy.kpm").exists());
        assert!(Path::new(FLAT_ROOT).join("legacy.kpm.disabled").exists());

        let input = Path::new(INPUT_ROOT).join("alpha.kpm");
        fs::create_dir_all(INPUT_ROOT)?;
        let first = include_bytes!("../alpha.kpm");
        fs::write(&input, first)?;
        assert!(native::import(&input, "", false, false, false).is_err());
        assert!(native::import(&input, &"x".repeat(255), true, false, false).is_err());
        native::import(&input, "", true, false, false)?;
        let disabled = Path::new(FLAT_ROOT).join("alpha.kpm.disabled");
        assert_eq!(fs::read(&disabled)?, first);
        assert!(native::import(&input, "", true, false, false).is_err());
        FAIL_WRITE.store(true, Ordering::Relaxed);
        assert!(native::set_enabled("alpha", true).is_err());
        assert!(disabled.exists()); // Enable metadata failure must undo rename.
        let previous_pending = br#"{"schemaVersion":1,"bootId":"current-boot","ids":["legacy"]}"#;
        fs::write(Path::new(STATE_ROOT).join(".boot_pending.json"), previous_pending)?;
        native::set_enabled("alpha", true)?;
        assert_eq!(*ksucalls::LOADED.lock().unwrap(), ["alpha"]);
        let pending: Value = serde_json::from_slice(&fs::read(Path::new(STATE_ROOT).join(".boot_pending.json"))?)?;
        assert_eq!(pending["ids"], json!(["legacy"])); // Do not erase the boot batch.
        native::clear_pending()?;
        native::set_enabled("alpha", false)?;
        assert!(ksucalls::LOADED.lock().unwrap().is_empty());

        fs::write(&input, include_bytes!("../alpha-v2.kpm"))?;
        FAIL_WRITE.store(true, Ordering::Relaxed);
        assert!(native::import(&input, "", true, true, false).is_err());
        assert_eq!(fs::read(&disabled)?, first); // Replacement rollback.
        native::set_enabled("alpha", true)?;
        native::set_enabled("alpha", false)?;
        native::import(&input, "", true, true, false)?;
        assert_ne!(fs::read(&disabled)?, first);

        native::set_policy(false)?;
        assert!(native::set_enabled("alpha", true).is_err());
        native::set_policy(true)?;
        ksucalls::FAIL_LOAD.store(true, Ordering::Relaxed);
        assert!(native::set_enabled("alpha", true).is_err());
        assert!(disabled.exists());
        let record = Path::new(STATE_ROOT).join("alpha.json");
        assert_eq!(serde_json::from_slice::<Value>(&fs::read(&record)?)?["quarantined"], true);
        ksucalls::FAIL_LOAD.store(false, Ordering::Relaxed);
        native::set_enabled("alpha", true)?;
        ksucalls::LOADED.lock().unwrap().clear();
        fs::write(Path::new(STATE_ROOT).join(".boot_pending.json"),
            br#"{"schemaVersion":1,"bootId":"previous-boot","ids":["alpha"]}"#)?;
        native::recover()?;
        assert!(disabled.exists());
        assert!(!Path::new(STATE_ROOT).join(".boot_pending.json").exists());
        native::set_enabled("alpha", true)?;
        ksucalls::LOADED.lock().unwrap().clear();
        utils::SAFE.store(true, Ordering::Relaxed);
        native::load_at_boot()?;
        assert!(ksucalls::LOADED.lock().unwrap().is_empty());
        utils::SAFE.store(false, Ordering::Relaxed);
        native::load_at_boot()?;
        assert_eq!(*ksucalls::LOADED.lock().unwrap(), ["alpha"]);
        assert!(Path::new(STATE_ROOT).join(".boot_pending.json").exists());
        native::clear_pending()?;
        native::remove("alpha")?;
        assert!(ksucalls::LOADED.lock().unwrap().is_empty());
        assert!(!disabled.exists() && !record.exists());
        Ok(())
    }
}
'''


def function(source, name):
    match = re.search(rf"^fn {name}(?:<[^\n]+>)?\(", source, re.M)
    if match is None:
        raise ValueError(f"missing real function {name}")
    # Top-level function closing braces are unindented in this source file.
    end = source.index("\n}", match.start()) + 2
    return source[match.start():end] + "\n"


def main():
    clang = pathlib.Path(os.environ["ANDROID_NDK_HOME"]) / "toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
    original = (SRC / "kpm.rs").read_text(encoding="utf-8")
    with tempfile.TemporaryDirectory(prefix="stersu-kpm-native-") as directory:
        root = pathlib.Path(directory)
        (root / "src").mkdir()
        paths = {"FLAT_ROOT": root / "flat", "STATE_ROOT": root / "flat/.stersu",
                 "LEGACY_ROOT": root / "legacy", "INPUT_ROOT": root / "input"}
        roots = "\n".join(f"const {name}: &str = {json.dumps(str(path))};" for name, path in paths.items())
        # Resolve the outer constant explicitly inside the mocked defs module.
        glue = GLUE.replace("KPM_DIR: &str = LEGACY_ROOT", "KPM_DIR: &str = super::LEGACY_ROOT")
        declarations = []
        for name in ("KpmMetadata", "KpmManifest"):
            declarations.append(re.search(rf"struct {name} \{{.*?\n}}", original, re.S)[0])
        declarations.extend(re.findall(r"^const (?:MANIFEST_VERSION|MAX_(?:IMAGE_SIZE|NAME_LEN|VERSION_LEN|LICENSE_LEN|AUTHOR_LEN|DESCRIPTION_LEN|ARGS_LEN|SECTIONS|SECTION_MEMORY_SIZE|INFO_SECTION_SIZE|PENDING_MODULES)|ELF64_[A-Z_]+):[^\n]+", original, re.M))
        helpers = "\n".join(function(original, name) for name in (
            "validate_id", "parse_runtime_names", "validate_metadata_field", "validate_args",
            "section_name", "section_bytes", "parse_metadata", "parse_kpm", "value_string",
            "read_regular_file", "read_manifest", "manifest_value", "monotonic_nonce",
        ))
        native = (SRC / "kpm_native.rs").read_text(encoding="utf-8")
        native = native.replace('const ROOT: &str = "/data/adb/kpm";', 'const ROOT: &str = super::FLAT_ROOT;')
        native = native.replace('const STATE: &str = "/data/adb/kpm/.stersu";', 'const STATE: &str = super::STATE_ROOT;')
        (root / "src/kpm_native.rs").write_text(native, encoding="utf-8")
        (root / "src/main.rs").write_text(
            glue + roots + "\n#[derive(Clone, Debug)]\n" + declarations[0] +
            "\n#[derive(Clone, Debug)]\n" + declarations[1] + "\n" + "\n".join(declarations[2:]) +
            helpers + f'\n#[path = {json.dumps(str(SRC / "kpm_store.rs"))}] mod kpm_store;\n' +
            f'\n#[path = {json.dumps(str(SRC / "kpm_abi.rs"))}] mod kpm_abi;\n' +
            'mod kpm_native; use kpm_native as native;\n' + TESTS, encoding="utf-8",
        )
        (root / "Cargo.toml").write_text('''[package]
name = "stersu-kpm-native-regression"
version = "0.0.0"
edition = "2024"
[dependencies]
anyhow = "1"
serde_json = "1"
sha256 = "1"
goblin = "0.10"
log = "0.4"
chrono = "0.4"
''', encoding="utf-8")
        for name, module, version in (("legacy", "legacy", 1), ("alpha", "alpha", 1), ("alpha-v2", "alpha", 2)):
            fixture = root / f"{name}.c"
            fixture.write_text(
                f'__attribute__((used,section(".kpm.info"))) const char info[] = "name={module}\\0version={version}\\0license=GPL\\0author=test\\0description=fixture\\0";\n'
                'static long init(const char *a,const char *e,void *r) { return 0; }\n'
                'static long exit(void *r) { return 0; }\n'
                '__attribute__((used,section(".kpm.init"))) void *ip = init;\n'
                '__attribute__((used,section(".kpm.exit"))) void *ep = exit;\n', encoding="ascii",
            )
            subprocess.run([str(clang), "--target=aarch64-none-elf", "-ffreestanding", "-fno-stack-protector", "-fno-unwind-tables", "-fno-asynchronous-unwind-tables", "-c", str(fixture), "-o", str(root / f"{name}.kpm")], check=True)
        subprocess.run(["cargo", "test", "--offline", "--manifest-path", str(root / "Cargo.toml"), "--target-dir", str(ROOT / "target/kpm-native-regression"), "--", "--nocapture"], check=True)


if __name__ == "__main__":
    main()
