package com.aritxonly.myhypermodifier;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Fractional visual spacing keeps the speed slot and its neighbouring icons in place. */
final class StatusBarNetworkSpeedSpacingHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String SPEED_VIEW = SYSTEM_UI + ".statusbar.views.NetworkSpeedView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Map<ViewGroup, State> VIEWS = new WeakHashMap<>();

    private StatusBarNetworkSpeedSpacingHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> type = Class.forName(SPEED_VIEW, false, loader);
            Class<?> owner = type;
            Method measure = null;
            while (owner != null) {
                try {
                    measure = owner.getDeclaredMethod("onMeasure", int.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) { owner = owner.getSuperclass(); }
            }
            if (measure == null) throw new NoSuchMethodException(SPEED_VIEW + ".onMeasure");
            module.hook(measure)
                    .setId("status-bar-network-speed-spacing")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object receiver = chain.getThisObject();
                        if (type.isInstance(receiver) && receiver instanceof ViewGroup root) {
                            ModuleSettings.ensureLoaded();
                            apply(root);
                        }
                        return chain.proceed();
                    });
            HookDiagnostics.available(SYSTEM_UI, "实时网速右侧距离");
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "实时网速右侧距离", error);
        }
    }

    private static void apply(ViewGroup root) {
        int id = root.getResources().getIdentifier("network_speed_container", "id", SYSTEM_UI);
        View content = id == 0 ? null : root.findViewById(id);
        if (content == null) return;
        State state;
        synchronized (VIEWS) {
            state = VIEWS.get(root);
            if (state == null) {
                state = new State(root, content);
                VIEWS.put(root, state);
            }
        }
        float value = ModuleSettings.statusBarNetworkSpeedRightGap;
        float dp = Float.isFinite(value) ? Math.max(-2f, Math.min(2f, value)) : 0f;
        float x = state.stockX - dp * root.getResources().getDisplayMetrics().density;
        if (content.getTranslationX() != x) content.setTranslationX(x);
        boolean children = dp == 0f && state.clipChildren;
        boolean padding = dp == 0f && state.clipToPadding;
        if (root.getClipChildren() != children) root.setClipChildren(children);
        if (root.getClipToPadding() != padding) root.setClipToPadding(padding);
    }

    static void refresh() {
        ArrayList<ViewGroup> roots;
        synchronized (VIEWS) { roots = new ArrayList<>(VIEWS.keySet()); }
        for (ViewGroup root : roots) {
            if (root != null) root.post(() -> apply(root));
        }
    }

    private static final class State {
        final float stockX;
        final boolean clipChildren;
        final boolean clipToPadding;

        State(ViewGroup root, View content) {
            stockX = content.getTranslationX();
            clipChildren = root.getClipChildren();
            clipToPadding = root.getClipToPadding();
        }
    }
}
