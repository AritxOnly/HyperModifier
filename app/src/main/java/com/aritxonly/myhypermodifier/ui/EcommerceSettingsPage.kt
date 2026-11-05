package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun EcommerceSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "购物与生活服务") {
        SettingsSwitchItem(
            "拼多多",
            "保留原生 Tab、图标、角标与页面路由",
            settings.pinduoduoFloatingNavigationEnabled,
            { update(settings.copy(pinduoduoFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "淘宝",
            "使用 TBFragmentTabHost 的官方点击链路",
            settings.taobaoFloatingNavigationEnabled,
            { update(settings.copy(taobaoFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "京东",
            "使用 NavigationGroup 的官方点击链路",
            settings.jingdongFloatingNavigationEnabled,
            { update(settings.copy(jingdongFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "闲鱼",
            "保留首页、附近、发布、消息和我的入口",
            settings.xianyuFloatingNavigationEnabled,
            { update(settings.copy(xianyuFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "抖音商城",
            "按底部原生标签行适配，兼容商城页面路由",
            settings.douyinMallFloatingNavigationEnabled,
            { update(settings.copy(douyinMallFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "菜鸟",
            "使用 NavigationView 的官方点击链路",
            settings.cainiaoFloatingNavigationEnabled,
            { update(settings.copy(cainiaoFloatingNavigationEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "使用说明") {
        SettingItem("首次启用", "在 LSPosed 中勾选对应应用，保存后强行停止并重新打开应用。")
        SettingItem("适配基线", "拼多多 8.23.0、淘宝 10.65.10、京东 15.7.50、闲鱼 7.28.10、抖音商城 40.1.0、菜鸟 8.11.508")
        SettingItem("版本变化", "若应用更新后隐藏或改名原生底栏，模块会尝试按底部标签行重新定位。")
    }
}
