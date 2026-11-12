package com.aritxonly.myhypermodifier;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Scales the stock status-bar battery icon and external charging mark around their centers. */
final class StatusBarBatteryIconSizeHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Binding> VIEWS = new WeakHashMap<>();
    private static Field digitalView, chargingView;

    private StatusBarBatteryIconSizeHooks() {}

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode;
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        Class<?> type = Class.forName(
                SYSTEM_UI + ".statusbar.views.MiuiBatteryMeterView", false, loader);
        digitalView = type.getField("mBatteryDigitalView");
        chargingView = type.getField("mBatteryChargingView");
        Method attach = type.getDeclaredMethod("onAttachedToWindow");
        Method layout = type.getDeclaredMethod("onLayout", boolean.class,
                int.class, int.class, int.class, int.class);
        Method detach = type.getDeclaredMethod("onDetachedFromWindow");
        module.hook(attach).setId("status-bar-battery-icon-size-attach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    ModuleSettings.ensureLoaded();
                    apply(view, remember(view));
                    return result;
                });
        module.hook(layout).setId("status-bar-battery-icon-size-layout")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    Binding binding = VIEWS.get(view);
                    if (binding != null) apply(view, binding);
                    return result;
                });
        module.hook(detach).setId("status-bar-battery-icon-size-detach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View view = (View) chain.getThisObject();
                    Binding binding = VIEWS.remove(view);
                    if (binding != null) restore(view, binding);
                    return chain.proceed();
                });
        HookDiagnostics.available(SYSTEM_UI, "状态栏电池大小");
    }

    private static Binding remember(View view) {
        Binding binding = VIEWS.get(view);
        if (binding == null) {
            binding = new Binding(view);
            VIEWS.put(view, binding);
        }
        return binding;
    }

    private static void apply(View view, Binding binding) {
        if (binding.failed) return;
        try {
            float scale = StatusBarBatteryIconSizePolicy.normalize(
                    ModuleSettings.statusBarBatteryIconScale);
            if (!enabled() || scale == StatusBarBatteryIconSizePolicy.DEFAULT) {
                restore(view, binding);
                return;
            }
            ViewGroup group = (ViewGroup) view;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            scale((View) digitalView.get(view), binding.originalDigitalScaleX,
                    binding.originalDigitalScaleY, scale);
            scale((View) chargingView.get(view), binding.originalChargingScaleX,
                    binding.originalChargingScaleY, scale);
            binding.applied = true;
        } catch (Throwable error) {
            binding.failed = true;
            HookDiagnostics.failure(SYSTEM_UI, "状态栏电池大小", error);
        }
    }

    private static void scale(View view, float originalScaleX, float originalScaleY, float scale) {
        if (view == null) return;
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(view.getHeight() / 2f);
        view.setScaleX(StatusBarBatteryIconSizePolicy.scaledValue(originalScaleX, scale));
        view.setScaleY(StatusBarBatteryIconSizePolicy.scaledValue(originalScaleY, scale));
        view.invalidate();
    }

    private static void restore(View view, Binding binding) {
        if (!binding.applied) return;
        try {
            View digital = (View) digitalView.get(view);
            View charging = (View) chargingView.get(view);
            restoreScale(digital, binding.originalDigitalScaleX, binding.originalDigitalScaleY);
            restoreScale(charging, binding.originalChargingScaleX, binding.originalChargingScaleY);
            ViewGroup group = (ViewGroup) view;
            group.setClipChildren(binding.originalClipChildren);
            group.setClipToPadding(binding.originalClipToPadding);
            binding.applied = false;
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "状态栏电池大小", error);
        }
    }

    private static void restoreScale(View view, float scaleX, float scaleY) {
        if (view == null) return;
        view.setScaleX(scaleX);
        view.setScaleY(scaleY);
        view.invalidate();
    }

    static void refresh() {
        if (!PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            PENDING.set(false);
            for (var entry : new ArrayList<>(VIEWS.entrySet())) {
                View view = entry.getKey();
                if (view != null) apply(view, entry.getValue());
            }
        });
    }

    private static final class Binding {
        final float originalDigitalScaleX;
        final float originalDigitalScaleY;
        final float originalChargingScaleX;
        final float originalChargingScaleY;
        final boolean originalClipChildren;
        final boolean originalClipToPadding;
        boolean applied;
        boolean failed;

        Binding(View view) {
            View digital = null;
            View charging = null;
            try {
                digital = (View) digitalView.get(view);
                charging = (View) chargingView.get(view);
            } catch (IllegalAccessException ignored) {
                // Fields are resolved before hooks are installed; keep stock values on failure.
            }
            originalDigitalScaleX = digital == null ? 1f : digital.getScaleX();
            originalDigitalScaleY = digital == null ? 1f : digital.getScaleY();
            originalChargingScaleX = charging == null ? 1f : charging.getScaleX();
            originalChargingScaleY = charging == null ? 1f : charging.getScaleY();
            ViewGroup group = (ViewGroup) view;
            originalClipChildren = group.getClipChildren();
            originalClipToPadding = group.getClipToPadding();
        }
    }
}
