pub const fn is_compatible(kernel_version: u32, userspace_version: u32) -> bool {
    kernel_version == userspace_version
        // UAPI 5 added the services event. UAPI 6 only adds optional preinit
        // and kernel-symbol lookup capabilities; ksud gates both on the
        // kernel-reported version and keeps the UAPI 5 code paths otherwise.
        || (kernel_version == 4 && userspace_version == 5)
        || (kernel_version == 5 && userspace_version == 6)
}

pub const fn supports_services_report(kernel_version: u32) -> bool {
    kernel_version >= 5
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn additive_uapi_upgrades_are_compatible() {
        assert!(is_compatible(4, 5));
        assert!(is_compatible(5, 5));
        assert!(is_compatible(5, 6));
        assert!(is_compatible(6, 6));
        assert!(!is_compatible(3, 5));
        assert!(!is_compatible(6, 5));
        assert!(!is_compatible(5, 4));
        assert!(!is_compatible(4, 6));
        assert!(!is_compatible(0, 6));
        assert!(!is_compatible(6, 7));
    }

    #[test]
    fn legacy_kernels_do_not_receive_the_new_event() {
        assert!(!supports_services_report(0));
        assert!(!supports_services_report(4));
        assert!(supports_services_report(5));
        assert!(supports_services_report(6));
    }
}
