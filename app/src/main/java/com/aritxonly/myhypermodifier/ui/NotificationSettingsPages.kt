package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aritxonly.deadliner.ui.material.glass.DeadlinerGlassRecipes
import com.aritxonly.deadliner.ui.material.glass.LocalImmersiveTopBarEnabled
import com.aritxonly.deadliner.ui.material.glass.LocalTopBarButtonMaterialProgress
import com.aritxonly.deadliner.ui.material.glass.SoftGlassIconButton
import com.aritxonly.deadliner.ui.material.glass.SoftGlassSurface
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.navigation.floatingNavigationShadow
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialBackdrop
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import com.kyant.shapes.Capsule
import java.util.concurrent.TimeUnit
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.graphics.ColorUtils

@Composable
internal fun NotificationControlCenterSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onOpenCardGlassSettings: () -> Unit,
    onOpenGlobalMaterialBlur: () -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "材质兼容性") {
        SettingsSwitchItem(
            "停用卡片玻璃 Hook",
            "与 HyperLight 等材质模块冲突时开启，需重启 SystemUI",
            settings.disableShadeGlassHooks,
            { update(settings.copy(disableShadeGlassHooks = it)) },
        )
    }
    SettingsSection(topLabel = "卡片柔光玻璃") {
        NavigationSettingItem(
            title = "卡片柔光玻璃",
            summary = if (settings.disableShadeGlassHooks) "玻璃 Hook 已停用" else "",
            onClick = onOpenCardGlassSettings,
        )
    }
    SettingsSection(topLabel = "通知中心") {
        SettingsSwitchItem("通知圆角", "", settings.notificationsEnabled, { update(settings.copy(notificationsEnabled = it)) })
        SettingsSliderItemWithLabel("圆角大小", settings.notificationRadius, 12f..48f, { update(settings.copy(notificationRadius = it)) }, steps = 17, enabled = settings.notificationsEnabled)
    }
    SettingsSection(topLabel = "控制中心") {
        SettingsSwitchItem("控制中心圆角", "", settings.controlCenterEnabled, { update(settings.copy(controlCenterEnabled = it)) })
        SettingsSliderItemWithLabel("圆角大小", settings.controlCenterRadius, 12f..48f, { update(settings.copy(controlCenterRadius = it)) }, steps = 17, enabled = settings.controlCenterEnabled)
    }
    SettingsSection(topLabel = "控制中心圆角细节") {
        SettingsSwitchItem(
            "独立圆角",
            "",
            settings.advancedControlCenterCorners,
            { update(settings.copy(advancedControlCenterCorners = it)) },
            enabled = settings.controlCenterEnabled,
        )
        if (settings.advancedControlCenterCorners) {
            SettingsSliderItemWithLabel("快捷图标", settings.controlCenterTileRadius, 0f..64f, { update(settings.copy(controlCenterTileRadius = it)) }, steps = 63, enabled = settings.controlCenterEnabled)
            SettingsSliderItemWithLabel("卡片（含二级菜单）", settings.controlCenterCardRadius, 0f..64f, { update(settings.copy(controlCenterCardRadius = it)) }, steps = 63, enabled = settings.controlCenterEnabled)
            SettingsSliderItemWithLabel("主滑杆", settings.controlCenterSliderRadius, 0f..64f, { update(settings.copy(controlCenterSliderRadius = it)) }, steps = 63, enabled = settings.controlCenterEnabled)
            SettingsSliderItemWithLabel("详情滑杆", settings.controlCenterDetailSliderRadius, 0f..64f, { update(settings.copy(controlCenterDetailSliderRadius = it)) }, steps = 63, enabled = settings.controlCenterEnabled)
            SettingsSliderItemWithLabel("控制中心媒体", settings.controlCenterMediaRadius, 0f..64f, { update(settings.copy(controlCenterMediaRadius = it)) }, steps = 63, enabled = settings.controlCenterEnabled)
            SettingsSliderItemWithLabel("外部入口", settings.controlCenterExternalEntryRadius, 0f..64f, { update(settings.copy(controlCenterExternalEntryRadius = it)) }, steps = 63, enabled = settings.controlCenterEnabled)
        }
    }
    SettingsSection(topLabel = "小米互联服务") {
        SettingsSwitchItem(
            "融合设备中心卡片圆角",
            "",
            settings.miLinkMainCardsEnabled,
            { update(settings.copy(miLinkMainCardsEnabled = it)) },
        )
        SettingsSliderItemWithLabel(
            "圆角大小",
            settings.miLinkMainCardRadius,
            0f..48f,
            { update(settings.copy(miLinkMainCardRadius = it)) },
            steps = 47,
            enabled = settings.miLinkMainCardsEnabled,
        )
    }
    GlobalMaterialBlurEntry(settings, onOpenGlobalMaterialBlur)
}

