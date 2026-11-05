package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun PiliPlusSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "PiliPlus") {
        SettingsSwitchItem(
            "液态柔光悬浮底栏",
            "读取 Flutter 首页语义树，保留原生 Tab 切换与顺序",
            settings.piliPlusFloatingNavigationEnabled,
            { update(settings.copy(piliPlusFloatingNavigationEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "显示范围") {
        SettingItem("首页导航", "仅显示首页、动态和我的；详情、播放器与键盘弹出时隐藏。")
        SettingItem("液态背景", "从当前 Flutter 窗口实时采样底栏后方内容，GPU 不可用时保留柔光材质兜底。")
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem("当前适配版本", "PiliPlus 2.1.6 · Flutter 3.47.7")
        SettingItem("首次使用", "在 LSPosed 中勾选 PiliPlus，保存后强行停止并重新打开应用。")
    }
}
