package com.aritxonly.myhypermodifier;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Restores HyperOS' optional network-generation label (for example, 5G) in the status bar.
 *
 * The visibility decision lives in a Kotlin coroutine lambda while the label's presentation is
 * owned by MobileSignalAnimatorTextView. Keeping the two hooks separate means that a ROM which
 * changes either internal class simply falls back to stock behavior.
 */
final class StatusBarNetworkType {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String VISIBILITY_LAMBDA =
            "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel."
                    + "MiuiCellularIconVM$mobileTypeSingleVisible$2";
    private static final String TYPE_TEXT_VIEW =
            "com.android.systemui.statusbar.views.MobileSignalAnimatorTextView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private StatusBarNetworkType() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installVisibilityHook(module, classLoader);
        installTextStyleHook(module, classLoader);
    }

    private static void installVisibilityHook(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> lambda = Class.forName(VISIBILITY_LAMBDA, false, classLoader);
            Method method = lambda.getDeclaredMethod("invokeSuspend", Object.class);
            module.hook(method)
                    .setId("status-bar-network-type-visible")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ModuleSettings.ensureLoaded();
                        Object result = chain.proceed();
                        return ModuleSettings.statusBarNetworkTypeEnabled && result instanceof Boolean
                                && !((Boolean) result) ? true : result;
                    });
        } catch (Throwable ignored) {
            // Internal pipelines change across HyperOS releases; stock visibility is safe.
        }
    }

    private static void installTextStyleHook(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> textViewClass = Class.forName(TYPE_TEXT_VIEW, false, classLoader);
            Method attached = findDeclaredMethod(textViewClass, "onAttachedToWindow");
            if (attached == null) return;
            module.hook(attached)
                    .setId("status-bar-network-type-style")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object view = chain.getThisObject();
                        if (view instanceof TextView) apply((TextView) view);
                        return result;
                    });
        } catch (Throwable ignored) {
            // The visibility hook remains useful on ROMs that use a different text view class.
        }
    }

    private static void apply(TextView view) {
        ModuleSettings.ensureLoaded();
        if (!ModuleSettings.statusBarNetworkTypeEnabled
                || view.getId() != view.getResources().getIdentifier(
                "mobile_type_single", "id", SYSTEM_UI)) {
            return;
        }
        float density = view.getResources().getDisplayMetrics().density;
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, ModuleSettings.statusBarNetworkTypeSize);
        view.setTypeface(ModuleSettings.statusBarNetworkTypeBold
                ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setTranslationX(ModuleSettings.statusBarNetworkTypeOffset * density);
        view.setVisibility(View.VISIBLE);
    }

    private static Method findDeclaredMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
