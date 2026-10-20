/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __KSU_UAPI_EXTENSION_H
#define __KSU_UAPI_EXTENSION_H

#include "uapi/supercall.h"

/* KPM subset of SukiSU-Ultra's unified extension ABI. Other IDs are
 * reserved here, not advertised as implemented by SterSU. */
enum ksu_extension_id {
    KSU_EXTENSION_KPM = 0,
    KSU_EXTENSION_CPU_SPOOF = 1,
    KSU_EXTENSION_UMOUNT_PATHS = 2,
    KSU_EXTENSION_DYNAMIC_MANAGER = 3,
    KSU_EXTENSION_SUSFS = 4,
    KSU_EXTENSION_MAX
};

#define KSU_EXTENSION_GET 1u
#define KSU_EXTENSION_SET 2u
#define KSU_EXTENSION_IO 3u
#define KSU_EXTENSION_WIPE 4u
#define KSU_EXTENSION_FLAG_FORCED (1u << 7)

struct ksu_extension_cmd {
    __u32 id;
    __u32 op;
    __u32 supported;
    __u32 reserved;
    __u64 value;
    __u64 user_ptr;
};

static const __u32 KSU_IOCTL_EXTENSION =
    _IOC(_IOC_READ | _IOC_WRITE, 'K', 300, sizeof(struct ksu_extension_cmd));

#endif /* __KSU_UAPI_EXTENSION_H */
