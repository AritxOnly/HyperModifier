package com.aritxonly.myhypermodifier

import android.app.BroadcastOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val SCHEMA_EXTRACTION = "adb exec-out su -c 'cat /system_ext/lib64/libhyper_os_schema_public.so' > libhyper_os_schema_public.so"

@Composable
internal fun DesktopWidgetCompatibilitySetting(enabled: Boolean) {
    if (android.os.Build.VERSION.SDK_INT < 36) return
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<DesktopWidgetCompatibilityStatus?>(null) }
    var message by remember { mutableStateOf("点击检查桌面 Hook 的实际运行状态") }
    var showGuide by remember { mutableStateOf(false) }
    var warnedReason by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(generation, enabled) {
        if (!enabled && generation == 0) return@LaunchedEffect
        busy = true
        try {
            val current = requestDesktopWidgetCompatibility(context)
            status = current
            message = current.summary()
            if (current.needsSchemaLibrary() && warnedReason != current.reason) {
                warnedReason = current.reason
                showGuide = true
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                status = null
                message = "系统界面未响应，请确认作用域并重启系统界面。尚未确认桌面兼容状态。"
            } else throw cancelled
        } catch (error: Exception) {
            status = null
            message = error.message ?: "无法读取桌面兼容状态"
        } finally {
            busy = false
        }
    }
    NavigationSettingItem(
        title = "桌面 Hook 兼容状态",
        summary = if (busy) "正在检查…" else "$message\n点击刷新" + if (status?.needsSchemaLibrary() == true) "或查看提取指引" else "",
        onClick = {
            if (!busy) {
                if (status?.needsSchemaLibrary() == true) showGuide = true
                generation++
            }
        },
    )
    if (showGuide) DeadlinerMiuixDialog(
        true, "当前系统的小组件接口暂不兼容",
        "小组件堆叠与玻璃能力桥未启用。请提取当前系统的 so 文件给开发者，供后续适配。",
        { showGuide = false },
    ) {
        val guide = """
            1. 电脑连接手机并启用 USB 调试，确认 adb 能识别设备。
            2. 在电脑终端运行下方命令，并在手机上允许 shell 的 root 请求。
            3. 将生成的 libhyper_os_schema_public.so、系统和桌面版本、模块版本，以及启动日志一并发给开发者。

            $SCHEMA_EXTRACTION

            若命令提示权限不足，可用已授权 root 的文件管理器复制：
            /system_ext/lib64/libhyper_os_schema_public.so

            请勿替换或删除手机中的系统库，也不需要清除桌面数据。
            诊断：${status?.reason ?: "unknown"}
        """.trimIndent()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Text(guide, modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()))
            }
            TextButton("复制命令和诊断信息", onClick = {
                val launcher = runCatching { context.packageManager.getPackageInfo("com.miui.home", 0).versionName }.getOrNull()
                val report = "$guide\n\n系统：${android.os.Build.FINGERPRINT}\n桌面：$launcher\n模块：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n$message"
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("小组件兼容诊断", report))
            }, modifier = Modifier.fillMaxWidth())
            TextButton("关闭", onClick = { showGuide = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private suspend fun requestDesktopWidgetCompatibility(context: Context): DesktopWidgetCompatibilityStatus = withTimeout(5000) {
    suspendCancellableCoroutine { continuation ->
        val reply = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                if (!continuation.isActive) return
                if (code != 0) {
                    continuation.resumeWithException(IllegalStateException(data?.getString("error") ?: "桌面状态读取失败"))
                    return
                }
                try { continuation.resume(DesktopWidgetCompatibilityStatus.parse(data?.getString("compatibility"))) }
                catch (error: Exception) { continuation.resumeWithException(error) }
            }
        }
        context.sendBroadcast(Intent(DesktopWidgetInventory.ACTION).setPackage("com.android.systemui")
            .putExtra("compatibility", true).putExtra("reply", reply), null,
            BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
    }
}
