package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun QQMusicSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "QQ音乐") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "保留原生 tab 顺序、点击路由与选中状态",
            settings.qqMusicFloatingNavigationEnabled,
            { update(settings.copy(qqMusicFloatingNavigationEnabled = it)) },
        )
        SettingItem("参考版本", "20.9.5.8")
        SettingItem("作用域", "在 LSPosed 中勾选 QQ音乐，保存后强行停止并重新打开。")
    }
}
