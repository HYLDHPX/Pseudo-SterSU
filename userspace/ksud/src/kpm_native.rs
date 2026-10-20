// SPDX-License-Identifier: GPL-2.0-or-later
//! SukiSU-compatible GKI management: scan and load /data/adb/kpm/*.kpm.
//! SterSU-only arguments/recovery metadata never become loadable images.
use super::{
    KpmManifest, KpmMetadata, MAX_IMAGE_SIZE, MAX_PENDING_MODULES, backend_name,
    ensure_native_loader_ready, hash_and_validate_image, manifest_value, parse_kpm,
    parse_runtime_names, read_manifest, validate_args, validate_id, write_atomic,
};
use crate::{defs, kpm_backend::KpmBackend, kpm_store, ksucalls, utils};
use anyhow::{Context, Result, ensure};
use log::warn;
use serde_json::{Value, json};
use sha256::digest;
use std::collections::HashSet;
use std::fs;
use std::io::{ErrorKind, Read};
use std::path::{Path, PathBuf};

const ROOT: &str = "/data/adb/kpm";
const STATE: &str = "/data/adb/kpm/.stersu";
const MIGRATION: &str = ".legacy-migration-v1";

struct Image {
    file: kpm_store::StoredImage,
    metadata: KpmMetadata,
    bytes: Vec<u8>,
    record: Value,
}

fn state_path(name: &str) -> PathBuf {
    Path::new(STATE).join(name)
}

fn record_path(id: &str) -> PathBuf {
    state_path(&format!("{id}.json"))
}

fn read_json(path: &Path) -> Result<Option<Value>> {
    let mut file = match kpm_store::regular_file(path) {
        Ok(file) => file,
        Err(error) if error.kind() == ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error.into()),
    };
    let mut bytes = Vec::new();
    file.by_ref().take(64 * 1024 + 1).read_to_end(&mut bytes)?;
    ensure!(bytes.len() <= 64 * 1024, "KPM metadata is too large");
    Ok(Some(serde_json::from_slice(&bytes)?))
}

fn write_json(path: &Path, value: &Value) -> Result<()> {
    write_atomic(path, &serde_json::to_vec_pretty(value)?)
}

fn read_image(file: kpm_store::StoredImage) -> Result<Image> {
    let mut bytes = Vec::new();
    kpm_store::regular_file(&file.path)?
        .take(MAX_IMAGE_SIZE as u64 + 1)
        .read_to_end(&mut bytes)?;
    let metadata = parse_kpm(&bytes)?;
    validate_id(&metadata.name)?;
    let record = match read_json(&record_path(&metadata.name))? {
        Some(record) => {
            validate_record(&record, &metadata.name)?;
            record
        }
        None => json!({}),
    };
    Ok(Image {
        file,
        metadata,
        bytes,
        record,
    })
}

fn validate_record(record: &Value, id: &str) -> Result<()> {
    ensure!(
        record.get("schemaVersion").and_then(Value::as_u64) == Some(super::MANIFEST_VERSION),
        "invalid GKI KPM record schema"
    );
    ensure!(
        record
            .get("metadata")
            .and_then(|value| value.get("name"))
            .and_then(Value::as_str)
            == Some(id),
        "GKI KPM record name does not match image"
    );
    let hash = record
        .get("sha256")
        .and_then(Value::as_str)
        .context("missing GKI KPM SHA-256")?;
    ensure!(
        hash.len() == 64 && hash.bytes().all(|byte| byte.is_ascii_hexdigit()),
        "invalid GKI KPM SHA-256"
    );
    validate_args(
        record
            .get("args")
            .and_then(Value::as_str)
            .context("invalid GKI KPM arguments")?,
    )?;
    ensure!(
        record.get("quarantined").and_then(Value::as_bool).is_some(),
        "invalid GKI KPM quarantine state"
    );
    Ok(())
}

fn images() -> Result<Vec<Image>> {
    let mut images = Vec::new();
    let mut names = HashSet::new();
    for file in kpm_store::scan(Path::new(ROOT))? {
        let image = match read_image(file) {
            Ok(image) => image,
            Err(error) => {
                warn!("ignoring invalid stored GKI KPM: {error:#}");
                continue;
            }
        };
        ensure!(
            names.insert(image.metadata.name.clone()),
            "duplicate KPM module name: {}",
            image.metadata.name
        );
        images.push(image);
    }
    Ok(images)
}

