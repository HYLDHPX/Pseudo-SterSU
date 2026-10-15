//! A loopback-only recovery page for disabling stealth mode.
//! It deliberately does not share routes or authentication with the old web manager.

use anyhow::{Context, Result, bail, ensure};
use log::{error, warn};
use serde_json::{Value, json};
use std::collections::{HashMap, VecDeque};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::net::{Ipv4Addr, SocketAddrV4, TcpListener, TcpStream};
use std::os::fd::AsRawFd;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;
use std::process::Command;
use std::time::{Duration, Instant};

use crate::utils;

pub const PORT: u16 = 10_241;
const ADDRESS: &str = "127.0.0.1:10241";
const ORIGIN: &str = "http://127.0.0.1:10241";
const MODE_PATH: &str = "/data/adb/ksu/hansu_mode";
const CODE_PATH: &str = "/data/adb/ksu/hansu_code";
const CODE_V2_MARKER: &str = "SterSU-stealth-code-v2:09e7997f";
const LOCK_PATH: &str = "/data/adb/ksu/stealth_recovery.lock";
const STOP_PATH: &str = "/data/adb/ksu/stealth_recovery.stop";
const BOOT_SCRIPT: &str = "/data/adb/service.d/99-stersu-stealth-recovery.sh";
const BOOT_BINARY: &str = "/data/adb/ksu/bin/stersu-stealth-recovery";
const BOOT_SCRIPT_CONTENT: &[u8] = b"#!/system/bin/sh\n[ -f /data/adb/ksu/hansu_mode ] || exit 0\nexec /data/adb/ksu/bin/stersu-stealth-recovery stealth-recovery start >/dev/null 2>&1\n";
const MAX_HEADERS: usize = 4096;
const MAX_BODY: usize = 4096;
const START_TIMEOUT: Duration = Duration::from_secs(3);
const IO_TIMEOUT: Duration = Duration::from_secs(5);
const RATE_WINDOW: Duration = Duration::from_mins(1);
const MAX_FAILURES: usize = 5;

struct ServerLock(File);

impl Drop for ServerLock {
    fn drop(&mut self) {
        unsafe { libc::flock(self.0.as_raw_fd(), libc::LOCK_UN) };
    }
}

struct Request {
    method: String,
    path: String,
    headers: HashMap<String, String>,
    body: Vec<u8>,
}

#[derive(Default)]
struct FailedAttempts(VecDeque<Instant>);

impl FailedAttempts {
    fn limited(&mut self) -> bool {
        let now = Instant::now();
        while self
            .0
            .front()
            .is_some_and(|time| now.duration_since(*time) >= RATE_WINDOW)
        {
            self.0.pop_front();
        }
        self.0.len() >= MAX_FAILURES
    }

    fn record(&mut self) {
        self.0.push_back(Instant::now());
    }
}

const fn address() -> SocketAddrV4 {
    SocketAddrV4::new(Ipv4Addr::LOCALHOST, PORT)
}

fn acquire_lock() -> Result<ServerLock> {
    let file = OpenOptions::new()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .mode(0o600)
        .open(LOCK_PATH)?;
    let result = unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
    ensure!(result == 0, "stealth recovery is already running");
    Ok(ServerLock(file))
}

fn lock_is_held() -> bool {
    let Ok(file) = OpenOptions::new().read(true).write(true).open(LOCK_PATH) else {
        return false;
    };
    let result = unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
    if result == 0 {
        unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_UN) };
        false
    } else {
        true
    }
}

fn health_check() -> bool {
    let Ok(mut stream) = TcpStream::connect_timeout(&address().into(), Duration::from_millis(250))
    else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(500)));
    if stream
        .write_all(b"GET /health HTTP/1.1\r\nHost: 127.0.0.1:10241\r\nConnection: close\r\n\r\n")
        .is_err()
    {
        return false;
    }
    let mut response = Vec::with_capacity(512);
    while response.len() < 2048 {
        let mut chunk = [0_u8; 512];
        let Ok(count) = stream.read(&mut chunk) else {
            return false;
        };
        if count == 0 {
            return false;
        }
        response.extend_from_slice(&chunk[..count]);
        if let Some(end) = response.windows(4).position(|part| part == b"\r\n\r\n") {
            let header = String::from_utf8_lossy(&response[..end]);
            return header.starts_with("HTTP/1.1 200 ")
                && header.lines().any(|line| line == "X-SterSU-Recovery: 1");
        }
    }
    false
}

fn running() -> bool {
    lock_is_held() && health_check()
}

