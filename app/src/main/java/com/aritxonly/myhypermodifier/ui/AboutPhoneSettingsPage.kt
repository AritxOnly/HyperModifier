package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun AboutPhoneSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onOpenPhoneImage: () -> Unit,
    onScroll: (Float) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsScrollPage(padding, onScroll) {
    SettingsSection {
        NavigationSettingItem("设置主页入口", "", onClick = { context.openDetailSettings(SettingsDestination.HomeEntries) })
    }
    SettingsSection(topLabel = "关于手机布局") {
        SettingsSwitchItem(
            "设备名称与存储双卡片",
            "",
            settings.aboutPhoneCardsEnabled,
            { update(settings.copy(aboutPhoneCardsEnabled = it)) },
        )
    }
    SettingsSection {
        NavigationSettingItem("手机型号图片", "", onClick = onOpenPhoneImage)
    }
    SettingsSection {
        SettingItem("恢复原样", "", onClick = {
            update(settings.copy(aboutPhoneCardsEnabled = false,
                aboutPhoneImageSource = "auto", aboutPhoneCustomImage = "", aboutPhoneImageTransforms = "{}"))
        })
    }
}

}