fn find(id: &str) -> Result<Image> {
    validate_id(id)?;
    images()?
        .into_iter()
        .find(|image| image.metadata.name == id)
        .with_context(|| format!("KPM '{id}' is not stored in {ROOT}"))
}

fn live() -> Result<HashSet<String>> {
    let names = parse_runtime_names(&ksucalls::native_kpm_list()?)?;
    let count = usize::try_from(ksucalls::native_kpm_num()?)?;
    ensure!(
        names.len() == count,
        "GKI KPM runtime list is incomplete or changed; retry the operation"
    );
    Ok(names.into_iter().collect())
}

fn record_for(image: &Image) -> Value {
    manifest_value(&KpmManifest {
        metadata: image.metadata.clone(),
        sha256: digest(&image.bytes),
        args: image
            .record
            .get("args")
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_string(),
        enabled: image.file.enabled,
        quarantined: false,
        quarantine_reason: String::new(),
        imported_at: chrono::Local::now().to_rfc3339(),
        source_name: image
            .file
            .path
            .file_name()
            .unwrap_or_default()
            .to_string_lossy()
            .into_owned(),
    })
}

pub(super) fn prepare() -> Result<()> {
    super::ensure_private_dir(Path::new(ROOT))?;
    super::ensure_private_dir(Path::new(STATE))?;
    let marker = state_path(MIGRATION);
    if read_json(&marker)?.is_some() {
        return Ok(());
    }
    // Copy only: old Manager/LKM state is kept as a recoverable backup. Never
    // overwrite an existing upstream-style image or its current configuration.
    for name in [".policy.json", ".boot_pending.json"] {
        let target = state_path(name);
        if read_json(&target)?.is_none()
            && let Some(value) = read_json(&Path::new(defs::KPM_DIR).join(name))?
        {
            write_json(&target, &value)?;
        }
    }
    for entry in fs::read_dir(defs::KPM_DIR)? {
        let entry = entry?;
        let id = entry.file_name().to_string_lossy().into_owned();
        if id.starts_with('.') || !entry.file_type()?.is_dir() {
            continue;
        }
        let migrated = (|| -> Result<_> {
            let manifest = read_manifest(&id)?;
            let bytes = hash_and_validate_image(&id, &manifest)?;
            Ok((manifest, bytes))
        })();
        let (manifest, bytes) = match migrated {
            Ok(migrated) => migrated,
            Err(error) => {
                warn!(
                    "legacy GKI KPM '{id}' could not be migrated; keeping its original files: {error:#}"
                );
                continue;
            }
        };
        if images()?.iter().any(|image| image.metadata.name == id) {
            warn!(
                "keeping existing flat GKI KPM '{id}'; legacy copy remains at {}",
                entry.path().display()
            );
            continue;
        }
        let suffix = if manifest.enabled && !manifest.quarantined {
            ".kpm"
        } else {
            ".kpm.disabled"
        };
        let destination = Path::new(ROOT).join(format!("{id}{suffix}"));
        // Record first; interrupted migration can only leave metadata, not an
        // untracked enabled image. Retrying publication never replaces a file.
        write_json(&record_path(&id), &manifest_value(&manifest))?;
        kpm_store::publish_new(&destination, &bytes)?;
    }
    write_json(&marker, &json!({"schemaVersion": 1}))
}

pub(super) fn read_policy() -> Result<bool> {
    let own = read_json(&state_path(".policy.json"))?;
    let value = if let Some(value) = own {
        Some(value)
    } else if read_json(&state_path(MIGRATION))?.is_none() {
        read_json(&Path::new(defs::KPM_DIR).join(".policy.json"))?
    } else {
        None
    };
    let Some(value) = value else {
        return Ok(true);
    };
    ensure!(
        value.get("schemaVersion").and_then(Value::as_u64) == Some(1),
        "invalid GKI KPM policy schema"
    );
    value
        .get("enabled")
        .and_then(Value::as_bool)
        .context("invalid GKI KPM policy state")
}

fn pending(ids: &[String]) -> Result<()> {
    ensure!(
        ids.len() <= MAX_PENDING_MODULES,
        "too many pending GKI KPM modules"
    );
    write_json(
        &state_path(".boot_pending.json"),
        &json!({
            "schemaVersion": 1,
            "bootId": super::current_boot_id()?,
            "ids": ids,
        }),
    )
}

