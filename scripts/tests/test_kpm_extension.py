"""Compile the real extension dispatcher with host mocks; never flash a device."""
import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

HARNESS = r"""
#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include "uapi/extension.h"
#define __user
static bool ksu_late_loaded;
static int fail_read, fail_write, calls;
static void *forwarded;
static int copy_from_user(void *to, const void *from, size_t size) {
    if (fail_read || !from) return 1;
    memcpy(to, from, size); return 0;
}
static int copy_to_user(void *to, const void *from, size_t size) {
    if (fail_write || !to) return 1;
    memcpy(to, from, size); return 0;
}
#if defined(CONFIG_KPM) && !defined(MODULE)
static int do_kpm(void *arg) { calls++; forwarded = arg; return 17; }
#endif
_Static_assert(sizeof(struct ksu_extension_cmd) == 32, "extension ABI size");
_Static_assert(offsetof(struct ksu_extension_cmd, value) == 16, "value offset");
_Static_assert(offsetof(struct ksu_extension_cmd, user_ptr) == 24, "pointer offset");
"""

MAIN = r"""
int main(void) {
    struct ksu_extension_cmd cmd = {0};
    cmd.op = KSU_EXTENSION_GET;
    assert(KSU_IOCTL_EXTENSION == _IOC(_IOC_READ | _IOC_WRITE, 'K', 300, 32));
    assert(do_extension(NULL) == -EFAULT);
    fail_read = 1; assert(do_extension(&cmd) == -EFAULT); fail_read = 0;
    fail_write = 1; assert(do_extension(&cmd) == -EFAULT); fail_write = 0;
    cmd.reserved = 1; assert(do_extension(&cmd) == -EINVAL); cmd.reserved = 0;
    cmd.id = KSU_EXTENSION_SUSFS;
    assert(do_extension(&cmd) == -EOPNOTSUPP); cmd.id = KSU_EXTENSION_KPM;
    for (int late = 0; late < 2; late++) {
        ksu_late_loaded = late;
        cmd.op = KSU_EXTENSION_GET;
        cmd.supported = 99; cmd.value = 99; cmd.user_ptr = 99;
        assert(do_extension(&cmd) == 0);
#if defined(CONFIG_KPM) && !defined(MODULE)
        assert(cmd.supported == (late ? 0 : KSU_FEATURE_FLAG_SUPPORTED));
        assert(cmd.value == (late ? 0 : 1));
#else
        assert(cmd.supported == 0 && cmd.value == 0);
#endif
        assert(cmd.user_ptr == 0);
        cmd.op = KSU_EXTENSION_IO;
        calls = 0; forwarded = NULL;
        cmd.user_ptr = 0;
#if defined(CONFIG_KPM) && !defined(MODULE)
        assert(do_extension(&cmd) == (late ? -EOPNOTSUPP : -EFAULT));
#else
        assert(do_extension(&cmd) == -EOPNOTSUPP);
#endif
        cmd.user_ptr = 0x1234;
#if defined(CONFIG_KPM) && !defined(MODULE)
        assert(do_extension(&cmd) == (late ? -EOPNOTSUPP : 17));
        assert(calls == (late ? 0 : 1));
        if (!late) assert(forwarded == (void *)(uintptr_t)0x1234);
#else
        assert(do_extension(&cmd) == -EOPNOTSUPP);
        assert(calls == 0);
#endif
        cmd.op = KSU_EXTENSION_SET; assert(do_extension(&cmd) == -EOPNOTSUPP);
        cmd.op = KSU_EXTENSION_WIPE; assert(do_extension(&cmd) == -EOPNOTSUPP);
        cmd.op = 99; assert(do_extension(&cmd) == -EINVAL);
    }
    return 0;
}
"""


class ExtensionTests(unittest.TestCase):
    def test_mirrored_header_and_permission_contract(self):
        self.assertEqual(
            (ROOT / "uapi/extension.h").read_bytes(),
            (ROOT / "manager/app/src/main/cpp/uapi/extension.h").read_bytes(),
        )
        source = (ROOT / "kernel/supercall/dispatch.c").read_text()
        self.assertRegex(source, re.compile(
            r"\.cmd = KSU_IOCTL_EXTENSION,\s*\.name = \"EXTENSION\","
            r"\s*\.handler = do_extension,\s*\.perm_check = manager_or_root"
        ))

    def test_dispatcher_in_gki_lkm_and_disabled_builds(self):
        source = (ROOT / "kernel/supercall/dispatch.c").read_text()
        start = source.index("static int do_extension(")
        end = source.index("\n#if IS_ENABLED(CONFIG_ABK_CONTROL)", start)
        with tempfile.TemporaryDirectory(prefix="stersu-kpm-extension-") as directory:
            path = pathlib.Path(directory)
            (path / "test.c").write_text(HARNESS + source[start:end] + MAIN)
            for defines in (["-DCONFIG_KPM"], ["-DCONFIG_KPM", "-DMODULE"], []):
                with self.subTest(defines=defines):
                    subprocess.run([
                        "cc", "-std=gnu11", "-Wall", "-Werror", *defines,
                        "-I", str(ROOT), str(path / "test.c"), "-o", str(path / "test"),
                    ], check=True)
                    subprocess.run([str(path / "test")], check=True)


if __name__ == "__main__":
    unittest.main(verbosity=2)
