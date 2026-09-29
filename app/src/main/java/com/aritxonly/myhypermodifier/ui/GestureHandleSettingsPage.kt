package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class GestureHandleApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val icon: ImageBitmap?,
)

private enum class GestureHandlePresetAction { Choose, Import, Export }

@Composable
internal fun GestureHandleSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val framework by ModuleFrameworkState.snapshot
    val scope = if (framework.connected) framework.scope else ModifierSettingsStore.gestureHandleScopePackages(context)
    val scroll = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<GestureHandleApp>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var presetAction by rememberSaveable { mutableStateOf<GestureHandlePresetAction?>(null) }
    var pendingPreset by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPreset = ModifierSettingsPresets.selectedGestureHandlePreset(settings)
    val currentPresetLabel = selectedPreset?.let(::gestureHandlePresetLabel) ?: "自定义"
    LaunchedEffect(context, loadAttempt) {
        loadFailed = false
        apps = withContext(Dispatchers.IO) { runCatching { loadVisibleApps(context) }.getOrNull() }
        loadFailed = apps == null
    }
    LaunchedEffect(scroll) {
        snapshotFlow {
            if (scroll.firstVisibleItemIndex > 0) 1f
            else (scroll.firstVisibleItemScrollOffset / 32f).coerceIn(0f, 1f)
        }.collect(onScroll)
    }
    val filtered = remember(apps, query) {
        apps.orEmpty().filter {
            it.label.contains(query.trim(), ignoreCase = true) ||
                it.packageName.contains(query.trim(), ignoreCase = true)
        }
    }
    LazyColumn(
        state = scroll,
        // Insets belong to the scrollable content, so the viewport and backdrop extend
        // beneath the top bar and system navigation area throughout the scroll.
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection) + 16.dp,
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = padding.calculateBottomPadding() + 82.dp,
        ),
    ) {
        item {
            SettingsSection {
                SettingItem(
                    "选择预设", "",
                    trailingContent = {
                        Text(
                            "当前：$currentPresetLabel",
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                    onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        pendingPreset = selectedPreset?.takeIf(GestureHandlePresets::selectable)
                        presetAction = GestureHandlePresetAction.Choose
                    },
                )
                SettingsSwitchItem("触摸时显示 3 秒", "", settings.gestureHandleTouchReveal,
                    { update(settings.copy(gestureHandleTouchReveal = it)) })
                SettingsSwitchItem("滑动时跟随", "", settings.gestureHandleSwipeMotion,
                    { update(settings.copy(gestureHandleSwipeMotion = it)) })
                SettingsSliderItemWithLabel(
                    "底部响应距离", settings.gestureHandleTouchAreaDp,
                    0f..GestureHandleTouchArea.MAX_DP,
                    { update(settings.copy(gestureHandleTouchAreaDp = it.roundToInt().toFloat())) },
                    steps = GestureHandleTouchArea.MAX_DP.toInt() - 1,
                    valueText = { "${it.roundToInt()} dp" },
                )
            }
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { focus.clearFocus(); keyboard?.hide() },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索应用名称或包名",
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                outsideEndAction = {
                    TextButton("取消", { searchExpanded = false; query = ""; focus.clearFocus(); keyboard?.hide() })
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Text(
                    "${filtered.size} 个匹配应用",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Text(
                "应用列表（包含系统应用）",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (apps == null) {
            item {
                SettingsSection {
                    SettingItem(
                        if (loadFailed) "应用列表加载失败" else "正在加载应用…",
                        if (loadFailed) "" else "筛选具有可见 Activity 页面的应用",
                        onClick = if (loadFailed) ({ loadAttempt++ }) else null,
                    )
                }
            }
        } else if (filtered.isEmpty()) {
            item { SettingsSection { SettingItem("没有匹配的应用", "尝试其他名称或包名") } }
        }
        if (filtered.isNotEmpty()) item {
            SettingsSection {
                filtered.forEach { app ->
                    key(app.packageName) {
                        SettingItem(
                            app.label,
                            "",
                            leadingContent = {
                                app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                            },
                            trailingContent = {
                                Text(
                                    gestureHandleModeLabel(effectiveMode(settings, app, scope)),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { focus.clearFocus(); keyboard?.hide(); selectedPackage = app.packageName },
                        )
                    }
                }
            }
        }
    }
    selectedPackage?.let { packageName ->
        val app = apps.orEmpty().firstOrNull { it.packageName == packageName }
        fun select(mode: String?) {
            val modes = settings.gestureHandleAppModes.toMutableMap()
            if (mode == null) modes.remove(packageName) else modes[packageName] = mode
            update(settings.copy(gestureHandleAppModes = modes.toMap()))
            selectedPackage = null
        }
        val effective = effectiveMode(settings, app, scope)
        DeadlinerMiuixDialog(true, app?.label ?: packageName, "当前模式：${gestureHandleModeLabel(effective)}", { selectedPackage = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                listOf(
                    GestureHandleRules.SHOW,
                    GestureHandleRules.HIDE,
                    GestureHandleRules.IMMERSIVE,
                ).forEach { mode ->
                    SettingsCheckboxItem(
                        gestureHandleModeLabel(mode), "",
                        effective == mode,
                        { select(mode) },
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton("跟随系统", { select(GestureHandleRules.SYSTEM) }, modifier = Modifier.fillMaxWidth())
                    TextButton("恢复预设", { select(null) }, modifier = Modifier.fillMaxWidth())
                    TextButton("取消", { selectedPackage = null }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (presetAction == GestureHandlePresetAction.Choose) {
        DeadlinerMiuixDialog(true, "选择预设", null, { presetAction = null }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    GestureHandlePresets.MODULE,
                    GestureHandlePresets.IMMERSIVE,
                    GestureHandlePresets.HIDE,
                    GestureHandlePresets.SHOW,
                ).forEach { preset ->
                    SettingsCheckboxItem(gestureHandlePresetLabel(preset), "",
                        pendingPreset == preset, { pendingPreset = if (pendingPreset == preset) null else preset })
                }
                Button(onClick = { presetAction = GestureHandlePresetAction.Import }, modifier = Modifier.fillMaxWidth()) {
                    Text("导入 JSON")
                }
                Button(onClick = { presetAction = GestureHandlePresetAction.Export }, modifier = Modifier.fillMaxWidth()) {
                    Text("导出 JSON")
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("取消", { presetAction = null }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        pendingPreset?.let { update(ModifierSettingsPresets.gestureHandlePreset(settings, it)) }
                        presetAction = null
                    }, modifier = Modifier.weight(1f), enabled = pendingPreset != null,
                        colors = ButtonDefaults.buttonColorsPrimary()) { Text("确认") }
                }
            }
        }
    }
    if (presetAction == GestureHandlePresetAction.Import || presetAction == GestureHandlePresetAction.Export) GestureHandlePresetDialog(
        settings = settings,
        mode = if (presetAction == GestureHandlePresetAction.Import) GestureHandlePresetDialogMode.Import
            else GestureHandlePresetDialogMode.Export,
        onDismiss = { presetAction = null },
        onImport = { update(it); presetAction = null },
    )
}

private fun effectiveMode(settings: ModifierSettings, app: GestureHandleApp?, scope: Set<String>): String? {
    if (app == null) return null
    val explicit = settings.gestureHandleAppModes[app.packageName]
    return if (explicit == GestureHandleRules.SYSTEM) null else explicit ?: GestureHandleDefaults.mode(
        settings.gestureHandlePreset, app.packageName, app.system, scope,
    )
}

internal fun gestureHandlePresetLabel(preset: String) = when (preset) {
    GestureHandlePresets.MODULE -> "模块预设"
    GestureHandlePresets.IMMERSIVE -> "全沉浸"
    GestureHandlePresets.HIDE -> "全隐藏"
    GestureHandlePresets.SHOW -> "全显示"
    GestureHandlePresets.STOCK -> "跟随系统"
    else -> "模块预设"
}

private fun gestureHandleModeLabel(mode: String?) = when (mode) {
    GestureHandleRules.SHOW -> "显示"
    GestureHandleRules.HIDE -> "隐藏"
    GestureHandleRules.IMMERSIVE -> "沉浸"
    else -> "跟随系统"
}

/** Include system apps and internal pages, but exclude service-only and no-display packages. */
private fun loadVisibleApps(context: Context): List<GestureHandleApp> {
    val pm = context.packageManager
    val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
    val collator = Collator.getInstance()
    return pm.getInstalledPackages(flags).mapNotNull { info ->
        val application = info.applicationInfo ?: return@mapNotNull null
        if (!application.enabled) return@mapNotNull null
        val activities = info.activities.orEmpty().filter { it.enabled }
        if (activities.isEmpty()) return@mapNotNull null
        val resources = runCatching { pm.getResourcesForApplication(application) }.getOrNull()
            ?: return@mapNotNull null
        val hasPage = activities.any { activity ->
            // Theme 0 uses the platform default, which has a visible window.
            val themeId = activity.themeResource
            if (themeId == 0) true else runCatching {
                val theme = resources.newTheme().apply { applyStyle(themeId, true) }
                val values = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowNoDisplay))
                try { !values.getBoolean(0, false) } finally { values.recycle() }
            }.getOrDefault(false)
        }
        if (!hasPage) return@mapNotNull null
        GestureHandleApp(
            info.packageName,
            application.loadLabel(pm).toString(),
            application.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            runCatching { application.loadIcon(pm).toBitmap(80, 80).asImageBitmap() }.getOrNull(),
        )
    }.sortedWith { first, second ->
        collator.compare(first.label, second.label).takeIf { it != 0 }
            ?: first.packageName.compareTo(second.packageName)
    }
}
