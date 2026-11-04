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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Focused lifecycle adapter for Kuaishou and Kuaishou Lite. */
final class KuaishouHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Set<String> HOOK_IDS = new HashSet<>();

    private KuaishouHooks() {}

    static boolean isTarget(String packageName) {
        return KuaishouFloatingNavigation.isTarget(packageName);
    }

    static void install(XposedModule module, ClassLoader loader, String packageName) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> activityClass = Class.forName(
                    "com.yxcorp.gifshow.HomeActivity", false, loader);
            installLifecycle(module, activityClass, packageName);
            installTabAttachment(module, loader, packageName);
            module.log(Log.INFO, "MyHyperModifier",
                    "Kuaishou navigation hooks installed for " + packageName);
        } catch (Throwable error) {
            INSTALLED.set(false);
            HookDiagnostics.failure(packageName, "快手悬浮底栏", error);
        }
    }

    private static void installTabAttachment(
            XposedModule module, ClassLoader loader, String packageName) {
        try {
            Class<?> type = Class.forName(
                    KuaishouFloatingNavigation.TAB_VIEW_CLASS, false, loader);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.hook(constructor)
                        .setId(unique(packageName + "-ks-tab-" + constructor.getParameterCount()))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (chain.getThisObject() instanceof View) {
                                Activity activity = findActivity(((View) chain.getThisObject()).getContext());
                                if (activity != null) {
                                    activity.getWindow().getDecorView().postDelayed(() -> {
                                        KuaishouFloatingNavigation.attach(activity, packageName);
                                    }, 80L);
                                }
                            }
                            return result;
                        });
            }
        } catch (Throwable error) {
            module.log(Log.WARN, "MyHyperModifier",
                    "Kuaishou bottom-tab attachment hook unavailable", error);
        }
    }

    private static void installLifecycle(
            XposedModule module, Class<?> type, String packageName) throws NoSuchMethodException {
        Method onCreate = findMethod(type, "onCreate", Bundle.class);
        Method onResume = findMethod(type, "onResume");
        Method onPause = findMethod(type, "onPause");
        Method onDestroy = findMethod(type, "onDestroy");
        Method onFocus = findMethod(type, "onWindowFocusChanged", boolean.class);
        Method dispatchTouch = findMethod(type, "dispatchTouchEvent", MotionEvent.class);
        String stem = packageName + "-kuaishou";

        module.hook(onCreate)
                .setId(unique(stem + "-create"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = type.isInstance(chain.getThisObject())
                            ? (Activity) chain.getThisObject() : null;
                    if (activity != null) KuaishouFloatingNavigation.prepare(activity, packageName);
                    Object result = chain.proceed();
                    if (activity != null) KuaishouFloatingNavigation.attach(activity, packageName);
                    return result;
                });
        module.hook(onResume)
                .setId(unique(stem + "-resume"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = type.isInstance(chain.getThisObject())
                            ? (Activity) chain.getThisObject() : null;
                    if (activity != null) KuaishouFloatingNavigation.attach(activity, packageName);
                    return result;
                });
        module.hook(onPause)
                .setId(unique(stem + "-pause"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (type.isInstance(chain.getThisObject())) {
                        KuaishouFloatingNavigation.setForeground(
                                (Activity) chain.getThisObject(), packageName, false);
                    }
                    return chain.proceed();
                });
        module.hook(onDestroy)
                .setId(unique(stem + "-destroy"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (type.isInstance(chain.getThisObject())) {
                        KuaishouFloatingNavigation.dispose(
                                (Activity) chain.getThisObject(), packageName);
                    }
                    return chain.proceed();
                });
        module.hook(onFocus)
                .setId(unique(stem + "-focus"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (type.isInstance(chain.getThisObject())) {
                        Activity activity = (Activity) chain.getThisObject();
                        KuaishouFloatingNavigation.attach(activity, packageName);
                        KuaishouFloatingNavigation.refresh(activity, packageName);
                    }
                    return result;
                });
        module.hook(dispatchTouch)
                .setId(unique(stem + "-touch"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (type.isInstance(chain.getThisObject())) {
                        Activity activity = (Activity) chain.getThisObject();
                        KuaishouFloatingNavigation.attach(activity, packageName);
                        if (chain.getArg(0) instanceof MotionEvent) {
                            KuaishouFloatingNavigation.onTouchEvent(
                                    activity, packageName, (MotionEvent) chain.getArg(0));
                        }
                    }
                    return result;
                });
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

    private static String unique(String id) {
        synchronized (HOOK_IDS) {
            return HOOK_IDS.add(id) ? id : id + "-duplicate";
        }
    }
}
