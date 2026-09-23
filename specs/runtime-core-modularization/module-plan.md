# Module Plan

| Module | Responsibility | Public collaboration surface |
| --- | --- | --- |
| `MyHyperModifier` | LSPosed entry point, package filtering, settings lifecycle | Calls domain installers with itself and a class loader |
| `SystemUiRuntimeHooks` | SystemUI loaded-class hooks, heads-up glass, global/MiLink blur, Control Center refresh state | `install*`, `onLoadedClass` |
| `LockscreenHooks` | PIN/password/fingerprint/notification hook installation and glass application | `install*` |
| `PluginHooks` | SystemUI plugin loader, corner/drawable/card hooks | `install*`, `onLoadedClass` |
| `MediaConstraintCustomizer` | ConstraintSet and seekbar transformations | Static transformation helpers used by SystemUI hooks |

The Android runtime follows `runtime/<domain>` ownership rather than a UI feature layer because
these classes run inside foreign processes. Shared settings remain in `ModuleSettings`, reflection
helpers remain in `ReflectiveAccess`, and resource values remain in `ResourceOverrides`.

## Dependency direction

`MyHyperModifier -> domain hook installers -> ModuleSettings / ReflectiveAccess / ResourceOverrides`

Domain installers do not depend on each other, except `SystemUiRuntimeHooks` invokes
`MediaConstraintCustomizer` for media-specific transformations.
