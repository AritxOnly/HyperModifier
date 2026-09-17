package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Connects Xiaomi Community's actual HomeFrameActivity to the shared View-tab adapter. */
final class XiaomiCommunityHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY =
            "com.xiaomi.vipaccount.ui.home.page.HomeFrameActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private XiaomiCommunityHooks() {
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
                    .setId("xiaomi-community-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        boolean isMain = mainActivity.isInstance(target) && target instanceof Activity;
                        if (isMain) XiaomiCommunityFloatingNavigation.prepare((Activity) target);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable throwable) {
                            if (isMain) XiaomiCommunityFloatingNavigation.dispose((Activity) target);
                            throw throwable;
                        }
                        if (isMain) XiaomiCommunityFloatingNavigation.attach((Activity) target);
                        return result;
                    });

            module.hook(onResume)
                    .setId("xiaomi-community-floating-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            XiaomiCommunityFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("xiaomi-community-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            XiaomiCommunityFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            module.hook(dispatchTouchEvent)
                    .setId("xiaomi-community-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (mainActivity.isInstance(target) && target instanceof Activity
                                && event instanceof MotionEvent) {
                            XiaomiCommunityFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Xiaomi Community navigation hooks", throwable);
        }
    }
}
