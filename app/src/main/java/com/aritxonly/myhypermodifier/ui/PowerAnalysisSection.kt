package com.aritxonly.myhypermodifier

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun PowerAnalysisSection(containerAlpha: Float) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var visible by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var state by remember { mutableStateOf(PowerAnalysisStore.state(context)) }
    var root by remember { mutableStateOf(PowerAnalysisStore.requestsRoot(context)) }
    var minutes by remember { mutableStateOf(15) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var exportStart by rememberSaveable { mutableStateOf(0L) }
    var exportEnd by rememberSaveable { mutableStateOf(0L) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val selected = PowerAnalysisStore.state(context).copy(start = exportStart, end = exportEnd)
        exportStart = 0L
        if (uri != null && selected.start > 0) scope.launch {
            busy = true
            message = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                        PowerAnalysisReport.export(context, selected, it)
                    }
                    "报告已导出"
                }.getOrElse { "导出失败：${it.message}" }
            }
            busy = false
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            visible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(context, visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            state = PowerAnalysisStore.state(context)
            if (state.due && !busy) {
                busy = true
                try {
                    message = withContext(Dispatchers.IO) {
                        runCatching { PowerAnalysisCollector.finish(context, state.start); null }
                            .getOrElse { "结束采集失败：${it.message}" }
                    }
                    state = PowerAnalysisStore.state(context)
                } finally {
                    busy = false
                }
            }
            delay(5_000)
        }
    }
    SettingsSection(topLabel = "性能分析", containerAlpha = containerAlpha) {
        SettingItem("熄屏功耗测试", when {
            state.due -> "正在补齐结束数据…"
            state.active -> "剩余约 ${((state.end - now + 59_999) / 60_000).coerceAtLeast(0)} 分钟 · 已采样 ${state.count} 次\n${state.status}"
            state.start > 0 -> "已结束 · 已采样 ${state.count} 次\n${state.status}"
            else -> "分析电池变化与宿主进程，包含其他 LSP 模块的排查线索。"
        })
        if (!state.active) {
            NavigationSettingItem("监测时长", "$minutes 分钟 · 点击切换 15 / 30 分钟",
                enabled = !busy, onClick = { minutes = if (minutes == 15) 30 else 15 })
            SettingsSwitchItem("读取整机统计（Root）", "开始后请求 Root，用于读取 CPU、唤醒及电池统计；拒绝后仍可采集基础电池数据。",
                root, { root = it }, enabled = !busy)
        }
        NavigationSettingItem(if (state.active) "提前结束" else "开始监测", "基线就绪后熄屏，期间只采集首尾两次；结束任务可能被休眠推迟，打开本页会补齐。",
            enabled = !busy && exportStart == 0L, onClick = {
                busy = true
                scope.launch {
                    message = withContext(Dispatchers.IO) {
                        runCatching {
                            if (state.active) PowerAnalysisCollector.finish(context, state.start, manual = true)
                            else PowerAnalysisStore.start(context, root, minutes)
                            null
                        }.getOrElse { "操作失败：${it.message}" }
                    }
                    state = PowerAnalysisStore.state(context)
                    busy = false
                }
            })
        if (state.start > 0) {
            NavigationSettingItem("查看报告", "显示首尾消耗、深度休眠比例与亮屏时间增量。", enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { PowerAnalysisReport.text(context, state) } }
                    report = result.getOrNull()
                    message = result.exceptionOrNull()?.let { "读取失败：${it.message}" }
                    busy = false
                }
            })
            NavigationSettingItem("导出报告与原始数据", "保存 ZIP 到选择的位置；包含设备、应用列表和电池统计。", enabled = !busy && exportStart == 0L,
                onClick = { exportStart = state.start; exportEnd = state.end; exporter.launch("HyperModifier-power-${state.start}.zip") })
        }
        SettingItem("分析范围", "模块耗电通常计入被注入的应用或 SystemUI；同一宿主内多个模块需通过开关对照区分。数据仅存本机，保留最近三轮。")
        message?.let { SettingItem("操作结果", it) }
    }
    report?.let { text ->
        DeadlinerMiuixDialog(true, "性能分析报告", null, { report = null }) {
            Text(text, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(vertical = 12.dp))
            TextButton("关闭", { report = null })
        }
    }
}
