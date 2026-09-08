package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared, process-local snapshot of the companion app's appearance settings. */
final class ModuleSettings {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean();
    private static final AtomicBoolean LOAD_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicInteger LOAD_FAILURES = new AtomicInteger();
    private static final AtomicLong NEXT_LOAD_UPTIME_MS = new AtomicLong();
    private static final ExecutorService SETTINGS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MyHyperModifier-settings");
        thread.setDaemon(true);
        return thread;
    });

    static volatile boolean notificationsEnabled = true;
    static volatile float notificationRadius = 28f;
    static volatile boolean controlCenterEnabled = true;
    static volatile float controlCenterRadius = 28f;
    static volatile boolean advancedControlCenterCorners = false;
    static volatile float controlCenterTileRadius = 28f;
    static volatile float controlCenterCardRadius = 28f;
    static volatile float controlCenterSliderRadius = 28f;
    static volatile float controlCenterDetailSliderRadius = 28f;
    static volatile float controlCenterMediaRadius = 28f;
    static volatile float controlCenterExternalEntryRadius = 28f;
    static volatile boolean miLinkMainCardsEnabled = true;
    static volatile float miLinkMainCardRadius = 20f;
    static volatile boolean mediaEnabled = true;
    static volatile float expandedHeight = 152f;
    static volatile float collapsedHeight = 120f;
    static volatile float fullAodHeight = 80f;
    static volatile boolean islandEnabled = true;
    static volatile int islandHeight = 160;
    static volatile boolean islandProgressBar = true;
    static volatile boolean hideAodActions = true;
    static volatile boolean hideAodSeamless = true;
    static volatile boolean inFullAod;
    static volatile boolean customMediaConstraintSetEnabled = false;
    static volatile String customMediaConstraintSetXml = "";

    private ModuleSettings() {
    }

    /** Never performs provider IPC on SystemUI's startup or resource-resolution thread. */
    static void markLoaded(Context context) {
        scheduleLoad(context);
    }

    /** Lazily schedules a load for HyperOS builds that notify PackageReady late. */
    static void ensureLoaded() {
        if (SETTINGS_LOADED.get()) {
            return;
        }
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context) {
                scheduleLoad((Context) application);
            }
        } catch (Throwable ignored) {
            // The application has not been attached yet; a later hooked call retries safely.
        }
    }

    private static void scheduleLoad(Context context) {
        if (SETTINGS_LOADED.get() || context == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now < NEXT_LOAD_UPTIME_MS.get() || !LOAD_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        try {
            SETTINGS_EXECUTOR.execute(() -> {
                try {
                    if (load(safeContext)) {
                        SETTINGS_LOADED.set(true);
                        LOAD_FAILURES.set(0);
                        NEXT_LOAD_UPTIME_MS.set(0L);
                        LOAD_FAILURE_LOGGED.set(false);
                    } else {
                        scheduleRetry();
                    }
                } finally {
                    LOAD_IN_FLIGHT.set(false);
                }
            });
        } catch (RuntimeException exception) {
            LOAD_IN_FLIGHT.set(false);
            scheduleRetry();
        }
    }

    private static void scheduleRetry() {
        int failures = Math.min(LOAD_FAILURES.incrementAndGet(), 5);
        long delayMs = Math.min(30_000L, 1_000L << failures);
        NEXT_LOAD_UPTIME_MS.set(SystemClock.uptimeMillis() + delayMs);
    }

    private static boolean load(Context context) {
        try {
            Bundle values = context.getContentResolver().call(
                    Uri.parse("content://com.aritxonly.myhypermodifier.settings"),
                    "get_settings", null, null);
            if (values == null) return false;
            notificationsEnabled = values.getBoolean("notifications_enabled", true);
            notificationRadius = values.getFloat("notification_radius", 28f);
            controlCenterEnabled = values.getBoolean("control_center_enabled", true);
            controlCenterRadius = values.getFloat("control_center_radius", 28f);
            advancedControlCenterCorners = values.getBoolean("advanced_control_center_corners", false);
            controlCenterTileRadius = values.getFloat("control_center_tile_radius", 28f);
            controlCenterCardRadius = values.getFloat("control_center_card_radius", 28f);
            controlCenterSliderRadius = values.getFloat("control_center_slider_radius", 28f);
            controlCenterDetailSliderRadius = values.getFloat("control_center_detail_slider_radius", 28f);
            controlCenterMediaRadius = values.getFloat("control_center_media_radius", 28f);
            controlCenterExternalEntryRadius = values.getFloat("control_center_external_entry_radius", 28f);
            miLinkMainCardsEnabled = values.getBoolean("milink_main_cards_enabled", true);
            miLinkMainCardRadius = values.getFloat("milink_main_card_radius", 20f);
            mediaEnabled = values.getBoolean("media_enabled", true);
            expandedHeight = values.getFloat("expanded_height", 152f);
            collapsedHeight = values.getFloat("collapsed_height", 120f);
            fullAodHeight = values.getFloat("full_aod_height", 80f);
            islandEnabled = values.getBoolean("island_enabled", true);
            islandHeight = Math.round(values.getFloat("island_height", 160f));
            islandProgressBar = values.getBoolean("island_progress", true);
            hideAodActions = values.getBoolean("hide_aod_actions", true);
            hideAodSeamless = values.getBoolean("hide_aod_seamless", true);
            customMediaConstraintSetEnabled = values.getBoolean("custom_media_constraint_set_enabled", false);
            customMediaConstraintSetXml = values.getString("custom_media_constraint_set_xml", "");
            return true;
        } catch (Throwable throwable) {
            if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
                Log.w(TAG, "Settings unavailable; using defaults and retrying in background", throwable);
            }
            return false;
        }
    }
}
