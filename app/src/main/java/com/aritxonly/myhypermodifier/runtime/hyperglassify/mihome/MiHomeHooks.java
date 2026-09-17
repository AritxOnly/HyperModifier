package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Bridges Mi Home's main Activity to the app-owned Compose navigation overlay. */
final class MiHomeHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY = "com.xiaomi.smarthome.SmartHomeMainActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private MiHomeHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = mainActivity.getDeclaredMethod("onCreate", Bundle.class);
            Method onResume = mainActivity.getDeclaredMethod("onResume");
            Method onDestroy = mainActivity.getDeclaredMethod("onDestroy");

            module.hook(onCreate)
                    .setId("mi-home-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            MiHomeFloatingNavigation.prepare((Activity) target);
                        }
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable throwable) {
                            if (target instanceof Activity) {
                                MiHomeFloatingNavigation.dispose((Activity) target);
                            }
                            throw throwable;
                        }
                        if (target instanceof Activity) {
                            MiHomeFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            // The tab strip is inflated from a ViewStub and can appear after the first startup
            // retry window (for example after onboarding or account initialization).
            module.hook(onResume)
                    .setId("mi-home-floating-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            MiHomeFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("mi-home-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            MiHomeFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatchTouchEvent)
                    .setId("mi-home-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (target instanceof Activity && event instanceof MotionEvent) {
                            MiHomeFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Mi Home navigation hooks", throwable);
        }
    }
}
