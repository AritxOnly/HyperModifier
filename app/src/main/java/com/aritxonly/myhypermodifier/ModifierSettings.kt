package com.aritxonly.myhypermodifier

import android.content.Context
import android.os.Bundle

data class ModifierSettings(
    val notificationsEnabled: Boolean = true,
    val notificationRadius: Float = 28f,
    val controlCenterEnabled: Boolean = true,
    val controlCenterRadius: Float = 28f,
    val advancedControlCenterCorners: Boolean = false,
    val controlCenterTileRadius: Float = 28f,
    val controlCenterCardRadius: Float = 28f,
    val controlCenterSliderRadius: Float = 28f,
    val controlCenterDetailSliderRadius: Float = 28f,
    val controlCenterMediaRadius: Float = 28f,
    val controlCenterExternalEntryRadius: Float = 28f,
    val miLinkMainCardsEnabled: Boolean = true,
    val miLinkMainCardRadius: Float = 20f,
    val mediaEnabled: Boolean = true,
    val expandedHeight: Float = 152f,
    val collapsedHeight: Float = 120f,
    val fullAodHeight: Float = 80f,
    val islandEnabled: Boolean = true,
    val islandHeight: Float = 160f,
    val islandProgressBar: Boolean = true,
    val hideAodActions: Boolean = true,
    val hideAodSeamless: Boolean = true,
    val customMediaConstraintSetEnabled: Boolean = false,
    val customMediaConstraintSetXml: String = "",
)

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
    private const val KEY_CUSTOM_MEDIA_CONSTRAINT_SET = "custom_media_constraint_set_enabled"
    private const val KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML = "custom_media_constraint_set_xml"

    fun load(context: Context): ModifierSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ModifierSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            notificationRadius = prefs.getFloat(KEY_NOTIFICATION_RADIUS, 28f),
            controlCenterEnabled = prefs.getBoolean(KEY_CONTROL_CENTER, true),
            controlCenterRadius = prefs.getFloat(KEY_CONTROL_CENTER_RADIUS, 28f),
            advancedControlCenterCorners = prefs.getBoolean(KEY_ADVANCED_CONTROL_CENTER_CORNERS, false),
            controlCenterTileRadius = prefs.getFloat(KEY_CONTROL_CENTER_TILE_RADIUS, 28f),
            controlCenterCardRadius = prefs.getFloat(KEY_CONTROL_CENTER_CARD_RADIUS, 28f),
            controlCenterSliderRadius = prefs.getFloat(KEY_CONTROL_CENTER_SLIDER_RADIUS, 28f),
            controlCenterDetailSliderRadius = prefs.getFloat(KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS, 28f),
            controlCenterMediaRadius = prefs.getFloat(KEY_CONTROL_CENTER_MEDIA_RADIUS, 28f),
            controlCenterExternalEntryRadius = prefs.getFloat(KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS, 28f),
            miLinkMainCardsEnabled = prefs.getBoolean(KEY_MILINK_MAIN_CARDS, true),
            miLinkMainCardRadius = prefs.getFloat(KEY_MILINK_MAIN_CARD_RADIUS, 20f),
            mediaEnabled = prefs.getBoolean(KEY_MEDIA, true),
            expandedHeight = prefs.getFloat(KEY_EXPANDED, 152f),
            collapsedHeight = prefs.getFloat(KEY_COLLAPSED, 120f),
            fullAodHeight = prefs.getFloat(KEY_FULL_AOD, 80f),
            islandEnabled = prefs.getBoolean(KEY_ISLAND, true),
            islandHeight = prefs.getFloat(KEY_ISLAND_HEIGHT, 160f),
            islandProgressBar = prefs.getBoolean(KEY_ISLAND_PROGRESS, true),
            hideAodActions = prefs.getBoolean(KEY_HIDE_AOD_ACTIONS, true),
            hideAodSeamless = prefs.getBoolean(KEY_HIDE_AOD_SEAMLESS, true),
            customMediaConstraintSetEnabled = prefs.getBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, false),
            customMediaConstraintSetXml = prefs.getString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, "").orEmpty(),
        )
    }

    fun save(context: Context, value: ModifierSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
            .putFloat(KEY_NOTIFICATION_RADIUS, value.notificationRadius)
            .putBoolean(KEY_CONTROL_CENTER, value.controlCenterEnabled)
            .putFloat(KEY_CONTROL_CENTER_RADIUS, value.controlCenterRadius)
            .putBoolean(KEY_ADVANCED_CONTROL_CENTER_CORNERS, value.advancedControlCenterCorners)
            .putFloat(KEY_CONTROL_CENTER_TILE_RADIUS, value.controlCenterTileRadius)
            .putFloat(KEY_CONTROL_CENTER_CARD_RADIUS, value.controlCenterCardRadius)
            .putFloat(KEY_CONTROL_CENTER_SLIDER_RADIUS, value.controlCenterSliderRadius)
            .putFloat(KEY_CONTROL_CENTER_DETAIL_SLIDER_RADIUS, value.controlCenterDetailSliderRadius)
            .putFloat(KEY_CONTROL_CENTER_MEDIA_RADIUS, value.controlCenterMediaRadius)
            .putFloat(KEY_CONTROL_CENTER_EXTERNAL_ENTRY_RADIUS, value.controlCenterExternalEntryRadius)
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
        putBoolean(KEY_CUSTOM_MEDIA_CONSTRAINT_SET, value.customMediaConstraintSetEnabled)
        putString(KEY_CUSTOM_MEDIA_CONSTRAINT_SET_XML, value.customMediaConstraintSetXml)
    }
}