fn quarantine(image: &mut Image, reason: &str) -> Result<()> {
    let mut record = if image.record.get("sha256").is_some() {
        image.record.clone()
    } else {
        record_for(image)
    };
    record["enabled"] = json!(false);
    record["quarantined"] = json!(true);
    record["quarantineReason"] = json!(reason);
    write_json(&record_path(&image.metadata.name), &record)?;
    image.file.path = kpm_store::set_enabled(&image.file, false)?;
    image.file.enabled = false;
    image.record = record;
    Ok(())
}

pub(super) fn recover() -> Result<()> {
    prepare()?;
    let path = state_path(".boot_pending.json");
    let Some(value) = read_json(&path)? else {
        return Ok(());
    };
    ensure!(
        value.get("schemaVersion").and_then(Value::as_u64) == Some(1),
        "invalid GKI KPM recovery schema"
    );
    let boot_id = value
        .get("bootId")
        .and_then(Value::as_str)
        .context("missing GKI KPM recovery boot id")?;
    if boot_id == super::current_boot_id()? {
        return Ok(());
    }
    let ids = value
        .get("ids")
        .and_then(Value::as_array)
        .context("missing GKI KPM recovery names")?;
    ensure!(
        ids.len() <= MAX_PENDING_MODULES,
        "invalid GKI KPM recovery count"
    );
    for id in ids {
        let id = id.as_str().context("invalid GKI KPM recovery name")?;
        validate_id(id)?;
        let mut image = match find(id) {
            Ok(image) => image,
            Err(error) => {
                warn!("cannot recover GKI KPM '{id}': {error:#}");
                continue;
            }
        };
        quarantine(
            &mut image,
            "previous boot did not complete after GKI KPM load",
        )?;
    }
    clear_pending()
}

pub(super) fn clear_pending() -> Result<()> {
    super::remove_marker(&state_path(".boot_pending.json"), "GKI KPM recovery marker")
}

fn load_image(image: &Image, boot: bool) -> Result<()> {
    ensure!(read_policy()?, "KPM loading is disabled by user policy");
    ensure_native_loader_ready()?;
    ensure!(image.file.enabled, "KPM is disabled");
    ensure!(
        !image
            .record
            .get("quarantined")
            .and_then(Value::as_bool)
            .unwrap_or(false),
        "KPM is quarantined; enable it to retry"
    );
    if let Some(hash) = image.record.get("sha256").and_then(Value::as_str) {
        ensure!(
            hash == digest(&image.bytes),
            "KPM image changed since import; import the trusted original again"
        );
    }
    let current = live()?;
    if current.contains(&image.metadata.name) {
        return Ok(());
    }
    ensure!(
        current.len() < MAX_PENDING_MODULES,
        "GKI KPM runtime module limit reached"
    );
    let record = if image.record.get("sha256").is_some() {
        image.record.clone()
    } else {
        record_for(image)
    };
    let args = record.get("args").and_then(Value::as_str).unwrap_or("");
    validate_args(args)?;
    ensure!(
        args.len() <= 254,
        "GKI KPM load arguments exceed the upstream limit of 254 bytes"
    );
    write_json(&record_path(&image.metadata.name), &record)?;
    // A manual load during early boot must not erase recovery information for
    // modules already loaded by the boot batch.
    let previous_pending = read_json(&state_path(".boot_pending.json"))?;
    if !boot {
        let mut ids = Vec::new();
        if let Some(value) = &previous_pending {
            ensure!(
                value.get("schemaVersion").and_then(Value::as_u64) == Some(1),
                "invalid GKI KPM recovery schema"
            );
            ensure!(
                value.get("bootId").and_then(Value::as_str)
                    == Some(super::current_boot_id()?.as_str()),
                "unrecovered GKI KPM boot marker; restart ksud recovery first"
            );
            let previous_ids = value
                .get("ids")
                .and_then(Value::as_array)
                .context("invalid GKI KPM recovery names")?;
            for id in previous_ids {
                let id = id.as_str().context("invalid GKI KPM recovery name")?;
                validate_id(id)?;
                ids.push(id.to_string());
            }
        }
        if !ids.contains(&image.metadata.name) {
            ids.push(image.metadata.name.clone());
        }
        pending(&ids)?;
    }
    let result =
        ksucalls::native_kpm_load(image.file.path.to_str().context("invalid KPM path")?, args)
            .map_err(|error| {
                let hint = crate::kpm_abi::native_load_failure_hint(
                    error.raw_os_error(),
                    image.file.path.is_file(),
                );
                anyhow::Error::new(error).context(format!(
                    "failed to load GKI KPM '{}': {hint}",
                    image.metadata.name
                ))
            });
    if !boot {
        let cleanup = previous_pending.map_or_else(clear_pending, |value| {
            write_json(&state_path(".boot_pending.json"), &value)
        });
        if let Err(error) = cleanup {
            if result.is_ok() {
                ksucalls::native_kpm_unload(&image.metadata.name)
                    .context("loaded KPM could not be rolled back after marker cleanup failure")?;
            }
            return Err(error.context("GKI KPM recovery marker cleanup failed"));
        }
    }
    result
}

