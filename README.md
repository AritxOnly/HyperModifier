# HyperModifier

The source ownership and dependency rules are documented in
[ARCHITECTURE.md](ARCHITECTURE.md).

API 102 (`io.github.libxposed:api:102.0.0`) LSPosed module for HyperOS SystemUI, MiLink, Xiaomi Health, Xiaomi Market, Mi Home, Amap, and Xiaomi Community.

It is scoped to `com.android.systemui`, `miui.systemui.plugin`, `com.milink.service`, `com.mi.health`, `com.xiaomi.market`, `com.xiaomi.smarthome`, `com.autonavi.minimap`, and `com.xiaomi.vipaccount`. The implementation was
matched against the decoded `reference/MiuiSystemUI.apk`,
`reference/MiuiSystemUI.bak.apk`, `reference/MIUISystemUIPlugin.apk`, Xiaomi Health 3.59.1 APK,
Xiaomi Market 4.125.11 APK, `reference/米家_11.8.605.APK`, Amap 17.00.0.2005,
and Xiaomi Community 6.6.9.

## Included changes

- Notification and Control Center corner radii: `28dp`.
- MiLink Fusion Device Center card radius: `20dp`.
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
  Xiaomi Community.
- `XiaomiCommunityHooks` / `XiaomiCommunityFloatingNavigation`: Xiaomi Community 6.6.9 adapter.

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
