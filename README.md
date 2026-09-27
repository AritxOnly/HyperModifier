# HyperModifier

The source ownership and dependency rules are documented in
[ARCHITECTURE.md](ARCHITECTURE.md).

API 102 (`io.github.libxposed:api:102.0.0`) LSPosed module for HyperOS SystemUI, MiLink, Xiaomi Health, Xiaomi Market, Mi Home, Amap, Xiaomi Community, and official Bilibili.

It is scoped to `com.android.systemui`, `miui.systemui.plugin`, `com.milink.service`, `com.mi.health`, `com.xiaomi.market`, `com.xiaomi.smarthome`, `com.autonavi.minimap`, `com.xiaomi.vipaccount`, and `tv.danmaku.bili`. The implementation was
matched against the decoded `reference/MiuiSystemUI.apk`,
`reference/MiuiSystemUI.bak.apk`, `reference/MIUISystemUIPlugin.apk`, Xiaomi Health 3.59.1 APK,
Xiaomi Market 4.125.11 APK, `reference/米家_11.8.605.APK`, Amap 17.00.0.2005,
Xiaomi Community 6.6.9, and Bilibili 9.13.0 (`reference/哔哩哔哩.apk`).

## Included changes

- Notification and Control Center corner radii: `28dp`.
- Notification/Control Center card glass parameters use one common editor in both light and dark modes, reusing the
  heads-up sliders, numeric dialog, and JSON flow but with separate settings and preset formats.
  The hook follows HyperChanger's native `View.setMiGlass(float[])` interception approach;
  each edited slot adds an offset to the original recipe, preserving per-card state differences.
  It is off by default and does not force glass onto non-glass themes. See
  [SHADE_CARD_GLASS.md](docs/investigations/SHADE_CARD_GLASS.md) for scope, reference defaults, and validation limits.
  Global material blur scales the native Glass sampling radii (`0–200%`), while an optional Glass blur radius
  supplies a common base (`0–100px`, including zero) before scaling. Like HyperChanger, the radius
  hook includes the shade BlurProvider's native sampling buffers, not just card Views. Recipe
  edits remain card-only. No child-radius injection or new capture layer is added; ordinary
  background blur remains under the global background setting. Shared Glass buffers mean this
  is not an independent blur layer per card. Descriptive labels follow HyperChanger's
  verified slot names; unpublished slots are explicitly marked rather than given speculative meanings.
- “全局材质模糊” is a standalone second-level page, with links from notification/Control Center,
  heads-up, and their material editors. Shared Glass radius/proportion (including heads-up sampling)
  is independent of per-surface recipe switches; panel/MiLink background blur and dim share this page.
  Existing blur values are preserved. An absent global enable key inherits the old card recipe switch.
  Recipe presets no longer reset shared blur. Card JSON v3 exports the recipe only; v1/v2 still import
  their recipes without applying legacy blur fields. The separate heads-up radius hook is removed.
- MiLink Fusion Device Center card radius: `20dp`.
- “全局背景材质” offers a shared `0–100%` background dim slider for the notification shade,
  Control Center, and MiLink Fusion Device Center. Custom dim is off by default (editable value
  `20%`). It replaces native blend colors with black SRC_OVER on the existing background surface,
  preserves expansion animation, and does not add a blur layer or change foreground text/cards.
  Turn it off to restore native blending, then reopen the affected panel. MiLink requires the
  native MIUI View blur/blend path; the SurfaceControl-only fallback is not tinted by this hook.
- Media card heights: expanded `152dp`, collapsed `120dp`, full AOD `80dp`.
- Dynamic Island media height: `160dp`, scoped to the media island without changing other
  Super Island cards.
- Both media ConstraintSets receive the edited XML constraints, and the normal media seek bar is
  configured with the Island seek bar attributes.
- `onFullAodStateChanged(true)` forces `action0` through `action4` to `GONE`.
- Lock-screen notifications can independently reserve space for the under-display fingerprint
  sensor. The fingerprint icon can be hidden on the interactive lock screen while remaining
  visible on AOD.
- Status-bar network-generation text (such as 5G) can be restored and adjusted for size, weight,
  and horizontal offset.
- The “小横条” page configures the gesture hint per app: show, hide, or reveal for 3 seconds
  after the foreground Activity/task changes, with 220 ms opacity fades on visibility changes.
  The module preset hides the hint for declared or configured scope apps, system apps and
  `com.aritxonly.*`; other apps default to immersive.
  The system preset restores the system default for every app. Individual overrides take priority,
  and switching presets clears them. Newly installed apps inherit the selected preset.
  The picker includes system apps with enabled, visible Activity windows and supports name/package
  search with the MIUIX SearchBar in a single grouped card. Gesture-handle presets can be imported by pasting JSON or
  selecting a file; see [gesture-handle-preset.example.json](docs/examples/gesture-handle-preset.example.json). Set `preset` to `module` or `system`,
  and map package names in `apps` to `show`, `hide`, `immersive`, or `system`. Import replaces
  only gesture-handle settings; omitted apps inherit the selected preset.
  The “选择预设” dialog also exports the current preset and overrides to the clipboard or a JSON file.
  Hooks run only in SystemUI; hiding the hint preserves gesture handling and navigation
  insets. Configure the SystemUI scope and restart it once after installing the updated module.
- Xiaomi Health's four-tab native navigation can be replaced with the module's Compose soft-glass
  floating bar while keeping the app's own selected/unselected icons, tab selection, and fragment
  routing. Its page content extends behind a transparent system navigation bar, while the floating
  bar respects gesture and three-button navigation insets. Its settings can opt back into the
  module's MIUIX icon set or tint native icons with the bar's monochrome foreground color. The
  injected content safe area keeps the last page items above the floating bar.
