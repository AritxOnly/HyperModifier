package com.aritxonly.myhypermodifier;

import android.os.Build;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Overrides the final launcher icon decision without altering stock appearance/sampling state. */
final class DesktopStatusBarHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final String PHONE = "com.android.systemui.statusbar.phone.";
    private static final String REPO = "com.android.systemui.statusbar.data.repository."
            + "StatusBarModePerDisplayRepositoryImpl";

    static void install(XposedModule module, ClassLoader loader) throws Throwable {
        if (Build.VERSION.SDK_INT < 36 || !INSTALLED.compareAndSet(false, true)) return;
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
                    Object result = chain.proceed();
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
                    if (binding.override(target)) {
                        binding.applied = true;
                        return null;
                    }
                    binding.applied = false;
                    return chain.proceed();
                });
        ModuleSettings.onChanged(binding::settingsChanged);
        HookDiagnostics.available("com.android.systemui", "桌面状态栏取色");
    }

    private static final class Binding {
        final XposedModule module;
        final Handler main = new Handler(Looper.getMainLooper());
        final Field display, callbackRepository, controllerRepository, injector, keyguard,
                statusMode, dispatcher, transitions, appearanceRegions, tintAnimator,
                pendingTint, pendingIntensity, currentIntensity, nextIntensity, tintAreas, delayRunnable;
        final Method value, modeValue, update, darkArea, iconsDark, cancelDelay;
        final Object shadeMode;
        final Object[] clearAreaArgs = {null};
        final Object[] lightArgs = {false, false, true};
        final Object[] darkArgs = {true, false, true};
        final AtomicBoolean queued = new AtomicBoolean();
        final AtomicBoolean failed = new AtomicBoolean();
        volatile WeakReference<Object> repository = new WeakReference<>(null);
        volatile WeakReference<Object> controller = new WeakReference<>(null);
        volatile boolean launcher;
        private int previousMode = -1;
        private boolean previousEnabled;
        private boolean applied;
        final Runnable refresh = () -> {
            queued.set(false);
            refresh();
        };

        private void refresh() {
            Object target = controller.get();
            if (target == null || failed.get()) return;
            try {
                // Stock region cache is never replaced, so SYSTEM immediately restores the ROM.
                update.invoke(target, appearanceRegions.get(target));
            } catch (Throwable error) { fail(error); }
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
                boolean locked = Boolean.TRUE.equals(value.invoke(keyguard.get(injector.get(target)), (Object[]) null));
                if (locked) return false;
                Object repo = controllerRepository.get(target);
                boolean shade = modeValue.invoke(statusMode.get(repo), (Object[]) null) == shadeMode;
                if (!DesktopStatusBarPolicy.applies(mode, true, launcher, locked, shade)) return false;
                Object dispatch = dispatcher.get(target);
                Object tint = transitions.get(dispatch);
                float desired = mode == DesktopStatusBarPolicy.DARK ? 1f : 0f;
                // setIconsDark(animate=false) alone does not cancel the old animator or its
                // pending transition target. Both can overwrite the immediate fixed color.
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
                    iconsDark.invoke(tint, mode == DesktopStatusBarPolicy.DARK ? darkArgs : lightArgs);
                }
                return true;
            } catch (Throwable error) {
                fail(error);
                return false;
            }
        }

        boolean needsOwnerRefresh() {
            return applied || (!failed.get() && ModuleSettings.moduleHooksEnabled
                    && !ModuleSettings.systemUiCompatibilityMode
                    && ModuleSettings.desktopStatusBarIconMode != DesktopStatusBarPolicy.SYSTEM);
        }

        synchronized void settingsChanged() {
            int mode = ModuleSettings.desktopStatusBarIconMode;
            boolean enabled = ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode;
            if (mode == previousMode && enabled == previousEnabled) return;
            previousMode = mode;
            previousEnabled = enabled;
            requestRefresh();
        }

        void requestRefresh() {
            if (queued.compareAndSet(false, true)) main.post(refresh);
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
