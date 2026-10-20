use std::io;

pub fn extension_ioctl_missing(error: &io::Error) -> bool {
    // Android/Linux ENOTTY and ENOSYS mean the transport is absent. A denied
    // or unsupported KPM operation must never be retried through another ABI.
    matches!(error.raw_os_error(), Some(25 | 38))
}

pub fn extension_or_legacy<T>(
    result: io::Result<T>,
    legacy: impl FnOnce() -> io::Result<T>,
) -> io::Result<T> {
    match result {
        Err(error) if extension_ioctl_missing(&error) => legacy(),
        result => result,
    }
}

pub fn kpimg_version(image: &[u8]) -> Option<String> {
    if image.len() < 12 || !image.starts_with(b"KP1158\0\0") {
        return None;
    }
    Some(format!("{}.{}.{}", image[11], image[10], image[9]))
}

pub fn format_runtime_version(raw: &str) -> String {
    let raw = raw.trim();
    let (number, suffix) = raw.split_once(char::is_whitespace).unwrap_or((raw, ""));
    match number.parse::<u32>() {
        Ok(version) if version <= 0x00ff_ffff => {
            let version = format!(
                "{}.{}.{}",
                version >> 16,
                (version >> 8) & 255,
                version & 255
            );
            if suffix.trim().is_empty() {
                version
            } else {
                format!("{version} {}", suffix.trim())
            }
        }
        _ => raw.to_owned(),
    }
}

pub const fn native_load_failure_hint(errno: Option<i32>, image_present: bool) -> &'static str {
    // Native GKI uses Linux errno values: ENOENT=2, ENOLINK=67.
    match (errno, image_present) {
        (Some(67), _) => {
            "the KPM imports a symbol unavailable in the running kernel/runtime; check the kernel log for the exact 'unknown symbol' and use a compatible module or KPM boot runtime"
        }
        (Some(2), true) => {
            "the verified KPM image still exists; ENOENT may mean an unresolved kernel/runtime symbol or a kernel-side file-open failure, not a missing import; check the kernel log for 'unknown symbol' and update the matching KPM boot runtime if required"
        }
        (Some(2), false) => {
            "the KPM image is no longer present; import the trusted original module again"
        }
        _ => {
            "check module/kernel compatibility and the kernel log; do not bypass quarantine or repeatedly retry an incompatible KPM"
        }
    }
}

pub fn parse_info_output(buffer: &[u8], operation_result: i32) -> io::Result<String> {
    let reported = usize::try_from(operation_result).map_err(|_| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            "native KPM info length is invalid",
        )
    })?;
    if reported > buffer.len() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "native KPM info is larger than its buffer",
        ));
    }
    let output = if reported == 0 {
        buffer
    } else {
        &buffer[..reported]
    };
    let end = output.iter().position(|byte| *byte == 0).ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            "native KPM info is not NUL terminated",
        )
    })?;

    // SukiSU reports zero after a successful copy. Positive lengths remain
    // accepted for images produced by earlier SterSU builds.
    Ok(String::from_utf8_lossy(&output[..end]).trim().to_string())
}

#[cfg(test)]
mod tests {
    use super::{
        extension_or_legacy, format_runtime_version, kpimg_version, native_load_failure_hint,
        parse_info_output,
    };
    use std::io;

    #[test]
    fn only_missing_extension_transport_can_retry_legacy() {
        for errno in [25, 38] {
            assert_eq!(
                extension_or_legacy(Err(io::Error::from_raw_os_error(errno)), || Ok(7)).unwrap(),
                7
            );
        }
        for errno in [1, 2, 5, 14, 22, 95] {
            let error =
                extension_or_legacy::<i32>(Err(io::Error::from_raw_os_error(errno)), || {
                    panic!("must not repeat a failed KPM operation")
                })
                .unwrap_err();
            assert_eq!(error.raw_os_error(), Some(errno));
        }
        assert_eq!(
            extension_or_legacy(Ok(0), || panic!(
                "must not repeat a successful KPM operation"
            ))
            .unwrap(),
            0
        );
    }

    #[test]
    fn reads_the_bundled_binary_version_not_source_defines() {
        assert_eq!(
            kpimg_version(b"KP1158\0\0\0\x09\x0d\0"),
            Some("0.13.9".into())
        );
        assert_eq!(kpimg_version(b"KP1158\0\0"), None);
        assert_eq!(kpimg_version(&[0; 12]), None);
    }

    #[test]
    fn formats_packed_runtime_versions_without_changing_custom_versions() {
        assert_eq!(format_runtime_version("3337 (build)"), "0.13.9 (build)");
        assert_eq!(format_runtime_version("3330"), "0.13.2");
        assert_eq!(format_runtime_version("0.13.9 custom"), "0.13.9 custom");
        assert_eq!(format_runtime_version(" "), "");
        assert_eq!(format_runtime_version("4294967295"), "4294967295");
    }

    #[test]
    fn does_not_misreport_a_missing_symbol_as_a_missing_import() {
        let hint = native_load_failure_hint(Some(2), true);
        assert!(hint.contains("unresolved"));
        assert!(hint.contains("file-open"));
        assert!(!hint.contains("import the trusted original"));
        assert!(native_load_failure_hint(Some(2), false).contains("import the trusted original"));
        assert!(native_load_failure_hint(Some(67), true).contains("imports a symbol"));
    }

    #[test]
    fn accepts_sukisu_zero_result() {
        let mut buffer = [0u8; 64];
        buffer[..18].copy_from_slice(b"name=hello\nvalue=1");

        assert_eq!(
            parse_info_output(&buffer, 0).unwrap(),
            "name=hello\nvalue=1"
        );
    }

    #[test]
    fn accepts_legacy_positive_result() {
        let mut buffer = [0u8; 32];
        buffer[..5].copy_from_slice(b"hello");

        assert_eq!(parse_info_output(&buffer, 6).unwrap(), "hello");
    }

    #[test]
    fn rejects_invalid_output() {
        assert!(parse_info_output(&[b'x'; 8], 0).is_err());
        assert!(parse_info_output(&[0; 8], 9).is_err());
        assert!(parse_info_output(&[0; 8], -5).is_err());
    }
}