pub(super) fn load_at_boot() -> Result<()> {
    recover()?;
    if !read_policy()? || utils::is_safe_mode() {
        return Ok(());
    }
    ensure_native_loader_ready()?;
    let current = live()?;
    let mut candidates = images()?
        .into_iter()
        .filter(|image| image.file.enabled)
        .collect::<Vec<_>>();
    candidates.retain(|image| !current.contains(&image.metadata.name));
    ensure!(
        current.len() <= MAX_PENDING_MODULES,
        "invalid live GKI KPM count"
    );
    let available = MAX_PENDING_MODULES - current.len();
    if candidates.len() > available {
        warn!("GKI KPM boot batch exceeds runtime capacity; leaving excess images unloaded");
        candidates.truncate(available);
    }
    if candidates.is_empty() {
        return Ok(());
    }
    pending(
        &candidates
            .iter()
            .map(|image| image.metadata.name.clone())
            .collect::<Vec<_>>(),
    )?;
    for mut image in candidates {
        if let Err(error) = load_image(&image, true) {
            warn!("GKI KPM boot load failed: {error:#}");
            quarantine(&mut image, &format!("boot load failed: {error:#}"))?;
        }
    }
    Ok(())
}

pub(super) fn print_list() -> Result<()> {
    prepare()?;
    let live = live().ok();
    let mut values = Vec::new();
    let mut seen = HashSet::new();
    for file in kpm_store::scan(Path::new(ROOT))? {
        match read_image(file.clone()) {
            Ok(image) => {
                let id = &image.metadata.name;
                ensure!(seen.insert(id.clone()), "duplicate KPM module name: {id}");
                values.push(json!({
                    "id": id, "name": id, "version": image.metadata.version,
                    "license": image.metadata.license, "author": image.metadata.author,
                    "description": image.metadata.description, "enabled": image.file.enabled,
                    "sha256": image.record.get("sha256").cloned().unwrap_or_else(|| json!(digest(&image.bytes))),
                    "args": image.record.get("args").and_then(Value::as_str).unwrap_or(""),
                    "quarantined": image.record.get("quarantined").and_then(Value::as_bool).unwrap_or(false),
                    "quarantineReason": image.record.get("quarantineReason").and_then(Value::as_str).unwrap_or(""),
                    "sourceName": image.record.get("sourceName").cloned().unwrap_or_else(|| json!(file.path.file_name().unwrap_or_default().to_string_lossy())),
                    "importedAt": image.record.get("importedAt").and_then(Value::as_str).unwrap_or(""),
                    "stored": true,
                    "loaded": live.as_ref().map(|names| names.contains(id)), "runtimeKnown": live.is_some(),
                }));
            }
            Err(error) => values.push(json!({"id": file.path.file_name().unwrap_or_default().to_string_lossy(), "error": format!("{error:#}")})),
        }
    }
    if let Some(live) = live {
        let mut runtime_only = live
            .into_iter()
            .filter(|id| !seen.contains(id))
            .collect::<Vec<_>>();
        runtime_only.sort();
        for id in runtime_only {
            values.push(
                json!({"id": id, "name": id, "stored": false, "enabled": false,
                "loaded": true, "runtimeKnown": true,
                "description": ksucalls::native_kpm_info(&id).unwrap_or_default()}),
            );
        }
    }
    println!("{}", Value::Array(values));
    Ok(())
}

