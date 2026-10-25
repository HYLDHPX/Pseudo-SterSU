# Built-in rain interface

## Continuous rain and regional ornaments (2026-10-25)

The refinement applies to the three Rain layouts: Miuix, Material and
enhanced Miuix. The existing wallpaper opt-in, semantic colors and UI passthrough
controls are preserved. Text, icons and images are never faded by ornament strength.

- A single resumed-lifecycle/system-animation-aware clock accumulates rain travel
  without wrapping or restarting on a speed change. Independent elapsed time drives
  wind, runoff, selection feedback and thunder. There are no per-card animation jobs.
- Density uses the visible dp area (400 x 800 dp reference) with a maximum of 128
  particles across both layers. Near, middle and far particles have distinct physical
  fall speeds, independent of window height, and retain their seeds when density changes.
- Advanced controls add five 0..1 regional multipliers: card edges, card interiors,
  top bar, navigation and sidebar. Each multiplies the existing decoration master.
  They are independent of background/surface/content opacity. Thunder interval is
  configurable from 8 to 60 seconds and does not follow the rain-speed setting.
- Condensation, rim highlights and sparse runoff stay near card edges, with stable
  per-instance variation. Wetness follows intensity. Top bars collect and release
  droplets; sidebar runoff grows, merges and fades before draining at the bottom.
- Navigation selection emits one shared-clock ripple. Inactive items stay quiet;
  water clipping does not crop the underlying navigation label. Standard/custom
  Miuix bars and rails, Material bars and rails, enhanced Miuix sidebar, floating
  navigation and compact-window fallback all use the same feedback modifier.
- Real rounded/circular outlines drive surface crossings and highlight tangents.
  Clipped edges and foreign layout roots cannot become fake interaction targets.
  Unsupported generic outlines are omitted from physical collisions rather than
  approximated as rectangles. A spatial x index prunes collision candidates.
- Stable card outlines, reflections, chrome paths, rain-streak gradients and distant
  thunder geometry are cached per size/palette. Thunder adds one gentle local pulse
  and synchronized wet-rim reflections, never an opaque full-screen flash.
- Preference storage, strict theme sharing, profile copying and local presets include
  the new values. Older themes keep defaults. English and all three Chinese resource
  variants have the same 30 unique decoration keys.
- Automatic power saving and the dedicated preview page are not reintroduced.

Verification on the final sources:

- Debug and Release Kotlin compilation succeeded; 1191 unit tests total,
  1190 passed and 1 opt-in live-network test skipped. All 95 Rain tests passed.
- Added regressions cover speed continuity, pause/resume, reduced motion, dp-area
  budgets, depth speed, wind smoothness, runoff lifecycle, collecting drips, one-shot
  navigation, independent thunder cadence, rounded/elliptic hits, spatial candidates,
  profile round trips, invalid/legacy imports, copy/presets and region isolation.
- No APK assembled, installed or published. Both pre-existing 3.3.3 release APKs keep
  their recorded hashes and sizes. Kernel and userspace are outside this task.
- No on-device visual or frame-timing verification was performed; cached rendering
  and geometry tests do not establish a measured FPS or device compatibility result.
- Evidence: `out/rain-refinement-verified-20261025.log` and
  `out/rain-refinement-verification-20261025.json`. Scoped source backups:
  `out/backup-before-rain-refinement-20261025/`.


## Scope and plan

2026-10-25 storm-glass reference revision (source only; no APK or device install):

1. [x] Strengthen the layered blue-gray sky and depth-separated silver rain,
   preserving daylight, wallpaper precedence and the existing shared scene clock.
2. [x] Rework wet-glass card interiors, rims and bounded chrome ornaments;
   connect visible splashes and runoff to actual card and sidebar geometry.
3. [x] Check disabled/reduced-motion behavior, compact geometry and day/night
   contrast; compile Kotlin and run Manager unit tests without packaging an APK.

2026-10-25 immersive-rain revision (source only; no APK or device install):

