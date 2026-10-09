# Spotify MediaSession buttons

## Current implementation

The glass settings tab has a Spotify page with independent favorite and shuffle options (off by default), plus a glass dock switch. Enable both `com.spotify.music` and `com.android.systemui` in LSPosed for the media card options. Installation requires restarting SystemUI and force-stopping/relaunching Spotify so its background playback process reloads the module. The in-app glass dock needs only Spotify's scope and restart.

`SpotifyHooks` intercepts the Android framework `MediaSession.setPlaybackState(PlaybackState)` in Spotify's process. This is the final publication point shared by its support-library and Media3 implementations. Hooking only `androidx.media3.session.legacy.MediaSessionCompat.setPlaybackState` was insufficient: the installed 9.1.84.2231 APK contains direct framework calls in obfuscated classes `p.mod0` / `p.nod0` using `android.support.v4.media.session.PlaybackStateCompat`.

Native favorite commands (`ADD_TO` / `CHECK_FILL`) and shuffle commands (`TURN_SHUFFLE_ON`, `TURN_SHUFFLE_OFF`, `TURN_SMART_SHUFFLE_OFF`) are prioritized. Favorite icon names include `mediaservice_vector_plus_alt` / `mediaservice_vector_check_alt_fill`; resource names rather than hardcoded IDs handle version-specific ID changes. Selected native action objects retain their icons, names and extras, so Spotify continues to own dispatch and state changes. Other actions remain in their relative order. Disabled switches leave the original order alone; unsupported content receives no fabricated actions.

When ordering changes, the bridge rebuilds PlaybackState using public Android APIs, retaining state, position, speed, update time, buffered position, action bits, active queue ID, error message and extras. This avoids reflective access to private builder fields. Null and unchanged states pass through; failed augmentation returns the original state. Settings loaded asynchronously can apply on later state updates.

## HyperOS integration

The local `MiuiSystemUINew.apk` reference gates semantic actions in `MediaDataLoader.loadMediaDataInBackground`: it checks `StatusBarManager.useMediaSessionActionsForApp(packageName, user)` and Xiaomi media/semantic-action allowlists. Only admitted apps call `MediaActionsKt.createActionsFromState`; otherwise the card uses notification actions. The semantic model has three transport slots plus `custom0` and `custom1`.

`SpotifyMediaCardHooks` opts only Spotify into that gate while the module and at least one Spotify media option are enabled. All other apps and disabled options retain the original result. Existing SystemUI compatibility and crash guard checks apply. The gate does not modify layouts or replace transport controls.

## Diagnosis and evidence (2026-10-03)

Initial captures after restarts lost the relevant main-buffer logs due to rapid buffer turnover. Missing log entries in those captures did not establish injection failure. The user confirmed both scopes are checked.

A live capture on the computer, started before the user manually restarted SystemUI and force-stopped/relaunched Spotify, confirmed:

- 01:25:47: SystemUI module entry ran with global enabled=true and compatibility=false; the Spotify action gate installed.
- 01:25:54: Spotify module entry and the old Media3 hook installation ran successfully.
- 01:26:00: SystemUI's Spotify session-action gate executed.
- No old Media3 PlaybackState augmentation entries appeared during playback.

The previously saved installed 9.1.84.2231 APK was then decoded locally. Its support-library path builds native PlaybackState custom actions and calls the framework directly, proving the previous hook did not cover all publishing paths. This led to the final framework publication hook described above.

Diagnostics now report bootstrap gates, framework native/published command IDs and SystemUI custom-slot presence. Repeated identical session/model details are suppressed. No track titles, URIs or action extras are logged by this module.

Debug unit tests cover command/resource recognition and native action ordering/payload identity. Release compilation and signing are checked. The final framework hook still needs live on-device verification; the gate was verified in logs on the preceding build. Every device access in this investigation was log capture. The agent did not install, restart, control playback, change settings or automate the phone UI.

## Framework-path verification and side-slot binding

Live capture at 02:32 confirmed the framework publication hook ran and reordered Spotify's commands to `[ADD_TO, TURN_SHUFFLE_ON, START_RADIO, TURN_REPEAT_ALL_ON]`, with both settings enabled. SystemUI initially triggered automatic recovery after rapid starts. The user manually invoked the existing recovery action; the subsequent 02:33 capture confirmed the session gate executed and the resulting model had `custom0=true, custom1=true`. The user still reported no visible buttons.

This narrows the remaining issue to the presentation/binding stage; the model and native action publication are verified. Added a Spotify-only `MiuiMediaViewControllerImpl.bindMediaData` post-hook that binds the existing `action0` / `action4` views from the native semantic model, keeps its Drawable and Runnable, and restores visible/enabled/alpha state. It repeats once with a posted callback for deferred stock binding, guarding against a changed current media model. It does not hook global View visibility, change dimensions/constraints, or affect other packages. Logs report side-view visibility, alpha, measured size and icon presence before and after binding. This last presentation adjustment still requires on-device confirmation.

