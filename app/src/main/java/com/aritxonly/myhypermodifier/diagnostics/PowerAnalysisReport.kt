package com.aritxonly.myhypermodifier

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object PowerAnalysisReport {
    fun text(context: Context, state: PowerAnalysisState): String {
        val folder = PowerAnalysisStore.directory(context, state.start)
        val samples = File(folder, "samples.jsonl").takeIf { it.exists() }?.readLines()
            ?.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.orEmpty()
        fun sample(json: JSONObject) = PowerSample(json.getLong("time"), json.getLong("elapsedRealtime"),
            json.optInt("plugged", -1), if (json.isNull("chargeUah")) null else json.getInt("chargeUah"),
            json.optBoolean("interactive"))
        val pairs = samples.map(::sample).zipWithNext()
        val valid = pairs.mapNotNull { (a, b) ->
            PowerAnalysisPolicy.dischargedMah(a, b)?.let { Triple(a, b, it) }
        }
        val modules = File(folder, "installed-lsp-modules.json").takeIf { it.exists() }
            ?.let { runCatching { JSONArray(it.readText()) }.getOrNull() } ?: JSONArray()
        fun number(value: Double) = String.format(Locale.getDefault(), "%.2f", value)
        fun time(value: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
        return buildString {
            appendLine("HyperModifier 性能与功耗报告")
            appendLine("开始：${time(state.start)}；计划结束：${time(state.end)}")
            appendLine("状态：${if (state.active) "进行中" else "已结束"}；${state.status}")
            appendLine("采样 ${samples.size} 次；熄屏测试只采集首尾，不进行周期采样。")
            samples.lastOrNull()?.takeIf { it.optString("phase") == "after" }?.let {
                appendLine("结束延迟：${number(it.optLong("completionDelayMs") / 60000.0)} 分钟；提前结束：${it.optBoolean("manualEnd")}")
            }
            if (samples.isNotEmpty()) {
                appendLine("实测覆盖：${time(samples.first().getLong("time"))} 至 ${time(samples.last().getLong("time"))}")
                appendLine("首末电量：${samples.first().optInt("level")}/${samples.first().optInt("scale")} → " +
                    "${samples.last().optInt("level")}/${samples.last().optInt("scale")}")
                appendLine("充电样本 ${samples.count { it.optInt("plugged", -1) > 0 }} 次；" +
                    "采样时亮屏 ${samples.count { it.optBoolean("interactive") }} 次。")
            }
            appendLine()
            if (valid.isEmpty()) {
                appendLine("没有可连续比较的放电电量计数据；无法计算可靠的 mAh 消耗。")
            } else {
                val minutes = valid.sumOf { (it.second.time - it.first.time) / 60000.0 }
                val mah = valid.sumOf { it.third }
                appendLine("可比较放电区间：${number(minutes)} 分钟；整机电量计减少 ${number(mah)} mAh。")
                appendLine("这些区间平均电流：${number(mah * 60 / minutes)} mA。")
                appendLine("已排除充电端点、跨重启、超过 45 分钟的间隔及电量计回升；两次采样间短暂充电仍可能未被捕获。")
            }
            appendLine("首尾电量计消耗不是瞬时电流积分；亮屏准备和结束操作也可能计入区间。")
            if (samples.size >= 2) {
                val first = samples.first()
                val last = samples.last()
                val elapsed = last.optLong("elapsedRealtime") - first.optLong("elapsedRealtime")
                if (first.has("uptimeMillis") && last.has("uptimeMillis") &&
                    kotlin.math.abs((last.getLong("time") - first.getLong("time")) - elapsed) <= 60_000) {
                    val sleep = PowerAnalysisPolicy.sleepMs(elapsed, last.getLong("uptimeMillis") - first.getLong("uptimeMillis"))
                    if (sleep != null) {
                        appendLine("首尾时钟覆盖：${number(elapsed / 60000.0)} 分钟；深度休眠约 ${number(sleep / 60000.0)} 分钟（${number(sleep * 100.0 / elapsed)}%）。")
                        appendLine("未进入深度休眠约 ${number((elapsed - sleep) / 60000.0)} 分钟；这不代表 CPU 始终忙碌。")
                    } else appendLine("休眠时钟不连续，可能发生重启，无法计算休眠比例。")
                }
            }
            val beforeStats = File(folder, "batterystats-before.txt")
            val afterStats = File(folder, "batterystats-after.txt")
            if (beforeStats.exists() && afterStats.exists() &&
                samples.firstOrNull()?.optBoolean("statsAvailable") == true &&
                samples.lastOrNull()?.optBoolean("statsAvailable") == true) {
                val before = PowerStatsSummary.parse(beforeStats.readText())
                val after = PowerStatsSummary.parse(afterStats.readText())
                val screen = before.screenOnDelta(after)
                if (screen != null) {
                    appendLine("首末累计统计的亮屏时间增量：${number(screen / 60000.0)} 分钟（含准备/结束操作）。")
                    if (screen > 60_000) appendLine("本轮包含超过 1 分钟亮屏，不能当作纯熄屏结果。")
                } else appendLine("亮屏累计时长不可比较：统计可能重置或厂商输出格式不支持。")
            } else appendLine("完整首尾统计尚不可用，无法验证实际亮屏时长；请查看采集状态。")
            appendLine()
            appendLine("检测到的已安装 LSP 模块（启用状态与作用域未验证）：")
            if (modules.length() == 0) appendLine("暂无检测结果；不代表没有其他模块。")
            for (i in 0 until modules.length()) {
                val item = modules.getJSONObject(i)
                appendLine("• ${item.optString("label")} · ${item.optString("package")}")
            }
            appendLine()
            appendLine("整机归因依据")
            val dumps = folder.listFiles()?.filter { it.name.startsWith("batterystats-") }?.sortedBy { it.name }.orEmpty()
            appendLine("已保存 ${dumps.size} 份累计电池统计；CPU 与电源状态在同一轮保存。")
            val cpu = File(folder, "cpuinfo-after.txt").takeIf { it.exists() }
                ?: folder.listFiles()?.filter { it.name.startsWith("cpuinfo-") }?.maxByOrNull { it.name }
            val cpuRows = cpu?.readLines()?.mapNotNull { line ->
                Regex("^\\s*([0-9.]+)%\\s+([0-9]+)/([^:]+):.*").matchEntire(line)?.let {
                    Triple(it.groupValues[1].toDoubleOrNull() ?: 0.0, it.groupValues[3], line.trim())
                }
            }?.sortedByDescending { it.first }?.take(8).orEmpty()
            if (cpuRows.isNotEmpty()) {
                appendLine()
                appendLine("最近一次 CPU 快照中较繁忙的宿主（短时占用，不能代表整段监测）：")
                cpu?.readLines()?.firstOrNull { it.startsWith("CPU usage from ") }?.let { appendLine("实际 CPU 窗口：$it") }
                cpuRows.forEach { appendLine("• ${it.third}") }
            }
            (afterStats.takeIf { it.exists() } ?: dumps.lastOrNull())?.let { dump ->
                val lines = dump.readLines()
                val index = lines.indexOfFirst { it.contains("Estimated power use (mAh)") }
                if (index >= 0) {
                    appendLine()
                    appendLine("系统累计估算耗电摘录（从系统统计起点累计，非本次监测增量）：")
                    lines.drop(index + 1).take(25).takeWhile { it.isNotBlank() }.forEach { appendLine(it.trim()) }
                }
            }
            appendLine()
            appendLine("导出后对比首末 batterystats 的 UID CPU、partial wakelock、网络与估算耗电，使用 uid-packages.json 对应宿主。CPU 快照不是整个区间累计值。")
            appendLine("跨充电重置或重启的累计统计不能直接相减；系统估算 mAh 不等于硬件实测。")
            appendLine("多个模块可共同影响同一宿主，现有数据无法准确拆分到单个 LSP 模块。需在 LSPosed 中逐个禁用、重启宿主，并重复相同场景；本工具不会自动禁用其他模块。")
            appendLine("若某宿主持续繁忙，再录制 30–60 秒 Perfetto 定位线程。")
            appendLine("本机保存最近三轮记录，不上传；开始新一轮前可导出本轮。")
        }
    }

    fun export(context: Context, state: PowerAnalysisState, output: OutputStream) {
        val report = text(context, state)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write(report.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            PowerAnalysisStore.directory(context, state.start).listFiles()?.filter { it.isFile }
                ?.sortedBy { it.name }?.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }
}
