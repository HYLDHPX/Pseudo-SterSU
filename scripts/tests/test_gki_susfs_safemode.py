"""Exercise the real GKI SUSFS safe-mode callback with host static-key stubs.

Run under Linux/WSL with Python 3 and cc. KSU_BOOT_TEST_REF=HEAD checks the
committed callback to reproduce the stopped-hook regression. No device access.
"""

import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = "kernel/runtime/ksud_integration_susfs.c"


GLUE = r"""
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <linux/input-event-codes.h>
static bool ksu_is_input_hook_enabled = true;
#define static_branch_likely(key) (*(key))
#define static_key_enabled(key) (*(key))
#define static_branch_disable(key) (*(key) = false)
#define pr_info(...) ((void)0)
"""

MAIN = r"""
static void press(unsigned int type, unsigned int code, int value)
{
    ksu_handle_input_handle_event(&type, &code, &value);
}

int main(int argc, char **argv)
{
    if (argc != 2)
        return 2;
    if (!strcmp(argv[1], "disabled")) {
        ksu_stop_input_hook_runtime();
        ksu_stop_input_hook_runtime();
    } else if (!strcmp(argv[1], "stopped_after_two")) {
        press(EV_KEY, KEY_VOLUMEDOWN, 1);
        press(EV_KEY, KEY_VOLUMEDOWN, 1);
        ksu_stop_input_hook_runtime();
    } else if (!strcmp(argv[1], "releases")) {
        for (int i = 0; i < 10; i++)
            press(EV_KEY, KEY_VOLUMEDOWN, 0);
        printf("%u|%d\n", volumedown_pressed_count, ksu_is_safe_mode());
        return 0;
    } else if (!strcmp(argv[1], "other_events")) {
        for (int i = 0; i < 10; i++) {
            press(EV_KEY, KEY_VOLUMEUP, 1);
            press(EV_SYN, KEY_VOLUMEDOWN, 1);
        }
        printf("%u|%d\n", volumedown_pressed_count, ksu_is_safe_mode());
        return 0;
    }
    for (int i = 0; i < 10; i++)
        press(EV_KEY, KEY_VOLUMEDOWN, 1);
    bool safe = ksu_is_safe_mode();
    printf("%u|%d|%d\n", volumedown_pressed_count, safe,
           ksu_is_input_hook_enabled);
    return 0;
}
"""


class GkiSusfsSafeModeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cc = shutil.which("cc")
        if not cc:
            raise RuntimeError("These tests require cc (use Linux/WSL)")
        ref = os.environ.get("KSU_BOOT_TEST_REF")
        source = subprocess.check_output(
            ["git", "show", f"{ref}:{SOURCE}"], cwd=ROOT, text=True,
        ) if ref else (ROOT / SOURCE).read_text(encoding="utf-8")
        start = source.index("static unsigned int volumedown_pressed_count = 0;")
        end = source.index("void ksu_handle_vfs_fstat", start)
        cls.temporary = tempfile.TemporaryDirectory(prefix="stersu-gki-safemode-")
        cls.addClassCleanup(cls.temporary.cleanup)
        directory = pathlib.Path(cls.temporary.name)
        fixture = directory / "safemode.c"
        fixture.write_text(GLUE + source[start:end] + MAIN, encoding="utf-8")
        cls.binary = directory / "safemode"
        subprocess.run(
            [cc, "-std=gnu11", "-Wall", "-Wextra", "-Werror",
             str(fixture), "-o", str(cls.binary)], check=True,
        )

    def check_mode(self, mode, expected):
        self.assertEqual(
            subprocess.check_output([str(self.binary), mode], text=True).strip(),
            expected,
        )

    def test_stopped_hook_ignores_late_direct_callbacks(self):
        self.check_mode("disabled", "0|0|0")

    def test_stopping_before_threshold_cannot_enter_safe_mode(self):
        self.check_mode("stopped_after_two", "2|0|0")

    def test_boot_volume_presses_still_enable_safe_mode(self):
        self.check_mode("enabled", "3|1|0")

    def test_releases_do_not_count(self):
        self.check_mode("releases", "0|0")

    def test_unrelated_input_does_not_count(self):
        self.check_mode("other_events", "0|0")


if __name__ == "__main__":
    unittest.main(verbosity=2)
