# QQ foldable split and native bottom safe area

## Scope

QQ 9.3.75 / 16580 only. Two separately persisted, default-off settings are included in remote snapshots, backup/import and QQ scope reporting:

- `qq_fold_split_ratio_enabled`: native flat foldable layout becomes left 0.4, right 0.6.
- `qq_fold_navigation_immersion_enabled`: enable QQ's native common-scene bottom safe-area treatment on foldables. UI name: 折叠屏底部安全区沉浸.

Neither needs floating navigation. No process scope is added. Native device identity, fold decision, multi-window eligibility and orientation decision remain authoritative. The module never substitutes a tablet identity, forces landscape, or hooks a global orientation setter.

## Evidence and corrected assumptions

On 2026-11-06 the first ineffective implementation read pad.e's static ratio fields as 0.3/0.7 while the visible FragmentContainerViews were equal: left r4e (0x7f0a27a2) x=0..1182; chat r4d (0x7f0a27a1) and function r4b (0x7f0a279f) x=1182..2364. Those fields did not describe the live layout. The original static-field guard was removed.

The next APK was installed by the user at 14:21. QQ-specific logs confirmed actual SplashActivity binding through DelegateLastClassLoader, adapter installation, and a later successful weight write with native getters 0.5/0.5. The user subsequently reported:

- Inner-screen cold startup initially stayed 1:1; background/fold transitions eventually produced 4:6.
- Starting on the cover screen and unfolding with QQ foreground could keep the inner screen in portrait; background unfolding behaved differently.
- The transparent Window treatment removed Tab elevation/clearance and left a fully transparent region under the chat input. The requested behavior is native clearance with the original blur/background extended underneath.

These reports establish incomplete timing and incorrect immersion semantics. Logs saying a hook installed or weights were written are not proof of complete visual correctness. The user then reported Hook detection and requested offline work. All device operations stopped at that point; the following revision has not been installed or tested on that phone. No detection hiding/bypass is implemented or promised.

## Startup and split timing

QQRuntimeHooks reads only QQ/master early values from the existing framework preference snapshot, arms main-thread binding immediately, and schedules the complete snapshot on the existing settings worker. This avoids waiting for ensureLoaded() to discover an Application.

A native Application.ActivityLifecycleCallbacks callback binds the actual SplashActivity loader in onActivityPreCreated, before native onCreate. The split preparation resolves metadata and installs hooks without reading manager static fields or invoking state getters at this stage. onActivityPostCreated performs the initial apply and unregisters the startup callback. Settings-time discovery uses cached ActivityThread metadata, not a QQ static singleton.

Five split methods are hooked: public fullscreen transitions b/c, native configuration/layout update d(Configuration, Activity, boolean), and ratio getters h/g. Three exact SplashActivity hooks cover focus, configuration and destruction. Adding d handles QQ's actual startup/fold layout write rather than waiting for focus to return. The same weight ownership object survives native resets, preserving its original restoration values.

Application is limited to native STATE_FLAT + FOLD and three observed native equal weights. Both right containers receive 0.6. Unknown versions/layouts fail closed; partial hook installation rolls back. Getter callbacks preserve the native result and use cached/preboxed replacements only while owned weights are active. AIO's native width cache is invalidated under its own monitor only at active-state transitions. Its infrequent i(boolean) cache recomputation is deoptimized once.

## Native orientation replay after Xiaomi posture updates

Static QQ code shows pad.c.c(Activity) requesting portrait when its native fold check cannot yet show a split, and unspecified orientation otherwise. Xiaomi's native check reads Settings.Global.device_posture through pad.j.a(). This supports a possible ordering race; it does not prove the foreground portrait issue was caused by the module or that the new treatment fixes it.

Only with the split opt-in enabled on Xiaomi, QQFoldPostureRefresh subscribes to that one setting. Notifications merge into one queued main task, which calls QQ's own orientation initialization and native configuration method using the current Activity configuration. Initial attachment replays orientation only, preserving an existing native fullscreen page. There is no forced orientation, synthetic posture, polling, timer or display-setting mutation. The subscription and queued work are removed on disable/destruction. Activity and resolver ownership are weak. A failed registration is not retried on every focus event.

