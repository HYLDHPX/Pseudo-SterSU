//! Optional loader for the SUSFS guard LKM vendored from KernelSU-Next.
//! The built-in GKI SUSFS implementation and its configuration are independent.

use anyhow::{Context, Result, bail, ensure};
use log::{info, warn};
use std::ffi::CString;
use std::fs;
use std::path::Path;

use crate::susfs::cmd::{paths, spoof, status};
use crate::{assets, boot_patch, ksucalls, module_config};

const CONFIG_ID: &str = "internal.ksud.susfsd";
const ENABLED_KEY: &str = "autoload.enabled";
const KMI_KEY: &str = "autoload.kmi";
const ERROR_KEY: &str = "autoload.error";
const GUARD_FEATURE: &str = "SUSFS_LKM_MODULES";
const PATH_FEATURE: &str = "CONFIG_KSU_SUSFS_SUS_PATH";
const LOG_FEATURE: &str = "CONFIG_KSU_SUSFS_ENABLE_LOG";
const MOUNT_FEATURE: &str = "CONFIG_KSU_SUSFS_SUS_MOUNT";
const UNAME_FEATURE: &str = "CONFIG_KSU_SUSFS_SPOOF_UNAME";
const PATHS_KEY: &str = "policy.paths";
const LOG_KEY: &str = "policy.logging";
const MOUNT_KEY: &str = "policy.hide_mounts";
const UNAME_KEY: &str = "policy.uname";
const POLICY_ERROR_KEY: &str = "policy.error";
const PATH_CONTROL: &str = "/sys/module/susfs_guard_lkm/parameters/hide_list";
const MAX_PATHS: usize = 256;

fn config_value(key: &str) -> Result<Option<String>> {
    module_config::get_config_value(CONFIG_ID, key, module_config::ConfigType::Persist)
}

fn set_config(key: &str, value: &str) -> Result<()> {
    module_config::set_config_value(CONFIG_ID, key, value, module_config::ConfigType::Persist)
}

fn require_lkm() -> Result<()> {
    ensure!(
        ksucalls::get_version() > 0,
        "KernelSU driver is unavailable"
    );
    ensure!(
        ksucalls::is_lkm_mode() && !ksucalls::is_late_load(),
        "SUSFS guard loading requires the normal LKM mode"
    );
    Ok(())
}

fn current_kmi() -> Result<String> {
    let kmi = boot_patch::get_current_kmi().context("Could not detect the running kernel KMI")?;
    ensure!(
        kmi.starts_with("android")
            && kmi.len() <= 64
            && kmi
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-')),
        "Invalid running kernel KMI: {kmi}"
    );
    Ok(kmi)
}

fn asset_name(kmi: &str) -> String {
    format!("{kmi}_susfs_guard_lkm.ko")
}

fn guard_loaded() -> bool {
    status::get_susfs_status()
        && status::get_susfs_features()
            .lines()
            .any(|line| line == GUARD_FEATURE)
}

fn require_guard() -> Result<()> {
    require_lkm()?;
    ensure!(guard_loaded(), "SUSFS LKM guard is not loaded");
    Ok(())
}

fn require_feature(feature: &str) -> Result<()> {
    require_guard()?;
    ensure!(
        status::get_susfs_features()
            .lines()
            .any(|line| line == feature),
        "SUSFS LKM feature {feature} is unavailable"
    );
    Ok(())
}

fn normalized_path(path: &str) -> Result<String> {
    let normalized = path.trim_end_matches('/');
    let normalized = if normalized.is_empty() {
        "/"
    } else {
        normalized
    };
    ensure!(
        normalized.starts_with('/')
            && normalized != "/"
            && normalized.len() < 255
            && !normalized.ends_with(' ')
            && !normalized.bytes().any(|byte| byte.is_ascii_control()),
        "SUSFS path must be an absolute path shorter than 255 bytes"
    );
    ensure!(
        !normalized
            .split('/')
            .any(|part| part == "." || part == ".."),
        "SUSFS path cannot contain . or .. components"
    );
    Ok(normalized.to_owned())
}

