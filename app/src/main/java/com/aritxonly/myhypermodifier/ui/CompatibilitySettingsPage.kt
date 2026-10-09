package com.aritxonly.myhypermodifier

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CompatibilitySettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    val context = LocalContext.current
    val diagnostics = rememberHookDiagnostics()
    var autoClean by remember(context) { mutableStateOf(HookDiagnosticStore.autoClean(context)) }
    val features = HookDiagnosticPolicy.latestFeatures(diagnostics)
    SettingsSection(topLabel = "模块恢复") {
        SettingsSwitchItem(
            "启用模块 Hook", "关闭后重启设备生效，功能设置保留",
            settings.moduleHooksEnabled,
            { update(settings.copy(moduleHooksEnabled = it)) },
        )
        SettingsSwitchItem(
            "SystemUI 兼容模式", "跳过系统界面 Hook，重启 SystemUI 生效",
            settings.systemUiCompatibilityMode,
            { update(settings.copy(systemUiCompatibilityMode = it)) },
            enabled = settings.moduleHooksEnabled,
        )
    }
    SettingsSection(topLabel = "自动恢复") {
        Text(
            "SystemUI 在 20 秒内连续启动 3 次时自动进入兼容模式，手动重启也会计入。保护持续生效，直到手动重试。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        NavigationSettingItem(
            "重试 SystemUI Hook", "解除兼容模式，重启 SystemUI 后重新检测",
            enabled = settings.moduleHooksEnabled,
            onClick = {
                update(settings.copy(
                    systemUiCompatibilityMode = false,
                    systemUiRetryGeneration = settings.systemUiRetryGeneration + 1,
                ))
            },
        )
    }
    SettingsSection(topLabel = "功能可用性") {
        if (features.isEmpty()) {
            SettingItem("尚未检测", "重启目标应用后查看功能安装结果")
        } else {
            Text(
                "显示各功能最近一次安装结果。已安装表示 Hook 入口可用，实际效果需在对应界面验证。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            features.forEach { item ->
                SettingItem(
                    "${item.feature} · ${if (item.warning) "不可用" else "已安装"}",
                    "${formatDiagnosticTime(item.time)} · ${item.packageName}" +
                        if (item.warning) "\n${item.detail}" else "",
                )
            }
        }
    }
    SettingsSection(topLabel = "注入日志") {
        SettingsSwitchItem(
            "自动清理日志", "保留最近 7 天，最多 120 条记录",
            autoClean, {
                autoClean = it
                HookDiagnosticStore.setAutoClean(context, it)
            },
        )
        if (diagnostics.isEmpty()) {
            SettingItem("暂无日志", "")
        } else {
            NavigationSettingItem("复制日志", "", onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("MyHyperModifier Hook 日志",
                    diagnostics.joinToString("\n") { item ->
                        "${formatDiagnosticTime(item.time)} ${if (item.warning) "WARN" else "INFO"} " +
                            "${item.packageName} ${item.feature}: ${item.detail}"
                    }))
            })
            NavigationSettingItem("清空日志", "", onClick = { HookDiagnosticStore.clear(context) })
            diagnostics.forEach { item ->
                SettingItem(
                    "${if (item.warning) "安装失败" else if (item.feature == "模块注入") "已进入进程" else "已安装"} · ${item.feature}",
                    "${formatDiagnosticTime(item.time)} · ${item.packageName}\n${item.detail}",
                )
            }
        }
    }
}

@Composable
internal fun rememberHookDiagnostics(): List<HookDiagnostic> {
    val context = LocalContext.current
    var diagnostics by remember(context) { mutableStateOf(HookDiagnosticStore.events(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(HookDiagnosticStore.PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            diagnostics = HookDiagnosticStore.events(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return diagnostics
}

private fun formatDiagnosticTime(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
