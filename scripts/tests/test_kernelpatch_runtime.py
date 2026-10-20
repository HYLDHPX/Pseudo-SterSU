"""Host regression tests for the vendored Native GKI KPM integration (no flashing)."""
import json
import hashlib
import importlib.util
import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
VENDOR = ROOT / "third_party/kernelpatch"

HARNESS = r"""
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
typedef long (*init_fn)(const char *, const char *, void *);
typedef long (*exit_fn)(void *);
struct list_head { int unused; };
struct load_info {
    int len;
    const void *hdr;
    struct { const char *name; char error_msg[256]; } info;
};
struct module {
    struct { const char *name; } info;
    char *args;
    char load_event[32], load_source[16];
    init_fn *init;
    exit_fn *exit;
    void *start;
    struct list_head list;
};
static struct module modules, *published;
static int module_lock, scenario, init_calls, exit_calls, result_code;
static init_fn init_pointer;
static exit_fn exit_pointer;
#define __user
#define logkfd(...) ((void)0)
#define logkfi(...) ((void)0)
#define spin_lock(x) ((void)(x))
#define spin_unlock(x) ((void)(x))
#define list_add_tail(a,b) ((void)(a), (void)(b))
static void *vmalloc(size_t size) { return calloc(1, size); }
#define kvfree free
static void kp_free_exec(void *p) { (void)p; }
static void flush_icache_all(void) {}
static int elf_header_check(struct load_info *i) { (void)i; return 0; }
static int setup_load_info(struct load_info *i) { i->info.name = "fixture"; return 0; }
static struct module *find_module_locked(const char *n) { (void)n; return NULL; }
static void layout_sections(struct module *m, struct load_info *i) { (void)m; (void)i; }
static void layout_symtab(struct module *m, struct load_info *i) { (void)m; (void)i; }
static void set_load_error(struct load_info *i, const char *s) { snprintf(i->info.error_msg, 256, "%s", s); }
static const char *load_error(const struct load_info *i, const char *s) { return i->info.error_msg[0] ? i->info.error_msg : s; }
static void set_kpm_load_result(void *r, long code, const char *s) { (void)r; (void)s; result_code = code; }
static long fixture_init(const char *a, const char *e, void *r) { (void)a; (void)e; (void)r; init_calls++; return scenario == 3 ? -EINVAL : 0; }
static long fixture_exit(void *r) { (void)r; exit_calls++; return 0; }
static int move_module(struct module *m, struct load_info *i) {
    (void)i; init_pointer = NULL; exit_pointer = NULL;
    m->init = &init_pointer; m->exit = &exit_pointer; m->info.name = "fixture";
    published = m; return 0;
}
static int simplify_symbols(struct module *m, struct load_info *i) {
    (void)m;
    if (scenario == 1) { set_load_error(i, "unknown symbol: fixture"); return -ENOLINK; }
    return 0;
}
static int apply_relocations(struct module *m, struct load_info *i) {
    (void)m; (void)i;
    if (scenario != 2) { init_pointer = fixture_init; exit_pointer = fixture_exit; }
    return 0;
}
"""

MAIN = r"""
int main(void) {
    for (scenario = 0; scenario < 4; scenario++) {
        init_calls = 0; exit_calls = 0; result_code = 999;
        long rc = load_module_ex("ELF", 3, "", "load-file", "file", NULL);
        long expected = scenario == 0 ? 0 : scenario == 1 ? -ENOLINK : scenario == 2 ? -ENOEXEC : -EINVAL;
        if (rc != expected || result_code != expected) return 1;
        if (init_calls != (scenario == 0 || scenario == 3)) return 2;
        if (exit_calls != (scenario == 3)) return 3;
        if (scenario == 0) { free(published->args); free(published); }
    }
    return 0;
}
"""