1. [x] Replace hard double/dashed outlines and repetitive dots with restrained
   wet-glass rims, soft corner highlights and rain-washed interior artwork.
2. [x] Add a touch-transparent foreground rain layer, using the existing scene
   clock and bounded geometry to link rain impacts to cards and sidebars.
3. [x] Add optional, infrequent thunder accents with no full-screen white flash;
   honor reduced motion, lifecycle pause, wallpaper settings and day/night colors.
4. [x] Verify configuration migration, geometry and rendering integration, then
   compile Kotlin and run unit tests without assembling an APK.

2026-10-25 revision:

1. [x] Remove automatic battery saving and the dedicated preview panel/dialog.
2. [x] Add independent card, top bar, navigation and sidebar edge ornaments.
   Keep the three layouts, wallpaper precedence and foreground opacity intact.
3. [x] Run a clean release build and unit tests; verify the signed APK and hash.
   Deliver the APK without installing or publishing it.

Screenshot follow-up:

1. [x] Fix the missing Miuix home-header hook and forward actual card shapes.
2. [x] Add independent complete card frames and internal artwork, with stronger
   day/night edge ornaments for top bars and navigation.
3. [x] Verify profiles, rendering contracts, compilation, unit tests and the
   updated release APK. Do not install the APK on a device.

## Configuration

`ui_mode=rain` remains the stored interface selection. The live page family is
resolved using `rain_v2_layout`; child pages receive that page family through
`LocalInterfaceStyle`. Rain styling is independently enabled by
`LocalRainSettings`, so the three layouts share business code, not copied pages.

`rain_v2_<layout>_*` stores each layout's scene, day/night color, motion quality,
density, speed, wind and surface opacity. A missing scoped scene reads the legacy
`rain_style` setting. Invalid preference types and non-finite numeric values use
safe defaults. Reset affects only the current rain profile.

Enhanced Miuix uses its existing component rail and sidebar editor. Its widgets,
navigation order, icons and home-card overrides remain shared with enhanced Miuix.
Plain Miuix can use a navigation rail. In small/short windows the existing bounded,
scrollable navigation fallback takes priority. Bottom and side navigation are not
drawn together.

## Rendering and accessibility

- A background Canvas draws far rain. `RainUiLayer` paints near rain after the
  existing page content with a draw modifier, adding no touch handlers or semantic
  nodes. It also surrounds the sidebar and navigation in all three page layouts.
  Far/near layers partition the same deterministic field, rather than drawing
  duplicate rain. The content itself is never made transparent.
- The scene clock pauses outside the resumed lifecycle and honors system animation
  settings. Static mode still paints a complete background.
- Surface opacity changes paint only, never an ancestor containing text, pictures,
  icons or switches. Foreground rain has its own strength control and can be
  disabled. Semantic error colors stay intact.
- Global and per-page wallpapers retain their existing rendering and selection
  precedence. Explicit UI passthrough settings override rain surface opacity.
- Rain over wallpaper is opt-in per layout. The rain-only canvas is painted after
  wallpaper media. Far rain stays inside the sidebar backdrop capture; near rain
  and impacts stay outside it, avoiding capture feedback. The wallpaper-rain
  switch gates both layers. Rain sound with wallpaper is a separate opt-in option.
- Line, needle and bead silhouettes use both the scene length and stroke width.
  Wet/dripping decoration also applies to shared Material tonal cards without
  replacing warning or error colors. Optional distant thunder uses one smooth
  local glow and a small bolt in the thunderstorm scene. It never paints a
  full-screen white flash and is absent with motion/system animations disabled.
- There is no automatic battery-saving policy. Manual rain quality and motion
  settings remain available; lifecycle and system animation preferences still
  pause the shared animation clocks when appropriate.
- Settings use flow-wrapped choices, scrollable content, 48dp control targets and
  existing compact headers/navigation. English and Simplified Chinese strings are
  included, together with Traditional Chinese; other locales use the normal
  English fallback.

## Weather and audio

