package com.aritxonly.myhypermodifier

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/** Shared controls; callers own the independent notification-center and lockscreen values. */
@Composable
internal fun NotificationWidthSettings(
    enabled: Boolean,
    mode: Int,
    width: Float,
    margin: Float,
    onEnabled: (Boolean) -> Unit,
    onMode: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onMargin: (Float) -> Unit,
) {
    val selected = NotificationWidthPolicy.mode(mode)
    SettingsSwitchItem("自定义通知卡片宽度", "", enabled, onEnabled)
    WindowDropdownPreference(
        title = "调整方式",
        summary = if (selected == NotificationWidthPolicy.FIXED) "居中显示，不超过系统可用宽度"
            else "调整通知区域内左右每侧留白；边距过大时保留必要内容宽度",
        items = listOf("固定宽度", "调整边距"),
        selectedIndex = selected,
        onSelectedIndexChange = onMode,
        enabled = enabled,
    )
    if (selected == NotificationWidthPolicy.FIXED) {
        SettingsSliderItemWithLabel("卡片宽度", width, NotificationWidthPolicy.MIN_DP..NotificationWidthPolicy.MAX_DP,
            onWidth, steps = 59, enabled = enabled)
    } else {
        SettingsSliderItemWithLabel("左右边距（每侧）", margin, 0f..NotificationWidthPolicy.MAX_MARGIN_DP,
            onMargin, steps = 159, enabled = enabled)
    }
}
