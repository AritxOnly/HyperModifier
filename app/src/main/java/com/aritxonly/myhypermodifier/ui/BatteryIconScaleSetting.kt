package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BatteryIconScaleSetting(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val safeValue = StatusBarBatteryIconSizePolicy.normalize(value)
    fun display(scale: Float) = String.format(
        Locale.ROOT,
        "%.3fx",
        StatusBarBatteryIconSizePolicy.normalize(scale),
    )
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    SettingsSliderItemWithLabel(
        label = "图标缩放",
        value = safeValue,
        valueRange = StatusBarBatteryIconSizePolicy.MIN..StatusBarBatteryIconSizePolicy.MAX,
        onValueChange = {
            onValueChange((it * 1000f).roundToInt().coerceIn(500, 2000) / 1000f)
        },
        steps = 1499,
        valueText = ::display,
        onLabelClick = {
            draft = String.format(Locale.ROOT, "%.3f", safeValue)
            editing = true
        },
    )
    if (editing) {
        val parsed = draft.trim().toFloatOrNull()?.takeIf {
            it.isFinite() &&
                it in StatusBarBatteryIconSizePolicy.MIN..StatusBarBatteryIconSizePolicy.MAX
        }?.let { StatusBarBatteryIconSizePolicy.normalize(it) }
        DeadlinerMiuixDialog(
            show = true,
            title = "电池图标大小",
            summary = "范围：0.500x～2.000x，精确到 0.001x",
            onDismissRequest = { editing = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                MiuixTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "缩放比例",
                    singleLine = true,
                )
                if (parsed == null) {
                    Text(
                        "请输入 0.500～2.000 之间的有效数值。",
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton("取消", { editing = false }, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            parsed?.let(onValueChange)
                            editing = false
                        },
                        enabled = parsed != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("保存") }
                }
            }
        }
    }
}
