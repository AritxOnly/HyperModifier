package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.libxposed.service.XposedService
import java.util.UUID

data class ModifierSettings(
    val settingsHomeEntryEnabled: Boolean = false,
    val settingsModulesEntryEnabled: Boolean = false,
    val settingsHomeEntryPosition: String = ModuleDefaultValues.SETTINGS_HOME_ENTRY_POSITION,
    val settingsManagerEntryPosition: String = ModuleDefaultValues.SETTINGS_MANAGER_ENTRY_POSITION,
    val settingsModuleEntries: String = "{}",
    val aboutPhoneCardsEnabled: Boolean = ModuleDefaultValues.ABOUT_PHONE_CARDS_ENABLED,
    val aboutPhoneImageSource: String = "auto",
    val aboutPhonePresetUrl: String = PhonePresetRepository.DEFAULT_URL,
    val aboutPhoneCustomImage: String = "",
    val aboutPhoneImageTransforms: String = "{}",
    val moduleHooksEnabled: Boolean = true,
    val systemUiCompatibilityMode: Boolean = false,
    val systemUiRetryGeneration: Int = 0,
    val gestureHandleEnabled: Boolean = false,
    val gestureHandlePreset: String = GestureHandlePresets.STOCK,
    val gestureHandleAppModes: Map<String, String> = emptyMap(),
    val gestureHandleTouchReveal: Boolean = false,
    val gestureHandleSwipeMotion: Boolean = false,
    val gestureHandleTouchAreaDp: Float = GestureHandleTouchArea.DEFAULT_DP,
    val notificationsEnabled: Boolean = true,
    val notificationRadius: Float = 28f,
    val hideHeadsUpMiniBar: Boolean = false,
    val headsUpBottomMarginEnabled: Boolean = false,
    val headsUpBottomMarginDp: Float = 13f,
    val headsUpGlassParametersEnabled: Boolean = false,
    val headsUpGlassParameters: String = HeadsUpGlassParameters.regularSerialized,
    val headsUpGlassDarkParameters: String = HeadsUpGlassParameters.darkSerialized,
    val shadeCardGlassParametersEnabled: Boolean = false,
    val disableShadeGlassHooks: Boolean = false,
    val shadeCardGlassParameters: String = HeadsUpGlassParameters.serialize(ShadeCardGlassPolicy.defaults()),
    val globalGlassBlurEnabled: Boolean = false,
    val shadeCardBackgroundBlurPercent: Float = 100f,
    val shadeCardGlassBlurEnabled: Boolean = false,
    val shadeCardGlassBlurRadius: Float = 20f,
    val headsUpBackgroundBlurRadiusEnabled: Boolean = false,
    val headsUpBackgroundBlurRadius: Float = 60f,
    /** Multiplies the stock blur radius for shared SystemUI background surfaces. */
    val globalBackgroundBlurPercent: Float = 100f,
    val globalBackgroundDimEnabled: Boolean = false,
    val globalBackgroundDimPercent: Float = 20f,
    val controlCenterFollowMiLinkBackgroundMaterial: Boolean = true,
    val controlCenterEnabled: Boolean = true,
    val controlCenterRadius: Float = 28f,
    val advancedControlCenterCorners: Boolean = false,
    val controlCenterTileRadius: Float = 28f,
    val controlCenterCardRadius: Float = 28f,
    val controlCenterSliderRadius: Float = 28f,
    val controlCenterDetailSliderRadius: Float = 28f,
    val controlCenterMediaRadius: Float = 28f,
    val controlCenterExternalEntryRadius: Float = 28f,
    val volumePanelRadius: Float = ModuleDefaultValues.VOLUME_PANEL_RADIUS,
    val miLinkMainCardsEnabled: Boolean = ModuleDefaultValues.MI_LINK_MAIN_CARDS_ENABLED,
    val miLinkMainCardRadius: Float = ModuleDefaultValues.MI_LINK_MAIN_CARD_RADIUS,
    val mediaEnabled: Boolean = ModuleDefaultValues.MEDIA_ENABLED,
    val expandedHeight: Float = 152f,
    val collapsedHeight: Float = 120f,
    val fullAodHeight: Float = 80f,
    val islandEnabled: Boolean = ModuleDefaultValues.ISLAND_ENABLED,
    val islandHeight: Float = 160f,
    val islandProgressBar: Boolean = ModuleDefaultValues.ISLAND_PROGRESS_BAR,
    val superIslandWhitelistDisabled: Boolean = false,
    val superIslandHidePullBar: Boolean = false,
    val superIslandContentBottomMarginEnabled: Boolean = false,
    val superIslandContentBottomMarginDp: Float = 8f,
    val hideAodActions: Boolean = ModuleDefaultValues.HIDE_AOD_ACTIONS,
    val hideAodSeamless: Boolean = false,
    val sinkLockscreenNotificationsForFingerprint: Boolean = false,
    val hideLockscreenFingerprintIcon: Boolean = false,
    val lowerLockscreenPasswordPage: Boolean = false,
    val showLockscreenFingerprintIconOnAod: Boolean = false,
    val forceLockscreenClockColon: Boolean = false,
    val bypassHyperMusicCoverClockAdjustment: Boolean = false,
    val progressiveLockscreenClockAvoidance: Boolean = false,
    val aodClockWeightEnabled: Boolean = ModuleDefaultValues.AOD_CLOCK_WEIGHT_ENABLED,
    val aodClockWeight: Float = ModuleDefaultValues.AOD_CLOCK_WEIGHT,
    val lockscreenPasswordBackgroundBlurEnabled: Boolean = false,
    /** Absolute value returned by SystemUI's wallpaperBlurRatio coroutine, from 0.0 to 1.0. */
    val lockscreenPasswordBackgroundOpacity: Float = 0f,
    val lockscreenPasswordBackgroundFollowShadeBlend: Boolean = true,
    val lockscreenPinKeySoftGlassEnabled: Boolean = false,
    val lockscreenPinKeyGlassExtraRadius: Float = 6f,
    val lockscreenPinKeyGlassVerticalGap: Float = 16f,
    val statusBarHideMobileTypeOnWifi: Boolean = false,
    val statusBarHideMobileActivity: Boolean = false,
    val statusBarHideWifiActivity: Boolean = false,
    val statusBarHideWifiStandard: Boolean = false,
    val statusBarMobileActivityOffsetX: Float = 0f,
    val statusBarMobileActivityOffsetY: Float = 0f,
    val statusBarWifiActivityOffsetX: Float = 0f,
    val statusBarWifiActivityOffsetY: Float = 0f,
    val statusBarNetworkSpeedRightGap: Float = 0f,
    val statusBarNetworkTypeEnabled: Boolean = false,
    val statusBarNetworkTypeSize: Float = 13.5f,
    val statusBarNetworkTypeBold: Boolean = true,
    val statusBarNetworkTypeOffset: Float = 0f,
    val hyperGlassifyHiddenNavigationLift: Float = ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
    val xiaomiHealthFloatingNavigationEnabled: Boolean = ModuleDefaultValues.XIAOMI_HEALTH_FLOATING_NAVIGATION_ENABLED,
    val xiaomiHealthMiuixIconsEnabled: Boolean = false,
    val xiaomiHealthMonochromeIconsEnabled: Boolean = true,
    val marketFloatingNavigationEnabled: Boolean = ModuleDefaultValues.MARKET_FLOATING_NAVIGATION_ENABLED,
    val marketMiuixIconsEnabled: Boolean = false,
    val marketMonochromeIconsEnabled: Boolean = true,
    // Application Store badges stay hidden in the glass navigation by design.
    val marketNavigationBadgesEnabled: Boolean = false,
    val marketHideGamesTab: Boolean = false,
    val marketHideRankingsTab: Boolean = false,
    val marketHideProfileTab: Boolean = false,
    val miHomeFloatingNavigationEnabled: Boolean = ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED,
    val miHomeMiuixIconsEnabled: Boolean = false,
    val miHomeNavigationBadgesEnabled: Boolean = true,
    val amapFloatingNavigationEnabled: Boolean = ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED,
    val amapMiuixIconsEnabled: Boolean = false,
    val amapMonochromeIconsEnabled: Boolean = true,
    val amapHideLongPressVoiceTabEnabled: Boolean = ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED,
    val xiaomiCommunityFloatingNavigationEnabled: Boolean = ModuleDefaultValues.XIAOMI_COMMUNITY_FLOATING_NAVIGATION_ENABLED,
    val xiaomiCommunityMiuixIconsEnabled: Boolean = false,
    val xiaomiCommunityMonochromeIconsEnabled: Boolean = true,
    val xiaomiCommunityNavigationBadgesEnabled: Boolean = true,
    val bilibiliFloatingNavigationEnabled: Boolean = ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED,
    val bilibiliNavigationBadgesEnabled: Boolean = true,
    val bilibiliHomeTabVisible: Boolean = true,
    val bilibiliDynamicTabVisible: Boolean = true,
    val bilibiliFollowTabVisible: Boolean = true,
    val bilibiliMallTabVisible: Boolean = true,
    val bilibiliMineTabVisible: Boolean = true,
    val bilibiliPublishButtonVisible: Boolean = true,
    val spotifyFloatingNavigationEnabled: Boolean = ModuleDefaultValues.SPOTIFY_FLOATING_NAVIGATION_ENABLED,
    val spotifyFavoriteButtonEnabled: Boolean = ModuleDefaultValues.SPOTIFY_FAVORITE_BUTTON_ENABLED,
    val spotifyShuffleButtonEnabled: Boolean = ModuleDefaultValues.SPOTIFY_SHUFFLE_BUTTON_ENABLED,
    val customMediaConstraintSetEnabled: Boolean = false,
    val customMediaConstraintSetXml: String = "",
    val customMediaIslandConstraintSetEnabled: Boolean = false,
    val customMediaIslandConstraintSetXml: String = "",
    /** Missing stored selectors still preserve legacy media installations when loading. */
    val mediaLayoutPreset: String = ModuleDefaultValues.MEDIA_LAYOUT_PRESET,
    val systemMediaHeight: Float = 168f,
    val compactMediaHeight: Float = 84f,
    val standardMediaHeight: Float = 150f,
    val customMediaHeight: Float = 150f,
    val systemMediaXml: String = "",
    val systemMediaIslandXml: String = "",
    val compactMediaXml: String = "",
    val compactMediaIslandXml: String = "",
    val standardMediaXml: String = "",
    val standardMediaIslandXml: String = "",
)

/** Explicit presets keep reset behavior independent from persisted values and future migrations. */
object ModifierSettingsPresets {
    /** Restore the preset selected in the module defaults editor. */
    fun moduleDefault(): ModifierSettings = ModifierSettings()

    fun gestureHandlePreset(settings: ModifierSettings, preset: String): ModifierSettings {
        require(GestureHandlePresets.selectable(preset))
        val module = preset == GestureHandlePresets.MODULE
        return settings.copy(
            gestureHandlePreset = preset,
            gestureHandleAppModes = emptyMap(),
            gestureHandleTouchReveal = module,
            gestureHandleSwipeMotion = module,
            gestureHandleTouchAreaDp = GestureHandleTouchArea.DEFAULT_DP,
        )
    }

    fun selectedGestureHandlePreset(settings: ModifierSettings): String? {
        val preset = settings.gestureHandlePreset
        if (preset == GestureHandlePresets.STOCK) return preset.takeIf {
            settings.gestureHandleAppModes.isEmpty() && !settings.gestureHandleTouchReveal &&
                !settings.gestureHandleSwipeMotion &&
                settings.gestureHandleTouchAreaDp == GestureHandleTouchArea.DEFAULT_DP
        }
        if (!GestureHandlePresets.selectable(preset)) return null
        val module = preset == GestureHandlePresets.MODULE
        val matchingRules = if (module) settings.gestureHandleAppModes.all { (app, mode) ->
            GestureHandlePresets.MODULE_APPS[app] == mode
        } else settings.gestureHandleAppModes.isEmpty()
        return preset.takeIf { matchingRules && settings.gestureHandleTouchReveal == module &&
            settings.gestureHandleSwipeMotion == module &&
            settings.gestureHandleTouchAreaDp == GestureHandleTouchArea.DEFAULT_DP }
    }

