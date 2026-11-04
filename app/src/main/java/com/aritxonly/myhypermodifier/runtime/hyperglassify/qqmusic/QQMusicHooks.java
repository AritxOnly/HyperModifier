package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Lifecycle adapter for QQ Music's custom-drawn bottom navigation. */
final class QQMusicHooks {
    private static final AtomicBoolean exactInstalled = new AtomicBoolean();

    private QQMusicHooks() {}

    static void install(XposedModule module, ClassLoader loader) throws Throwable {
        if (exactInstalled.get()) return;
        Class<?> activityClass = Class.forName(
                "com.tencent.qqmusic.activity.AppStarterActivity", false, loader);
        Method onCreate = findMethod(activityClass, "onCreate", Bundle.class);
        Method onResume = findMethod(activityClass, "onResume");
        Method onPause = findMethod(activityClass, "onPause");
        Method onDestroy = findMethod(activityClass, "onDestroy");
        Method onFocus = findMethod(activityClass, "onWindowFocusChanged", boolean.class);
        Method dispatchTouch = findMethod(activityClass, "dispatchTouchEvent", MotionEvent.class);

        module.hook(onCreate)
                .setId("qqmusic-nav-create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) QQMusicFloatingNavigation.prepare(activity);
                    Object result = chain.proceed();
                    if (activity != null) QQMusicFloatingNavigation.attach(activity);
                    return result;
                });
        module.hook(onResume)
                .setId("qqmusic-nav-resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) QQMusicFloatingNavigation.attach(activity);
                    return result;
                });
        module.hook(onPause)
                .setId("qqmusic-nav-pause")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) QQMusicFloatingNavigation.setForeground(activity, false);
                    return chain.proceed();
                });
        module.hook(onDestroy)
                .setId("qqmusic-nav-destroy")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) QQMusicFloatingNavigation.dispose(activity);
                    return chain.proceed();
                });
        module.hook(onFocus)
                .setId("qqmusic-nav-focus")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) {
                        QQMusicFloatingNavigation.attach(activity);
                        QQMusicFloatingNavigation.refresh(activity);
                    }
                    return result;
                });
        module.hook(dispatchTouch)
                .setId("qqmusic-nav-touch")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null && chain.getArg(0) instanceof MotionEvent) {
                        QQMusicFloatingNavigation.attach(activity);
                        QQMusicFloatingNavigation.onTouchEvent(activity, (MotionEvent) chain.getArg(0));
                    }
                    return result;
                });
        exactInstalled.set(true);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Activity asActivity(Object target, Class<?> type) {
        return type.isInstance(target) && target instanceof Activity ? (Activity) target : null;
    }
}
