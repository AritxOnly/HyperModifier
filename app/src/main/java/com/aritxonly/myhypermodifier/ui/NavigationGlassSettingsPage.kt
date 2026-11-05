package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.aritxonly.deadliner.model.AdvancedMaterialTuning
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import kotlin.math.roundToInt

internal object SettingsItemDefaults { val HorizontalPadding = 24.dp }
@Composable internal fun SettingsHeroSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    SettingsSection(modifier = modifier, content = content)

@Composable
internal fun NavigationGlassSettingsPage(
    padding: PaddingValues, settings: ModifierSettings,
    update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit,
) {
    val saved = remember(settings.navigationGlassParameters) { NavigationGlassParameters.decode(settings.navigationGlassParameters) }
    var preview by remember(saved) { mutableStateOf(saved) }
    var lift by remember(settings.hyperGlassifyHiddenNavigationLift) { mutableFloatStateOf(settings.hyperGlassifyHiddenNavigationLift) }
    var geometry by remember(settings.navigationBarGeometry) {
        mutableStateOf(NavigationBarGeometryParameters.decode(settings.navigationBarGeometry))
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun commit(value: NavigationGlassParameters = preview, liftValue: Float = lift) {
        preview = value.normalized()
        update(settings.copy(navigationGlassParameters = preview.encode(),
            hyperGlassifyHiddenNavigationLift = liftValue.coerceIn(0f, 48f)))
    }
    fun commitGeometry(value: NavigationBarGeometryParameters = geometry) {
        geometry = value.normalized()
        update(settings.copy(navigationBarGeometry = geometry.encode()))
    }
    fun updateActiveGeometry(value: NavigationBarGeometry) {
        geometry = geometry.withActiveGeometry(value).normalized()
    }
    fun normalizedWidth(value: Float): Float = if (value < 80f) 0f
        else ((value / 5f).roundToInt() * 5).coerceIn(160, 600).toFloat()
    fun normalizedHeight(value: Float): Float = if (value < 18f) 0f
        else ((value / 2f).roundToInt() * 2).coerceIn(36, 80).toFloat()
    SettingsScrollPage(padding, onScroll) {
        SettingsSection(topLabel = "模糊度") {
            NavigationMaterialRadiusSlider(value = preview.blurRadius, enabled = true,
                onValueChange = { preview = preview.copy(blurRadius = it) },
                onValueChangeFinished = { commit() })
        }
        AdvancedMaterialPreviewCard(preview.blurRadius, preview.fineTuning, true, bottomLiftDp = lift, useOfficialMiuixGlass = false)
        SettingsSection(topLabel = "底栏位置") {
            SettingItem("底栏额外抬高", "${lift.toInt()} dp · 在系统导航栏间距之外增加")
            Slider(value = lift, valueRange = 0f..48f, steps = 47,
                onValueChange = { lift = it }, onValueChangeFinished = { commit() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
        }
        SettingsSection(topLabel = "底栏尺寸") {
            val active = geometry.activeGeometry()
            SettingsSwitchItem(
                label = "同步至所有底栏",
                supportingText = if (geometry.syncAll) {
                    "当前设置会统一应用到所有悬浮底栏"
                } else {
                    "关闭后只调节所选底栏，其他底栏保持原尺寸"
                },
                checked = geometry.syncAll,
                onCheckedChange = { commitGeometry(geometry.copy(syncAll = it)) },
            )
            if (!geometry.syncAll) {
                val selectedIndex = NavigationBarGeometryTargets.all.indexOfFirst {
                    it.key == geometry.selectedTarget
                }.coerceAtLeast(0)
                WindowDropdownPreference(
                    title = "调节对象",
                    summary = "逐底栏保存左侧边距、宽度和高度",
                    items = NavigationBarGeometryTargets.all.map { it.label },
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = {
                        commitGeometry(
                            geometry.copy(
                                selectedTarget = NavigationBarGeometryTargets.all[it].key,
                            ),
                        )
                    },
                )
            }
            SettingItem(
                "横向左边距",
                "${active.leftMarginDp.roundToInt()} dp · 在现有对齐位置右侧增加",
            )
            Slider(
                value = active.leftMarginDp,
                valueRange = 0f..160f,
                steps = 159,
                onValueChange = {
                    updateActiveGeometry(active.copy(leftMarginDp = it.roundToInt().toFloat()))
                },
                onValueChangeFinished = { commitGeometry() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SettingItem(
                "底栏宽度",
                if (active.maximumWidthDp == 0f) "自动 · 跟随当前底栏规则"
                else "${active.maximumWidthDp.roundToInt()} dp",
            )
            Slider(
                value = active.maximumWidthDp,
                valueRange = 0f..600f,
                steps = 119,
                onValueChange = {
                    updateActiveGeometry(
                        active.copy(maximumWidthDp = normalizedWidth(it)),
                    )
                },
                onValueChangeFinished = { commitGeometry() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SettingItem(
                "底栏高度",
                if (active.heightDp == 0f) "自动 · 跟随当前底栏规则"
                else "${active.heightDp.roundToInt()} dp",
            )
            Slider(
                value = active.heightDp,
                valueRange = 0f..80f,
                steps = 40,
                onValueChange = {
                    updateActiveGeometry(active.copy(heightDp = normalizedHeight(it)))
                },
                onValueChangeFinished = { commitGeometry() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        SettingsSection(topLabel = "高级参数调节") {
            AdvancedMaterialFineTuningPanel(
                value = preview.fineTuning,
                onValueChange = { preview = preview.copy(fineTuning = it.normalized()) },
                onValueChangeFinished = { commit() },
                onImmediateChange = { commit(preview.copy(fineTuning = it)) },
                onExport = {
                    val json = org.json.JSONObject().put("format", "myhypermodifier-navigation-glass")
                        .put("version", 1).put("parameters", preview.encode()).put("liftDp", lift).toString(2)
                    clipboard.setText(AnnotatedString(json))
                    Toast.makeText(context, "参数已复制", Toast.LENGTH_SHORT).show()
                },
                onReset = {
                    lift = ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT
                    commit(NavigationGlassParameters.Default, lift)
                },
            )
        }
        SettingsSection(topLabel = "开源参考") {
            SettingItem("AndroidLiquidGlass · Apache-2.0", "Shader 设计参考 · 查看开源项目", onClick = {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/Kyant0/AndroidLiquidGlass")))
            })
        }
    }
}
