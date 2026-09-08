# MyHyperModifier

API 102 (`io.github.libxposed:api:102.0.0`) LSPosed module for HyperOS SystemUI and MiLink.

It is scoped to `com.android.systemui`, `miui.systemui.plugin`, and `com.milink.service`. The implementation was
matched against the decoded `reference/MiuiSystemUI.apk`,
`reference/MiuiSystemUI.bak.apk`, and `reference/MIUISystemUIPlugin.apk`.

## Included changes

- Notification and Control Center corner radii: `28dp`.
- MiLink Fusion Device Center card radius: `20dp`.
- Media card heights: expanded `152dp`, collapsed `120dp`, full AOD `80dp`.
- Dynamic Island expanded-media integer height: `160dp`.
- Both media ConstraintSets receive the edited XML constraints, and the normal media seek bar is
  configured with the Island seek bar attributes.
- `onFullAodStateChanged(true)` forces `action0` through `action4` to `GONE`.

API 102 no longer supports legacy resource replacement. `Resources#getDimension*` and
`Resources#getInteger` are therefore hooked by resource name, which covers the base, xxhdpi, and
xxxhdpi `dimens.xml` variants selected on-device.

## Hook module layout

`MyHyperModifier` is the single Xposed entry point and owns only package routing and hook
registration. Runtime responsibilities are separated so changes in one surface do not alter the
secondary-panel hook path:

- `ModuleSettings`: process-local, lazily loaded snapshot of companion-app options.
- `ResourceOverrides`: resource-name based dimensions and typed-array fallbacks.
- `ControlCenterAppearance`: surface-to-radius routing and XML drawable patching.
- `ReflectiveAccess`: defensive compatibility operations for changing HyperOS internals.

The secondary brightness hook changes only its outer `setOutlineRadius`. Its progress layer keeps
MIUI's small native clip radius, leaving the fill edge flat while the parent outline rounds the
bottom. Secondary volume is replaced only through `VolumeColumnRes` when it belongs to Control
Center, so the regular system volume dialog remains stock. Settings-provider IPC runs on a
dedicated worker with bounded retry backoff, keeping SystemUI's boot and resource-resolution
threads non-blocking.

## Build and install

```sh
./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, enable **MyHyperModifier** in LSPosed, then
restart SystemUI (or reboot). The debug APK is signed and suitable for local installation.
