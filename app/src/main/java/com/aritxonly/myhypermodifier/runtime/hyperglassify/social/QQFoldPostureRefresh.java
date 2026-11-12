package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Xiaomi posture notification, not a poll. Re-run QQ's own predicates after the setting changes. */
final class QQFoldPostureRefresh {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static WeakReference<ContentResolver> resolver = new WeakReference<>(null);
    private static Method orientation, configuration;
    private static boolean registered, queued, postureChanged;
    private static final Runnable REFRESH = () -> {
        queued = false;
        boolean changed = postureChanged;
        postureChanged = false;
        Activity activity = owner.get();
        if (!enabled() || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            orientation.invoke(null, activity);
            if (changed) configuration.invoke(null, activity.getResources().getConfiguration(), activity, true);
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure(ModuleScopePackage.QQ, "QQ 原生折叠状态刷新", error);
        }
    };
    private static final ContentObserver OBSERVER = new ContentObserver(MAIN) {
        @Override public void onChange(boolean selfChange) { enqueue(true); }
    };

    static void prepare(Method nativeOrientation, Method nativeConfiguration) {
        orientation = nativeOrientation;
        configuration = nativeConfiguration;
    }

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && ModuleSettings.qqFoldSplitRatioEnabled;
    }

    static void attach(Activity activity) {
        if (!enabled() || !"Xiaomi".equalsIgnoreCase(Build.MANUFACTURER) || orientation == null) return;
        if (owner.get() == activity) return;
        detach();
        owner = new WeakReference<>(activity);
        ContentResolver target = activity.getApplication().getContentResolver();
        try {
            target.registerContentObserver(Settings.Global.getUriFor("device_posture"), false, OBSERVER);
            resolver = new WeakReference<>(target);
            registered = true;
            // One startup replay handles an orientation decision made before the final posture.
            // Do not replay the split transition here: preserve an existing native full-screen page.
            enqueue(false);
        } catch (RuntimeException error) {
            HookDiagnostics.failure(ModuleScopePackage.QQ, "QQ 折叠状态订阅", error);
        }
    }

    private static void enqueue(boolean changed) {
        if (!registered || !enabled()) return;
        postureChanged |= changed;
        if (queued) return;
        queued = true;
        MAIN.post(REFRESH);
    }

    static void detach(Activity activity) {
        if (owner.get() == activity) detach();
    }

    static void detach() {
        MAIN.removeCallbacks(REFRESH);
        queued = postureChanged = false;
        ContentResolver target = resolver.get();
        if (registered && target != null) target.unregisterContentObserver(OBSERVER);
        registered = false;
        owner.clear();
        resolver.clear();
    }
}