fn install_boot_fallback() -> Result<()> {
    let current = std::env::current_exe().context("find ksud executable")?;
    install_boot_fallback_at(&current, Path::new(BOOT_BINARY), Path::new(BOOT_SCRIPT))
}

fn install_boot_fallback_at(current: &Path, binary: &Path, script: &Path) -> Result<()> {
    let binary_dir = binary.parent().context("recovery binary has no parent")?;
    let script_dir = script.parent().context("recovery script has no parent")?;
    fs::create_dir_all(binary_dir)?;
    fs::create_dir_all(script_dir)?;
    if current != binary {
        let temp = tempfile::NamedTempFile::new_in(binary_dir)?;
        fs::copy(current, temp.path()).context("copy recovery-capable ksud")?;
        temp.as_file()
            .set_permissions(fs::Permissions::from_mode(0o700))?;
        temp.persist(binary).map_err(|error| error.error)?;
    }
    let mut temp = tempfile::NamedTempFile::new_in(script_dir)?;
    temp.write_all(BOOT_SCRIPT_CONTENT)?;
    temp.as_file()
        .set_permissions(fs::Permissions::from_mode(0o700))?;
    temp.persist(script).map_err(|error| error.error)?;
    Ok(())
}

fn remove_boot_fallback() {
    if let Err(error) = fs::remove_file(BOOT_SCRIPT)
        && error.kind() != std::io::ErrorKind::NotFound
    {
        warn!("failed to remove stealth recovery boot script: {error}");
    }
    if let Err(error) = fs::remove_file(BOOT_BINARY)
        && error.kind() != std::io::ErrorKind::NotFound
    {
        warn!("failed to remove stealth recovery boot binary: {error}");
    }
}

pub fn start() -> Result<()> {
    ensure!(
        Path::new(MODE_PATH).is_file(),
        "stealth mode is not enabled"
    );
    if let Err(error) = crate::web_manager::disable() {
        warn!("failed to retire legacy web manager: {error:#}");
    }
    install_boot_fallback()?;
    start_background()
}

pub fn start_if_enabled() {
    if Path::new(MODE_PATH).is_file() {
        if let Err(error) = install_boot_fallback() {
            warn!("failed to refresh stealth recovery boot fallback: {error:#}");
        }
        if let Err(error) = start_background() {
            warn!("failed to start stealth recovery: {error:#}");
        }
    }
}

