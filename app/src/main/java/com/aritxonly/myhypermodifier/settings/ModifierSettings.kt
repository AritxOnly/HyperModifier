package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.libxposed.service.XposedService

data class ModifierSettings(
    val notificationsEnabled: Boolean = false,
    val notificationRadius: Float = 28f,
    val hideHeadsUpMiniBar: Boolean = false,
    val headsUpBottomMarginEnabled: Boolean = false,
    val headsUpBottomMarginDp: Float = 13f,
    val headsUpGlassParametersEnabled: Boolean = false,
    val headsUpGlassParameters: String = HeadsUpGlassParameters.regularSerialized,
    val headsUpGlassDarkParameters: String = HeadsUpGlassParameters.darkSerialized,
    val headsUpBackgroundBlurRadiusEnabled: Boolean = false,
    val headsUpBackgroundBlurRadius: Float = 60f,
    /** Multiplies the stock blur radius for shared SystemUI background surfaces. */
    val globalBackgroundBlurPercent: Float = 100f,
    val controlCenterFollowMiLinkBackgroundMaterial: Boolean = true,
    val controlCenterEnabled: Boolean = false,
    val controlCenterRadius: Float = 28f,
    val advancedControlCenterCorners: Boolean = false,
    val controlCenterTileRadius: Float = 28f,
    val controlCenterCardRadius: Float = 28f,
    val controlCenterSliderRadius: Float = 28f,
    val controlCenterDetailSliderRadius: Float = 28f,
    val controlCenterMediaRadius: Float = 28f,
    val controlCenterExternalEntryRadius: Float = 28f,
    val volumePanelRadius: Float = 0f,
    val miLinkMainCardsEnabled: Boolean = false,
    val miLinkMainCardRadius: Float = 20f,
    val mediaEnabled: Boolean = false,
    val expandedHeight: Float = 152f,
    val collapsedHeight: Float = 120f,
    val fullAodHeight: Float = 80f,
    val islandEnabled: Boolean = false,
    val islandHeight: Float = 160f,
    val islandProgressBar: Boolean = false,
    val hideAodActions: Boolean = false,
    val hideAodSeamless: Boolean = false,
    val sinkLockscreenNotificationsForFingerprint: Boolean = false,
    val hideLockscreenFingerprintIcon: Boolean = false,
    val lowerLockscreenPasswordPage: Boolean = false,
    val showLockscreenFingerprintIconOnAod: Boolean = false,
    val forceLockscreenClockColon: Boolean = false,
    val aodClockWeightEnabled: Boolean = false,
    val aodClockWeight: Float = 400f,
    val lockscreenPasswordBackgroundBlurEnabled: Boolean = false,
    /** Absolute value returned by SystemUI's wallpaperBlurRatio coroutine, from 0.0 to 1.0. */
    val lockscreenPasswordBackgroundOpacity: Float = 0f,
    val lockscreenPasswordBackgroundFollowShadeBlend: Boolean = true,
    val lockscreenPinKeySoftGlassEnabled: Boolean = false,
    val lockscreenPinKeyGlassExtraRadius: Float = 6f,
    val lockscreenPinKeyGlassVerticalGap: Float = 16f,
    val statusBarNetworkTypeEnabled: Boolean = false,
    val statusBarNetworkTypeSize: Float = 13.5f,
    val statusBarNetworkTypeBold: Boolean = true,
    val statusBarNetworkTypeOffset: Float = 0f,
    val hyperGlassifyHiddenNavigationLift: Float = 24f,
    val xiaomiHealthFloatingNavigationEnabled: Boolean = true,
    val xiaomiHealthMiuixIconsEnabled: Boolean = false,
    val xiaomiHealthMonochromeIconsEnabled: Boolean = true,
    val marketFloatingNavigationEnabled: Boolean = true,
    val marketMiuixIconsEnabled: Boolean = false,
    val marketMonochromeIconsEnabled: Boolean = true,
    // Application Store badges stay hidden in the glass navigation by design.
    val marketNavigationBadgesEnabled: Boolean = false,
    val marketHideGamesTab: Boolean = false,
    val marketHideRankingsTab: Boolean = false,
    val marketHideProfileTab: Boolean = false,
    val miHomeFloatingNavigationEnabled: Boolean = true,
    val miHomeMiuixIconsEnabled: Boolean = false,
    val miHomeNavigationBadgesEnabled: Boolean = true,
    val amapFloatingNavigationEnabled: Boolean = true,
    val amapMiuixIconsEnabled: Boolean = false,
    val amapMonochromeIconsEnabled: Boolean = true,
    val amapHideLongPressVoiceTabEnabled: Boolean = true,
    val xiaomiCommunityFloatingNavigationEnabled: Boolean = true,
    val xiaomiCommunityMiuixIconsEnabled: Boolean = false,
    val xiaomiCommunityMonochromeIconsEnabled: Boolean = true,
    val xiaomiCommunityNavigationBadgesEnabled: Boolean = true,
    val spotifyFloatingNavigationEnabled: Boolean = false,
    val spotifyFavoriteButtonEnabled: Boolean = false,
    val spotifyShuffleButtonEnabled: Boolean = false,
    val customMediaConstraintSetEnabled: Boolean = false,
    val customMediaConstraintSetXml: String = "",
)

