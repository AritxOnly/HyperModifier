package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun BilibiliSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    val enabled = settings.bilibiliFloatingNavigationEnabled
    val visibleCount = listOf(
        settings.bilibiliHomeTabVisible, settings.bilibiliDynamicTabVisible,
        settings.bilibiliMallTabVisible, settings.bilibiliMineTabVisible, settings.bilibiliFollowTabVisible,
    ).count { it }
    SettingsSection(topLabel = "哔哩哔哩") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏", "系统风格图标与独立发布按钮",
            enabled, { update(settings.copy(bilibiliFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "显示底栏角标", "",
            settings.bilibiliNavigationBadgesEnabled,
            { update(settings.copy(bilibiliNavigationBadgesEnabled = it)) },
            enabled = enabled,
        )
    }
    SettingsSection(topLabel = "底栏功能区") {
        SettingItem("入口显隐", "至少保留一个导航入口；修改后重启哔哩哔哩生效。")
        SettingsSwitchItem(
            "首页", "", settings.bilibiliHomeTabVisible,
            { update(settings.copy(bilibiliHomeTabVisible = it)) },
            enabled = enabled && (!settings.bilibiliHomeTabVisible || visibleCount > 1),
        )
        SettingsSwitchItem(
            "动态", "", settings.bilibiliDynamicTabVisible,
            { update(settings.copy(bilibiliDynamicTabVisible = it)) },
            enabled = enabled && (!settings.bilibiliDynamicTabVisible || visibleCount > 1),
        )
        SettingsSwitchItem(
            "关注", "", settings.bilibiliFollowTabVisible,
            { update(settings.copy(bilibiliFollowTabVisible = it)) },
            enabled = enabled && (!settings.bilibiliFollowTabVisible || visibleCount > 1),
        )
        SettingsSwitchItem(
            "会员购", "", settings.bilibiliMallTabVisible,
            { update(settings.copy(bilibiliMallTabVisible = it)) },
            enabled = enabled && (!settings.bilibiliMallTabVisible || visibleCount > 1),
        )
        SettingsSwitchItem(
            "我的", "", settings.bilibiliMineTabVisible,
            { update(settings.copy(bilibiliMineTabVisible = it)) },
            enabled = enabled && (!settings.bilibiliMineTabVisible || visibleCount > 1),
        )
        SettingsSwitchItem(
            "发布按钮", "独立的 + 按钮，打开官方发布面板",
            settings.bilibiliPublishButtonVisible,
            { update(settings.copy(bilibiliPublishButtonVisible = it)) },
            enabled = enabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem("当前适配版本", "9.13.0 · 官方版")
    }
}
