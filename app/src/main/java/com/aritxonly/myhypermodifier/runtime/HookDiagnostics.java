package com.aritxonly.myhypermodifier;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Sends rare startup failures to the companion app without doing IPC inside a Hook callback. */
final class HookDiagnostics {
    private static final String TAG = "MyHyperModifier";
    private static final String MODULE_PACKAGE = "com.aritxonly.myhypermodifier";
    private static final String ACTION = MODULE_PACKAGE + ".HOOK_DIAGNOSTIC";
    private static final int MAX_PENDING = 128;
    private static final ArrayDeque<Event> PENDING = new ArrayDeque<>();
    private static final Map<String, Boolean> REPORTED = new HashMap<>();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MyHyperModifier-diagnostics");
        thread.setDaemon(true);
        return thread;
    });
    private static Context context;
    private static boolean scheduled;

    private HookDiagnostics() {}

    static void attach(Context targetContext) {
        try {
            synchronized (HookDiagnostics.class) {
                Context appContext = targetContext.getApplicationContext();
                context = appContext != null ? appContext : targetContext;
                scheduleLocked();
            }
        } catch (Throwable ignored) { /* Diagnostics must never affect the target process. */ }
    }

    static void failure(String packageName, String feature, Throwable error) {
        try {
            Log.w(TAG, "Hook unavailable in " + packageName + ": " + feature, error);
            record(packageName, feature, error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : ": " + error.getMessage()), true);
        } catch (Throwable ignored) { /* Diagnostics must never affect the target process. */ }
    }

    static void unavailable(String packageName, String feature, String detail) {
        try {
            Log.w(TAG, "Hook unavailable in " + packageName + ": " + feature + " (" + detail + ")");
            record(packageName, feature, detail, true);
        } catch (Throwable ignored) { /* Diagnostics must never affect the target process. */ }
    }

    static void available(String packageName, String feature) {
        try {
            record(packageName, feature, "Hook 已安装；实际效果需在对应界面验证", false);
        } catch (Throwable ignored) { /* Diagnostics must never affect the target process. */ }
    }

    static void ready(String packageName) {
        try {
            record(packageName, "模块注入", "已进入目标进程；具体功能以失败记录为准", false);
        } catch (Throwable ignored) { /* Diagnostics must never affect the target process. */ }
    }

    private static void record(String packageName, String feature, String detail, boolean warning) {
        if (ModuleSettings.diagnosticToken == null || ModuleSettings.diagnosticToken.isEmpty()) return;
        synchronized (HookDiagnostics.class) {
            String key = packageName + '|' + feature;
            Boolean previous = REPORTED.put(key, warning);
            if (previous != null && previous == warning) return;
            if (PENDING.size() == MAX_PENDING) PENDING.removeFirst();
            PENDING.addLast(new Event(packageName, feature, detail, warning));
            scheduleLocked();
        }
    }

    private static void scheduleLocked() {
        if (context == null || PENDING.isEmpty() || scheduled) return;
        scheduled = true;
        try {
            WORKER.schedule(HookDiagnostics::flush, 250, TimeUnit.MILLISECONDS);
        } catch (Throwable error) {
            scheduled = false;
            Log.w(TAG, "Could not schedule Hook diagnostics", error);
        }
    }

    private static void flush() {
        Context targetContext;
        synchronized (HookDiagnostics.class) {
            targetContext = context;
            scheduled = false;
        }
        if (targetContext == null) return;
        for (;;) {
            Event event;
            synchronized (HookDiagnostics.class) {
                event = PENDING.pollFirst();
            }
            if (event == null) return;
            try {
                Intent intent = new Intent(ACTION)
                        .setComponent(new ComponentName(MODULE_PACKAGE,
                                MODULE_PACKAGE + ".HookDiagnosticReceiver"))
                        .putExtra("token", ModuleSettings.diagnosticToken)
                        .putExtra("package", event.packageName)
                        .putExtra("feature", event.feature)
                        .putExtra("detail", event.detail)
                        .putExtra("warning", event.warning);
                targetContext.sendBroadcast(intent);
            } catch (Throwable error) {
                Log.w(TAG, "Could not deliver Hook diagnostic", error);
            }
        }
    }

    private static final class Event {
        final String packageName;
        final String feature;
        final String detail;
        final boolean warning;

        Event(String packageName, String feature, String detail, boolean warning) {
            this.packageName = packageName;
            this.feature = feature;
            this.detail = detail;
            this.warning = warning;
        }
    }
}
