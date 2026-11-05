package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Bridges PiliPlus' single FlutterActivity to the app-owned soft-glass navigation overlay. */
final class PiliPlusHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY = "com.example.piliplus.MainActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private PiliPlusHooks() {}

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = findMethod(mainActivity, "onCreate", Bundle.class);
            Method onResume = findMethod(mainActivity, "onResume");
            Method onPause = findMethod(mainActivity, "onPause");
            Method onDestroy = findMethod(mainActivity, "onDestroy");
            Method onWindowFocusChanged =
                    findMethod(mainActivity, "onWindowFocusChanged", boolean.class);
            Method dispatchTouchEvent =
                    findMethod(mainActivity, "dispatchTouchEvent", MotionEvent.class);

            module.hook(onCreate)
                    .setId("piliplus-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity activity = asActivity(chain.getThisObject(), mainActivity);
                        if (activity != null) PiliPlusFloatingNavigation.prepare(activity);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable error) {
                            if (activity != null) PiliPlusFloatingNavigation.dispose(activity);
                            throw error;
                        }
                        if (activity != null) PiliPlusFloatingNavigation.attach(activity);
                        return result;
                    });

            module.hook(onResume)
                    .setId("piliplus-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = asActivity(chain.getThisObject(), mainActivity);
                        if (activity != null) {
                            PiliPlusFloatingNavigation.attach(activity);
                            PiliPlusFloatingNavigation.setForeground(activity, true);
                        }
                        return result;
                    });

            module.hook(onPause)
                    .setId("piliplus-navigation-pause")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity activity = asActivity(chain.getThisObject(), mainActivity);
                        if (activity != null) {
                            PiliPlusFloatingNavigation.setForeground(activity, false);
                        }
                        return chain.proceed();
                    });

            module.hook(onDestroy)
                    .setId("piliplus-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Activity activity = asActivity(chain.getThisObject(), mainActivity);
                        if (activity != null) PiliPlusFloatingNavigation.dispose(activity);
                        return chain.proceed();
                    });

            module.hook(onWindowFocusChanged)
                    .setId("piliplus-navigation-focus")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = asActivity(chain.getThisObject(), mainActivity);
                        if (activity != null) PiliPlusFloatingNavigation.refresh(activity);
                        return result;
                    });

            module.hook(dispatchTouchEvent)
                    .setId("piliplus-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Activity activity = asActivity(chain.getThisObject(), mainActivity);
                        Object event = chain.getArg(0);
                        if (activity != null && event instanceof MotionEvent) {
                            PiliPlusFloatingNavigation.onTouchEvent(
                                    activity, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install PiliPlus navigation hooks", error);
            HookDiagnostics.failure(
                    "com.example.piliplus", "PiliPlus 液态柔光底栏", error);
        }
    }

    private static Activity asActivity(Object target, Class<?> activityClass) {
        return activityClass.isInstance(target) && target instanceof Activity
                ? (Activity) target : null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
