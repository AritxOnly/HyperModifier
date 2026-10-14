package com.aritxonly.myhypermodifier

import android.app.BroadcastOptions
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class GlassWidget(val provider: ComponentName, val label: String, val cellSize: String? = null)
internal data class GlassWidgetApp(val packageName: String, val label: String, val icon: ImageBitmap?, val widgets: List<GlassWidget>)
internal data class DesktopWidgetInstance(val id: Int, val provider: ComponentName) {
    val key get() = "${provider.flattenToString()}#$id"
}
internal fun loadGlassWidgetApps(context: Context): List<GlassWidgetApp> {
    val pm = context.packageManager
    val collator = Collator.getInstance()
    return AppWidgetManager.getInstance(context).installedProviders.distinctBy { it.provider }.groupBy { it.provider.packageName }
        .map { (pkg, providers) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            val widgets = providers.map { info ->
                GlassWidget(info.provider, runCatching { info.loadLabel(pm) }.getOrNull() ?: info.provider.shortClassName,
                    if (info.targetCellWidth > 0 && info.targetCellHeight > 0) "${info.targetCellWidth} × ${info.targetCellHeight}" else null)
            }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
            GlassWidgetApp(pkg, label, icon, widgets)
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
}
internal suspend fun requestDesktopWidgetInstances(context: Context): List<DesktopWidgetInstance> {
    if (android.os.Build.VERSION.SDK_INT < 36) throw IllegalStateException("已添加小组件设置需要 Android 16 / HyperOS Runtime 桌面")
    return try {
        withTimeout(8000) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(code: Int, data: Bundle?) {
                        if (!continuation.isActive) return
                        if (code != 0) {
                            continuation.resumeWithException(IllegalStateException(data?.getString("error") ?: "读取失败，请刷新重试"))
                            return
                        }
                        val instances = data?.getStringArrayList("widgets").orEmpty().mapNotNull { row ->
                            val pair = row.split('\t')
                            val id = pair.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                            val provider = pair.getOrNull(1)?.let(ComponentName::unflattenFromString) ?: return@mapNotNull null
                            if (id > 0) DesktopWidgetInstance(id, provider) else null
                        }.distinctBy { it.key }
                        continuation.resume(instances)
                    }
                }
                context.sendBroadcast(Intent(DesktopWidgetInventory.ACTION).setPackage("com.android.systemui").putExtra("reply", receiver), null,
                    BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
            }
        }
    } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
        throw IllegalStateException("系统界面未响应，请确认作用域已启用，更新模块后重启手机")
    }
}