pub(super) fn print_info(id: &str) -> Result<()> {
    prepare()?;
    validate_id(id)?;
    let loaded = live().ok().map(|names| names.contains(id));
    let image = match find(id) {
        Ok(image) => image,
        Err(error) => {
            ensure!(loaded == Some(true), "{error:#}");
            println!(
                "{}",
                json!({"backend": backend_name(KpmBackend::NativeGki), "name": id,
                "state": 2, "stored": false, "runtimeKnown": true,
                "runtimeInfo": ksucalls::native_kpm_info(id)?})
            );
            return Ok(());
        }
    };
    println!(
        "{}",
        json!({
            "backend": backend_name(KpmBackend::NativeGki), "name": id,
            "version": image.metadata.version, "license": image.metadata.license,
            "author": image.metadata.author, "description": image.metadata.description,
            "state": loaded.map_or(-1, |loaded| if loaded { 2 } else { 0 }),
            "runtimeKnown": loaded.is_some(), "imageSize": image.bytes.len(),
            "textSize": 0, "roSize": 0, "loadedAtNs": 0,
            "runtimeInfo": if loaded == Some(true) { ksucalls::native_kpm_info(id).ok() } else { None },
        })
    );
    Ok(())
}

pub(super) fn import(
    path: &Path,
    args: &str,
    trusted: bool,
    force: bool,
    enable: bool,
) -> Result<()> {
    prepare()?;
    ensure!(trusted, "KPM import requires --trusted acknowledgement");
    ensure!(read_policy()?, "KPM loading is disabled by user policy");
    validate_args(args)?;
    ensure!(
        args.len() <= 254,
        "GKI KPM load arguments exceed the upstream limit of 254 bytes"
    );
    let mut bytes = Vec::new();
    kpm_store::regular_file(path)?
        .take(MAX_IMAGE_SIZE as u64 + 1)
        .read_to_end(&mut bytes)?;
    let metadata = parse_kpm(&bytes)?;
    validate_id(&metadata.name)?;
    let id = metadata.name.clone();
    let existing = images()?
        .into_iter()
        .find(|image| image.metadata.name == id);
    ensure!(
        existing.is_none() || force,
        "KPM '{id}' already exists; pass --force to replace it"
    );
    if existing.is_some() && live()?.contains(&id) {
        ksucalls::native_kpm_unload(&id)?;
    }
    let target = Path::new(ROOT).join(format!("{id}.kpm.disabled"));
    let old_record = read_json(&record_path(&id))?;
    if let Some(existing) = &existing {
        // Retain the old image until the replacement and metadata both exist.
        let backup = state_path(&format!("{id}-{}.kpm.bak", super::monotonic_nonce()));
        kpm_store::publish_new(&backup, &existing.bytes)?;
        fs::remove_file(&existing.file.path)?;
    }
    let mut published = false;
    let result = (|| -> Result<()> {
        kpm_store::publish_new(&target, &bytes)?;
        published = true;
        let manifest = KpmManifest {
            metadata,
            sha256: digest(&bytes),
            args: args.to_string(),
            enabled: false,
            quarantined: false,
            quarantine_reason: String::new(),
            imported_at: chrono::Local::now().to_rfc3339(),
            source_name: path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .into_owned(),
        };
        write_json(&record_path(&id), &manifest_value(&manifest))
    })();
    if let Err(error) = result {
        if published {
            fs::remove_file(&target)?;
        }
        if let Some(existing) = existing {
            kpm_store::publish_new(&existing.file.path, &existing.bytes)?;
        }
        if let Some(record) = old_record {
            write_json(&record_path(&id), &record)?;
        }
        return Err(error);
    }
    if enable {
        set_enabled(&id, true)?;
    }
    println!("Imported GKI KPM '{id}' into {ROOT}");
    Ok(())
}