    /** Apply the paired light/dark heads-up preset without changing unrelated module settings. */
    fun headsUpGlassModuleDefault(settings: ModifierSettings): ModifierSettings = settings.copy(
        headsUpGlassParametersEnabled = true,
        headsUpBackgroundBlurRadiusEnabled = false,
        headsUpGlassParameters = HeadsUpGlassParameters.regularSerialized,
        headsUpGlassDarkParameters = HeadsUpGlassParameters.darkSerialized,
    )

    /** Restore stock heads-up rendering while keeping the user's editable values. */
    fun headsUpGlassSystemDefault(settings: ModifierSettings): ModifierSettings = settings.copy(
        headsUpGlassParametersEnabled = false,
        headsUpBackgroundBlurRadiusEnabled = false,
    )

    /**
     * Most optional appearance hooks are disabled. The hidden Control Center/MiLink background
     * material alignment remains enabled by the module's default policy.
     */
    fun systemDefault(): ModifierSettings = ModifierSettings(
        aboutPhoneCardsEnabled = false,
        gestureHandleEnabled = false,
        gestureHandlePreset = GestureHandlePresets.STOCK,
        gestureHandleAppModes = emptyMap(),
        gestureHandleTouchReveal = false,
        gestureHandleSwipeMotion = false,
        notificationsEnabled = false,
        controlCenterFollowMiLinkBackgroundMaterial = true,
        hideHeadsUpMiniBar = false,
        headsUpBottomMarginEnabled = false,
        controlCenterEnabled = false,
        volumePanelRadius = 0f,
        miLinkMainCardsEnabled = false,
        mediaEnabled = false,
        islandEnabled = false,
        islandProgressBar = false,
        superIslandWhitelistDisabled = false,
        superIslandHidePullBar = false,
        superIslandContentBottomMarginEnabled = false,
        hideAodActions = false,
        hideAodSeamless = false,
        sinkLockscreenNotificationsForFingerprint = false,
        hideLockscreenFingerprintIcon = false,
        lowerLockscreenPasswordPage = false,
        showLockscreenFingerprintIconOnAod = false,
        forceLockscreenClockColon = false,
        bypassHyperMusicCoverClockAdjustment = false,
        progressiveLockscreenClockAvoidance = false,
        aodClockWeightEnabled = false,
        lockscreenPasswordBackgroundBlurEnabled = false,
        lockscreenPasswordBackgroundFollowShadeBlend = true,
        lockscreenPinKeySoftGlassEnabled = false,
        statusBarHideMobileTypeOnWifi = false,
        statusBarHideMobileActivity = false,
        statusBarHideWifiActivity = false,
        statusBarHideWifiStandard = false,
        statusBarMobileActivityOffsetX = 0f,
        statusBarMobileActivityOffsetY = 0f,
        statusBarWifiActivityOffsetX = 0f,
        statusBarWifiActivityOffsetY = 0f,
        statusBarNetworkSpeedRightGap = 0f,
        statusBarNetworkTypeEnabled = false,
        xiaomiHealthFloatingNavigationEnabled = false,
        xiaomiHealthMiuixIconsEnabled = false,
        xiaomiHealthMonochromeIconsEnabled = false,
        marketFloatingNavigationEnabled = false,
        marketMiuixIconsEnabled = false,
        marketMonochromeIconsEnabled = false,
        marketNavigationBadgesEnabled = false,
        marketHideGamesTab = false,
        marketHideRankingsTab = false,
        marketHideProfileTab = false,
        miHomeFloatingNavigationEnabled = false,
        miHomeMiuixIconsEnabled = false,
        miHomeNavigationBadgesEnabled = false,
        amapFloatingNavigationEnabled = false,
        amapMiuixIconsEnabled = false,
        amapMonochromeIconsEnabled = false,
        amapHideLongPressVoiceTabEnabled = false,
        xiaomiCommunityFloatingNavigationEnabled = false,
        xiaomiCommunityMiuixIconsEnabled = false,
        xiaomiCommunityMonochromeIconsEnabled = false,
        xiaomiCommunityNavigationBadgesEnabled = false,
        bilibiliFloatingNavigationEnabled = false,
        spotifyFloatingNavigationEnabled = false,
        spotifyFavoriteButtonEnabled = false,
        spotifyShuffleButtonEnabled = false,
        customMediaConstraintSetEnabled = false,
        customMediaConstraintSetXml = "",
        customMediaIslandConstraintSetEnabled = false,
        customMediaIslandConstraintSetXml = "",
        mediaLayoutPreset = "system",
        systemMediaHeight = 168f,
        compactMediaHeight = 84f,
        standardMediaHeight = 150f,
        customMediaHeight = 150f,
        systemMediaXml = "",
        systemMediaIslandXml = "",
        compactMediaXml = "",
        compactMediaIslandXml = "",
        standardMediaXml = "",
        standardMediaIslandXml = "",
    )
}