fn configured_paths() -> Result<Vec<String>> {
    let paths = config_value(PATHS_KEY)?.unwrap_or_default();
    let entries: Vec<String> = paths.lines().map(str::to_owned).collect();
    ensure!(
        entries.len() <= MAX_PATHS,
        "SUSFS LKM path list is too large"
    );
    for path in &entries {
        ensure!(
            normalized_path(path)? == *path,
            "Invalid stored SUSFS LKM path"
        );
    }
    Ok(entries)
}

fn configured_uname() -> Result<Option<(String, String)>> {
    let Some(value) = config_value(UNAME_KEY)? else {
        return Ok(None);
    };
    if value.is_empty() {
        return Ok(None);
    }
    let (release, version) = value
        .split_once('\t')
        .context("Invalid stored SUSFS LKM uname config")?;
    validate_uname(release, version)?;
    Ok(Some((release.to_owned(), version.to_owned())))
}

fn validate_uname(release: &str, version: &str) -> Result<()> {
    for value in [release, version] {
        ensure!(
            !value.is_empty()
                && value.len() < 65
                && !value.bytes().any(|byte| byte.is_ascii_control()),
            "Kernel release and build version must each be 1-64 bytes without control characters"
        );
    }
    Ok(())
}

fn apply_policy() -> Result<()> {
    require_guard()?;
    if let Some(value) = config_value(LOG_KEY)? {
        require_feature(LOG_FEATURE)?;
        spoof::enable_log(module_config::parse_bool_config(&value))?;
    }
    if let Some(value) = config_value(MOUNT_KEY)? {
        require_feature(MOUNT_FEATURE)?;
        spoof::hide_sus_mnts_for_non_su_procs(module_config::parse_bool_config(&value))?;
    }
    if let Some((release, version)) = configured_uname()? {
        require_feature(UNAME_FEATURE)?;
        spoof::set_uname(&release, &version)?;
    }
    for path in configured_paths()? {
        require_feature(PATH_FEATURE)?;
        paths::add_sus_path_loop(&path).with_context(|| format!("Could not hide {path}"))?;
    }
    Ok(())
}

fn update_bool(
    key: &str,
    feature: &str,
    value: &str,
    apply: impl FnOnce(bool) -> Result<()>,
) -> Result<()> {
    require_feature(feature)?;
    let enabled = match value {
        "0" => false,
        "1" => true,
        _ => bail!("SUSFS LKM toggle must be 0 or 1"),
    };
    let previous = config_value(key)?;
    set_config(key, value)?;
    if let Err(error) = apply(enabled) {
        if let Some(previous) = previous {
            let _ = set_config(key, &previous);
        } else {
            let _ = module_config::delete_config_value(
                CONFIG_ID,
                key,
                module_config::ConfigType::Persist,
            );
        }
        let _ = set_config(POLICY_ERROR_KEY, &format!("{error:#}"));
        return Err(error);
    }
    set_config(POLICY_ERROR_KEY, "")?;
    Ok(())
}

fn set_uname(release: &str, version: &str) -> Result<()> {
    require_feature(UNAME_FEATURE)?;
    validate_uname(release, version)?;
    let previous = config_value(UNAME_KEY)?.unwrap_or_default();
    set_config(UNAME_KEY, &format!("{release}\t{version}"))?;
    if let Err(error) = spoof::set_uname(release, version) {
        let _ = set_config(UNAME_KEY, &previous);
        let _ = set_config(POLICY_ERROR_KEY, &format!("{error:#}"));
        return Err(error);
    }
    set_config(POLICY_ERROR_KEY, "")?;
    Ok(())
}

fn clear_uname() -> Result<()> {
    require_feature(UNAME_FEATURE)?;
    // This LKM accepts "default" to restore the live kernel strings.
    spoof::set_uname("default", "default")?;
    set_config(UNAME_KEY, "")?;
    set_config(POLICY_ERROR_KEY, "")?;
    Ok(())
}