@Composable
internal fun HeadsUpNotificationsSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onOpenHeadsUpGlassSettings: () -> Unit,
    onOpenGlobalMaterialBlur: () -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "悬浮通知小窗") {
        SettingsSwitchItem(
            "隐藏底部提示横条",
            "",
            settings.hideHeadsUpMiniBar,
            { update(settings.copy(hideHeadsUpMiniBar = it)) },
        )
        SettingsSwitchItem(
            "自定义小窗通知底边距",
            "",
            settings.headsUpBottomMarginEnabled,
            { update(settings.copy(headsUpBottomMarginEnabled = it)) },
        )
        SettingsSliderItemWithLabel(
            label = "通知内容底部边距",
            value = settings.headsUpBottomMarginDp,
            valueRange = 0f..32f,
            onValueChange = { update(settings.copy(headsUpBottomMarginDp = it)) },
            steps = 31,
            enabled = settings.headsUpBottomMarginEnabled,
            valueText = { "${it.toInt()} dp" },
        )
    }
    GlobalMaterialBlurEntry(settings, onOpenGlobalMaterialBlur)
    SettingsSection(topLabel = "悬浮通知柔光玻璃") {
        NavigationSettingItem(
            title = "悬浮通知柔光玻璃",
            summary = "",
            onClick = onOpenHeadsUpGlassSettings,
        )
    }
}

internal data class HeadsUpGlassParameterTarget(val dark: Boolean, val index: Int)
internal enum class HeadsUpGlassPresetDialogMode { Import, Export }

@Composable
internal fun HeadsUpGlassSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenGlobalMaterialBlur: () -> Unit,
    onOpenPresetDialog: (HeadsUpGlassPresetDialogMode) -> Unit,
    scope: GlassParameterScope = GlassParameterScope.HeadsUp,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "使用方式") {
        SettingsSwitchItem(
            label = "启用自定义参数",
            supportingText = "",
            checked = scope.enabled(settings),
            onCheckedChange = { update(scope.setEnabled(settings, it)) },
            enabled = scope != GlassParameterScope.ShadeCards || !settings.disableShadeGlassHooks,
        )
    }
    SettingsSection(topLabel = "预设") {
        val usingModulePreset = scope.enabled(settings) &&
            scope.serialized(settings, false) == HeadsUpGlassParameters.serialize(scope.defaults(false)) &&
            scope.serialized(settings, true) == HeadsUpGlassParameters.serialize(scope.defaults(true))
        val usingSystemPreset = !scope.enabled(settings)
        Text(
            when {
                usingModulePreset -> if (scope == GlassParameterScope.ShadeCards) "当前：原生基准，尚未增加参数偏移。" else "当前柔光参数：模块默认。亮色与暗色分别应用。"
                usingSystemPreset -> if (scope == GlassParameterScope.ShadeCards && settings.disableShadeGlassHooks) "当前：兼容模式，玻璃材质 Hook 已停用。预设操作只保存参数，不会重新启用 Hook。" else "当前：系统默认。自定义参数已保留，可随时重新启用。"
                else -> if (scope == GlassParameterScope.ShadeCards) "当前：自定义。亮暗模式共用参数，原生基准会恢复零偏移，不更改全局模糊。" else "当前：自定义。应用模块默认会重置亮色与暗色参数。"
            },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { update(scope.moduleDefault(settings)) },
                modifier = Modifier.weight(1f),
            ) { Text(if (scope == GlassParameterScope.ShadeCards) "原生基准" else "模块默认") }
            Button(
                onClick = { update(scope.systemDefault(settings)) },
                modifier = Modifier.weight(1f),
            ) { Text("系统默认") }
        }
    }
    GlobalMaterialBlurEntry(settings, onOpenGlobalMaterialBlur)
    SettingsSection(topLabel = "高级设置") {
        NavigationSettingItem(
            title = "高级参数调整",
            summary = "",
            enabled = scope.enabled(settings),
            onClick = onOpenAdvanced,
        )
    }
    SettingsSection(topLabel = "导入与导出") {
        SettingItem(
            headlineText = "导入预设",
            supportingText = "导入后会覆盖当前设置并启用自定义效果",
            onClick = { onOpenPresetDialog(HeadsUpGlassPresetDialogMode.Import) },
        )
        SettingItem(
            headlineText = if (scope == GlassParameterScope.ShadeCards) "导出共用材质参数" else "导出已保存的亮暗参数",
            supportingText = "即使当前为系统默认，也可备份保留的自定义参数",
            onClick = { onOpenPresetDialog(HeadsUpGlassPresetDialogMode.Export) },
        )
    }
}

