package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import io.github.libxposed.api.XposedModule;

/** Enable QQ's own common-scene inset/blur treatment. No Window or rendering hook. */
final class QQGestureImmersionHooks {
    private static final String FEATURE = "QQ 折叠屏原生底部安全区";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean attempted, installed;
    private static QQImmersionSceneOverride scene;
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static final Runnable RECREATE = () -> {
        Activity activity = owner.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && !activity.isChangingConfigurations()) activity.recreate();
    };

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && ModuleSettings.qqFoldNavigationImmersionEnabled;
    }

    static void refresh(XposedModule module, ClassLoader loader, Activity activity) {
        refresh(module, loader, activity, true);
    }

    static void refresh(XposedModule module, ClassLoader loader, Activity activity, boolean created) {
        if (activity != null) owner = new WeakReference<>(activity);
        if (enabled() && !attempted && activity != null) install(module, loader, activity);
        if (!installed) return;
        try {
            boolean changed = enabled() ? scene.enable() : scene.restore();
            // Existing native views must register their inset/blur treatment during creation.
            // Rebuild only this Activity on a relevant toggle, never replay arbitrary QQ UI.
            if (changed && created) {
                if (activity == null) {
                    owner = new WeakReference<>(QQActivityLookup.currentSplash());
                }
                MAIN.removeCallbacks(RECREATE);
                MAIN.post(RECREATE);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure(ModuleScopePackage.QQ, FEATURE, error);
        }
    }

    private static void install(XposedModule module, ClassLoader loader, Activity activity) {
        attempted = true;
        try {
            Application app = activity.getApplication();
            if (app == null) throw new IllegalStateException("QQ Application 尚未就绪");
            var info = app.getPackageManager().getPackageInfo(ModuleScopePackage.QQ, 0);
            if (!"9.3.75".equals(info.versionName) || info.getLongVersionCode() != 16580L) {
                throw new IllegalStateException("仅适配 QQ 9.3.75 (16580)");
            }
            if (LargeScreenDevicePolicy.detectedMode() != LargeScreenDevicePolicy.FOLDABLE) return;
            Class<?> helper = Class.forName("com.tencent.mobileqq.qui.QUINavigationBarImmersiveHelper", false, loader);
            Field scenes = helper.getDeclaredField("b");
            scenes.setAccessible(true);
            Class<?> lazy = Class.forName("kotlin.Lazy", false, loader);
            Object sceneLazy = scenes.get(null);
            Object entries = lazy.getMethod("getValue").invoke(sceneLazy);
            if (!(entries instanceof List<?>)) throw new IllegalStateException("QQ 沉浸场景结构不匹配");
            scene = new QQImmersionSceneOverride(sceneLazy,
                    Class.forName("kotlin.Pair", false, loader));
            installed = true;
            module.log(Log.INFO, "MyHyperModifier", "QQ native safe-area scene adapter ready (no immersion hooks)");
            HookDiagnostics.available(ModuleScopePackage.QQ, FEATURE);
        } catch (Throwable error) {
            HookDiagnostics.failure(ModuleScopePackage.QQ, FEATURE, error);
        }
    }

}