## Native immersion instead of transparent Window flags

The previous QQGestureWindowState implementation was removed. It set LAYOUT_HIDE_NAVIGATION, transparent colors and contrast=false without registering QQ's companion view adjustments; this was insufficient for safe-area elevation and background extension.

In this exact QQ APK, QUINavigationBarImmersiveHelper.isOpen(Context, scene) treats missing scenes as mode 1. Mode 1 excludes split-capable devices; mode 2 allows them; mode 0 remains disabled. QQGestureImmersionHooks uses QQ's already defined lazy scene configuration and changes only common mode 1 to mode 2 (or appends common=2 when missing). An explicit cloud-off mode 0, native mode 2 and all other scenes remain untouched. The version-checked SynchronizedLazyImpl._value volatile reference publishes a copied ArrayList; the existing list and final helper field b are not modified. Native iterators can finish reading the previous snapshot. Restoration uses object identity and preserves another owner's scene/snapshot replacement, including edits to unrelated scenes. No new isOpen/Window/View/render hook is installed, and no native global split predicate is changed.

QQ's native RFWNavigationBarImmersiveHelper then owns navigation insets and its registered view adjustments. Static call sites register mTabBlurView for height extension and mTabWidget for height + bottom padding; AIO's native bottom blur consumes navigation height. Existing native feature/cloud decisions for blur remain intact; this adapter does not force a new blur renderer or a separate AIO experiment.

Changing the setting on an existing host queues a single Activity.recreate(), so QQ can rebuild/register the native inset consumers and undo them on disable. This is visible and potentially costly once per relevant toggle, and is described in settings. Cold startup applies the scene before native creation and needs no recreation. Ordinary focus/configuration events do not mutate scenes or rebuild the Activity. Immersion-only does not install the three split lifecycle hooks. Scene references contain QQ's initialized Lazy and string/integer configuration data, not Activity/View/Context.

## Performance review

- Split enabled: 5 native methods + 3 exact host hooks. Immersion adds no method hooks.
- No generic Activity/View/Window setter, draw, touch or animation hook; no new process, service, broadcast, dedicated worker, screenshot layer or blur renderer.
- Reflection metadata is resolved once; getter paths do no reflection, I/O, locking or per-call allocation.
- Only relevant settings schedule coalesced work. Native scene mutation and optional recreation occur at startup/toggle, not per frame.
- Xiaomi posture subscription uses one registration IPC, notification delivery and native reads only around posture changes. It has no recurring work. Lifecycle/configuration hooks do bounded view/metadata work; only changed weights request layout.
- Existing native fullscreen/other-module weight values and another owner's scene replacements are preserved on restoration.

These are structural/code-review results. There is no before/after Perfetto, FrameTimeline, allocation, FPS, jank or power measurement, and no performance improvement is claimed. Reduced method-hook count for immersion does not establish that QQ cannot detect module activity.

## Offline validation

Focused JVM/Robolectric tests cover native ConstraintLayout measurement (400/600 and restored 500/500), all right containers, repeated application/native resets, full-width and other-owner restoration, independent defaults/persistence/backup/remote bootstrap, scene mode 0/1/2/missing behavior and identity restoration, and native posture replay/coalescing/background/disable cleanup. Existing QQ drawer/navigation/default tests are included.

Final offline verification passed all 30 focused tests, Release/R8 assembly, release vital-Lint, APK v2 signature verification and git diff --check. This is not a full Lint pass. The copied candidate is app/build/outputs/apk/release/app-qq-fold-offline-release.apk. No install, launch or device test was performed after the user requested offline work. Native hotfix behavior, actual fold timing, Activity state preservation on recreation, blur/safe-area geometry, coexistence with other modules and detection behavior remain unverified on the device by the user's request.

Local analysis: user-supplied base(4).apk and /private/tmp/qq-base4-analysis. APK/smali stay local and are not committed. Previous full Release Lint reported 47 errors outside this QQ work; do not describe full Lint as passing. Release vital checks and unit tests have distinct scope.
