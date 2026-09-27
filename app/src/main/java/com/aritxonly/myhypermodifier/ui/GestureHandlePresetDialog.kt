package com.aritxonly.myhypermodifier

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class GestureHandlePresetDialogMode { Import, Export }

@Composable
internal fun GestureHandlePresetDialog(
    settings: ModifierSettings,
    mode: GestureHandlePresetDialogMode,
    onDismiss: () -> Unit,
    onImport: (ModifierSettings) -> Unit,
) {
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    val isImport = mode == GestureHandlePresetDialogMode.Import
    var json by rememberSaveable(mode) {
        mutableStateOf(if (isImport) "" else GestureHandlePresetJson.export(settings))
    }
    var reading by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val imported = remember(json, isImport) { if (isImport) GestureHandlePresetJson.parse(json) else null }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutine.launch {
            reading = true
            fileError = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val buffer = CharArray(GestureHandlePresetJson.MAX_LENGTH + 1)
                        var count = 0
                        while (count < buffer.size) {
                            val read = reader.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        require(count <= GestureHandlePresetJson.MAX_LENGTH)
                        String(buffer, 0, count)
                    } ?: error("Cannot open file")
                }
                json = text
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fileError = "无法读取文件，或文件内容过长。"
            } finally {
                reading = false
            }
        }
    }
    val fileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) coroutine.launch {
            reading = true
            fileError = null
            feedback = null
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(json)
                    } ?: error("Cannot save file")
                }
                feedback = "JSON 文件已保存。"
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fileError = "无法保存 JSON 文件。"
            } finally {
                reading = false
            }
        }
    }
    DeadlinerMiuixDialog(
        true,
        if (isImport) "导入 JSON 预设" else "导出 JSON 预设",
        if (isImport) "替换小横条预设及应用设置。" else null,
        onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = json,
                onValueChange = { json = it; fileError = null },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                label = if (isImport) "粘贴 JSON" else "JSON",
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                readOnly = reading || !isImport,
            )
            val message = when {
                reading -> if (isImport) "正在读取…" else "正在保存…"
                fileError != null -> fileError
                feedback != null -> feedback
                isImport && json.isNotBlank() && imported == null -> "JSON 格式、预设类型或应用模式无效。"
                imported != null -> "${if (imported.modulePreset) "模块预设" else "系统预设"} · ${imported.apps.size} 个应用设置"
                else -> null
            }
            message?.let {
                Text(
                    it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (fileError != null || (isImport && json.isNotBlank() && imported == null))
                        MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            TextButton(
                if (isImport) "选择 JSON 文件" else "保存 JSON 文件",
                {
                    if (isImport) filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    else fileSaver.launch("gesture-handle-preset.json")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !reading,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("取消", onDismiss, Modifier.weight(1f))
                Button(
                    onClick = {
                        if (isImport) imported?.let { onImport(it.applyTo(settings)) }
                        else {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Gesture handle preset", json))
                            feedback = "JSON 已复制。"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !reading && (!isImport || (imported != null && fileError == null)),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(if (isImport) "导入" else "复制 JSON") }
            }
        }
    }
}
