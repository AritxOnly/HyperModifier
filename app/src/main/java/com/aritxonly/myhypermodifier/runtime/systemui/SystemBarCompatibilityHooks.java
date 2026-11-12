package com.aritxonly.myhypermodifier;

import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Lets DisplayPolicy use transparent bars over compatibility/letterboxed app windows. */
final class SystemBarCompatibilityHooks {
    static final String KEY = "system_bar_compatibility_scrim_disabled";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final WeakHashMap<Object, Refresh> POLICIES = new WeakHashMap<>();
    private static volatile boolean enabled;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    static void install(XposedModule module, ClassLoader loader, SharedPreferences preferences) throws Throwable {
        if (!INSTALLED.compareAndSet(false, true)) return;
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            Class<?> policy = Class.forName("com.android.server.wm.DisplayPolicy", false, loader);
            Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
            Method decision = policy.getDeclaredMethod("isFullyTransparentAllowed", window, int.class);
            if (decision.getReturnType() != boolean.class) throw new NoSuchMethodException(decision.toString());
            Method update = policy.getDeclaredMethod("updateSystemBarAttributes");
            Method status = policy.getDeclaredMethod("configureStatusBarOpacity", int.class);
            Method nav = policy.getDeclaredMethod("configureNavBarOpacity", int.class, boolean.class, boolean.class);
            Field lock = policy.getDeclaredField("mLock");
            Field handler = policy.getDeclaredField("mHandler");
            update.setAccessible(true);
            lock.setAccessible(true);
            handler.setAccessible(true);
            enabled = enabled(preferences);
            handles.add(module.hook(decision).setId("system-bar-compatibility-transparency")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        // No settings/IPC, reflection lookup, allocation or locks on this WM path.
                        return enabled ? Boolean.TRUE : chain.proceed();
                    }));
            for (var constructor : policy.getDeclaredConstructors()) {
                handles.add(module.hook(constructor).setId("system-bar-compatibility-policy")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            Object target = chain.getThisObject();
                            synchronized (POLICIES) {
                                POLICIES.put(target, new Refresh(target, (Handler) handler.get(target), lock, update));
                            }
                            return result;
                        }));
            }
            // Both native methods can inline the small compatibility decision.
            module.deoptimize(status);
            module.deoptimize(nav);
            listener = (prefs, key) -> {
                if (key != null && !KEY.equals(key) && !"module_hooks_enabled".equals(key)) return;
                boolean next = enabled(prefs);
                if (enabled == next) return;
                enabled = next;
                ArrayList<Refresh> targets;
                synchronized (POLICIES) { targets = new ArrayList<>(POLICIES.values()); }
                for (Refresh refresh : targets) refresh.schedule();
            };
            preferences.registerOnSharedPreferenceChangeListener(listener);
            // Close the install/listener-registration gap without replaying unchanged state.
            listener.onSharedPreferenceChanged(preferences, KEY);
            module.log(Log.INFO, "MyHyperModifier", "System bar compatibility transparency installed");
        } catch (Throwable error) {
            for (var handle : handles) handle.unhook();
            enabled = false;
            INSTALLED.set(false);
            throw error;
        }
    }

    private static boolean enabled(SharedPreferences preferences) {
        return preferences.getBoolean("module_hooks_enabled", true)
                && preferences.getBoolean(KEY, false);
    }

    private static final class Refresh implements Runnable {
        final WeakReference<Object> policy;
        final WeakReference<Handler> handler;
        final Field lock;
        final Method update;
        final AtomicBoolean queued = new AtomicBoolean();

        Refresh(Object policy, Handler handler, Field lock, Method update) {
            this.policy = new WeakReference<>(policy);
            this.handler = new WeakReference<>(handler);
            this.lock = lock;
            this.update = update;
        }

        void schedule() {
            Handler target = handler.get();
            if (target != null && queued.compareAndSet(false, true) && !target.post(this)) queued.set(false);
        }

        @Override public void run() {
            queued.set(false);
            Object target = policy.get();
            if (target == null) return;
            try {
                // The normal WM update expects its existing global lock.
                synchronized (lock.get(target)) { update.invoke(target); }
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.w("MyHyperModifier", "System bar compatibility refresh failed", error);
            }
        }
    }

    private SystemBarCompatibilityHooks() {}
}
