package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Collapses only the heads-up mini-window hint, leaving the row's slide gesture untouched. */
final class HeadsUpMiniBarHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String ROW_INJECTOR =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean REFRESH_REGISTERED = new AtomicBoolean();
    private static final Map<View, BarAppearance> TRACKED_BARS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private HeadsUpMiniBarHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> injector = Class.forName(ROW_INJECTOR, false, classLoader);
            Method getMiniBar = injector.getDeclaredMethod("getMiniBar");
            module.hook(getMiniBar)
                    .setId("heads-up-mini-window-bar-appearance")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (result instanceof View) {
                            ModuleSettings.ensureLoaded();
                            applyAppearance((View) result);
                        }
                        return result;
                    });
            if (REFRESH_REGISTERED.compareAndSet(false, true)) {
                ModuleSettings.onLoaded(HeadsUpMiniBarHooks::refreshTrackedBars);
            }
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.WARN, TAG, "Heads-up mini-window bar hook unavailable", throwable);
        }
    }

    private static void applyAppearance(View bar) {
        synchronized (TRACKED_BARS) {
            BarAppearance appearance = TRACKED_BARS.get(bar);
            if (appearance == null) {
                appearance = new BarAppearance();
                TRACKED_BARS.put(bar, appearance);
            }
            if (ModuleSettings.hideHeadsUpMiniBar) {
                if (!appearance.hidden) {
                    appearance.originalAlpha = bar.getAlpha();
                    appearance.hidden = true;
                }
                if (bar.getAlpha() != 0f) bar.setAlpha(0f);
                ViewGroup.LayoutParams params = bar.getLayoutParams();
                if (params != null) {
                    if (!appearance.hasOriginalHeight) {
                        appearance.originalHeight = params.height;
                        appearance.hasOriginalHeight = true;
                    }
                    if (params.height != 0) {
                        params.height = 0;
                        bar.setLayoutParams(params);
                    }
                }
            } else if (appearance.hidden) {
                bar.setAlpha(appearance.originalAlpha);
                ViewGroup.LayoutParams params = bar.getLayoutParams();
                if (params != null && appearance.hasOriginalHeight
                        && params.height != appearance.originalHeight) {
                    params.height = appearance.originalHeight;
                    bar.setLayoutParams(params);
                }
                appearance.hidden = false;
                appearance.hasOriginalHeight = false;
            }
        }
    }

    private static void refreshTrackedBars() {
        ArrayList<View> bars;
        synchronized (TRACKED_BARS) {
            bars = new ArrayList<>(TRACKED_BARS.keySet());
        }
        for (View bar : bars) {
            if (bar != null) bar.post(() -> applyAppearance(bar));
        }
    }

    private static final class BarAppearance {
        float originalAlpha;
        int originalHeight;
        boolean hidden;
        boolean hasOriginalHeight;
    }
}
