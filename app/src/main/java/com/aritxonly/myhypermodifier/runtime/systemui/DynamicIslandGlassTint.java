package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Scoped recipe override for expanded island and pull-to-window backgrounds. */
final class DynamicIslandGlassTint {
    private static final Map<View, State> TARGETS = new WeakHashMap<>();
    private static final AtomicInteger CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static volatile boolean hasTargets;
    private static Method setter;

    static final class State {
        float[] stock;
        float[] tuned;
        int applied = -1;
        final AtomicBoolean pending = new AtomicBoolean();

        void record(float[] original) {
            if (stock == null || stock.length != original.length) {
                stock = new float[original.length];
                tuned = new float[original.length];
            }
            System.arraycopy(original, 0, stock, 0, original.length);
            applied = -1;
        }

        boolean record(float[] original, int percent) {
            boolean claimCardRecipe = percent >= 0 && applied != percent;
            record(original);
            applied = percent;
            return claimCardRecipe;
        }

        float[] recipe(int percent) {
            if (percent < 0) return stock;
            System.arraycopy(stock, 0, tuned, 0, stock.length);
            tuned[14] = Math.max(0, Math.min(100, percent)) / 100f;
            return tuned;
        }
    }

    static synchronized void install() {
        if (setter != null || ModuleSettings.disableShadeGlassHooks) return;
        try {
            setter = View.class.getMethod("setMiGlass", float[].class);
            ModuleSettings.onChanged(DynamicIslandGlassTint::refresh);
        } catch (Throwable error) {
            HookDiagnostics.failure("com.android.systemui", "超级岛背景不透明度", error);
        }
    }

    /** Register BEFORE stock apply so its real recipe is captured, including animation changes. */
    static void register(View view) {
        if (setter == null || ModuleSettings.disableShadeGlassHooks) return;
        synchronized (TARGETS) {
            if (!TARGETS.containsKey(view)) TARGETS.put(view, new State());
            hasTargets = true;
        }
    }

    /** Called from the existing native recipe Hook; no extra universal Hook or frame callback. */
    static float[] rewrite(View view, float[] original) {
        if (!hasTargets) return null;
        State state;
        synchronized (TARGETS) {
            state = TARGETS.get(view);
        }
        if (state == null) return null;
        if (Boolean.TRUE.equals(REPLAYING.get())) return original;
        if (original == null || original.length <= 14 || !ShadeCardGlassPolicy.isActiveRecipe(original)) {
            synchronized (TARGETS) {
                if (TARGETS.get(view) == state) TARGETS.remove(view);
                hasTargets = !TARGETS.isEmpty();
            }
            return null;
        }
        // Stock OEM material callbacks and View.post replay use this target's UI thread.
        // Validation/copies must not hold the shared weak-map lock across unrelated Views.
        int percent = config();
        if (state.record(original, percent)) ShadeCardGlassHooks.forgetGenericCard(view);
        if (percent < 0) return null;
        return Float.compare(original[14], percent / 100f) == 0 ? original : state.recipe(percent);
    }

    static void forget(View view) {
        synchronized (TARGETS) {
            TARGETS.remove(view);
            hasTargets = !TARGETS.isEmpty();
        }
    }

    static int effectiveOpacity(boolean enabled, int percent) {
        return enabled ? Math.max(0, Math.min(100, percent)) : -1;
    }

    private static int config() {
        if (!ModuleSettings.moduleHooksEnabled || ModuleSettings.disableShadeGlassHooks) return -1;
        return effectiveOpacity(ModuleSettings.dynamicIslandOpacityEnabled, ModuleSettings.dynamicIslandExpandedOpacity);
    }

    private static void refresh() {
        if (ModuleSettings.disableShadeGlassHooks) return;
        int config = config();
        if (CONFIG.getAndSet(config) == config || !hasTargets) return;
        ArrayList<View> views;
        synchronized (TARGETS) {
            if (TARGETS.isEmpty()) return;
            views = new ArrayList<>(TARGETS.keySet());
        }
        for (View view : views) {
            if (view == null) continue;
            State state;
            synchronized (TARGETS) { state = TARGETS.get(view); }
            if (state == null || !state.pending.compareAndSet(false, true)) continue;
            State current = state;
            if (!view.post(() -> {
                current.pending.set(false);
                if (ModuleSettings.disableShadeGlassHooks) return;
                synchronized (TARGETS) { if (TARGETS.get(view) != current) return; }
                apply(view, current);
            })) current.pending.set(false);
        }
    }

    private static void apply(View view, State state) {
        int percent = config();
        if (state.stock == null || state.applied == percent) return;
        try {
            ShadeCardGlassHooks.forgetGenericCard(view);
            REPLAYING.set(true);
            setter.invoke(view, (Object) state.recipe(percent));
            state.applied = percent;
        } catch (ReflectiveOperationException error) {
            Log.w("MyHyperModifier", "Dynamic island tint refresh failed", error);
        } finally {
            REPLAYING.remove();
        }
    }

    static float[] withOpacity(float[] original, int percent) {
        if (original == null || original.length <= 14) throw new IllegalArgumentException("Invalid Glass recipe");
        float[] result = original.clone();
        result[14] = Math.max(0, Math.min(100, percent)) / 100f;
        return result;
    }
}
