package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Only app-owned launcher lifecycle/style hooks; no Activity/View/resource-wide interception. */
final class AlipayHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private AlipayHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> fragment = Class.forName("com.alipay.android.launcher.TabLauncherFragment", false, loader);
            Method getActivity = fragment.getMethod("getActivity");
            InjectedNavigationContext.initialize(module.getModuleApplicationInfo().sourceDir);
            AlipayFloatingNavigation.initialize(loader);
            hook(module, fragment.getDeclaredMethod("onViewCreated", View.class, Bundle.class), "created", chain -> {
                Object result = chain.proceed();
                Object activity = getActivity.invoke(chain.getThisObject());
                if (activity instanceof Activity) AlipayFloatingNavigation.created(
                        chain.getThisObject(), (Activity) activity);
                return result;
            });
            hook(module, fragment.getDeclaredMethod("onResume"), "resume", chain -> {
                Object result = chain.proceed();
                Object activity = getActivity.invoke(chain.getThisObject());
                if (activity instanceof Activity) AlipayFloatingNavigation.resume((Activity) activity);
                return result;
            });
            hook(module, fragment.getDeclaredMethod("onPause"), "pause", chain -> {
                Object activity = getActivity.invoke(chain.getThisObject());
                if (activity instanceof Activity) AlipayFloatingNavigation.pause((Activity) activity);
                return chain.proceed();
            });
            hook(module, fragment.getDeclaredMethod("onDestroyView"), "destroy-view", chain -> {
                Object activity = getActivity.invoke(chain.getThisObject());
                if (activity instanceof Activity) AlipayFloatingNavigation.dispose((Activity) activity);
                return chain.proceed();
            });
            Class<?> utils = Class.forName("com.alipay.mobile.tablauncher.util.TLCommonUtils", false, loader);
            hook(module, utils.getDeclaredMethod("setViewMargin", View.class, int.class, int.class,
                    int.class, int.class), "content-margin", chain -> {
                View view = (View) chain.getArg(0);
                if (view == null) return chain.proceed();
                int requested = (int) chain.getArg(4);
                int effective = AlipayFloatingNavigation.nativeBottomMargin(view, requested);
                if (effective == requested) return chain.proceed();
                int left = (int) chain.getArg(1);
                int top = (int) chain.getArg(2);
                int right = (int) chain.getArg(3);
                // The native helper always calls setLayoutParams, even for identical margins.
                // Avoid a layout -> native reservation -> identical layout feedback loop.
                if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params
                        && params.leftMargin == left && params.topMargin == top
                        && params.rightMargin == right && params.bottomMargin == effective) return null;
                return chain.proceed(new Object[] {view, left, top, right, effective});
            });
            Class<?> manager = Class.forName("com.alipay.android.launcher.tab.TabBarManager", false, loader);
            for (String method : new String[] {"refreshTabBar", "refreshTabBarInternal", "initYoungStyle", "refreshDefaultTabs"}) {
                hook(module, manager.getDeclaredMethod(method), method, chain -> {
                    Object result = chain.proceed();
                    AlipayFloatingNavigation.styleChanged(chain.getThisObject());
                    return result;
                });
            }
            Class<?> tab = Class.forName("com.alipay.android.launcher.tab.TabWidgetItemView", false, loader);
            hook(module, tab.getDeclaredMethod("refreshWithDefaultIcon"), "default-icon", chain -> {
                Object result = chain.proceed();
                AlipayFloatingNavigation.invalidateIcon((View) chain.getThisObject());
                return result;
            });
        } catch (Throwable error) {
            module.log(Log.ERROR, "MyHyperModifier", "Could not install Alipay classic navigation", error);
            HookDiagnostics.failure(ModuleScopePackage.ALIPAY, "支付宝经典底栏", error);
        }
    }

    private static void hook(XposedModule module, Method method, String id,
                             io.github.libxposed.api.XposedInterface.Hooker hooker) {
        method.setAccessible(true);
        module.hook(method).setId("alipay-classic-" + id)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }
}