object ModifierSettingsStore {
    private const val TAG = "MyHyperModifier"
    const val PREFS = "modifier_settings"
    const val METHOD_GET = "get_settings"
    private const val KEY_MODULE_HOOKS_ENABLED = "module_hooks_enabled"
    private const val KEY_SYSTEM_UI_COMPATIBILITY_MODE = "systemui_compatibility_mode"
    private const val KEY_SYSTEM_UI_RETRY_GENERATION = "systemui_retry_generation"
    private const val KEY_DIAGNOSTIC_TOKEN = "diagnostic_token"
    private const val KEY_GESTURE_HANDLE_MODULE_PRESET = "gesture_handle_module_preset"
    private const val KEY_GESTURE_HANDLE_ENABLED = "gesture_handle_enabled"
    private const val KEY_GESTURE_HANDLE_PRESET = "gesture_handle_preset"
    private const val KEY_GESTURE_HANDLE_SCOPE_PACKAGES = "gesture_handle_scope_packages"
    private const val KEY_GESTURE_HANDLE_APP_MODES = "gesture_handle_app_modes"
    private const val KEY_GESTURE_HANDLE_TOUCH_REVEAL = "gesture_handle_touch_reveal"
    private const val KEY_GESTURE_HANDLE_SWIPE_MOTION = "gesture_handle_swipe_motion"
    private const val KEY_GESTURE_HANDLE_TOUCH_AREA_DP = "gesture_handle_touch_area_dp"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_NOTIFICATION_RADIUS = "notification_radius"
    private const val KEY_HIDE_HEADS_UP_MINI_BAR = "hide_heads_up_mini_bar"
    private const val KEY_HEADS_UP_BOTTOM_MARGIN_ENABLED = "heads_up_bottom_margin_enabled"
    private const val KEY_HEADS_UP_BOTTOM_MARGIN_DP = "heads_up_bottom_margin_dp"
    private const val KEY_HEADS_UP_GLASS_PARAMETERS_ENABLED = "heads_up_glass_parameters_enabled"
    private const val KEY_HEADS_UP_GLASS_PARAMETERS = "heads_up_glass_parameters"
    private const val KEY_HEADS_UP_GLASS_DARK_PARAMETERS = "heads_up_glass_dark_parameters"
    private const val KEY_SHADE_CARD_GLASS_ENABLED = "shade_card_glass_parameters_enabled"
    private const val KEY_DISABLE_SHADE_GLASS_HOOKS = "disable_shade_glass_hooks"
    private const val KEY_GLOBAL_GLASS_BLUR_ENABLED = "global_glass_blur_enabled"
    private const val KEY_SHADE_CARD_GLASS_PARAMETERS = "shade_card_glass_parameters"
    private const val KEY_SHADE_CARD_GLASS_DARK_PARAMETERS = "shade_card_glass_dark_parameters"
    private const val KEY_SHADE_CARD_BACKGROUND_BLUR_PERCENT = "shade_card_background_blur_percent"
    private const val KEY_SHADE_CARD_GLASS_BLUR_ENABLED = "shade_card_glass_blur_enabled"
    private const val KEY_SHADE_CARD_GLASS_BLUR_RADIUS = "shade_card_glass_blur_radius"
    private const val KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS_ENABLED =
        "heads_up_background_blur_radius_enabled"
    private const val KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS = "heads_up_background_blur_radius"
    private const val KEY_GLOBAL_BACKGROUND_BLUR_PERCENT = "global_background_blur_percent"
    private const val KEY_GLOBAL_BACKGROUND_DIM_ENABLED = "global_background_dim_enabled"
    private const val KEY_GLOBAL_BACKGROUND_DIM_PERCENT = "global_background_dim_percent"
    // New key intentionally ignores the earlier opt-in switch's saved false state.
    private const val KEY_CONTROL_CENTER_FOLLOW_MILINK_BACKGROUND_MATERIAL =
        "control_center_follow_milink_background_material_default_on"
    private const val KEY_CONTROL_CENTER = "control_center_enabled"
    private const val KEY_CONTROL_CENTER_RADIUS = "control_center_radius"
    private const val KEY_ADVANCED_CONTROL_CENTER_CORNERS = "advanced_control_center_corners"
    private const val KEY_CONTROL_CENTER_TILE_RADIUS = "control_center_tile_radius"
    private const val KEY_CONTROL_CENTER_CARD_RADIUS = "control_center_card_radius"
    private const val KEY_CONTROL_CENTER_SLIDER_RADIUS = "control_center_slider_radius"
    private const val KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS = "control_center_detail_slider_radius"
    private const val KEY_CONTROL_CENTER_MEDIA_RADIUS = "control_center_media_radius"
    private const val KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS = "control_center_external_entry_radius"
    private const val KEY_VOLUME_PANEL_RADIUS = "volume_panel_radius"
    private const val KEY_MILINK_MAIN_CARDS = "milink_main_cards_enabled"
    private const val KEY_MILINK_MAIN_CARD_RADIUS = "milink_main_card_radius"
    private const val KEY_MEDIA = "media_enabled"
    private const val KEY_EXPANDED = "expanded_height"
    private const val KEY_COLLAPSED = "collapsed_height"
    private const val KEY_FULL_AOD = "full_aod_height"
    private const val KEY_ISLAND = "island_enabled"
    private const val KEY_ISLAND_HEIGHT = "island_height"
    private const val KEY_ISLAND_PROGRESS = "island_progress"
    private const val KEY_SUPER_ISLAND_WHITELIST_DISABLED = "super_island_whitelist_disabled"
    private const val KEY_SUPER_ISLAND_HIDE_PULL_BAR = "super_island_hide_pull_bar"
    private const val LEGACY_KEY_SUPER_ISLAND_PULL_BAR_BOTTOM_MARGIN_ENABLED =
        "super_island_pull_bar_bottom_margin_enabled"
    private const val LEGACY_KEY_SUPER_ISLAND_PULL_BAR_BOTTOM_MARGIN_DP =
        "super_island_pull_bar_bottom_margin_dp"
    private const val KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_ENABLED =
        "super_island_content_bottom_margin_enabled"
    private const val KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_DP =
        "super_island_content_bottom_margin_dp"
    private const val KEY_HIDE_AOD_ACTIONS = "hide_aod_actions"
    private const val KEY_HIDE_AOD_SEAMLESS = "hide_aod_seamless"
    private const val KEY_SINK_LOCKSCREEN_NOTIFICATIONS_FOR_FINGERPRINT =
        "sink_lockscreen_notifications_for_fingerprint"
    private const val KEY_HIDE_LOCKSCREEN_FINGERPRINT_ICON = "hide_lockscreen_fingerprint_icon"
    private const val KEY_LOWER_LOCKSCREEN_PASSWORD_PAGE = "lower_lockscreen_password_page"
    private const val KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD =
        "show_lockscreen_fingerprint_icon_on_aod"
    private const val KEY_BYPASS_HYPER_MUSIC_COVER_CLOCK_ADJUSTMENT = "bypass_hyper_music_cover_clock_adjustment"
    private const val KEY_PROGRESSIVE_LOCKSCREEN_CLOCK_AVOIDANCE = "progressive_lockscreen_clock_avoidance"
    private const val KEY_SETTINGS_HOME_ENTRY = "settings_home_entry_enabled"
    private const val KEY_SETTINGS_MODULES_ENTRY = "settings_modules_entry_enabled"
    private const val KEY_SETTINGS_ENTRY_POSITION = "settings_home_entry_position"
    private const val KEY_SETTINGS_MANAGER_POSITION = "settings_manager_entry_position"
    private const val KEY_SETTINGS_MODULE_ENTRIES = "settings_module_entries"
    private const val KEY_ABOUT_PHONE_CARDS = "about_phone_cards_enabled"
    private const val KEY_PHONE_IMAGE_SOURCE = "about_phone_image_source"
    private const val KEY_PHONE_PRESET_URL = "about_phone_preset_url"
    private const val KEY_PHONE_CUSTOM_IMAGE = "about_phone_custom_image"
    private const val KEY_PHONE_IMAGE_TRANSFORMS = "about_phone_image_transforms"
    private const val KEY_FORCE_LOCKSCREEN_CLOCK_COLON = "force_lockscreen_clock_colon"
    private const val KEY_AOD_CLOCK_WEIGHT_ENABLED = "aod_clock_weight_enabled"
    private const val KEY_AOD_CLOCK_WEIGHT = "aod_clock_weight"
    private const val KEY_LOCKSCREEN_PASSWORD_BACKGROUND_BLUR_ENABLED =
        "lockscreen_password_background_blur_enabled"
    private const val KEY_LOCKSCREEN_PASSWORD_BACKGROUND_OPACITY =
        "lockscreen_password_background_opacity"
    private const val KEY_LOCKSCREEN_PASSWORD_BACKGROUND_FOLLOW_SHADE_BLEND =
        "lockscreen_password_background_follow_shade_blend"
    private const val KEY_LOCKSCREEN_PIN_KEY_SOFT_GLASS_ENABLED =
        "lockscreen_pin_key_soft_glass_enabled"
    private const val KEY_LOCKSCREEN_PIN_KEY_GLASS_EXTRA_RADIUS =
        "lockscreen_pin_key_glass_extra_radius"
    private const val KEY_LOCKSCREEN_PIN_KEY_GLASS_VERTICAL_GAP =
        "lockscreen_pin_key_glass_vertical_gap"
    private const val KEY_STATUS_BAR_HIDE_MOBILE_TYPE_ON_WIFI = "status_bar_hide_mobile_on_wifi"
    private const val KEY_STATUS_BAR_HIDE_MOBILE_ACTIVITY = "status_bar_hide_mobile_activity"
    private const val KEY_STATUS_BAR_HIDE_WIFI_ACTIVITY = "status_bar_hide_wifi_activity"
    private const val KEY_STATUS_BAR_HIDE_WIFI_STANDARD = "status_bar_hide_wifi_standard"
    private const val KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_X = "status_bar_mobile_activity_offset_x"
    private const val KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_Y = "status_bar_mobile_activity_offset_y"
    private const val KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_X = "status_bar_wifi_activity_offset_x"
    private const val KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_Y = "status_bar_wifi_activity_offset_y"
    private const val KEY_STATUS_BAR_NETWORK_SPEED_RIGHT_GAP = "status_bar_network_speed_right_gap"
    private const val KEY_STATUS_BAR_NETWORK_TYPE_ENABLED = "status_bar_network_type_enabled"
    private const val KEY_STATUS_BAR_NETWORK_TYPE_SIZE = "status_bar_network_type_size"
    private const val KEY_STATUS_BAR_NETWORK_TYPE_BOLD = "status_bar_network_type_bold"
    private const val KEY_STATUS_BAR_NETWORK_TYPE_OFFSET = "status_bar_network_type_offset"
    private const val KEY_HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT =
        "hyper_glassify_hidden_navigation_lift"
    private const val KEY_XIAOMI_HEALTH_FLOATING_NAVIGATION =
        "xiaomi_health_floating_navigation_enabled"
    private const val KEY_XIAOMI_HEALTH_MIUIX_ICONS = "xiaomi_health_miuix_icons_enabled"
    private const val KEY_XIAOMI_HEALTH_MONOCHROME_ICONS =
        "xiaomi_health_monochrome_icons_enabled"
    private const val KEY_MARKET_FLOATING_NAVIGATION = "market_floating_navigation_enabled"
    private const val KEY_MARKET_MIUIX_ICONS = "market_miuix_icons_enabled"
    private const val KEY_MARKET_MONOCHROME_ICONS = "market_monochrome_icons_enabled"
    private const val KEY_MARKET_NAVIGATION_BADGES = "market_navigation_badges_enabled"
    private const val KEY_MARKET_HIDE_GAMES_TAB = "market_hide_games_tab"
    private const val KEY_MARKET_HIDE_RANKINGS_TAB = "market_hide_rankings_tab"
    private const val KEY_MARKET_HIDE_PROFILE_TAB = "market_hide_profile_tab"
    private const val KEY_MI_HOME_FLOATING_NAVIGATION = "mi_home_floating_navigation_enabled"
    private const val KEY_MI_HOME_MIUIX_ICONS = "mi_home_miuix_icons_enabled"
    private const val KEY_MI_HOME_MONOCHROME_ICONS = "mi_home_monochrome_icons_enabled"
    private const val KEY_MI_HOME_NAVIGATION_BADGES = "mi_home_navigation_badges_enabled"
    private const val KEY_AMAP_FLOATING_NAVIGATION = "amap_floating_navigation_enabled"
    private const val KEY_AMAP_MIUIX_ICONS = "amap_miuix_icons_enabled"
    private const val KEY_AMAP_MONOCHROME_ICONS = "amap_monochrome_icons_enabled"
    private const val KEY_AMAP_HIDE_LONG_PRESS_VOICE_TAB =
        "amap_hide_long_press_voice_tab_enabled"
    private const val KEY_XIAOMI_COMMUNITY_FLOATING_NAVIGATION =
        "xiaomi_community_floating_navigation_enabled"
    private const val KEY_XIAOMI_COMMUNITY_MIUIX_ICONS =
        "xiaomi_community_miuix_icons_enabled"
    private const val KEY_XIAOMI_COMMUNITY_MONOCHROME_ICONS =
        "xiaomi_community_monochrome_icons_enabled"
    private const val KEY_XIAOMI_COMMUNITY_NAVIGATION_BADGES =
        "xiaomi_community_navigation_badges_enabled"
    private const val KEY_BILIBILI_FLOATING_NAVIGATION_ENABLED = "bilibili_floating_navigation_enabled"
    private const val KEY_BILIBILI_NAVIGATION_BADGES_ENABLED = "bilibili_navigation_badges_enabled"
    private const val KEY_BILIBILI_HOME_TAB_VISIBLE = "bilibili_home_tab_visible"
    private const val KEY_BILIBILI_FOLLOW_TAB_VISIBLE = "bilibili_follow_tab_visible"
    private const val KEY_BILIBILI_DYNAMIC_TAB_VISIBLE = "bilibili_dynamic_tab_visible"
    private const val KEY_BILIBILI_MALL_TAB_VISIBLE = "bilibili_mall_tab_visible"
    private const val KEY_BILIBILI_MINE_TAB_VISIBLE = "bilibili_mine_tab_visible"
    private const val KEY_BILIBILI_PUBLISH_BUTTON_VISIBLE = "bilibili_publish_button_visible"
    private const val KEY_SPOTIFY_FLOATING_NAVIGATION = "spotify_floating_navigation_enabled"
    private const val KEY_SPOTIFY_FAVORITE_BUTTON = "spotify_favorite_button_enabled"
    private const val KEY_SPOTIFY_SHUFFLE_BUTTON = "spotify_shuffle_button_enabled"
    private const val KEY_CUSTOM_MEDIA_CONSTRAINT_SET = "custom_media_constraint_set_enabled"
    private const val KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML = "custom_media_constraint_set_xml"
    private const val KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET =
        "custom_media_island_constraint_set_enabled"
    private const val KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET_XML =
        "custom_media_island_constraint_set_xml"
    private const val KEY_MEDIA_LAYOUT_PRESET = "media_layout_preset"
    private const val KEY_SYSTEM_MEDIA_HEIGHT = "system_media_height"
    private const val KEY_COMPACT_MEDIA_HEIGHT = "compact_media_height"
    private const val KEY_STANDARD_MEDIA_HEIGHT = "standard_media_height"
    private const val KEY_CUSTOM_MEDIA_HEIGHT = "custom_media_height"
    private const val KEY_SYSTEM_MEDIA_XML = "system_media_xml"
    private const val KEY_SYSTEM_MEDIA_ISLAND_XML = "system_media_island_xml"
    private const val KEY_COMPACT_MEDIA_XML = "compact_media_xml"
    private const val KEY_COMPACT_MEDIA_ISLAND_XML = "compact_media_island_xml"
    private const val KEY_STANDARD_MEDIA_XML = "standard_media_xml"
    private const val KEY_STANDARD_MEDIA_ISLAND_XML = "standard_media_island_xml"

