package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import com.aritxonly.deadliner.ui.material.glass.SoftGlassSurface
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.navigation.floatingNavigationShadow
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialBackdrop
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import com.kyant.shapes.Capsule
import java.util.concurrent.TimeUnit
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.graphics.ColorUtils

private enum class SettingsDestination(val key: String, val label: String) {
    Home("home", "主页"), About("about", "关于"),
}
private enum class ScopeRestartState { Ready, Restarting, Succeeded, Failed }

@Composable
fun MyHyperModifierSettingsApp() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    var settings by remember { mutableStateOf(ModifierSettingsStore.load(context)) }
    var destination by remember { mutableStateOf(SettingsDestination.Home) }
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    var restartState by remember { mutableStateOf(ScopeRestartState.Ready) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartSystemUi by remember { mutableStateOf(true) }
    var restartPlugin by remember { mutableStateOf(true) }
    var restartMiLink by remember { mutableStateOf(false) }
    var restartSystem by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showMediaConstraintSetSheet by remember { mutableStateOf(false) }
    val homeTopBarScrollBehavior = if (destination == SettingsDestination.Home) MiuixScrollBehavior() else null
    fun update(value: ModifierSettings) { settings = value; ModifierSettingsStore.save(context, value) }

    MiuixTheme(colors = miuixColors) {
        MaterialTheme(colorScheme = materialColors) {
            DeadlinerMiuixScaffold(
                scrollProgress = scrollProgress,
                topBar = when (destination) {
                    SettingsDestination.Home -> homeTopBarScrollBehavior?.let { scrollBehavior ->
                        {
                            TopAppBar(
                                title = "主页",
                                largeTitle = "主页",
                                color = Color.Transparent,
                                titleColor = MiuixTheme.colorScheme.onSurface,
                                largeTitleColor = MiuixTheme.colorScheme.onSurface,
                                scrollBehavior = scrollBehavior,
                            )
                        }
                    }
                    // No title on About, but retain a sized material host for its scroll blur.
                    SettingsDestination.About -> { { Spacer(Modifier.fillMaxWidth().height(88.dp)) } }
                },
                modifier = homeTopBarScrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier,
                bottomBar = {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixFloatingTabBar(
                            items = floatingItems(), selectedKey = destination.key,
                            onItemSelected = { selected ->
                                destination = SettingsDestination.entries.first { it.key == selected.key }
                                scrollProgress = 0f
                            },
                            layout = MiuixFloatingTabLayout.Stacked,
                        )
                        Spacer(Modifier.width(12.dp))
                        RestartScopeGlassButton {
                            restartState = ScopeRestartState.Ready
                            showRestartDialog = true
                        }
                    }
                },
                overlay = {
                    DeadlinerMiuixDialog(showRestartDialog, "重启作用域", restartSummary(restartState), {
                        if (restartState != ScopeRestartState.Restarting) showRestartDialog = false
                    }) {
                        RestartScopeChoices(
                            state = restartState,
                            restartSystemUi = restartSystemUi,
                            restartPlugin = restartPlugin,
                            restartMiLink = restartMiLink,
                            restartSystem = restartSystem,
                            onSystemUiChange = { restartSystemUi = it },
                            onPluginChange = {
                                restartPlugin = it
                                // SystemUIPlugin is hosted by SystemUI; reloading only its package
                                // leaves the currently inflated control-centre views untouched.
                                if (it) restartSystemUi = true
                            },
                            onMiLinkChange = { restartMiLink = it },
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartPlugin = false
                                    restartMiLink = false
                                }
                            },
                        )
                        DialogActions(restartState, { showRestartDialog = false }) {
                            restartState = ScopeRestartState.Restarting
                            restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartSystem) {
                                restartState = if (it) ScopeRestartState.Succeeded else ScopeRestartState.Failed
                            }
                        }
                    }
                    DeadlinerMiuixBottomSheet(showMediaSheet, "媒体组件自定义", { showMediaSheet = false }) {
                        MediaCustomizationSheet(settings, ::update) { showMediaConstraintSetSheet = true }
                    }
                    DeadlinerMiuixBottomSheet(showMediaConstraintSetSheet, "媒体 ConstraintSet XML", { showMediaConstraintSetSheet = false }) {
                        MediaConstraintSetXmlSheet(settings, ::update)
                    }
                },
            ) { padding ->
                AnimatedContent(destination, label = "settings-page") { page ->
                    when (page) {
                        SettingsDestination.Home -> HomeSettingsPage(padding, settings, ::update, { scrollProgress = it }) { showMediaSheet = true }
                        SettingsDestination.About -> AboutSettingsPage(padding) { scrollProgress = it }
                    }
                }
            }
        }
    }
}

