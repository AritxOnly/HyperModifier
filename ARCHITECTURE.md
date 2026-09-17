# Source architecture

The Android module is organized by ownership rather than by file type. Runtime hook code and the
companion settings application share one APK, but they have different lifecycle and dependency
rules.

## Layers

```text
app/src/main/java/com/aritxonly/
├── deadliner/                         Reusable soft-glass material and navigation primitives
└── myhypermodifier/
    ├── app/                           Android application and Activity entry points
    ├── settings/                      Persisted settings and LSPosed connection state
    ├── ui/
    │   ├── components/                Settings UI building blocks and previews
    │   └── theme/                     Companion-app color mapping
    └── runtime/
        ├── systemui/                  HyperOS SystemUI-specific hooks
        └── hyperglassify/
            ├── common/                Shared insets, early suppression and icon adapters
            ├── xiaomihealth/           Xiaomi Health adapter
            ├── market/                 Xiaomi Market adapter
            ├── mihome/                 Mi Home adapter
            ├── amap/                   Amap LiteTabBar / SurfaceView adapter
            ├── community/              Xiaomi Community BottomNavView adapter
            └── spotify/                Dormant Spotify experiment (not shipped in 1.3.8 scope)
```

## Dependency direction

1. `deadliner` owns platform-neutral glass rendering and the floating navigation component. It
   must not depend on module settings or any hooked application.
2. `settings` owns saved configuration and framework connection state. It must not depend on the
   settings UI or a target-app adapter.
3. `ui` reads and writes `settings`, and may use `deadliner` components. It must not install or
   call runtime hooks.
4. `runtime` owns the Xposed entry point and process-local settings snapshot. It may read settings
   through the provider contract but must not depend on Activity UI state.
5. Every `runtime/hyperglassify/<target>` adapter may depend on `common` and `deadliner`, but must
   not depend on another target adapter. App-version-specific reflection and resource names stay
   inside that target directory.

The Java/Kotlin package remains `com.aritxonly.myhypermodifier` for now. This is intentional: the
Xposed entry class, manifest component names, package-private Java helpers, and existing reflection
paths are binary contracts. The directory layers establish ownership without changing those
contracts in a behavior-preserving refactor. A future namespace migration should be a dedicated
change with explicit manifest, keep-rule, Xposed-entry, and upgrade testing.

## Change placement

- New target-app bottom navigation: add a directory below `runtime/hyperglassify/` containing only
  that app's lifecycle bridge and adapter.
- Sampling, inset, or shared native-icon behavior used by two or more adapters: place it in
  `runtime/hyperglassify/common/`.
- Reusable material geometry or rendering: place it under `deadliner/ui/material/glass/` or
  `deadliner/ui/navigation/`; never copy the shader recipe into an adapter.
- A companion-app preference or page: persist the value in `settings/` and render it in `ui/`.
- A SystemUI-only modification: place it in `runtime/systemui/` and register it from the runtime
  entry point.

## Validation boundary

Pure source moves must pass both `:app:assembleDebug` and `:app:assembleRelease`. Target adapter
changes additionally require an on-device check against the APK version named in its source and
settings compatibility note; compilation alone cannot validate target view hierarchies, blur
sampling, navigation routing, or media-session behavior.
