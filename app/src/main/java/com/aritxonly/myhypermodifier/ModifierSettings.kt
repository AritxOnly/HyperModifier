package com.aritxonly.myhypermodifier

import android.content.Context
import android.os.Bundle

data class ScopeRuntimeStatus(
    val systemUiActive: Boolean,
    val pluginActive: Boolean,
    val miLinkActive: Boolean,
    val xiaomiHealthActive: Boolean,
    val marketActive: Boolean,
) {
    fun isActive(scope: String): Boolean = when (scope) {
        SCOPE_SYSTEM_UI -> systemUiActive
        SCOPE_PLUGIN -> pluginActive
        SCOPE_MILINK -> miLinkActive
        SCOPE_XIAOMI_HEALTH -> xiaomiHealthActive
        SCOPE_MARKET -> marketActive
        else -> false
    }

    fun toBundle(): Bundle = Bundle().apply {
        putBoolean(SCOPE_SYSTEM_UI, systemUiActive)
        putBoolean(SCOPE_PLUGIN, pluginActive)
        putBoolean(SCOPE_MILINK, miLinkActive)
        putBoolean(SCOPE_XIAOMI_HEALTH, xiaomiHealthActive)
        putBoolean(SCOPE_MARKET, marketActive)
    }

    companion object {
        const val SCOPE_SYSTEM_UI = "com.android.systemui"
        const val SCOPE_PLUGIN = "miui.systemui.plugin"
        const val SCOPE_MILINK = "com.milink.service"
        const val SCOPE_XIAOMI_HEALTH = "com.mi.health"
        const val SCOPE_MARKET = "com.xiaomi.market"

        fun fromBundle(bundle: Bundle) = ScopeRuntimeStatus(
            systemUiActive = bundle.getBoolean(SCOPE_SYSTEM_UI),
            pluginActive = bundle.getBoolean(SCOPE_PLUGIN),
            miLinkActive = bundle.getBoolean(SCOPE_MILINK),
            xiaomiHealthActive = bundle.getBoolean(SCOPE_XIAOMI_HEALTH),
            marketActive = bundle.getBoolean(SCOPE_MARKET),
        )

        fun inactive() = ScopeRuntimeStatus(false, false, false, false, false)
    }
}

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
    val xiaomiHealthFloatingNavigationEnabled: Boolean = true,
    val xiaomiHealthMiuixIconsEnabled: Boolean = false,
    val xiaomiHealthMonochromeIconsEnabled: Boolean = true,
    val marketFloatingNavigationEnabled: Boolean = true,
    val marketMiuixIconsEnabled: Boolean = false,
    val marketMonochromeIconsEnabled: Boolean = true,
    val marketNavigationBadgesEnabled: Boolean = true,
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
        customMediaConstraintSetEnabled = false,
        customMediaConstraintSetXml = "",
    )
}

object ModifierSettingsStore {
    const val PREFS = "modifier_settings"
    const val METHOD_GET = "get_settings"
    const val METHOD_GET_SCOPE_STATUS = "get_scope_status"
    const val METHOD_REPORT_SCOPE_HEARTBEAT = "report_scope_heartbeat"
    const val EXTRA_SCOPE = "scope"
    private const val SCOPE_HEARTBEAT_PREFIX = "scope_heartbeat_"
    private const val SCOPE_ACTIVE_WINDOW_MILLIS = 45_000L
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
    private const val KEY_XIAOMI_HEALTH_FLOATING_NAVIGATION =
        "xiaomi_health_floating_navigation_enabled"
    private const val KEY_XIAOMI_HEALTH_MIUIX_ICONS = "xiaomi_health_miuix_icons_enabled"
    private const val KEY_XIAOMI_HEALTH_MONOCHROME_ICONS =
        "xiaomi_health_monochrome_icons_enabled"
    private const val KEY_MARKET_FLOATING_NAVIGATION = "market_floating_navigation_enabled"
    private const val KEY_MARKET_MIUIX_ICONS = "market_miuix_icons_enabled"
    private const val KEY_MARKET_MONOCHROME_ICONS = "market_monochrome_icons_enabled"
    private const val KEY_MARKET_NAVIGATION_BADGES = "market_navigation_badges_enabled"
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
        putBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, value.customMediaConstraintSetEnabled)
        putString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, value.customMediaConstraintSetXml)
    }

    /** A scope is active only while its injected process continues reporting a recent heartbeat. */
    fun scopeRuntimeStatus(context: Context): ScopeRuntimeStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activeAfter = System.currentTimeMillis() - SCOPE_ACTIVE_WINDOW_MILLIS
        fun active(scope: String) = prefs.getLong(SCOPE_HEARTBEAT_PREFIX + scope, 0L) >= activeAfter
        return ScopeRuntimeStatus(
            systemUiActive = active(ScopeRuntimeStatus.SCOPE_SYSTEM_UI),
            pluginActive = active(ScopeRuntimeStatus.SCOPE_PLUGIN),
            miLinkActive = active(ScopeRuntimeStatus.SCOPE_MILINK),
            xiaomiHealthActive = active(ScopeRuntimeStatus.SCOPE_XIAOMI_HEALTH),
            marketActive = active(ScopeRuntimeStatus.SCOPE_MARKET),
        )
    }

    fun reportScopeHeartbeat(context: Context, scope: String?) {
        if (scope == null || scope !in trackedScopes) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(SCOPE_HEARTBEAT_PREFIX + scope, System.currentTimeMillis())
            .apply()
    }

    private val trackedScopes = setOf(
        ScopeRuntimeStatus.SCOPE_SYSTEM_UI,
        ScopeRuntimeStatus.SCOPE_PLUGIN,
        ScopeRuntimeStatus.SCOPE_MILINK,
        ScopeRuntimeStatus.SCOPE_XIAOMI_HEALTH,
        ScopeRuntimeStatus.SCOPE_MARKET,
    )
}
