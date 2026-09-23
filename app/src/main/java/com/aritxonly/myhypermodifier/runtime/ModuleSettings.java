package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Shared, process-local snapshot of the companion app's appearance settings. */
final class ModuleSettings {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean();
    private static final AtomicBoolean LOAD_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean REMOTE_LISTENER_REGISTERED = new AtomicBoolean();
    private static final AtomicInteger LOAD_FAILURES = new AtomicInteger();
    private static final AtomicLong NEXT_LOAD_UPTIME_MS = new AtomicLong();
    private static final CopyOnWriteArrayList<Runnable> LOAD_LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final ExecutorService SETTINGS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MyHyperModifier-settings");
        thread.setDaemon(true);
        return thread;
    });

    static volatile boolean notificationsEnabled = false;
    static volatile float notificationRadius = 28f;
    static volatile boolean headsUpGlassParametersEnabled = false;
    static volatile boolean headsUpBackgroundBlurRadiusEnabled = false;
    static volatile int headsUpBackgroundBlurRadius = 60;
    static volatile int globalBackgroundBlurPercent = 100;
    private static final float[] DEFAULT_HEADS_UP_GLASS_PARAMETERS = new float[] {
            0.5f, 1f, 0f, 0.8f, 0.5f, 1.2f, 0f, 0.2f, 0f, 0f, 0.03f,
            1f, 1f, 1f, 1.5f, 0f, 0.6f, 0.6f, 1f, 62f, 3.8f, 80f, 600f,
            1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.2f, 0.6f, 0.8f, 1.15f, 3f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };
    private static final float[] DEFAULT_HEADS_UP_GLASS_DARK_PARAMETERS = new float[] {
            0.8f, 1f, 0f, 1f, 0.2f, 2f, 0.14f, 0.1f, 0f, 0f, 0.02f,
            0.27f, 0.27f, 0.27f, 0.6f, 0f, 0.2f, 1.2f, 1f, 72f, 3.8f, 80f,
            600f, 1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.5f, 1f, 0.8f, 1.15f, 3f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };
    private static volatile float[] headsUpGlassParameters =
            DEFAULT_HEADS_UP_GLASS_PARAMETERS.clone();
    private static volatile float[] headsUpGlassDarkParameters =
            DEFAULT_HEADS_UP_GLASS_DARK_PARAMETERS.clone();
    static volatile boolean controlCenterEnabled = false;
    static volatile float controlCenterRadius = 28f;
    static volatile boolean advancedControlCenterCorners = false;
    static volatile float controlCenterTileRadius = 28f;
    static volatile float controlCenterCardRadius = 28f;
    static volatile float controlCenterSliderRadius = 28f;
    static volatile float controlCenterDetailSliderRadius = 28f;
    static volatile float controlCenterMediaRadius = 28f;
    static volatile float controlCenterExternalEntryRadius = 28f;
    static volatile float volumePanelRadius = 0f;
    static volatile boolean miLinkMainCardsEnabled = false;
    static volatile float miLinkMainCardRadius = 20f;
    static volatile boolean mediaEnabled = false;
    static volatile float expandedHeight = 152f;
    static volatile float collapsedHeight = 120f;
    static volatile float fullAodHeight = 80f;
    static volatile boolean islandEnabled = false;
    static volatile int islandHeight = 160;
    static volatile boolean islandProgressBar = false;
    static volatile boolean hideAodActions = false;
    static volatile boolean hideAodSeamless = false;
    static volatile boolean sinkLockscreenNotificationsForFingerprint = false;
    static volatile boolean hideLockscreenFingerprintIcon = false;
    static volatile boolean showLockscreenFingerprintIconOnAod = false;
    static volatile boolean lockscreenPasswordBackgroundBlurEnabled = false;
    static volatile float lockscreenPasswordBackgroundOpacity = 0f;
    static volatile boolean lockscreenPasswordBackgroundFollowShadeBlend = true;
    static volatile boolean lockscreenPinKeySoftGlassEnabled = false;
    static volatile float lockscreenPinKeyGlassExtraRadius = 6f;
    static volatile float lockscreenPinKeyGlassVerticalGap = 16f;
    static volatile boolean statusBarNetworkTypeEnabled = false;
    static volatile float statusBarNetworkTypeSize = 13.5f;
    static volatile boolean statusBarNetworkTypeBold = true;
    static volatile float statusBarNetworkTypeOffset = 0f;
    // A target process can begin drawing before the companion Provider becomes reachable. Keep
    // that bootstrap state visually neutral; the saved default is published once loading succeeds.
    static volatile float hyperGlassifyHiddenNavigationLift = 0f;
    static volatile boolean xiaomiHealthFloatingNavigationEnabled = true;
    static volatile boolean xiaomiHealthMiuixIconsEnabled = false;
    static volatile boolean xiaomiHealthMonochromeIconsEnabled = true;
    static volatile boolean marketFloatingNavigationEnabled = true;
    static volatile boolean marketMiuixIconsEnabled = false;
    static volatile boolean marketMonochromeIconsEnabled = true;
    static volatile boolean marketNavigationBadgesEnabled = false;
    static volatile boolean marketHideGamesTab = false;
    static volatile boolean marketHideRankingsTab = false;
    static volatile boolean marketHideProfileTab = false;
    static volatile boolean miHomeFloatingNavigationEnabled = true;
    static volatile boolean miHomeMiuixIconsEnabled = false;
    static volatile boolean miHomeNavigationBadgesEnabled = true;
    static volatile boolean amapFloatingNavigationEnabled = true;
    static volatile boolean amapMiuixIconsEnabled = false;
    static volatile boolean amapMonochromeIconsEnabled = true;
    static volatile boolean amapHideLongPressVoiceTabEnabled = true;
    static volatile boolean xiaomiCommunityFloatingNavigationEnabled = true;
    static volatile boolean xiaomiCommunityMiuixIconsEnabled = false;
    static volatile boolean xiaomiCommunityMonochromeIconsEnabled = true;
    static volatile boolean xiaomiCommunityNavigationBadgesEnabled = true;
    static volatile boolean spotifyFloatingNavigationEnabled = false;
    static volatile boolean spotifyFavoriteButtonEnabled = false;
    static volatile boolean spotifyShuffleButtonEnabled = false;
    static volatile boolean inFullAod;
    static volatile boolean customMediaConstraintSetEnabled = false;
    static volatile String customMediaConstraintSetXml = "";
    private static volatile String lastLoadStatus = "not-requested";
    // Supplied by XposedInterface#getRemotePreferences. It is specifically designed for module
    // state and stays available even when Android hides this module package from the target app.
    private static volatile SharedPreferences remotePreferences;

    private ModuleSettings() {
    }

    /**
     * Connects this target process to the LSPosed-owned preferences bridge. This must happen
     * from XposedModule rather than through a target-app ContentResolver: Android 11+ package
     * visibility can reject the latter even when the module is installed and enabled.
     */
    static void setRemotePreferences(SharedPreferences preferences) {
        if (preferences == null) return;
        remotePreferences = preferences;
        if (!REMOTE_LISTENER_REGISTERED.compareAndSet(false, true)) return;
        try {
            preferences.registerOnSharedPreferenceChangeListener((sharedPreferences, key) ->
                    scheduleReload());
        } catch (Throwable throwable) {
            // The initial snapshot remains valid on framework versions whose remote bridge is
            // read-only; a scope restart will still obtain the latest values.
            Log.w(TAG, "Remote settings changes are not observable", throwable);
        }
    }

    /** Never performs provider IPC on SystemUI's startup or resource-resolution thread. */
    static void markLoaded(Context context) {
        scheduleLoad(context);
    }

    /**
     * Publishes settings before SystemUI begins inflating its first resources.  This is used only
     * by the SystemUI bootstrap path; ordinary target apps retain the non-blocking loader.
     */
    static boolean loadImmediately(Context context) {
        if (SETTINGS_LOADED.get()) return true;
        if (!LOAD_IN_FLIGHT.compareAndSet(false, true)) {
            return SETTINGS_LOADED.get();
        }
        try {
            if (!load()) {
                scheduleRetry();
                return false;
            }
            publishLoadedSettings();
            return true;
        } finally {
            LOAD_IN_FLIGHT.set(false);
        }
    }

    /** Attempts the eager SystemUI read when PackageReady arrives after Application.attach(). */
    static boolean loadImmediately() {
        if (SETTINGS_LOADED.get()) return true;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            return application instanceof Context && loadImmediately((Context) application);
        } catch (Throwable ignored) {
            return false;
        }
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

    static boolean isLoaded() {
        return SETTINGS_LOADED.get();
    }

    /** Always returns an isolated payload so View implementations cannot alter saved settings. */
    static float[] headsUpGlassParameters(boolean dark) {
        return (dark ? headsUpGlassDarkParameters : headsUpGlassParameters).clone();
    }

    /** Compact diagnostic for target-app logs; never includes a settings value or stack trace. */
    static String loadStatus() {
        return lastLoadStatus;
    }

    /** Runs after a complete settings snapshot is published; never invoked on SystemUI's UI thread. */
    static void onLoaded(Runnable listener) {
        if (SETTINGS_LOADED.get()) {
            listener.run();
            return;
        }
        LOAD_LISTENERS.add(listener);
        if (SETTINGS_LOADED.get() && LOAD_LISTENERS.remove(listener)) {
            listener.run();
        }
    }

    private static void notifyLoaded() {
        for (Runnable listener : LOAD_LISTENERS) {
            if (LOAD_LISTENERS.remove(listener)) {
                try {
                    listener.run();
                } catch (Throwable throwable) {
                    Log.w(TAG, "Settings load listener failed", throwable);
                }
            }
        }
    }

    private static void scheduleLoad(Context context) {
        if (SETTINGS_LOADED.get() || remotePreferences == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now < NEXT_LOAD_UPTIME_MS.get() || !LOAD_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        try {
            lastLoadStatus = "scheduled";
            SETTINGS_EXECUTOR.execute(() -> {
                try {
                    if (load()) {
                        publishLoadedSettings();
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

    /** Refreshes an already loaded target process after LSPosed reports a preference change. */
    private static void scheduleReload() {
        if (remotePreferences == null || !LOAD_IN_FLIGHT.compareAndSet(false, true)) return;
        try {
            SETTINGS_EXECUTOR.execute(() -> {
                try {
                    if (load()) {
                        publishLoadedSettings();
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

    private static void publishLoadedSettings() {
        SETTINGS_LOADED.set(true);
        notifyLoaded();
        LOAD_FAILURES.set(0);
        NEXT_LOAD_UPTIME_MS.set(0L);
        LOAD_FAILURE_LOGGED.set(false);
    }

    private static void scheduleRetry() {
        int failures = Math.min(LOAD_FAILURES.incrementAndGet(), 5);
        long delayMs = Math.min(30_000L, 1_000L << failures);
        NEXT_LOAD_UPTIME_MS.set(SystemClock.uptimeMillis() + delayMs);
    }

    private static boolean load() {
        try {
            SharedPreferences preferences = remotePreferences;
            if (preferences == null) {
                lastLoadStatus = "remote-unavailable";
                return false;
            }
            lastLoadStatus = "remote-preferences";
            Bundle values = preferencesToBundle(preferences);
            notificationsEnabled = values.getBoolean("notifications_enabled", false);
            notificationRadius = values.getFloat("notification_radius", 28f);
            headsUpGlassParametersEnabled = values.getBoolean(
                    "heads_up_glass_parameters_enabled", false);
            headsUpGlassParameters = parseHeadsUpGlassParameters(values.getString(
                    "heads_up_glass_parameters", ""), DEFAULT_HEADS_UP_GLASS_PARAMETERS);
            headsUpGlassDarkParameters = parseHeadsUpGlassParameters(values.getString(
                    "heads_up_glass_dark_parameters", ""),
                    DEFAULT_HEADS_UP_GLASS_DARK_PARAMETERS);
            headsUpBackgroundBlurRadiusEnabled = values.getBoolean(
                    "heads_up_background_blur_radius_enabled", false);
            headsUpBackgroundBlurRadius = Math.max(0, Math.min(200, Math.round(values.getFloat(
                    "heads_up_background_blur_radius", 60f))));
            globalBackgroundBlurPercent = Math.max(0, Math.min(200, Math.round(values.getFloat(
                    "global_background_blur_percent", 100f))));
            controlCenterEnabled = values.getBoolean("control_center_enabled", false);
            controlCenterRadius = values.getFloat("control_center_radius", 28f);
            advancedControlCenterCorners = values.getBoolean("advanced_control_center_corners", false);
            controlCenterTileRadius = values.getFloat("control_center_tile_radius", 28f);
            controlCenterCardRadius = values.getFloat("control_center_card_radius", 28f);
            controlCenterSliderRadius = values.getFloat("control_center_slider_radius", 28f);
            controlCenterDetailSliderRadius = values.getFloat("control_center_detail_slider_radius", 28f);
            controlCenterMediaRadius = values.getFloat("control_center_media_radius", 28f);
            controlCenterExternalEntryRadius = values.getFloat("control_center_external_entry_radius", 28f);
            volumePanelRadius = values.getFloat("volume_panel_radius", 0f);
            miLinkMainCardsEnabled = values.getBoolean("milink_main_cards_enabled", false);
            miLinkMainCardRadius = values.getFloat("milink_main_card_radius", 20f);
            mediaEnabled = values.getBoolean("media_enabled", false);
            expandedHeight = values.getFloat("expanded_height", 152f);
            collapsedHeight = values.getFloat("collapsed_height", 120f);
            fullAodHeight = values.getFloat("full_aod_height", 80f);
            islandEnabled = values.getBoolean("island_enabled", false);
            islandHeight = Math.round(values.getFloat("island_height", 160f));
            islandProgressBar = values.getBoolean("island_progress", false);
            hideAodActions = values.getBoolean("hide_aod_actions", false);
            hideAodSeamless = values.getBoolean("hide_aod_seamless", false);
            sinkLockscreenNotificationsForFingerprint = values.getBoolean(
                    "sink_lockscreen_notifications_for_fingerprint", false);
            hideLockscreenFingerprintIcon = values.getBoolean(
                    "hide_lockscreen_fingerprint_icon", false);
            showLockscreenFingerprintIconOnAod = values.getBoolean(
                    "show_lockscreen_fingerprint_icon_on_aod", false);
            lockscreenPasswordBackgroundBlurEnabled = values.getBoolean(
                    "lockscreen_password_background_blur_enabled", false);
            lockscreenPasswordBackgroundOpacity = Math.max(0f, Math.min(1f, values.getFloat(
                    "lockscreen_password_background_opacity", 0f)));
            lockscreenPasswordBackgroundFollowShadeBlend = values.getBoolean(
                    "lockscreen_password_background_follow_shade_blend", true);
            lockscreenPinKeySoftGlassEnabled = values.getBoolean(
                    "lockscreen_pin_key_soft_glass_enabled", false);
            lockscreenPinKeyGlassExtraRadius = Math.max(0f, Math.min(16f, values.getFloat(
                    "lockscreen_pin_key_glass_extra_radius", 6f)));
            lockscreenPinKeyGlassVerticalGap = Math.max(0f, Math.min(32f, values.getFloat(
                    "lockscreen_pin_key_glass_vertical_gap", 16f)));
            statusBarNetworkTypeEnabled = values.getBoolean("status_bar_network_type_enabled", false);
            statusBarNetworkTypeSize = values.getFloat("status_bar_network_type_size", 13.5f);
            statusBarNetworkTypeBold = values.getBoolean("status_bar_network_type_bold", true);
            statusBarNetworkTypeOffset = values.getFloat("status_bar_network_type_offset", 0f);
            hyperGlassifyHiddenNavigationLift = Math.max(0f, Math.min(48f, values.getFloat(
                    "hyper_glassify_hidden_navigation_lift", 24f)));
            xiaomiHealthFloatingNavigationEnabled = values.getBoolean(
                    "xiaomi_health_floating_navigation_enabled", true);
            xiaomiHealthMiuixIconsEnabled = values.getBoolean(
                    "xiaomi_health_miuix_icons_enabled", false);
            xiaomiHealthMonochromeIconsEnabled = values.getBoolean(
                    "xiaomi_health_monochrome_icons_enabled", true);
            marketFloatingNavigationEnabled = values.getBoolean(
                    "market_floating_navigation_enabled", true);
            marketMiuixIconsEnabled = values.getBoolean(
                    "market_miuix_icons_enabled", false);
            marketMonochromeIconsEnabled = values.getBoolean(
                    "market_monochrome_icons_enabled", true);
            marketNavigationBadgesEnabled = false;
            marketHideGamesTab = values.getBoolean("market_hide_games_tab", false);
            marketHideRankingsTab = values.getBoolean("market_hide_rankings_tab", false);
            marketHideProfileTab = values.getBoolean("market_hide_profile_tab", false);
            miHomeFloatingNavigationEnabled = values.getBoolean(
                    "mi_home_floating_navigation_enabled", true);
            miHomeMiuixIconsEnabled = values.getBoolean(
                    "mi_home_miuix_icons_enabled", false);
            miHomeNavigationBadgesEnabled = values.getBoolean(
                    "mi_home_navigation_badges_enabled", true);
            amapFloatingNavigationEnabled = values.getBoolean(
                    "amap_floating_navigation_enabled", true);
            amapMiuixIconsEnabled = values.getBoolean(
                    "amap_miuix_icons_enabled", false);
            amapMonochromeIconsEnabled = values.getBoolean(
                    "amap_monochrome_icons_enabled", true);
            amapHideLongPressVoiceTabEnabled = values.getBoolean(
                    "amap_hide_long_press_voice_tab_enabled", true);
            xiaomiCommunityFloatingNavigationEnabled = values.getBoolean(
                    "xiaomi_community_floating_navigation_enabled", true);
            xiaomiCommunityMiuixIconsEnabled = values.getBoolean(
                    "xiaomi_community_miuix_icons_enabled", false);
            xiaomiCommunityMonochromeIconsEnabled = values.getBoolean(
                    "xiaomi_community_monochrome_icons_enabled", true);
            xiaomiCommunityNavigationBadgesEnabled = values.getBoolean(
                    "xiaomi_community_navigation_badges_enabled", true);
            spotifyFloatingNavigationEnabled = values.getBoolean(
                    "spotify_floating_navigation_enabled", false);
            spotifyFavoriteButtonEnabled = values.getBoolean(
                    "spotify_favorite_button_enabled", false);
            spotifyShuffleButtonEnabled = values.getBoolean(
                    "spotify_shuffle_button_enabled", false);
            customMediaConstraintSetEnabled = values.getBoolean("custom_media_constraint_set_enabled", false);
            customMediaConstraintSetXml = values.getString("custom_media_constraint_set_xml", "");
            lastLoadStatus = "loaded(remote:" + values.size() + ')';
            return true;
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            lastLoadStatus = throwable.getClass().getSimpleName() +
                    (message == null || message.isEmpty() ? "" : ":" +
                            message.replace('\n', ' ').substring(0, Math.min(96, message.length())));
            if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
                Log.w(TAG, "Settings unavailable; using defaults and retrying in background", throwable);
            }
            return false;
        }
    }

    private static Bundle preferencesToBundle(SharedPreferences preferences) {
        Bundle values = new Bundle();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) values.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) values.putFloat(key, (Float) value);
            else if (value instanceof Integer) values.putInt(key, (Integer) value);
            else if (value instanceof Long) values.putLong(key, (Long) value);
            else if (value instanceof String) values.putString(key, (String) value);
        }
        return values;
    }

    private static float[] parseHeadsUpGlassParameters(String serialized, float[] fallback) {
        if (serialized == null) return fallback.clone();
        String[] values = serialized.split(",", -1);
        if (values.length != fallback.length) return fallback.clone();
        float[] parsed = new float[fallback.length];
        try {
            for (int index = 0; index < values.length; index++) {
                float value = Float.parseFloat(values[index].trim());
                if (Float.isNaN(value) || Float.isInfinite(value)) return fallback.clone();
                parsed[index] = value;
            }
            return parsed;
        } catch (RuntimeException exception) {
            return fallback.clone();
        }
    }
}