Weather following reuses the sidebar weather repository and its existing consent,
cache identity and request throttle. It never asks for location or grants weather
access. Disabled/unapproved providers, permission errors, missing or mismatched
cache, future timestamps and expired readings fall back to the time of day. The
maximum cache age is twice the configured refresh interval, with a one-hour floor.
Unsupported conditions also use the clock. The design screen shows the source or
fallback reason and provides a throttled refresh action and weather settings link.
The resolved scene reaches the live palette and canvas.

Rain audio uses the existing runtime audio coordinator for audio focus, silent
mode, Do Not Disturb and headset disconnection. Playback belongs to the resumed
activity and is released on pause/disposal; sound setting changes are observed.
PCM synthesis runs off the main thread. Volume updates reuse the current track
and cannot bypass a pause caused by the system.

## Decorations and presets

Cards, top bars, navigation bars (including floating/compact bars and rails), and
component sidebars have independent Off, Wet, Dripping, Ripples and Rain Streaks
choices, with a shared ornament-strength slider. Cards also have independent None,
Soft Glass Rim, Crystal Reflection and Rain-polished Rim frames, plus None, Soft
Mist, Flowing Rain and Ripples interiors. The stored `double` and `dashed` enum
values remain compatible, but now paint soft highlights and asymmetric short rain
traces instead of hard nested/dashed boxes. Frames follow the actual card shape. Interior
artwork is painted below content, including inside Material card containers;
wallpaper backgrounds retain precedence. Chrome ornaments use bounded edge strips
and day/night-adaptive ink. The custom Miuix home header uses the same top-bar hook.
No ornament applies alpha to text, images, icons or controls,
replace semantic card colors, create click targets, or change measured sizes.

The storm-glass follow-up uses blue-gray cloud banks and refracted silver streaks,
inspired by the supplied weather UI reference. Nine cloud gradients are cached by
viewport/palette; they drift gently on the existing scene clock. Wallpaper overlay
mode never paints the sky or clouds over the user's image. The near/far field is
still partitioned rather than duplicated, with stronger length/width/alpha depth
cues and an 80-drop budget for built-in scenes. Default light rain now supplies
32 drops (11 near drops), rather than 11 drops with only four crossing the UI.
The intensity slider stays monotonic and zero still removes every drop. Explicit
rain opacity and foreground opacity remain in control, including imported profiles.

Card interiors have an asymmetric glass reflection and edge condensation below
content. Shape-aware refracted rims and paired light/dark water highlights stay
visible in both palettes. Top, navigation and sidebar strips retain the bounded
19-percent cross-axis limit, use responsive bead counts, and keep all artwork
inside their paint bounds. Sidebar runoff follows gravity down its actual edge.
Near rain uses the same trajectory for impact rings, spray and trailing beads;
impacts last 900 ms and no longer multiply two default opacity settings into an
unreadably faint result; the lower of the two settings limits impact opacity.
The sidebar divider switch also gates the continuous ornamental rim, while
retaining individual condensation beads. Top-bar collision visibility checks its exposed bottom
edge. The wrap margin includes the longest near streak, preventing a visible tail
from disappearing when the scene wraps. No new preference keys or resources are
needed, and the existing three layout profiles and Off options remain compatible.

Colors follow the active day/night rain palette. Rain, surface ornaments and
impacts now share one lifecycle-aware scene clock; the separate decoration clock
has been removed. Disabling rain/system motion retains still surface artwork
without animated impacts or thunder. The independent profiles and theme export include the
new choices. Old theme profiles use safe defaults and accept the retired battery
saving field only for import compatibility; that field is ignored and not exported.
There is no dedicated preview, light/dark preview selector or expanded preview dialog.

`rain_through_ui`, `foreground_opacity`, `surface_interaction` and `thunder` are
per-layout settings and participate in presets, sharing and profile copy. Old
profiles get the new defaults. Corrupt preferences fall back safely; malformed
shared values are rejected.