fn add_path(path: &str) -> Result<()> {
    require_feature(PATH_FEATURE)?;
    let path = normalized_path(path)?;
    let mut entries = configured_paths()?;
    if entries.contains(&path) {
        return Ok(());
    }
    ensure!(entries.len() < MAX_PATHS, "SUSFS LKM path list is full");
    let previous = entries.join("\n");
    entries.push(path.clone());
    set_config(PATHS_KEY, &entries.join("\n"))?;
    if let Err(error) =
        paths::add_sus_path_loop(&path).with_context(|| format!("Could not hide {path}"))
    {
        let _ = set_config(PATHS_KEY, &previous);
        let _ = set_config(POLICY_ERROR_KEY, &format!("{error:#}"));
        return Err(error);
    }
    set_config(POLICY_ERROR_KEY, "")?;
    Ok(())
}

fn remove_path(path: &str) -> Result<()> {
    require_feature(PATH_FEATURE)?;
    let path = normalized_path(path)?;
    let mut entries = configured_paths()?;
    if !entries.contains(&path) {
        return Ok(());
    }
    let previous = entries.join("\n");
    entries.retain(|entry| entry != &path);
    set_config(PATHS_KEY, &entries.join("\n"))?;
    if let Err(error) = fs::write(Path::new(PATH_CONTROL), format!("del {path}")) {
        let _ = set_config(PATHS_KEY, &previous);
        let _ = set_config(
            POLICY_ERROR_KEY,
            &format!("Could not remove {path}: {error}"),
        );
        bail!("Could not remove {path} from the running LKM: {error}");
    }
    set_config(POLICY_ERROR_KEY, "")?;
    Ok(())
}

pub fn show_status() -> Result<()> {
    let mode = if ksucalls::is_late_load() {
        "late-load"
    } else if ksucalls::is_lkm_mode() {
        "lkm"
    } else {
        "gki"
    };
    let kmi = current_kmi().unwrap_or_default();
    let supported =
        mode == "lkm" && !kmi.is_empty() && assets::get_asset_data(&asset_name(&kmi)).is_ok();
    let loaded = guard_loaded();
    let version = if loaded {
        status::get_susfs_version()
    } else {
        String::new()
    };
    let autoload = config_value(ENABLED_KEY)?
        .as_deref()
        .is_some_and(module_config::parse_bool_config);
    let last_error = config_value(ERROR_KEY)?
        .unwrap_or_default()
        .replace(['\n', '\r'], " ");
    let policy_error = config_value(POLICY_ERROR_KEY)?
        .unwrap_or_default()
        .replace(['\n', '\r'], " ");
    println!("mode={mode}");
    println!("kmi={kmi}");
    println!("supported={}", u8::from(supported));
    println!("loaded={}", u8::from(loaded));
    println!("autoload={}", u8::from(autoload));
    println!("version={version}");
    println!("last_error={last_error}");
    println!(
        "logging={}",
        u8::from(
            config_value(LOG_KEY)?
                .as_deref()
                .is_none_or(module_config::parse_bool_config)
        )
    );
    println!(
        "hide_mounts={}",
        u8::from(
            config_value(MOUNT_KEY)?
                .as_deref()
                .is_some_and(module_config::parse_bool_config)
        )
    );
    if let Some((release, build)) = configured_uname()? {
        println!("uname_release={release}");
        println!("uname_version={build}");
    }
    if mode == "lkm" {
        for feature in status::get_susfs_features().lines() {
            println!("feature={feature}");
        }
    }
    for path in configured_paths()? {
        println!("path={path}");
    }
    println!("policy_error={policy_error}");
    Ok(())
}

pub fn load() -> Result<()> {
    require_lkm()?;
    if guard_loaded() {
        println!("SUSFS guard is already loaded");
        return Ok(());
    }
    ensure!(
        !status::get_susfs_status(),
        "Another SUSFS implementation is active; refusing to load the LKM guard"
    );

    let kmi = current_kmi()?;
    let name = asset_name(&kmi);
    let module_data = assets::get_asset_data(&name)
        .with_context(|| format!("No SUSFS guard KO is bundled for KMI {kmi}"))?;
    let params = CString::new("").expect("empty module parameters are valid");
    info!("Loading SUSFS guard for {kmi}");
    ksuinit::load_module_strict(&module_data, &params).context("Could not load SUSFS guard KO")?;
    if !guard_loaded() {
        let _ = rustix::system::delete_module(c"susfs_guard_lkm", 0);
        bail!("SUSFS guard loaded without reporting its LKM feature marker; rolled back");
    }
    println!("SUSFS guard loaded for {kmi}");
    Ok(())
}

