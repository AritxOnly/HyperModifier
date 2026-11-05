package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
internal fun FoldSettingsPage(
    padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    var dpi by rememberSaveable { mutableIntStateOf(420) }
    // -1 = no request, 0 = clear override. Survives the configuration change caused by wm itself.
    var pending by rememberSaveable { mutableIntStateOf(-1) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("需要 Root。若待机屏不可写，请保持此页打开并切换内外屏。") }
    val config = LocalConfiguration.current
    LaunchedEffect(pending, retry, config.screenWidthDp, config.screenHeightDp) {
        if (pending < 0) return@LaunchedEffect
        message = "正在核对内外屏…"
        val result = withContext(Dispatchers.IO) { FoldDensity.apply(pending) }
        message = result.first
        if (!result.second) pending = -1
    }
    SettingsSection(topLabel = "修改 wm density") {
        SettingsSliderItemWithLabel("目标密度", dpi.toFloat(), 320f..520f,
            { dpi = it.roundToInt() }, steps = 39, valueText = { "${it.roundToInt()} dpi" })
        SettingItem("同步内外屏", message, onClick = { pending = dpi; retry++ })
        SettingItem("恢复双屏默认密度", "清除两块内置屏的密度覆盖值", onClick = { pending = 0; retry++ })
    }

}

internal object FoldDensity {
    // Keep root work serialized across activity recreation and rapid requests.
    @Synchronized fun apply(dpi: Int): Pair<String, Boolean> {
        require(dpi == 0 || dpi in 320..520)
        val value = if (dpi == 0) "reset" else dpi.toString()
        // IDs come only from INTERNAL DisplayDeviceInfo records. Logical display 0 changes panels.
        val script = """
            ids=${'$'}(dumpsys display | sed -n '/DisplayDeviceInfo{.*type INTERNAL/s/.*uniqueId="\(local:[0-9][0-9]*\)".*/\1/p' | sort -u)
            set -- ${'$'}ids
            [ "${'$'}#" = 2 ] || { echo unsupported; exit 0; }
            for id in "${'$'}@"; do
              before=${'$'}(wm density -u "${'$'}id" 2>/dev/null) || { echo pending; continue; }
              printf '%s\n' "${'$'}before" | grep -Eq '^Physical density: [1-9][0-9]*${'$'}' || { echo pending; continue; }
              actual=${'$'}(printf '%s\n' "${'$'}before" | sed -n 's/^Override density: //p')
              if [ "${'$'}actual" = "$value" ]; then echo ok; continue; fi
              wm density $value -u "${'$'}id" >/dev/null 2>&1 || { echo pending; continue; }
              after=${'$'}(wm density -u "${'$'}id" 2>/dev/null) || { echo pending; continue; }
              printf '%s\n' "${'$'}after" | grep -Eq '^Physical density: [1-9][0-9]*${'$'}' || { echo pending; continue; }
              actual=${'$'}(printf '%s\n' "${'$'}after" | sed -n 's/^Override density: //p')
              if [ "$value" = reset ] && [ -z "${'$'}actual" ]; then echo ok
              elif [ "${'$'}actual" = "$value" ] || { [ -z "${'$'}actual" ] && printf '%s\n' "${'$'}after" | grep -Fxq "Physical density: $value"; }; then echo ok
              else echo pending; fi
            done
        """.trimIndent()
        return try {
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            try {
                if (!process.waitFor(25, TimeUnit.SECONDS)) return "Root 操作超时，请重试。" to false
                val lines = process.inputStream.bufferedReader().use { it.readLines() }
                when {
                    process.exitValue() != 0 -> "未取得 Root 或命令失败。" to false
                    "unsupported" in lines -> "未识别到两块内置屏幕。" to false
                    lines.count { it == "ok" } == 2 ->
                        (if (dpi == 0) "已核实两块屏均已恢复默认。" else "已核实内外屏均为 $dpi dpi。") to false
                    "pending" in lines -> "已核实 ${lines.count { it == "ok" }}/2 块屏。切换内外屏后会继续，也可点此重试。" to true
                    else -> "无法核实显示配置，请重试。" to false
                }
            } finally { process.destroy() }
        } catch (_: Exception) { "无法执行 Root 命令，请检查授权。" to false }
    }
}
