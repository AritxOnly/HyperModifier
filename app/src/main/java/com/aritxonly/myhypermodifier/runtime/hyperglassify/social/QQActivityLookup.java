package com.aritxonly.myhypermodifier;

import android.app.Activity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Settings-time lookup only. Avoid initializing QQ's SplashActivity just to read a static field. */
final class QQActivityLookup {
    private static Method currentThread;
    private static Field activities, recordActivity;

    static Activity currentSplash() throws ReflectiveOperationException {
        if (currentThread == null) {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            currentThread = thread.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            activities = thread.getDeclaredField("mActivities");
            activities.setAccessible(true);
        }
        Object thread = currentThread.invoke(null);
        if (thread == null) return null;
        Object records = activities.get(thread);
        if (!(records instanceof Map<?, ?>)) return null;
        for (Object record : ((Map<?, ?>) records).values()) {
            if (record == null) continue;
            if (recordActivity == null) {
                recordActivity = record.getClass().getDeclaredField("activity");
                recordActivity.setAccessible(true);
            }
            Object candidate = recordActivity.get(record);
            if (candidate instanceof Activity) {
                Activity activity = (Activity) candidate;
                if (!activity.isFinishing() && !activity.isDestroyed()
                        && "com.tencent.mobileqq.activity.SplashActivity".equals(activity.getClass().getName())) {
                    return activity;
                }
            }
        }
        return null;
    }
}
