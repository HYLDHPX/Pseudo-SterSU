//! One-way migration for previously enabled web-manager instances.
//! The general-purpose server and its assets are no longer compiled into ksud.

use anyhow::{Context, Result, ensure};
use serde_json::{Value, json};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::{Ipv4Addr, SocketAddrV4, TcpStream};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;
use std::time::{Duration, Instant};

use crate::defs;

pub fn disable() -> Result<()> {
    let config_path = Path::new(defs::WEB_MANAGER_CONFIG_PATH);
    let mut config_port = None;
    if config_path.is_file() {
        let mut config: Value =
            serde_json::from_slice(&fs::read(config_path)?).unwrap_or_else(|_| json!({}));
        if !config.is_object() {
            config = json!({});
        }
        config_port = config.get("port").and_then(Value::as_u64);
        let object = config
            .as_object_mut()
            .context("invalid legacy web-manager config")?;
        object.insert("enabled".to_string(), Value::Bool(false));
        let directory = config_path
            .parent()
            .context("legacy web-manager directory missing")?;
        let mut temp = tempfile::NamedTempFile::new_in(directory)?;
        temp.write_all(&serde_json::to_vec_pretty(&config)?)?;
        temp.as_file()
            .set_permissions(fs::Permissions::from_mode(0o600))?;
        temp.persist(config_path).map_err(|error| error.error)?;
    }

    let state_path = Path::new(defs::WEB_MANAGER_STATE_PATH);
    if !state_path.is_file() {
        let _ = fs::remove_file(defs::WEB_MANAGER_STOP_PATH);
        return Ok(());
    }
    let state_port = fs::read(state_path)
        .ok()
        .and_then(|bytes| serde_json::from_slice::<Value>(&bytes).ok())
        .and_then(|state| state.get("port").and_then(Value::as_u64));
    let port = state_port
        .or(config_port)
        .filter(|port| (1024..=65535).contains(port));
    let mut stop = OpenOptions::new()
        .create(true)
        .truncate(true)
        .write(true)
        .mode(0o600)
        .open(defs::WEB_MANAGER_STOP_PATH)?;
    stop.write_all(b"stop\n")?;
    if let Some(port) = port {
        let address = SocketAddrV4::new(Ipv4Addr::LOCALHOST, port as u16);
        let _ = TcpStream::connect_timeout(&address.into(), Duration::from_millis(250));
    }
    let deadline = Instant::now() + Duration::from_secs(3);
    while state_path.is_file() && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(50));
    }
    ensure!(!state_path.is_file(), "legacy web manager did not stop");
    let _ = fs::remove_file(defs::WEB_MANAGER_STOP_PATH);
    Ok(())
}