pub(super) fn set_enabled(id: &str, enabled: bool) -> Result<()> {
    prepare()?;
    let mut image = find(id)?;
    if enabled {
        ensure!(read_policy()?, "KPM loading is disabled by user policy");
        if let Some(hash) = image.record.get("sha256").and_then(Value::as_str) {
            ensure!(
                hash == digest(&image.bytes),
                "KPM image changed since import; import the trusted original again"
            );
        }
    }
    let runtime = live();
    if !enabled && runtime.as_ref().is_ok_and(|names| names.contains(id)) {
        ksucalls::native_kpm_unload(id)?;
    }
    let previous_file = image.file.clone();
    image.file.path = kpm_store::set_enabled(&image.file, enabled)?;
    image.file.enabled = enabled;
    let mut record = if image.record.get("sha256").is_some() {
        image.record.clone()
    } else {
        record_for(&image)
    };
    if enabled {
        record["quarantined"] = json!(false);
        record["quarantineReason"] = json!("");
    }
    record["enabled"] = json!(enabled);
    if let Err(error) = write_json(&record_path(id), &record) {
        kpm_store::set_enabled(&image.file, previous_file.enabled)
            .context("failed to roll back GKI KPM file state after metadata write failure")?;
        return Err(error);
    }
    image.record = record;
    if enabled
        && read_policy()?
        && super::native_loader_ready()
        && let Err(error) = load_image(&image, false)
    {
        quarantine(&mut image, &format!("enable failed: {error:#}"))?;
        return Err(error);
    }
    if !enabled && runtime.is_err() {
        println!(
            "Disabled GKI KPM '{id}' persistently; runtime state is unavailable, so reboot to guarantee it is unloaded"
        );
    } else {
        println!(
            "{} GKI KPM '{id}'",
            if enabled { "Enabled" } else { "Disabled" }
        );
    }
    Ok(())
}

pub(super) fn remove(id: &str) -> Result<()> {
    prepare()?;
    let image = find(id)?;
    if live()?.contains(id) {
        ksucalls::native_kpm_unload(id)?;
    }
    fs::remove_file(&image.file.path)?;
    super::remove_marker(&record_path(id), "GKI KPM metadata")?;
    println!("Removed GKI KPM '{id}'");
    Ok(())
}

pub(super) fn load(id: &str) -> Result<()> {
    prepare()?;
    let image = find(id)?;
    load_image(&image, false)?;
    println!(
        "{}",
        json!({"loaded": true, "name": id, "version": image.metadata.version})
    );
    Ok(())
}

pub(super) fn set_policy(enabled: bool) -> Result<()> {
    prepare()?;
    write_json(
        &state_path(".policy.json"),
        &json!({"schemaVersion": 1, "enabled": enabled}),
    )?;
    let mut failures = Vec::new();
    if enabled {
        if super::native_loader_ready() {
            for mut image in images()?.into_iter().filter(|image| image.file.enabled) {
                if let Err(error) = load_image(&image, false) {
                    let reason = format!("policy enabled but load failed: {error:#}");
                    if let Err(error) = quarantine(&mut image, &reason) {
                        failures.push(format!(
                            "{}: quarantine failed: {error:#}",
                            image.metadata.name
                        ));
                    }
                    failures.push(format!("{}: {reason}", image.metadata.name));
                }
            }
        }
    } else if super::native_loader_ready() {
        for id in live()? {
            if let Err(error) = ksucalls::native_kpm_unload(&id) {
                failures.push(format!("{id}: {error:#}"));
            }
        }
    }
    ensure!(
        failures.is_empty(),
        "GKI KPM policy saved as {enabled}, but runtime reconciliation failed: {}",
        failures.join("; ")
    );
    println!(
        "GKI KPM policy {}",
        if enabled { "enabled" } else { "disabled" }
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid_record() -> Value {
        json!({
            "schemaVersion": 1, "metadata": {"name": "fixture"},
            "sha256": "a".repeat(64), "args": "", "quarantined": false,
        })
    }

    #[test]
    fn accepts_complete_record() {
        assert!(validate_record(&valid_record(), "fixture").is_ok());
    }

    #[test]
    fn rejects_corrupt_records_instead_of_skipping_hash_validation() {
        for (field, value) in [
            ("schemaVersion", json!(2)),
            ("metadata", json!({"name": "other"})),
            ("sha256", Value::Null),
            ("sha256", json!("x".repeat(64))),
            ("args", json!("bad\0argument")),
            ("quarantined", json!("false")),
        ] {
            let mut record = valid_record();
            record[field] = value;
            assert!(validate_record(&record, "fixture").is_err(), "{field}");
        }
        assert!(validate_record(&json!({}), "fixture").is_err());
    }
}
