package com.aritxonly.myhypermodifier;

import android.animation.ValueAnimator;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Repairs the launcher/recents status-bar appearance handoff without changing stock sampling. */
final class DesktopStatusBarHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    // The tablet recents-appearance repair is isolated until it is proven safe across SystemUI
    // restarts. Keep the original desktop icon decision path active.
    private static final boolean RECENTS_APPEARANCE_REPAIR_ENABLED = false;
    private static final String PHONE = "com.android.systemui.statusbar.phone.";
    private static final String REPO = "com.android.systemui.statusbar.data.repository."
            + "StatusBarModePerDisplayRepositoryImpl";
    private static final String MIUI_FULLSCREEN_STATE_CHANGE =
            "com.miui.fullscreen_state_change";
    private static final String EXTRA_STATE = "state";
    private static final String STATE_TO_RECENTS = "toRecents";
    private static final long RECENTS_REPAIR_TIMEOUT_MS = 900L;
    private static final long RECENTS_REPAIR_FALLBACK_MS = 220L;
    private static BroadcastReceiver recentsReceiver;
    private static BroadcastReceiver miuiFullscreenReceiver;

    static void install(XposedModule module, ClassLoader loader, Application application)
            throws Throwable {
        if (Build.VERSION.SDK_INT < 36
                || !INSTALLED.compareAndSet(false, true)) return;
        module.log(Log.INFO, "MyHyperModifier", "Installing desktop status bar hooks");
        Class<?> controller = Class.forName(PHONE + "LightBarControllerImpl", false, loader);
        Class<?> callback = Class.forName(REPO + "$commandQueueCallback$1", false, loader);
        Class<?> region = Class.forName("com.android.internal.view.AppearanceRegion", false, loader);
        Class<?> regions = java.lang.reflect.Array.newInstance(region, 0).getClass();
        Class<?> letterboxes = java.lang.reflect.Array.newInstance(
                Class.forName("com.android.internal.statusbar.LetterboxDetails", false, loader), 0).getClass();
        Method attributes = callback.getDeclaredMethod("onSystemBarAttributesChanged", int.class,
                int.class, regions, boolean.class, int.class, int.class, String.class,
                letterboxes);
        Binding binding = new Binding(module, loader, controller, callback, regions);
        module.hook(attributes).setId("desktop-status-bar-window-owner")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if ((int) chain.getArg(0) != 0
                            || binding.display.getInt(chain.getThisObject()) != 0) return chain.proceed();
                    Object repository = binding.callbackRepository.get(chain.getThisObject());
                    if (binding.repository.get() != repository) {
                        binding.repository = new WeakReference<>(repository);
                        binding.controller = new WeakReference<>(null);
                    }
                    boolean launcher = "com.miui.home".equals(chain.getArg(6));
                    boolean changed = binding.launcher != launcher;
                    binding.launcher = launcher;
                    int requestedAppearance = (int) chain.getArg(1);
                    Object[] repaired = binding.repairLauncherArguments(chain, launcher);
                    binding.rememberLauncherAppearance(launcher,
                            repaired == null ? requestedAppearance
                                    : DesktopStatusBarPolicy.APPEARANCE_LIGHT_STATUS_BARS);
                    Object result = repaired == null ? chain.proceed() : chain.proceed(repaired);
                    if (repaired != null) binding.restoreLauncherIcons();
                    if (changed && binding.needsOwnerRefresh()) binding.refresh();
                    return result;
                });
        module.hook(binding.update).setId("desktop-status-bar-icon-decision")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object target = chain.getThisObject();
                    // Only the primary display repository identified by the real window callback.
                    if (binding.controllerRepository.get(target) != binding.repository.get()
                            || binding.repository.get() == null) return chain.proceed();
                    if (binding.controller.get() != target) {
                        binding.controller = new WeakReference<>(target);
                    }
                    binding.pendingRefresh.set(false);
                    if (binding.override(target)) {
                        binding.applied = true;
                        return null;
                    }
                    binding.applied = false;
                    return chain.proceed();
                });
        if (RECENTS_APPEARANCE_REPAIR_ENABLED) {
            installRecentsReceiver(module, application, binding);
            installMiuiFullscreenReceiver(module, application, binding);
        }
        ModuleSettings.onChanged(binding::settingsChanged);
        HookDiagnostics.available("com.android.systemui", "桌面状态栏取色");
    }

    private static synchronized void installRecentsReceiver(
            XposedModule module, Application application, Binding binding) {
        if (recentsReceiver != null) return;
        if (application == null) {
            module.log(Log.WARN, "MyHyperModifier",
                    "Recents handoff receiver unavailable before Application attach");
            return;
        }
        recentsReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (RecentsHandoffContract.ACTION_RECENTS_STARTED.equals(action)) {
                    binding.onRecentsStarted();
                } else if (RecentsHandoffContract.ACTION_RECENTS_FINISHED.equals(action)) {
                    binding.onRecentsFinished();
                }
            }
        };
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(RecentsHandoffContract.ACTION_RECENTS_STARTED);
            filter.addAction(RecentsHandoffContract.ACTION_RECENTS_FINISHED);
            application.registerReceiver(recentsReceiver, filter,
                    RecentsHandoffContract.PERMISSION_STATUS_BAR, null, Context.RECEIVER_EXPORTED);
        } catch (Throwable error) {
            recentsReceiver = null;
            module.log(Log.WARN, "MyHyperModifier",
                    "Could not register recents handoff receiver", error);
        }
    }

    private static synchronized void installMiuiFullscreenReceiver(
            XposedModule module, Application application, Binding binding) {
        if (miuiFullscreenReceiver != null) return;
        if (application == null) {
            module.log(Log.WARN, "MyHyperModifier",
                    "MIUI fullscreen state receiver unavailable before Application attach");
            return;
        }
        miuiFullscreenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null
                        || !MIUI_FULLSCREEN_STATE_CHANGE.equals(intent.getAction())) return;
                if (Build.VERSION.SDK_INT >= 34) {
                    String sender = getSentFromPackage();
                    if (sender != null && !"com.miui.home".equals(sender)
                            && !"android".equals(sender)) return;
                }
                String state = intent.getStringExtra(EXTRA_STATE);
                module.log(Log.INFO, "MyHyperModifier",
                        "MIUI fullscreen state=" + state + ", sender="
                                + (Build.VERSION.SDK_INT >= 34 ? getSentFromPackage() : "unknown"));
                if (STATE_TO_RECENTS.equals(state)) {
                    binding.onRecentsStarted();
                } else {
                    binding.onRecentsFinished();
                }
            }
        };
        try {
            application.registerReceiver(miuiFullscreenReceiver,
                    new IntentFilter(MIUI_FULLSCREEN_STATE_CHANGE), Context.RECEIVER_EXPORTED);
            module.log(Log.INFO, "MyHyperModifier",
                    "Installed MIUI fullscreen state receiver");
        } catch (Throwable error) {
            miuiFullscreenReceiver = null;
            module.log(Log.WARN, "MyHyperModifier",
                    "Could not register MIUI fullscreen state receiver", error);
        }
    }

    private static final class Binding {
        final XposedModule module;
        final Handler main = new Handler(Looper.getMainLooper());
        final Field display, callbackRepository, controllerRepository, injector, keyguard,
                statusMode, dispatcher, transitions, appearanceRegions, tintAnimator,
                pendingTint, pendingIntensity, currentIntensity, nextIntensity, tintAreas,
                delayRunnable, appearance;
        final Method value, modeValue, update, darkArea, iconsDark, cancelDelay,
                regionAppearance, regionBounds;
        final Class<?> regionClass;
        final Constructor<?> regionConstructor;
        final Object shadeMode;
        final Object[] clearAreaArgs = {null};
        final Object[] lightArgs = {false, false, true};
        final Object[] darkArgs = {true, false, true};
        final Object[] animatedLightArgs = {false, true, true};
        final Object[] animatedDarkArgs = {true, true, true};
        final AtomicBoolean queued = new AtomicBoolean();
        final AtomicBoolean pendingRefresh = new AtomicBoolean();
        final AtomicBoolean failed = new AtomicBoolean();
        volatile WeakReference<Object> repository = new WeakReference<>(null);
        volatile WeakReference<Object> controller = new WeakReference<>(null);
        volatile boolean launcher;
        volatile boolean launcherUsesLightBars;
        volatile boolean recentsActive;
        volatile boolean recentsLauncherWasLight;
        volatile long recentsRepairUntil;
        private int previousMode = -1;
        private boolean previousEnabled;
        private boolean applied;
        final Runnable refresh = () -> {
            queued.set(false);
            refresh();
        };
        final Runnable repairAfterRecents = this::repairAfterRecents;

        private void refresh() {
            Object target = controller.get();
            if (target == null || failed.get()) return;
            pendingRefresh.set(false);
            try {
                // Stock region cache is never replaced, so SYSTEM immediately restores the ROM.
                update.invoke(target, appearanceRegions.get(target));
            } catch (Throwable error) {
                fail(error);
            }
        }

        private void rememberLauncherAppearance(boolean launcher, int requestedAppearance) {
            if (!launcher) return;
            if (DesktopStatusBarPolicy.hasLightStatusBars(requestedAppearance)) {
                launcherUsesLightBars = true;
            } else if (!recentsActive && recentsRepairUntil == 0L) {
                launcherUsesLightBars = false;
            }
        }

        private Object[] repairLauncherArguments(XposedInterface.Chain chain, boolean launcher) {
            int requestedAppearance = (int) chain.getArg(1);
            if (!pendingRecentsRepair(launcher, requestedAppearance)) return null;
            Object repairedRegions = withLightStatusBars(chain.getArg(2));
            if (repairedRegions == null) return null;
            recentsRepairUntil = 0L;
            main.removeCallbacks(repairAfterRecents);
            Object[] args = chain.getArgs().toArray();
            args[1] = requestedAppearance | DesktopStatusBarPolicy.APPEARANCE_LIGHT_STATUS_BARS;
            args[2] = repairedRegions;
            module.log(Log.INFO, "MyHyperModifier",
                    "Repaired launcher status bar appearance after recents");
            return args;
        }

        void onRecentsStarted() {
            if (recentsActive) return;
            recentsActive = true;
            recentsRepairUntil = 0L;
            recentsLauncherWasLight = launcherUsesLightBars;
            main.removeCallbacks(repairAfterRecents);
            if (!DesktopStatusBarPolicy.shouldLightenRecentsIcons(
                    repairEnabled(), true, isNightMode())) return;
            try {
                Object target = controller.get();
                if (target != null && setIconDarkness(target, false, true)) {
                    module.log(Log.INFO, "MyHyperModifier",
                            "Lightened status bar icons for recents");
                }
            } catch (Throwable error) {
                fail(error);
            }
        }

        void onRecentsFinished() {
            if (!recentsActive) return;
            recentsActive = false;
            boolean wasLight = recentsLauncherWasLight || launcherUsesLightBars;
            recentsLauncherWasLight = false;
            if (!repairEnabled() || isNightMode() || !wasLight) return;
            recentsRepairUntil = SystemClock.uptimeMillis() + RECENTS_REPAIR_TIMEOUT_MS;
            main.removeCallbacks(repairAfterRecents);
            main.postDelayed(repairAfterRecents, RECENTS_REPAIR_FALLBACK_MS);
        }

        private void repairAfterRecents() {
            if (recentsRepairUntil == 0L) return;
            if (SystemClock.uptimeMillis() > recentsRepairUntil) {
                recentsRepairUntil = 0L;
                return;
            }
            try {
                Object target = controller.get();
                if (target == null || !pendingRecentsRepair(launcher,
                        appearance.getInt(target))) return;
                Object current = appearanceRegions.get(target);
                if (containsLightStatusBars(current)) {
                    setIconDarkness(target, true, true);
                    recentsRepairUntil = 0L;
                    return;
                }
                Object repairedRegions = withLightStatusBars(current);
                if (repairedRegions == null) {
                    recentsRepairUntil = 0L;
                    return;
                }
                recentsRepairUntil = 0L;
                appearanceRegions.set(target, repairedRegions);
                update.invoke(target, repairedRegions);
                module.log(Log.INFO, "MyHyperModifier",
                        "Restored launcher appearance after recents consumer ended");
            } catch (Throwable error) {
                recentsRepairUntil = 0L;
                fail(error);
            }
        }

        private boolean pendingRecentsRepair(boolean launcher, int requestedAppearance) {
            return recentsRepairUntil != 0L
                    && SystemClock.uptimeMillis() <= recentsRepairUntil
                    && DesktopStatusBarPolicy.canRepairRecentsAppearance(repairEnabled(),
                    launcher, isNightMode(), launcherUsesLightBars, requestedAppearance);
        }

        private boolean repairEnabled() {
            return !failed.get() && ModuleSettings.moduleHooksEnabled
                    && !ModuleSettings.systemUiCompatibilityMode;
        }

        private boolean isNightMode() {
            int mode = Resources.getSystem().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }

        private Object withLightStatusBars(Object regions) {
            if (regions == null || !regions.getClass().isArray()) return null;
            int length = Array.getLength(regions);
            if (length == 0) return null;
            Object copy = Array.newInstance(regionClass, length);
            try {
                for (int i = 0; i < length; ++i) {
                    Object region = Array.get(regions, i);
                    int current = (Integer) regionAppearance.invoke(region, (Object[]) null);
                    Object bounds = regionBounds.invoke(region, (Object[]) null);
                    Array.set(copy, i, regionConstructor.newInstance(
                            current | DesktopStatusBarPolicy.APPEARANCE_LIGHT_STATUS_BARS, bounds));
                }
                return copy;
            } catch (Throwable error) {
                fail(error);
                return null;
            }
        }

        private boolean containsLightStatusBars(Object regions) {
            if (regions == null || !regions.getClass().isArray()) return false;
            try {
                for (int i = 0, length = Array.getLength(regions); i < length; ++i) {
                    int requested = (Integer) regionAppearance.invoke(
                            Array.get(regions, i), (Object[]) null);
                    if (DesktopStatusBarPolicy.hasLightStatusBars(requested)) return true;
                }
            } catch (Throwable error) {
                fail(error);
            }
            return false;
        }

        Binding(XposedModule module, ClassLoader loader, Class<?> owner, Class<?> callback,
                Class<?> regions) throws Throwable {
            this.module = module;
            display = field(callback, "$thisDisplayId");
            callbackRepository = field(callback, "this$0");
            controllerRepository = field(owner, "mStatusBarModeRepository");
            injector = field(owner, "mInjector");
            Class<?> inject = injector.getType();
            keyguard = field(inject, "isOnKeyguard");
            Class<?> repo = Class.forName(REPO, false, loader);
            statusMode = field(repo, "statusBarMode");
            dispatcher = field(owner, "mStatusBarIconController");
            Class<?> dispatch = Class.forName(PHONE + "DarkIconDispatcherImpl", false, loader);
            transitions = field(dispatch, "mTransitionsController");
            appearanceRegions = field(owner, "mAppearanceRegions");
            if (RECENTS_APPEARANCE_REPAIR_ENABLED) {
                appearance = field(owner, "mAppearance");
                regionClass = regions.getComponentType();
                regionConstructor = regionClass.getDeclaredConstructor(
                        int.class, android.graphics.Rect.class);
                regionAppearance = regionClass.getMethod("getAppearance");
                regionBounds = regionClass.getMethod("getBounds");
            } else {
                appearance = null;
                regionClass = null;
                regionConstructor = null;
                regionAppearance = null;
                regionBounds = null;
            }
            // Resolve from the ROM field type. A literal Kotlin class name can be rewritten by
            // R8 to our bundled Kotlin class name, which the target ClassLoader cannot load.
            value = keyguard.getType().getMethod("getValue");
            modeValue = statusMode.getType().getMethod("getValue");
            update = owner.getDeclaredMethod("updateStatus", regions);
            Class<?> tint = transitions.getType();
            tintAnimator = field(tint, "mTintAnimator");
            pendingTint = field(tint, "mTintChangePending");
            pendingIntensity = field(tint, "mPendingDarkIntensity");
            currentIntensity = field(tint, "mDarkIntensity");
            nextIntensity = field(tint, "mNextDarkIntensity");
            delayRunnable = field(tint, "mDelayRunnable");
            tintAreas = field(dispatch, "mTintAreas");
            cancelDelay = tint.getDeclaredMethod("cancelDelayRunnable");
            darkArea = dispatch.getDeclaredMethod("setIconsDarkArea", ArrayList.class);
            iconsDark = transitions.getType().getDeclaredMethod("setIconsDark", boolean.class,
                    boolean.class, boolean.class);
            shadeMode = Class.forName("com.android.systemui.statusbar.data.model.StatusBarMode",
                    false, loader).getField("SEMI_TRANSPARENT").get(null);
            update.setAccessible(true);
            if (regionConstructor != null) regionConstructor.setAccessible(true);
            cancelDelay.setAccessible(true);
            darkArea.setAccessible(true);
            iconsDark.setAccessible(true);
        }

        boolean override(Object target) {
            int mode = ModuleSettings.desktopStatusBarIconMode;
            // Off/app exit avoids flow reads, allocations, IPC, and rule parsing.
            if (failed.get() || !ModuleSettings.moduleHooksEnabled
                    || ModuleSettings.systemUiCompatibilityMode || !launcher
                    || mode == DesktopStatusBarPolicy.SYSTEM) return false;
            try {
                boolean locked = Boolean.TRUE.equals(value.invoke(
                        keyguard.get(injector.get(target)), (Object[]) null));
                if (locked) return false;
                Object repo = controllerRepository.get(target);
                boolean shade = modeValue.invoke(statusMode.get(repo), (Object[]) null) == shadeMode;
                if (!DesktopStatusBarPolicy.applies(
                        mode, true, launcher, locked, shade, recentsActive)) return false;
                return setIconDarkness(target, mode == DesktopStatusBarPolicy.DARK, false);
            } catch (Throwable error) {
                fail(error);
                return false;
            }
        }

        private boolean setIconDarkness(Object target, boolean dark, boolean animate) {
            try {
                Object dispatch = dispatcher.get(target);
                Object tint = transitions.get(dispatch);
                float desired = dark ? 1f : 0f;
                // setIconsDark(animate=false) alone does not cancel the old animator or its
                // pending transition target. Both can overwrite the requested color.
                ValueAnimator animator = (ValueAnimator) tintAnimator.get(tint);
                if (animator != null && animator.isStarted()) animator.cancel();
                if (delayRunnable.get(tint) != null) cancelDelay.invoke(tint, (Object[]) null);
                if (pendingTint.getBoolean(tint) && pendingIntensity.getFloat(tint) != desired) {
                    pendingIntensity.setFloat(tint, desired);
                }
                boolean hadAreas = !((ArrayList<?>) tintAreas.get(dispatch)).isEmpty();
                if (hadAreas) darkArea.invoke(dispatch, clearAreaArgs);
                if (currentIntensity.getFloat(tint) != desired
                        || nextIntensity.getFloat(tint) != desired) {
                    iconsDark.invoke(tint, dark
                            ? (animate ? animatedDarkArgs : darkArgs)
                            : (animate ? animatedLightArgs : lightArgs));
                }
                return true;
            } catch (Throwable error) {
                fail(error);
                return false;
            }
        }

        void restoreLauncherIcons() {
            try {
                Object target = controller.get();
                if (target != null) setIconDarkness(target, true, true);
            } catch (Throwable error) {
                fail(error);
            }
        }

        boolean needsOwnerRefresh() {
            return applied || (!failed.get() && ModuleSettings.moduleHooksEnabled
                    && !ModuleSettings.systemUiCompatibilityMode
                    && ModuleSettings.desktopStatusBarIconMode != DesktopStatusBarPolicy.SYSTEM);
        }

        synchronized void settingsChanged() {
            int mode = ModuleSettings.desktopStatusBarIconMode;
            boolean enabled = ModuleSettings.moduleHooksEnabled
                    && !ModuleSettings.systemUiCompatibilityMode;
            if (mode == previousMode && enabled == previousEnabled) return;
            previousMode = mode;
            previousEnabled = enabled;
            if (!enabled) {
                recentsRepairUntil = 0L;
                main.removeCallbacks(repairAfterRecents);
            }
            requestRefresh();
        }

        void requestRefresh() {
            pendingRefresh.set(true);
            Object target = controller.get();
            if (target != null && !failed.get() && queued.compareAndSet(false, true)) {
                main.post(refresh);
            }
        }

        void fail(Throwable error) {
            if (failed.compareAndSet(false, true)) {
                HookDiagnostics.failure("com.android.systemui", "桌面状态栏取色", error);
            }
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