- Xiaomi Market's server-driven native tabs can use the same soft-glass floating bar. Labels,
  selected/unselected icons, selected state, and optional badges are mirrored from the live native
  `TabView` list, and clicks continue through Market's own routing and analytics. Basic mode and
  pages that hide the native tab bar are left untouched. MIUIX icons, native monochrome tint, and
  bottom-tab badges can be toggled independently.
- Mi Home's delayed `TabPageIndicatorNew` is adapted to the same floating bar. It preserves native
  click routing and reads labels and badges while a dedicated live-view adapter snapshots either
  the ImageView or hosts a cloned Lottie renderer selected by each native tab. Startup and page
  transitions use bounded backdrop-sampling bursts, active scrolling drives frame-rate sampling,
  and vertical list containers receive reversible end padding so only the scrolled-to-bottom
  content clears the floating bar.
- Amap 17.00.0.2005 has an experimental adapter for its native `LiteTabBar`. It retains the app's
  own tab click and analytics path, can use Amap's official selected/unselected icon resources,
  samples the map's hardware-rendered `SurfaceView` with bounded `PixelCopy` bursts, and can hide
  the dynamically configured “长按说话” tab without hiding the ordinary message tab.
- Xiaomi Community 6.6.9 mirrors `BottomNavView`/`NavItemView`, including native icons and unread
  badges, while retaining the original ViewPager routing and reselection chain. The fixed 56dp
  navigation reservation is removed, vertical lists receive end-only safe padding, and transparent
  padding is trimmed from app-supplied icon canvases before rendering.

- Official Bilibili 9.13.0 uses Deadliner's soft-glass tab capsule with an independent “+”
  action. Home (`Home`, 0.95 scale), Dynamics (`Messages`), Following (`Album`), Mall (`Carrier`), and Mine
  (`ContactsCircle`) use MIUIX icons; each destination, the publish button, and badges can be
  configured in “哔哩哔哩”. At least one navigation destination stays available. Native indices,
  page routing, reselection and publish touch callbacks remain authoritative. While replacement
  is enabled, the original dock stays hidden and rejects physical touches, including while the
  floating panel is temporarily covered; disabling replacement restores it. Enable the
  `tv.danmaku.bili` LSPosed scope and restart Bilibili after changing settings. Home extends
  beneath the transparent system navigation bar; only the official bottom inset/background strip
  is removed, retaining status and side insets. Splash ads, startup covers, loss of window focus,
  backgrounding and the keyboard hide the overlay and suspend sampling. Input interception is
  scoped to the app's tab/publish callbacks. Native state/tree work is capped at 100 ms; the
  backdrop uses PixelCopy at a minimum 64 ms interval, with no synchronous View.draw fallback.

API 102 no longer supports legacy resource replacement. `Resources#getDimension*` and
`Resources#getInteger` are therefore hooked by resource name, which covers the base, xxhdpi, and
xxxhdpi `dimens.xml` variants selected on-device.

## Hook module layout

The `MyHyperModifier` entry class is the single Xposed entry point and owns only package routing and hook
registration. Runtime responsibilities are separated so changes in one surface do not alter the
secondary-panel hook path:

- `ModuleSettings`: process-local, lazily loaded snapshot of companion-app options.
- `ResourceOverrides`: resource-name based dimensions and typed-array fallbacks.
- `ControlCenterAppearance`: surface-to-radius routing and XML drawable patching.
- `ReflectiveAccess`: defensive compatibility operations for changing HyperOS internals.
- `XiaomiHealthHooks`: version-tolerant `MainActivity` lifecycle and touch bridge.
- `XiaomiHealthFloatingNavigation`: Compose overlay, native-tab synchronization, and sampled
  soft-glass backdrop.
- `MarketHooks` / `MarketFloatingNavigation`: lifecycle bridge and dynamic native-tab adapter for
  Xiaomi Market.
- `MiHomeHooks` / `MiHomeFloatingNavigation`: lifecycle bridge and native resource-backed adapter
  for Mi Home 11.8.605.
- `AmapHooks` / `AmapFloatingNavigation`: experimental `LiteTabBar` bridge and hardware-map
  backdrop adapter for Amap 17.00.0.2005.
- `NativeViewBottomBarNavigation`: shared classic-View tab mirror and content-inset adapter used by
  Xiaomi Community and Bilibili.
- `XiaomiCommunityHooks` / `XiaomiCommunityFloatingNavigation`: Xiaomi Community 6.6.9 adapter.
- `BilibiliHooks` / `BilibiliFloatingNavigation`: official 9.13.0 `TabHost` adapter and touch-only
  `HomeTabPublishView` action. Hyper-PiliPlus icon mapping follows local revision `58e5edc67`;
  the detached emphasis button follows Deadliner revision `4d1b755`.

The secondary brightness hook changes only its outer `setOutlineRadius`. Its progress layer keeps
MIUI's small native clip radius, leaving the fill edge flat while the parent outline rounds the
bottom. Secondary volume is replaced only through `VolumeColumnRes` when it belongs to Control
Center, so the regular system volume dialog remains stock. Settings-provider IPC runs on a
dedicated worker with bounded retry backoff, keeping SystemUI's boot and resource-resolution
threads non-blocking.

## Acknowledgements

The status-bar network-type adaptation and related HyperOS research reference
HyperBlackScreen by Coolapk user **@不愧是小睦**.

## Build and install

```sh
./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, enable **HyperModifier** for the desired LSPosed
scopes, then restart those processes (or reboot). The debug APK is signed and suitable for local installation.
