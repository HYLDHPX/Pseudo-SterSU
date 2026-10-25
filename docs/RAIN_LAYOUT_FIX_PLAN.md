# Rain layout fixes - 2026-10-25

Scope: fix the screenshot header/content overlap, delayed navigation feedback,
and search-field overflow. No APK packaging, device installation or publishing.

1. [x] Bound inline search painting and list viewports below transparent headers.
2. [x] Remove delayed/queued navigation feedback and clip retained pager pages.
3. [x] Add focused regressions and compile Debug/Release Kotlin and unit tests;
       verify existing APK hashes are unchanged.

Preserve all existing rain customization and unrelated working tree changes.

Implementation:
- Miuix superuser/module content now lives in a clipped viewport below the
  measured header. List padding does not count the header a second time.
  Refresh indicators share this viewport instead of painting in the status bar.
- Material superuser/module refresh viewports also clip scrolling content.
- Search surfaces clip their background/content to the rounded outline; long
  placeholders stay on one line and ellipsize in narrow windows.
- Main pager navigation uses Pager's own rounding/cancellation with a 240 ms
  transition. Repeated taps do not restart the same in-flight destination.
- Floating navigation observes a stable selection callback. Indicator animation
  children belong to the collecting job, so rapid taps cancel stale work.
  Programmatic selection does not simulate a drag press; rain selection takes
  180 ms and genuine rain drags use a restrained 1.04x press scale.
- Both the pager and retained page contents clip to their bounds.

Verification scope:
- Deterministic frame-clock tests cover completion, cancellation, rapid
  reselection and destination clamping. MainPagerState tests cover immediate
  selection, duplicate taps and stale settled-page feedback.
- Header/refresh viewport insets, search clipping, pager clipping and existing
  bottom/system-bar padding were reviewed against the saved source baseline.
- ADB reported no connected devices. Screenshot matching, real-device frame
  timing and notification-overlay behavior have not been visually verified.

Results:
- Debug and Release Kotlin compilation passed.
- Unit tests: 1198 total, 1197 passed, 1 skipped, zero failures/errors.
- All 7 new behavioral regression tests passed.
- Existing APK sizes and SHA-256 hashes are unchanged. No APK was packaged.
- Evidence: `out/rain-layout-fixes-verification-20261025.json`.