Surface interaction uses live layout coordinates, not assumed screen offsets.
Only attached, visible surfaces from the same root can be hit; dialog roots,
clipped-off card tops and disposed lazy items are excluded. The registry has at
most 128 entries and samples at most 64 visible targets per draw. Each near drop
paints at most one recent impact, following the actual wind trajectory. Sidebars
respect their configured side; still air falls onto the top instead. No card owns
an animation coroutine. The clock updates drawing, not page composition.

Up to 12 named local presets live in `rain_v2_presets`. Applying one preserves the
selected layout. Saving the same name replaces that preset. Layout copying asks
before replacing the target profile, never switches the selected layout and does
not modify shared sidebar widgets or wallpapers. Presets can be deleted; reset
only affects the current layout. Saved presets are local, while theme sharing
continues to export the selected layout and its three profiles.

## Theme sharing

The existing theme export includes a bounded `rain` object with the selected layout
and all three profiles. Import validates exact field names, enum values, booleans,
color integers and numeric ranges. It cannot write arbitrary preference keys.
No downloaded style package is required. Legacy rain shares still accept their
scene selection; legacy archives retain signature/hash verification.

## Verification boundary

Storm-glass reference revision verification on 2026-10-25:

- `:app:compileDebugKotlin :app:testDebugUnitTest` passed on the final sources.
- 1167 tests total: 1166 passed and one opt-in live-network test skipped.
  All 74 rain tests passed. Added checks cover default rain visibility, intensity
  limits, wrap margins, cloud geometry in compact/tablet viewports, stacked-cloud
  text contrast, exposed top-bar edges and splash lifetime/opacity limits.
- No Manager APK was packaged and no APK was installed. The existing 3.3.3 release
  SHA-256 is `656559FBB582E40687A95887F2C59E50960034650D8C16C8B50CCB01C446C15F`.
- No on-device visual or frame-timing verification was performed. Unit contrast
  checks sample the cloud gradient geometry; they are not device screenshots.
- Evidence: `out/rain-storm-glass-verified.log` and
  `out/rain-storm-glass-verification-20261025.json`. Pre-edit Rain sources are in
  `out/backup-before-rain-weather-20261025/`.

Immersive-rain source revision verification on Windows / PowerShell / Java 21:

- `:app:compileDebugKotlin :app:testDebugUnitTest` succeeded on the final sources.
- 1149 unit tests total: 1148 passed, one opt-in live-network check skipped.
  All 65 rain tests passed, including wind-linked impacts, clock wraparound,
  left/right sidebars, clipped surfaces, thunder envelopes and profile migration.
- All four translated decoration-resource files contain the same 23 unique keys.
- No Manager APK was assembled. The existing release APK retained SHA-256
  `B6503052E452290B19C1705BC911ED3CF82CF9787AAEEA65F6F64D2FE83B6BAF`.
- No device installation or runtime visual/frame-timing verification was performed.
- Logs: `out/rain-immersive-source-check-final-20261025.log` and
  `out/rain-immersive-source-verification-20261025.json`.

Automated checks cover layout resolution, profile isolation, corrupt preferences,
scene bounds, palette contrast, opacity/semantic colors, offline import and legacy
migration. Source contract tests also guard draw ordering, touch transparency and
lifecycle behavior. These are not substitutes for on-device visual checks of
font scaling, window resizing, vendor rendering or frame timing. No device install
is authorized by this task.

Screenshot follow-up verification on Windows / PowerShell / Java 21:

- Clean release build and final incremental release build both succeeded.
- Unit tests: 1137 total, 1136 passed, one opt-in live-network check skipped.
  All 53 rain tests passed, including frame/interior persistence, legacy import,
  profile isolation, day/night frame contrast and the missing-header regression.
- APK: `out/SterSU_3.3.2_33200-rain-card-artwork-release-20261025.apk`,
  18,637,201 bytes. APK Signature Scheme v2 verification passed using the existing
  Android Debug certificate. The copied artifact matches the build output hash.
- SHA-256: `B6503052E452290B19C1705BC911ED3CF82CF9787AAEEA65F6F64D2FE83B6BAF`.
- No device installation, runtime visual verification or publication was performed.