pub fn enable() -> Result<()> {
    require_lkm()?;
    let kmi = current_kmi()?;
    if let Err(error) = load() {
        let _ = set_config(ERROR_KEY, &format!("{error:#}"));
        return Err(error);
    }
    if let Err(error) = apply_policy() {
        let _ = set_config(POLICY_ERROR_KEY, &format!("{error:#}"));
        return Err(error);
    }
    set_config(KMI_KEY, &kmi)?;
    set_config(ERROR_KEY, "")?;
    set_config(POLICY_ERROR_KEY, "")?;
    set_config(ENABLED_KEY, "true")?;
    println!("SUSFS guard will load at boot for {kmi}");
    Ok(())
}

pub fn disable(unload: bool) -> Result<()> {
    set_config(ENABLED_KEY, "false")?;
    if unload && guard_loaded() {
        require_lkm()?;
        rustix::system::delete_module(c"susfs_guard_lkm", 0)
            .context("Could not unload SUSFS guard; autoload remains disabled")?;
    }
    println!(
        "SUSFS guard autoload disabled{}",
        if unload { " and module unloaded" } else { "" }
    );
    Ok(())
}

pub fn load_if_autoload_enabled() -> Result<()> {
    if !config_value(ENABLED_KEY)?
        .as_deref()
        .is_some_and(module_config::parse_bool_config)
    {
        return Ok(());
    }
    let result = (|| {
        require_lkm()?;
        let kmi = current_kmi()?;
        ensure!(
            config_value(KMI_KEY)?.as_deref() == Some(kmi.as_str()),
            "Kernel KMI changed; SUSFS guard autoload was stopped"
        );
        load()?;
        apply_policy()
    })();
    if let Err(error) = &result {
        warn!("SUSFS guard autoload failed: {error:#}");
        // Fail closed: do not retry a potentially incompatible kernel module every boot.
        let _ = set_config(ERROR_KEY, &format!("{error:#}"));
        let _ = set_config(ENABLED_KEY, "false");
    } else {
        let _ = set_config(ERROR_KEY, "");
        let _ = set_config(POLICY_ERROR_KEY, "");
    }
    result
}

pub fn run(args: &[String]) -> Result<()> {
    match args {
        [action] if action == "status" => show_status(),
        [action] if action == "load" => load(),
        [action] if action == "enable" => enable(),
        [action] if action == "disable" => disable(false),
        [action] if action == "unload" => disable(true),
        [action, value] if action == "set-log" => {
            update_bool(LOG_KEY, LOG_FEATURE, value, spoof::enable_log)
        }
        [action, value] if action == "set-hide-mounts" => update_bool(
            MOUNT_KEY,
            MOUNT_FEATURE,
            value,
            spoof::hide_sus_mnts_for_non_su_procs,
        ),
        [action, release, version] if action == "set-uname" => set_uname(release, version),
        [action] if action == "clear-uname" => clear_uname(),
        [action, path] if action == "add-path" => add_path(path),
        [action, path] if action == "remove-path" => remove_path(path),
        _ => bail!(
            "Usage: ksud susfs lkm <status|load|enable|disable|unload|set-log 0|1|set-hide-mounts 0|1|set-uname RELEASE VERSION|clear-uname|add-path PATH|remove-path PATH>"
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::{normalized_path, validate_uname};

    #[test]
    fn lkm_paths_are_absolute_and_canonical() {
        assert_eq!(
            normalized_path("/data/adb/modules/example/").unwrap(),
            "/data/adb/modules/example"
        );
        for invalid in ["relative/path", "/", "/a/../b", "/a/./b", "/a\nb", "/a "] {
            assert!(normalized_path(invalid).is_err(), "accepted {invalid:?}");
        }
    }

    #[test]
    fn lkm_uname_fields_fit_kernel_abi() {
        assert!(validate_uname("6.1.0", "#1 SMP").is_ok());
        assert!(validate_uname("", "#1 SMP").is_err());
        assert!(validate_uname(&"x".repeat(65), "#1 SMP").is_err());
        assert!(validate_uname("6.1.0", "bad\nvalue").is_err());
    }
}
