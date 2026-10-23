"""Exercise real GKI boot helpers with host C stubs and GNU make.

Run under Linux/WSL with Python 3, cc and make. KSU_BOOT_TEST_REF=HEAD
checks the committed sources without changing the working tree.
No kernel image is built or flashed and no device data is accessed.
"""

import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


def source(relative):
    ref = os.environ.get("KSU_BOOT_TEST_REF")
    if ref:
        return subprocess.check_output(
            ["git", "show", f"{ref}:{relative}"], cwd=ROOT, text=True
        )
    return (ROOT / relative).read_text(encoding="utf-8")


def function(text, name):
    match = re.search(rf"^static [^\n]+\b{name}\([^\n]*\)\n\{{", text, re.M)
    if match is None:
        raise AssertionError(f"Missing real function {name}")
    end = text.index("\n}", match.start()) + 2
    return text[match.start():end]


GLUE = r'''
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>

#define IS_ERR(p) ((uintptr_t)(p) >= (uintptr_t)-4095)
#define ERR_PTR(e) ((void *)(intptr_t)(e))
struct dentry { bool regular; struct { const char *name; } d_name; };
struct path { struct dentry *dentry; const char *mounted_path; };
struct file { struct path f_path; };
struct task_struct { const char *comm; };
static struct task_struct task;
#define current (&task)

static bool d_is_reg(struct dentry *entry) { return entry->regular; }
static char *d_path(const struct path *path, char *buf, size_t size)
{
    if (!path->mounted_path)
        return ERR_PTR(-2);
    if (strlen(path->mounted_path) >= size)
        return ERR_PTR(-36);
    strcpy(buf, path->mounted_path);
    return buf;
}

static unsigned int available;
static char attempts[512];
static struct file opened;
static struct file *filp_open(const char *path, int flags, int mode)
{
    (void)flags;
    (void)mode;
    strcat(attempts, path);
    strcat(attempts, ",");
    unsigned int bit = !strcmp(path, "/metadata/vold/ksu/modules.rc") ? 1 :
        !strcmp(path, "/metadata/watchdog/ksu/modules.rc") ? 2 : 4;
    return available & bit ? &opened : ERR_PTR(-2);
}
'''

MAIN = r'''
int main(int argc, char **argv)
{
    if (argc == 6 && !strcmp(argv[1], "initrc")) {
        struct dentry entry = { .regular = atoi(argv[5]), .d_name.name = argv[3] };
        struct file file = { .f_path = { .dentry = &entry,
            .mounted_path = strcmp(argv[4], "-") ? argv[4] : NULL } };
        task.comm = argv[2];
        printf("%d\n", is_init_rc(&file));
        return 0;
    }
    if (argc == 3 && !strcmp(argv[1], "module_rc")) {
        const char *chosen = NULL;
        available = (unsigned int)atoi(argv[2]);
        struct file *file = open_module_rc(&chosen);
        printf("%d|%s|%s\n", !IS_ERR(file), chosen, attempts);
        return 0;
    }
    return 2;
}
'''


class GkiBootIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cc = shutil.which("cc")
        cls.make = shutil.which("make")
        if not cls.cc or not cls.make:
            raise RuntimeError("These tests require cc and GNU make (use Linux/WSL)")
        cls.temporary = tempfile.TemporaryDirectory(prefix="stersu-gki-boot-")
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.directory = pathlib.Path(cls.temporary.name)
        cls.binaries = {}
        for variant in ("ksud_integration", "ksud_integration_susfs"):
            text = source(f"kernel/runtime/{variant}.c")
            constants = "\n".join(re.findall(r'^#define MODULE_RC_PATH_.*$', text, re.M))
            fixture = cls.directory / f"{variant}.c"
            fixture.write_text(
                GLUE + constants + "\n" + function(text, "is_init_rc") + "\n"
                + function(text, "open_module_rc") + "\n" + MAIN,
                encoding="utf-8",
            )
            binary = cls.directory / variant
            subprocess.run(
                [cls.cc, "-std=gnu11", "-Wall", "-Wextra", "-Werror",
                 str(fixture), "-o", str(binary)], check=True, capture_output=True, text=True,
            )
            cls.binaries[variant] = binary

    def test_init_rc_uses_mounted_path(self):
        cases = [
            ("init", "init.rc", "/system/etc/init/hw/init.rc", "1", "1"),
            ("init", "replacement.rc", "/system/etc/init/hw/init.rc", "1", "1"),
            ("init", "init.rc", "/data/local/tmp/init.rc", "1", "0"),
            ("init", "init.rc", "/system/etc/init/hw/init.rc.bak", "1", "0"),
            ("sh", "init.rc", "/system/etc/init/hw/init.rc", "1", "0"),
            ("init", "init.rc", "/system/etc/init/hw/init.rc", "0", "0"),
            ("init", "init.rc", "-", "1", "0"),
            ("init", "init.rc", "/" + "x" * 300, "1", "0"),
        ]
        for variant, binary in self.binaries.items():
            for comm, name, path, regular, expected in cases:
                with self.subTest(variant=variant, comm=comm, name=name, path=path, regular=regular):
                    result = subprocess.check_output(
                        [str(binary), "initrc", comm, name, path, regular], text=True,
                    ).strip()
                    self.assertEqual(result, expected)

    def test_module_rc_matches_userspace_locations(self):
        paths = ["/metadata/vold/ksu/modules.rc", "/metadata/watchdog/ksu/modules.rc",
                 "/metadata/ksu/modules.rc"]
        # Cover every availability combination, including all locations absent.
        for variant, binary in self.binaries.items():
            for mask in range(8):
                with self.subTest(variant=variant, available=mask):
                    index = next((i for i in range(3) if mask & (1 << i)), 2)
                    expected = f"{int(mask != 0)}|{paths[index]}|" + ",".join(paths[:index + 1]) + ","
                    self.assertEqual(subprocess.check_output(
                        [str(binary), "module_rc", str(mask)], text=True,
                    ).strip(), expected)

    def test_preinit_build_capability_matrix(self):
        kbuild = source("kernel/Kbuild")
        block = kbuild[kbuild.index("# preinit runner:"):kbuild.index("obj-$(CONFIG_KSU) += kernelsu.o")]
        for susfs, mode in (("n", "y"), ("n", "m"), ("y", "y")):
            for headers in (False, True):
                with self.subTest(susfs=susfs, ksu=mode, headers=headers):
                    objtree = self.directory / f"obj-{susfs}-{mode}-{int(headers)}"
                    if headers:
                        header = objtree / "usr/include/linux/unistd.h"
                        header.parent.mkdir(parents=True)
                        header.touch()
                    makefile = (
                        f"objtree := {objtree}\nobj := {objtree}/kernelsu\n"
                        f"KSU_KERNEL_DIR := {ROOT / 'kernel'}\nKSU_ROOT_DIR := {ROOT}\n"
                        f"CC := {self.cc}\nCONFIG_KSU_SUSFS := {susfs}\nCONFIG_KSU := {mode}\n"
                        + block + '\n.PHONY: inspect\ninspect:\n'
                        '\t@echo "objects=$(kernelsu-objs)"\n'
                        '\t@echo "flags=$(ccflags-y)"\n'
                        '\t@echo "targets=$(targets)"\n'
                    )
                    result = subprocess.run(
                        [self.make, "--no-print-directory", "-f", "-", "inspect"],
                        input=makefile, text=True, capture_output=True, check=True,
                    ).stdout
                    enabled = headers and susfs != "y"
                    self.assertEqual("preinit/preinit.o" in result, enabled)
                    self.assertEqual("preinit/blob.o" in result, enabled)
                    self.assertEqual("targets=preinit/runner.elf" in result, enabled)
                    self.assertEqual("-DKSU_NO_PREINIT" in result, not enabled)

    def test_disabled_preinit_reports_unavailable(self):
        includes = self.directory / "include"
        (includes / "linux").mkdir(parents=True)
        (includes / "asm").mkdir()
        (includes / "linux/types.h").write_text(
            "#include <stdbool.h>\n#define __user\n", encoding="utf-8",
        )
        (includes / "asm/ptrace.h").write_text(
            "struct pt_regs;\n", encoding="utf-8",
        )
        header = self.directory / "preinit.h"
        header.write_text(source("kernel/preinit/preinit.h"), encoding="utf-8")
        fixture = self.directory / "preinit-disabled.c"
        fixture.write_text(
            '#include "preinit.h"\nint main(void) {\n'
            '    ksu_preinit_init();\n    ksu_preinit_release();\n'
            '    ksu_preinit_exit();\n'
            '    return ksu_preinit_enabled_param() ||\n'
            '        ksu_preinit_handle_execve(0, 0, 0, 0, 0);\n}\n',
            encoding="utf-8",
        )
        binary = self.directory / "preinit-disabled"
        subprocess.run(
            [self.cc, "-std=gnu11", "-Wall", "-Wextra", "-Werror",
             "-Wno-unused-parameter", "-DKSU_NO_PREINIT", "-I", str(includes),
             str(fixture), "-o", str(binary)],
            check=True, capture_output=True, text=True,
        )
        subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main(verbosity=2)
