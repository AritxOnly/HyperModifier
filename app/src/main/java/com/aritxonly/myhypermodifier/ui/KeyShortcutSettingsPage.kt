package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun KeyShortcutSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    val actions = listOf(KeyShortcutActions.STOCK, KeyShortcutActions.WALLET,
        KeyShortcutActions.MEMORY, KeyShortcutActions.SCREEN, KeyShortcutActions.NONE)
    val labels = listOf("系统默认", "钱包", "小爱记忆", "小爱识屏", "无")
    SettingsSection {
        WindowDropdownPreference(
            title = "音量+ 与电源键",
            summary = "同时按下",
            items = labels,
            selectedIndex = actions.indexOf(KeyShortcutActions.normalize(settings.volumeUpPowerAction)),
            onSelectedIndexChange = { update(settings.copy(volumeUpPowerAction = actions[it])) },
        )
        WindowDropdownPreference(
            title = "双击电源键",
            summary = "",
            items = labels,
            selectedIndex = actions.indexOf(KeyShortcutActions.normalize(settings.doublePowerAction)),
            onSelectedIndexChange = { update(settings.copy(doublePowerAction = actions[it])) },
        )
        Text(
            "需系统框架与超级小爱作用域，首次启用需重启手机。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
