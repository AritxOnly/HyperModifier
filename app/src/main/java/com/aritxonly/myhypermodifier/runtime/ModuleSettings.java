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
    static volatile boolean moduleHooksEnabled = true;
    static volatile boolean systemUiCompatibilityMode;
    static volatile int systemUiRetryGeneration;
    static volatile String diagnosticToken = "";
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean();
    private static final AtomicBoolean LOAD_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean REMOTE_LISTENER_REGISTERED = new AtomicBoolean();
    private static final AtomicInteger LOAD_FAILURES = new AtomicInteger();
    private static final AtomicLong NEXT_LOAD_UPTIME_MS = new AtomicLong();
    private static final CopyOnWriteArrayList<Runnable> LOAD_LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> CHANGE_LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final ExecutorService SETTINGS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MyHyperModifier-settings");
        thread.setDaemon(true);
        return thread;
    });

    static volatile boolean notificationsEnabled = true;
    static volatile float notificationRadius = 28f;
    static volatile boolean hideHeadsUpMiniBar = false;
    static volatile boolean headsUpBottomMarginEnabled = false;
    static volatile float headsUpBottomMarginDp = 13f;
    static volatile boolean headsUpGlassParametersEnabled = false;
    static volatile boolean shadeCardGlassParametersEnabled = false;
    static volatile boolean disableShadeGlassHooks = false;
    static volatile boolean globalGlassBlurEnabled = false;
    private static volatile float[] shadeCardGlassParameters = ShadeCardGlassPolicy.defaults();
    static volatile int shadeCardBackgroundBlurPercent = 100;
    static volatile boolean shadeCardGlassBlurEnabled = false;
    static volatile int shadeCardGlassBlurRadius = 20;
    static volatile boolean headsUpBackgroundBlurRadiusEnabled = false;
    static volatile int headsUpBackgroundBlurRadius = 60;
    static volatile int globalBackgroundBlurPercent = 100;
    static volatile boolean globalBackgroundDimEnabled = false;
    static volatile float globalBackgroundDimPercent = 20f;
    static volatile boolean controlCenterFollowMiLinkBackgroundMaterial = true;
    private static final float[] DEFAULT_HEADS_UP_GLASS_PARAMETERS =
            HeadsUpGlassDefaults.regular();
    private static final float[] DEFAULT_HEADS_UP_GLASS_DARK_PARAMETERS =
            HeadsUpGlassDefaults.dark();
    private static volatile float[] headsUpGlassParameters =
            DEFAULT_HEADS_UP_GLASS_PARAMETERS.clone();
    private static volatile float[] headsUpGlassDarkParameters =
            DEFAULT_HEADS_UP_GLASS_DARK_PARAMETERS.clone();
    static volatile boolean controlCenterEnabled = true;
    static volatile float controlCenterRadius = 28f;
    static volatile boolean advancedControlCenterCorners = false;
    static volatile float controlCenterTileRadius = 28f;
    static volatile float controlCenterCardRadius = 28f;
    static volatile float controlCenterSliderRadius = 28f;
    static volatile float controlCenterDetailSliderRadius = 28f;
    static volatile float controlCenterMediaRadius = 28f;
    static volatile float controlCenterExternalEntryRadius = 28f;
    static volatile float volumePanelRadius = ModuleDefaultValues.VOLUME_PANEL_RADIUS;
    static volatile boolean miLinkMainCardsEnabled = ModuleDefaultValues.MI_LINK_MAIN_CARDS_ENABLED;
    static volatile float miLinkMainCardRadius = ModuleDefaultValues.MI_LINK_MAIN_CARD_RADIUS;
    static volatile boolean mediaEnabled = ModuleDefaultValues.MEDIA_ENABLED;
    static volatile float expandedHeight = 152f;
    static volatile float collapsedHeight = 120f;
    static volatile float fullAodHeight = 80f;
    static volatile boolean islandEnabled = ModuleDefaultValues.ISLAND_ENABLED;
    static volatile int islandHeight = 160;
    static volatile boolean islandProgressBar = ModuleDefaultValues.ISLAND_PROGRESS_BAR;
    static volatile boolean superIslandWhitelistDisabled = false;
    static volatile boolean superIslandHidePullBar = false;
    static volatile boolean superIslandContentBottomMarginEnabled = false;
    static volatile float superIslandContentBottomMarginDp = 8f;
    static volatile boolean hideAodActions = ModuleDefaultValues.HIDE_AOD_ACTIONS;
    static volatile boolean hideAodSeamless = false;
    static volatile boolean sinkLockscreenNotificationsForFingerprint = false;
    static volatile boolean hideLockscreenFingerprintIcon = false;
    static volatile boolean lowerLockscreenPasswordPage = false;
    static volatile boolean showLockscreenFingerprintIconOnAod = false;
    static volatile String aboutPhoneImageSource = "auto";
    static volatile String aboutPhonePresetImageKey = "";
    static volatile String aboutPhonePresetImage = "";
    static volatile String aboutPhoneCustomImage = "";
    static volatile String aboutPhoneImageTransforms = "{}";
    static volatile boolean settingsHomeEntryEnabled = false;
    static volatile boolean settingsModulesEntryEnabled;
    static volatile String settingsHomeEntryPosition = ModuleDefaultValues.SETTINGS_HOME_ENTRY_POSITION;
    static volatile String settingsManagerEntryPosition = ModuleDefaultValues.SETTINGS_MANAGER_ENTRY_POSITION;
    static volatile String settingsModuleEntries = "{}";
    static volatile boolean aboutPhoneCardsEnabled = ModuleDefaultValues.ABOUT_PHONE_CARDS_ENABLED;
    static volatile boolean forceLockscreenClockColon = false;
    static volatile boolean bypassHyperMusicCoverClockAdjustment = false;
    static volatile boolean progressiveLockscreenClockAvoidance = false;
    static volatile boolean aodClockWeightEnabled = ModuleDefaultValues.AOD_CLOCK_WEIGHT_ENABLED;
    static volatile int aodClockWeight = (int) ModuleDefaultValues.AOD_CLOCK_WEIGHT;
    static volatile boolean lockscreenPasswordBackgroundBlurEnabled = false;
    static volatile float lockscreenPasswordBackgroundOpacity = 0f;
    static volatile boolean lockscreenPasswordBackgroundFollowShadeBlend = true;
    static volatile boolean lockscreenPinKeySoftGlassEnabled = false;
    static volatile float lockscreenPinKeyGlassExtraRadius = 6f;
    static volatile float lockscreenPinKeyGlassVerticalGap = 16f;
    static volatile boolean statusBarHideMobileTypeOnWifi = false;
    static volatile boolean statusBarHideMobileActivity = false;
    static volatile boolean statusBarHideWifiActivity = false;
    static volatile boolean statusBarHideWifiStandard = false;
    static volatile float statusBarMobileActivityOffsetX = 0f;
    static volatile float statusBarMobileActivityOffsetY = 0f;
    static volatile float statusBarWifiActivityOffsetX = 0f;
    static volatile float statusBarWifiActivityOffsetY = 0f;
    static volatile float statusBarNetworkSpeedRightGap = 0f;
    static volatile boolean statusBarNetworkTypeEnabled = false;
    static volatile float statusBarNetworkTypeSize = 13.5f;
    static volatile boolean statusBarNetworkTypeBold = true;
    static volatile float statusBarNetworkTypeOffset = 0f;
    // A target process can begin drawing before the companion Provider becomes reachable. Keep
    // that bootstrap state visually neutral; the saved default is published once loading succeeds.
    static volatile float hyperGlassifyHiddenNavigationLift = 0f;
    static volatile boolean xiaomiHealthFloatingNavigationEnabled = ModuleDefaultValues.XIAOMI_HEALTH_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean xiaomiHealthMiuixIconsEnabled = false;
    static volatile boolean xiaomiHealthMonochromeIconsEnabled = true;
    static volatile boolean marketFloatingNavigationEnabled = ModuleDefaultValues.MARKET_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean marketMiuixIconsEnabled = false;
    static volatile boolean marketMonochromeIconsEnabled = true;
    static volatile boolean marketNavigationBadgesEnabled = false;
    static volatile boolean marketHideGamesTab = false;
    static volatile boolean marketHideRankingsTab = false;
    static volatile boolean marketHideProfileTab = false;
    static volatile boolean miHomeFloatingNavigationEnabled = ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean miHomeMiuixIconsEnabled = false;
    static volatile boolean miHomeNavigationBadgesEnabled = true;
    static volatile boolean amapFloatingNavigationEnabled = ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean amapMiuixIconsEnabled = false;
    static volatile boolean amapMonochromeIconsEnabled = true;
    static volatile boolean amapHideLongPressVoiceTabEnabled = ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED;
    static volatile boolean xiaomiCommunityFloatingNavigationEnabled = ModuleDefaultValues.XIAOMI_COMMUNITY_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean xiaomiCommunityMiuixIconsEnabled = false;
    static volatile boolean xiaomiCommunityMonochromeIconsEnabled = true;
    static volatile boolean xiaomiCommunityNavigationBadgesEnabled = true;
    static volatile boolean bilibiliFloatingNavigationEnabled = ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean bilibiliNavigationBadgesEnabled = true;
    static volatile boolean bilibiliHomeTabVisible = true;
    static volatile boolean bilibiliFollowTabVisible = true;
    static volatile boolean bilibiliDynamicTabVisible = true;
    static volatile boolean bilibiliMallTabVisible = true;
    static volatile boolean bilibiliMineTabVisible = true;
    static volatile boolean bilibiliPublishButtonVisible = true;
    static volatile boolean spotifyFloatingNavigationEnabled = ModuleDefaultValues.SPOTIFY_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean spotifyFavoriteButtonEnabled = ModuleDefaultValues.SPOTIFY_FAVORITE_BUTTON_ENABLED;
    static volatile boolean spotifyShuffleButtonEnabled = ModuleDefaultValues.SPOTIFY_SHUFFLE_BUTTON_ENABLED;
    static volatile boolean inFullAod;
    static volatile boolean customMediaConstraintSetEnabled = false;
    static volatile String customMediaConstraintSetXml = "";
    static volatile boolean customMediaIslandConstraintSetEnabled = false;
    static volatile String customMediaIslandConstraintSetXml = "";
    static volatile String mediaLayoutPreset = ModuleDefaultValues.MEDIA_LAYOUT_PRESET;
    static volatile int systemMediaHeight = 168;
    static volatile int compactMediaHeight = 84;
    static volatile int standardMediaHeight = 150;
    static volatile int customMediaHeight = 150;
    static volatile String systemMediaXml = "";
    static volatile String systemMediaIslandXml = "";
    static volatile String compactMediaXml = "";
    static volatile String compactMediaIslandXml = "";
    static volatile String standardMediaXml = "";
    static volatile String standardMediaIslandXml = "";
    private static volatile String lastLoadStatus = "not-requested";
    // Supplied by XposedInterface#getRemotePreferences. It is specifically designed for module
    // state and stays available even when Android hides this module package from the target app.
    private static volatile SharedPreferences remotePreferences;

    static volatile boolean gestureHandleEnabled = false;
    static volatile Map<String, String> gestureHandleAppModes = java.util.Collections.emptyMap();
    static volatile String gestureHandlePreset = GestureHandlePresets.STOCK;
    static volatile boolean gestureHandleTouchReveal = false;
    static volatile boolean gestureHandleSwipeMotion = false;
    static volatile float gestureHandleTouchAreaDp = GestureHandleTouchArea.DEFAULT_DP;
    static volatile java.util.Set<String> gestureHandleScopePackages = java.util.Collections.emptySet();

    private ModuleSettings() {
    }

    /**
     * Connects this target process to the LSPosed-owned preferences bridge. This must happen
     * from XposedModule rather than through a target-app ContentResolver: Android 11+ package
     * visibility can reject the latter even when the module is installed and enabled.
     */
    static void setRemotePreferences(SharedPreferences preferences) {
        if (preferences == null) {
            moduleHooksEnabled = false;
            systemUiCompatibilityMode = true;
            diagnosticToken = "";
            return;
        }
        remotePreferences = preferences;
        // Read this gate before PackageLoaded installs native Glass hooks. No Application or
        // provider IPC is needed: LSPosed has already supplied the preferences snapshot.
        try {
            moduleHooksEnabled = preferences.getBoolean("module_hooks_enabled", true);
            settingsHomeEntryEnabled = preferences.getBoolean("settings_home_entry_enabled", false);
            settingsModulesEntryEnabled = preferences.getBoolean("settings_modules_entry_enabled", false);
            settingsHomeEntryPosition = SettingsHomeHeaderPolicy.normalizePosition(preferences.getString("settings_home_entry_position", ModuleDefaultValues.SETTINGS_HOME_ENTRY_POSITION));
            settingsManagerEntryPosition = SettingsHomeHeaderPolicy.normalizePosition(preferences.getString("settings_manager_entry_position",
                    preferences.contains("settings_home_entry_position")
                            ? settingsHomeEntryPosition : ModuleDefaultValues.SETTINGS_MANAGER_ENTRY_POSITION));
            settingsModuleEntries = preferences.getString("settings_module_entries", "{}");
            systemUiCompatibilityMode = preferences.getBoolean("systemui_compatibility_mode", false);
            systemUiRetryGeneration = preferences.getInt("systemui_retry_generation", 0);
            String token = preferences.getString("diagnostic_token", "");
            diagnosticToken = token == null ? "" : token;
            disableShadeGlassHooks = preferences.getBoolean("disable_shade_glass_hooks", false);
        } catch (Throwable throwable) {
            moduleHooksEnabled = false;
            systemUiCompatibilityMode = true;
            diagnosticToken = "";
            Log.w(TAG, "Could not read early hook safety gates", throwable);
        }
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

    /** Persistent observer for surfaces that must replay after every remote settings snapshot. */
    static void onChanged(Runnable listener) {
        CHANGE_LISTENERS.addIfAbsent(listener);
        if (SETTINGS_LOADED.get()) listener.run();
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
        for (Runnable listener : CHANGE_LISTENERS) {
            try { listener.run(); }
            catch (Throwable throwable) { Log.w(TAG, "Settings change listener failed", throwable); }
        }
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
            moduleHooksEnabled = values.getBoolean("module_hooks_enabled", true);
            systemUiCompatibilityMode = values.getBoolean("systemui_compatibility_mode", false);
            systemUiRetryGeneration = values.getInt("systemui_retry_generation", 0);
            gestureHandleEnabled = values.getBoolean("gesture_handle_enabled", false);
            gestureHandlePreset = GestureHandlePresets.fromStored(
                    values.getString("gesture_handle_preset"),
                    values.getBoolean("gesture_handle_module_preset", false));
            boolean newModuleDefaults = GestureHandlePresets.MODULE.equals(gestureHandlePreset)
                    && !values.containsKey("gesture_handle_app_modes");
            gestureHandleAppModes = GestureHandleRules.decode(values.getString("gesture_handle_app_modes", ""));
            gestureHandleTouchReveal = values.getBoolean("gesture_handle_touch_reveal", newModuleDefaults);
            gestureHandleSwipeMotion = values.getBoolean("gesture_handle_swipe_motion", newModuleDefaults);
            gestureHandleTouchAreaDp = GestureHandleTouchArea.normalize(values.getFloat(
                    "gesture_handle_touch_area_dp", GestureHandleTouchArea.DEFAULT_DP));
            java.util.Set<String> scope = new java.util.HashSet<>();
            for (String name : values.getString("gesture_handle_scope_packages", "").split("\n")) {
                if (!name.isEmpty()) scope.add(name);
            }
            gestureHandleScopePackages = java.util.Collections.unmodifiableSet(scope);
            notificationsEnabled = values.getBoolean("notifications_enabled", true);
            notificationRadius = values.getFloat("notification_radius", 28f);
            hideHeadsUpMiniBar = values.getBoolean("hide_heads_up_mini_bar", false);
            headsUpBottomMarginEnabled = values.getBoolean(
                    "heads_up_bottom_margin_enabled", false);
            float requestedBottomMargin = values.getFloat("heads_up_bottom_margin_dp", 13f);
            headsUpBottomMarginDp = Float.isNaN(requestedBottomMargin)
                    || Float.isInfinite(requestedBottomMargin) ? 13f
                    : Math.max(0f, Math.min(32f, requestedBottomMargin));
            headsUpGlassParametersEnabled = values.getBoolean(
                    "heads_up_glass_parameters_enabled", false);
            shadeCardGlassParametersEnabled = values.getBoolean("shade_card_glass_parameters_enabled", false);
            disableShadeGlassHooks = values.getBoolean("disable_shade_glass_hooks", false);
            globalGlassBlurEnabled = values.getBoolean("global_glass_blur_enabled", shadeCardGlassParametersEnabled);
            shadeCardGlassParameters = parseHeadsUpGlassParameters(values.getString(
                    "shade_card_glass_parameters", ""), ShadeCardGlassPolicy.defaults());
            shadeCardBackgroundBlurPercent = Math.round(ShadeCardGlassPolicy.normalize(values.getFloat(
                    "shade_card_background_blur_percent", 100f), 200f, 100f));
            shadeCardGlassBlurEnabled = values.getBoolean("shade_card_glass_blur_enabled", false);
            shadeCardGlassBlurRadius = Math.round(ShadeCardGlassPolicy.normalize(values.getFloat(
                    "shade_card_glass_blur_radius", 20f), ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS, 20f));
            headsUpGlassParameters = parseHeadsUpGlassParameters(values.getString(
                    "heads_up_glass_parameters", ""), DEFAULT_HEADS_UP_GLASS_PARAMETERS);
            headsUpGlassDarkParameters = parseHeadsUpGlassParameters(values.getString(
                    "heads_up_glass_dark_parameters", ""),
                    DEFAULT_HEADS_UP_GLASS_DARK_PARAMETERS);
            if (HeadsUpGlassDefaults.isLegacyDefaultPair(
                    headsUpGlassParameters, headsUpGlassDarkParameters)) {
                headsUpGlassParameters = HeadsUpGlassDefaults.regular();
                headsUpGlassDarkParameters = HeadsUpGlassDefaults.dark();
            }
            headsUpBackgroundBlurRadiusEnabled = values.getBoolean(
                    "heads_up_background_blur_radius_enabled", false);
            headsUpBackgroundBlurRadius = Math.round(ShadeCardGlassPolicy.normalize(values.getFloat(
                    "heads_up_background_blur_radius", 60f), ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS, 60f));
            globalBackgroundBlurPercent = Math.max(0, Math.min(200, Math.round(values.getFloat(
                    "global_background_blur_percent", 100f))));
            globalBackgroundDimEnabled = values.getBoolean("global_background_dim_enabled", false);
            globalBackgroundDimPercent = BackgroundDimPolicy.normalizePercent(values.getFloat(
                    "global_background_dim_percent", 20f));
            // The new key ignores the earlier visible switch's saved false state.
            controlCenterFollowMiLinkBackgroundMaterial = values.getBoolean(
                    "control_center_follow_milink_background_material_default_on", true);
            controlCenterEnabled = values.getBoolean("control_center_enabled", true);
            controlCenterRadius = values.getFloat("control_center_radius", 28f);
            advancedControlCenterCorners = values.getBoolean("advanced_control_center_corners", false);
            controlCenterTileRadius = values.getFloat("control_center_tile_radius", 28f);
            controlCenterCardRadius = values.getFloat("control_center_card_radius", 28f);
            controlCenterSliderRadius = values.getFloat("control_center_slider_radius", 28f);
            controlCenterDetailSliderRadius = values.getFloat("control_center_detail_slider_radius", 28f);
            controlCenterMediaRadius = values.getFloat("control_center_media_radius", 28f);
            controlCenterExternalEntryRadius = values.getFloat("control_center_external_entry_radius", 28f);
            volumePanelRadius = values.getFloat("volume_panel_radius", ModuleDefaultValues.VOLUME_PANEL_RADIUS);
            miLinkMainCardsEnabled = values.getBoolean("milink_main_cards_enabled", ModuleDefaultValues.MI_LINK_MAIN_CARDS_ENABLED);
            miLinkMainCardRadius = values.getFloat("milink_main_card_radius", ModuleDefaultValues.MI_LINK_MAIN_CARD_RADIUS);
            mediaEnabled = values.getBoolean("media_enabled", ModuleDefaultValues.MEDIA_ENABLED);
            expandedHeight = values.getFloat("expanded_height", 152f);
            collapsedHeight = values.getFloat("collapsed_height", 120f);
            fullAodHeight = values.getFloat("full_aod_height", 80f);
            islandEnabled = values.getBoolean("island_enabled", ModuleDefaultValues.ISLAND_ENABLED);
            islandHeight = Math.round(values.getFloat("island_height", 160f));
            islandProgressBar = values.getBoolean("island_progress", ModuleDefaultValues.ISLAND_PROGRESS_BAR);
            superIslandWhitelistDisabled = values.getBoolean("super_island_whitelist_disabled", false);
            superIslandHidePullBar = values.getBoolean("super_island_hide_pull_bar", false);
            superIslandContentBottomMarginEnabled = values.getBoolean(
                    "super_island_content_bottom_margin_enabled",
                    values.getBoolean("super_island_pull_bar_bottom_margin_enabled", false));
            float requestedSuperIslandContentMargin = values.getFloat(
                    "super_island_content_bottom_margin_dp",
                    values.getFloat("super_island_pull_bar_bottom_margin_dp", 8f));
            superIslandContentBottomMarginDp = Float.isNaN(requestedSuperIslandContentMargin)
                    || Float.isInfinite(requestedSuperIslandContentMargin) ? 8f
                    : Math.max(0f, Math.min(48f, requestedSuperIslandContentMargin));
            hideAodActions = values.getBoolean("hide_aod_actions", ModuleDefaultValues.HIDE_AOD_ACTIONS);
            hideAodSeamless = values.getBoolean("hide_aod_seamless", false);
            sinkLockscreenNotificationsForFingerprint = values.getBoolean(
                    "sink_lockscreen_notifications_for_fingerprint", false);
            hideLockscreenFingerprintIcon = values.getBoolean(
                    "hide_lockscreen_fingerprint_icon", false);
            lowerLockscreenPasswordPage = values.getBoolean(
                    "lower_lockscreen_password_page", false);
            showLockscreenFingerprintIconOnAod = values.getBoolean(
                    "show_lockscreen_fingerprint_icon_on_aod", false);
            aboutPhoneImageSource = values.getString("about_phone_image_source", "auto");
            aboutPhonePresetImageKey = values.getString("about_phone_preset_image_key", "");
            aboutPhonePresetImage = values.getString("about_phone_preset_image", "");
            aboutPhoneCustomImage = values.getString("about_phone_custom_image", "");
            aboutPhoneImageTransforms = values.getString("about_phone_image_transforms", "{}");
            settingsHomeEntryEnabled = values.getBoolean("settings_home_entry_enabled", false);
            settingsModulesEntryEnabled = values.getBoolean("settings_modules_entry_enabled", false);
            settingsHomeEntryPosition = SettingsHomeHeaderPolicy.normalizePosition(values.getString("settings_home_entry_position", ModuleDefaultValues.SETTINGS_HOME_ENTRY_POSITION));
            settingsManagerEntryPosition = SettingsHomeHeaderPolicy.normalizePosition(values.getString("settings_manager_entry_position",
                    values.containsKey("settings_home_entry_position")
                            ? settingsHomeEntryPosition : ModuleDefaultValues.SETTINGS_MANAGER_ENTRY_POSITION));
            settingsModuleEntries = values.getString("settings_module_entries", "{}");
            aboutPhoneCardsEnabled = values.getBoolean("about_phone_cards_enabled", ModuleDefaultValues.ABOUT_PHONE_CARDS_ENABLED);
            forceLockscreenClockColon = values.getBoolean("force_lockscreen_clock_colon", false);
            bypassHyperMusicCoverClockAdjustment = values.getBoolean("bypass_hyper_music_cover_clock_adjustment", false);
            progressiveLockscreenClockAvoidance = values.getBoolean("progressive_lockscreen_clock_avoidance", false);
            aodClockWeightEnabled = values.getBoolean("aod_clock_weight_enabled", ModuleDefaultValues.AOD_CLOCK_WEIGHT_ENABLED);
            float requestedAodWeight = values.getFloat("aod_clock_weight", ModuleDefaultValues.AOD_CLOCK_WEIGHT);
            aodClockWeight = Float.isNaN(requestedAodWeight)
                    || Float.isInfinite(requestedAodWeight) ? (int) ModuleDefaultValues.AOD_CLOCK_WEIGHT
                    : Math.max(100, Math.min(700, Math.round(requestedAodWeight)));
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
            statusBarHideMobileTypeOnWifi = values.getBoolean("status_bar_hide_mobile_on_wifi", false);
            statusBarHideMobileActivity = values.getBoolean("status_bar_hide_mobile_activity", false);
            statusBarHideWifiActivity = values.getBoolean("status_bar_hide_wifi_activity", false);
            statusBarHideWifiStandard = values.getBoolean("status_bar_hide_wifi_standard", false);
            statusBarMobileActivityOffsetX = values.getFloat("status_bar_mobile_activity_offset_x", 0f);
            statusBarMobileActivityOffsetY = values.getFloat("status_bar_mobile_activity_offset_y", 0f);
            statusBarWifiActivityOffsetX = values.getFloat("status_bar_wifi_activity_offset_x", 0f);
            statusBarWifiActivityOffsetY = values.getFloat("status_bar_wifi_activity_offset_y", 0f);
            statusBarNetworkSpeedRightGap = values.getFloat("status_bar_network_speed_right_gap", 0f);
            statusBarNetworkTypeEnabled = values.getBoolean("status_bar_network_type_enabled", false);
            statusBarNetworkTypeSize = values.getFloat("status_bar_network_type_size", 13.5f);
            statusBarNetworkTypeBold = values.getBoolean("status_bar_network_type_bold", true);
            statusBarNetworkTypeOffset = values.getFloat("status_bar_network_type_offset", 0f);
            hyperGlassifyHiddenNavigationLift = Math.max(0f, Math.min(48f, values.getFloat(
                    "hyper_glassify_hidden_navigation_lift", ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT)));
            xiaomiHealthFloatingNavigationEnabled = values.getBoolean(
                    "xiaomi_health_floating_navigation_enabled", ModuleDefaultValues.XIAOMI_HEALTH_FLOATING_NAVIGATION_ENABLED);
            xiaomiHealthMiuixIconsEnabled = values.getBoolean(
                    "xiaomi_health_miuix_icons_enabled", false);
            xiaomiHealthMonochromeIconsEnabled = values.getBoolean(
                    "xiaomi_health_monochrome_icons_enabled", true);
            marketFloatingNavigationEnabled = values.getBoolean(
                    "market_floating_navigation_enabled", ModuleDefaultValues.MARKET_FLOATING_NAVIGATION_ENABLED);
            marketMiuixIconsEnabled = values.getBoolean(
                    "market_miuix_icons_enabled", false);
            marketMonochromeIconsEnabled = values.getBoolean(
                    "market_monochrome_icons_enabled", true);
            marketNavigationBadgesEnabled = false;
            marketHideGamesTab = values.getBoolean("market_hide_games_tab", false);
            marketHideRankingsTab = values.getBoolean("market_hide_rankings_tab", false);
            marketHideProfileTab = values.getBoolean("market_hide_profile_tab", false);
            miHomeFloatingNavigationEnabled = values.getBoolean(
                    "mi_home_floating_navigation_enabled", ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED);
            miHomeMiuixIconsEnabled = values.getBoolean(
                    "mi_home_miuix_icons_enabled", false);
            miHomeNavigationBadgesEnabled = values.getBoolean(
                    "mi_home_navigation_badges_enabled", true);
            amapFloatingNavigationEnabled = values.getBoolean(
                    "amap_floating_navigation_enabled", ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED);
            amapMiuixIconsEnabled = values.getBoolean(
                    "amap_miuix_icons_enabled", false);
            amapMonochromeIconsEnabled = values.getBoolean(
                    "amap_monochrome_icons_enabled", true);
            amapHideLongPressVoiceTabEnabled = values.getBoolean(
                    "amap_hide_long_press_voice_tab_enabled", ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED);
            xiaomiCommunityFloatingNavigationEnabled = values.getBoolean(
                    "xiaomi_community_floating_navigation_enabled", ModuleDefaultValues.XIAOMI_COMMUNITY_FLOATING_NAVIGATION_ENABLED);
            xiaomiCommunityMiuixIconsEnabled = values.getBoolean(
                    "xiaomi_community_miuix_icons_enabled", false);
            xiaomiCommunityMonochromeIconsEnabled = values.getBoolean(
                    "xiaomi_community_monochrome_icons_enabled", true);
            xiaomiCommunityNavigationBadgesEnabled = values.getBoolean(
                    "xiaomi_community_navigation_badges_enabled", true);
            bilibiliFloatingNavigationEnabled = values.getBoolean("bilibili_floating_navigation_enabled", ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED);
            bilibiliNavigationBadgesEnabled = values.getBoolean("bilibili_navigation_badges_enabled", true);
            bilibiliHomeTabVisible = values.getBoolean("bilibili_home_tab_visible", true);
            bilibiliFollowTabVisible = values.getBoolean("bilibili_follow_tab_visible", true);
            bilibiliDynamicTabVisible = values.getBoolean("bilibili_dynamic_tab_visible", true);
            bilibiliMallTabVisible = values.getBoolean("bilibili_mall_tab_visible", true);
            bilibiliMineTabVisible = values.getBoolean("bilibili_mine_tab_visible", true);
            bilibiliPublishButtonVisible = values.getBoolean("bilibili_publish_button_visible", true);
            spotifyFloatingNavigationEnabled = values.getBoolean(
                    "spotify_floating_navigation_enabled", ModuleDefaultValues.SPOTIFY_FLOATING_NAVIGATION_ENABLED);
            spotifyFavoriteButtonEnabled = values.getBoolean(
                    "spotify_favorite_button_enabled", ModuleDefaultValues.SPOTIFY_FAVORITE_BUTTON_ENABLED);
            spotifyShuffleButtonEnabled = values.getBoolean(
                    "spotify_shuffle_button_enabled", ModuleDefaultValues.SPOTIFY_SHUFFLE_BUTTON_ENABLED);
            customMediaConstraintSetEnabled = values.getBoolean("custom_media_constraint_set_enabled", false);
            customMediaConstraintSetXml = values.getString("custom_media_constraint_set_xml", "");
            customMediaIslandConstraintSetEnabled = values.getBoolean(
                    "custom_media_island_constraint_set_enabled", false);
            customMediaIslandConstraintSetXml = values.getString(
                    "custom_media_island_constraint_set_xml", "");
            mediaLayoutPreset = values.getString("media_layout_preset",
                    values.containsKey("media_enabled") || values.containsKey("island_enabled")
                            || values.containsKey("custom_media_constraint_set_enabled")
                            ? "" : ModuleDefaultValues.MEDIA_LAYOUT_PRESET);
            systemMediaHeight = boundedMediaHeight(values.getFloat("system_media_height", 168f), 168);
            compactMediaHeight = boundedMediaHeight(values.getFloat("compact_media_height", 84f), 84);
            standardMediaHeight = boundedMediaHeight(values.getFloat("standard_media_height", 150f), 150);
            customMediaHeight = boundedMediaHeight(values.getFloat("custom_media_height", 150f), 150);
            systemMediaXml = values.getString("system_media_xml", "");
            systemMediaIslandXml = values.getString("system_media_island_xml", "");
            compactMediaXml = values.getString("compact_media_xml", "");
            compactMediaIslandXml = values.getString("compact_media_island_xml", "");
            standardMediaXml = values.getString("standard_media_xml", "");
            standardMediaIslandXml = values.getString("standard_media_island_xml", "");
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

    private static int boundedMediaHeight(float requested, int fallback) {
        if (Float.isNaN(requested) || Float.isInfinite(requested)) return fallback;
        return Math.max(84, Math.min(240, Math.round(requested)));
    }

    static int activeMediaPresetHeight() {
        switch (mediaLayoutPreset) {
            case "system": return systemMediaHeight;
            case "compact": return compactMediaHeight;
            case "standard": return standardMediaHeight;
            case "custom": return customMediaHeight;
            default: return 0;
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

    static float[] tuneShadeCardGlass(float[] original) {
        return ShadeCardGlassPolicy.tune(original, shadeCardGlassParameters);
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
