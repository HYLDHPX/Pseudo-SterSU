# Rain refinement - 2026-10-25

Scope: implement the accepted rain refinement proposal for Miuix, Material and
enhanced Miuix. Source and compile/test checks only; do not assemble/install an APK.

1. [x] Preserve motion across speed changes; add viewport density, depth speeds
   and continuous wind while keeping one lifecycle-aware scene clock.
2. [x] Add independently adjustable ornament strengths and thunder timing,
   including profile storage, safe import/export, presets and translated settings.
3. [x] Refine cached card/chrome artwork, continuous runoff and navigation selection
   feedback; improve shape-aware impacts, localized wet highlights and visibility.
4. [x] Run Kotlin compilation and regression tests; check disabled/reduced motion,
   wallpapers, compact/tablet geometry, settings compatibility and unchanged APKs.

Do not change kernel/userspace, publish repositories, install to a device, restore
automatic battery saving, or add a dedicated preview page.

Implementation completed. Debug and Release Kotlin compilation and the first
full unit suite passed. Final regression rerun includes shared reduced-motion
permission, so selection pulses and thunder reflections cannot freeze mid-effect.

Final: Debug/Release Kotlin compilation passed; 1190 passed, 1 skipped,
all 95 Rain tests passed. Four matching resource variants verified. Both existing
APKs retain their original sizes and SHA-256 hashes. No APK packaged or installed.
