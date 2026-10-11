package com.aritxonly.myhypermodifier;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** HyperOS icon containers account for child width/padding rather than LayoutParams margins. */
final class StatusBarNetworkMarginsHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String MOBILE_VIEW = SYSTEM_UI
            + ".statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView";
    private static final String SPEED_VIEW = SYSTEM_UI + ".statusbar.views.NetworkSpeedView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Map<ViewGroup, State> VIEWS = new WeakHashMap<>();

    private StatusBarNetworkMarginsHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        Set<Method> hooked = new HashSet<>();
        installView(module, loader, MOBILE_VIEW, hooked);
        installView(module, loader, SPEED_VIEW, hooked);
    }

    private static void installView(XposedModule module, ClassLoader loader, String name,
                                    Set<Method> hooked) {
        String feature = name.equals(MOBILE_VIEW) ? "移动信号左右边距" : "实时网速左右边距";
        try {
            Class<?> owner = Class.forName(name, false, loader);
            Method measure = null;
            while (owner != null) {
                try {
                    measure = owner.getDeclaredMethod("onMeasure", int.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) { owner = owner.getSuperclass(); }
            }
            if (measure == null) throw new NoSuchMethodException(name + ".onMeasure");
            if (hooked.add(measure)) {
                module.hook(measure)
                        .setId("status-bar-network-margins-" + hooked.size())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (chain.getThisObject() instanceof ViewGroup root) {
                                String actual = root.getClass().getName();
                                if (MOBILE_VIEW.equals(actual) || SPEED_VIEW.equals(actual)) {
                                    ModuleSettings.ensureLoaded();
                                    apply(root, SPEED_VIEW.equals(actual));
                                }
                            }
                            return chain.proceed();
                        });
            }
            HookDiagnostics.available(SYSTEM_UI, feature);
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, feature, error);
        }
    }

    private static void apply(ViewGroup root, boolean speed) {
        State state;
        synchronized (VIEWS) {
            state = VIEWS.get(root);
            if (state == null) {
                state = new State(root);
                VIEWS.put(root, state);
            }
        }
        float left = speed ? ModuleSettings.statusBarNetworkSpeedMarginLeft
                           : ModuleSettings.statusBarMobileMarginLeft;
        float right = speed ? ModuleSettings.statusBarNetworkSpeedMarginRight
                            : ModuleSettings.statusBarMobileMarginRight;
        int[] padding = state.policy.resolve(root.getPaddingLeft(), root.getPaddingRight(), left, right,
                root.getResources().getDisplayMetrics().density);
        // The native speed right padding is zero on the reference ROM. Negative spacing must
        // let the text extend beyond the shrunken slot rather than cutting off its last glyph.
        if (speed) {
            boolean negative = padding[0] < 0 || padding[1] < 0;
            boolean children = !negative && state.clipChildren;
            boolean clipPadding = !negative && state.clipToPadding;
            if (root.getClipChildren() != children) root.setClipChildren(children);
            if (root.getClipToPadding() != clipPadding) root.setClipToPadding(clipPadding);
        }
        if (root.getPaddingLeft() == padding[0] && root.getPaddingRight() == padding[1]) return;
        if (root.isPaddingRelative()) {
            boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            root.setPaddingRelative(rtl ? padding[1] : padding[0], root.getPaddingTop(),
                    rtl ? padding[0] : padding[1], root.getPaddingBottom());
        } else {
            root.setPadding(padding[0], root.getPaddingTop(), padding[1], root.getPaddingBottom());
        }
        // The fallback width for an empty speed label includes padding; rebuild its cache.
        if (speed) ReflectiveAccess.setFieldValue(root, "mEmptyWidth", -1);
    }

    static void refresh() {
        ArrayList<ViewGroup> roots;
        synchronized (VIEWS) { roots = new ArrayList<>(VIEWS.keySet()); }
        for (ViewGroup root : roots) {
            if (root != null) root.post(() -> {
                apply(root, SPEED_VIEW.equals(root.getClass().getName()));
                root.requestLayout();
            });
        }
    }

    private static final class State {
        final StatusBarMarginPolicy policy;
        final boolean clipChildren;
        final boolean clipToPadding;

        State(ViewGroup root) {
            policy = new StatusBarMarginPolicy(root.getPaddingLeft(), root.getPaddingRight());
            clipChildren = root.getClipChildren();
            clipToPadding = root.getClipToPadding();
        }
    }
}
