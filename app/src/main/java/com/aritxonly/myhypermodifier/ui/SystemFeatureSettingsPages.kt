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

@Composable internal fun MediaSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onOpenConstraintEditor: () -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "锁屏媒体") {
        SettingsSwitchItem("自定义媒体组件大小", "", settings.mediaEnabled, { update(settings.copy(mediaEnabled = it)) })
        SettingsSliderItemWithLabel("展开高度", settings.expandedHeight, 120f..200f, { update(settings.copy(expandedHeight = it)) }, steps = 19, enabled = settings.mediaEnabled)
        SettingsSliderItemWithLabel("息屏收起高度", settings.collapsedHeight, 80f..160f, { update(settings.copy(collapsedHeight = it)) }, steps = 19, enabled = settings.mediaEnabled)
        SettingsSliderItemWithLabel("息屏常显高度", settings.fullAodHeight, 56f..120f, { update(settings.copy(fullAodHeight = it)) }, steps = 15, enabled = settings.mediaEnabled)
        NavigationSettingItem("高级布局编辑", "", enabled = settings.mediaEnabled, onClick = onOpenConstraintEditor)
        SettingsSwitchItem("息屏时隐藏操作按钮", "", settings.hideAodActions, { update(settings.copy(hideAodActions = it)) })
        SettingsSwitchItem("息屏时隐藏设备切换", "", settings.hideAodSeamless, { update(settings.copy(hideAodSeamless = it)) })
    }
    SettingsSection(topLabel = "超级岛媒体") {
        SettingsSwitchItem("自定义超级岛高度", "", settings.islandEnabled, { update(settings.copy(islandEnabled = it)) })
        SettingsSliderItemWithLabel("超级岛高度", settings.islandHeight, 96f..200f, { update(settings.copy(islandHeight = it)) }, steps = 25, enabled = settings.islandEnabled)
        SettingsSwitchItem(
            "进度条光效",
            "普通媒体组件也使用超级岛的进度条样式",
            settings.islandProgressBar,
            { update(settings.copy(islandProgressBar = it)) },
            enabled = settings.islandEnabled,
        )
    }
}

@Composable internal fun LockscreenSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "锁屏时钟") {
        SettingsSwitchItem(
            "强制显示时钟冒号",
            "24 小时制下也显示时钟冒号",
            settings.forceLockscreenClockColon,
            { update(settings.copy(forceLockscreenClockColon = it)) },
        )
    }
    SettingsSection(topLabel = "息屏时钟") {
        SettingsSwitchItem(
            "自定义息屏时钟字重",
            "",
            settings.aodClockWeightEnabled,
            { update(settings.copy(aodClockWeightEnabled = it)) },
        )
        SettingsSliderItemWithLabel(
            label = "息屏时钟字重",
            value = settings.aodClockWeight,
            valueRange = 100f..700f,
            onValueChange = { update(settings.copy(aodClockWeight = it)) },
            steps = 23,
            valueText = { it.toInt().toString() },
            enabled = settings.aodClockWeightEnabled,
        )
    }
    SettingsSection(topLabel = "锁屏通知") {
        SettingsSwitchItem(
            "锁屏通知下沉",
            "开启后，锁屏通知将下沉到指纹图标下方",
            settings.sinkLockscreenNotificationsForFingerprint,
            { update(settings.copy(sinkLockscreenNotificationsForFingerprint = it)) },
        )
    }
    SettingsSection(topLabel = "锁屏指纹") {
        SettingsSwitchItem(
            "隐藏锁屏指纹图标",
            "保留指纹解锁功能",
            settings.hideLockscreenFingerprintIcon,
            { update(settings.copy(hideLockscreenFingerprintIcon = it)) },
        )
        SettingsSwitchItem(
            "息屏时显示指纹图标",
            "",
            settings.showLockscreenFingerprintIconOnAod,
            { update(settings.copy(showLockscreenFingerprintIconOnAod = it)) },
            enabled = settings.hideLockscreenFingerprintIcon,
        )
    }
    SettingsSection(topLabel = "密码输入界面") {
        SettingsSwitchItem(
            "密码页下沉与指纹切换",
            "密码页与指纹页可通过底部按钮或滑动切换",
            settings.lowerLockscreenPasswordPage,
            { update(settings.copy(lowerLockscreenPasswordPage = it)) },
        )
        SettingsSwitchItem(
            "数字按钮柔光玻璃",
            "",
            settings.lockscreenPinKeySoftGlassEnabled,
            { update(settings.copy(lockscreenPinKeySoftGlassEnabled = it)) },
        )
        SettingsSliderItemWithLabel(
            label = "按钮额外半径",
            value = settings.lockscreenPinKeyGlassExtraRadius,
            valueRange = 0f..16f,
            onValueChange = {
                update(settings.copy(lockscreenPinKeyGlassExtraRadius = it))
            },
            steps = 15,
            valueText = { "${it.toInt()} dp" },
            enabled = settings.lockscreenPinKeySoftGlassEnabled,
        )
        SettingsSliderItemWithLabel(
            label = "按钮上下间距",
            value = settings.lockscreenPinKeyGlassVerticalGap,
            valueRange = 0f..32f,
            onValueChange = {
                update(settings.copy(lockscreenPinKeyGlassVerticalGap = it))
            },
            steps = 31,
            valueText = { "${it.toInt()} dp" },
            enabled = settings.lockscreenPinKeySoftGlassEnabled,
        )
    }
}

