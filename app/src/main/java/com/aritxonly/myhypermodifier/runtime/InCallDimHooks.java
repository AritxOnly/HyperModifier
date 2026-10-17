package com.aritxonly.myhypermodifier;

import android.graphics.Point;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Owns only the backdrop passed to MaterialUtils.applyContainer, never button material. */
final class InCallDimHooks {
    private static final Map<View, State> TARGETS = new WeakHashMap<>();
    private static final AtomicInteger CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static Method setColors;

    private static final class State {
        final int[] stock;
        int applied = -1;
        final AtomicBoolean pending = new AtomicBoolean();
        State(int[] stock) { this.stock = stock; }
    }

    private InCallDimHooks() {}

    private static int config() {
        return moduleHooksEnabled && globalBackgroundDimEnabled
                ? BackgroundDimPolicy.blackColor(globalBackgroundDimPercent, 1f) >>> 24 : -1;
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> material = Class.forName("com.android.incallui.util.MaterialUtils", false, loader);
            Class<?> token = Class.forName("D1.j", false, loader);
            // Verified metadata-only chain j.c -> e.g -> g.a/g.b (colors/modes).
            Field container = field(token, "c");
            Field blend = field(container.getType(), "g");
            Field colors = field(blend.getType(), "a");
            Field modes = field(blend.getType(), "b");
            Method setter = View.class.getMethod("setMiBackgroundBlendColors", ArrayList.class);
            module.hook(material.getDeclaredMethod("applyContainer", View.class, token))
                    .setId("incall-backdrop-dim")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        View view = (View) chain.getArg(0);
                        if (view == null) return result;
                        if (!isLoaded()) markLoaded(view.getContext());
                        Object recipe = chain.getArg(1);
                        Object properties = recipe == null ? null : container.get(recipe);
                        Object tint = properties == null ? null : blend.get(properties);
                        if (tint == null) {
                            synchronized (TARGETS) { TARGETS.remove(view); }
                            return result;
                        }
                        int[] stockColors = (int[]) colors.get(tint);
                        int[] stockModes = (int[]) modes.get(tint);
                        if (stockColors == null || stockModes == null) {
                            synchronized (TARGETS) { TARGETS.remove(view); }
                            return result;
                        }
                        int count = Math.min(stockColors.length, stockModes.length);
                        int[] pairs = new int[count * 2];
                        for (int i = 0; i < count; i++) {
                            pairs[i * 2] = stockColors[i];
                            pairs[i * 2 + 1] = stockModes[i];
                        }
                        State state = new State(pairs);
                        synchronized (TARGETS) { TARGETS.put(view, state); }
                        if (config() >= 0) apply(view, state, config());
                        return result;
                    });
            setColors = setter;
            onChanged(InCallDimHooks::refresh);
            HookDiagnostics.available(ModuleScopePackage.INCALL_UI, "通话背景压暗");
        } catch (Throwable error) {
            HookDiagnostics.failure(ModuleScopePackage.INCALL_UI, "通话背景压暗", error);
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void refresh() {
        int desired = config();
        if (CONFIG.getAndSet(desired) == desired) return;
        ArrayList<View> targets;
        synchronized (TARGETS) { targets = new ArrayList<>(TARGETS.keySet()); }
        for (View view : targets) {
            if (view == null) continue;
            State state;
            synchronized (TARGETS) { state = TARGETS.get(view); }
            if (state == null || !state.pending.compareAndSet(false, true)) continue;
            if (!view.post(() -> {
                state.pending.set(false);
                synchronized (TARGETS) { if (TARGETS.get(view) != state) return; }
                int latest = config();
                if (state.applied != latest) apply(view, state, latest);
            })) state.pending.set(false);
        }
    }

    private static void apply(View view, State state, int alpha) {
        int[] pairs = alpha < 0 ? state.stock
                : BackgroundDimPolicy.blendColors(state.stock, alpha * 100f / 255f);
        ArrayList<Point> colors = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i + 1 < pairs.length; i += 2) colors.add(new Point(pairs[i], pairs[i + 1]));
        try {
            setColors.invoke(view, colors);
            state.applied = alpha;
        } catch (ReflectiveOperationException error) {
            if (FAILURE_LOGGED.compareAndSet(false, true)) Log.w("MyHyperModifier", "InCall dim refresh failed", error);
        }
    }
}