    fun load(context: Context): ModifierSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val regularGlass = HeadsUpGlassParameters.parseSerializedOrDefault(
            prefs.getString(KEY_HEADS_UP_GLASS_PARAMETERS, null),
            HeadsUpGlassParameters.regularDefault,
        )
        val darkGlass = HeadsUpGlassParameters.parseSerializedOrDefault(
            prefs.getString(KEY_HEADS_UP_GLASS_DARK_PARAMETERS, null),
            HeadsUpGlassParameters.darkDefault,
        )
        val migrateGlassDefaults = HeadsUpGlassDefaults.isLegacyDefaultPair(regularGlass, darkGlass)
        val gesturePreset = GestureHandlePresets.fromStored(
            prefs.getString(KEY_GESTURE_HANDLE_PRESET, null),
            prefs.getBoolean(KEY_GESTURE_HANDLE_MODULE_PRESET, false),
        )
        val newModuleDefaults = gesturePreset == GestureHandlePresets.MODULE &&
            !prefs.contains(KEY_GESTURE_HANDLE_APP_MODES)
        return ModifierSettings(
            moduleHooksEnabled = prefs.getBoolean(KEY_MODULE_HOOKS_ENABLED, true),
            systemUiCompatibilityMode = prefs.getBoolean(KEY_SYSTEM_UI_COMPATIBILITY_MODE, false),
            systemUiRetryGeneration = prefs.getInt(KEY_SYSTEM_UI_RETRY_GENERATION, 0),
            gestureHandleEnabled = prefs.getBoolean(KEY_GESTURE_HANDLE_ENABLED, false),
            gestureHandlePreset = gesturePreset,
            gestureHandleAppModes = GestureHandleRules.decode(prefs.getString(KEY_GESTURE_HANDLE_APP_MODES, "")),
            gestureHandleTouchReveal = prefs.getBoolean(KEY_GESTURE_HANDLE_TOUCH_REVEAL, newModuleDefaults),
            gestureHandleSwipeMotion = prefs.getBoolean(KEY_GESTURE_HANDLE_SWIPE_MOTION, newModuleDefaults),
            gestureHandleTouchAreaDp = GestureHandleTouchArea.normalize(
                prefs.getFloat(KEY_GESTURE_HANDLE_TOUCH_AREA_DP, GestureHandleTouchArea.DEFAULT_DP)),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            notificationRadius = prefs.getFloat(KEY_NOTIFICATION_RADIUS, 28f),
            hideHeadsUpMiniBar = prefs.getBoolean(KEY_HIDE_HEADS_UP_MINI_BAR, false),
            headsUpBottomMarginEnabled = prefs.getBoolean(KEY_HEADS_UP_BOTTOM_MARGIN_ENABLED, false),
            headsUpBottomMarginDp = normalizedHeadsUpBottomMargin(
                prefs.getFloat(KEY_HEADS_UP_BOTTOM_MARGIN_DP, 13f),
            ),
            headsUpGlassParametersEnabled = prefs.getBoolean(
                KEY_HEADS_UP_GLASS_PARAMETERS_ENABLED,
                false,
            ),
            headsUpGlassParameters = HeadsUpGlassParameters.serialize(
                if (migrateGlassDefaults) HeadsUpGlassParameters.regularDefault else regularGlass,
            ),
            headsUpGlassDarkParameters = HeadsUpGlassParameters.serialize(
                if (migrateGlassDefaults) HeadsUpGlassParameters.darkDefault else darkGlass,
            ),
            headsUpBackgroundBlurRadiusEnabled = prefs.getBoolean(
                KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS_ENABLED,
                false,
            ),
            shadeCardGlassParametersEnabled = prefs.getBoolean(KEY_SHADE_CARD_GLASS_ENABLED, false),
            disableShadeGlassHooks = prefs.getBoolean(KEY_DISABLE_SHADE_GLASS_HOOKS, false),
            globalGlassBlurEnabled = prefs.getBoolean(KEY_GLOBAL_GLASS_BLUR_ENABLED,
                prefs.getBoolean(KEY_SHADE_CARD_GLASS_ENABLED, false)),
            shadeCardGlassParameters = HeadsUpGlassParameters.serialize(
                HeadsUpGlassParameters.parseSerializedOrDefault(
                    prefs.getString(KEY_SHADE_CARD_GLASS_PARAMETERS,
                        prefs.getString(KEY_SHADE_CARD_GLASS_DARK_PARAMETERS, null)), ShadeCardGlassPolicy.defaults(),
                ),
            ),
            shadeCardBackgroundBlurPercent = ShadeCardGlassPolicy.normalize(prefs.getFloat(KEY_SHADE_CARD_BACKGROUND_BLUR_PERCENT, 100f), 200f, 100f),
            shadeCardGlassBlurEnabled = prefs.getBoolean(KEY_SHADE_CARD_GLASS_BLUR_ENABLED, false),
            shadeCardGlassBlurRadius = ShadeCardGlassPolicy.normalize(prefs.getFloat(KEY_SHADE_CARD_GLASS_BLUR_RADIUS, 20f), ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(), 20f),
            headsUpBackgroundBlurRadius = ShadeCardGlassPolicy.normalize(prefs.getFloat(
                KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS, 60f,
            ), ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(), 60f),
            globalBackgroundBlurPercent = prefs.getFloat(
                KEY_GLOBAL_BACKGROUND_BLUR_PERCENT,
                100f,
            ).coerceIn(0f, 200f),
            globalBackgroundDimEnabled = prefs.getBoolean(KEY_GLOBAL_BACKGROUND_DIM_ENABLED, false),
            globalBackgroundDimPercent = BackgroundDimPolicy.normalizePercent(
                prefs.getFloat(KEY_GLOBAL_BACKGROUND_DIM_PERCENT, 20f),
            ),
            controlCenterFollowMiLinkBackgroundMaterial = prefs.getBoolean(
                KEY_CONTROL_CENTER_FOLLOW_MILINK_BACKGROUND_MATERIAL,
                true,
            ),
            controlCenterEnabled = prefs.getBoolean(KEY_CONTROL_CENTER, true),
            controlCenterRadius = prefs.getFloat(KEY_CONTROL_CENTER_RADIUS, 28f),
            advancedControlCenterCorners = prefs.getBoolean(KEY_ADVANCED_CONTROL_CENTER_CORNERS, false),
            controlCenterTileRadius = prefs.getFloat(KEY_CONTROL_CENTER_TILE_RADIUS, 28f),
            controlCenterCardRadius = prefs.getFloat(KEY_CONTROL_CENTER_CARD_RADIUS, 28f),
            controlCenterSliderRadius = prefs.getFloat(KEY_CONTROL_CENTER_SLIDER_RADIUS, 28f),
            controlCenterDetailSliderRadius = prefs.getFloat(KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS, 28f),
            controlCenterMediaRadius = prefs.getFloat(KEY_CONTROL_CENTER_MEDIA_RADIUS, 28f),
            controlCenterExternalEntryRadius = prefs.getFloat(KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS, 28f),
            volumePanelRadius = prefs.getFloat(KEY_VOLUME_PANEL_RADIUS, ModuleDefaultValues.VOLUME_PANEL_RADIUS),
            miLinkMainCardsEnabled = prefs.getBoolean(KEY_MILINK_MAIN_CARDS, ModuleDefaultValues.MI_LINK_MAIN_CARDS_ENABLED),
            miLinkMainCardRadius = prefs.getFloat(KEY_MILINK_MAIN_CARD_RADIUS, ModuleDefaultValues.MI_LINK_MAIN_CARD_RADIUS),
            mediaEnabled = prefs.getBoolean(KEY_MEDIA, ModuleDefaultValues.MEDIA_ENABLED),
            expandedHeight = prefs.getFloat(KEY_EXPANDED, 152f),
            collapsedHeight = prefs.getFloat(KEY_COLLAPSED, 120f),
            fullAodHeight = prefs.getFloat(KEY_FULL_AOD, 80f),
            islandEnabled = prefs.getBoolean(KEY_ISLAND, ModuleDefaultValues.ISLAND_ENABLED),
            islandHeight = prefs.getFloat(KEY_ISLAND_HEIGHT, 160f),
            islandProgressBar = prefs.getBoolean(KEY_ISLAND_PROGRESS, ModuleDefaultValues.ISLAND_PROGRESS_BAR),
            superIslandWhitelistDisabled = prefs.getBoolean(KEY_SUPER_ISLAND_WHITELIST_DISABLED, false),
            superIslandHidePullBar = prefs.getBoolean(KEY_SUPER_ISLAND_HIDE_PULL_BAR, false),
            superIslandContentBottomMarginEnabled = prefs.getBoolean(
                KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_ENABLED,
                prefs.getBoolean(LEGACY_KEY_SUPER_ISLAND_PULL_BAR_BOTTOM_MARGIN_ENABLED, false),
            ),
            superIslandContentBottomMarginDp = normalizedSuperIslandContentBottomMargin(
                prefs.getFloat(KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_DP,
                    prefs.getFloat(LEGACY_KEY_SUPER_ISLAND_PULL_BAR_BOTTOM_MARGIN_DP, 8f)),
            ),
            hideAodActions = prefs.getBoolean(KEY_HIDE_AOD_ACTIONS, ModuleDefaultValues.HIDE_AOD_ACTIONS),
            hideAodSeamless = prefs.getBoolean(KEY_HIDE_AOD_SEAMLESS, false),
            sinkLockscreenNotificationsForFingerprint = prefs.getBoolean(
                KEY_SINK_LOCKSCREEN_NOTIFICATIONS_FOR_FINGERPRINT,
                false,
            ),
            hideLockscreenFingerprintIcon = prefs.getBoolean(KEY_HIDE_LOCKSCREEN_FINGERPRINT_ICON, false),
            lowerLockscreenPasswordPage = prefs.getBoolean(KEY_LOWER_LOCKSCREEN_PASSWORD_PAGE, false),
            showLockscreenFingerprintIconOnAod = prefs.getBoolean(
                KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD,
                false,
            ),
            settingsHomeEntryEnabled = prefs.getBoolean(KEY_SETTINGS_HOME_ENTRY, false),
            settingsModulesEntryEnabled = prefs.getBoolean(KEY_SETTINGS_MODULES_ENTRY, false),
            settingsHomeEntryPosition = SettingsHomeHeaderPolicy.normalizePosition(prefs.getString(KEY_SETTINGS_ENTRY_POSITION, ModuleDefaultValues.SETTINGS_HOME_ENTRY_POSITION)),
            settingsManagerEntryPosition = SettingsHomeHeaderPolicy.normalizePosition(prefs.getString(KEY_SETTINGS_MANAGER_POSITION,
                prefs.getString(KEY_SETTINGS_ENTRY_POSITION, ModuleDefaultValues.SETTINGS_MANAGER_ENTRY_POSITION))),
            settingsModuleEntries = prefs.getString(KEY_SETTINGS_MODULE_ENTRIES, "{}") ?: "{}",
            aboutPhoneCardsEnabled = prefs.getBoolean(KEY_ABOUT_PHONE_CARDS, ModuleDefaultValues.ABOUT_PHONE_CARDS_ENABLED),
            aboutPhoneImageSource = prefs.getString(KEY_PHONE_IMAGE_SOURCE, "auto") ?: "auto",
            aboutPhonePresetUrl = prefs.getString(KEY_PHONE_PRESET_URL, PhonePresetRepository.DEFAULT_URL) ?: PhonePresetRepository.DEFAULT_URL,
            aboutPhoneCustomImage = prefs.getString(KEY_PHONE_CUSTOM_IMAGE, "") ?: "",
            aboutPhoneImageTransforms = prefs.getString(KEY_PHONE_IMAGE_TRANSFORMS, "{}") ?: "{}",
            forceLockscreenClockColon = prefs.getBoolean(KEY_FORCE_LOCKSCREEN_CLOCK_COLON, false),
            bypassHyperMusicCoverClockAdjustment = prefs.getBoolean(KEY_BYPASS_HYPER_MUSIC_COVER_CLOCK_ADJUSTMENT, false),
            progressiveLockscreenClockAvoidance = prefs.getBoolean(KEY_PROGRESSIVE_LOCKSCREEN_CLOCK_AVOIDANCE, false),
            aodClockWeightEnabled = prefs.getBoolean(KEY_AOD_CLOCK_WEIGHT_ENABLED, ModuleDefaultValues.AOD_CLOCK_WEIGHT_ENABLED),
            aodClockWeight = normalizedAodClockWeight(prefs.getFloat(KEY_AOD_CLOCK_WEIGHT, ModuleDefaultValues.AOD_CLOCK_WEIGHT)),
            lockscreenPasswordBackgroundBlurEnabled = prefs.getBoolean(
                KEY_LOCKSCREEN_PASSWORD_BACKGROUND_BLUR_ENABLED,
                false,
            ),
            lockscreenPasswordBackgroundOpacity = prefs.getFloat(
                KEY_LOCKSCREEN_PASSWORD_BACKGROUND_OPACITY,
                0f,
            ).coerceIn(0f, 1f),
            lockscreenPasswordBackgroundFollowShadeBlend = prefs.getBoolean(
                KEY_LOCKSCREEN_PASSWORD_BACKGROUND_FOLLOW_SHADE_BLEND,
                true,
            ),
            lockscreenPinKeySoftGlassEnabled = prefs.getBoolean(
                KEY_LOCKSCREEN_PIN_KEY_SOFT_GLASS_ENABLED,
                false,
            ),
            lockscreenPinKeyGlassExtraRadius = prefs.getFloat(
                KEY_LOCKSCREEN_PIN_KEY_GLASS_EXTRA_RADIUS,
                6f,
            ).coerceIn(0f, 16f),
            lockscreenPinKeyGlassVerticalGap = prefs.getFloat(
                KEY_LOCKSCREEN_PIN_KEY_GLASS_VERTICAL_GAP,
                16f,
            ).coerceIn(0f, 32f),
            statusBarHideMobileTypeOnWifi = prefs.getBoolean(KEY_STATUS_BAR_HIDE_MOBILE_TYPE_ON_WIFI, false),
            statusBarHideMobileActivity = prefs.getBoolean(KEY_STATUS_BAR_HIDE_MOBILE_ACTIVITY, false),
            statusBarHideWifiActivity = prefs.getBoolean(KEY_STATUS_BAR_HIDE_WIFI_ACTIVITY, false),
            statusBarHideWifiStandard = prefs.getBoolean(KEY_STATUS_BAR_HIDE_WIFI_STANDARD, false),
            statusBarMobileActivityOffsetX = prefs.getFloat(KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_X, 0f),
            statusBarMobileActivityOffsetY = prefs.getFloat(KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_Y, 0f),
            statusBarWifiActivityOffsetX = prefs.getFloat(KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_X, 0f),
            statusBarWifiActivityOffsetY = prefs.getFloat(KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_Y, 0f),
            statusBarNetworkSpeedRightGap = prefs.getFloat(KEY_STATUS_BAR_NETWORK_SPEED_RIGHT_GAP, 0f),
            statusBarNetworkTypeEnabled = prefs.getBoolean(KEY_STATUS_BAR_NETWORK_TYPE_ENABLED, false),
            statusBarNetworkTypeSize = prefs.getFloat(KEY_STATUS_BAR_NETWORK_TYPE_SIZE, 13.5f),
            statusBarNetworkTypeBold = prefs.getBoolean(KEY_STATUS_BAR_NETWORK_TYPE_BOLD, true),
            statusBarNetworkTypeOffset = prefs.getFloat(KEY_STATUS_BAR_NETWORK_TYPE_OFFSET, 0f),
            hyperGlassifyHiddenNavigationLift = prefs.getFloat(
                KEY_HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
                ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
            ).coerceIn(0f, 48f),
            xiaomiHealthFloatingNavigationEnabled = prefs.getBoolean(
                KEY_XIAOMI_HEALTH_FLOATING_NAVIGATION,
                ModuleDefaultValues.XIAOMI_HEALTH_FLOATING_NAVIGATION_ENABLED,
            ),
            xiaomiHealthMiuixIconsEnabled = prefs.getBoolean(KEY_XIAOMI_HEALTH_MIUIX_ICONS, false),
            xiaomiHealthMonochromeIconsEnabled = prefs.getBoolean(
                KEY_XIAOMI_HEALTH_MONOCHROME_ICONS,
                true,
            ),
            marketFloatingNavigationEnabled = prefs.getBoolean(KEY_MARKET_FLOATING_NAVIGATION, ModuleDefaultValues.MARKET_FLOATING_NAVIGATION_ENABLED),
            marketMiuixIconsEnabled = prefs.getBoolean(KEY_MARKET_MIUIX_ICONS, false),
            marketMonochromeIconsEnabled = prefs.getBoolean(KEY_MARKET_MONOCHROME_ICONS, true),
            marketNavigationBadgesEnabled = false,
            marketHideGamesTab = prefs.getBoolean(KEY_MARKET_HIDE_GAMES_TAB, false),
            marketHideRankingsTab = prefs.getBoolean(KEY_MARKET_HIDE_RANKINGS_TAB, false),
            marketHideProfileTab = prefs.getBoolean(KEY_MARKET_HIDE_PROFILE_TAB, false),
            miHomeFloatingNavigationEnabled = prefs.getBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED),
            miHomeMiuixIconsEnabled = prefs.getBoolean(KEY_MI_HOME_MIUIX_ICONS, false),
            miHomeNavigationBadgesEnabled = prefs.getBoolean(KEY_MI_HOME_NAVIGATION_BADGES, true),
            amapFloatingNavigationEnabled = prefs.getBoolean(KEY_AMAP_FLOATING_NAVIGATION, ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED),
            amapMiuixIconsEnabled = prefs.getBoolean(KEY_AMAP_MIUIX_ICONS, false),
            amapMonochromeIconsEnabled = prefs.getBoolean(KEY_AMAP_MONOCHROME_ICONS, true),
            amapHideLongPressVoiceTabEnabled = prefs.getBoolean(
                KEY_AMAP_HIDE_LONG_PRESS_VOICE_TAB,
                ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED,
            ),
            xiaomiCommunityFloatingNavigationEnabled = prefs.getBoolean(
                KEY_XIAOMI_COMMUNITY_FLOATING_NAVIGATION,
                ModuleDefaultValues.XIAOMI_COMMUNITY_FLOATING_NAVIGATION_ENABLED,
            ),
            xiaomiCommunityMiuixIconsEnabled = prefs.getBoolean(
                KEY_XIAOMI_COMMUNITY_MIUIX_ICONS,
                false,
            ),
            xiaomiCommunityMonochromeIconsEnabled = prefs.getBoolean(
                KEY_XIAOMI_COMMUNITY_MONOCHROME_ICONS,
                true,
            ),
            xiaomiCommunityNavigationBadgesEnabled = prefs.getBoolean(
                KEY_XIAOMI_COMMUNITY_NAVIGATION_BADGES,
                true,
            ),
            bilibiliFloatingNavigationEnabled = prefs.getBoolean(KEY_BILIBILI_FLOATING_NAVIGATION_ENABLED, ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED),
            bilibiliNavigationBadgesEnabled = prefs.getBoolean(KEY_BILIBILI_NAVIGATION_BADGES_ENABLED, true),
            bilibiliHomeTabVisible = prefs.getBoolean(KEY_BILIBILI_HOME_TAB_VISIBLE, true),
            bilibiliFollowTabVisible = prefs.getBoolean(KEY_BILIBILI_FOLLOW_TAB_VISIBLE, true),
            bilibiliDynamicTabVisible = prefs.getBoolean(KEY_BILIBILI_DYNAMIC_TAB_VISIBLE, true),
            bilibiliMallTabVisible = prefs.getBoolean(KEY_BILIBILI_MALL_TAB_VISIBLE, true),
            bilibiliMineTabVisible = prefs.getBoolean(KEY_BILIBILI_MINE_TAB_VISIBLE, true),
            bilibiliPublishButtonVisible = prefs.getBoolean(KEY_BILIBILI_PUBLISH_BUTTON_VISIBLE, true),
            spotifyFloatingNavigationEnabled = prefs.getBoolean(KEY_SPOTIFY_FLOATING_NAVIGATION, ModuleDefaultValues.SPOTIFY_FLOATING_NAVIGATION_ENABLED),
            spotifyFavoriteButtonEnabled = prefs.getBoolean(KEY_SPOTIFY_FAVORITE_BUTTON, ModuleDefaultValues.SPOTIFY_FAVORITE_BUTTON_ENABLED),
            spotifyShuffleButtonEnabled = prefs.getBoolean(KEY_SPOTIFY_SHUFFLE_BUTTON, ModuleDefaultValues.SPOTIFY_SHUFFLE_BUTTON_ENABLED),
            customMediaConstraintSetEnabled = prefs.getBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, false),
            customMediaConstraintSetXml = prefs.getString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, "").orEmpty(),
            customMediaIslandConstraintSetEnabled = prefs.getBoolean(
                KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET, false,
            ),
            customMediaIslandConstraintSetXml = prefs.getString(
                KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET_XML, "",
            ).orEmpty(),
            mediaLayoutPreset = prefs.getString(KEY_MEDIA_LAYOUT_PRESET,
                if (prefs.contains(KEY_MEDIA) || prefs.contains(KEY_ISLAND)
                    || prefs.contains(KEY_CUSTOM_MEDIA_CONSTRAINT_SET)) ""
                else ModuleDefaultValues.MEDIA_LAYOUT_PRESET).orEmpty(),
            systemMediaHeight = prefs.getFloat(KEY_SYSTEM_MEDIA_HEIGHT, 168f),
            compactMediaHeight = prefs.getFloat(KEY_COMPACT_MEDIA_HEIGHT, 84f),
            standardMediaHeight = prefs.getFloat(KEY_STANDARD_MEDIA_HEIGHT, 150f),
            customMediaHeight = prefs.getFloat(KEY_CUSTOM_MEDIA_HEIGHT, 150f),
            systemMediaXml = prefs.getString(KEY_SYSTEM_MEDIA_XML, "").orEmpty(),
            systemMediaIslandXml = prefs.getString(KEY_SYSTEM_MEDIA_ISLAND_XML, "").orEmpty(),
            compactMediaXml = prefs.getString(KEY_COMPACT_MEDIA_XML, "").orEmpty(),
            compactMediaIslandXml = prefs.getString(KEY_COMPACT_MEDIA_ISLAND_XML, "").orEmpty(),
            standardMediaXml = prefs.getString(KEY_STANDARD_MEDIA_XML, "").orEmpty(),
            standardMediaIslandXml = prefs.getString(KEY_STANDARD_MEDIA_ISLAND_XML, "").orEmpty(),
        )
    }

    fun save(context: Context, value: ModifierSettings) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val presetChanged = preferences.getString(KEY_GESTURE_HANDLE_PRESET, null) != value.gestureHandlePreset
        val safetyGateChanged = preferences.getBoolean(KEY_MODULE_HOOKS_ENABLED, true) != value.moduleHooksEnabled ||
            preferences.getBoolean(KEY_SYSTEM_UI_COMPATIBILITY_MODE, false) != value.systemUiCompatibilityMode ||
            preferences.getInt(KEY_SYSTEM_UI_RETRY_GENERATION, 0) != value.systemUiRetryGeneration
        val editor = preferences.edit()
        editor
            .putBoolean(KEY_MODULE_HOOKS_ENABLED, value.moduleHooksEnabled)
            .putBoolean(KEY_SYSTEM_UI_COMPATIBILITY_MODE, value.systemUiCompatibilityMode)
            .putInt(KEY_SYSTEM_UI_RETRY_GENERATION, value.systemUiRetryGeneration)
            .putBoolean(KEY_GESTURE_HANDLE_ENABLED, value.gestureHandleEnabled)
            .putString(KEY_GESTURE_HANDLE_PRESET, value.gestureHandlePreset)
            .putBoolean(KEY_GESTURE_HANDLE_MODULE_PRESET,
                value.gestureHandlePreset == GestureHandlePresets.MODULE)
            .putString(KEY_GESTURE_HANDLE_APP_MODES, GestureHandleRules.encode(value.gestureHandleAppModes))
            .putBoolean(KEY_GESTURE_HANDLE_TOUCH_REVEAL, value.gestureHandleTouchReveal)
            .putBoolean(KEY_GESTURE_HANDLE_SWIPE_MOTION, value.gestureHandleSwipeMotion)
            .putFloat(KEY_GESTURE_HANDLE_TOUCH_AREA_DP,
                GestureHandleTouchArea.normalize(value.gestureHandleTouchAreaDp))
            .putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
            .putFloat(KEY_NOTIFICATION_RADIUS, value.notificationRadius)
            .putBoolean(KEY_HIDE_HEADS_UP_MINI_BAR, value.hideHeadsUpMiniBar)
            .putBoolean(KEY_HEADS_UP_BOTTOM_MARGIN_ENABLED, value.headsUpBottomMarginEnabled)
            .putFloat(
                KEY_HEADS_UP_BOTTOM_MARGIN_DP,
                normalizedHeadsUpBottomMargin(value.headsUpBottomMarginDp),
            )
            .putBoolean(
                KEY_HEADS_UP_GLASS_PARAMETERS_ENABLED,
                value.headsUpGlassParametersEnabled,
            )
            .putString(
                KEY_HEADS_UP_GLASS_PARAMETERS,
                HeadsUpGlassParameters.serialize(
                    HeadsUpGlassParameters.parseSerializedOrDefault(
                        value.headsUpGlassParameters,
                        HeadsUpGlassParameters.regularDefault,
                    ),
                ),
            )
            .putString(
                KEY_HEADS_UP_GLASS_DARK_PARAMETERS,
                HeadsUpGlassParameters.serialize(
                    HeadsUpGlassParameters.parseSerializedOrDefault(
                        value.headsUpGlassDarkParameters,
                        HeadsUpGlassParameters.darkDefault,
                    ),
                ),
            )
            .putBoolean(
                KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS_ENABLED,
                value.headsUpBackgroundBlurRadiusEnabled,
            )
            .putBoolean(KEY_SHADE_CARD_GLASS_ENABLED, value.shadeCardGlassParametersEnabled)
            .putBoolean(KEY_DISABLE_SHADE_GLASS_HOOKS, value.disableShadeGlassHooks)
            .putBoolean(KEY_GLOBAL_GLASS_BLUR_ENABLED, value.globalGlassBlurEnabled)
            .putString(KEY_SHADE_CARD_GLASS_PARAMETERS, HeadsUpGlassParameters.serialize(
                HeadsUpGlassParameters.parseSerializedOrDefault(value.shadeCardGlassParameters, ShadeCardGlassPolicy.defaults()),
            ))
            // Keep the old dark preference untouched for rollback; the common recipe uses regular.
            .putFloat(KEY_SHADE_CARD_BACKGROUND_BLUR_PERCENT, ShadeCardGlassPolicy.normalize(value.shadeCardBackgroundBlurPercent, 200f, 100f))
            .putBoolean(KEY_SHADE_CARD_GLASS_BLUR_ENABLED, value.shadeCardGlassBlurEnabled)
            .putFloat(KEY_SHADE_CARD_GLASS_BLUR_RADIUS, ShadeCardGlassPolicy.normalize(value.shadeCardGlassBlurRadius, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(), 20f))
            .putFloat(
                KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS,
                ShadeCardGlassPolicy.normalize(value.headsUpBackgroundBlurRadius, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(), 60f),
            )
            .putFloat(
                KEY_GLOBAL_BACKGROUND_BLUR_PERCENT,
                value.globalBackgroundBlurPercent.coerceIn(0f, 200f),
            )
            .putBoolean(KEY_GLOBAL_BACKGROUND_DIM_ENABLED, value.globalBackgroundDimEnabled)
            .putFloat(
                KEY_GLOBAL_BACKGROUND_DIM_PERCENT,
                BackgroundDimPolicy.normalizePercent(value.globalBackgroundDimPercent),
            )
            .putBoolean(
                KEY_CONTROL_CENTER_FOLLOW_MILINK_BACKGROUND_MATERIAL,
                value.controlCenterFollowMiLinkBackgroundMaterial,
            )
            .remove("notification_background_effect_enabled")
            .remove("notification_background_blur_radius")
            .remove("notification_background_dim_amount")
            .putBoolean(KEY_CONTROL_CENTER, value.controlCenterEnabled)
            .putFloat(KEY_CONTROL_CENTER_RADIUS, value.controlCenterRadius)
            .remove("control_center_background_effect_enabled")
            .remove("control_center_background_blur_radius")
            .remove("control_center_background_dim_amount")
            .putBoolean(KEY_ADVANCED_CONTROL_CENTER_CORNERS, value.advancedControlCenterCorners)
            .putFloat(KEY_CONTROL_CENTER_TILE_RADIUS, value.controlCenterTileRadius)
            .putFloat(KEY_CONTROL_CENTER_CARD_RADIUS, value.controlCenterCardRadius)
            .putFloat(KEY_CONTROL_CENTER_SLIDER_RADIUS, value.controlCenterSliderRadius)
            .putFloat(KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS, value.controlCenterDetailSliderRadius)
            .putFloat(KEY_CONTROL_CENTER_MEDIA_RADIUS, value.controlCenterMediaRadius)
            .putFloat(KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS, value.controlCenterExternalEntryRadius)
            .putFloat(KEY_VOLUME_PANEL_RADIUS, value.volumePanelRadius)
            .putBoolean(KEY_MILINK_MAIN_CARDS, value.miLinkMainCardsEnabled)
            .putFloat(KEY_MILINK_MAIN_CARD_RADIUS, value.miLinkMainCardRadius)
            .putBoolean(KEY_MEDIA, value.mediaEnabled)
            .putFloat(KEY_EXPANDED, value.expandedHeight)
            .putFloat(KEY_COLLAPSED, value.collapsedHeight)
            .putFloat(KEY_FULL_AOD, value.fullAodHeight)
            .putBoolean(KEY_ISLAND, value.islandEnabled)
            .putFloat(KEY_ISLAND_HEIGHT, value.islandHeight)
            .putBoolean(KEY_ISLAND_PROGRESS, value.islandProgressBar)
            .putBoolean(KEY_SUPER_ISLAND_WHITELIST_DISABLED, value.superIslandWhitelistDisabled)
            .putBoolean(KEY_SUPER_ISLAND_HIDE_PULL_BAR, value.superIslandHidePullBar)
            .putBoolean(
                KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_ENABLED,
                value.superIslandContentBottomMarginEnabled,
            )
            .putFloat(
                KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_DP,
                normalizedSuperIslandContentBottomMargin(value.superIslandContentBottomMarginDp),
            )
            .putBoolean(KEY_HIDE_AOD_ACTIONS, value.hideAodActions)
            .putBoolean(KEY_HIDE_AOD_SEAMLESS, value.hideAodSeamless)
            .putBoolean(
                KEY_SINK_LOCKSCREEN_NOTIFICATIONS_FOR_FINGERPRINT,
                value.sinkLockscreenNotificationsForFingerprint,
            )
            .putBoolean(KEY_HIDE_LOCKSCREEN_FINGERPRINT_ICON, value.hideLockscreenFingerprintIcon)
            .putBoolean(KEY_LOWER_LOCKSCREEN_PASSWORD_PAGE, value.lowerLockscreenPasswordPage)
            .putBoolean(
                KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD,
                value.showLockscreenFingerprintIconOnAod,
            )
            .putBoolean(KEY_SETTINGS_HOME_ENTRY, value.settingsHomeEntryEnabled)
            .putBoolean(KEY_SETTINGS_MODULES_ENTRY, value.settingsModulesEntryEnabled)
            .putString(KEY_SETTINGS_ENTRY_POSITION, SettingsHomeHeaderPolicy.normalizePosition(value.settingsHomeEntryPosition))
            .putString(KEY_SETTINGS_MANAGER_POSITION, SettingsHomeHeaderPolicy.normalizePosition(value.settingsManagerEntryPosition))
            .putString(KEY_SETTINGS_MODULE_ENTRIES, value.settingsModuleEntries)
            .putBoolean(KEY_ABOUT_PHONE_CARDS, value.aboutPhoneCardsEnabled)
            .putString(KEY_PHONE_IMAGE_SOURCE, value.aboutPhoneImageSource)
            .putString(KEY_PHONE_PRESET_URL, value.aboutPhonePresetUrl)
            .putString(KEY_PHONE_CUSTOM_IMAGE, value.aboutPhoneCustomImage)
            .putString(KEY_PHONE_IMAGE_TRANSFORMS, value.aboutPhoneImageTransforms)
            .putBoolean(KEY_FORCE_LOCKSCREEN_CLOCK_COLON, value.forceLockscreenClockColon)
            .putBoolean(KEY_BYPASS_HYPER_MUSIC_COVER_CLOCK_ADJUSTMENT, value.bypassHyperMusicCoverClockAdjustment)
            .putBoolean(KEY_PROGRESSIVE_LOCKSCREEN_CLOCK_AVOIDANCE, value.progressiveLockscreenClockAvoidance)
            .putBoolean(KEY_AOD_CLOCK_WEIGHT_ENABLED, value.aodClockWeightEnabled)
            .putFloat(KEY_AOD_CLOCK_WEIGHT, normalizedAodClockWeight(value.aodClockWeight))
            .putBoolean(
                KEY_LOCKSCREEN_PASSWORD_BACKGROUND_BLUR_ENABLED,
                value.lockscreenPasswordBackgroundBlurEnabled,
            )
            .putFloat(
                KEY_LOCKSCREEN_PASSWORD_BACKGROUND_OPACITY,
                value.lockscreenPasswordBackgroundOpacity,
            )
            .putBoolean(
                KEY_LOCKSCREEN_PASSWORD_BACKGROUND_FOLLOW_SHADE_BLEND,
                value.lockscreenPasswordBackgroundFollowShadeBlend,
            )
            .putBoolean(
                KEY_LOCKSCREEN_PIN_KEY_SOFT_GLASS_ENABLED,
                value.lockscreenPinKeySoftGlassEnabled,
            )
            .putFloat(
                KEY_LOCKSCREEN_PIN_KEY_GLASS_EXTRA_RADIUS,
                value.lockscreenPinKeyGlassExtraRadius,
            )
            .putFloat(
                KEY_LOCKSCREEN_PIN_KEY_GLASS_VERTICAL_GAP,
                value.lockscreenPinKeyGlassVerticalGap,
            )
            .putBoolean(KEY_STATUS_BAR_HIDE_MOBILE_TYPE_ON_WIFI, value.statusBarHideMobileTypeOnWifi)
            .putBoolean(KEY_STATUS_BAR_HIDE_MOBILE_ACTIVITY, value.statusBarHideMobileActivity)
            .putBoolean(KEY_STATUS_BAR_HIDE_WIFI_ACTIVITY, value.statusBarHideWifiActivity)
            .putBoolean(KEY_STATUS_BAR_HIDE_WIFI_STANDARD, value.statusBarHideWifiStandard)
            .putFloat(KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_X, value.statusBarMobileActivityOffsetX)
            .putFloat(KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_Y, value.statusBarMobileActivityOffsetY)
            .putFloat(KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_X, value.statusBarWifiActivityOffsetX)
            .putFloat(KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_Y, value.statusBarWifiActivityOffsetY)
            .putFloat(KEY_STATUS_BAR_NETWORK_SPEED_RIGHT_GAP, value.statusBarNetworkSpeedRightGap)
            .putBoolean(KEY_STATUS_BAR_NETWORK_TYPE_ENABLED, value.statusBarNetworkTypeEnabled)
            .putFloat(KEY_STATUS_BAR_NETWORK_TYPE_SIZE, value.statusBarNetworkTypeSize)
            .putBoolean(KEY_STATUS_BAR_NETWORK_TYPE_BOLD, value.statusBarNetworkTypeBold)
            .putFloat(KEY_STATUS_BAR_NETWORK_TYPE_OFFSET, value.statusBarNetworkTypeOffset)
            .putFloat(
                KEY_HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
                value.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f),
            )
            .putBoolean(
                KEY_XIAOMI_HEALTH_FLOATING_NAVIGATION,
                value.xiaomiHealthFloatingNavigationEnabled,
            )
            .putBoolean(KEY_XIAOMI_HEALTH_MIUIX_ICONS, value.xiaomiHealthMiuixIconsEnabled)
            .putBoolean(
                KEY_XIAOMI_HEALTH_MONOCHROME_ICONS,
                value.xiaomiHealthMonochromeIconsEnabled,
            )
            .putBoolean(KEY_MARKET_FLOATING_NAVIGATION, value.marketFloatingNavigationEnabled)
            .putBoolean(KEY_MARKET_MIUIX_ICONS, value.marketMiuixIconsEnabled)
            .putBoolean(KEY_MARKET_MONOCHROME_ICONS, value.marketMonochromeIconsEnabled)
            .remove(KEY_MARKET_NAVIGATION_BADGES)
            .putBoolean(KEY_MARKET_HIDE_GAMES_TAB, value.marketHideGamesTab)
            .putBoolean(KEY_MARKET_HIDE_RANKINGS_TAB, value.marketHideRankingsTab)
            .putBoolean(KEY_MARKET_HIDE_PROFILE_TAB, value.marketHideProfileTab)
            .putBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, value.miHomeFloatingNavigationEnabled)
            .putBoolean(KEY_MI_HOME_MIUIX_ICONS, value.miHomeMiuixIconsEnabled)
            .remove(KEY_MI_HOME_MONOCHROME_ICONS)
            .putBoolean(KEY_MI_HOME_NAVIGATION_BADGES, value.miHomeNavigationBadgesEnabled)
            .putBoolean(KEY_AMAP_FLOATING_NAVIGATION, value.amapFloatingNavigationEnabled)
            .putBoolean(KEY_AMAP_MIUIX_ICONS, value.amapMiuixIconsEnabled)
            .putBoolean(KEY_AMAP_MONOCHROME_ICONS, value.amapMonochromeIconsEnabled)
            .putBoolean(
                KEY_AMAP_HIDE_LONG_PRESS_VOICE_TAB,
                value.amapHideLongPressVoiceTabEnabled,
            )
            .putBoolean(
                KEY_XIAOMI_COMMUNITY_FLOATING_NAVIGATION,
                value.xiaomiCommunityFloatingNavigationEnabled,
            )
            .putBoolean(KEY_XIAOMI_COMMUNITY_MIUIX_ICONS, value.xiaomiCommunityMiuixIconsEnabled)
            .putBoolean(
                KEY_XIAOMI_COMMUNITY_MONOCHROME_ICONS,
                value.xiaomiCommunityMonochromeIconsEnabled,
            )
            .putBoolean(
                KEY_XIAOMI_COMMUNITY_NAVIGATION_BADGES,
                value.xiaomiCommunityNavigationBadgesEnabled,
            )
            .putBoolean(KEY_BILIBILI_FLOATING_NAVIGATION_ENABLED, value.bilibiliFloatingNavigationEnabled)
            .putBoolean(KEY_BILIBILI_NAVIGATION_BADGES_ENABLED, value.bilibiliNavigationBadgesEnabled)
            .putBoolean(KEY_BILIBILI_HOME_TAB_VISIBLE, value.bilibiliHomeTabVisible)
            .putBoolean(KEY_BILIBILI_FOLLOW_TAB_VISIBLE, value.bilibiliFollowTabVisible)
            .putBoolean(KEY_BILIBILI_DYNAMIC_TAB_VISIBLE, value.bilibiliDynamicTabVisible)
            .putBoolean(KEY_BILIBILI_MALL_TAB_VISIBLE, value.bilibiliMallTabVisible)
            .putBoolean(KEY_BILIBILI_MINE_TAB_VISIBLE, value.bilibiliMineTabVisible)
            .putBoolean(KEY_BILIBILI_PUBLISH_BUTTON_VISIBLE, value.bilibiliPublishButtonVisible)
            .putBoolean(KEY_SPOTIFY_FLOATING_NAVIGATION, value.spotifyFloatingNavigationEnabled)
            .putBoolean(KEY_SPOTIFY_FAVORITE_BUTTON, value.spotifyFavoriteButtonEnabled)
            .putBoolean(KEY_SPOTIFY_SHUFFLE_BUTTON, value.spotifyShuffleButtonEnabled)
            .putBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, value.customMediaConstraintSetEnabled)
            .putString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, value.customMediaConstraintSetXml)
            .putBoolean(
                KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET,
                value.customMediaIslandConstraintSetEnabled,
            )
            .putString(
                KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET_XML,
                value.customMediaIslandConstraintSetXml,
            )
            .putString(KEY_MEDIA_LAYOUT_PRESET, value.mediaLayoutPreset)
            .putFloat(KEY_SYSTEM_MEDIA_HEIGHT, value.systemMediaHeight)
            .putFloat(KEY_COMPACT_MEDIA_HEIGHT, value.compactMediaHeight)
            .putFloat(KEY_STANDARD_MEDIA_HEIGHT, value.standardMediaHeight)
            .putFloat(KEY_CUSTOM_MEDIA_HEIGHT, value.customMediaHeight)
            .putString(KEY_SYSTEM_MEDIA_XML, value.systemMediaXml)
            .putString(KEY_SYSTEM_MEDIA_ISLAND_XML, value.systemMediaIslandXml)
            .putString(KEY_COMPACT_MEDIA_XML, value.compactMediaXml)
            .putString(KEY_COMPACT_MEDIA_ISLAND_XML, value.compactMediaIslandXml)
            .putString(KEY_STANDARD_MEDIA_XML, value.standardMediaXml)
            .putString(KEY_STANDARD_MEDIA_ISLAND_XML, value.standardMediaIslandXml)
        // Recovery switches and preset changes must survive an immediate process restart.
        if (presetChanged || safetyGateChanged) {
            if (!editor.commit()) Log.w(TAG, "Could not persist critical module settings")
        } else {
            editor.apply()
        }
        syncRemotePreferences(context)
        context.contentResolver.notifyChange(SETTINGS_URI, null)
    }

    /**
     * API 102 remote preferences are an LSPosed-owned key/value store, separate from this
     * application's private SharedPreferences XML. Keep a complete mirror there so hooked
     * processes can retrieve settings without resolving a cross-package ContentProvider.
     */
    fun onXposedServiceBound(context: Context, service: XposedService) {
        remoteService = service
        syncRemotePreferences(context)
    }

    internal fun cachedPhonePreset(context: Context, key: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getString("about_phone_preset_image_key", "") == key)
            prefs.getString("about_phone_preset_image", "").orEmpty() else ""
    }

    internal fun cachePhonePreset(context: Context, key: String, image: String) {
        if (cachedPhonePreset(context, key) == image) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().putString("about_phone_preset_image_key", key)
            .putString("about_phone_preset_image", image).commit()) { "无法保存图片缓存。" }
        syncRemotePreferences(context)
    }

    fun onXposedServiceDied() {
        remoteService = null
    }

    @Synchronized
    fun ensureDiagnosticToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getString(KEY_DIAGNOSTIC_TOKEN, null).isNullOrEmpty()) return
        if (!prefs.edit().putString(KEY_DIAGNOSTIC_TOKEN, UUID.randomUUID().toString()).commit()) {
            Log.w(TAG, "Could not initialize Hook diagnostic token")
        }
    }

    fun diagnosticToken(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_DIAGNOSTIC_TOKEN, "").orEmpty()

    private fun syncRemotePreferences(context: Context) {
        val service = remoteService ?: return
        try {
            ensureDiagnosticToken(context)
            val source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Preserve the actual framework scope for offline UI and SystemUI classification.
            source.edit().putString(KEY_GESTURE_HANDLE_SCOPE_PACKAGES, service.scope.joinToString("\n")).apply()
            val editor = service.getRemotePreferences(PREFS).edit().clear()
            source.all.forEach { (key, value) -> editor.putRemoteValue(key, value) }
            // A synchronous commit makes the snapshot available before LSPosed starts a scoped
            // process immediately after the user changes a setting.
            editor.commit()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Could not mirror settings to LSPosed remote preferences", throwable)
        }
    }

    private fun SharedPreferences.Editor.putRemoteValue(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private val SETTINGS_URI = Uri.parse("content://com.aritxonly.myhypermodifier.settings")
    @Volatile private var remoteService: XposedService? = null

    internal fun gestureHandleScopePackages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GESTURE_HANDLE_SCOPE_PACKAGES, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toSet()

    fun toBundle(value: ModifierSettings) = Bundle().apply {
        putBoolean(KEY_MODULE_HOOKS_ENABLED, value.moduleHooksEnabled)
        putBoolean(KEY_SYSTEM_UI_COMPATIBILITY_MODE, value.systemUiCompatibilityMode)
        putInt(KEY_SYSTEM_UI_RETRY_GENERATION, value.systemUiRetryGeneration)
        putBoolean(KEY_GESTURE_HANDLE_ENABLED, value.gestureHandleEnabled)
        putString(KEY_GESTURE_HANDLE_PRESET, value.gestureHandlePreset)
        putBoolean(KEY_GESTURE_HANDLE_MODULE_PRESET,
            value.gestureHandlePreset == GestureHandlePresets.MODULE)
        putString(KEY_GESTURE_HANDLE_APP_MODES, GestureHandleRules.encode(value.gestureHandleAppModes))
        putBoolean(KEY_GESTURE_HANDLE_TOUCH_REVEAL, value.gestureHandleTouchReveal)
        putBoolean(KEY_GESTURE_HANDLE_SWIPE_MOTION, value.gestureHandleSwipeMotion)
        putFloat(KEY_GESTURE_HANDLE_TOUCH_AREA_DP,
            GestureHandleTouchArea.normalize(value.gestureHandleTouchAreaDp))
        putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
        putFloat(KEY_NOTIFICATION_RADIUS, value.notificationRadius)
        putBoolean(KEY_HIDE_HEADS_UP_MINI_BAR, value.hideHeadsUpMiniBar)
        putBoolean(KEY_HEADS_UP_BOTTOM_MARGIN_ENABLED, value.headsUpBottomMarginEnabled)
        putFloat(
            KEY_HEADS_UP_BOTTOM_MARGIN_DP,
            normalizedHeadsUpBottomMargin(value.headsUpBottomMarginDp),
        )
        putBoolean(KEY_HEADS_UP_GLASS_PARAMETERS_ENABLED, value.headsUpGlassParametersEnabled)
        putString(KEY_HEADS_UP_GLASS_PARAMETERS, value.headsUpGlassParameters)
        putString(KEY_HEADS_UP_GLASS_DARK_PARAMETERS, value.headsUpGlassDarkParameters)
        putBoolean(KEY_SHADE_CARD_GLASS_ENABLED, value.shadeCardGlassParametersEnabled)
        putBoolean(KEY_DISABLE_SHADE_GLASS_HOOKS, value.disableShadeGlassHooks)
        putBoolean(KEY_GLOBAL_GLASS_BLUR_ENABLED, value.globalGlassBlurEnabled)
        putString(KEY_SHADE_CARD_GLASS_PARAMETERS, value.shadeCardGlassParameters)
        putFloat(KEY_SHADE_CARD_BACKGROUND_BLUR_PERCENT, ShadeCardGlassPolicy.normalize(value.shadeCardBackgroundBlurPercent, 200f, 100f))
        putBoolean(KEY_SHADE_CARD_GLASS_BLUR_ENABLED, value.shadeCardGlassBlurEnabled)
        putFloat(KEY_SHADE_CARD_GLASS_BLUR_RADIUS, ShadeCardGlassPolicy.normalize(value.shadeCardGlassBlurRadius, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(), 20f))
        putBoolean(
            KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS_ENABLED,
            value.headsUpBackgroundBlurRadiusEnabled,
        )
        putFloat(
            KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS,
            ShadeCardGlassPolicy.normalize(value.headsUpBackgroundBlurRadius, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(), 60f),
        )
        putFloat(
            KEY_GLOBAL_BACKGROUND_BLUR_PERCENT,
            value.globalBackgroundBlurPercent.coerceIn(0f, 200f),
        )
        putBoolean(KEY_GLOBAL_BACKGROUND_DIM_ENABLED, value.globalBackgroundDimEnabled)
        putFloat(
            KEY_GLOBAL_BACKGROUND_DIM_PERCENT,
            BackgroundDimPolicy.normalizePercent(value.globalBackgroundDimPercent),
        )
        putBoolean(
            KEY_CONTROL_CENTER_FOLLOW_MILINK_BACKGROUND_MATERIAL,
            value.controlCenterFollowMiLinkBackgroundMaterial,
        )
        putBoolean(KEY_CONTROL_CENTER, value.controlCenterEnabled)
        putFloat(KEY_CONTROL_CENTER_RADIUS, value.controlCenterRadius)
        putBoolean(KEY_ADVANCED_CONTROL_CENTER_CORNERS, value.advancedControlCenterCorners)
        putFloat(KEY_CONTROL_CENTER_TILE_RADIUS, value.controlCenterTileRadius)
        putFloat(KEY_CONTROL_CENTER_CARD_RADIUS, value.controlCenterCardRadius)
        putFloat(KEY_CONTROL_CENTER_SLIDER_RADIUS, value.controlCenterSliderRadius)
        putFloat(KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS, value.controlCenterDetailSliderRadius)
        putFloat(KEY_CONTROL_CENTER_MEDIA_RADIUS, value.controlCenterMediaRadius)
        putFloat(KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS, value.controlCenterExternalEntryRadius)
        putFloat(KEY_VOLUME_PANEL_RADIUS, value.volumePanelRadius)
        putBoolean(KEY_MILINK_MAIN_CARDS, value.miLinkMainCardsEnabled)
        putFloat(KEY_MILINK_MAIN_CARD_RADIUS, value.miLinkMainCardRadius)
        putBoolean(KEY_MEDIA, value.mediaEnabled)
        putFloat(KEY_EXPANDED, value.expandedHeight)
        putFloat(KEY_COLLAPSED, value.collapsedHeight)
        putFloat(KEY_FULL_AOD, value.fullAodHeight)
        putBoolean(KEY_ISLAND, value.islandEnabled)
        putFloat(KEY_ISLAND_HEIGHT, value.islandHeight)
        putBoolean(KEY_ISLAND_PROGRESS, value.islandProgressBar)
        putBoolean(KEY_SUPER_ISLAND_WHITELIST_DISABLED, value.superIslandWhitelistDisabled)
        putBoolean(KEY_SUPER_ISLAND_HIDE_PULL_BAR, value.superIslandHidePullBar)
        putBoolean(
            KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_ENABLED,
            value.superIslandContentBottomMarginEnabled,
        )
        putFloat(
            KEY_SUPER_ISLAND_CONTENT_BOTTOM_MARGIN_DP,
            normalizedSuperIslandContentBottomMargin(value.superIslandContentBottomMarginDp),
        )
        putBoolean(KEY_HIDE_AOD_ACTIONS, value.hideAodActions)
        putBoolean(KEY_HIDE_AOD_SEAMLESS, value.hideAodSeamless)
        putBoolean(
            KEY_SINK_LOCKSCREEN_NOTIFICATIONS_FOR_FINGERPRINT,
            value.sinkLockscreenNotificationsForFingerprint,
        )
        putBoolean(KEY_HIDE_LOCKSCREEN_FINGERPRINT_ICON, value.hideLockscreenFingerprintIcon)
        putBoolean(KEY_LOWER_LOCKSCREEN_PASSWORD_PAGE, value.lowerLockscreenPasswordPage)
        putBoolean(
            KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD,
            value.showLockscreenFingerprintIconOnAod,
        )
        putBoolean(KEY_SETTINGS_HOME_ENTRY, value.settingsHomeEntryEnabled)
        putBoolean(KEY_SETTINGS_MODULES_ENTRY, value.settingsModulesEntryEnabled)
        putString(KEY_SETTINGS_ENTRY_POSITION, SettingsHomeHeaderPolicy.normalizePosition(value.settingsHomeEntryPosition))
        putString(KEY_SETTINGS_MANAGER_POSITION, SettingsHomeHeaderPolicy.normalizePosition(value.settingsManagerEntryPosition))
        putString(KEY_SETTINGS_MODULE_ENTRIES, value.settingsModuleEntries)
        putBoolean(KEY_ABOUT_PHONE_CARDS, value.aboutPhoneCardsEnabled)
        putString(KEY_PHONE_IMAGE_SOURCE, value.aboutPhoneImageSource)
        putString(KEY_PHONE_PRESET_URL, value.aboutPhonePresetUrl)
        putString(KEY_PHONE_CUSTOM_IMAGE, value.aboutPhoneCustomImage)
        putString(KEY_PHONE_IMAGE_TRANSFORMS, value.aboutPhoneImageTransforms)
        putBoolean(KEY_FORCE_LOCKSCREEN_CLOCK_COLON, value.forceLockscreenClockColon)
        putBoolean(KEY_BYPASS_HYPER_MUSIC_COVER_CLOCK_ADJUSTMENT, value.bypassHyperMusicCoverClockAdjustment)
        putBoolean(KEY_PROGRESSIVE_LOCKSCREEN_CLOCK_AVOIDANCE, value.progressiveLockscreenClockAvoidance)
        putBoolean(KEY_AOD_CLOCK_WEIGHT_ENABLED, value.aodClockWeightEnabled)
        putFloat(KEY_AOD_CLOCK_WEIGHT, normalizedAodClockWeight(value.aodClockWeight))
        putBoolean(
            KEY_LOCKSCREEN_PASSWORD_BACKGROUND_BLUR_ENABLED,
            value.lockscreenPasswordBackgroundBlurEnabled,
        )
        putFloat(
            KEY_LOCKSCREEN_PASSWORD_BACKGROUND_OPACITY,
            value.lockscreenPasswordBackgroundOpacity,
        )
        putBoolean(
            KEY_LOCKSCREEN_PASSWORD_BACKGROUND_FOLLOW_SHADE_BLEND,
            value.lockscreenPasswordBackgroundFollowShadeBlend,
        )
        putBoolean(
            KEY_LOCKSCREEN_PIN_KEY_SOFT_GLASS_ENABLED,
            value.lockscreenPinKeySoftGlassEnabled,
        )
        putFloat(
            KEY_LOCKSCREEN_PIN_KEY_GLASS_EXTRA_RADIUS,
            value.lockscreenPinKeyGlassExtraRadius,
        )
        putFloat(
            KEY_LOCKSCREEN_PIN_KEY_GLASS_VERTICAL_GAP,
            value.lockscreenPinKeyGlassVerticalGap,
        )
        putBoolean(KEY_STATUS_BAR_HIDE_MOBILE_TYPE_ON_WIFI, value.statusBarHideMobileTypeOnWifi)
        putBoolean(KEY_STATUS_BAR_HIDE_MOBILE_ACTIVITY, value.statusBarHideMobileActivity)
        putBoolean(KEY_STATUS_BAR_HIDE_WIFI_ACTIVITY, value.statusBarHideWifiActivity)
        putBoolean(KEY_STATUS_BAR_HIDE_WIFI_STANDARD, value.statusBarHideWifiStandard)
        putFloat(KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_X, value.statusBarMobileActivityOffsetX)
        putFloat(KEY_STATUS_BAR_MOBILE_ACTIVITY_OFFSET_Y, value.statusBarMobileActivityOffsetY)
        putFloat(KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_X, value.statusBarWifiActivityOffsetX)
        putFloat(KEY_STATUS_BAR_WIFI_ACTIVITY_OFFSET_Y, value.statusBarWifiActivityOffsetY)
        putFloat(KEY_STATUS_BAR_NETWORK_SPEED_RIGHT_GAP, value.statusBarNetworkSpeedRightGap)
        putBoolean(KEY_STATUS_BAR_NETWORK_TYPE_ENABLED, value.statusBarNetworkTypeEnabled)
        putFloat(KEY_STATUS_BAR_NETWORK_TYPE_SIZE, value.statusBarNetworkTypeSize)
        putBoolean(KEY_STATUS_BAR_NETWORK_TYPE_BOLD, value.statusBarNetworkTypeBold)
        putFloat(KEY_STATUS_BAR_NETWORK_TYPE_OFFSET, value.statusBarNetworkTypeOffset)
        putFloat(
            KEY_HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
            value.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f),
        )
        putBoolean(
            KEY_XIAOMI_HEALTH_FLOATING_NAVIGATION,
            value.xiaomiHealthFloatingNavigationEnabled,
        )
        putBoolean(KEY_XIAOMI_HEALTH_MIUIX_ICONS, value.xiaomiHealthMiuixIconsEnabled)
        putBoolean(
            KEY_XIAOMI_HEALTH_MONOCHROME_ICONS,
            value.xiaomiHealthMonochromeIconsEnabled,
        )
        putBoolean(KEY_MARKET_FLOATING_NAVIGATION, value.marketFloatingNavigationEnabled)
        putBoolean(KEY_MARKET_MIUIX_ICONS, value.marketMiuixIconsEnabled)
        putBoolean(KEY_MARKET_MONOCHROME_ICONS, value.marketMonochromeIconsEnabled)
        putBoolean(KEY_MARKET_NAVIGATION_BADGES, false)
        putBoolean(KEY_MARKET_HIDE_GAMES_TAB, value.marketHideGamesTab)
        putBoolean(KEY_MARKET_HIDE_RANKINGS_TAB, value.marketHideRankingsTab)
        putBoolean(KEY_MARKET_HIDE_PROFILE_TAB, value.marketHideProfileTab)
        putBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, value.miHomeFloatingNavigationEnabled)
        putBoolean(KEY_MI_HOME_MIUIX_ICONS, value.miHomeMiuixIconsEnabled)
        putBoolean(KEY_MI_HOME_NAVIGATION_BADGES, value.miHomeNavigationBadgesEnabled)
        putBoolean(KEY_AMAP_FLOATING_NAVIGATION, value.amapFloatingNavigationEnabled)
        putBoolean(KEY_AMAP_MIUIX_ICONS, value.amapMiuixIconsEnabled)
        putBoolean(KEY_AMAP_MONOCHROME_ICONS, value.amapMonochromeIconsEnabled)
        putBoolean(
            KEY_AMAP_HIDE_LONG_PRESS_VOICE_TAB,
            value.amapHideLongPressVoiceTabEnabled,
        )
        putBoolean(
            KEY_XIAOMI_COMMUNITY_FLOATING_NAVIGATION,
            value.xiaomiCommunityFloatingNavigationEnabled,
        )
        putBoolean(KEY_XIAOMI_COMMUNITY_MIUIX_ICONS, value.xiaomiCommunityMiuixIconsEnabled)
        putBoolean(
            KEY_XIAOMI_COMMUNITY_MONOCHROME_ICONS,
            value.xiaomiCommunityMonochromeIconsEnabled,
        )
        putBoolean(
            KEY_XIAOMI_COMMUNITY_NAVIGATION_BADGES,
            value.xiaomiCommunityNavigationBadgesEnabled,
        )
        putBoolean(KEY_BILIBILI_FLOATING_NAVIGATION_ENABLED, value.bilibiliFloatingNavigationEnabled)
        putBoolean(KEY_BILIBILI_NAVIGATION_BADGES_ENABLED, value.bilibiliNavigationBadgesEnabled)
        putBoolean(KEY_BILIBILI_HOME_TAB_VISIBLE, value.bilibiliHomeTabVisible)
        putBoolean(KEY_BILIBILI_FOLLOW_TAB_VISIBLE, value.bilibiliFollowTabVisible)
        putBoolean(KEY_BILIBILI_DYNAMIC_TAB_VISIBLE, value.bilibiliDynamicTabVisible)
        putBoolean(KEY_BILIBILI_MALL_TAB_VISIBLE, value.bilibiliMallTabVisible)
        putBoolean(KEY_BILIBILI_MINE_TAB_VISIBLE, value.bilibiliMineTabVisible)
        putBoolean(KEY_BILIBILI_PUBLISH_BUTTON_VISIBLE, value.bilibiliPublishButtonVisible)
        putBoolean(KEY_SPOTIFY_FLOATING_NAVIGATION, value.spotifyFloatingNavigationEnabled)
        putBoolean(KEY_SPOTIFY_FAVORITE_BUTTON, value.spotifyFavoriteButtonEnabled)
        putBoolean(KEY_SPOTIFY_SHUFFLE_BUTTON, value.spotifyShuffleButtonEnabled)
        putBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, value.customMediaConstraintSetEnabled)
        putString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, value.customMediaConstraintSetXml)
        putBoolean(
            KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET,
            value.customMediaIslandConstraintSetEnabled,
        )
        putString(
            KEY_CUSTOM_MEDIA_ISLAND_CONSTRAINT_SET_XML,
            value.customMediaIslandConstraintSetXml,
        )
        putString(KEY_MEDIA_LAYOUT_PRESET, value.mediaLayoutPreset)
        putFloat(KEY_SYSTEM_MEDIA_HEIGHT, value.systemMediaHeight)
        putFloat(KEY_COMPACT_MEDIA_HEIGHT, value.compactMediaHeight)
        putFloat(KEY_STANDARD_MEDIA_HEIGHT, value.standardMediaHeight)
        putFloat(KEY_CUSTOM_MEDIA_HEIGHT, value.customMediaHeight)
        putString(KEY_SYSTEM_MEDIA_XML, value.systemMediaXml)
        putString(KEY_SYSTEM_MEDIA_ISLAND_XML, value.systemMediaIslandXml)
        putString(KEY_COMPACT_MEDIA_XML, value.compactMediaXml)
        putString(KEY_COMPACT_MEDIA_ISLAND_XML, value.compactMediaIslandXml)
        putString(KEY_STANDARD_MEDIA_XML, value.standardMediaXml)
        putString(KEY_STANDARD_MEDIA_ISLAND_XML, value.standardMediaIslandXml)
    }

    private fun normalizedAodClockWeight(weight: Float): Float =
        if (weight.isFinite()) weight.coerceIn(100f, 700f) else ModuleDefaultValues.AOD_CLOCK_WEIGHT

    private fun normalizedHeadsUpBottomMargin(margin: Float): Float =
        if (margin.isFinite()) margin.coerceIn(0f, 32f) else 13f

    private fun normalizedSuperIslandContentBottomMargin(margin: Float): Float =
        if (margin.isFinite()) margin.coerceIn(0f, 48f) else 8f
}
