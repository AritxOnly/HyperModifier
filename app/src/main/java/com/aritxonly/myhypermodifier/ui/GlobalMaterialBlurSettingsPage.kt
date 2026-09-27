package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One settings owner for shared Glass buffers and whole-panel background effects. */
@Composable
internal fun GlobalMaterialBlurSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
        SettingsSection(topLabel = "共享 Glass 材质模糊") {
            SettingsSwitchItem("启用全局 Glass 模糊调整", "统一通知中心、控制中心和悬浮通知共享材质缓冲，与各页面材质参数开关无关",
                settings.globalGlassBlurEnabled, { update(settings.copy(globalGlassBlurEnabled = it)) })
            SettingsSliderItemWithLabel(
                label = "材质模糊比例",
                value = settings.shadeCardBackgroundBlurPercent,
                valueRange = 0f..200f,
                onValueChange = { update(settings.copy(shadeCardBackgroundBlurPercent = it)) },
                steps = 199, enabled = settings.globalGlassBlurEnabled, valueText = { "${it.toInt()}%" },
            )
            Text("缩放通知／控制中心原生 Glass 大小模糊半径；开启自定义半径时，以自定义值为基准。100% 不缩放，0% 将 Glass 半径设为零。这是共享模糊缓冲，不是每张卡片独立的背景模糊。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SettingsSwitchItem("自定义 Glass 模糊半径", "关闭时使用系统半径；开启后先设置半径，再应用上方比例",
                settings.shadeCardGlassBlurEnabled, { update(settings.copy(shadeCardGlassBlurEnabled = it)) },
                enabled = settings.globalGlassBlurEnabled)
            SettingsSliderItemWithLabel(
                label = "Glass 模糊半径", value = settings.shadeCardGlassBlurRadius,
                valueRange = 0f..ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(),
                onValueChange = { update(settings.copy(shadeCardGlassBlurRadius = it)) },
                steps = 99, enabled = settings.globalGlassBlurEnabled && settings.shadeCardGlassBlurEnabled,
                valueText = { "${it.toInt()} px" },
            )
            Text("范围 0–100 px，滑动每档 1 px，再应用上方模糊比例。参考通知默认小半径约 14.55dp、大半径约 181.82dp（440dpi 时约 40／500px）。开启后将大小半径统一为指定值；关闭半径开关后仍保留系统原生两档半径。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }
    SettingsSection(topLabel = "全局背景材质") {
        SettingsSliderItemWithLabel(
            label = "全局背景模糊",
            value = settings.globalBackgroundBlurPercent,
            valueRange = 0f..200f,
            onValueChange = { update(settings.copy(globalBackgroundBlurPercent = it)) },
            steps = 199,
            valueText = { "${it.toInt()}%" },
        )
        Text(
            "影响通知中心、控制中心和融合设备中心的背景。100% 为系统原有模糊强度；重新打开相关界面后生效。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        SettingsSwitchItem(
            "自定义背景压暗",
            "统一通知中心、控制中心和融合设备中心；关闭恢复原生混色",
            settings.globalBackgroundDimEnabled,
            { update(settings.copy(globalBackgroundDimEnabled = it)) },
        )
        SettingsSliderItemWithLabel(
            label = "背景压暗程度",
            value = settings.globalBackgroundDimPercent,
            valueRange = 0f..100f,
            onValueChange = { update(settings.copy(globalBackgroundDimPercent = it)) },
            steps = 99,
            enabled = settings.globalBackgroundDimEnabled,
            valueText = { "${it.toInt()}%" },
        )
        Text(
            "替换背景混色，不叠加模糊。0% 不压暗，100% 为纯黑背景；重新打开相关界面后生效。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun GlobalMaterialBlurEntry(settings: ModifierSettings, onOpen: () -> Unit) {
    val glass = if (!settings.globalGlassBlurEnabled) "Glass：系统默认" else {
        val radius = if (settings.shadeCardGlassBlurEnabled) "${settings.shadeCardGlassBlurRadius.toInt()} px" else "系统半径"
        "Glass：$radius × ${settings.shadeCardBackgroundBlurPercent.toInt()}%"
    }
    SettingsSection(topLabel = "共享模糊设置") {
        NavigationSettingItem(
            title = "全局材质模糊",
            summary = "$glass；背景模糊：${settings.globalBackgroundBlurPercent.toInt()}%",
            onClick = onOpen,
        )
    }
}
