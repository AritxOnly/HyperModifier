package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun SocialNavigationSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
    onlyPackage: String? = null,
) = SettingsScrollPage(padding, onScroll) {
    if (onlyPackage == null || onlyPackage == ModuleScopePackage.WECHAT) SettingsSection(topLabel = "微信") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "保留微信原生 tab 文案、点击路由与未读角标",
            settings.wechatFloatingNavigationEnabled,
            { update(settings.copy(wechatFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "显示底栏角标",
            "",
            settings.wechatNavigationBadgesEnabled,
            { update(settings.copy(wechatNavigationBadgesEnabled = it)) },
            enabled = settings.wechatFloatingNavigationEnabled,
        )
    }
    if (onlyPackage == null || onlyPackage == ModuleScopePackage.QQ) SettingsSection(topLabel = "QQ") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "仅替换 QQTabWidget，保留页面内容与官方选中状态",
            settings.qqFloatingNavigationEnabled,
            { update(settings.copy(qqFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "显示底栏角标",
            "",
            settings.qqNavigationBadgesEnabled,
            { update(settings.copy(qqNavigationBadgesEnabled = it)) },
            enabled = settings.qqFloatingNavigationEnabled,
        )
    }
    SettingsSection(topLabel = "切换动画") {
        SettingItem(
            "快速切换可打断",
            "所有柔光玻璃底栏在指示器尚未落定时继续接收下一次点击，并继承当前速度",
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem("微信适配版本", "8.0.78")
        SettingItem("QQ 适配版本", "9.1.25")
    }
}
