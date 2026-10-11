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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HyperMusicCoverFontHeightSetting(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    label: String = "字体高度",
    defaultValue: Float = 28f,
    summary: String = "点击输入数值，支持负数和小数；默认 28",
    inputSummary: String = "直接输入原生字体高度数值，支持负数和小数。实际字形由系统字体决定。",
    positiveOnly: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    NavigationSettingItem(
        title = "$label：$value",
        summary = summary,
        enabled = enabled,
        onClick = { draft = value.toString(); editing = true },
    )
    if (editing && enabled) {
        val parsed = draft.trim().toFloatOrNull()?.takeIf { it.isFinite() && (!positiveOnly || it > 0f) }
        DeadlinerMiuixDialog(
            show = true,
            title = label,
            summary = inputSummary,
            onDismissRequest = { editing = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(), label = label, singleLine = true)
                if (parsed == null) Text(if (positiveOnly) "请输入大于 0 的有效数字。" else "请输入有效的有限数字。",
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("恢复 $defaultValue", { draft = defaultValue.toString() }, modifier = Modifier.weight(1f))
                    Button(onClick = { parsed?.let(onValueChange); editing = false },
                        enabled = parsed != null, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()) { Text("保存") }
                }
            }
        }
    }
}