/** Explicit presets keep reset behavior independent from persisted values and future migrations. */
object ModifierSettingsPresets {
    /** Fresh installs enable HyperGlassify only; SystemUI beautification stays opt-in. */
    fun moduleDefault(): ModifierSettings = ModifierSettings()

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
        hideAodActions = false,
        hideAodSeamless = false,
        sinkLockscreenNotificationsForFingerprint = false,
        hideLockscreenFingerprintIcon = false,
        lowerLockscreenPasswordPage = false,
        showLockscreenFingerprintIconOnAod = false,
        forceLockscreenClockColon = false,
        aodClockWeightEnabled = false,
        lockscreenPasswordBackgroundBlurEnabled = false,
        lockscreenPasswordBackgroundFollowShadeBlend = true,
        lockscreenPinKeySoftGlassEnabled = false,
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
        spotifyFloatingNavigationEnabled = false,
        spotifyFavoriteButtonEnabled = false,
        spotifyShuffleButtonEnabled = false,
        customMediaConstraintSetEnabled = false,
        customMediaConstraintSetXml = "",
    )
}

object ModifierSettingsStore {
    private const val TAG = "MyHyperModifier"
    const val PREFS = "modifier_settings"
    const val METHOD_GET = "get_settings"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_NOTIFICATION_RADIUS = "notification_radius"
    private const val KEY_HIDE_HEADS_UP_MINI_BAR = "hide_heads_up_mini_bar"
    private const val KEY_HEADS_UP_BOTTOM_MARGIN_ENABLED = "heads_up_bottom_margin_enabled"
    private const val KEY_HEADS_UP_BOTTOM_MARGIN_DP = "heads_up_bottom_margin_dp"
    private const val KEY_HEADS_UP_GLASS_PARAMETERS_ENABLED = "heads_up_glass_parameters_enabled"
    private const val KEY_HEADS_UP_GLASS_PARAMETERS = "heads_up_glass_parameters"
    private const val KEY_HEADS_UP_GLASS_DARK_PARAMETERS = "heads_up_glass_dark_parameters"
    private const val KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS_ENABLED =
        "heads_up_background_blur_radius_enabled"
    private const val KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS = "heads_up_background_blur_radius"
    private const val KEY_GLOBAL_BACKGROUND_BLUR_PERCENT = "global_background_blur_percent"
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
    private const val KEY_HIDE_AOD_ACTIONS = "hide_aod_actions"
    private const val KEY_HIDE_AOD_SEAMLESS = "hide_aod_seamless"
    private const val KEY_SINK_LOCKSCREEN_NOTIFICATIONS_FOR_FINGERPRINT =
        "sink_lockscreen_notifications_for_fingerprint"
    private const val KEY_HIDE_LOCKSCREEN_FINGERPRINT_ICON = "hide_lockscreen_fingerprint_icon"
    private const val KEY_LOWER_LOCKSCREEN_PASSWORD_PAGE = "lower_lockscreen_password_page"
    private const val KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD =
        "show_lockscreen_fingerprint_icon_on_aod"
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
    private const val KEY_SPOTIFY_FLOATING_NAVIGATION = "spotify_floating_navigation_enabled"
    private const val KEY_SPOTIFY_FAVORITE_BUTTON = "spotify_favorite_button_enabled"
    private const val KEY_SPOTIFY_SHUFFLE_BUTTON = "spotify_shuffle_button_enabled"
    private const val KEY_CUSTOM_MEDIA_CONSTRAINT_SET = "custom_media_constraint_set_enabled"
    private const val KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML = "custom_media_constraint_set_xml"

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
        return ModifierSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, false),
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
            headsUpBackgroundBlurRadius = prefs.getFloat(
                KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS,
                60f,
            ).coerceIn(0f, 200f),
            globalBackgroundBlurPercent = prefs.getFloat(
                KEY_GLOBAL_BACKGROUND_BLUR_PERCENT,
                100f,
            ).coerceIn(0f, 200f),
            controlCenterFollowMiLinkBackgroundMaterial = prefs.getBoolean(
                KEY_CONTROL_CENTER_FOLLOW_MILINK_BACKGROUND_MATERIAL,
                true,
            ),
            controlCenterEnabled = prefs.getBoolean(KEY_CONTROL_CENTER, false),
            controlCenterRadius = prefs.getFloat(KEY_CONTROL_CENTER_RADIUS, 28f),
            advancedControlCenterCorners = prefs.getBoolean(KEY_ADVANCED_CONTROL_CENTER_CORNERS, false),
            controlCenterTileRadius = prefs.getFloat(KEY_CONTROL_CENTER_TILE_RADIUS, 28f),
            controlCenterCardRadius = prefs.getFloat(KEY_CONTROL_CENTER_CARD_RADIUS, 28f),
            controlCenterSliderRadius = prefs.getFloat(KEY_CONTROL_CENTER_SLIDER_RADIUS, 28f),
            controlCenterDetailSliderRadius = prefs.getFloat(KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS, 28f),
            controlCenterMediaRadius = prefs.getFloat(KEY_CONTROL_CENTER_MEDIA_RADIUS, 28f),
            controlCenterExternalEntryRadius = prefs.getFloat(KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS, 28f),
            volumePanelRadius = prefs.getFloat(KEY_VOLUME_PANEL_RADIUS, 0f),
            miLinkMainCardsEnabled = prefs.getBoolean(KEY_MILINK_MAIN_CARDS, false),
            miLinkMainCardRadius = prefs.getFloat(KEY_MILINK_MAIN_CARD_RADIUS, 20f),
            mediaEnabled = prefs.getBoolean(KEY_MEDIA, false),
            expandedHeight = prefs.getFloat(KEY_EXPANDED, 152f),
            collapsedHeight = prefs.getFloat(KEY_COLLAPSED, 120f),
            fullAodHeight = prefs.getFloat(KEY_FULL_AOD, 80f),
            islandEnabled = prefs.getBoolean(KEY_ISLAND, false),
            islandHeight = prefs.getFloat(KEY_ISLAND_HEIGHT, 160f),
            islandProgressBar = prefs.getBoolean(KEY_ISLAND_PROGRESS, false),
            hideAodActions = prefs.getBoolean(KEY_HIDE_AOD_ACTIONS, false),
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
            forceLockscreenClockColon = prefs.getBoolean(KEY_FORCE_LOCKSCREEN_CLOCK_COLON, false),
            aodClockWeightEnabled = prefs.getBoolean(KEY_AOD_CLOCK_WEIGHT_ENABLED, false),
            aodClockWeight = normalizedAodClockWeight(prefs.getFloat(KEY_AOD_CLOCK_WEIGHT, 400f)),
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
            statusBarNetworkTypeEnabled = prefs.getBoolean(KEY_STATUS_BAR_NETWORK_TYPE_ENABLED, false),
            statusBarNetworkTypeSize = prefs.getFloat(KEY_STATUS_BAR_NETWORK_TYPE_SIZE, 13.5f),
            statusBarNetworkTypeBold = prefs.getBoolean(KEY_STATUS_BAR_NETWORK_TYPE_BOLD, true),
            statusBarNetworkTypeOffset = prefs.getFloat(KEY_STATUS_BAR_NETWORK_TYPE_OFFSET, 0f),
            hyperGlassifyHiddenNavigationLift = prefs.getFloat(
                KEY_HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
                24f,
            ).coerceIn(0f, 48f),
            xiaomiHealthFloatingNavigationEnabled = prefs.getBoolean(
                KEY_XIAOMI_HEALTH_FLOATING_NAVIGATION,
                true,
            ),
            xiaomiHealthMiuixIconsEnabled = prefs.getBoolean(KEY_XIAOMI_HEALTH_MIUIX_ICONS, false),
            xiaomiHealthMonochromeIconsEnabled = prefs.getBoolean(
                KEY_XIAOMI_HEALTH_MONOCHROME_ICONS,
                true,
            ),
            marketFloatingNavigationEnabled = prefs.getBoolean(KEY_MARKET_FLOATING_NAVIGATION, true),
            marketMiuixIconsEnabled = prefs.getBoolean(KEY_MARKET_MIUIX_ICONS, false),
            marketMonochromeIconsEnabled = prefs.getBoolean(KEY_MARKET_MONOCHROME_ICONS, true),
            marketNavigationBadgesEnabled = false,
            marketHideGamesTab = prefs.getBoolean(KEY_MARKET_HIDE_GAMES_TAB, false),
            marketHideRankingsTab = prefs.getBoolean(KEY_MARKET_HIDE_RANKINGS_TAB, false),
            marketHideProfileTab = prefs.getBoolean(KEY_MARKET_HIDE_PROFILE_TAB, false),
            miHomeFloatingNavigationEnabled = prefs.getBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, true),
            miHomeMiuixIconsEnabled = prefs.getBoolean(KEY_MI_HOME_MIUIX_ICONS, false),
            miHomeNavigationBadgesEnabled = prefs.getBoolean(KEY_MI_HOME_NAVIGATION_BADGES, true),
            amapFloatingNavigationEnabled = prefs.getBoolean(KEY_AMAP_FLOATING_NAVIGATION, true),
            amapMiuixIconsEnabled = prefs.getBoolean(KEY_AMAP_MIUIX_ICONS, false),
            amapMonochromeIconsEnabled = prefs.getBoolean(KEY_AMAP_MONOCHROME_ICONS, true),
            amapHideLongPressVoiceTabEnabled = prefs.getBoolean(
                KEY_AMAP_HIDE_LONG_PRESS_VOICE_TAB,
                true,
            ),
            xiaomiCommunityFloatingNavigationEnabled = prefs.getBoolean(
                KEY_XIAOMI_COMMUNITY_FLOATING_NAVIGATION,
                true,
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
            spotifyFloatingNavigationEnabled = prefs.getBoolean(KEY_SPOTIFY_FLOATING_NAVIGATION, false),
            spotifyFavoriteButtonEnabled = prefs.getBoolean(KEY_SPOTIFY_FAVORITE_BUTTON, false),
            spotifyShuffleButtonEnabled = prefs.getBoolean(KEY_SPOTIFY_SHUFFLE_BUTTON, false),
            customMediaConstraintSetEnabled = prefs.getBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, false),
            customMediaConstraintSetXml = prefs.getString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, "").orEmpty(),
        )
    }

    fun save(context: Context, value: ModifierSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
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
            .putFloat(
                KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS,
                value.headsUpBackgroundBlurRadius.coerceIn(0f, 200f),
            )
            .putFloat(
                KEY_GLOBAL_BACKGROUND_BLUR_PERCENT,
                value.globalBackgroundBlurPercent.coerceIn(0f, 200f),
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
            .putBoolean(KEY_FORCE_LOCKSCREEN_CLOCK_COLON, value.forceLockscreenClockColon)
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
            .putBoolean(KEY_SPOTIFY_FLOATING_NAVIGATION, value.spotifyFloatingNavigationEnabled)
            .putBoolean(KEY_SPOTIFY_FAVORITE_BUTTON, value.spotifyFavoriteButtonEnabled)
            .putBoolean(KEY_SPOTIFY_SHUFFLE_BUTTON, value.spotifyShuffleButtonEnabled)
            .putBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, value.customMediaConstraintSetEnabled)
            .putString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, value.customMediaConstraintSetXml)
            .apply()
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

    fun onXposedServiceDied() {
        remoteService = null
    }

    private fun syncRemotePreferences(context: Context) {
        val service = remoteService ?: return
        try {
            val source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    fun toBundle(value: ModifierSettings) = Bundle().apply {
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
        putBoolean(
            KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS_ENABLED,
            value.headsUpBackgroundBlurRadiusEnabled,
        )
        putFloat(
            KEY_HEADS_UP_BACKGROUND_BLUR_RADIUS,
            value.headsUpBackgroundBlurRadius.coerceIn(0f, 200f),
        )
        putFloat(
            KEY_GLOBAL_BACKGROUND_BLUR_PERCENT,
            value.globalBackgroundBlurPercent.coerceIn(0f, 200f),
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
        putBoolean(KEY_FORCE_LOCKSCREEN_CLOCK_COLON, value.forceLockscreenClockColon)
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
        putBoolean(KEY_SPOTIFY_FLOATING_NAVIGATION, value.spotifyFloatingNavigationEnabled)
        putBoolean(KEY_SPOTIFY_FAVORITE_BUTTON, value.spotifyFavoriteButtonEnabled)
        putBoolean(KEY_SPOTIFY_SHUFFLE_BUTTON, value.spotifyShuffleButtonEnabled)
        putBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, value.customMediaConstraintSetEnabled)
        putString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, value.customMediaConstraintSetXml)
    }

    private fun normalizedAodClockWeight(weight: Float): Float =
        if (weight.isFinite()) weight.coerceIn(100f, 700f) else 400f

    private fun normalizedHeadsUpBottomMargin(margin: Float): Float =
        if (margin.isFinite()) margin.coerceIn(0f, 32f) else 13f
}
