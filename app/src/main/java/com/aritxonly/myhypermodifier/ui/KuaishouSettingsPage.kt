package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun KuaishouSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "快手") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "保留原生 tab 顺序、点击路由与选中状态",
            settings.kuaishouFloatingNavigationEnabled,
            { update(settings.copy(kuaishouFloatingNavigationEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "快手极速版") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "保留原生 tab 顺序、点击路由与选中状态",
            settings.kuaishouLiteFloatingNavigationEnabled,
            { update(settings.copy(kuaishouLiteFloatingNavigationEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem("快手适配版本", "14.8.10.50114")
        SettingItem("快手极速版适配版本", "14.9.10.11957")
        SettingItem("首次使用", "在 LSPosed 中勾选对应应用，保存后强行停止并重新打开。")
    }
}
