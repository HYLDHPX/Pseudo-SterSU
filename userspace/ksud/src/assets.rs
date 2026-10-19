use anyhow::Result;
use rust_embed::RustEmbed;

#[cfg(target_os = "android")]
mod android {
    use crate::assets::Asset;
    use crate::defs::BINARY_DIR;
    use crate::utils::ensure_binary;
    use const_format::concatcp;

    pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
    pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
    pub const BOOTCTL_PATH: &str = concatcp!(BINARY_DIR, "bootctl");

    pub fn ensure_binaries(ignore_if_exist: bool) -> anyhow::Result<()> {
        for file in Asset::iter() {
            if matches!(file.as_ref(), "ksuinit" | "kpimg" | "kptools") || file.ends_with(".ko") {
                // don't extract ksuinit and kernel modules
                continue;
            }
            let asset =
                Asset::get(&file).ok_or_else(|| anyhow::anyhow!("asset not found: {file}"))?;
            ensure_binary(format!("{BINARY_DIR}{file}"), &asset.data, ignore_if_exist)?;
        }

        // Create resetprop -> ksud symlink (resetprop is now built into ksud)
        let resetprop_link = RESETPROP_PATH;
        let _ = std::fs::remove_file(resetprop_link);
        std::os::unix::fs::symlink("/data/adb/ksud", resetprop_link)?;

        Ok(())
    }
}

#[cfg(target_os = "android")]
pub use android::*;

#[cfg(all(target_arch = "x86_64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/x86_64"]
struct Asset;

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/aarch64"]
struct Asset;

// If not Android, ie. macos, linux, windows, include both
#[cfg(not(target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin"]
struct Asset;

#[allow(unused)]
pub fn get_asset_data(name: &str) -> Result<std::borrow::Cow<'static, [u8]>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(asset.data)
}

pub fn get_asset(name: &str) -> Result<Box<dyn AsRef<[u8]>>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(Box::new(asset.data))
}

pub fn list_supported_kmi() -> std::vec::Vec<std::string::String> {
    let mut list = Vec::new();
    for file in Asset::iter() {
        // kmi_name = "xxx_kernelsu.ko"
        if let Some(kmi) = file.strip_suffix("_kernelsu.ko") {
            list.push(kmi.to_string());
        }
    }
    list
}

#[cfg(all(test, not(target_os = "android")))]
mod tests {
    use super::*;

    #[test]
    fn embedded_kernel_modules_match_current_files() {
        let asset_dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("bin");
        let modules: Vec<_> = Asset::iter().filter(|name| name.ends_with(".ko")).collect();
        assert!(!modules.is_empty(), "no bundled kernel modules");

        for name in modules {
            let embedded = get_asset_data(&name).expect("embedded module is readable");
            let source = std::fs::read(asset_dir.join(name.as_ref()))
                .expect("bundled module source is readable");
            assert_eq!(embedded.as_ref(), source.as_slice(), "stale asset: {name}");
        }
    }

    #[test]
    fn bundled_drivers_have_matching_optional_susfs_assets() {
        for kmi in list_supported_kmi() {
            let name = format!("{kmi}_susfs_guard_lkm.ko");
            let module = get_asset_data(&name).expect("matching optional SUSFS asset");
            assert!(module.starts_with(b"\x7fELF"), "invalid module: {name}");
        }
    }
}
