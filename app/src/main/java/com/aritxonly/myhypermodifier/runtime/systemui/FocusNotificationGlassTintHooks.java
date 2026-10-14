package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.view.View;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Only focus/live-update notification materials; ordinary notification rows stay unchanged. */
final class FocusNotificationGlassTintHooks {
    private static volatile boolean complete;
    private static final Set<Method> INSTALLED = ConcurrentHashMap.newKeySet();

    static synchronized void install(XposedModule module, ClassLoader loader) {
        if (ModuleSettings.disableShadeGlassHooks || complete) return;
        DynamicIslandGlassTint.install();
        try {
            Method injector = Class.forName("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", false, loader)
                    .getMethod("getInjector");
            Method background = injector.getReturnType().getMethod("getBackgroundNormal");
            boolean allInstalled = true;
            for (String suffix : new String[]{"", "OnKeyguard", "OnKeyguardLightWallPaper", "FullAod"}) {
                String name = "com.android.systemui.statusbar.notification.style.vieweffect.FocusNotificationGlass" + suffix + "Effect";
                try {
                    Class<?> effect = Class.forName(name, false, loader);
                    hook(module, effect.getDeclaredMethod("clear", Object.class), injector, background, false);
                    hook(module, effect.getDeclaredMethod("apply", Object.class, Context.class), injector, background, true);
                } catch (Throwable error) {
                    allInstalled = false;
                    HookDiagnostics.failure("com.android.systemui", "通知中心超级岛不透明度" + suffix, error);
                }
            }
            complete = allInstalled;
        } catch (Throwable error) {
            HookDiagnostics.failure("com.android.systemui", "通知中心超级岛不透明度", error);
        }
    }

    private static void hook(XposedModule module, Method method, Method injector, Method background, boolean applying) throws Throwable {
        if (!INSTALLED.add(method)) return;
        try {
            module.hook(method).setId("focus-island-tint-" + method.getDeclaringClass().getSimpleName() + "-" + method.getName())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!ModuleSettings.disableShadeGlassHooks) {
                            Object owner = chain.getArg(0);
                            Object bridge = owner == null ? null : injector.invoke(owner);
                            Object target = bridge == null ? null : background.invoke(bridge);
                            if (target instanceof View) {
                                if (applying) DynamicIslandGlassTint.register((View) target, true);
                                else DynamicIslandGlassTint.forget((View) target);
                            }
                        }
                        return chain.proceed();
                    });
            if (applying) HookDiagnostics.available("com.android.systemui", "通知中心超级岛不透明度" + method.getDeclaringClass().getSimpleName());
        } catch (Throwable error) {
            INSTALLED.remove(method);
            throw error;
        }
    }
}