@Composable private fun floatingItems(): List<MiuixFloatingTabItem> {
    val home = rememberVectorPainter(MiuixIcons.Settings); val about = rememberVectorPainter(MiuixIcons.Contacts)
    return listOf(MiuixFloatingTabItem("home", "主页", home, home), MiuixFloatingTabItem("about", "关于", about, about))
}

@Composable private fun HomeSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit, onMediaSheet: () -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "通知中心") {
        SettingsSwitchItem("通知圆角", "notification_item_bg_radius · ${settings.notificationRadius.toInt()} dp", settings.notificationsEnabled, { update(settings.copy(notificationsEnabled = it)) })
        SettingsSectionDivider()
        SettingsSliderItemWithLabel("圆角大小", settings.notificationRadius, 12f..48f, { update(settings.copy(notificationRadius = it)) }, steps = 17, enabled = settings.notificationsEnabled)
    }
    SettingsSection(topLabel = "控制中心") {
        SettingsSwitchItem("控制中心圆角", "control_center_universal_corner_radius · ${settings.controlCenterRadius.toInt()} dp", settings.controlCenterEnabled, { update(settings.copy(controlCenterEnabled = it)) })
        SettingsSliderItemWithLabel("圆角大小", settings.controlCenterRadius, 12f..48f, { update(settings.copy(controlCenterRadius = it)) }, steps = 17, enabled = settings.controlCenterEnabled)
    }
    SettingsSection(topLabel = "控制中心高级修改") {
        SettingsSwitchItem(
            "独立圆角",
            "分别覆盖所有使用 universal_corner_radius 的界面",
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
            "CirculateWorldActivity · ${settings.miLinkMainCardRadius.toInt()} dp",
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
    SettingsSection(topLabel = "通知中心 / 锁屏媒体") {
        SettingsSwitchItem("启用媒体组件修改", "展开 ${settings.expandedHeight.toInt()} dp · 收起 ${settings.collapsedHeight.toInt()} dp", settings.mediaEnabled, { update(settings.copy(mediaEnabled = it)) })
        SettingsSectionDivider(); SettingItem("自定义尺寸", "展开、息屏和 Full AOD 高度", onClick = onMediaSheet); SettingsSectionDivider()
        SettingsSwitchItem("AOD 隐藏操作按钮", "退出 Full AOD 后自动恢复 action0–action4", settings.hideAodActions, { update(settings.copy(hideAodActions = it)) })
        SettingsSwitchItem("AOD 隐藏设备切换", "隐藏 media_seamless；退出 Full AOD 后自动恢复", settings.hideAodSeamless, { update(settings.copy(hideAodSeamless = it)) })
    }
    SettingsSection(topLabel = "灵动岛媒体") {
        SettingsSwitchItem("启用灵动岛高度", "expanded_island_height_dp · ${settings.islandHeight.toInt()} dp", settings.islandEnabled, { update(settings.copy(islandEnabled = it)) })
        SettingsSliderItemWithLabel("灵动岛高度", settings.islandHeight, 96f..200f, { update(settings.copy(islandHeight = it)) }, steps = 25, enabled = settings.islandEnabled)
        SettingsSectionDivider(); SettingsSwitchItem("Island 柔光进度条", "普通媒体组件使用灵动岛 SeekProgressBar 光效", settings.islandProgressBar, { update(settings.copy(islandProgressBar = it)) })
    }
}

@Composable private fun MediaCustomizationSheet(settings: ModifierSettings, update: (ModifierSettings) -> Unit, onEditConstraintSet: () -> Unit) = Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
    HeightSlider("展开高度", settings.expandedHeight, 120f..200f) { update(settings.copy(expandedHeight = it)) }
    HeightSlider("息屏收起高度", settings.collapsedHeight, 80f..160f) { update(settings.copy(collapsedHeight = it)) }
    HeightSlider("Full AOD 高度", settings.fullAodHeight, 56f..120f) { update(settings.copy(fullAodHeight = it)) }
    SettingsSectionDivider()
    SettingItem("编辑 ConstraintSet XML", "直接编辑 media_session 的布局约束", onClick = onEditConstraintSet)
    Spacer(Modifier.navigationBarsPadding())
}

