//! Feature ids shared by the feature manager and boot-patch (which also runs on the host).

#![cfg_attr(not(target_os = "android"), allow(dead_code))]

use anyhow::{Result, bail};

const AVC_SPOOF_LEGACY_ID: u32 = 10003;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u32)]
pub enum FeatureId {
    SuCompat = 0,
    KernelUmount = 1,
    Sulog = 2,
    AdbRoot = 3,
    SelinuxHide = 4,
    AvcSpoof = 5,
    WebviewZygoteUmount = 7,
    SeccompHookStatus = 8,
    SeccompHookLastError = 9,
    SeccompHookCallCount = 10,
    SeccompHookReleaseCount = 11,
    SeccompHookFailureCount = 12,
    // SterSU reserves upstream ID 6 for hook status; retain that UAPI contract.
    ShellSuAdbOnly = 13,
}

impl FeatureId {
    pub const ALL: [Self; 13] = [
        Self::SuCompat,
        Self::KernelUmount,
        Self::Sulog,
        Self::AdbRoot,
        Self::SelinuxHide,
        Self::AvcSpoof,
        Self::WebviewZygoteUmount,
        Self::SeccompHookStatus,
        Self::SeccompHookLastError,
        Self::SeccompHookCallCount,
        Self::SeccompHookReleaseCount,
        Self::SeccompHookFailureCount,
        Self::ShellSuAdbOnly,
    ];

    pub const fn from_u32(id: u32) -> Option<Self> {
        match id {
            0 => Some(Self::SuCompat),
            1 => Some(Self::KernelUmount),
            2 => Some(Self::Sulog),
            3 => Some(Self::AdbRoot),
            4 => Some(Self::SelinuxHide),
            5 | AVC_SPOOF_LEGACY_ID => Some(Self::AvcSpoof),
            7 => Some(Self::WebviewZygoteUmount),
            8 => Some(Self::SeccompHookStatus),
            9 => Some(Self::SeccompHookLastError),
            10 => Some(Self::SeccompHookCallCount),
            11 => Some(Self::SeccompHookReleaseCount),
            12 => Some(Self::SeccompHookFailureCount),
            13 => Some(Self::ShellSuAdbOnly),
            _ => None,
        }
    }

    pub const fn name(self) -> &'static str {
        match self {
            Self::SuCompat => "su_compat",
            Self::KernelUmount => "kernel_umount",
            Self::Sulog => "sulog",
            Self::AdbRoot => "adb_root",
            Self::SelinuxHide => "selinux_hide",
            Self::AvcSpoof => "avc_spoof",
            Self::WebviewZygoteUmount => "webview_zygote_umount",
            Self::SeccompHookStatus => "seccomp_hook_status",
            Self::SeccompHookLastError => "seccomp_hook_last_error",
            Self::SeccompHookCallCount => "seccomp_hook_call_count",
            Self::SeccompHookReleaseCount => "seccomp_hook_release_count",
            Self::SeccompHookFailureCount => "seccomp_hook_failure_count",
            Self::ShellSuAdbOnly => "shell_su_adb_only",
        }
    }

    pub const fn canonical_id(self) -> u32 {
        self as u32
    }

    pub const fn legacy_id(self) -> Option<u32> {
        match self {
            Self::AvcSpoof => Some(AVC_SPOOF_LEGACY_ID),
            _ => None,
        }
    }

    pub const fn is_read_only(self) -> bool {
        matches!(
            self,
            Self::SeccompHookStatus
                | Self::SeccompHookLastError
                | Self::SeccompHookCallCount
                | Self::SeccompHookReleaseCount
                | Self::SeccompHookFailureCount
        )
    }

    pub const fn description(self) -> &'static str {
        match self {
            Self::SuCompat => {
                "SU Compatibility Mode - allows authorized apps to gain root via traditional 'su' command"
            }
            Self::KernelUmount => {
                "Kernel Umount - controls whether kernel automatically unmounts modules when not needed"
            }
            Self::Sulog => {
                "SU Log - streams kernel sulog events to userspace and persists them to disk"
            }
            Self::AdbRoot => "ADB Root - Enable adbd root",
            Self::SelinuxHide => {
                "SELinux Hide - sanitize /sys/fs/selinux access results for app UIDs"
            }
            Self::AvcSpoof => "AVC Spoof - hide KernelSU SELinux domains in AVC audit logs",
            Self::WebviewZygoteUmount => {
                "WebView Zygote Umount - unmount modules from WebView zygote and its isolated children"
            }
            Self::SeccompHookStatus => "GKI Seccomp hook capability and runtime status flags",
            Self::SeccompHookLastError => "GKI Seccomp hook most recent errno",
            Self::SeccompHookCallCount => "GKI Seccomp hook invocation count",
            Self::SeccompHookReleaseCount => "GKI Seccomp filter release count",
            Self::SeccompHookFailureCount => "GKI Seccomp hook failure count",
            Self::ShellSuAdbOnly => {
                "Shell SU ADB Only - require a granted shell process to descend from adbd or a KernelSU root process"
            }
        }
    }
}

pub fn parse_feature_id(name: &str) -> Result<FeatureId> {
    match name {
        "su_compat" | "0" => Ok(FeatureId::SuCompat),
        "kernel_umount" | "1" => Ok(FeatureId::KernelUmount),
        "sulog" | "2" => Ok(FeatureId::Sulog),
        "adb_root" | "3" => Ok(FeatureId::AdbRoot),
        "selinux_hide" | "4" => Ok(FeatureId::SelinuxHide),
        "avc_spoof" | "5" | "10003" => Ok(FeatureId::AvcSpoof),
        "webview_zygote_umount" | "7" => Ok(FeatureId::WebviewZygoteUmount),
        "seccomp_hook_status" | "8" => Ok(FeatureId::SeccompHookStatus),
        "seccomp_hook_last_error" | "9" => Ok(FeatureId::SeccompHookLastError),
        "seccomp_hook_call_count" | "10" => Ok(FeatureId::SeccompHookCallCount),
        "seccomp_hook_release_count" | "11" => Ok(FeatureId::SeccompHookReleaseCount),
        "seccomp_hook_failure_count" | "12" => Ok(FeatureId::SeccompHookFailureCount),
        "shell_su_adb_only" | "13" => Ok(FeatureId::ShellSuAdbOnly),
        _ => bail!("Unknown feature: {name}"),
    }
}