@Composable internal fun StatusBarSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "状态栏网络类型") {
        SettingsSwitchItem(
            "独立 4G/5G 标识",
            "",
            settings.statusBarNetworkTypeEnabled,
            { update(settings.copy(statusBarNetworkTypeEnabled = it)) },
        )
        if (settings.statusBarNetworkTypeEnabled) {
            SettingsSliderItemWithLabel(
                "文字大小",
                settings.statusBarNetworkTypeSize,
                10f..20f,
                { update(settings.copy(statusBarNetworkTypeSize = it)) },
                steps = 19,
                valueText = { "${settingNumber(it)} sp" },
            )
            SettingsSwitchItem(
                "加粗文字",
                "",
                settings.statusBarNetworkTypeBold,
                { update(settings.copy(statusBarNetworkTypeBold = it)) },
            )
            SettingsSliderItemWithLabel(
                "水平偏移",
                settings.statusBarNetworkTypeOffset,
                -24f..24f,
                { update(settings.copy(statusBarNetworkTypeOffset = it)) },
                steps = 47,
                valueText = { "${settingNumber(it)} dp" },
            )
        }
    }
}

@Composable internal fun VolumeSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "音量键弹出面板") {
        SettingsSliderItemWithLabel(
            "外层圆角",
            settings.volumePanelRadius,
            0f..64f,
            { update(settings.copy(volumePanelRadius = it)) },
            steps = 63,
            valueText = { if (it == 0f) "系统默认" else "${settingNumber(it)} dp" },
        )
    }
}

@Composable
internal fun MediaConstraintSetSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    var xml by remember(settings.customMediaConstraintSetXml) {
        mutableStateOf(settings.customMediaConstraintSetXml.ifBlank { MEDIA_CONSTRAINT_SET_TEMPLATE })
    }
    MediaConstraintSetPreview(
        xml = xml,
        defaultCardHeight = settings.expandedHeight,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    SettingsSection(topLabel = "编辑布局") {
        SettingsSwitchItem(
            "使用自定义 XML",
            "适用于普通锁屏媒体组件",
            settings.customMediaConstraintSetEnabled,
            { update(settings.copy(customMediaConstraintSetEnabled = it, customMediaConstraintSetXml = xml)) },
        )
        Text("支持 Constraint / android:layout_* / app:layout_constraint* 属性。未写出的属性保持系统原值。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        TextField(
            value = xml,
            onValueChange = { xml = it },
            modifier = Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 16.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            singleLine = false,
            maxLines = Int.MAX_VALUE,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            "恢复模块默认",
            { update(settings.copy(customMediaConstraintSetEnabled = false, customMediaConstraintSetXml = "")) },
            modifier = Modifier.weight(1f),
        )
        Button(
            { update(settings.copy(customMediaConstraintSetEnabled = true, customMediaConstraintSetXml = xml)) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("保存并启用") }
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

@Composable
internal fun AboutSettingsPage(padding: PaddingValues, onScroll: (Float) -> Unit) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val scroll = rememberScrollState()
    var checkingForUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    val progress by remember(scroll) { derivedStateOf { (scroll.value / 320f).coerceIn(0f, 1f) } }
    LaunchedEffect(progress) { onScroll(progress) }
    fun checkForUpdate() {
        if (checkingForUpdate) return
        checkingForUpdate = true
        Thread {
            val result = UpdateChecker.check()
            Handler(Looper.getMainLooper()).post {
                checkingForUpdate = false
                updateResult = result
            }
        }.start()
    }
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
                SettingItem(
                    "检查更新",
                    if (checkingForUpdate) "正在检查 GitHub Release…" else "检查 GitHub 上的最新稳定版本",
                    enabled = !checkingForUpdate,
                    onClick = ::checkForUpdate,
                )
            }
            SettingsSection(topLabel = "致谢", containerAlpha = 0.5f + progress * 0.5f) {
                SettingItem("HyperBlackScreen", "功能参考与适配贡献 · 酷安@不愧是小睦")
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
        updateResult?.let { result ->
            UpdateCheckDialog(
                result = result,
                onDismiss = { updateResult = null },
                onOpenRelease = { url ->
                    updateResult = null
                    context.openWebPage(url)
                },
            )
        }
    }
}

@Composable
private fun UpdateCheckDialog(
    result: UpdateCheckResult,
    onDismiss: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val title = when (result) {
        is UpdateCheckResult.Available -> "发现新版本"
        UpdateCheckResult.Latest -> "已是最新版本"
        UpdateCheckResult.Unavailable -> "暂时无法检查更新"
    }
    val summary = when (result) {
        is UpdateCheckResult.Available -> "HyperModifier v${result.versionName} 已发布。"
        UpdateCheckResult.Latest -> "当前使用的是 v${BuildConfig.VERSION_NAME}。"
        UpdateCheckResult.Unavailable -> "请检查网络连接，或稍后在 GitHub Release 页面重试。"
    }
    DeadlinerMiuixDialog(true, title, summary, onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton("关闭", onDismiss, modifier = Modifier.weight(1f))
            if (result is UpdateCheckResult.Available) {
                Button(
                    onClick = { onOpenRelease(result.releaseUrl) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("查看发布") }
            }
        }
    }
}

@Composable internal fun SettingsScrollPage(
    padding: PaddingValues,
    onScroll: (Float) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    val progress by remember(scrollState) { derivedStateOf { (scrollState.value / 32f).coerceIn(0f, 1f) } }
    LaunchedEffect(progress) { onScroll(progress) }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(padding).padding(horizontal = 16.dp).padding(bottom = 82.dp)) { content() }
}

