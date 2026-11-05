package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** NIO is packed: filter framework lifecycle callbacks without resolving encrypted app classes. */
final class NioHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final String HOME = "cn.com.weilaihui3.app.ui.activity.HomeActivity";
    private NioHooks() {}

    static void install(XposedModule module) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            InjectedNavigationContext.initialize(module.getModuleApplicationInfo().sourceDir);
            // Framework PhoneWindow is available before the packed app classes are loaded.
            Class<?> phoneWindow = Class.forName("com.android.internal.policy.PhoneWindow");
            module.hook(phoneWindow.getDeclaredMethod("setDecorFitsSystemWindows", boolean.class))
                    .setId("nio-navigation-decor-fits")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        boolean requested = (boolean) chain.getArg(0);
                        boolean effective = NioEdgeToEdge.nativeDecorFits(
                                (android.view.Window) chain.getThisObject(), requested);
                        return requested == effective ? chain.proceed()
                                : chain.proceed(new Object[]{effective});
                    });
            for (String event : new String[]{"onResume", "onPause", "onDestroy"}) {
                module.hook(Activity.class.getDeclaredMethod(event))
                        .setId("nio-navigation-" + event)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object target = chain.getThisObject();
                            if (!HOME.equals(target.getClass().getName())) return chain.proceed();
                            Activity activity = (Activity) target;
                            if (event.equals("onPause")) NioFloatingNavigation.pause(activity);
                            else if (event.equals("onDestroy")) NioFloatingNavigation.dispose(activity);
                            Object result = chain.proceed();
                            // Post attachment until the packed subclass has completed its resume.
                            if (event.equals("onResume")) NioFloatingNavigation.resume(activity);
                            return result;
                        });
            }
        } catch (Throwable error) {
            module.log(Log.ERROR, "MyHyperModifier", "Could not install NIO navigation", error);
            HookDiagnostics.failure(ModuleScopePackage.NIO, "蔚来悬浮底栏", error);
        }
    }
}
