# SukiSU-compatible GKI KPM notice

This file is the source and compatibility notice for SterSU's Native GKI KPM
interface. It makes the upstream reference, implementation scope, licensing
boundary, and compatibility limits explicit.

## 中文说明

SterSU 的 Native GKI KPM 接口是参考
[SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) 的 GKI KPM 接口
和 KernelPatch/KPIMG 启动接管方式实现的兼容接口。SterSU 不是 SukiSU-Ultra
的官方发行版，也不代表 SukiSU-Ultra 作者背书或维护本项目。

本项目审计时使用的 SukiSU-Ultra 接口参考提交为
[`31255c460caefef75e98b3406dd40efcc00fad38`](https://github.com/SukiSU-Ultra/SukiSU-Ultra/commit/31255c460caefef75e98b3406dd40efcc00fad38)（2026-10-19）。
早期启动运行时 `kpimg` 和镜像工具 `kptools` 使用该提交的 Manager assets
原版文件，内置运行库实际版本为 **0.13.0**。它与独立运行库仓库的最新标签
0.13.9 不是同一个版本。两个文件的 SHA-256、Git blob 和固定下载路径见
[`third_party/sukisu-runtime/SOURCE.json`](../third_party/sukisu-runtime/SOURCE.json)。
同版本源码参考为 SukiSU_KernelPatch_patch 的 `0.13.0` 标签，提交
`762e6fb446f58020691b83509873268f6af634f2`；未宣称该快照能逐字节重现
上游二进制。原来的 `third_party/kernelpatch` 0.13.9 本地加固源码保留作
实验参考，不是本次内置文件的来源，也不再由默认构建脚本选用。

### 接口范围

- `CONFIG_KPM` 仅支持 AArch64、内置 `CONFIG_KSU=y` 的 GKI 内核；
  `CONFIG_KSU=m` 的 LKM 模式不启用这条 Native GKI KPM 链路。
- `uapi/supercall.h` 声明了能力查询、后端类型、KPM 操作号和扩展 ioctl。
- `uapi/extension.h` 对齐上游 `KSU_IOCTL_EXTENSION` / `KSU_EXTENSION_KPM`
  的 GET 和 IO 布局；只有旧内核缺少该传输接口（ENOTTY/ENOSYS）时才回退旧
  KPM ioctl。权限拒绝或操作失败不会通过另一套协议重复执行。
- GKI 从 `/data/adb/kpm/*.kpm` 扫描模块，禁用时使用 `.kpm.disabled`；
  参数、校验值、策略和恢复记录保存在 `.stersu/`。旧管理目录仅复制迁移，
  原文件保留。LKM 的 KPatch-Next 存储和加载路线不变。
- `kernel/kpm/` 提供 `sukisu_kpm_*` ABI 桥接、参数边界检查、失败关闭的
  能力探测和安全占位实现。
- `kpimg` 在早期启动阶段解析内核符号并接管兼容 ABI；`kptools` 只负责向
  `boot.img` 的 kernel Image 注入运行时并重写入口。
- `init_boot.img`、`vendor_boot.img`、LKM 和晚加载模式不属于这条 Native
  GKI KPM 镜像链路。LKM 的 KPM 页面和加载后端仍由 KPatch-Next 单独提供。

### SterSU 特有部分

SukiSU 兼容的是 ABI 和加载思路，运行库资源采用上游原版，不等于复制整个 Manager。以下
部分属于 SterSU 自有集成：

- 通过 SterSU supercall/JNI/ksud 进行能力查询和权限校验；
- Native GKI 与 KPatch-Next 使用互斥的后端识别和独立的管理路径；
- KPM 导入、启用、加载、卸载、控制和恢复标记由 SterSU 的状态机、日志和
  救砖恢复流程管理；
- 用户空间对路径、参数、名称、列表长度、镜像大小和操作状态进行边界校验；
- `boot-patch-kpimg` 只生成已校验的输出镜像，不负责 AVB 签名或刷写。

Native KPM 可执行内核代码，只有在确认来源可信、设备型号和 KMI 匹配并准备
好原始 boot 镜像和恢复方案后才应使用。任何兼容性、启动失败、无限重启或
数据损坏风险由使用者自行承担。

本次对齐 APK 内置 KPIMG 和配套工具，不替换设备内核，也不更新内置 LKM KO。
安装 APK 不会更新或回退设备 boot 内已经运行的 KernelPatch。目录仍使用私有权限，并保留
可信导入、SHA-256 校验、安全模式跳过和启动失败隔离，不照搬上游的宽松权限。
为兼容上游缓冲区边界，LIST/INFO 使用 1024 字节缓冲区，LOAD 参数最多
254 字节（预留 NUL 终止符）；CONTROL 参数仍最多 1023 字节。

## English declaration

SterSU's Native GKI KPM interface is a compatibility implementation designed
with reference to the GKI KPM interface in
[SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra) and the
KernelPatch/KPIMG early-boot takeover model. SterSU is not an official
SukiSU-Ultra distribution and this project is not endorsed or maintained by
the SukiSU-Ultra authors.

The audited SukiSU-Ultra interface reference is commit
[`31255c460caefef75e98b3406dd40efcc00fad38`](https://github.com/SukiSU-Ultra/SukiSU-Ultra/commit/31255c460caefef75e98b3406dd40efcc00fad38) (2026-10-19).
The bundled kpimg and kptools are the unmodified Manager assets from that
SukiSU-Ultra commit. The actual bundled runtime is **0.13.0**, not the separate
runtime repository's latest 0.13.9 tag. Exact sizes, SHA-256 values and Git blob
IDs are pinned in
[`third_party/sukisu-runtime/SOURCE.json`](../third_party/sukisu-runtime/SOURCE.json).
The same-version source reference is SukiSU_KernelPatch_patch tag `0.13.0`,
commit `762e6fb446f58020691b83509873268f6af634f2`; byte-reproducibility of the
upstream assets from this source has not been established. The locally hardened
0.13.9 source remains an experimental reference, not the source of the shipped
binaries and not the default release build input.

The upstream kptools uses `/system/bin/linker64`. SterSU accepts this dynamic
tool only when its complete SHA-256 matches the pinned artifact. Arbitrary
dynamic overrides are still rejected. `scripts/build_kernelpatch.ps1` and
`.sh` now synchronize/verify the pinned pair by default. Experimental source
builds require an explicit opt-in and a separate output directory. Release
ksud builds verify both artifact hashes, preventing accidental mixed versions.

The compatibility surface is limited to the documented KPM capability query,
operation numbers, extension ioctls, and `sukisu_kpm_*` bridge symbols. Native
GKI requires an AArch64 kernel with built-in `CONFIG_KSU=y` and `CONFIG_KPM=y`.
LKM and late-load builds remain on the separate KPatch-Next backend. SterSU's
supercall permissions, ksud state machine, import validation, rescue markers,
logging, and UI integration are SterSU-specific and are not claimed as
SukiSU-Ultra code.

This notice does not relicense any upstream or vendored source. Every source
file remains subject to its own SPDX header, upstream license, and the notices
in [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) and
[`GPL-COMPLIANCE.md`](../GPL-COMPLIANCE.md).

The Manager/ksud integration now prefers the upstream unified KPM extension
and scans flat `/data/adb/kpm/*.kpm` files. Legacy GKI imports are copied, not
deleted; disabled files use `.kpm.disabled`. Private recovery metadata lives
under `.stersu/`. Only ENOTTY/ENOSYS allows a legacy transport fallback. LKM
remains isolated on KPatch-Next. Private permissions, trusted import, hash
validation, safe-mode handling and boot quarantine remain SterSU-specific.
This update aligns the APK's bundled KPIMG/tools, not the device kernel or LKM
KOs. Installing the APK alone does not update or downgrade a runtime already
injected into a device boot image. Upstream binary assets do not include the
additional hardening in the separate local 0.13.9 experimental source tree.
The boot-image verifier recognizes the upstream 0.13.0 preset (1400 bytes,
entry backup offset 208) and the former 0.13.9 preset (1416 bytes, offset 224)
separately. Unsupported preset versions fail closed rather than guessing offsets.
The 0.13.0 patcher is invoked with an explicit empty `-s` compatibility argument;
its CLI otherwise rejects a missing argument. This does not add a recovery key
or enable the APatch root/supercall entry points removed by SukiSU.
