package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Lifecycle adapter for WeChat and QQ; the host app remains authoritative for tab routing. */
final class SocialHooks {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Set<String> HOOK_IDS = new HashSet<>();

    private SocialHooks() {}

    static boolean isTarget(String packageName) {
        return SocialNavigationTargets.INSTANCE.specFor(packageName) != null;
    }

    static void install(XposedModule module, ClassLoader classLoader, String packageName) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        SocialNavigationSpec spec = SocialNavigationTargets.INSTANCE.specFor(packageName);
        SocialNavigationDelegate delegate =
                SocialNavigationTargets.INSTANCE.delegateFor(packageName);
        if (spec == null || delegate == null) {
            INSTALLED.set(false);
            return;
        }
        module.log(Log.INFO, TAG, spec.getLogName() + " navigation bootstrap in "
                + Application.getProcessName() + ", pid=" + android.os.Process.myPid());
        boolean installedAny = false;
        Throwable firstError = null;
        for (String activityName : spec.getActivityClassNames()) {
            try {
                Class<?> activityClass = Class.forName(activityName, false, classLoader);
                installLifecycle(module, activityClass, spec, delegate);
                installedAny = true;
            } catch (Throwable error) {
                if (firstError == null) firstError = error;
            }
        }
        try {
            installGenericActivityLifecycle(module, spec, delegate);
            installedAny = true;
        } catch (Throwable error) {
            if (firstError == null) firstError = error;
        }
        if (spec.getKind() == SocialNavigationKind.QQ) {
            installQQBarAttachmentHooks(module, classLoader, delegate);
        }
        if (!installedAny) {
            INSTALLED.set(false);
            Throwable error = firstError == null
                    ? new ClassNotFoundException(spec.getLogName() + " main Activity")
                    : firstError;
            module.log(Log.ERROR, TAG,
                    "Could not install " + spec.getLogName() + " navigation hooks", error);
            HookDiagnostics.failure(packageName, spec.getLogName() + "悬浮底栏", error);
        }
    }

    /** Attach as soon as QQ inflates either supported bottom-bar implementation. */
    private static void installQQBarAttachmentHooks(
            XposedModule module,
            ClassLoader classLoader,
            SocialNavigationDelegate delegate
    ) {
        for (String className : new String[] {
                "com.tencent.mobileqq.widget.QQTabWidget",
                "com.tencent.mobileqq.widget.QQTabLayout",
                "com.tencent.mobileqq.widget.CustomedTabWidget",
                "com.tencent.biz.qui.quipagetabbar.QUIPageTabBar"}) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    module.hook(constructor)
                            .setId(unique(className + "-attachment-" + constructor.getParameterCount()))
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                Object target = chain.getThisObject();
                                if (target instanceof View) {
                                    Activity activity = findActivity(((View) target).getContext());
                                    if (activity != null) {
                                        delegate.attach(activity, (View) target);
                                        activity.getWindow().getDecorView().postDelayed(
                                                () -> delegate.attach(activity), 120L);
                                    }
                                }
                                return result;
                            });
                }
            } catch (Throwable error) {
                module.log(Log.WARN, "MyHyperModifier",
                        "QQ bottom-bar attachment hook unavailable: " + className, error);
            }
        }
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

    private static void installLifecycle(
            XposedModule module,
            Class<?> activityClass,
            SocialNavigationSpec spec,
            SocialNavigationDelegate delegate
    ) throws NoSuchMethodException {
        Method onCreate = findMethod(activityClass, "onCreate", Bundle.class);
        Method onResume = findMethod(activityClass, "onResume");
        Method onPause = findMethod(activityClass, "onPause");
        Method onDestroy = findMethod(activityClass, "onDestroy");
        Method onFocus = findMethod(activityClass, "onWindowFocusChanged", boolean.class);
        Method dispatchTouch = findMethod(activityClass, "dispatchTouchEvent", MotionEvent.class);
        String stem = spec.getPackageName() + "-" + activityClass.getSimpleName() + "-social";

        module.hook(onCreate)
                .setId(unique(stem + "-create"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) delegate.prepare(activity);
                    Object result;
                    try {
                        result = chain.proceed();
                    } catch (Throwable error) {
                        if (activity != null) delegate.dispose(activity);
                        throw error;
                    }
                    if (activity != null) delegate.attach(activity);
                    return result;
                });

        module.hook(onResume)
                .setId(unique(stem + "-resume"))
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

        module.hook(onPause)
                .setId(unique(stem + "-pause"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) delegate.setForeground(activity, false);
                    return chain.proceed();
                });

        module.hook(onDestroy)
                .setId(unique(stem + "-destroy"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    if (activity != null) delegate.dispose(activity);
                    return chain.proceed();
                });

        module.hook(onFocus)
                .setId(unique(stem + "-focus"))
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

        module.hook(dispatchTouch)
                .setId(unique(stem + "-touch"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = asActivity(chain.getThisObject(), activityClass);
                    Object event = chain.getArg(0);
                    if (activity != null) {
                        delegate.attach(activity);
                        if (event instanceof MotionEvent) {
                            delegate.onTouchEvent(activity, (MotionEvent) event);
                        }
                    }
                    return result;
                });
    }

    /** Keeps lifecycle attachment working across host/delegate Activity subclasses. */
    private static void installGenericActivityLifecycle(
            XposedModule module,
            SocialNavigationSpec spec,
            SocialNavigationDelegate delegate
    ) throws NoSuchMethodException {
        Method onResume = findMethod(Activity.class, "onResume");
        Method onPause = findMethod(Activity.class, "onPause");
        Method onDestroy = findMethod(Activity.class, "onDestroy");
        Method onFocus = findMethod(Activity.class, "onWindowFocusChanged", boolean.class);
        Method dispatchTouch = findMethod(
                Activity.class, "dispatchTouchEvent", MotionEvent.class);
        String stem = spec.getPackageName() + "-Activity-fallback-social";

        module.hook(onResume)
                .setId(unique(stem + "-resume"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null) {
                        activate(activity, spec, delegate);
                        activity.getWindow().getDecorView().postDelayed(
                                () -> activate(activity, spec, delegate), 120L);
                    }
                    return result;
                });
        module.hook(onPause)
                .setId(unique(stem + "-pause"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null && delegate.hasHost(activity)) {
                        delegate.setForeground(activity, false);
                    }
                    return chain.proceed();
                });
        module.hook(onDestroy)
                .setId(unique(stem + "-destroy"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null && delegate.hasHost(activity)) delegate.dispose(activity);
                    return chain.proceed();
                });
        module.hook(onFocus)
                .setId(unique(stem + "-focus"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null) {
                        activate(activity, spec, delegate);
                        if (delegate.hasHost(activity)) delegate.refresh(activity);
                    }
                    return result;
                });
        module.hook(dispatchTouch)
                .setId(unique(stem + "-touch"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    Object event = chain.getArg(0);
                    if (activity != null) {
                        activate(activity, spec, delegate);
                        if (event instanceof MotionEvent && delegate.hasHost(activity)) {
                            delegate.onTouchEvent(activity, (MotionEvent) event);
                        }
                    }
                    return result;
                });
    }

    private static void activate(
            Activity activity,
            SocialNavigationSpec spec,
            SocialNavigationDelegate delegate
    ) {
        if (activity.isFinishing() || activity.isDestroyed() ||
                !spec.getPackageName().equals(activity.getPackageName())) return;
        if (delegate.hasHost(activity) || isPrimaryActivity(activity, spec) ||
                delegate.hasNavigationTarget(activity)) {
            delegate.attach(activity);
            delegate.setForeground(activity, true);
        }
    }

    private static boolean isPrimaryActivity(
            Activity activity,
            SocialNavigationSpec spec
    ) {
        for (Class<?> type = activity.getClass(); type != null; type = type.getSuperclass()) {
            if (spec.getActivityClassNames().contains(type.getName())) return true;
        }
        return false;
    }

    private static Activity targetActivity(Object target, SocialNavigationSpec spec) {
        if (!(target instanceof Activity)) return null;
        Activity activity = (Activity) target;
        return spec.getPackageName().equals(activity.getPackageName()) ? activity : null;
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

    private static String unique(String id) {
        synchronized (HOOK_IDS) {
            if (!HOOK_IDS.add(id)) return id + "-duplicate";
            return id;
        }
    }
}