## Empty-icon evidence and hidden-action policy

The 02:41 live capture showed both side views visible, alpha=1, measured 186x155, but icon=false before and after binding. The user confirmed the buttons still were not visible. Local SystemUI disassembly of `MediaActionsKt$$ExternalSyntheticLambda0.invoke` shows that the effective `NotificationSettingsManager.mHiddenCustomActionsList` (or its local list when cloud data is disabled) deliberately clears the loaded Drawable. `MediaActionsInjector$getCustomAction$2.run` independently returns without dispatching when the same list contains the package. This mechanism explains a present semantic action with a missing icon; runtime list membership has not been independently read from the device.

The Spotify-only `createActionsFromState` post-hook now rebuilds null-icon side actions. It obtains the exact CustomAction and MediaController from the existing callback's typed captured fields, loads Spotify's icon resource for the supplied user, and dispatches that same native command with its original extras. Names, background, rebind IDs, transport slots and reservation flags are preserved. No translated-label matching, hardcoded Spotify resource IDs, or mutation of the shared hidden-action list is used. Failures keep the original model. Model diagnostics now include icon0/icon1. Device display and button behavior still require manual verification on this build.

## Successful device verification

The final `app-release-spotify-icons.apk` compiled successfully and passed APK signature verification. The four Spotify action-policy tests passed. After the user manually installed and restarted the processes, live logs at 02:49:45 confirmed `custom0=true, custom1=true, icon0=true, icon1=true`. At 02:49:46 both side views were visible, alpha=1, measured 186x155 and had icons. The user then confirmed that both buttons were displayed and their actions worked. The computer-side log capture was stopped after this confirmation; no phone control was performed by the agent.

## In-app glass dock

The user requested the same floating Tab component used by other apps, with a Capsule player above it, and clarified that the Tab's height should be slightly greater. `SpotifyGlassHooks` now attaches the existing shared-tab adapter on `SpotifyMainActivity.onResume`, pauses sampling on pause, and cleans up on destruction. MediaSession hooks remain independent of the glass preference.

`SpotifyPlaybackCapsule` decorates `now_playing_bar_layout` directly without reparenting it out of Spotify's Compose AndroidView holder or replacing its controls. Under normal font scaling the player is 50dp tall versus the shared Tab's 54dp. Large-font layouts retain their original height for readability. The player uses a capsule outline and a native RenderNode backdrop blur with the shared default frost radius/tint parameters, with a subtle edge and a neutral tint fallback. This View material does not reproduce the Compose Tab's full refraction stack. The native cover, track carousel, labels, buttons, progress, gestures and accessibility remain owned by Spotify.

Both samplers use `fragment_container` as the background source so the player and navigation do not recursively enter the sampled image. The original bottom-gradient layers are suppressed while the dock is visible and restored alongside background, outline, clipping, height, navigation inset and window state when hidden or disposed. Native track-background replacements are tracked for restoration. Missing navigation resources retain the original navigation instead of hiding it. Local 9.1.84.2231 resources confirm the target layout IDs and tab drawable names. Release compilation is checked; layout, transitions, font scaling and gesture behavior require user-side visual verification. No device operation was performed during this implementation.

## First dock crash and lifecycle correction

The user supplied `logcat_2026-10-03_03-34-38.txt`. Both Spotify crashes at 03:33:37 and 03:33:42 have `ViewTreeLifecycleOwner not found from ... DecorView`. The module's release mapping identifies `q72.b` as Compose `getWindowRecomposer` / `createLifecycleAwareWindowRecomposer`, and `o.onMeasure` as `AbstractComposeView.onMeasure`. Setting owner tags only on the overlay did not prevent automatic window-recomposer discovery from starting at DecorView.

The Spotify overlay now sets an explicit parent composition context using `composeView.createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle)` before `setContent` or attachment. The locally cached source for Compose UI 1.12.0-rc01 confirms that the explicit lifecycle bypasses root owner lookup, runs recomposition with the UI frame clock, and cancels on view detach or lifecycle destruction. This leaves Spotify's DecorView tags, original owners and window composition context untouched. Compilation/signature verification are checked; user-side launch verification remains pending.

## Backdrop, geometry and pressed-state correction

The user subsequently reported that the Tab lacked a sampled background, the player and Tab had different widths/excess spacing, and tapping the player revealed its stock appearance. Both Spotify samplers now opt into public `HardwareRenderer` + `RenderNode` + RGBA `ImageReader` offscreen capture of `fragment_container`. Software Canvas cannot reliably reproduce host Compose hardware display lists/images. Sampling retains the existing downsampled region and burst cadence, is limited to 30 captures/second, and releases renderer/image resources on disposal. The content source is required; DecorView fallback is avoided because it would include our own dock. Diagnostics report the first successful capture or failure.