@Composable
private fun MediaConstraintSetXmlSheet(settings: ModifierSettings, update: (ModifierSettings) -> Unit) {
    var xml by remember(settings.customMediaConstraintSetXml) {
        mutableStateOf(settings.customMediaConstraintSetXml.ifBlank { MEDIA_CONSTRAINT_SET_TEMPLATE })
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        SettingsSwitchItem(
            "使用自定义 XML",
            "仅覆盖普通 media_session；错误 XML 会安全回退模块默认布局",
            settings.customMediaConstraintSetEnabled,
            { update(settings.copy(customMediaConstraintSetEnabled = it, customMediaConstraintSetXml = xml)) },
        )
        Spacer(Modifier.height(12.dp))
        Text("支持 Constraint / android:layout_* / app:layout_constraint* 属性。未写出的属性保持系统原值。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = xml,
            onValueChange = { xml = it },
            modifier = Modifier.fillMaxWidth().height(360.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            singleLine = false,
            maxLines = Int.MAX_VALUE,
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("恢复模块默认", { update(settings.copy(customMediaConstraintSetEnabled = false, customMediaConstraintSetXml = "")) }, modifier = Modifier.weight(1f))
            Button(
                { update(settings.copy(customMediaConstraintSetEnabled = true, customMediaConstraintSetXml = xml)) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("保存并启用") }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

private val MEDIA_CONSTRAINT_SET_TEMPLATE = """
    <ConstraintSet xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto">
      <Constraint android:id="@id/media_bg" android:layout_width="0dp" android:layout_height="@dimen/qs_media_session_height_expanded" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toTopOf="parent" />
      <Constraint android:id="@id/album_art" android:layout_width="@dimen/album_art_width" android:layout_height="@dimen/album_art_width" android:layout_marginTop="@dimen/media_margin_left" android:layout_marginStart="@dimen/media_margin_left" app:layout_constraintEnd_toStartOf="@id/header_title" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toTopOf="parent" />
      <Constraint android:id="@id/media_seamless" android:layout_width="@dimen/media_control_seamless" android:layout_height="@dimen/media_control_seamless" android:layout_marginTop="18dp" android:layout_marginEnd="12dp" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintTop_toTopOf="parent" />
      <Constraint android:id="@id/header_title" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_marginTop="18dp" android:layout_marginStart="@dimen/album_art_margin_right" android:layout_marginEnd="@dimen/media_header_title_margin_end" app:layout_constraintEnd_toStartOf="@id/media_seamless" app:layout_constraintHorizontal_bias="0.0" app:layout_constraintStart_toEndOf="@id/album_art" app:layout_constraintTop_toTopOf="parent" />
      <Constraint android:id="@id/header_artist" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_marginTop="@dimen/header_artist_margin_top" android:layout_marginStart="@dimen/album_art_margin_right" android:layout_marginEnd="@dimen/media_header_title_margin_end" app:layout_constraintEnd_toStartOf="@id/media_seamless" app:layout_constraintHorizontal_bias="0.0" app:layout_constraintStart_toEndOf="@id/album_art" app:layout_constraintTop_toBottomOf="@id/header_title" />
      <Constraint android:id="@id/media_elapsed_time" android:layout_width="@dimen/media_time_width" android:layout_height="wrap_content" android:layout_marginStart="8dp" app:layout_constraintBottom_toBottomOf="@id/media_progress_bar" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toTopOf="@id/media_progress_bar" />
      <Constraint android:id="@id/media_progress_bar" android:layout_width="0dp" android:layout_height="@dimen/media_hyper_seekbar_height" android:layout_marginTop="@dimen/media_progressbar_margin_top" android:layout_marginStart="46dp" android:layout_marginEnd="46dp" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/album_art" />
      <Constraint android:id="@id/media_total_time" android:layout_width="@dimen/media_time_width" android:layout_height="wrap_content" android:layout_marginEnd="8dp" app:layout_constraintBottom_toBottomOf="@id/media_progress_bar" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintTop_toTopOf="@id/media_progress_bar" />
      <Constraint android:id="@id/actions" android:layout_width="fill_parent" android:layout_height="wrap_content" android:layout_marginTop="14dp" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintTop_toBottomOf="@id/media_progress_bar" />
      <Constraint android:id="@id/action0" android:layout_width="@dimen/media_action_width" android:layout_height="@dimen/media_action_height" app:layout_constraintBottom_toBottomOf="@id/actions" app:layout_constraintLeft_toLeftOf="@id/actions" app:layout_constraintRight_toLeftOf="@id/action1" app:layout_constraintTop_toTopOf="@id/actions" />
      <Constraint android:id="@id/action1" android:layout_width="@dimen/media_action_width" android:layout_height="@dimen/media_action_height" android:layout_marginEnd="5dp" app:layout_constraintBottom_toBottomOf="@id/action0" app:layout_constraintLeft_toRightOf="@id/action0" app:layout_constraintRight_toLeftOf="@id/action2" app:layout_constraintTop_toTopOf="@id/action0" />
      <Constraint android:id="@id/action2" android:layout_width="@dimen/media_action_width" android:layout_height="@dimen/media_action_height" app:layout_constraintBottom_toBottomOf="@id/action0" app:layout_constraintLeft_toRightOf="@id/action1" app:layout_constraintRight_toLeftOf="@id/action3" app:layout_constraintTop_toTopOf="@id/action0" />
      <Constraint android:id="@id/action3" android:layout_width="@dimen/media_action_width" android:layout_height="@dimen/media_action_height" android:layout_marginStart="5dp" app:layout_constraintBottom_toBottomOf="@id/action0" app:layout_constraintLeft_toRightOf="@id/action2" app:layout_constraintRight_toLeftOf="@id/action4" app:layout_constraintTop_toTopOf="@id/action0" />
      <Constraint android:id="@id/action4" android:layout_width="@dimen/media_action_width" android:layout_height="@dimen/media_action_height" app:layout_constraintBottom_toBottomOf="@id/action0" app:layout_constraintLeft_toRightOf="@id/action3" app:layout_constraintRight_toRightOf="@id/actions" app:layout_constraintTop_toTopOf="@id/action0" />
    </ConstraintSet>
""".trimIndent()

@Composable private fun HeightSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) =
    SettingsSliderItemWithLabel(label, value, range, onChange, steps = ((range.endInclusive - range.start) / 4).toInt() - 1)

@Composable
private fun AboutSettingsPage(padding: PaddingValues, onScroll: (Float) -> Unit) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scroll = rememberScrollState()
    val progress by remember(scroll) { derivedStateOf { (scroll.value / 320f).coerceIn(0f, 1f) } }
    LaunchedEffect(progress) { onScroll(progress) }
    Box(Modifier.fillMaxSize()) {
        AboutFloatingBackground(Modifier.fillMaxSize(), alpha = 1f - progress)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                // The titleless blur layer overlays this blank header area; do not let its
                // measured height move the requested 180dp icon offset.
                .padding(
                    start = padding.calculateLeftPadding(layoutDirection),
                    end = padding.calculateRightPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding(),
                )
                .padding(horizontal = 16.dp)
                .padding(bottom = 82.dp),
        ) {
            AboutAppHeader()
            SettingsSection(topLabel = "版本信息", containerAlpha = 0.5f + progress * 0.5f) {
                SettingItem("版本号", "v${BuildConfig.VERSION_NAME}")
                SettingItem("LSPosed API", "API 102")
            }
            SettingsSection(topLabel = "作用范围", containerAlpha = 0.5f + progress * 0.5f) {
                SettingItem("MIUISystemUI", "com.android.systemui")
                SettingItem("MIUISystemUIPlugin", "miui.systemui.plugin")
            }
            SettingsSection(topLabel = "关于", containerAlpha = 0.5f + progress * 0.5f) {
                SettingItem("作者", "AritxOnly · GitHub", onClick = { context.openWebPage("https://github.com/AritxOnly") })
                SettingItem("设计参考：Deadliner", "AritxOnly 开发 · 点击查看项目", onClick = { context.openWebPage("https://github.com/AritxOnly/Deadliner") })
            }
            SettingsSection(topLabel = "使用的库", containerAlpha = 0.5f + progress * 0.5f) {
                SettingItem("LSPosed API", "模块运行时 API 102")
                SettingItem("MIUIX Compose", "HyperOS 风格组件、图标与模糊效果")
                SettingItem("Jetpack Compose", "Android 声明式界面")
                SettingItem("Material Kolor", "预设色与 HyperOS 背景配色")
            }
        }
    }
}

@Composable private fun SettingsScrollPage(padding: PaddingValues, onScroll: (Float) -> Unit, content: @Composable () -> Unit) {
    val scroll = rememberScrollState(); val progress by remember(scroll) { derivedStateOf { (scroll.value / 32f).coerceIn(0f, 1f) } }; LaunchedEffect(progress) { onScroll(progress) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding).padding(horizontal = 16.dp).padding(bottom = 82.dp)) { content() }
}

@Composable private fun AboutAppHeader() = Column(Modifier.fillMaxWidth().height(404.dp).padding(top = 180.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Card(cornerRadius = 24.dp) { Image(painter = painterResource(R.drawable.ic_launcher_mhm), contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Fit) }
    Text("MyHyperModifier", style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("v${BuildConfig.VERSION_NAME}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

/** Deadliner's standalone soft-glass action capsule beside the compact floating tab bar. */
@Composable private fun RestartScopeGlassButton(onClick: () -> Unit) {
    val icon = rememberVectorPainter(MiuixIcons.Refresh)
    val advancedMaterial = LocalAdvancedMaterialSpec.current
    SoftGlassSurface(
        modifier = Modifier
            .size(54.dp)
            .floatingNavigationShadow(
                shape = Capsule(),
                isDark = MiuixTheme.colorScheme.onSurface.luminance() > 0.5f,
            ),
        shape = Capsule(),
        recipe = DeadlinerGlassRecipes.floatingNavigation(advancedMaterial.fineTuning),
        backdrop = LocalAdvancedMaterialBackdrop.current,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = "重启作用域",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable private fun RestartScopeChoices(
    state: ScopeRestartState,
    restartSystemUi: Boolean,
    restartPlugin: Boolean,
    restartMiLink: Boolean,
    restartSystem: Boolean,
    onSystemUiChange: (Boolean) -> Unit,
    onPluginChange: (Boolean) -> Unit,
    onMiLinkChange: (Boolean) -> Unit,
    onSystemChange: (Boolean) -> Unit,
) = Column(Modifier.padding(top = 8.dp)) {
    val editable = state == ScopeRestartState.Ready
    SettingItem(
        "MIUISystemUI",
        "停止 com.android.systemui 后自动重新拉起",
        enabled = editable && !restartSystem,
        trailingContent = { DeadlinerCheckbox(restartSystemUi, onSystemUiChange, enabled = editable && !restartSystem) },
    )
    SettingItem(
        "MIUISystemUIPlugin",
        "由 MIUISystemUI 宿主进程重启后重新加载",
        enabled = editable && !restartSystem,
        trailingContent = { DeadlinerCheckbox(restartPlugin, onPluginChange, enabled = editable && !restartSystem) },
    )
    SettingItem(
        "小米互联服务",
        "停止 com.milink.service；再次进入其设置页时会重建卡片",
        enabled = editable && !restartSystem,
        trailingContent = { DeadlinerCheckbox(restartMiLink, onMiLinkChange, enabled = editable && !restartSystem) },
    )
    SettingsSectionDivider()
    SettingItem(
        "重启系统",
        "完整重启设备；勾选后不会单独停止作用域",
        enabled = editable,
        trailingContent = { DeadlinerCheckbox(restartSystem, onSystemChange, enabled = editable) },
    )
}

@Composable private fun DialogActions(state: ScopeRestartState, onDismiss: () -> Unit, onConfirm: () -> Unit) = Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (state == ScopeRestartState.Ready) {
        TextButton("取消", onDismiss, modifier = Modifier.weight(1f))
        Button(onConfirm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text("立即重启") }
    } else {
        TextButton("关闭", onDismiss, modifier = Modifier.fillMaxWidth(), enabled = state != ScopeRestartState.Restarting)
    }
}
private fun restartSummary(state: ScopeRestartState) = when (state) { ScopeRestartState.Ready -> "选择需要重新加载的模块作用域，或改为完整重启系统。"; ScopeRestartState.Restarting -> "正在请求 root 权限并执行重启…"; ScopeRestartState.Succeeded -> "重启请求已完成，已加载保存的自定义。"; ScopeRestartState.Failed -> "重启失败：请确认 root 授权与 LSPosed 作用域配置。" }
private fun restartSelectedScope(systemUi: Boolean, plugin: Boolean, miLink: Boolean, system: Boolean, onCompleted: (Boolean) -> Unit) {
    Thread {
        // `am force-stop` merely marks the package stopped, and it does not recreate the control
        // centre plugin views.  Killing the SystemUI host lets system_server immediately start it
        // again and rebuilds both MIUISystemUI and MIUISystemUIPlugin content.
        val command = when {
            system -> "reboot"
            systemUi || plugin || miLink -> buildList {
                if (systemUi || plugin) add("killall com.android.systemui")
                if (miLink) add("am force-stop com.milink.service")
            }.joinToString("; ")
            else -> ""
        }
        val completed = try {
            if (command.isBlank()) false else {
                val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                val finished = process.waitFor(12, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                finished && process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
        Handler(Looper.getMainLooper()).post { onCompleted(completed) }
    }.start()
}

private fun Context.openWebPage(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

/** Directly adapted from Deadliner's AboutSettings floating-glow backdrop. */
@Composable
private fun AboutFloatingBackground(modifier: Modifier = Modifier, alpha: Float) {
    val transition = rememberInfiniteTransition(label = "aboutFloatingBackground")
    val horizontalOffset = transition.animateFloat(-0.12f, 0.12f, infiniteRepeatable(tween(7_500, easing = LinearEasing), RepeatMode.Reverse), label = "aboutBackgroundHorizontalOffset")
    val verticalOffset = transition.animateFloat(0.08f, -0.08f, infiniteRepeatable(tween(5_600, easing = LinearEasing), RepeatMode.Reverse), label = "aboutBackgroundVerticalOffset")
    val accentOffset = transition.animateFloat(-0.08f, 0.1f, infiniteRepeatable(tween(6_400, easing = LinearEasing), RepeatMode.Reverse), label = "aboutBackgroundAccentOffset")
    val primary = MaterialTheme.colorScheme.primary.toVividGlowColor()
    val secondary = MaterialTheme.colorScheme.secondary.toVividGlowColor()
    val tertiary = MaterialTheme.colorScheme.tertiary.toVividGlowColor()
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        drawRect(surface)
        if (alpha <= 0f) return@Canvas
        val radius = maxOf(size.width, size.height) * 0.9f
        fun center(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        val first = center(0.18f + horizontalOffset.value, 0.22f + verticalOffset.value)
        val second = center(0.86f - horizontalOffset.value, 0.66f - verticalOffset.value)
        val third = center(0.52f + accentOffset.value, 0.96f - accentOffset.value)
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.22f * alpha), Color.Transparent), first, radius), radius, first)
        drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha = 0.18f * alpha), Color.Transparent), second, radius), radius * 0.86f, second)
        drawCircle(Brush.radialGradient(listOf(tertiary.copy(alpha = 0.16f * alpha), Color.Transparent), third, radius * 0.7f), radius * 0.72f, third)
    }
}

private fun Color.toVividGlowColor(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.55f).coerceIn(0.56f, 0.92f)
    hsl[2] = if (hsl[2] < 0.5f) (hsl[2] + 0.14f).coerceAtMost(0.68f) else (hsl[2] - 0.06f).coerceAtLeast(0.36f)
    return Color(ColorUtils.HSLToColor(hsl))
}
