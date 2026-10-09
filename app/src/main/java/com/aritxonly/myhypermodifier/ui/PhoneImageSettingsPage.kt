package com.aritxonly.myhypermodifier

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
internal fun PhoneImageSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) {
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    val currentSettings by rememberUpdatedState(settings)
    val detected = remember { PhoneProductPolicy.currentAsset() }
    var presetMenuExpanded by remember { mutableStateOf(false) }
    var presetMenuItems by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val key = PhoneImageSettings.profileKey(settings.aboutPhoneImageSource, detected)
    val transform = remember(settings.aboutPhoneImageTransforms, key) { PhoneImageSettings.transform(settings, key) }
    var x by remember(key, settings.aboutPhoneImageTransforms) { mutableFloatStateOf(transform[0]) }
    var y by remember(key, settings.aboutPhoneImageTransforms) { mutableFloatStateOf(transform[1]) }
    var scale by remember(key, settings.aboutPhoneImageTransforms) { mutableFloatStateOf(transform[2]) }
    var xText by remember(key, settings.aboutPhoneImageTransforms) { mutableStateOf(transform[0].toString()) }
    var yText by remember(key, settings.aboutPhoneImageTransforms) { mutableStateOf(transform[1].toString()) }
    var scaleText by remember(key, settings.aboutPhoneImageTransforms) { mutableStateOf(transform[2].toString()) }
    fun validNumber(text: String, range: ClosedFloatingPointRange<Float>): Boolean =
        text.toFloatOrNull()?.let { it.isFinite() && it in range } == true
    val valid = validNumber(xText, -96f..96f) && validNumber(yText, -96f..96f) && validNumber(scaleText, .1f..4f)
    var presets by remember { mutableStateOf(PhonePresetRepository.cachedCatalog(context, settings.aboutPhonePresetUrl).associate { it.id to it.name }) }
    var sourceUrl by remember(settings.aboutPhonePresetUrl) { mutableStateOf(settings.aboutPhonePresetUrl) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var catalogRevision by remember { mutableStateOf(0) }
    val selectedLabel = when (settings.aboutPhoneImageSource) {
        "auto" -> "自动匹配 · ${presets[detected] ?: "通用手机"}"
        "custom" -> "自定义图片"
        else -> presets[settings.aboutPhoneImageSource] ?: settings.aboutPhoneImageSource
    }

    LaunchedEffect(settings.aboutPhoneImageSource, settings.aboutPhoneCustomImage, settings.aboutPhonePresetUrl, catalogRevision) {
        bitmap = null
        loading = true
        error = null
        try {
            val snapshot = currentSettings
            bitmap = withContext(Dispatchers.IO) { PhoneImageSettings.loadPreview(context, snapshot, detected) }
            presets = PhonePresetRepository.cachedCatalog(context, snapshot.aboutPhonePresetUrl).associate { it.id to it.name }
            val items = withContext(Dispatchers.IO) { PhonePresetRepository.catalog(context, snapshot.aboutPhonePresetUrl) }
            presets = items.associate { it.id to it.name }
            if (snapshot.aboutPhoneImageSource != "custom") {
                withContext(Dispatchers.IO) { PhonePresetRepository.ensureSelected(context, snapshot) }
                bitmap = withContext(Dispatchers.IO) { PhoneImageSettings.loadPreview(context, snapshot, detected) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = "在线预设暂不可用，已保留本地缓存。${failure.message.orEmpty()}" }
        finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutine.launch {
            busy = true; error = null; feedback = null
            try {
                val encoded = withContext(Dispatchers.IO) { PhoneImageSettings.importImage(context, uri) }
                val value = PhoneImageSettings.withTransform(currentSettings, "custom", 0f, 0f, 1f)
                update(value.copy(aboutPhoneImageSource = "custom", aboutPhoneCustomImage = encoded))
                feedback = "图片已保存。"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "无法导入图片。" }
            finally { busy = false }
        }
    }
    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutine.launch {
            busy = true; error = null; feedback = null
            try {
                val snapshot = currentSettings
                val items = withContext(Dispatchers.IO) {
                    PhonePresetRepository.importPackage(context, uri, snapshot.aboutPhonePresetUrl)
                }
                if (currentSettings.aboutPhonePresetUrl == snapshot.aboutPhonePresetUrl) {
                    presets = items.associate { it.id to it.name }
                    val currentKey = PhoneImageSettings.profileKey(currentSettings.aboutPhoneImageSource, detected)
                    if (items.any { it.id == currentKey }) {
                        withContext(Dispatchers.IO) { PhonePresetRepository.ensureSelected(context, currentSettings) }
                        bitmap = withContext(Dispatchers.IO) { PhoneImageSettings.loadPreview(context, currentSettings, detected) }
                    }
                }
                catalogRevision++
                feedback = "已导入 ${items.size} 个预设，可离线选择使用。"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "无法导入预设包。" }
            finally { busy = false }
        }
    }
    fun refreshPresets() {
        if (busy || loading || presetMenuExpanded) return
        refreshing = true
        busy = true
        coroutine.launch {
            busy = true; error = null
            try {
                val snapshot = currentSettings
                val items = withContext(Dispatchers.IO) { PhonePresetRepository.catalog(context, snapshot.aboutPhonePresetUrl, true) }
                presets = items.associate { it.id to it.name }
                withContext(Dispatchers.IO) { PhonePresetRepository.ensureSelected(context, snapshot) }
                bitmap = withContext(Dispatchers.IO) { PhoneImageSettings.loadPreview(context, snapshot, detected) }
                feedback = "预设已刷新，本地缓存可离线使用。"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "无法刷新在线预设。" }
            finally { busy = false; refreshing = false }
        }
    }
    RefreshableSettingsContent(padding, refreshing, ::refreshPresets) {
        SettingsScrollPage(padding, onScroll) {
            SettingsSection(topLabel = "图片预览（3 倍）") {
                Text(selectedLabel, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { PhoneImageView(it) },
                        update = { view -> view.setImage(bitmap); view.setTransform(x, y, scale) },
                        modifier = Modifier.size(192.dp),
                    )
                }
                if (loading) Text("正在加载图片预设…", modifier = Modifier.padding(20.dp))
            }
            if (settings.aboutPhoneImageSource == "custom") {
                SettingsSection(topLabel = "自定义图片调整") {
                    SettingsSliderItemWithLabel("X 偏移", x, -96f..96f, { x = it; xText = it.toString() },
                        enabled = !busy, valueText = { String.format(Locale.ROOT, "%.1f dp", it) })
                    SettingsSliderItemWithLabel("Y 偏移", y, -96f..96f, { y = it; yText = it.toString() },
                        enabled = !busy, valueText = { String.format(Locale.ROOT, "%.1f dp", it) })
                    SettingsSliderItemWithLabel("缩放", scale, .1f..4f, { scale = it; scaleText = it.toString() },
                        enabled = !busy, valueText = { String.format(Locale.ROOT, "%.2f ×", it) })
                    TextField(value = xText, onValueChange = {
                        xText = it
                        if (validNumber(it, -96f..96f)) x = it.toFloat()
                    }, label = "X 精确值（dp）", singleLine = true, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
                    TextField(value = yText, onValueChange = {
                        yText = it
                        if (validNumber(it, -96f..96f)) y = it.toFloat()
                    }, label = "Y 精确值（dp）", singleLine = true, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
                    TextField(value = scaleText, onValueChange = {
                        scaleText = it
                        if (validNumber(it, .1f..4f)) scale = it.toFloat()
                    }, label = "缩放精确值", singleLine = true, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
                    if (!valid) Text("偏移范围为 -96～96 dp，缩放范围为 0.1～4。",
                        color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                    Text("正 X 向右，正 Y 向下。保存后重启系统设置生效。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    Button(onClick = {
                        update(PhoneImageSettings.withTransform(currentSettings, "custom", x, y, scale))
                        feedback = "参数已保存。"
                    }, enabled = !busy && valid,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { Text("保存参数") }
                    SettingItem("恢复默认参数", "", enabled = !busy, onClick = {
                        x = 0f; y = 0f; scale = 1f
                        xText = "0.0"; yText = "0.0"; scaleText = "1.0"
                        update(PhoneImageSettings.withTransform(currentSettings, "custom", x, y, scale))
                    })
                }
            }
            SettingsSection(topLabel = "预设管理") {
                TextField(value = sourceUrl, onValueChange = { sourceUrl = it.trim() },
                    label = "GitHub raw 清单地址", singleLine = true, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                Button(onClick = { update(currentSettings.copy(aboutPhonePresetUrl = sourceUrl)) },
                    enabled = !busy && PhonePresetRepository.validUrl(sourceUrl) && sourceUrl != settings.aboutPhonePresetUrl,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { Text("保存预设源") }
                SettingItem("下载全部预设到缓存", "", enabled = !busy && !loading, onClick = {
                    coroutine.launch {
                        busy = true; error = null; feedback = "正在下载全部预设…"
                        try {
                            val snapshot = currentSettings
                            val count = withContext(Dispatchers.IO) {
                                PhonePresetRepository.downloadAll(context, snapshot.aboutPhonePresetUrl)
                            }
                            feedback = "已缓存 $count 个预设，可离线切换机型。"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "下载失败，已完成的图片会保留在缓存中。"; feedback = null }
                        finally { busy = false }
                    }
                })
                SettingItem("一键导入预设包", "", enabled = !busy, onClick = {
                    packagePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                })
            }
            SettingsSection(topLabel = "图片来源") {
                SettingItem("自动匹配机型", "", enabled = !busy, onClick = {
                    update(currentSettings.copy(aboutPhoneImageSource = "auto"))
                }, trailingContent = { if (settings.aboutPhoneImageSource == "auto") Text("已选") })
                SettingItem("上传图片", "", enabled = !busy, onClick = { picker.launch(arrayOf("image/*")) })
                if (settings.aboutPhoneCustomImage.isNotEmpty()) {
                    SettingItem("使用已上传的图片", "", enabled = !busy, onClick = {
                        update(currentSettings.copy(aboutPhoneImageSource = "custom"))
                    }, trailingContent = { if (settings.aboutPhoneImageSource == "custom") Text("已选") })
                }
            }
            SettingsSection(topLabel = "预设图片") {
                if (presets.isEmpty()) Text("刷新在线预设或导入 ZIP 预设包后，支持机型会显示在这里。",
                    modifier = Modifier.padding(20.dp))
                // Keep IDs and order stable if the catalog finishes refreshing while open.
                val options = if (presetMenuExpanded) presetMenuItems else presets.toList()
                WindowDropdownPreference(
                    title = "选择预设图片",
                    entry = DropdownEntry(options.map { (asset, label) ->
                        DropdownItem(
                            text = label,
                            selected = settings.aboutPhoneImageSource == asset,
                            onClick = { update(currentSettings.copy(aboutPhoneImageSource = asset)) },
                        )
                    }),
                    enabled = !busy && options.isNotEmpty(),
                    onExpandedChange = { expanded ->
                        if (expanded) presetMenuItems = presets.toList()
                        presetMenuExpanded = expanded
                    },
                )
            }
            error?.let { Text(it, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
            feedback?.let { Text(it, modifier = Modifier.padding(20.dp)) }
        }
    }
}
