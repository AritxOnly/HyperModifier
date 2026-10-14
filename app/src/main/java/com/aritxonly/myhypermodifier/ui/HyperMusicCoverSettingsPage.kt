package com.aritxonly.myhypermodifier

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HyperMusicCoverSettingsPage(padding: PaddingValues, settings: ModifierSettings,
    update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(HmcAvailabilityState.Checking) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { state = HmcAvailabilityState.Checking; refresh++ }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) { state = loadHmcAvailability(context) }
    val available = android.os.Build.VERSION.SDK_INT >= 34 &&
        (state == HmcAvailabilityState.Ready || settings.hmcForceEnable)
    SettingsScrollPage(padding, onScroll) {
        SettingsSection {
            SettingItem("状态", state.summary)
            SettingsSwitchItem(
                "强制启用",
                "忽略包名检测，解锁扩展设置",
                settings.hmcForceEnable,
                { update(settings.copy(hmcForceEnable = it)) },
                enabled = android.os.Build.VERSION.SDK_INT >= 34,
            )
        }
        SettingsSection {
            SettingsSwitchItem(
                "绕过 HyperMusicCover 时钟避让",
                "恢复原生通知避让，保留音乐封面模式",
                settings.bypassHyperMusicCoverClockAdjustment,
                { update(settings.copy(bypassHyperMusicCoverClockAdjustment = it)) },
                enabled = available,
            )

            SettingsSwitchItem(
                "全屏岛展开时下沉通知并隐藏指纹",
                "展开全部卡片时恢复避让；首次启用请重启系统界面",
                settings.hmcExpandedFingerprint,
                { update(settings.copy(hmcExpandedFingerprint = it)) },
                enabled = available,
            )
            SettingsSwitchItem(
                "自定义封面时钟字体高度",
                "需 AllInOne 字体；首次启用请重启系统界面",
                settings.hmcClockFontHeightEnabled,
                { update(settings.copy(hmcClockFontHeightEnabled = it)) },
                enabled = available,
            )
            HyperMusicCoverFontHeightSetting(
                value = settings.hmcClockFontHeight,
                onValueChange = { update(settings.copy(hmcClockFontHeight = it)) },
                enabled = settings.hmcClockFontHeightEnabled && !settings.hmcClockFontRatioEnabled && available,
            )
            SettingsSwitchItem(
                "按宽高比调整字体",
                "联动原生字体宽度、高度和字号；关闭后恢复上面的高度设置",
                settings.hmcClockFontRatioEnabled,
                { update(settings.copy(hmcClockFontRatioEnabled = it)) },
                enabled = settings.hmcClockFontHeightEnabled && available,
            )
            HyperMusicCoverFontHeightSetting(
                value = settings.hmcClockFontRatio,
                onValueChange = { update(settings.copy(hmcClockFontRatio = it)) },
                enabled = settings.hmcClockFontHeightEnabled && settings.hmcClockFontRatioEnabled && available,
                label = "字形宽高比",
                defaultValue = 2.4f,
                summary = "每组数字的宽／高，越大越扁；默认 2.4",
                inputSummary = "以两位数字 00 的轮廓为基准，保持数字宽度并调整高度。输入大于 0 的数值，超出范围时采用字体可达的最接近形态。",
                positiveOnly = true,
            )
        }
    }
}

internal enum class HmcAvailabilityState(val summary: String) {
    Checking("正在检测 HyperMusicCover"),
    Unsupported("需要 Android 14 或更新版本"),
    Missing("未安装 HyperMusicCover"),
    Ready("已安装 HyperMusicCover"),
}

private suspend fun loadHmcAvailability(context: Context): HmcAvailabilityState {
    if (android.os.Build.VERSION.SDK_INT < 34) return HmcAvailabilityState.Unsupported
    val installed = withContext(Dispatchers.IO) {
        // Official releases use a GitHub application ID; older builds used the Java namespace.
        listOf("com.github.zyl6932.HyperMusicCover", "com.os4.musiccover").any { packageName ->
            try { context.packageManager.getApplicationInfo(packageName, 0); true }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
        }
    }
    return if (installed) HmcAvailabilityState.Ready else HmcAvailabilityState.Missing
}
