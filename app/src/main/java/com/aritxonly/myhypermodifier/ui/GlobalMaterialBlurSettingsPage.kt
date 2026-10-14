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
    SettingsSection(topLabel = "Glass 模糊") {
        SettingsSwitchItem(
            "自定义 Glass 模糊半径",
            "",
            settings.globalGlassBlurEnabled,
            { update(settings.copy(globalGlassBlurEnabled = it)) },
            enabled = !settings.disableShadeGlassHooks,
        )
        if (settings.disableShadeGlassHooks) Text(
            "玻璃材质修改已停用，可在通知/控制中心开启。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        SettingsSliderItemWithLabel(
            label = "Glass 模糊半径",
            value = settings.shadeCardGlassBlurRadius,
            valueRange = 0f..ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat(),
            onValueChange = { update(settings.copy(shadeCardGlassBlurRadius = it)) },
            steps = 99,
            enabled = settings.globalGlassBlurEnabled && !settings.disableShadeGlassHooks,
            valueText = { "${it.toInt()} px" },
        )
        Text(
            "作用域内生效；0 px 关闭模糊。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
    SettingsSection(topLabel = "背景材质") {
        SettingsSliderItemWithLabel(
            label = "全局背景模糊",
            value = settings.globalBackgroundBlurPercent,
            valueRange = 0f..200f,
            onValueChange = { update(settings.copy(globalBackgroundBlurPercent = it)) },
            steps = 199,
            valueText = { "${it.toInt()}%" },
        )
        Text(
            "100% 为系统默认，重新打开界面生效。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        SettingsSwitchItem(
            "关闭背景缩放",
            "通知中心与控制中心",
            settings.shadeBackgroundScaleDisabled,
            { update(settings.copy(shadeBackgroundScaleDisabled = it)) },
        )
        SettingsSwitchItem(
            "自定义背景压暗",
            "通知中心、控制中心与设备互联",
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
            "0% 不压暗，100% 纯黑。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun GlobalMaterialBlurEntry(settings: ModifierSettings, onOpen: () -> Unit) {
    val glass = when {
        settings.disableShadeGlassHooks -> "Glass 已停用"
        settings.globalGlassBlurEnabled -> "Glass：${settings.shadeCardGlassBlurRadius.toInt()} px"
        else -> "Glass：系统默认"
    }
    SettingsSection {
        NavigationSettingItem(
            title = "全局材质模糊",
            summary = "$glass；背景模糊：${settings.globalBackgroundBlurPercent.toInt()}%",
            onClick = onOpen,
        )
    }
}
