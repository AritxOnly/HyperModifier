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
            "折叠屏内屏 4:6 分栏",
            "仅调整 QQ 原生内屏分栏为左 40%、右 60%，保留原生折叠判断；适配 QQ 9.3.75",
            settings.qqFoldSplitRatioEnabled,
            { update(settings.copy(qqFoldSplitRatioEnabled = it)) },
        )
        SettingsSwitchItem(
            "折叠屏底部安全区沉浸",
            "使用 QQ 原生底部留白与背景延伸，保留 Tab 和输入区抬高；切换时重建 QQ 主页，适配 9.3.75",
            settings.qqFoldNavigationImmersionEnabled,
            { update(settings.copy(qqFoldNavigationImmersionEnabled = it)) },
        )
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
        SettingItem("QQ 悬浮底栏适配版本", "9.1.25")
        SettingItem("QQ 折叠分栏适配版本", "9.3.75（16580）")
    }
}
