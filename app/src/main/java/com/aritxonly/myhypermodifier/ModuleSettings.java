package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared, process-local snapshot of the companion app's appearance settings. */
final class ModuleSettings {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean();

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

    static void markLoaded(Context context) {
        if (load(context)) {
            SETTINGS_LOADED.set(true);
        }
    }

    /** Lazily resolves Application for HyperOS builds that notify PackageReady late. */
    static void ensureLoaded() {
        if (SETTINGS_LOADED.get()) {
            return;
        }
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context && load((Context) application)) {
                SETTINGS_LOADED.set(true);
            }
        } catch (Throwable ignored) {
            // The application has not been attached yet; a later hooked call retries safely.
        }
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
            Log.w(TAG, "Could not load settings; using safe defaults", throwable);
            return false;
        }
    }
}
