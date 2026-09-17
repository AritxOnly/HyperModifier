package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Bridges Amap's WingActivity lifecycle to the app-owned Compose navigation overlay. */
final class AmapHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY = "com.autonavi.map.activity.NewMapActivity";
    private static final String BASE_ACTIVITY = "com.autonavi.wing.WingActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private AmapHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Class<?> baseActivity = Class.forName(BASE_ACTIVITY, false, classLoader);
            Method onCreate = baseActivity.getDeclaredMethod("onCreate", Bundle.class);
            Method onResume = baseActivity.getDeclaredMethod("onResume");
            Method onDestroy = baseActivity.getDeclaredMethod("onDestroy");
            Method dispatchTouchEvent = mainActivity.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);

            module.hook(onCreate)
                    .setId("amap-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        boolean isMain = mainActivity.isInstance(target) && target instanceof Activity;
                        if (isMain) {
                            AmapFloatingNavigation.prepare((Activity) target);
                        }
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable throwable) {
                            if (isMain) {
                                AmapFloatingNavigation.dispose((Activity) target);
                            }
                            throw throwable;
                        }
                        if (isMain) {
                            AmapFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            module.hook(onResume)
                    .setId("amap-floating-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            AmapFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("amap-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            AmapFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            module.hook(dispatchTouchEvent)
                    .setId("amap-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (target instanceof Activity && event instanceof MotionEvent) {
                            AmapFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Amap navigation hooks", throwable);
        }
    }
}
