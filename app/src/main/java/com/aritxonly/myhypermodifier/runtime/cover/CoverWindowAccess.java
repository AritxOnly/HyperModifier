package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolves the complete access plan once, before installing any window hooks. */
final class CoverWindowAccess {
    final Class<?> activity, window, displayPolicy, service;
    final Method resolve, recompute, getResolved, getConfiguration, getParent, getTask;
    final Method getDisplayId, getMode, getType, compatInsets, computeResources, requestedOrientation;
    final Method getBounds, setBounds, setAppBounds, setMaxBounds, visibleTypes, isVisible;
    final Method focusChanged, updateBars, requestTraversal, systemReady;
    final Field windowActivity, activityInfo, taskId, globalLock, context, policyDisplay, windowConfig;
    final Method displayId;

    CoverWindowAccess(ClassLoader loader) throws ReflectiveOperationException {
        this(loader, "com.android.server.wm");
    }

    CoverWindowAccess(ClassLoader loader, String namespace) throws ReflectiveOperationException {
        activity = Class.forName(namespace + ".ActivityRecord", false, loader);
        window = Class.forName(namespace + ".WindowState", false, loader);
        displayPolicy = Class.forName(namespace + ".DisplayPolicy", false, loader);
        service = Class.forName(namespace + ".WindowManagerService", false, loader);
        Class<?> task = Class.forName(namespace + ".Task", false, loader);
        windowConfig = field(Configuration.class, "windowConfiguration");
        Class<?> wc = windowConfig.getType();
        resolve = method(activity, "resolveOverrideConfiguration", Configuration.class);
        recompute = method(activity, "recomputeConfiguration");
        getResolved = method(activity, "getResolvedOverrideConfiguration");
        getConfiguration = method(activity, "getConfiguration");
        getParent = method(activity, "getParent");
        getTask = method(activity, "getTask");
        getDisplayId = method(activity, "getDisplayId");
        getMode = method(task, "getWindowingMode");
        getType = method(task, "getActivityType");
        compatInsets = method(activity, "getAppCompatDisplayInsets");
        requestedOrientation = method(activity, "getRequestedConfigurationOrientation");
        computeResources = method(task, "computeConfigResourceOverrides", Configuration.class, Configuration.class);
        getBounds = method(wc, "getBounds");
        setBounds = method(wc, "setBounds", Rect.class);
        setAppBounds = method(wc, "setAppBounds", Rect.class);
        setMaxBounds = method(wc, "setMaxBounds", Rect.class);
        visibleTypes = method(window, "getRequestedVisibleTypes");
        isVisible = method(window, "isRequestedVisible", int.class);
        focusChanged = method(displayPolicy, "focusChangedLw", window, window);
        updateBars = method(displayPolicy, "updateSystemBarAttributes");
        requestTraversal = method(service, "requestTraversal");
        systemReady = method(service, "systemReady");
        windowActivity = field(window, "mActivityRecord");
        activityInfo = field(activity, "info");
        taskId = field(task, "mTaskId");
        globalLock = field(service, "mGlobalLock");
        context = field(service, "mContext");
        policyDisplay = field(displayPolicy, "mDisplayContent");
        displayId = method(policyDisplay.getType(), "getDisplayId");
    }

    boolean eligible(Object record) throws ReflectiveOperationException {
        if (record == null || (Integer) getDisplayId.invoke(record) != 0) return false;
        ActivityInfo info = (ActivityInfo) activityInfo.get(record);
        if (info == null || info.applicationInfo == null || info.applicationInfo.uid < 10000
                || !CoverAdaptationPolicy.application(info.packageName)) return false;
        Object task = getTask.invoke(record);
        return task != null && (Integer) getMode.invoke(task) == 1
                && getParent.invoke(record) == task
                && (Integer) requestedOrientation.invoke(record) != Configuration.ORIENTATION_LANDSCAPE
                && (Integer) getType.invoke(task) == 1 && compatInsets.invoke(record) == null;
    }

    Rect bounds(Configuration config) throws ReflectiveOperationException {
        return (Rect) getBounds.invoke(windowConfig.get(config));
    }

    void apply(Object record, Configuration parent, Rect bounds) throws ReflectiveOperationException {
        Configuration config = (Configuration) getResolved.invoke(record);
        Object wc = windowConfig.get(config);
        setBounds.invoke(wc, bounds);
        // Explicit app bounds remove the old top status-bar reservation. The actual navigation
        // and cutout insets are still dispatched normally by WM, not consumed by this hook.
        setAppBounds.invoke(wc, bounds);
        config.screenWidthDp = config.screenHeightDp = config.smallestScreenWidthDp = 0;
        config.orientation = Configuration.ORIENTATION_UNDEFINED;
        config.screenLayout = Configuration.SCREENLAYOUT_UNDEFINED;
        computeResources.invoke(getTask.invoke(record), config, parent);
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        setMaxBounds.invoke(wc, bounds);
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name, args); m.setAccessible(true); return m; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
