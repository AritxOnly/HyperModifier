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
import java.math.BigDecimal
import kotlin.math.round
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NetworkActivityOffsetSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    limit: Float = 1.5f,
    step: Float = 0.05f,
) {
    val safeValue = value.takeIf { it.isFinite() }?.coerceIn(-limit, limit) ?: 0f
    fun display(number: Float) = BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    SettingsSliderItemWithLabel(
        label = label,
        value = safeValue,
        valueRange = -limit..limit,
        onValueChange = {
            val tick = round(it / step).toInt()
            onValueChange(BigDecimal(step.toString()).multiply(BigDecimal(tick)).toFloat())
        },
        steps = (2f * limit / step).roundToInt() - 1,
        valueText = { "${display(it)} dp" },
        enabled = enabled,
        onLabelClick = {
            draft = display(safeValue)
            editing = true
        },
    )
    if (editing && enabled) {
        val parsed = draft.trim().toFloatOrNull()?.takeIf { it.isFinite() && it in -limit..limit }
        DeadlinerMiuixDialog(
            show = true,
            title = label,
            summary = "输入 −${display(limit)}～${display(limit)} dp，可输入比 ${display(step)} dp 更精细的小数。",
            onDismissRequest = { editing = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                MiuixTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "偏移（dp）",
                    singleLine = true,
                )
                if (parsed == null) {
                    Text(
                        "请输入 −${display(limit)}～${display(limit)} 之间的有效数字。",
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
