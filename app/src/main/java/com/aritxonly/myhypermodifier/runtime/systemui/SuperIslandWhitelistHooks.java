package com.aritxonly.myhypermodifier;

import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Local Focus admission and XMS Focus-auth completion; no firewall/network mutations. */
final class SuperIslandWhitelistHooks {
    private static final Set<Method> INSTALLED = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean ADMITTED = new AtomicBoolean();
    private static final AtomicBoolean AUTHENTICATED = new AtomicBoolean();
    private SuperIslandWhitelistHooks() {}

    static void installSystemUi(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> manager = Class.forName("com.miui.systemui.notification.NotificationSettingsManager", false, loader);
        Method gate = manager.getDeclaredMethod("isInSupportBlockFocusXmsList", String.class);
        if (gate.getReturnType() != boolean.class) throw new NoSuchMethodException("Unexpected Focus whitelist return type");
        if (!INSTALLED.add(gate)) return;
        try {
            module.hook(gate).setId("super-island-whitelist")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    ModuleSettings.ensureLoaded();
                    if (!enabled()) return chain.proceed();
                    if (ADMITTED.compareAndSet(false, true))
                        Log.i("MyHyperModifier", "Super Island: bypassed local Focus whitelist; package=" + chain.getArg(0));
                    // The caller still evaluates its saved focus state, including explicit user disable.
                    return true;
                });
            Log.i("MyHyperModifier", "Installed Super Island local whitelist gate");
        } catch (Throwable error) { INSTALLED.remove(gate); throw error; }
    }

    static void installXmsf(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> session = Class.forName("com.xiaomi.xms.auth.AuthSession", false, loader);
        Class<?> error = Class.forName("com.xiaomi.xms.auth.AuthError", false, loader);
        FocusAuthMethods methods = FocusAuthMethods.find(session, error, Bundle.class);
        if (!INSTALLED.add(methods.failure)) return;
        try {
            module.hook(methods.failure).setId("super-island-focus-auth")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    ModuleSettings.ensureLoaded();
                    if (!enabled() || chain.getArg(0) == null) return chain.proceed();
                    Object result = methods.success.invoke(chain.getThisObject());
                    if (AUTHENTICATED.compareAndSet(false, true))
                        Log.i("MyHyperModifier", "Super Island: completed XMS Focus authentication via " + methods.success.getName());
                    return result;
                });
            Log.i("MyHyperModifier", "Installed XMS Focus auth gate: " + methods.failure + " -> " + methods.success);
        } catch (Throwable failure) { INSTALLED.remove(methods.failure); throw failure; }
    }
    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && ModuleSettings.superIslandWhitelistDisabled;
    }
}
