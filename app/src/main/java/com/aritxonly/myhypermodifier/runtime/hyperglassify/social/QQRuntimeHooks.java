package com.aritxonly.myhypermodifier;

import android.content.SharedPreferences;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.lang.ref.WeakReference;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

/** QQ main-process bootstrap. A split-only opt-in must not install navigation/touch hooks. */
final class QQRuntimeHooks {
    private static XposedModule module;
    private static ClassLoader loader;
    private static boolean observing, waitingForActivity, reportedHost;
    private static WeakReference<Application> startupOwner = new WeakReference<>(null);
    private static final Application.ActivityLifecycleCallbacks STARTUP = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityPreCreated(Activity activity, Bundle state) {
            if (isSplash(activity) && wantsHost()) bind(activity, false);
        }
        @Override public void onActivityPostCreated(Activity activity, Bundle state) {
            if (!isSplash(activity)) return;
            stopWaiting();
            if (wantsHost()) bind(activity, true);
        }
        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    };
    private static boolean lastMaster, lastNavigation, lastSplit, lastImmersion, seen;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable REFRESH = QQRuntimeHooks::refresh;

    static synchronized void install(XposedModule target, ClassLoader classLoader, SharedPreferences preferences) {
        module = target;
        loader = classLoader;
        // These early reads belong only to QQ, not to every module target process.
        if (!ModuleSettings.isLoaded() && preferences != null) {
            ModuleSettings.moduleHooksEnabled = preferences.getBoolean("module_hooks_enabled", true);
            ModuleSettings.qqFloatingNavigationEnabled = preferences.getBoolean(
                    "qq_floating_navigation_enabled", ModuleDefaultValues.QQ_FLOATING_NAVIGATION_ENABLED);
            ModuleSettings.qqFoldSplitRatioEnabled = preferences.getBoolean("qq_fold_split_ratio_enabled", false);
            ModuleSettings.qqFoldNavigationImmersionEnabled = preferences.getBoolean(
                    "qq_fold_navigation_immersion_enabled", false);
        }
        // Preserve the existing early bottom-bar attachment, but only when it is requested.
        if (ModuleSettings.moduleHooksEnabled && ModuleSettings.qqFloatingNavigationEnabled) {
            SocialHooks.install(target, classLoader, ModuleScopePackage.QQ);
        }
        if (!observing) {
            observing = true;
            ModuleSettings.onChanged(QQRuntimeHooks::settingsChanged);
        }
        ModuleSettings.loadRemoteAsync();
        // Arm pre-create binding from the framework snapshot before the asynchronous full load.
        if (ModuleSettings.isLoaded() || preferences != null) settingsChanged();
    }

    private static synchronized void settingsChanged() {
        boolean master = ModuleSettings.moduleHooksEnabled;
        boolean navigation = ModuleSettings.qqFloatingNavigationEnabled;
        boolean split = ModuleSettings.qqFoldSplitRatioEnabled;
        boolean immersion = ModuleSettings.qqFoldNavigationImmersionEnabled;
        if (seen && master == lastMaster && navigation == lastNavigation && split == lastSplit
                && immersion == lastImmersion) return;
        seen = true;
        lastMaster = master;
        lastNavigation = navigation;
        lastSplit = split;
        lastImmersion = immersion;
        MAIN.removeCallbacks(REFRESH);
        MAIN.post(REFRESH);
    }

    private static void refresh() {
        module.log(Log.INFO, "MyHyperModifier", "QQ settings ready: master="
                + ModuleSettings.moduleHooksEnabled + ", split=" + ModuleSettings.qqFoldSplitRatioEnabled
                + ", navigation=" + ModuleSettings.qqFloatingNavigationEnabled
                + ", immersion=" + ModuleSettings.qqFoldNavigationImmersionEnabled
                + ", deviceMode=" + LargeScreenDevicePolicy.currentMode());
        if (ModuleSettings.moduleHooksEnabled && ModuleSettings.qqFloatingNavigationEnabled) {
            SocialHooks.install(module, loader, ModuleScopePackage.QQ);
        }
        if (!wantsHost()) {
            stopWaiting();
            QQFoldSplitHooks.refresh(module, loader, null);
            QQGestureImmersionHooks.refresh(module, loader, null);
            return;
        }
        try {
            Activity activity = QQActivityLookup.currentSplash();
            if (activity != null) {
                stopWaiting();
                bind(activity, true);
            } else if (!waitingForActivity) {
                Application app = (Application) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                if (app != null) {
                    startupOwner = new WeakReference<>(app);
                    app.registerActivityLifecycleCallbacks(STARTUP);
                    waitingForActivity = true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure(ModuleScopePackage.QQ, "QQ 主页绑定", error);
        }
    }
    private static boolean isSplash(Activity activity) {
        return "com.tencent.mobileqq.activity.SplashActivity".equals(activity.getClass().getName());
    }

    private static boolean wantsHost() {
        return ModuleSettings.moduleHooksEnabled && (ModuleSettings.qqFoldSplitRatioEnabled
                || ModuleSettings.qqFoldNavigationImmersionEnabled);
    }

    private static void stopWaiting() {
        if (!waitingForActivity) return;
        Application app = startupOwner.get();
        if (app != null) app.unregisterActivityLifecycleCallbacks(STARTUP);
        waitingForActivity = false;
        startupOwner.clear();
    }

    private static void bind(Activity activity, boolean created) {
        ClassLoader actualLoader = activity.getClass().getClassLoader();
        if (!reportedHost) {
            reportedHost = true;
            module.log(Log.INFO, "MyHyperModifier", "QQ actual Activity bound: "
                    + actualLoader.getClass().getName());
        }
        QQFoldSplitHooks.refresh(module, actualLoader, activity, created);
        QQGestureImmersionHooks.refresh(module, actualLoader, activity, created);
        QQHostLifecycleHooks.install(module, actualLoader);
    }

}
