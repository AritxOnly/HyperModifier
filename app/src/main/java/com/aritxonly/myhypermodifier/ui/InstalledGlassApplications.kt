package com.aritxonly.myhypermodifier

import android.content.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

private data class GlassApplication(val category: String, val name: String, val packageName: String, val destination: SettingsDestination)
private val glassApplications = listOf(
    GlassApplication("健康与生活", "小米运动健康", ModuleScopePackage.XIAOMI_HEALTH, SettingsDestination.XiaomiHealth),
    GlassApplication("健康与生活", "米家", ModuleScopePackage.MI_HOME, SettingsDestination.MiHome),
    GlassApplication("健康与生活", "高德地图", ModuleScopePackage.AMAP, SettingsDestination.Amap),
    GlassApplication("健康与生活", "蔚来汽车", ModuleScopePackage.NIO, SettingsDestination.Nio),
    GlassApplication("健康与生活", "支付宝", ModuleScopePackage.ALIPAY, SettingsDestination.Alipay),
    GlassApplication("音乐与视频", "小米音乐", ModuleScopePackage.MI_MUSIC, SettingsDestination.Music),
    GlassApplication("音乐与视频", "Spotify", ModuleScopePackage.SPOTIFY, SettingsDestination.Spotify),
    GlassApplication("音乐与视频", "哔哩哔哩", ModuleScopePackage.BILIBILI, SettingsDestination.Bilibili),
    GlassApplication("音乐与视频", "QQ 音乐", ModuleScopePackage.QQ_MUSIC, SettingsDestination.QQMusic),
    GlassApplication("音乐与视频", "抖音", ModuleScopePackage.DOUYIN, SettingsDestination.Douyin),
    GlassApplication("音乐与视频", "抖音极速版", ModuleScopePackage.DOUYIN_LITE, SettingsDestination.DouyinLite),
    GlassApplication("音乐与视频", "快手", ModuleScopePackage.KUAISHOU, SettingsDestination.Kuaishou),
    GlassApplication("音乐与视频", "快手极速版", ModuleScopePackage.KUAISHOU_LITE, SettingsDestination.KuaishouLite),
    GlassApplication("社交与社区", "微信", ModuleScopePackage.WECHAT, SettingsDestination.Wechat),
    GlassApplication("社交与社区", "QQ", ModuleScopePackage.QQ, SettingsDestination.QQ),
    GlassApplication("社交与社区", "小米社区", ModuleScopePackage.XIAOMI_COMMUNITY, SettingsDestination.XiaomiCommunity),
    GlassApplication("购物与服务", "拼多多", ModuleScopePackage.PINDUODUO, SettingsDestination.Pinduoduo),
    GlassApplication("购物与服务", "淘宝", ModuleScopePackage.TAOBAO, SettingsDestination.Taobao),
    GlassApplication("购物与服务", "京东", ModuleScopePackage.JINGDONG, SettingsDestination.Jingdong),
    GlassApplication("购物与服务", "闲鱼", ModuleScopePackage.XIANYU, SettingsDestination.Xianyu),
    GlassApplication("购物与服务", "抖音商城", ModuleScopePackage.DOUYIN_MALL, SettingsDestination.DouyinMall),
    GlassApplication("购物与服务", "菜鸟", ModuleScopePackage.CAINIAO, SettingsDestination.Cainiao),
    GlassApplication("系统应用", "应用商店", ModuleScopePackage.MARKET, SettingsDestination.Market),
)

/** Query only supported packages on IO, then refresh on package changes; never during composition. */
@Composable
internal fun rememberInstalledGlassPackages(): Set<String>? {
    val context = LocalContext.current.applicationContext
    var installed by remember { mutableStateOf<Set<String>?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        var pending: Job? = null
        fun refresh() {
            pending?.cancel()
            pending = scope.launch {
                installed = withContext(Dispatchers.IO) {
                    glassApplications.mapNotNull { item ->
                        try {
                            context.packageManager.getApplicationInfo(item.packageName, 0)
                            item.packageName
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
                    }.toSet()
                }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (glassApplications.any { it.packageName == intent.data?.schemeSpecificPart }) refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
        onDispose { pending?.cancel(); context.unregisterReceiver(receiver) }
    }
    return installed
}

@Composable
internal fun InstalledGlassApplications(onNavigate: (SettingsDestination) -> Unit) {
    val installed = rememberInstalledGlassPackages()
    val context = LocalContext.current
    val groups = remember(installed) {
        glassApplications.filter { installed?.contains(it.packageName) == true }.groupBy { it.category }
    }
    if (installed == null || groups.isEmpty()) SettingsSection {
        SettingItem(if (installed == null) "正在读取已安装应用…" else "尚未安装受支持的应用", "")
    }
    groups.forEach { (category, apps) ->
        SettingsSection(topLabel = category) {
            apps.forEach { app ->
                NavigationSettingItem(app.name, "", onClick = { onNavigate(app.destination) },
                    leadingContent = { HomeAppIcon(app.packageName) })
            }
        }
    }
    SettingsSection(topLabel = "更多有完整柔光玻璃的 APP") {
        NavigationSettingItem("F1Info", "", onClick = { context.openWebPage("https://github.com/AritxOnly/F1Info") })
        NavigationSettingItem("HyperPiliPlus", "", onClick = { context.openWebPage("https://github.com/AritxOnly/Hyper-PiliPlus") })
    }
}
