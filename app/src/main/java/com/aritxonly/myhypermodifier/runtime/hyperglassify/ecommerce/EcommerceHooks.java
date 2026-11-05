package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Lifecycle bridge shared by the supported shopping and package apps. */
final class EcommerceHooks {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Set<String> HOOK_IDS = new HashSet<>();

    private EcommerceHooks() {}

    static boolean isTarget(String packageName) {
        return EcommerceNavigationTargets.INSTANCE.specFor(packageName) != null;
    }

    static void install(XposedModule module, ClassLoader classLoader, String packageName) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        EcommerceNavigationSpec spec =
                EcommerceNavigationTargets.INSTANCE.specFor(packageName);
        EcommerceNavigationDelegate delegate =
                EcommerceNavigationTargets.INSTANCE.delegateFor(packageName);
        if (spec == null || delegate == null) {
            INSTALLED.set(false);
            return;
        }
        Throwable firstError = null;
        boolean installedAny = false;
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

    /**
     * Alibaba-family apps can create their main Activity before PackageReady and may replace the
     * manifest class with a host/delegate class. A framework Activity fallback keeps the focused
     * adapter attached without installing any package-wide View or resource hooks.
     */
    private static void installGenericActivityLifecycle(
            XposedModule module,
            EcommerceNavigationSpec spec,
            EcommerceNavigationDelegate delegate
    ) throws NoSuchMethodException {
        Method onResume = findMethod(Activity.class, "onResume");
        Method onPause = findMethod(Activity.class, "onPause");
        Method onDestroy = findMethod(Activity.class, "onDestroy");
        Method onWindowFocusChanged = findMethod(
                Activity.class, "onWindowFocusChanged", boolean.class);
        Method dispatchTouchEvent = findMethod(
                Activity.class, "dispatchTouchEvent", MotionEvent.class);
        String stem = spec.getPackageName() + "-Activity-fallback";

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
                    if (activity != null && delegate.hasHost(activity)) {
                        delegate.dispose(activity);
                    }
                    return chain.proceed();
                });
        module.hook(onWindowFocusChanged)
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
        module.hook(dispatchTouchEvent)
                .setId(unique(stem + "-touch"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    Object event = chain.getArg(0);
                    if (activity != null) {
                        // This also catches PackageReady arriving after the first resume/focus.
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
            EcommerceNavigationSpec spec,
            EcommerceNavigationDelegate delegate
    ) {
        if (activity.isFinishing() || activity.isDestroyed() ||
                !spec.getPackageName().equals(activity.getPackageName())) return;
        // A framework-level fallback may run before the app's navigation View is inflated.
        // Keep a delayed attachment for the actual home Activity instead of requiring the bar
        // to be discoverable in this exact callback. Other Activities stay scan-free.
        if (delegate.hasHost(activity) || delegate.hasNavigationTarget(activity) ||
                isPrimaryActivity(activity, spec)) {
            delegate.attach(activity);
            delegate.setForeground(activity, true);
        }
    }

    private static boolean isPrimaryActivity(
            Activity activity,
            EcommerceNavigationSpec spec
    ) {
        for (Class<?> type = activity.getClass(); type != null; type = type.getSuperclass()) {
            if (spec.getActivityClassNames().contains(type.getName())) return true;
        }
        return false;
    }

    private static Activity targetActivity(Object target, EcommerceNavigationSpec spec) {
        if (!(target instanceof Activity)) return null;
        Activity activity = (Activity) target;
        return spec.getPackageName().equals(activity.getPackageName()) ? activity : null;
    }

    private static void installLifecycle(
            XposedModule module,
            Class<?> activityClass,
            EcommerceNavigationSpec spec,
            EcommerceNavigationDelegate delegate
    ) throws NoSuchMethodException {
        Method onCreate = findMethod(activityClass, "onCreate", Bundle.class);
        Method onResume = findMethod(activityClass, "onResume");
        Method onPause = findMethod(activityClass, "onPause");
        Method onDestroy = findMethod(activityClass, "onDestroy");
        Method onWindowFocusChanged = findMethod(
                activityClass, "onWindowFocusChanged", boolean.class);
        Method dispatchTouchEvent = findMethod(
                activityClass, "dispatchTouchEvent", MotionEvent.class);
        String stem = spec.getPackageName() + "-" + activityClass.getSimpleName() + "-ecommerce";

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

        module.hook(onWindowFocusChanged)
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

        module.hook(dispatchTouchEvent)
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
