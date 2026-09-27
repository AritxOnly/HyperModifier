package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Official Bilibili 9.13.0 lifecycle, with original TabHost routing retained. */
final class BilibiliHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY =
            "tv.danmaku.bili.MainActivityV2";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private BilibiliHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = mainActivity.getDeclaredMethod("onCreate", Bundle.class);
            Method onResume = mainActivity.getDeclaredMethod("onResume");
            Method onDestroy = mainActivity.getDeclaredMethod("onDestroy");
            Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);

            module.hook(onCreate)
                    .setId("bilibili-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        boolean isMain = mainActivity.isInstance(target) && target instanceof Activity;
                        if (isMain) BilibiliFloatingNavigation.prepare((Activity) target);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable throwable) {
                            if (isMain) BilibiliFloatingNavigation.dispose((Activity) target);
                            throw throwable;
                        }
                        if (isMain) BilibiliFloatingNavigation.attach((Activity) target);
                        return result;
                    });

            module.hook(onResume)
                    .setId("bilibili-floating-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            BilibiliFloatingNavigation.attach((Activity) target);
                            BilibiliFloatingNavigation.setForeground((Activity) target, true);
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("bilibili-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            BilibiliFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            module.hook(dispatchTouchEvent)
                    .setId("bilibili-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (mainActivity.isInstance(target) && target instanceof Activity
                                && event instanceof MotionEvent) {
                            BilibiliFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
            module.hook(mainActivity.getDeclaredMethod("onPause"))
                    .setId("bilibili-floating-navigation-pause")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (chain.getThisObject() instanceof Activity) {
                            BilibiliFloatingNavigation.setForeground((Activity) chain.getThisObject(), false);
                        }
                        return chain.proceed();
                    });
            module.hook(mainActivity.getDeclaredMethod("onWindowFocusChanged", boolean.class))
                    .setId("bilibili-floating-navigation-focus")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getThisObject() instanceof Activity) {
                            BilibiliFloatingNavigation.refresh((Activity) chain.getThisObject());
                        }
                        return result;
                    });

            // Hook the two app-owned input callbacks, not ViewGroup.dispatchTouchEvent for every
            // scrolling row and nested layout in the entire process.
            Class<?> tabClick = Class.forName(
                    "com.bilibili.lib.homepage.widget.TabHost$a", false, classLoader);
            module.hook(tabClick.getDeclaredMethod("onClick", View.class))
                    .setId("bilibili-native-bottom-bar-click")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getArg(0);
                        if (target instanceof View
                                && BilibiliFloatingNavigation.blocksNativeInput((View) target)) {
                            return null;
                        }
                        return chain.proceed();
                    });
            Class<?> publishView = Class.forName(
                    "com.bilibili.lib.homepage.widget.HomeTabPublishView", false, classLoader);
            module.hook(publishView.getDeclaredMethod("onTouch", View.class, MotionEvent.class))
                    .setId("bilibili-native-publish-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof View
                                && BilibiliFloatingNavigation.blocksNativeInput((View) target)) {
                            return true;
                        }
                        return chain.proceed();
                    });

            // Keep the app's listener authoritative; adjust its bottom inset after it runs.
            Class<?> mainInsets = Class.forName(
                    "tv.danmaku.bili.components.a", false, classLoader);
            Class<?> compatInsets = Class.forName(
                    "androidx.core.view.WindowInsetsCompat", false, classLoader);
            module.hook(mainInsets.getDeclaredMethod("onApplyWindowInsets", View.class, compatInsets))
                    .setId("bilibili-home-navigation-insets")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getArg(0) instanceof View) {
                            BilibiliFloatingNavigation.onHomeInsets((View) chain.getArg(0));
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Bilibili navigation hooks", throwable);
        }
    }
}
