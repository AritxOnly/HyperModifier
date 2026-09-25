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
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
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
            "分别调整各区域的圆角",
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
    SettingsSection(topLabel = "全局背景模糊") {
        SettingsSliderItemWithLabel(
            label = "全局背景模糊",
            value = settings.globalBackgroundBlurPercent,
            valueRange = 0f..200f,
            onValueChange = { update(settings.copy(globalBackgroundBlurPercent = it)) },
            steps = 199,
            valueText = { "${it.toInt()}%" },
        )
        Text(
            "影响通知中心、控制中心和融合设备中心的背景。100% 为系统原有模糊强度；重新打开相关界面后生效。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun HeadsUpNotificationsSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onOpenHeadsUpGlassSettings: () -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "悬浮通知小窗") {
        SettingsSwitchItem(
            "隐藏底部提示横条",
            "保留下滑打开小窗的手势",
            settings.hideHeadsUpMiniBar,
            { update(settings.copy(hideHeadsUpMiniBar = it)) },
        )
        SettingsSwitchItem(
            "自定义底部边距",
            "适用于可下滑打开小窗的通知",
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
    onOpenPresetDialog: (HeadsUpGlassPresetDialogMode) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "使用方式") {
        SettingsSwitchItem(
            label = "启用自定义参数",
            supportingText = "",
            checked = settings.headsUpGlassParametersEnabled,
            onCheckedChange = { update(settings.copy(headsUpGlassParametersEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "预设") {
        val usingModulePreset = settings.headsUpGlassParametersEnabled &&
            settings.headsUpGlassParameters == HeadsUpGlassParameters.regularSerialized &&
            settings.headsUpGlassDarkParameters == HeadsUpGlassParameters.darkSerialized
        val usingSystemPreset = !settings.headsUpGlassParametersEnabled
        Text(
            when {
                usingModulePreset -> "当前柔光参数：模块默认。亮色与暗色分别应用。"
                usingSystemPreset -> "当前：系统默认。自定义参数已保留，可随时重新启用。"
                else -> "当前：自定义。应用模块默认会重置亮色与暗色参数。"
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
                onClick = { update(ModifierSettingsPresets.headsUpGlassModuleDefault(settings)) },
                modifier = Modifier.weight(1f),
            ) { Text("模块默认") }
            Button(
                onClick = { update(ModifierSettingsPresets.headsUpGlassSystemDefault(settings)) },
                modifier = Modifier.weight(1f),
            ) { Text("系统默认") }
        }
    }
    SettingsSection(topLabel = "高级设置") {
        NavigationSettingItem(
            title = "高级参数调整",
            summary = "",
            enabled = settings.headsUpGlassParametersEnabled,
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
            headlineText = "导出已保存的亮暗参数",
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
) = SettingsScrollPage(padding, {}, scrollState) {
    Text(
        "拖动滑块微调，点击名称输入精确数值。保留项建议保持 0，固定项建议保持 1。",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
    if (selectedTab == 0) {
        HeadsUpGlassSliderList(
            values = HeadsUpGlassParameters.parseSerializedOrDefault(
                settings.headsUpGlassParameters,
                HeadsUpGlassParameters.regularDefault,
            ),
            enabled = settings.headsUpGlassParametersEnabled,
            onValueChange = { index, value ->
                val values = HeadsUpGlassParameters.parseSerializedOrDefault(
                    settings.headsUpGlassParameters,
                    HeadsUpGlassParameters.regularDefault,
                )
                values[index] = value
                update(settings.copy(headsUpGlassParameters = HeadsUpGlassParameters.serialize(values)))
            },
            onEdit = { onEditParameter(false, it) },
        )
    } else {
        HeadsUpGlassSliderList(
            values = HeadsUpGlassParameters.parseSerializedOrDefault(
                settings.headsUpGlassDarkParameters,
                HeadsUpGlassParameters.darkDefault,
            ),
            enabled = settings.headsUpGlassParametersEnabled,
            onValueChange = { index, value ->
                val values = HeadsUpGlassParameters.parseSerializedOrDefault(
                    settings.headsUpGlassDarkParameters,
                    HeadsUpGlassParameters.darkDefault,
                )
                values[index] = value
                update(settings.copy(headsUpGlassDarkParameters = HeadsUpGlassParameters.serialize(values)))
            },
            onEdit = { onEditParameter(true, it) },
        )
    }
}

@Composable
private fun HeadsUpGlassSliderList(
    values: FloatArray,
    enabled: Boolean,
    onValueChange: (Int, Float) -> Unit,
    onEdit: (Int) -> Unit,
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
) {
    val parameter = HeadsUpGlassParameters.definitions[index]
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("${index.toString().padStart(2, '0')} · ${parameter.label}", style = MiuixTheme.textStyles.body1)
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
) {
    val currentTarget = target ?: return
    val definition = HeadsUpGlassParameters.definitions[currentTarget.index]
    val source = if (currentTarget.dark) {
        HeadsUpGlassParameters.parseSerializedOrDefault(
            settings.headsUpGlassDarkParameters,
            HeadsUpGlassParameters.darkDefault,
        )
    } else {
        HeadsUpGlassParameters.parseSerializedOrDefault(
            settings.headsUpGlassParameters,
            HeadsUpGlassParameters.regularDefault,
        )
    }
    var draft by remember(currentTarget, settings.headsUpGlassParameters, settings.headsUpGlassDarkParameters) {
        mutableStateOf(source[currentTarget.index].toString())
    }
    val parsedValue = draft.trim().toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

    DeadlinerMiuixDialog(
        show = true,
        title = "${currentTarget.index.toString().padStart(2, '0')} · ${definition.label}",
        summary = when (currentTarget.index) {
            18 -> "固定项，建议保持 1。"
            in 36..39 -> "保留项，建议保持 0。"
            else -> "调整${if (currentTarget.dark) "暗色" else "亮色"}效果的${definition.label}。"
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
                Button(
                    onClick = {
                        source[currentTarget.index] = requireNotNull(parsedValue)
                        onSave(
                            if (currentTarget.dark) {
                                settings.copy(
                                    headsUpGlassDarkParameters = HeadsUpGlassParameters.serialize(source),
                                )
                            } else {
                                settings.copy(
                                    headsUpGlassParameters = HeadsUpGlassParameters.serialize(source),
                                )
                            },
                        )
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
) {
    val currentMode = mode ?: return
    val context = LocalContext.current
    var json by remember(currentMode, settings.headsUpGlassParameters, settings.headsUpGlassDarkParameters) {
        mutableStateOf(
            if (currentMode == HeadsUpGlassPresetDialogMode.Export) {
                HeadsUpGlassPresetJson.export(settings)
            } else {
                ""
            },
        )
    }
    val imported = if (currentMode == HeadsUpGlassPresetDialogMode.Import && json.isNotBlank()) {
        HeadsUpGlassPresetJson.import(json)
    } else {
        null
    }
    val isImport = currentMode == HeadsUpGlassPresetDialogMode.Import

    DeadlinerMiuixDialog(
        show = true,
        title = if (isImport) "导入 JSON 预设" else "导出 JSON 预设",
        summary = if (isImport) {
            "导入会覆盖亮色与暗色的设置，并启用自定义效果。"
        } else {
            "复制已保存的亮色与暗色参数，供备份或在其他设备导入。"
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
                    "JSON 格式无效，或亮色 / 暗色参数不是 42 个有限数字。",
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
                            onImport(
                                settings.copy(
                                    headsUpGlassParametersEnabled = true,
                                    headsUpBackgroundBlurRadiusEnabled = false,
                                    headsUpGlassParameters = HeadsUpGlassParameters.serialize(preset.regular),
                                    headsUpGlassDarkParameters = HeadsUpGlassParameters.serialize(preset.dark),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = imported != null,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("导入并启用") }
                } else {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Heads-up glass preset", json))
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
            "",
            settings.spotifyFloatingNavigationEnabled,
            { update(settings.copy(spotifyFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "系统媒体控制中显示收藏按钮",
            "",
            settings.spotifyFavoriteButtonEnabled,
            { update(settings.copy(spotifyFavoriteButtonEnabled = it)) },
        )
        SettingsSwitchItem(
            "系统媒体控制中显示随机播放按钮",
            "",
            settings.spotifyShuffleButtonEnabled,
            { update(settings.copy(spotifyShuffleButtonEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "9.1.80.2221",
        )
    }
}
