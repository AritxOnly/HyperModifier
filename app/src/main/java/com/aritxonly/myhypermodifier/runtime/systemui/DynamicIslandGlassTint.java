package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Scoped recipe override shared by expanded island and focus notification backgrounds. */
final class DynamicIslandGlassTint {
    private static final Map<View, State> TARGETS = new WeakHashMap<>();
    private static final Map<View, State> NOTIFICATION_TARGETS = new WeakHashMap<>();
    private static final AtomicInteger NOTIFICATION_CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final AtomicInteger CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static volatile boolean hasTargets;
    private static Method setter;

    static final class State {
        final boolean notification;
        State() { this(false); }
        State(boolean notification) { this.notification = notification; }
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
    static void register(View view) { register(view, false); }

    static void register(View view, boolean notification) {
        if (setter == null || ModuleSettings.disableShadeGlassHooks) return;
        synchronized (TARGETS) {
            Map<View, State> targets = targets(notification);
            targets(!notification).remove(view);
            if (!targets.containsKey(view)) targets.put(view, new State(notification));
            hasTargets = true;
        }
    }

    private static Map<View, State> targets(boolean notification) {
        return notification ? NOTIFICATION_TARGETS : TARGETS;
    }

    /** Called from the existing native recipe Hook; no extra universal Hook or frame callback. */
    static float[] rewrite(View view, float[] original) {
        if (!hasTargets) return null;
        State state;
        synchronized (TARGETS) {
            state = TARGETS.get(view);
            if (state == null) state = NOTIFICATION_TARGETS.get(view);
        }
        if (state == null) return null;
        if (Boolean.TRUE.equals(REPLAYING.get())) return original;
        if (original == null || original.length <= 14 || !ShadeCardGlassPolicy.isActiveRecipe(original)) {
            synchronized (TARGETS) {
                Map<View, State> targets = targets(state.notification);
                if (targets.get(view) == state) targets.remove(view);
                hasTargets = !TARGETS.isEmpty() || !NOTIFICATION_TARGETS.isEmpty();
            }
            return null;
        }
        // Stock OEM material callbacks and View.post replay use this target's UI thread.
        // Validation/copies must not hold the shared weak-map lock across unrelated Views.
        int percent = config(state.notification);
        if (state.record(original, percent)) ShadeCardGlassHooks.forgetGenericCard(view);
        if (percent < 0) return null;
        return Float.compare(original[14], percent / 100f) == 0 ? original : state.recipe(percent);
    }

    static void forget(View view) {
        synchronized (TARGETS) {
            TARGETS.remove(view);
            NOTIFICATION_TARGETS.remove(view);
            hasTargets = !TARGETS.isEmpty() || !NOTIFICATION_TARGETS.isEmpty();
        }
    }

    static int effectiveOpacity(boolean notification, boolean expandedEnabled, int expanded,
                                boolean notificationEnabled, int notificationPercent) {
        boolean enabled = notification ? notificationEnabled : expandedEnabled;
        int percent = notification ? notificationPercent : expanded;
        return enabled ? Math.max(0, Math.min(100, percent)) : -1;
    }

    private static int config(boolean notification) {
        if (!ModuleSettings.moduleHooksEnabled || ModuleSettings.disableShadeGlassHooks) return -1;
        return effectiveOpacity(notification, ModuleSettings.dynamicIslandOpacityEnabled,
                ModuleSettings.dynamicIslandExpandedOpacity, ModuleSettings.dynamicIslandNotificationOpacityEnabled,
                ModuleSettings.dynamicIslandNotificationOpacity);
    }

    private static void refresh() {
        if (ModuleSettings.disableShadeGlassHooks) return;
        refreshKind(false, CONFIG);
        refreshKind(true, NOTIFICATION_CONFIG);
    }

    private static void refreshKind(boolean notification, AtomicInteger previous) {
        int config = config(notification);
        if (previous.getAndSet(config) == config || !hasTargets) return;
        ArrayList<View> views;
        Map<View, State> targets = targets(notification);
        synchronized (TARGETS) {
            if (targets.isEmpty()) return;
            views = new ArrayList<>(targets.keySet());
        }
        for (View view : views) {
            if (view == null) continue;
            State state;
            synchronized (TARGETS) { state = targets.get(view); }
            if (state == null || !state.pending.compareAndSet(false, true)) continue;
            State current = state;
            if (!view.post(() -> {
                current.pending.set(false);
                if (ModuleSettings.disableShadeGlassHooks) return;
                synchronized (TARGETS) { if (targets.get(view) != current) return; }
                apply(view, current);
            })) current.pending.set(false);
        }
    }

    private static void apply(View view, State state) {
        int percent = config(state.notification);
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
