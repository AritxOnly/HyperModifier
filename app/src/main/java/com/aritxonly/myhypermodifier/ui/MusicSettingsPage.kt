package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun MusicSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "小米音乐") {
        SettingsSwitchItem(
            label = "柔光玻璃悬浮底栏",
            supportingText = "仅适配手机版五入口布局，点击和页面切换保持原生行为",
            checked = settings.miMusicFloatingNavigationEnabled,
            onCheckedChange = {
                update(settings.copy(miMusicFloatingNavigationEnabled = it))
            },
        )
    }
    SettingsSection(topLabel = "适配信息") {
        SettingItem("测试版本", "4.48.0.1")
        SettingItem("入口", "发现 · 听书 · 免费听 · 赚钱/福利 · 我的")
        SettingItem(
            "布局限制",
            "仅在手机版五槽位底栏存在时启用，平板版三栏布局不受影响",
        )
        SettingItem(
            "图标来源",
            "读取小米音乐当前原生图标并应用选中、颜色和角标状态",
        )
    }
}
