package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.content.res.Configuration;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Three exact SplashActivity events for split only; immersion uses no method hooks. */
final class QQHostLifecycleHooks {
    private static boolean installed;

    static void install(XposedModule module, ClassLoader loader) {
        if (installed || !QQFoldSplitHooks.isInstalled()) return;
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>(3);
        try {
            Class<?> splash = Class.forName("com.tencent.mobileqq.activity.SplashActivity", false, loader);
            Method focus = splash.getDeclaredMethod("onWindowFocusChanged", boolean.class);
            Method configuration = splash.getDeclaredMethod("onConfigurationChanged", Configuration.class);
            Method destroy = splash.getDeclaredMethod("doOnDestroy");
            for (Method event : new Method[]{focus, configuration}) {
                handles.add(module.hook(event).setId("qq-fold-host-" + event.getName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            if (event != focus || Boolean.TRUE.equals(chain.getArg(0))) {
                                Activity activity = (Activity) chain.getThisObject();
                                try { QQFoldSplitHooks.onWindowEvent(activity); }
                                catch (ReflectiveOperationException | RuntimeException error) {
                                    HookDiagnostics.failure(ModuleScopePackage.QQ, "QQ 分栏窗口刷新", error);
                                }
                            }
                            return result;
                        }));
            }
            handles.add(module.hook(destroy).setId("qq-fold-host-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Activity activity = (Activity) chain.getThisObject();
                        try { QQFoldSplitHooks.onDestroy(activity); }
                        catch (ReflectiveOperationException | RuntimeException error) {
                            HookDiagnostics.failure(ModuleScopePackage.QQ, "QQ 分栏窗口清理", error);
                        }
                        return chain.proceed();
                    }));
            installed = true;
            module.log(Log.INFO, "MyHyperModifier", "QQ fold shared lifecycle installed");
        } catch (Throwable error) {
            for (var handle : handles) handle.unhook();
            HookDiagnostics.failure(ModuleScopePackage.QQ, "QQ 折叠屏窗口生命周期", error);
        }
    }
}