class RuntimeTests(unittest.TestCase):
    def test_version_and_artifact_match(self):
        metadata = json.loads((ROOT / "third_party/sukisu-runtime/SOURCE.json").read_text())
        for name, artifact in metadata["artifacts"].items():
            data = (ROOT / "userspace/ksud/bin/aarch64" / name).read_bytes()
            self.assertEqual(len(data), artifact["size"])
            self.assertEqual(hashlib.sha256(data).hexdigest(), artifact["sha256"])
            self.assertEqual(hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest(), artifact["git_blob"])
        image = (ROOT / "userspace/ksud/bin/aarch64/kpimg").read_bytes()
        self.assertEqual(image[:8], b"KP1158\0\0")
        self.assertEqual(f"{image[11]}.{image[10]}.{image[9]}", metadata["release"])
        for name in (b"sukisu_kpm_version\0", b"sukisu_kpm_list\0", b"sukisu_kpm_control\0"):
            self.assertIn(name, image)

    def test_sync_rejects_corrupted_artifact(self):
        spec = importlib.util.spec_from_file_location("sync", ROOT / "scripts/sync_sukisu_runtime.py")
        sync = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(sync)
        metadata = json.loads(sync.MANIFEST.read_text())
        for name in metadata["artifacts"]:
            image = (ROOT / "userspace/ksud/bin/aarch64" / name).read_bytes()
            sync.validate(name, image, metadata)
            with self.assertRaises(ValueError):
                sync.validate(name, image[:-1] + bytes([image[-1] ^ 1]), metadata)

    def test_upstream_preset_matches_rust_patcher(self):
        header = ROOT / "third_party/sukisu-runtime/preset.h"
        source = ('#include <stddef.h>\n' + f'#include "{header.as_posix()}"\n' +
                  '_Static_assert(sizeof(preset_t) == 1400, "preset size");\n' +
                  '_Static_assert(offsetof(preset_t, setup.kimg_size) == 72, "original size");\n' +
                  '_Static_assert(offsetof(preset_t, setup.kpimg_size) == 80, "runtime size");\n' +
                  '_Static_assert(offsetof(preset_t, setup.setup_offset) == 104, "setup offset");\n' +
                  '_Static_assert(offsetof(preset_t, setup.header_backup) == 208, "entry backup");\n' +
                  'int main(void) { return 0; }\n')
        rust = (ROOT / "userspace/ksud/src/kpimg.rs").read_text()
        self.assertIn("const PRESET_SIZE: usize = 1400;", rust)
        self.assertIn("const HEADER_BACKUP: usize = PRESET_HEADER + 104 + 40;", rust)
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            (path / "test.c").write_text(source)
            subprocess.run(["cc", "-Wall", "-Werror", "-o", str(path / "test"), str(path / "test.c")], check=True)
            subprocess.run([str(path / "test")], check=True)

    def test_experimental_source_version(self):
        # This is NOT the source of the pinned upstream production binaries.
        metadata = json.loads((VENDOR / "SOURCE.json").read_text())
        defines = dict(re.findall(r"#define (MAJOR|MINOR|PATCH) (\d+)", (VENDOR / "version").read_text()))
        version = ".".join(defines[key] for key in ("MAJOR", "MINOR", "PATCH"))
        self.assertEqual(version, metadata["release"])

    def test_preset_contract_is_identical(self):
        self.assertEqual((VENDOR / "kernel/include/preset.h").read_text(), (VENDOR / "tools/preset.h").read_text())

    def test_loader_relocations_and_failures(self):
        source = (VENDOR / "kernel/patch/module/module.c").read_text()
        start = source.index("long load_module_ex(")
        end = source.index("\nlong unload_module(", start)
        function = source[start:end]
        with tempfile.TemporaryDirectory() as directory:
            for broken in (False, True):
                candidate = function
                if broken:
                    candidate = candidate.replace("if (!mod->init || !mod->exit)", "if (!mod->init || !*mod->init || !mod->exit || !*mod->exit)")
                path = pathlib.Path(directory)
                (path / "test.c").write_text(HARNESS + candidate + MAIN)
                subprocess.run(["cc", "-Wall", "-Werror", "-o", str(path / "test"), str(path / "test.c")], check=True)
                result = subprocess.run([str(path / "test")], check=False)
                if broken:
                    self.assertNotEqual(result.returncode, 0, "test must detect the old premature-null-check regression")
                else:
                    self.assertEqual(result.returncode, 0)

if __name__ == "__main__":
    unittest.main(verbosity=2)
