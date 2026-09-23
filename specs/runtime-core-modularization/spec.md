# Runtime Core Modularization

## Context

`MyHyperModifier` is the LSPosed module entry point and has grown to include package routing,
settings lifecycle management, SystemUI hooks, lockscreen hooks, plugin hooks, and media
constraint transformations. At 2352 lines it obscures feature ownership and makes hook changes
risky to review.

## Goals

- Keep `MyHyperModifier` as the sole LSPosed entry point and package router.
- Move independent hook domains into package-private runtime collaborators.
- Preserve hook timing, target-package decisions, class names, constants, and settings refresh
  behavior.
- Keep every resulting Java core file below 1000 lines.

## Non-goals

- Change visual behavior, target packages, resource overrides, or preference schema.
- Convert the runtime implementation from Java to Kotlin.
- Modify the existing HyperGlassify feature modules.

## Acceptance criteria

1. `MyHyperModifier.java` only coordinates lifecycle and delegates to domain installers.
2. Lockscreen, SystemUI, plugin, and media-constraint logic each have explicit owners.
3. No core Java source file is over 1000 effective lines.
4. The Android debug compilation succeeds after the refactor.

## Compatibility and rollout

This is an internal, behavior-preserving refactor. Class hooks still install through the same
`XposedModule` instance so LSPosed callback ownership and de-duplication semantics remain intact.
The disabled password-background experiment remains disabled.

## Implemented result

The entry point now delegates to `SystemUiRuntimeHooks`, `LockscreenHooks`, and `PluginHooks`.
`RuntimeRefreshRegistry` retains weak-reference refresh state, while
`MediaConstraintCustomizer` owns media layout transformation. The original hook IDs, hook timing,
and target-package routing remain at their pre-refactor call sites.
