package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Installs the small, version-tolerant bridge around Xiaomi Health's main Activity. */
final class XiaomiHealthHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY = "com.xiaomi.fitness.main.MainActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private XiaomiHealthHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = mainActivity.getDeclaredMethod("onCreate", Bundle.class);
            Method onDestroy = mainActivity.getDeclaredMethod("onDestroy");

            module.hook(onCreate)
                    .setId("xiaomi-health-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            XiaomiHealthFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("xiaomi-health-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            XiaomiHealthFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            // The backdrop sampler follows direct and inertial scrolls that originate from a
            // gesture without installing listeners on Xiaomi's fragment hierarchy.
            Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatchTouchEvent)
                    .setId("xiaomi-health-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (target instanceof Activity && event instanceof MotionEvent) {
                            XiaomiHealthFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Xiaomi Health navigation hooks", throwable);
        }
    }
}