fn start_background() -> Result<()> {
    if running() {
        return Ok(());
    }
    let _ = fs::remove_file(STOP_PATH);
    if utils::create_daemon(true)? {
        if let Err(error) = serve() {
            error!("stealth recovery server exited: {error:#}");
        }
        unsafe { libc::_exit(0) }
    }
    let deadline = Instant::now() + START_TIMEOUT;
    while Instant::now() < deadline {
        if running() {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    bail!("stealth recovery could not bind to {ADDRESS}")
}

pub fn stop() -> Result<()> {
    remove_boot_fallback();
    if !lock_is_held() {
        let _ = fs::remove_file(STOP_PATH);
        return Ok(());
    }
    fs::write(STOP_PATH, b"stop\n")?;
    let _ = TcpStream::connect_timeout(&address().into(), Duration::from_millis(250));
    let deadline = Instant::now() + START_TIMEOUT;
    while Instant::now() < deadline {
        if !lock_is_held() {
            let _ = fs::remove_file(STOP_PATH);
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    bail!("stealth recovery did not stop")
}

pub fn status() {
    println!(
        "{}",
        json!({"enabled": Path::new(MODE_PATH).is_file(), "running": running(), "url": ORIGIN})
    );
}

pub fn serve() -> Result<()> {
    ensure!(
        Path::new(MODE_PATH).is_file(),
        "stealth mode is not enabled"
    );
    let _lock = acquire_lock()?;
    utils::switch_mnt_ns(1).context("switch to global mount namespace")?;
    let listener = TcpListener::bind(address()).with_context(|| format!("bind {ADDRESS}"))?;
    let mut failures = FailedAttempts::default();
    while Path::new(MODE_PATH).is_file() && !Path::new(STOP_PATH).exists() {
        let (mut stream, _) = match listener.accept() {
            Ok(client) => client,
            Err(error) if error.kind() == std::io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error.into()),
        };
        if Path::new(STOP_PATH).exists() || !Path::new(MODE_PATH).is_file() {
            break;
        }
        let _ = stream.set_read_timeout(Some(IO_TIMEOUT));
        let _ = stream.set_write_timeout(Some(IO_TIMEOUT));
        match handle_client(&mut stream, &mut failures) {
            Ok(true) => break,
            Ok(false) => {}
            Err(error) => warn!("stealth recovery client failed: {error:#}"),
        }
    }
    let _ = fs::remove_file(STOP_PATH);
    Ok(())
}

fn read_request(stream: &mut TcpStream) -> Result<Request> {
    let mut data = Vec::with_capacity(1024);
    let header_end = loop {
        if let Some(end) = data.windows(4).position(|part| part == b"\r\n\r\n") {
            break end + 4;
        }
        ensure!(data.len() <= MAX_HEADERS, "headers too large");
        let mut chunk = [0_u8; 1024];
        let count = stream.read(&mut chunk)?;
        ensure!(count > 0, "incomplete request");
        data.extend_from_slice(&chunk[..count]);
    };
    ensure!(header_end <= MAX_HEADERS, "headers too large");
    let header = std::str::from_utf8(&data[..header_end])?;
    let mut lines = header.split("\r\n");
    let mut first = lines.next().unwrap_or_default().split_ascii_whitespace();
    let method = first.next().unwrap_or_default();
    let path = first.next().unwrap_or_default();
    ensure!(
        first.next() == Some("HTTP/1.1") && first.next().is_none(),
        "invalid request line"
    );
    let mut headers = HashMap::new();
    for line in lines.filter(|line| !line.is_empty()) {
        let (name, value) = line.split_once(':').context("invalid header")?;
        let name = name.trim().to_ascii_lowercase();
        ensure!(
            !name.is_empty() && !headers.contains_key(&name),
            "duplicate or invalid header"
        );
        headers.insert(name, value.trim().to_string());
    }
    ensure!(
        headers.get("host").is_some_and(|value| value == ADDRESS),
        "invalid host"
    );
    ensure!(
        !headers.contains_key("transfer-encoding"),
        "chunked requests are unsupported"
    );
    let length = headers
        .get("content-length")
        .map_or(Ok(0), |value| value.parse::<usize>())?;
    ensure!(length <= MAX_BODY, "request body too large");
    let mut body = data[header_end..].to_vec();
    ensure!(body.len() <= length, "unexpected request body");
    while body.len() < length {
        let mut chunk = [0_u8; 1024];
        let count = stream.read(&mut chunk)?;
        ensure!(count > 0, "incomplete body");
        body.extend_from_slice(&chunk[..count]);
        ensure!(body.len() <= length, "unexpected request body");
    }
    Ok(Request {
        method: method.to_string(),
        path: path.to_string(),
        headers,
        body,
    })
}

fn write_response(stream: &mut TcpStream, status: u16, kind: &str, body: &[u8]) -> Result<()> {
    let reason = match status {
        200 => "OK",
        400 => "Bad Request",
        403 => "Forbidden",
        404 => "Not Found",
        405 => "Method Not Allowed",
        429 => "Too Many Requests",
        _ => "Internal Server Error",
    };
    write!(
        stream,
        "HTTP/1.1 {status} {reason}\r\nContent-Type: {kind}\r\nContent-Length: {}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nX-SterSU-Recovery: 1\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'\r\n\r\n",
        body.len()
    )?;
    stream.write_all(body)?;
    Ok(())
}

fn json_response(stream: &mut TcpStream, status: u16, message: &str) -> Result<()> {
    let body = json!({"ok": status == 200, "message": message}).to_string();
    write_response(
        stream,
        status,
        "application/json; charset=utf-8",
        body.as_bytes(),
    )
}

fn handle_client(stream: &mut TcpStream, failures: &mut FailedAttempts) -> Result<bool> {
    let Ok(request) = read_request(stream) else {
        json_response(stream, 400, "请求格式无效")?;
        return Ok(false);
    };
    match (request.method.as_str(), request.path.as_str()) {
        ("GET", "/health") => json_response(stream, 200, "ready")?,
        ("GET", "/") => write_response(
            stream,
            200,
            "text/html; charset=utf-8",
            include_bytes!("stealth_recovery.html"),
        )?,
        ("GET", "/app.js") => write_response(
            stream,
            200,
            "text/javascript; charset=utf-8",
            include_bytes!("stealth_recovery.js"),
        )?,
        ("GET", "/style.css") => write_response(
            stream,
            200,
            "text/css; charset=utf-8",
            include_bytes!("stealth_recovery.css"),
        )?,
        ("POST", "/disable") => {
            let origin_ok = request
                .headers
                .get("origin")
                .is_some_and(|value| value == ORIGIN);
            let header_ok = request
                .headers
                .get("x-stersu-recovery")
                .is_some_and(|value| value == "1");
            let type_ok = request
                .headers
                .get("content-type")
                .is_some_and(|value| value.starts_with("application/json"));
            if !origin_ok || !header_ok || !type_ok {
                json_response(stream, 403, "请求来源无效")?;
                return Ok(false);
            }
            if failures.limited() {
                json_response(stream, 429, "尝试次数过多，请稍后重试")?;
                return Ok(false);
            }
            let Ok(payload) = serde_json::from_slice::<Value>(&request.body) else {
                json_response(stream, 400, "请输入有效的密令")?;
                return Ok(false);
            };
            let supplied = payload
                .get("code")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let configured = match stored_code() {
                Ok(code) => code,
                Err(error) => {
                    error!("stealth recovery code is unavailable: {error:#}");
                    json_response(stream, 500, "密令备份不可用，请使用拨号密令恢复")?;
                    return Ok(false);
                }
            };
            if !code_matches(&configured, supplied) {
                failures.record();
                json_response(stream, 403, "密令不正确")?;
                return Ok(false);
            }
            match fs::remove_file(MODE_PATH) {
                Ok(()) => {
                    remove_boot_fallback();
                    let response = json_response(stream, 200, "隐身模式已关闭");
                    broadcast_disabled();
                    response?;
                    return Ok(true);
                }
                Err(error) => {
                    error!("failed to disable stealth mode: {error}");
                    json_response(stream, 500, "关闭失败，请使用拨号密令重试")?;
                }
            }
        }
        ("GET" | "POST", _) => json_response(stream, 404, "页面不存在")?,
        _ => json_response(stream, 405, "请求方法不支持")?,
    }
    Ok(false)
}

fn normalized_code(value: &str) -> Option<&str> {
    if value.is_empty() {
        return None;
    }
    Some(
        value
            .strip_prefix("*#*#")
            .and_then(|part| part.strip_suffix("#*#*"))
            .filter(|part| !part.is_empty())
            .unwrap_or(value),
    )
}

fn stored_code() -> Result<String> {
    let value = fs::read_to_string(CODE_PATH).context("read stealth code backup")?;
    decode_stored_code(&value)
}

fn decode_stored_code(value: &str) -> Result<String> {
    let mut lines = value.lines();
    let first = lines.next().unwrap_or_default();
    let code = if first == CODE_V2_MARKER {
        let encoded = lines.next().context("missing encoded stealth code")?;
        ensure!(lines.next().is_none(), "invalid stealth code backup");
        let bytes = encoded.as_bytes();
        ensure!(bytes.len() % 2 == 0, "invalid encoded stealth code");
        let mut decoded = Vec::with_capacity(bytes.len() / 2);
        for pair in bytes.chunks_exact(2) {
            let digit = |byte: u8| match byte {
                b'0'..=b'9' => Some(byte - b'0'),
                b'a'..=b'f' => Some(byte - b'a' + 10),
                _ => None,
            };
            let high = digit(pair[0]).context("invalid encoded stealth code")?;
            let low = digit(pair[1]).context("invalid encoded stealth code")?;
            decoded.push((high << 4) | low);
        }
        String::from_utf8(decoded).context("invalid UTF-8 stealth code")?
    } else {
        first.trim().to_owned()
    };
    ensure!(!code.is_empty(), "invalid stealth code backup");
    Ok(code)
}

fn code_matches(configured: &str, supplied: &str) -> bool {
    let (Some(configured), Some(supplied)) =
        (normalized_code(configured), normalized_code(supplied))
    else {
        return false;
    };
    let left = configured.as_bytes();
    let right = supplied.as_bytes();
    let mut difference = left.len() ^ right.len();
    for index in 0..left.len().max(right.len()) {
        difference |= usize::from(
            left.get(index).copied().unwrap_or_default()
                ^ right.get(index).copied().unwrap_or_default(),
        );
    }
    difference == 0
}

fn broadcast_disabled() {
    for package in [
        "io.github.fixz.stersu",
        "io.github.fixz.stersu.dev",
        "io.github.fixz.apkesu",
        "io.github.fixz.apkesu.dev",
    ] {
        let receiver = format!("{package}/me.weishu.kernelsu.stealth.StealthModeSyncReceiver");
        let status = Command::new("/system/bin/am")
            .args([
                "broadcast",
                "--include-stopped-packages",
                "--receiver-foreground",
                "-a",
                "me.weishu.kernelsu.action.STEALTH_MODE_DISABLED",
                "-n",
                &receiver,
            ])
            .status();
        if !status.is_ok_and(|value| value.success()) {
            warn!("failed to broadcast stealth recovery to {package}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn request_from(raw: &[u8]) -> Result<Request> {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0))?;
        let address = listener.local_addr()?;
        let data = raw.to_vec();
        let sender = std::thread::spawn(move || {
            let mut stream = TcpStream::connect(address).expect("connect test socket");
            stream.write_all(&data).expect("send test request");
        });
        let (mut stream, _) = listener.accept()?;
        let result = read_request(&mut stream);
        sender.join().expect("finish test sender");
        result
    }

    #[test]
    fn wrong_code_is_rejected() {
        assert!(code_matches("*#*#4211#*#*", "4211"));
        assert!(!code_matches("*#*#4211#*#*", "4212"));
        assert!(!code_matches("", "4211"));
    }

    #[test]
    fn encoded_code_preserves_whitespace_unicode_and_control_characters() {
        let code = "  密令\nwith\0symbols'  ";
        let encoded = code
            .as_bytes()
            .iter()
            .map(|byte| format!("{byte:02x}"))
            .collect::<String>();
        let stored = format!("{CODE_V2_MARKER}\n{encoded}\n");
        assert_eq!(decode_stored_code(&stored).unwrap(), code);
        assert!(code_matches(code, code));
        assert!(!code_matches(code, code.trim()));
        assert_eq!(
            decode_stored_code("*#*#4211#*#*\n").unwrap(),
            "*#*#4211#*#*"
        );
        assert!(decode_stored_code(&format!("{CODE_V2_MARKER}\nzz\n")).is_err());
    }

    #[test]
    fn oversized_recovery_request_is_rejected() {
        let request = format!(
            "POST /disable HTTP/1.1\r\nHost: {ADDRESS}\r\nContent-Length: {}\r\n\r\n",
            MAX_BODY + 1
        );
        assert!(request_from(request.as_bytes()).is_err());
    }

    #[test]
    fn request_at_body_limit_is_accepted() {
        let body = vec![b'a'; MAX_BODY];
        let header = format!(
            "POST /disable HTTP/1.1\r\nHost: {ADDRESS}\r\nContent-Length: {}\r\n\r\n",
            body.len()
        );
        let mut request = header.into_bytes();
        request.extend_from_slice(&body);
        assert_eq!(request_from(&request).unwrap().body.len(), MAX_BODY);
    }

    #[test]
    fn recovery_artifacts_survive_source_removal_and_explicit_refresh() {
        let temp = tempfile::tempdir().unwrap();
        let source = temp.path().join("apk/libksud.so");
        let binary = temp.path().join("adb/ksu/bin/stersu-stealth-recovery");
        let script = temp
            .path()
            .join("adb/service.d/99-stersu-stealth-recovery.sh");
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        fs::write(&source, b"ksud-v1").unwrap();
        install_boot_fallback_at(&source, &binary, &script).unwrap();

        // The daemon's fallback does not depend on the Manager staying alive.
        fs::remove_file(&source).unwrap();
        assert_eq!(fs::read(&binary).unwrap(), b"ksud-v1");
        assert_eq!(fs::read(&script).unwrap(), BOOT_SCRIPT_CONTENT);

        // A later explicit install refreshes the fallback without losing the script.
        fs::write(&source, b"ksud-v2").unwrap();
        install_boot_fallback_at(&source, &binary, &script).unwrap();
        assert_eq!(fs::read(&binary).unwrap(), b"ksud-v2");
        assert_eq!(fs::read(&script).unwrap(), BOOT_SCRIPT_CONTENT);
    }

    #[test]
    fn deleting_simulated_adb_directory_removes_stealth_marker() {
        let temp = tempfile::tempdir().unwrap();
        let adb = temp.path().join("adb");
        let marker = adb.join("ksu/hansu_mode");
        let script = adb.join("service.d/99-stersu-stealth-recovery.sh");
        fs::create_dir_all(marker.parent().unwrap()).unwrap();
        fs::create_dir_all(script.parent().unwrap()).unwrap();
        fs::write(&marker, b"").unwrap();
        fs::write(&script, BOOT_SCRIPT_CONTENT).unwrap();
        fs::remove_dir_all(&adb).unwrap();
        assert!(!marker.is_file());
        assert!(!script.is_file());
    }
}
