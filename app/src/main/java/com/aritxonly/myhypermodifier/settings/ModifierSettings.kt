package com.aritxonly.myhypermodifier

import android.content.Context
import android.os.Bundle

data class ModifierSettings(
    val notificationsEnabled: Boolean = false,
    val notificationRadius: Float = 28f,
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
    val showLockscreenFingerprintIconOnAod: Boolean = false,
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
    val marketNavigationBadgesEnabled: Boolean = true,
    val miHomeFloatingNavigationEnabled: Boolean = true,
    val miHomeMiuixIconsEnabled: Boolean = false,
    val miHomeMonochromeIconsEnabled: Boolean = true,
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

    /**
     * Stock HyperOS behavior.  Numeric values remain harmless defaults, while every hook that
     * changes SystemUI is disabled so SystemUI receives its original resources and layouts.
     */
    fun systemDefault(): ModifierSettings = ModifierSettings(
        notificationsEnabled = false,
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
        showLockscreenFingerprintIconOnAod = false,
        statusBarNetworkTypeEnabled = false,
        xiaomiHealthFloatingNavigationEnabled = false,
        xiaomiHealthMiuixIconsEnabled = false,
        xiaomiHealthMonochromeIconsEnabled = false,
        marketFloatingNavigationEnabled = false,
        marketMiuixIconsEnabled = false,
        marketMonochromeIconsEnabled = false,
        marketNavigationBadgesEnabled = false,
        miHomeFloatingNavigationEnabled = false,
        miHomeMiuixIconsEnabled = false,
        miHomeMonochromeIconsEnabled = false,
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
    const val PREFS = "modifier_settings"
    const val METHOD_GET = "get_settings"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_NOTIFICATION_RADIUS = "notification_radius"
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
    private const val KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD =
        "show_lockscreen_fingerprint_icon_on_aod"
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
        return ModifierSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, false),
            notificationRadius = prefs.getFloat(KEY_NOTIFICATION_RADIUS, 28f),
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
            showLockscreenFingerprintIconOnAod = prefs.getBoolean(
                KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD,
                false,
            ),
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
            marketNavigationBadgesEnabled = prefs.getBoolean(KEY_MARKET_NAVIGATION_BADGES, true),
            miHomeFloatingNavigationEnabled = prefs.getBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, true),
            miHomeMiuixIconsEnabled = prefs.getBoolean(KEY_MI_HOME_MIUIX_ICONS, false),
            miHomeMonochromeIconsEnabled = prefs.getBoolean(KEY_MI_HOME_MONOCHROME_ICONS, true),
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
            .putBoolean(
                KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD,
                value.showLockscreenFingerprintIconOnAod,
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
            .putBoolean(KEY_MARKET_NAVIGATION_BADGES, value.marketNavigationBadgesEnabled)
            .putBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, value.miHomeFloatingNavigationEnabled)
            .putBoolean(KEY_MI_HOME_MIUIX_ICONS, value.miHomeMiuixIconsEnabled)
            .putBoolean(KEY_MI_HOME_MONOCHROME_ICONS, value.miHomeMonochromeIconsEnabled)
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
    }

    fun toBundle(value: ModifierSettings) = Bundle().apply {
        putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
        putFloat(KEY_NOTIFICATION_RADIUS, value.notificationRadius)
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
        putBoolean(
            KEY_SHOW_LOCKSCREEN_FINGERPRINT_ICON_ON_AOD,
            value.showLockscreenFingerprintIconOnAod,
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
        putBoolean(KEY_MARKET_NAVIGATION_BADGES, value.marketNavigationBadgesEnabled)
        putBoolean(KEY_MI_HOME_FLOATING_NAVIGATION, value.miHomeFloatingNavigationEnabled)
        putBoolean(KEY_MI_HOME_MIUIX_ICONS, value.miHomeMiuixIconsEnabled)
        putBoolean(KEY_MI_HOME_MONOCHROME_ICONS, value.miHomeMonochromeIconsEnabled)
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

}
