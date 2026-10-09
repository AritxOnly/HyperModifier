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
            SettingsSwitchItem("全局 Glass 模糊", "影响通知中心、控制中心和悬浮通知",
                settings.globalGlassBlurEnabled, { update(settings.copy(globalGlassBlurEnabled = it)) },
                enabled = !settings.disableShadeGlassHooks)
            if (settings.disableShadeGlassHooks) Text(
                "卡片玻璃 Hook 已停用，可在通知/控制中心页面恢复。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SettingsSliderItemWithLabel(
                label = "材质模糊比例",
                value = settings.shadeCardBackgroundBlurPercent,
                valueRange = 0f..200f,
                onValueChange = { update(settings.copy(shadeCardBackgroundBlurPercent = it)) },
                steps = 199, enabled = settings.globalGlassBlurEnabled && !settings.disableShadeGlassHooks, valueText = { "${it.toInt()}%" },
            )
            Text("100% 保持原有半径，0% 关闭模糊；自定义半径也按此比例缩放。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SettingsSwitchItem("自定义 Glass 模糊半径", "",
                settings.shadeCardGlassBlurEnabled, { update(settings.copy(shadeCardGlassBlurEnabled = it)) },
                enabled = settings.globalGlassBlurEnabled && !settings.disableShadeGlassHooks)
            SettingsSliderItemWithLabel(
                label = "Glass 模糊半径", value = settings.shadeCardGlassBlurRadius,
                valueRange = 0f..ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(),
                onValueChange = { update(settings.copy(shadeCardGlassBlurRadius = it)) },
                steps = 99, enabled = settings.globalGlassBlurEnabled && settings.shadeCardGlassBlurEnabled && !settings.disableShadeGlassHooks,
                valueText = { "${it.toInt()} px" },
            )
            Text("系统大小半径将统一为此值。",
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
            "100% 为系统默认，重新打开相关界面后生效。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        SettingsSwitchItem(
            "自定义背景压暗",
            "影响通知中心、控制中心和融合设备中心",
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
            "0% 不压暗，100% 为纯黑背景。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun GlobalMaterialBlurEntry(settings: ModifierSettings, onOpen: () -> Unit) {
    val glass = if (settings.disableShadeGlassHooks) "Glass：Hook 已停用" else if (!settings.globalGlassBlurEnabled) "Glass：系统默认" else {
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
