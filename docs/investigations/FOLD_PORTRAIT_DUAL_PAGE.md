# Inner portrait native dual-page desktop

Added as an independent, default-off switch in 折叠屏专区. Preference and broadcast keys are `fold_portrait_dual_page_enabled` and `mhm_fold_portrait_dual_page`. Saving, backup/import, system defaults, launcher/SystemUI scope reporting and master/compatibility gates use the existing settings pipeline. WM size/density and developer options are untouched.

## Verified local reference

Xiaomi lhasa, MiuiHome `RELEASE-8.01.02.6325-260818-09181716-R`, libapp build ID `4f1bdaedc72c20e7de7d3ebc351bdd3a`. The UI version/device check is preliminary. Runtime requires the ELF build ID and nine code-range hashes to match. Other versions or already-patched contracts fail closed.

Pool labels were resolved by the actual byte offsets from `unflutter _debug objects`, correcting the disassembler's two-entry offset. RVAs below are virtual addresses, not APK/file offsets.

- `0x8917f4` is the predicate logged as `isFoldDualPageMode`. Its Q18 path invokes the physical orientation predicate at `0x8918c0` and returns at `0x8918c4`. Native page pairing, parity screens, page trimming and page positioning have callers of this predicate.
- `0x880974` is the inner-landscape predicate used by `insertNewScreenForEditMode`. Its orientation call returns at `0x8809b0`. Its OEM fold/feature checks run before that call.
- `0x8809c4` checks orientation. The detour overrides it ONLY for those two exact caller return addresses and only while current initialized geometry is inner portrait. All other physical orientation callers continue through the original trampoline.
- Q18 grid dispatch at `0x19ebc10` chooses external, inner landscape (`0x19ec99c`) or inner portrait (`0x19ebcd0`) from geometry fields 27 and 35. The portrait detour delegates to the native dual-page calculator with the original receiver and payload untouched. The native calculator reads actual width at payload+11 and height at payload+19, and computes per-page width as width/2. It does not rotate or swap portrait dimensions or modify the orientation enum.
- Global geometry access follows the initialized state chain already used by the icon-size feature, with additional null/sentinel guards. Grid dispatch reads its incoming geometry so it does not select using the previous frame's state. No Dart pointers are stored outside a call.

This is native layout reuse. There is no new layout database, copying of icons, custom drag/drop implementation or global orientation spoof. OEM code remains responsible for parity/empty-page handling and all edits. The existing portrait icon percentage remains independent and can scale icons within this layout.

## Enable and restore

Install the module APK and reload the module (a device reboot reloads both SystemUI's carrier and the native launcher module). Enable the switch, then restart the system launcher. Restart the launcher after disabling as well so cached Flutter layout/page state is rebuilt. The carrier revokes the effective flag when the module is disabled or compatibility mode is enabled. The disabled native path tail-calls the original functions.

Installation is lazy on first enable and requires a rollback backend. Both detours must succeed before the shared active flag is published. Failed or null-trampoline installs roll back only successfully owned targets. Failure is terminal for that process, so repeated load/settings notifications do not repeatedly attempt patching. Logs use `MHM.FoldDualPage`.

## Validation and limits

- Production ARM64 detours execute on the macOS arm64 host over 432 combinations of enable state, inner/outer, portrait/landscape, allowed/other callers and initialized/null/sentinel states. Tests assert original argument, payload/geometry and NZCV preservation, plus original fallback or dual-page dispatch as appropriate.
- Production C++ installer is tested with the mapped real launcher ELF under ASan/UBSan. Tests include every code-range mutation, build-ID mutation, default off, missing backend, occupied target, null trampoline, rollback, terminal failures, disabling during install and re-enabling.
- Existing icon entry and native install-loop regressions pass. Settings tests cover default off, independence from icon percentage, save/load, backup/import, bundle propagation and restoration to system defaults.
- Android unit tests and release APK build are run for delivery. These are not device-rendering tests. Paired-page appearance, odd/trailing empty pages, widget sizing, edit mode, drag/drop, page selection across fold/rotation and restoring original cover/landscape behavior still require the user's device test. No APK was installed by this task.

## Performance review

No new process scope, receiver, thread, polling or timer was added. Configuration is carried by the existing authenticated SystemUI-to-launcher bridge and its existing native install worker. Cache I/O occurs only during initialization or a changed setting. ABI hashing occurs once when enabling the feature.

Hot paths use an acquire byte load and register/pointer comparisons. No heap allocation, disk I/O, IPC, C++ locks, reflection, logging or JNI/Dart calls were added to the detours. Disabled paths return immediately to the OEM trampoline. Dual-page drawing, widget layout and drag/drop use OEM implementations. This is a structural code review and host correctness validation, not a measured frame-rate, energy or jank improvement.

## Follow-up after no visible effect

The user reports enabling the switch and restarting both the device and launcher, with no visible change. ADB was unavailable and the user could not connect the phone. No device observation establishes whether the settings carrier, installation or Dart paths failed. The layout change remains unverified, and this follow-up must not be described as a confirmed layout fix.

A separate diagnostic incompatibility was confirmed in source. `WidgetStatusSocket` accepted only UID 1000, while the prior device inspection recorded SystemUI UID 10193. The authenticated SystemUI carrier now includes its actual process UID. Only after verifying the carrier's sender package does native code accept a same-user SystemUI UID for the read-only socket. Arbitrary broadcasts and cross-user/invalid UIDs cannot change it. The module UI still queries through the existing authenticated module-to-SystemUI receiver and verifies the launcher's socket peer UID.

The existing status protocol now carries optional `fold_protocol=2` fields. The folding settings page has an on-demand status action for settings receipt, requested/effective state, ABI/install stage, and whether the two allowed mode calls and the portrait grid entry have executed or applied their override. Old module responses, missing updates and failures are displayed separately. On native connection failure, SystemUI reports whether its current module has sent the setting, without claiming delivery.

Four sticky atomic byte flags record the first call/first application for the current launcher process. They never log, allocate, lock, poll or write files from Dart callbacks. There are no new receivers, threads or scopes. These observations do not measure rendering performance and do not establish that the user-visible layout is correct. A new phone-process test is still required.

Validation adds production ARM64 assertions for all four markers, authenticated-carrier UID regression tests, cross-user UID rejection, worst-case snapshot size, and Android-free parser tests distinguishing not-called, condition-rejected, applied, old-version, mismatched settings and failed installations.