@Composable
internal fun HeadsUpGlassAdvancedSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    selectedTab: Int,
    scrollState: ScrollState,
    onEditParameter: (dark: Boolean, index: Int) -> Unit,
    scope: GlassParameterScope = GlassParameterScope.HeadsUp,
) = SettingsScrollPage(padding, {}, scrollState) {
    Text(
        if (scope == GlassParameterScope.ShadeCards) {
            "通知/控制中心、亮暗模式共用一套调整。显示值相对原生基准产生偏移，例如混合亮度从 -0.02 改到 0.18，所有卡片增加 0.20；仍保留各卡片原有差异。"
        } else "拖动滑块微调，点击名称输入精确数值。保留项建议保持 0，固定项建议保持 1。",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
    if (selectedTab == 0) {
        HeadsUpGlassSliderList(
            values = scope.values(settings, false),
            enabled = scope.enabled(settings),
            onValueChange = { index, value ->
                val values = scope.values(settings, false)
                values[index] = value
                update(scope.setParameters(settings, false, values))
            },
            onEdit = { onEditParameter(false, it) },
            scope = scope,
        )
    } else {
        HeadsUpGlassSliderList(
            values = scope.values(settings, true),
            enabled = scope.enabled(settings),
            onValueChange = { index, value ->
                val values = scope.values(settings, true)
                values[index] = value
                update(scope.setParameters(settings, true, values))
            },
            onEdit = { onEditParameter(true, it) },
            scope = scope,
        )
    }
}

@Composable
private fun HeadsUpGlassSliderList(
    values: FloatArray,
    enabled: Boolean,
    onValueChange: (Int, Float) -> Unit,
    onEdit: (Int) -> Unit,
    scope: GlassParameterScope,
) {
    HeadsUpGlassParameters.groups.forEach { group ->
        SettingsSection(topLabel = group.title) {
            group.indices.forEach { index ->
                HeadsUpGlassSliderItem(
                    index = index,
                    value = values[index],
                    enabled = enabled,
                    onValueChange = { onValueChange(index, it) },
                    onEdit = { onEdit(index) },
                    scope = scope,
                )
            }
        }
    }
}

@Composable
private fun HeadsUpGlassSliderItem(
    index: Int,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onEdit: () -> Unit,
    scope: GlassParameterScope,
) {
    val parameter = HeadsUpGlassParameters.definitions[index]
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("${index.toString().padStart(2, '0')} · ${parameter.label}", style = MiuixTheme.textStyles.body1)
                Text(parameter.summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1)
                if (scope == GlassParameterScope.ShadeCards) {
                    Text("相对原生偏移：${formatGlassParameter(value - ShadeCardGlassPolicy.defaults()[index])}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                }
            }
            Text(
                formatGlassParameter(value),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.body1,
            )
        }
        DeadlinerSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = parameter.valueRange,
            steps = 0,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
internal fun HeadsUpGlassParameterDialog(
    target: HeadsUpGlassParameterTarget?,
    settings: ModifierSettings,
    onDismissRequest: () -> Unit,
    onSave: (ModifierSettings) -> Unit,
    scope: GlassParameterScope = GlassParameterScope.HeadsUp,
) {
    val currentTarget = target ?: return
    val definition = HeadsUpGlassParameters.definitions[currentTarget.index]
    val source = scope.values(settings, currentTarget.dark)
    var draft by remember(currentTarget, scope, scope.serialized(settings, currentTarget.dark)) {
        mutableStateOf(source[currentTarget.index].toString())
    }
    val parsedValue = draft.trim().toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    DeadlinerMiuixDialog(
        show = true,
        title = "${currentTarget.index.toString().padStart(2, '0')} · ${definition.label}",
        summary = when (currentTarget.index) {
            18 -> "固定项，建议保持 1。"
            in 36..39 -> "保留项，建议保持 0。"
            else -> definition.summary +
                if (scope == GlassParameterScope.ShadeCards) "原生基准：${scope.defaults(currentTarget.dark)[currentTarget.index]}；修改量叠加至各卡片原有参数。" else ""
        },
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiuixTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = "数值",
                singleLine = true,
            )
            if (parsedValue == null) {
                Text(
                    text = "请输入有效的有限数字。",
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton("取消", onDismissRequest, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        source[currentTarget.index] = requireNotNull(parsedValue)
                        onSave(scope.setParameters(settings, currentTarget.dark, source))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = parsedValue != null,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
internal fun HeadsUpGlassPresetDialog(
    mode: HeadsUpGlassPresetDialogMode?,
    settings: ModifierSettings,
    onDismissRequest: () -> Unit,
    onImport: (ModifierSettings) -> Unit,
    scope: GlassParameterScope = GlassParameterScope.HeadsUp,
) {
    val currentMode = mode ?: return
    val context = LocalContext.current
    var json by remember(currentMode, scope, scope.serialized(settings, false), scope.serialized(settings, true)) {
        mutableStateOf(
            if (currentMode == HeadsUpGlassPresetDialogMode.Export) {
                HeadsUpGlassPresetJson.export(settings, scope)
            } else {
                ""
            },
        )
    }
    val imported = if (currentMode == HeadsUpGlassPresetDialogMode.Import && json.isNotBlank()) {
        HeadsUpGlassPresetJson.import(json, scope)
    } else {
        null
    }
    val isImport = currentMode == HeadsUpGlassPresetDialogMode.Import

    DeadlinerMiuixDialog(
        show = true,
        title = if (isImport) "导入 JSON 预设" else "导出 JSON 预设",
        summary = if (isImport) {
            if (scope == GlassParameterScope.ShadeCards) "导入共用材质参数，启用自定义效果；不更改全局模糊。旧版亮暗预设采用亮色参数。" else "导入会覆盖亮色与暗色的设置，并启用自定义效果。"
        } else {
            if (scope == GlassParameterScope.ShadeCards) "复制共用材质参数，亮暗模式使用同一套；全局模糊单独管理。" else "复制已保存的亮色与暗色参数，供备份或在其他设备导入。"
        },
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiuixTextField(
                value = json,
                onValueChange = { if (isImport) json = it },
                modifier = Modifier.fillMaxWidth().height(260.dp),
                label = "JSON",
                readOnly = !isImport,
                singleLine = false,
                maxLines = Int.MAX_VALUE,
            )
            if (isImport && json.isNotBlank() && imported == null) {
                Text(
                    if (scope == GlassParameterScope.ShadeCards) "JSON 格式无效，或参数数量/模糊设置不符合范围。" else "JSON 格式无效，或亮色 / 暗色参数不是 42 个有限数字。",
                    color = MaterialTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton("取消", onDismissRequest, modifier = Modifier.weight(1f))
                if (isImport) {
                    Button(
                        onClick = {
                            val preset = requireNotNull(imported)
                            onImport(scope.importPreset(settings, preset))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = imported != null,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("导入并启用") }
                } else {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText(scope.presetFormat, json))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("复制 JSON") }
                }
            }
        }
    }
}

@Composable
internal fun XiaomiHealthSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "小米运动健康") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "",
            settings.xiaomiHealthFloatingNavigationEnabled,
            { update(settings.copy(xiaomiHealthFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用系统风格图标",
            "",
            settings.xiaomiHealthMiuixIconsEnabled,
            { update(settings.copy(xiaomiHealthMiuixIconsEnabled = it)) },
            enabled = settings.xiaomiHealthFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "使用单色图标",
            "",
            settings.xiaomiHealthMonochromeIconsEnabled,
            { update(settings.copy(xiaomiHealthMonochromeIconsEnabled = it)) },
            enabled = settings.xiaomiHealthFloatingNavigationEnabled &&
                !settings.xiaomiHealthMiuixIconsEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "3.59.1",
        )
    }
}

@Composable
internal fun MarketSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "应用商店") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "",
            settings.marketFloatingNavigationEnabled,
            { update(settings.copy(marketFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用系统风格图标",
            "",
            settings.marketMiuixIconsEnabled,
            { update(settings.copy(marketMiuixIconsEnabled = it)) },
            enabled = settings.marketFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "使用单色图标",
            "",
            settings.marketMonochromeIconsEnabled,
            { update(settings.copy(marketMonochromeIconsEnabled = it)) },
            enabled = settings.marketFloatingNavigationEnabled &&
                !settings.marketMiuixIconsEnabled,
        )
        SettingsSwitchItem(
            "隐藏“游戏” Tab",
            "",
            settings.marketHideGamesTab,
            { update(settings.copy(marketHideGamesTab = it)) },
            enabled = settings.marketFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "隐藏“榜单” Tab",
            "",
            settings.marketHideRankingsTab,
            { update(settings.copy(marketHideRankingsTab = it)) },
            enabled = settings.marketFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "隐藏“我的” Tab",
            "",
            settings.marketHideProfileTab,
            { update(settings.copy(marketHideProfileTab = it)) },
            enabled = settings.marketFloatingNavigationEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "4.125.11",
        )
    }
}

@Composable
internal fun MiHomeSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "米家") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "",
            settings.miHomeFloatingNavigationEnabled,
            { update(settings.copy(miHomeFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用系统风格图标",
            "",
            settings.miHomeMiuixIconsEnabled,
            { update(settings.copy(miHomeMiuixIconsEnabled = it)) },
            enabled = settings.miHomeFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "显示底栏角标",
            "",
            settings.miHomeNavigationBadgesEnabled,
            { update(settings.copy(miHomeNavigationBadgesEnabled = it)) },
            enabled = settings.miHomeFloatingNavigationEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "11.8.605",
        )
    }
}

@Composable
internal fun AmapSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "高德地图") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "",
            settings.amapFloatingNavigationEnabled,
            { update(settings.copy(amapFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用系统风格图标",
            "",
            settings.amapMiuixIconsEnabled,
            { update(settings.copy(amapMiuixIconsEnabled = it)) },
            enabled = settings.amapFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "使用单色图标",
            "",
            settings.amapMonochromeIconsEnabled,
            { update(settings.copy(amapMonochromeIconsEnabled = it)) },
            enabled = settings.amapFloatingNavigationEnabled && !settings.amapMiuixIconsEnabled,
        )
        SettingsSwitchItem(
            "隐藏“长按说话”按钮",
            "“消息”入口不受影响",
            settings.amapHideLongPressVoiceTabEnabled,
            { update(settings.copy(amapHideLongPressVoiceTabEnabled = it)) },
            enabled = settings.amapFloatingNavigationEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前实验版本",
            "17.00.0.2005",
        )
    }
}

@Composable
internal fun XiaomiCommunitySettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "小米社区") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "",
            settings.xiaomiCommunityFloatingNavigationEnabled,
            { update(settings.copy(xiaomiCommunityFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用系统风格图标",
            "",
            settings.xiaomiCommunityMiuixIconsEnabled,
            { update(settings.copy(xiaomiCommunityMiuixIconsEnabled = it)) },
            enabled = settings.xiaomiCommunityFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "使用单色图标",
            "",
            settings.xiaomiCommunityMonochromeIconsEnabled,
            { update(settings.copy(xiaomiCommunityMonochromeIconsEnabled = it)) },
            enabled = settings.xiaomiCommunityFloatingNavigationEnabled &&
                !settings.xiaomiCommunityMiuixIconsEnabled,
        )
        SettingsSwitchItem(
            "显示底栏角标",
            "",
            settings.xiaomiCommunityNavigationBadgesEnabled,
            { update(settings.copy(xiaomiCommunityNavigationBadgesEnabled = it)) },
            enabled = settings.xiaomiCommunityFloatingNavigationEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem("当前适配版本", "6.6.9")
    }
}

@Composable
internal fun SpotifySettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "Spotify") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "悬浮 Tab 上方显示 Capsule 播放组件；修改后强停并重开 Spotify",
            settings.spotifyFloatingNavigationEnabled,
            { update(settings.copy(spotifyFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "系统媒体控制中显示收藏按钮",
            "仅在内容支持收藏时显示；修改后重启 Spotify 和系统界面",
            settings.spotifyFavoriteButtonEnabled,
            { update(settings.copy(spotifyFavoriteButtonEnabled = it)) },
        )
        SettingsSwitchItem(
            "系统媒体控制中显示随机播放按钮",
            "使用 Spotify 原生状态图标；修改后重启 Spotify 和系统界面",
            settings.spotifyShuffleButtonEnabled,
            { update(settings.copy(spotifyShuffleButtonEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "9.1.84.2231（布局适配待实机确认）",
        )
    }
}
