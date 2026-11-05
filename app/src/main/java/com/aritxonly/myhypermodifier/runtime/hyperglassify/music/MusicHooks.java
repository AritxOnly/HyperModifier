package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Lifecycle adapter for Xiaomi Music's phone-only five-slot bottom dock. */
final class MusicHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String PACKAGE = ModuleScopePackage.MI_MUSIC;
    private static final String MAIN_ACTIVITY = "com.tencent.qqmusiclite.activity.MainActivity";
    private static final String BOTTOM_BAR =
            "com.tencent.qqmusiclite.ui.BottomNavigationView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private MusicHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            MusicNavigationDelegate delegate = MusicNavigationTargets.delegate();
            Class<?> activityClass = Class.forName(MAIN_ACTIVITY, false, loader);
            installLifecycle(module, activityClass, delegate);
            installBottomBarAttachment(module, loader, activityClass, delegate);
            HookDiagnostics.available(PACKAGE, "小米音乐手机版悬浮底栏");
            module.log(Log.INFO, TAG,
                    "Xiaomi Music phone navigation installed, pid=" + android.os.Process.myPid());
        } catch (Throwable error) {
            INSTALLED.set(false);
            HookDiagnostics.failure(PACKAGE, "小米音乐手机版悬浮底栏", error);
            module.log(Log.ERROR, TAG, "Could not install Xiaomi Music phone navigation", error);
        }
    }

    private static void installLifecycle(
            XposedModule module,
            Class<?> activityClass,
            MusicNavigationDelegate delegate
    ) throws NoSuchMethodException {
        Method onCreate = findMethod(activityClass, "onCreate", Bundle.class);
        Method onResume = findMethod(activityClass, "onResume");
        Method onPause = findMethod(activityClass, "onPause");
        Method onDestroy = findMethod(activityClass, "onDestroy");
        Method onFocus = findMethod(activityClass, "onWindowFocusChanged", boolean.class);
        Method dispatchTouch = findMethod(activityClass, "dispatchTouchEvent", MotionEvent.class);

        module.hook(onCreate).setId("xiaomi-music-phone-create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) delegate.prepare(activity);
                    Object result = chain.proceed();
                    if (activity != null) {
                        delegate.attach(activity);
                        activity.getWindow().getDecorView().postDelayed(
                                () -> delegate.attach(activity), 120L);
                    }
                    return result;
                });
        module.hook(onResume).setId("xiaomi-music-phone-resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) {
                        delegate.attach(activity);
                        delegate.setForeground(activity, true);
                    }
                    return result;
                });
        module.hook(onPause).setId("xiaomi-music-phone-pause")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) delegate.setForeground(activity, false);
                    return chain.proceed();
                });
        module.hook(onDestroy).setId("xiaomi-music-phone-destroy")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) delegate.dispose(activity);
                    return chain.proceed();
                });
        module.hook(onFocus).setId("xiaomi-music-phone-focus")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) {
                        delegate.attach(activity);
                        delegate.refresh(activity);
                    }
                    return result;
                });
        module.hook(dispatchTouch).setId("xiaomi-music-phone-touch")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    Object event = chain.getArg(0);
                    if (activity != null && event instanceof MotionEvent) {
                        delegate.attach(activity);
                        delegate.onTouchEvent(activity, (MotionEvent) event);
                    }
                    return result;
                });
    }

    private static void installBottomBarAttachment(
            XposedModule module,
            ClassLoader loader,
            Class<?> activityClass,
            MusicNavigationDelegate delegate
    ) {
        try {
            Class<?> type = Class.forName(BOTTOM_BAR, false, loader);
            int index = 0;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                int constructorIndex = index++;
                module.hook(constructor).setId("xiaomi-music-phone-bottom-bar-" + constructorIndex)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object target = chain.getThisObject();
                            if (target instanceof View) {
                                Activity activity = findActivity(((View) target).getContext());
                                if (activity != null && activityClass.isInstance(activity)) {
                                    delegate.attach(activity, (View) target);
                                    activity.getWindow().getDecorView().postDelayed(
                                            () -> delegate.attach(activity), 120L);
                                }
                            }
                            return result;
                        });
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG,
                    "Xiaomi Music phone bottom-bar constructor hook unavailable", error);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Activity asActivity(Object target, Class<?> activityClass) {
        return activityClass.isInstance(target) ? (Activity) target : null;
    }

    private static Activity findActivity(Context context) {
        for (int depth = 0; context != null && depth < 12; depth++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return null;
            context = base;
        }
        return null;
    }
}
