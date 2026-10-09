package com.aritxonly.myhypermodifier

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowListPopup

internal val homeEntryPositions = listOf("device" to "设备信息分组", "top" to "顶部", "bottom" to "底部", "middle" to "中间")
private data class VisibleModule(val entry: HomeModuleEntryConfig.Entry, val icon: ImageBitmap?)

@Composable
internal fun HomeEntriesSettingsPage(
    padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    var modules by remember { mutableStateOf<List<VisibleModule>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() { if (!loading) { refreshing = true; loading = true; revision++ } }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { if (resumed) revision++ else resumed = true }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision) {
        loading = true
        error = false
        try {
            modules = withContext(Dispatchers.IO) {
                LspModuleRepository.installed(context).map { entry ->
                    VisibleModule(entry, runCatching {
                        context.packageManager.getApplicationIcon(entry.packageName).toBitmap(96, 96).asImageBitmap()
                    }.getOrNull())
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false; refreshing = false }
    }
    val enabled = remember(settings.settingsModuleEntries) {
        HomeModuleEntryConfig.decode(settings.settingsModuleEntries).associateBy { it.packageName }
    }
    val iconModifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))
    RefreshableSettingsContent(padding, refreshing, ::refresh) {
        SettingsScrollPage(padding, onScroll) {
            SettingsSection(topLabel = "模块设置入口") {
                EntryRow("HyperModifier", settings.settingsHomeEntryEnabled, settings.settingsHomeEntryPosition,
                    { update(settings.copy(settingsHomeEntryPosition = it)) }, { update(settings.copy(settingsHomeEntryEnabled = it)) }) {
                    Image(painterResource(R.drawable.ic_settings_hypermodifier), null, iconModifier)
                }
                EntryRow("LSPosed 管理器", settings.settingsModulesEntryEnabled, settings.settingsManagerEntryPosition,
                    { update(settings.copy(settingsManagerEntryPosition = it)) }, { update(settings.copy(settingsModulesEntryEnabled = it)) }) {
                    Image(painterResource(R.drawable.ic_settings_lsposed), null, iconModifier)
                }
                modules.forEach { visible ->
                    val module = visible.entry
                    val saved = enabled[module.packageName]
                    key(module.packageName) {
                        EntryRow(module.title, saved != null, saved?.position ?: "middle",
                            { position ->
                                val entry = HomeModuleEntryConfig.Entry(module.packageName, module.activity, module.title, position, module.category)
                                update(settings.copy(settingsModuleEntries = HomeModuleEntryConfig.update(settings.settingsModuleEntries, entry, true)))
                            }, { selected ->
                                val entry = HomeModuleEntryConfig.Entry(module.packageName, module.activity, module.title, saved?.position ?: "middle", module.category)
                                update(settings.copy(settingsModuleEntries = HomeModuleEntryConfig.update(settings.settingsModuleEntries, entry, selected)))
                            }) {
                            if (visible.icon != null) Image(visible.icon, null, iconModifier, contentScale = ContentScale.Fit)
                            else Image(painterResource(R.drawable.ic_home_settings), null, iconModifier)
                        }
                    }
                }
                if (loading) SettingItem("正在读取模块…", "")
                else if (error) SettingItem("无法读取模块列表，请下拉刷新重试", "", onClick = ::refresh)
                else if (modules.isEmpty()) SettingItem("没有其他可打开设置界面的模块", "")
            }
        }
    }
}

@Composable
private fun EntryRow(
    title: String, enabled: Boolean, position: String, onPosition: (String) -> Unit,
    onEnabled: (Boolean) -> Unit, icon: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box {
        SettingItem(title, if (enabled) homeEntryPositions.firstOrNull { it.first == position }?.second ?: "中间" else "未启用",
            onClick = if (enabled) ({ expanded = true }) else null,
            leadingContent = icon,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .25f)))
                    Spacer(Modifier.width(16.dp))
                    DeadlinerSwitch(enabled, onEnabled)
                }
            })
        WindowListPopup(
            show = expanded && enabled,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                homeEntryPositions.forEachIndexed { index, (value, label) ->
                    DropdownImpl(
                        text = label,
                        optionSize = homeEntryPositions.size,
                        isSelected = value == position,
                        index = index,
                        onSelectedIndexChange = {
                            onPosition(value)
                            dismiss?.invoke()
                        },
                    )
                }
            }
        }
    }
}
