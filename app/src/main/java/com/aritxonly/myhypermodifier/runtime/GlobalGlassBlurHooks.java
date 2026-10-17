package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Process-wide native Glass radius override, independent of card recipe classification. */
final class GlobalGlassBlurHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean OBSERVER_INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static final Map<View, GlobalGlassBlurState> TARGETS =
            new WeakHashMap<>();
    private static Method setter;
    private static final AtomicInteger REFRESH_CONFIG = new AtomicInteger(Integer.MIN_VALUE);

    private GlobalGlassBlurHooks() {}

    static void install(XposedModule module, String packageName) {
        if (disableShadeGlassHooks || !INSTALLED.compareAndSet(false, true)) return;
        try {
            final boolean preserveClear = ModuleScopePackage.INCALL_UI.equals(packageName);
            Method method = View.class.getMethod("setMiGlassBlurRadius", int.class, int.class);
            module.hook(method).setId("global-glass-blur-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (Boolean.TRUE.equals(REPLAYING.get())) return chain.proceed();
                        if ((!moduleHooksEnabled && !preserveClear) || disableShadeGlassHooks) return chain.proceed();
                        Object receiver = chain.getThisObject();
                        if (!(receiver instanceof View)) return chain.proceed();
                        View view = (View) receiver;
                        // Load once from this View's context; no ActivityThread reflection on animation ticks.
                        if (!isLoaded()) markLoaded(view.getContext());
                        int small = (Integer) chain.getArg(0);
                        int big = (Integer) chain.getArg(1);
                        GlobalGlassBlurState state;
                        synchronized (TARGETS) {
                            state = TARGETS.get(view);
                            if (state == null) {
                                // Island sampling is shared with the collapsed pill. Only its
                                // expanded lifecycle may activate the override for this owner.
                                boolean active = !view.getClass().getName().equals(
                                        "miui.systemui.dynamicisland.window.DynamicIslandWindowView");
                                state = new GlobalGlassBlurState(small, big, active);
                                TARGETS.put(view, state);
                            } else {
                                state.recordStock(small, big);
                            }
                        }
                        // InCallUI clearGlassMaterial explicitly sends 0/0. Preserve that lifecycle
                        // boundary and allow a later positive stock call to reactivate the owner.
                        if (preserveClear) state.active = InCallBlurPolicy.glassActive(small, big);
                        // Keep stock tracking while off so enabling can update already-created Views.
                        // Reuse the per-View state and allocate arguments only when replacement is needed.
                        int radius = GlobalGlassBlurState.clamp(shadeCardGlassBlurRadius);
                        boolean override = enabled() && state.active && (small != radius || big != radius);
                        Object result = override ? chain.proceed(new Object[]{radius, radius}) : chain.proceed();
                        state.recordApplied(override ? radius : small, override ? radius : big);
                        return result;
                    });
            setter = method;
            if (OBSERVER_INSTALLED.compareAndSet(false, true)) onChanged(GlobalGlassBlurHooks::refresh);
            HookDiagnostics.available(packageName, "全局 Glass 模糊半径");
            Log.i("MyHyperModifier", "Global Glass radius hook installed in " + packageName);
        } catch (Throwable error) {
            INSTALLED.set(false);
            HookDiagnostics.failure(packageName, "全局 Glass 模糊半径", error);
            Log.w("MyHyperModifier", "Native Glass radius API unavailable in " + packageName, error);
        }
    }

    private static boolean enabled() {
        return moduleHooksEnabled && !disableShadeGlassHooks && globalGlassBlurEnabled;
    }

    private static void refresh() {
        // Compatibility mode yields ownership to other material modules until process restart.
        if (disableShadeGlassHooks) return;
        int config = enabled() ? GlobalGlassBlurState.clamp(shadeCardGlassBlurRadius) : -1;
        if (REFRESH_CONFIG.getAndSet(config) == config) return;
        ArrayList<View> targets;
        synchronized (TARGETS) { targets = new ArrayList<>(TARGETS.keySet()); }
        for (View view : targets) {
            if (view == null) continue;
            GlobalGlassBlurState state;
            synchronized (TARGETS) { state = TARGETS.get(view); }
            if (state != null) schedule(view, state);
        }
    }

    /** Some material targets apply a recipe without making a native radius call. */
    static boolean onMaterialApplied(View view) {
        if (setter == null || !moduleHooksEnabled || disableShadeGlassHooks) return false;
        if (!isLoaded()) markLoaded(view.getContext());
        GlobalGlassBlurState state;
        synchronized (TARGETS) {
            state = TARGETS.get(view);
            if (state == null) {
                // Verified native BackgroundBlurBlendProperties defaults on the supported build.
                // An observed system setter call always takes precedence over these defaults.
                state = new GlobalGlassBlurState(0, 0);
                TARGETS.put(view, state);
            }
        }
        return applyRadius(view, state, enabled());
    }

    static boolean onExpandedMaterialApplied(View owner) {
        if (setter == null || !moduleHooksEnabled || disableShadeGlassHooks) return false;
        if (!isLoaded()) markLoaded(owner.getContext());
        GlobalGlassBlurState state;
        synchronized (TARGETS) {
            state = TARGETS.get(owner);
            if (state == null) {
                // Stock Bionics DynamicIslandWindowView.updateWindowBlur(true) supplies 50/500.
                state = new GlobalGlassBlurState(50, 500, false);
                TARGETS.put(owner, state);
            }
            state.active = true;
        }
        return applyRadius(owner, state, enabled());
    }

    static void onMaterialCleared(View view) {
        GlobalGlassBlurState state;
        synchronized (TARGETS) { state = TARGETS.remove(view); }
        // Removing first also invalidates any pending settings replay for a now non-Glass target.
        if (state == null || setter == null || disableShadeGlassHooks) return;
        applyRadius(view, state, false);
    }

    private static void schedule(View view, GlobalGlassBlurState state) {
        if (!state.refreshPending.compareAndSet(false, true)) return;
        if (!view.post(() -> {
            state.refreshPending.set(false);
            if (disableShadeGlassHooks || setter == null) return;
            synchronized (TARGETS) { if (TARGETS.get(view) != state) return; }
            // Read the latest config/stock values after coalescing queued settings updates.
            applyRadius(view, state, enabled());
        })) state.refreshPending.set(false);
    }

    private static boolean applyRadius(View view, GlobalGlassBlurState state, boolean enabled) {
        int radius = shadeCardGlassBlurRadius;
        if (!state.needsRefresh(enabled, radius)) return true;
        int small = state.smallRadius(enabled, radius);
        int big = state.bigRadius(enabled, radius);
        try {
            REPLAYING.set(true);
            setter.invoke(view, small, big);
            state.recordApplied(small, big);
            return true;
        } catch (ReflectiveOperationException error) {
            Log.w("MyHyperModifier", "Global Glass radius refresh failed", error);
            return false;
        } finally {
            REPLAYING.remove();
        }
    }
}
