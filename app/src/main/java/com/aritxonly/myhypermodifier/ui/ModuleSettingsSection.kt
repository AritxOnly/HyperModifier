package com.aritxonly.myhypermodifier

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun ModuleSettingsSection(containerAlpha: Float, onSettingsRestored: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var hidden by remember { mutableStateOf(ModuleLauncher.isHidden(context)) }
    var busy by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Map<String, Any>?>(null) }
    fun runOperation(success: String, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { operation() }
                Toast.makeText(context, success, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(context, error.message ?: "操作失败", Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runOperation("配置已备份") {
            val encoded = SettingsBackupCodec.encode(ModifierSettingsStore.backupValues(appContext))
            checkNotNull(appContext.contentResolver.openOutputStream(uri, "wt")) { "无法打开备份文件" }
                .bufferedWriter(Charsets.UTF_8).use { it.write(encoded) }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val values = withContext(Dispatchers.IO) {
                        val bytes = checkNotNull(appContext.contentResolver.openInputStream(uri)) { "无法打开备份文件" }
                            .use { it.readNBytes(SettingsBackupCodec.MAX_BYTES + 1) }
                        require(bytes.size <= SettingsBackupCodec.MAX_BYTES) { "备份文件过大" }
                        SettingsBackupCodec.decode(bytes.toString(Charsets.UTF_8), ModifierSettingsStore.backupSchema())
                    }
                    pendingRestore = values
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Toast.makeText(context, error.message ?: "无法读取备份", Toast.LENGTH_LONG).show()
                } finally { busy = false }
            }
        }
    }
    SettingsSection(topLabel = "模块设置", containerAlpha = containerAlpha) {
        SettingsSwitchItem("隐藏桌面图标", "可从 LSPosed 或拨号 *#*#${ModuleLauncher.CODE}#*#* 打开", hidden, { checked ->
            if (checked) confirmHide = true else runOperation("桌面图标已显示") {
                ModuleLauncher.setHidden(appContext, false)
                withContext(Dispatchers.Main) { hidden = false }
            }
        }, enabled = !busy)
        SettingItem("备份配置", if (busy) "正在处理…" else "包含自定义图片", enabled = !busy,
            onClick = { export.launch("HyperModifier-${BuildConfig.VERSION_NAME}.json") })
        SettingItem("恢复配置", "覆盖备份中的配置项", enabled = !busy,
            onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
    }
    if (confirmHide) DeadlinerMiuixDialog(true, "隐藏桌面图标", "隐藏后可从 LSPosed 或拨号 *#*#${ModuleLauncher.CODE}#*#* 打开。", { confirmHide = false }) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("取消", { confirmHide = false }, modifier = Modifier.weight(1f))
            TextButton("隐藏", {
                confirmHide = false
                runOperation("桌面图标已隐藏") {
                    ModuleLauncher.setHidden(appContext, true)
                    withContext(Dispatchers.Main) { hidden = true }
                }
            }, modifier = Modifier.weight(1f))
        }
    }
    pendingRestore?.let { values ->
        DeadlinerMiuixDialog(true, "恢复配置", "将覆盖 ${values.size} 项配置，部分功能需重启对应应用。", { pendingRestore = null }) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("取消", { pendingRestore = null }, modifier = Modifier.weight(1f))
                TextButton("恢复", {
                    pendingRestore = null
                    runOperation("配置已恢复；请按需重启对应作用域") {
                        ModifierSettingsStore.restoreBackup(appContext, values)
                        withContext(Dispatchers.Main) { onSettingsRestored() }
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}