@Composable private fun AboutAppHeader() = Column(Modifier.fillMaxWidth().height(404.dp).padding(top = 180.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Card(cornerRadius = 24.dp) { Image(painter = painterResource(R.drawable.ic_launcher_mhm), contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Fit) }
    Text("HyperModifier", style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("v${BuildConfig.VERSION_NAME}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

private fun settingNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

internal fun formatGlassParameter(value: Float): String =
    String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }

internal fun Context.openDetailSettings(destination: SettingsDestination) {
    if (destination.isTopLevel) return
    val intent = Intent(this, DetailSettingsActivity::class.java)
        .putExtra(DetailSettingsActivity.EXTRA_DESTINATION, destination.key)
    startActivity(intent)
}

@Composable
internal fun RestartScopeDialogContent(
    state: ScopeRestartState,
    restartSystemUi: Boolean,
    restartPlugin: Boolean,
    restartMiLink: Boolean,
    restartXiaomiHealth: Boolean,
    restartMarket: Boolean,
    restartMiHome: Boolean,
    restartAmap: Boolean,
    restartXiaomiCommunity: Boolean,
    restartSpotify: Boolean,
    restartSystem: Boolean,
    onSystemUiChange: (Boolean) -> Unit,
    onPluginChange: (Boolean) -> Unit,
    onMiLinkChange: (Boolean) -> Unit,
    onXiaomiHealthChange: (Boolean) -> Unit,
    onMarketChange: (Boolean) -> Unit,
    onMiHomeChange: (Boolean) -> Unit,
    onAmapChange: (Boolean) -> Unit,
    onXiaomiCommunityChange: (Boolean) -> Unit,
    onSpotifyChange: (Boolean) -> Unit,
    onSystemChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val choicesScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 328.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(choicesScroll),
        ) {
            RestartScopeChoices(
                state = state,
                restartSystemUi = restartSystemUi,
                restartPlugin = restartPlugin,
                restartMiLink = restartMiLink,
                restartXiaomiHealth = restartXiaomiHealth,
                restartMarket = restartMarket,
                restartMiHome = restartMiHome,
                restartAmap = restartAmap,
                restartXiaomiCommunity = restartXiaomiCommunity,
                restartSpotify = restartSpotify,
                restartSystem = restartSystem,
                onSystemUiChange = onSystemUiChange,
                onPluginChange = onPluginChange,
                onMiLinkChange = onMiLinkChange,
                onXiaomiHealthChange = onXiaomiHealthChange,
                onMarketChange = onMarketChange,
                onMiHomeChange = onMiHomeChange,
                onAmapChange = onAmapChange,
                onXiaomiCommunityChange = onXiaomiCommunityChange,
                onSpotifyChange = onSpotifyChange,
                onSystemChange = onSystemChange,
            )
        }
        DialogActions(state, onDismiss, onConfirm)
    }
}

@Composable private fun RestartScopeChoices(
    state: ScopeRestartState,
    restartSystemUi: Boolean,
    restartPlugin: Boolean,
    restartMiLink: Boolean,
    restartXiaomiHealth: Boolean,
    restartMarket: Boolean,
    restartMiHome: Boolean,
    restartAmap: Boolean,
    restartXiaomiCommunity: Boolean,
    restartSpotify: Boolean,
    restartSystem: Boolean,
    onSystemUiChange: (Boolean) -> Unit,
    onPluginChange: (Boolean) -> Unit,
    onMiLinkChange: (Boolean) -> Unit,
    onXiaomiHealthChange: (Boolean) -> Unit,
    onMarketChange: (Boolean) -> Unit,
    onMiHomeChange: (Boolean) -> Unit,
    onAmapChange: (Boolean) -> Unit,
    onXiaomiCommunityChange: (Boolean) -> Unit,
    onSpotifyChange: (Boolean) -> Unit,
    onSystemChange: (Boolean) -> Unit,
) = Column(Modifier.padding(top = 8.dp)) {
    val editable = state == ScopeRestartState.Ready
    SettingItem(
        "系统界面",
        "",
        enabled = editable && !restartSystem,
        trailingContent = { DeadlinerCheckbox(restartSystemUi, onSystemUiChange, enabled = editable && !restartSystem) },
    )
    SettingItem(
        "控制中心组件",
        "",
        enabled = editable && !restartSystem,
        trailingContent = { DeadlinerCheckbox(restartPlugin, onPluginChange, enabled = editable && !restartSystem) },
    )
    SettingItem(
        "小米互联服务",
        "",
        enabled = editable && !restartSystem,
        trailingContent = { DeadlinerCheckbox(restartMiLink, onMiLinkChange, enabled = editable && !restartSystem) },
    )
    SettingItem(
        "小米运动健康",
        "",
        enabled = editable && !restartSystem,
        trailingContent = {
            DeadlinerCheckbox(
                restartXiaomiHealth,
                onXiaomiHealthChange,
                enabled = editable && !restartSystem,
            )
        },
    )
    SettingItem(
        "应用商店",
        "",
        enabled = editable && !restartSystem,
        trailingContent = {
            DeadlinerCheckbox(
                restartMarket,
                onMarketChange,
                enabled = editable && !restartSystem,
            )
        },
    )
    SettingItem(
        "米家",
        "",
        enabled = editable && !restartSystem,
        trailingContent = {
            DeadlinerCheckbox(
                restartMiHome,
                onMiHomeChange,
                enabled = editable && !restartSystem,
            )
        },
    )
    SettingItem(
        "高德地图",
        "",
        enabled = editable && !restartSystem,
        trailingContent = {
            DeadlinerCheckbox(
                restartAmap,
                onAmapChange,
                enabled = editable && !restartSystem,
            )
        },
    )
    SettingItem(
        "小米社区",
        "",
        enabled = editable && !restartSystem,
        trailingContent = {
            DeadlinerCheckbox(
                restartXiaomiCommunity,
                onXiaomiCommunityChange,
                enabled = editable && !restartSystem,
            )
        },
    )
    SettingsSectionDivider()
    SettingItem(
        "重启系统",
        "完整重启设备",
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
internal fun restartSummary(state: ScopeRestartState) = when (state) { ScopeRestartState.Ready -> "选择要重新启动的界面或应用。"; ScopeRestartState.Restarting -> "正在重启…"; ScopeRestartState.Succeeded -> "重启完成。"; ScopeRestartState.Failed -> "重启失败，请检查授权。" }
internal fun restartSelectedScope(
    systemUi: Boolean,
    plugin: Boolean,
    miLink: Boolean,
    xiaomiHealth: Boolean,
    market: Boolean,
    miHome: Boolean,
    amap: Boolean,
    xiaomiCommunity: Boolean,
    spotify: Boolean,
    system: Boolean,
    onCompleted: (Boolean) -> Unit,
) {
    Thread {
        // `am force-stop` merely marks the package stopped, and it does not recreate the control
        // centre plugin views.  Killing the SystemUI host lets system_server immediately start it
        // again and rebuilds both MIUISystemUI and MIUISystemUIPlugin content.
        val command = when {
            system -> "reboot"
            systemUi || plugin || miLink || xiaomiHealth || market || miHome || amap ||
                xiaomiCommunity || spotify -> buildList {
                if (systemUi || plugin) add("killall com.android.systemui")
                if (miLink) add("am force-stop com.milink.service")
                if (xiaomiHealth) add("am force-stop com.mi.health")
                if (market) add("am force-stop com.xiaomi.market")
                if (miHome) add("am force-stop com.xiaomi.smarthome")
                if (amap) add("am force-stop com.autonavi.minimap")
                if (xiaomiCommunity) add("am force-stop com.xiaomi.vipaccount")
                if (spotify) add("am force-stop com.spotify.music")
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
