package com.aritxonly.myhypermodifier;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Reuses the native stack's one-pass child measurement and centered layout. */
final class NotificationWidthHooks {
    private static final WeakHashMap<View, Boolean> STACKS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private static volatile long lastSignature;
    private static Field minimum, status, expanded;
    private NotificationWidthHooks() {}

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", false, loader);
        minimum = type.getDeclaredField("mMinimumPaddings");
        status = type.getDeclaredField("mStatusBarState");
        expanded = type.getDeclaredField("mIsExpanded");
        minimum.setAccessible(true);
        status.setAccessible(true);
        expanded.setAccessible(true);
        Method measure = type.getDeclaredMethod("onMeasure", int.class, int.class);
        module.hook(measure).setId("notification-max-width-measure")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View stack = (View) chain.getThisObject();
                    // Weak keys and constant values: no View/Context retained through map values.
                    STACKS.put(stack, Boolean.TRUE);
                    long config = signature();
                    if (config == 0) return chain.proceed();
                    int limit = NotificationWidthPolicy.selected(config, status.getInt(stack), expanded.getBoolean(stack));
                    if (limit == 0) return chain.proceed();
                    int spec = (int) chain.getArg(0);
                    if (View.MeasureSpec.getMode(spec) == View.MeasureSpec.UNSPECIFIED) return chain.proceed();
                    int original = minimum.getInt(stack);
                    int padding = NotificationWidthPolicy.configuredPadding(View.MeasureSpec.getSize(spec),
                            original, stack.getResources().getDisplayMetrics().density, limit);
                    if (padding == original) return chain.proceed();
                    minimum.setInt(stack, padding);
                    try {
                        // Native code measures all children once and stores effective mSidePaddings,
                        // which the native swipe bounds and background geometry also consume.
                        return chain.proceed();
                    } finally {
                        // The configured system padding is never persisted as our custom value.
                        if (minimum.getInt(stack) == padding) minimum.setInt(stack, original);
                    }
                });
        for (String name : new String[] {"setStatusBarState", "setIsExpanded"}) {
            Method change = type.getDeclaredMethod(name, name.equals("setIsExpanded") ? boolean.class : int.class);
            module.hook(change).setId("notification-max-width-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (signature() == 0) return chain.proceed();
                        View stack = (View) chain.getThisObject();
                        int before = active(stack);
                        Object result = chain.proceed();
                        if (before != active(stack)) stack.requestLayout();
                        return result;
                    });
        }
        lastSignature = signature();
    }

    private static int active(View stack) throws IllegalAccessException {
        return NotificationWidthPolicy.selected(signature(), status.getInt(stack), expanded.getBoolean(stack));
    }

    private static long signature() {
        if (!ModuleSettings.moduleHooksEnabled || ModuleSettings.systemUiCompatibilityMode) return 0;
        int shade = NotificationWidthPolicy.settingCode(ModuleSettings.notificationMaxWidthEnabled,
                ModuleSettings.notificationWidthMode, ModuleSettings.notificationMaxWidth, ModuleSettings.notificationSideMargin);
        int lock = NotificationWidthPolicy.settingCode(ModuleSettings.lockscreenNotificationMaxWidthEnabled,
                ModuleSettings.lockscreenNotificationWidthMode, ModuleSettings.lockscreenNotificationMaxWidth, ModuleSettings.lockscreenNotificationSideMargin);
        return ((long) shade << 32) | (lock & 0xffffffffL);
    }

    static void refresh() {
        if (lastSignature == signature() || !QUEUED.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            QUEUED.set(false);
            long now = signature();
            if (now == lastSignature) return;
            long previous = lastSignature;
            lastSignature = now;
            for (View stack : new ArrayList<>(STACKS.keySet())) {
                if (!stack.isAttachedToWindow()) continue;
                try {
                    int scene = status.getInt(stack);
                    boolean open = expanded.getBoolean(stack);
                    if (NotificationWidthPolicy.selected(previous, scene, open) != NotificationWidthPolicy.selected(now, scene, open)) stack.requestLayout();
                } catch (IllegalAccessException error) {
                    android.util.Log.w("MyHyperModifier", "Notification width refresh unavailable", error);
                }
            }
        });
    }
}