The actual shared Tab bounds define the player's capsule outline/background viewport and native content padding, preserving the original player instance. Navigation reservation is computed from measured native margins and window coordinates to target a 6dp vertical gap. Pure layout tests cover differing native margins, stability after relayout, shifted window origins and absent-player layout.

Paused/hidden player transitions stop sampling but keep the capsule decoration until explicit disable/destruction. Two setter hooks in Spotify's process immediately retain the glass background and suppress stock foreground updates only on the registered mini-player instance; original backgrounds/foregrounds and padding are saved for restoration. This prevents a stock surface from entering a frame between the native press callback and our pre-draw update. Native click listeners, playback controls, track gestures and full-player navigation remain in place. Release build/tests and signature verification are checked; the three visual corrections require manual device confirmation.

## Transparent fragment substrate

On the next build the user reported that images blurred but text looked merely translucent. Live logs confirmed both hardware samplers successfully produced images (219x64 and 219x66) without a capture exception. The offscreen renderer had been transparent and only drew the fragment; it omitted the window's background substrate. Therefore transparency in the fragment could survive blur and expose the original, unblurred content beneath the glass.

Hardware capture now first fills an opaque dark fallback, composites the actual DecorView background in the correct source/window coordinates, then draws the fragment. The offscreen renderer is opaque. This produces a complete background image while still excluding the injected dock. First-capture diagnostics additionally report sampled minimum/maximum alpha to verify opacity from logs. No artwork, text content or pixel colors are logged. The actual visual improvement requires user-side verification.

## Dark theme, frame cadence and cover offset

The user confirmed the opaque-background build looked good, then requested a permanently dark Tab, reduced sampling lag, and a roughly 4dp rightward cover offset. The Spotify-only Compose theme now explicitly uses the dark palette rather than the system theme. Both samplers use source-frame updates, display-aware cadence with a 16ms minimum, and inline hardware capture at pre-draw when due. This replaces the 33ms cadence and prevents a 160ms post-touch burst from ending before host Compose inertia/animations have finished. Identical snapshots still do not publish or invalidate the UI, and paused/hidden samplers remain inactive. Other app sampler settings retain their defaults.

The mini-player's own `cover_image` is translated 4dp to the right, independently of the capsule viewport and shared width. The original translation is restored on replacement/disposal. Compilation and signing are checked; real device smoothness and cover spacing require manual confirmation.

## Cover offset clipping correction

The user reported that the translated cover was partially clipped. The local Spotify APK's `now_playing_bar.xml` shows `cover_image` filling an inner MotionLayout (`content_container`), constrained to its end edge. Translating the image leaves that parent's clipping bounds unchanged. The offset now applies to the cover region's direct ancestor under `now_playing_bar_layout`, moving its image and container together by 4dp. Native clipping and the outer capsule outline remain intact, and the original container translation is restored on replacement/disposal. Device visual verification remains manual.

## Slow-drag discontinuities and complete Capsule optics

The user reported drawing discontinuities during slow dragging and that the Capsule did not look like soft glass. Inspection confirms two independent hardware samplers previously captured overlapping content and blocked at pre-draw on GPU presentation; the Capsule only implemented blur/tint/border. Spotify now captures the union of Tab and native player once, retargets that same snapshot in window coordinates for both surfaces, and schedules capture after host traversal. ImageReader completion and strided pixel readback run on a dedicated worker; the UI thread no longer waits for presentation. Cadence follows the display refresh rate, including 120Hz, without the former 16ms floor. Only one capture may be in flight, obsolete sampling-context results are discarded, and readback storage is reused. Other apps retain their scheduling defaults. Alignment tests cover shared crops, missing player, and window movement.

The native Capsule reuses `DeadlinerGlassRecipes.floatingNavigation` and the exact shared `GlassRefractionShader`. Its effect chain now includes color treatment, pixel-unit material blur, rounded capsule refraction, chromatic aberration, noise, highlight, and post-refraction softening, followed by the same dark tint and edge parameters as the shared surface. API 31–32 retains blur fallback; runtime optics require API 33. Native content, gestures, cover-container offset, and stock background/foreground interception remain intact. Release compilation and APK signature verification passed. Unit tests passed, including two shared-backdrop alignment tests, three dock-layout tests and four action-policy tests. Slow-drag rendering and the material appearance still require manual device verification; no phone control was performed.

## Full-width Spotify dock

Spotify opts into the shared Tab's `fillAvailableWidth` parameter, bypassing both the 380dp panel cap and the per-item 80dp cap. The existing host's 16dp horizontal padding defines the Tab width; measured Tab bounds continue to define the native player capsule's viewport and content insets, so both surfaces share those same edges. Other callers retain compact width defaults.

The user subsequently requested another 4dp inset on each side. Spotify's horizontal host padding is now 20dp; the player continues to follow measured Tab bounds.
