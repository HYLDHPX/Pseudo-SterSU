//! Filesystem portion of the upstream-compatible flat GKI KPM directory.
use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Clone, Debug)]
pub struct StoredImage {
    pub path: PathBuf,
    pub enabled: bool,
}

// Keep the exact, case-sensitive suffix used by the upstream boot scanner.
#[allow(clippy::case_sensitive_file_extension_comparisons)]
pub fn scan(root: &Path) -> io::Result<Vec<StoredImage>> {
    let mut images = Vec::new();
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        if !entry.file_type()?.is_file() {
            continue;
        }
        let name = entry.file_name();
        let Some(name) = name.to_str() else {
            continue;
        };
        if name.starts_with('.') {
            continue;
        }
        let enabled = name.ends_with(".kpm");
        if enabled || name.ends_with(".kpm.disabled") {
            images.push(StoredImage {
                path: entry.path(),
                enabled,
            });
        }
    }
    images.sort_by(|left, right| left.path.cmp(&right.path));
    Ok(images)
}

pub fn publish_new(path: &Path, bytes: &[u8]) -> io::Result<()> {
    static SEQUENCE: AtomicU64 = AtomicU64::new(0);
    let parent = path
        .parent()
        .ok_or_else(|| io::Error::other("missing KPM parent"))?;
    let temporary = parent.join(format!(
        ".kpm-import-{}-{}",
        std::process::id(),
        SEQUENCE.fetch_add(1, Ordering::Relaxed)
    ));
    let mut options = OpenOptions::new();
    options.create_new(true).write(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(&temporary)?;
    let result = (|| {
        file.write_all(bytes)?;
        file.sync_all()?;
        // Atomic no-replace publication: a conflict cannot destroy an existing
        // user's module, even if another manager wrote it after our scan.
        fs::hard_link(&temporary, path)
    })();
    drop(file);
    let _ = fs::remove_file(&temporary);
    result
}

pub fn set_enabled(image: &StoredImage, enabled: bool) -> io::Result<PathBuf> {
    if image.enabled == enabled {
        return Ok(image.path.clone());
    }
    let name = image
        .path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| io::Error::other("invalid KPM filename"))?;
    let name = if enabled {
        name.strip_suffix(".disabled")
            .ok_or_else(|| io::Error::other("invalid disabled KPM"))?
            .to_string()
    } else {
        format!("{name}.disabled")
    };
    let target = image.path.with_file_name(name);
    fs::hard_link(&image.path, &target)?;
    if let Err(error) = fs::remove_file(&image.path) {
        let _ = fs::remove_file(&target);
        return Err(error);
    }
    Ok(target)
}

pub fn regular_file(path: &Path) -> io::Result<File> {
    let mut options = OpenOptions::new();
    options.read(true);
    #[cfg(target_os = "android")]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.custom_flags(libc::O_NOFOLLOW);
    }
    let file = options.open(path)?;
    if !file.metadata()?.is_file() || fs::symlink_metadata(path)?.file_type().is_symlink() {
        return Err(io::Error::other("KPM must be a regular, non-symlink file"));
    }
    Ok(file)
}

#[cfg(test)]
mod tests {
    use super::*;

    struct Directory(PathBuf);
    impl Directory {
        fn new() -> Self {
            static NEXT: AtomicU64 = AtomicU64::new(0);
            let path = std::env::temp_dir().join(format!(
                "stersu-kpm-test-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            Self(path)
        }
    }
    impl Drop for Directory {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    #[test]
    fn scans_upstream_files_and_disabled_files_only() {
        let dir = Directory::new();
        for name in ["alpha.kpm", "beta.kpm.disabled", ".hidden.kpm", "note.txt"] {
            publish_new(&dir.0.join(name), b"fixture").unwrap();
        }
        fs::create_dir(dir.0.join("directory.kpm")).unwrap();
        let images = scan(&dir.0).unwrap();
        assert_eq!(images.len(), 2);
        assert!(images[0].enabled);
        assert!(!images[1].enabled);
    }

    #[test]
    fn publication_and_toggle_do_not_overwrite_conflicts() {
        let dir = Directory::new();
        let path = dir.0.join("alpha.kpm");
        publish_new(&path, b"old").unwrap();
        assert!(publish_new(&path, b"new").is_err());
        assert_eq!(fs::read(&path).unwrap(), b"old");
        let image = StoredImage {
            path,
            enabled: true,
        };
        let disabled = set_enabled(&image, false).unwrap();
        assert!(!image.path.exists());
        publish_new(&image.path, b"conflict").unwrap();
        assert!(
            set_enabled(
                &StoredImage {
                    path: disabled.clone(),
                    enabled: false
                },
                true
            )
            .is_err()
        );
        assert_eq!(fs::read(disabled).unwrap(), b"old");
        assert_eq!(fs::read(image.path).unwrap(), b"conflict");
    }

    #[test]
    fn disabling_survives_rescan_and_enabling_restores_original_name() {
        let dir = Directory::new();
        let path = dir.0.join("custom-name.kpm");
        publish_new(&path, b"module").unwrap();
        set_enabled(&scan(&dir.0).unwrap()[0], false).unwrap();
        let disabled = scan(&dir.0).unwrap();
        assert!(!disabled[0].enabled);
        assert_eq!(set_enabled(&disabled[0], true).unwrap(), path);
        assert_eq!(fs::read(path).unwrap(), b"module");
    }

    #[cfg(unix)]
    #[test]
    fn scan_does_not_follow_symlinks() {
        let dir = Directory::new();
        publish_new(&dir.0.join("target"), b"module").unwrap();
        std::os::unix::fs::symlink(dir.0.join("target"), dir.0.join("link.kpm")).unwrap();
        assert!(scan(&dir.0).unwrap().is_empty());
        assert!(regular_file(&dir.0.join("link.kpm")).is_err());
    }
}
