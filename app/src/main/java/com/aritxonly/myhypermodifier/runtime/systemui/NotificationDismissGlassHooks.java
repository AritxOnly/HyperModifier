package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Supplement only the notification dismiss button's existing Glass effect lifecycle. */
final class NotificationDismissGlassHooks {
    private static final String EFFECT =
            "com.android.systemui.statusbar.notification.style.vieweffect.NotificationDismissViewGlassEffect";
    private static final String PACKAGE = "com.android.systemui";
    private static final String FEATURE = "通知清除按钮 Glass 半径";
    private static final AtomicBoolean APPLY_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean CLEAR_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MATCH_LOGGED = new AtomicBoolean();

    private NotificationDismissGlassHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (ModuleSettings.disableShadeGlassHooks) return;
        try {
            Class<?> effect = Class.forName(EFFECT, false, loader);
            // Clear must be available before supplementing apply, to preserve lifecycle cleanup.
            installMethod(module, effect.getDeclaredMethod("clear", Object.class), CLEAR_INSTALLED, false);
            installMethod(module, effect.getDeclaredMethod("apply", Object.class, Context.class), APPLY_INSTALLED, true);
            HookDiagnostics.available(PACKAGE, FEATURE);
        } catch (Throwable error) {
            HookDiagnostics.failure(PACKAGE, FEATURE, error);
            Log.w("MyHyperModifier", "Notification dismiss Glass lifecycle unavailable", error);
        }
    }

    private static void installMethod(XposedModule module, Method method, AtomicBoolean installed, boolean applying) throws Throwable {
        if (!installed.compareAndSet(false, true)) return;
        try {
            module.hook(method).setId(applying ? "notification-dismiss-glass-apply" : "notification-dismiss-glass-clear")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getArg(0);
                        if (!(target instanceof View)) return result;
                        if (applying) {
                            boolean applied = GlobalGlassBlurHooks.onMaterialApplied((View) target);
                            if (applied && ModuleSettings.moduleHooksEnabled && !ModuleSettings.disableShadeGlassHooks
                                    && ModuleSettings.globalGlassBlurEnabled && MATCH_LOGGED.compareAndSet(false, true)) {
                                Log.i("MyHyperModifier", "Notification dismiss Glass matched: radius="
                                        + ModuleSettings.shadeCardGlassBlurRadius);
                            }
                        } else {
                            GlobalGlassBlurHooks.onMaterialCleared((View) target);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            installed.set(false);
            throw error;
        }
    }
}
