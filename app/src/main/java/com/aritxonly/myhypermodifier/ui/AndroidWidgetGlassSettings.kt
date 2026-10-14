package com.aritxonly.myhypermodifier

import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val widgetGlassLabels = listOf("不启用柔光玻璃", "色调柔光玻璃", "通透柔光玻璃")
internal fun ModifierSettings.widgetStyle(provider: ComponentName, id: Int = 0) = WidgetGlassStyles.resolve(
    androidWidgetGlassStyles, androidWidgetGlassTargets, androidWidgetClockGlassEnabled,
    androidWidgetClockGlassTargets, provider.packageName, provider.className, id,
)

@Composable
internal fun AndroidWidgetGlassSettingsPage(
    padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberLazyListState()
    var apps by remember { mutableStateOf<List<GlassWidgetApp>?>(null) }
    var instances by remember { mutableStateOf<List<DesktopWidgetInstance>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var customPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var customDraft by rememberSaveable { mutableStateOf("") }
    val rules = remember(settings.androidWidgetGlassStyles) { WidgetGlassStyles.parse(settings.androidWidgetGlassStyles) }
    fun refresh() { if (!loading) { refreshing = true; loading = true; attempt++ } }
    fun hideKeyboard() { focus.clearFocus(); keyboard?.hide() }
    fun save(next: Map<String, Int>): Boolean {
        val encoded = WidgetGlassStyles.encode(next)
        if (encoded.length > WidgetGlassStyles.MAX_BYTES) {
            Toast.makeText(context, "选择过多，请优先按整个应用设置", Toast.LENGTH_SHORT).show()
            return false
        }
        update(settings.copy(androidWidgetGlassStyles = encoded))
        return true
    }
    LaunchedEffect(context, tab, attempt) {
        loading = true; error = null
        try {
            if (tab == 0 || apps == null) apps = withContext(Dispatchers.IO) { loadGlassWidgetApps(context) }
            if (tab == 1) instances = requestDesktopWidgetInstances(context)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "读取失败，请刷新重试" }
        finally { loading = false; refreshing = false }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { if (scroll.firstVisibleItemIndex > 0) 1f else (scroll.firstVisibleItemScrollOffset / 32f).coerceIn(0f, 1f) }.collect(onScroll)
    }
    val filteredApps = remember(apps, query) { apps.orEmpty().filter { app ->
        val search = query.trim()
        app.label.contains(search, true) || app.packageName.contains(search, true) ||
            app.widgets.any { it.label.contains(search, true) || it.provider.className.contains(search, true) }
    } }
    val filteredInstances = remember(instances, apps, query) { instances.filter { instance ->
        val app = apps.orEmpty().find { it.packageName == instance.provider.packageName }
        val widget = app?.widgets?.find { it.provider == instance.provider }
        val search = query.trim()
        app?.label?.contains(search, true) == true || widget?.label?.contains(search, true) == true ||
            instance.provider.flattenToString().contains(search, true) || instance.id.toString() == search
    } }
    RefreshableSettingsContent(padding, refreshing, ::refresh) {
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp, top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection) + 16.dp, bottom = padding.calculateBottomPadding() + 82.dp,
            )) {
            item(key = "controls") {
                TabRow(tabs = listOf("应用设置", "已添加小组件"), selectedTabIndex = tab,
                    onTabSelected = { if (tab != it) { tab = it; query = ""; hideKeyboard() } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                SettingsSection {
                    SettingItem(if (tab == 0) "按应用设置材质" else "按桌面实例设置材质",
                        (if (tab == 0) "自定义可按类型设置，实例设置优先。"
                         else "仅调整选中的桌面实例。") +
                            "修改后重启桌面。" + if (settings.androidWidgetGlassEnabled) "" else "玻璃背景已关闭。")
                }
                SearchBar(inputField = {
                    InputField(query = query, onQueryChange = { query = it }, onSearch = { hideKeyboard() },
                        expanded = searchExpanded, onExpandedChange = { searchExpanded = it }, label = "搜索应用或小组件")
                }, expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                    outsideEndAction = { TextButton("取消", { searchExpanded = false; query = ""; hideKeyboard() }) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {}
            }
            if (loading || error != null) item(key = "status") {
                SettingsSection { SettingItem(if (loading) "正在读取…" else "读取失败", error ?: "正在读取小组件列表",
                    onClick = if (loading) null else (::refresh)) }
            }
            if (tab == 0) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val explicit = rules[app.packageName]
                    val hasProviderOverrides = rules.keys.any { it.startsWith(app.packageName + "/") && '#' !in it }
                    val modes = app.widgets.map { settings.widgetStyle(it.provider) }.distinct()
                    val mode = if (explicit == WidgetGlassStyles.CUSTOM || hasProviderOverrides || modes.size > 1)
                        WidgetGlassStyles.CUSTOM else explicit ?: modes.firstOrNull() ?: WidgetGlassStyles.TINTED
                    SettingsSection {
                        WindowDropdownPreference(title = app.label, summary = "${app.widgets.size} 种小组件 · ${app.packageName}",
                            items = widgetGlassLabels + "自定义", selectedIndex = mode,
                            startAction = { app.icon?.let { Image(it, null, Modifier.size(40.dp)) } },
                            onSelectedIndexChange = { selected ->
                                hideKeyboard()
                                if (selected == WidgetGlassStyles.CUSTOM) {
                                    customDraft = WidgetGlassStyles.encode(app.widgets.associate { it.provider.flattenToString() to settings.widgetStyle(it.provider) })
                                    customPackage = app.packageName
                                } else save(WidgetGlassStyles.setApplication(settings.androidWidgetGlassStyles, app.packageName, selected))
                            })
                    }
                }
                if (!loading && error == null && filteredApps.isEmpty()) item(key = "empty-apps") {
                    SettingsSection { SettingItem("没有匹配的应用或小组件", "尝试其他名称，或下拉刷新") }
                }
            } else {
                items(filteredInstances, key = { it.key }) { instance ->
                    val app = apps.orEmpty().find { it.packageName == instance.provider.packageName }
                    val widget = app?.widgets?.find { it.provider == instance.provider }
                    SettingsSection {
                        WindowDropdownPreference(title = widget?.label ?: instance.provider.shortClassName,
                            summary = "${app?.label ?: instance.provider.packageName} · 实例 #${instance.id}",
                            items = widgetGlassLabels, selectedIndex = settings.widgetStyle(instance.provider, instance.id),
                            startAction = { app?.icon?.let { Image(it, null, Modifier.size(40.dp)) } },
                            onSelectedIndexChange = { selected -> save(rules + (instance.key to selected)) })
                        if (instance.key in rules) SettingItem("恢复应用设置", "移除此实例的单独选择", onClick = { save(rules - instance.key) })
                    }
                }
                if (!loading && error == null && filteredInstances.isEmpty()) item(key = "empty-instances") {
                    SettingsSection { SettingItem("没有已添加的匹配小组件", "添加安卓小组件到系统桌面后，下拉刷新") }
                }
            }
        }
    }
    val customApp = apps.orEmpty().find { it.packageName == customPackage }
    if (customApp != null) WidgetGlassCustomDialog(customApp, customDraft, { customDraft = it },
        onDismiss = { customPackage = null }, onSave = {
            val next = WidgetGlassStyles.setApplication(settings.androidWidgetGlassStyles, customApp.packageName, WidgetGlassStyles.CUSTOM)
            next.putAll(WidgetGlassStyles.parse(customDraft))
            if (save(next)) customPackage = null
        })
}

@Composable
private fun WidgetGlassCustomDialog(app: GlassWidgetApp, draft: String, onDraft: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    val rules = remember(draft) { WidgetGlassStyles.parse(draft) }
    DeadlinerMiuixDialog(true, app.label, "按类型设置，桌面实例设置优先。", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(app.widgets, key = { it.provider.flattenToString() }) { widget ->
                    WindowDropdownPreference(title = widget.label,
                        summary = widget.cellSize ?: if (app.widgets.count { it.label == widget.label } > 1) widget.provider.shortClassName.substringAfterLast('.') else null,
                        items = widgetGlassLabels, selectedIndex = rules[widget.provider.flattenToString()] ?: WidgetGlassStyles.TINTED,
                        onSelectedIndexChange = { onDraft(WidgetGlassStyles.encode(rules + (widget.provider.flattenToString() to it))) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("取消", onDismiss, Modifier.weight(1f))
                Button(onClick = onSave, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text("保存") }
            }
        }
    }
}
