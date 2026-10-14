package com.aritxonly.myhypermodifier;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Synchronizes the real DisplayPolicy cache, including callers with an inlined getter. */
final class GestureHandleAppTransitionHooks {
    private static final String TAG = "GestureHandleTransition";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    // SharedPreferences implementations may keep only weak listener references.
    private static SharedPreferences.OnSharedPreferenceChangeListener settingsListener;

    static void install(XposedModule module, ClassLoader loader, SharedPreferences prefs) throws Throwable {
        if (prefs == null || !prefs.getBoolean("module_hooks_enabled", true)
                || !prefs.getBoolean("gesture_handle_enabled", false)
                || !prefs.getBoolean("gesture_handle_attach_to_app_transition", false)) return;
        if (!INSTALLED.compareAndSet(false, true)) return;
        Class<?> policy = Class.forName("com.android.server.wm.DisplayPolicy", false, loader);
        Method decision = policy.getDeclaredMethod("shouldAttachNavBarToAppDuringTransition");
        Method resources = policy.getDeclaredMethod("getCurrentUserResources");
        Method configuration = policy.getDeclaredMethod("onConfigurationChanged");
        Field flag = policy.getDeclaredField("mShouldAttachNavBarToAppDuringTransition");
        Field lock = policy.getDeclaredField("mLock");
        if (decision.getReturnType() != boolean.class || flag.getType() != boolean.class) {
            throw new NoSuchMethodException("Unexpected DisplayPolicy transition decision signature");
        }
        flag.setAccessible(true);
        lock.setAccessible(true);
        resources.setAccessible(true);
        Set<Object> policies = Collections.newSetFromMap(new WeakHashMap<>());
        AtomicBoolean failureLogged = new AtomicBoolean();
        AtomicBoolean appliedLogged = new AtomicBoolean();
        java.util.function.Consumer<Object> sync = target -> {
            try {
                Object policyLock = lock.get(target);
                if (policyLock == null) return;
                synchronized (policyLock) {
                    Resources current = (Resources) resources.invoke(target);
                    int configId = current.getIdentifier("config_attachNavBarToAppDuringTransition", "bool", "android");
                    int modeId = current.getIdentifier("config_navBarInteractionMode", "integer", "android");
                    if (configId == 0 || modeId == 0) {
                        throw new IllegalStateException("Missing navigation attachment/mode resource");
                    }
                    boolean enabled = prefs != null && prefs.getBoolean("module_hooks_enabled", true)
                            && prefs.getBoolean("gesture_handle_enabled", false)
                            && prefs.getBoolean("gesture_handle_attach_to_app_transition", false);
                    int mode = current.getInteger(modeId);
                    boolean stock = current.getBoolean(configId);
                    boolean desired = GestureHandleTransitionOverride.resolve(stock, mode, enabled);
                    flag.setBoolean(target, desired);
                    if (enabled && appliedLogged.compareAndSet(false, true)) {
                        module.log(Log.INFO, TAG, "Applied: navigationMode=" + mode
                                + ", overlay=" + stock + ", cached=" + desired);
                    }
                }
                synchronized (policies) { policies.add(target); }
            } catch (Throwable error) {
                if (failureLogged.compareAndSet(false, true)) {
                    module.log(Log.ERROR, TAG, "Could not synchronize navigation attachment", error);
                }
            }
        };
        module.hook(configuration).setId("gesture-handle-transition-config")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    sync.accept(chain.getThisObject());
                    return result;
                });
        for (Constructor<?> constructor : policy.getDeclaredConstructors()) {
            module.hook(constructor).setId("gesture-handle-transition-policy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        sync.accept(chain.getThisObject());
                        return result;
                    });
        }
        settingsListener = (preferences, key) -> {
            if (key != null && !key.equals("module_hooks_enabled")
                    && !key.equals("gesture_handle_enabled")
                    && !key.equals("gesture_handle_attach_to_app_transition")) return;
            ArrayList<Object> snapshot;
            synchronized (policies) { snapshot = new ArrayList<>(policies); }
            for (Object target : snapshot) sync.accept(target);
            module.log(Log.INFO, TAG, "Settings refreshed; policies=" + snapshot.size());
        };
        if (prefs != null) {
            try { prefs.registerOnSharedPreferenceChangeListener(settingsListener); }
            catch (RuntimeException error) {
                module.log(Log.WARN, TAG, "Remote settings listener unavailable; restart required after preference changes", error);
            }
        }
        // The real cache is already updated above. No getter hook or broad caller deoptimization.
        module.log(Log.INFO, TAG, "Installed cache synchronization in system_server");
    }
}
