package com.aritxonly.myhypermodifier

import kotlinx.coroutines.launch
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Build
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalDensity
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
import com.aritxonly.deadliner.ui.base.TabRow
import com.aritxonly.deadliner.ui.base.rememberTopBarMaterialScrollObserver
import com.aritxonly.myhypermodifier.adaptive.MiuixFloatingTabBar
import com.aritxonly.myhypermodifier.adaptive.isWideSettingsLayout
import com.aritxonly.myhypermodifier.adaptive.LocalDockScrollFraction
import com.aritxonly.myhypermodifier.adaptive.MiuixFloatingTabBarDefaults
import com.aritxonly.myhypermodifier.adaptive.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.floatingNavigationShadow
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialBackdrop
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import com.kyant.shapes.Capsule
import java.util.concurrent.TimeUnit
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import com.aritxonly.myhypermodifier.adaptive.AdaptiveSmallTopAppBar as SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import com.aritxonly.myhypermodifier.adaptive.AdaptiveMiuixTopAppBar as TopAppBar
import com.aritxonly.myhypermodifier.adaptive.AdaptiveMiuixScrollBehavior as MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class SettingsDestination(val key: String, val label: String) {
    Home("home", "主页"),
    Donation("donation", "捐赠"),
    Compatibility("compatibility", "兼容与恢复"),
    NotificationsControlCenter("notifications-control-center", "通知/控制中心"),
    GlobalMaterialBlur("global-material-blur", "全局玻璃材质与模糊"),
    UnifiedGlass("unified-glass", "统一玻璃材质"),
    HeadsUpNotifications("heads-up-notifications", "悬浮通知"),
    HeadsUpGlass("heads-up-glass", "悬浮通知柔光玻璃"),
    HeadsUpGlassAdvanced("heads-up-glass-advanced", "高级参数调整"),
    ShadeCardGlass("shade-card-glass", "通知/控制中心卡片柔光玻璃"),
    ShadeCardGlassAdvanced("shade-card-glass-advanced", "高级参数调整"),
    NavigationGlass("navigation-glass", "底栏柔光玻璃参数调节"),
    Ecommerce("ecommerce-navigation", "电商应用"),
    XiaomiHealth("xiaomi-health", "小米运动健康"),
    Music("mi-music", "小米音乐手机版"),
    Market("market", "应用商店"),
    MiHome("mi-home", "米家"),
    Amap("amap", "高德地图"),
    Nio("nio", "蔚来汽车"),
    Alipay("alipay", "支付宝"),
    Douyin("douyin", "抖音"),
    Kuaishou("kuaishou", "快手"),
    Social("social", "微信 / QQ"),
    QQMusic("qq-music", "QQ音乐"),
    XiaomiCommunity("xiaomi-community", "小米社区"),
    Bilibili("bilibili", "哔哩哔哩"),
    DouyinLite("douyin-lite", "抖音极速版"),
    KuaishouLite("kuaishou-lite", "快手极速版"),
    Wechat("app-wechat", "微信"),
    QQ("app-qq", "QQ"),
    Pinduoduo("app-pinduoduo", "拼多多"),
    Taobao("app-taobao", "淘宝"),
    Jingdong("app-jingdong", "京东"),
    Xianyu("app-xianyu", "闲鱼"),
    DouyinMall("app-douyin_mall", "抖音商城"),
    Cainiao("app-cainiao", "菜鸟"),
    Spotify("spotify", "Spotify"),
    Media("media", "超级岛与媒体组件"),
    MediaConstraintSet("media-constraint-set", "高级布局编辑"),
    Lockscreen("lockscreen", "锁屏"),
    HyperMusicCover("hyper-music-cover", "HyperMusicCover 扩展"),
    Foldables("foldables", "折叠屏专区"),
    Desktop("desktop", "系统桌面"),
    WidgetGlassApps("widget-glass-apps", "小组件材质"),
    StatusBar("status-bar", "状态栏"),
    AboutPhone("about-phone", "系统设置"),
    PhoneImage("phone-image", "手机型号图片"),
    HomeEntries("home-entries", "设置主页入口"),
    KeyShortcuts("key-shortcuts", "快捷键"),
    Volume("volume", "音量面板"),
    GestureHandle("gesture-handle", "手势提示线"),
    About("about", "关于"),
    ;

    val isTopLevel: Boolean get() = this == Home || this == About
    val supportsRestore: Boolean get() = this != About && this != Donation && this != NavigationGlass

    companion object {
        fun fromKey(key: String?): SettingsDestination? = entries.firstOrNull {
            it.key == key
        }
    }
}
internal enum class ScopeRestartState { Ready, Restarting, Succeeded, Failed }

private data class RestartScopeDefaults(
    val systemUi: Boolean = false,
    val launcher: Boolean = false,
    val plugin: Boolean = false,
    val miLink: Boolean = false,
    val xiaomiHealth: Boolean = false,
    val music: Boolean = false,
    val market: Boolean = false,
    val miHome: Boolean = false,
    val amap: Boolean = false,
    val xiaomiCommunity: Boolean = false,
    val bilibili: Boolean = false,
    val nio: Boolean = false,
    val piliPlus: Boolean = false,
    val alipay: Boolean = false,
    val douyin: Boolean = false,
    val kuaishou: Boolean = false,
    val socialApps: Boolean = false,
    val qqMusic: Boolean = false,
    val ecommerceApps: Boolean = false,
    val spotify: Boolean = false,
    val systemSettings: Boolean = false,
)

private fun SettingsDestination.requiredRestartScopes(): RestartScopeDefaults = when (this) {
    SettingsDestination.Compatibility -> RestartScopeDefaults(
        systemUi = true, launcher = true, plugin = true, miLink = true, xiaomiHealth = true,
        nio = true, music = true, market = true, miHome = true, amap = true, xiaomiCommunity = true,
        bilibili = true, douyin = true, kuaishou = true,
        alipay = true, socialApps = true, qqMusic = true,
        ecommerceApps = true,
        spotify = true, systemSettings = true,
    )
    SettingsDestination.NotificationsControlCenter,
    SettingsDestination.UnifiedGlass,
    SettingsDestination.GlobalMaterialBlur -> RestartScopeDefaults(systemUi = true, plugin = true, miLink = true)
    SettingsDestination.ShadeCardGlass,
    SettingsDestination.ShadeCardGlassAdvanced -> RestartScopeDefaults(systemUi = true, plugin = true)
    SettingsDestination.NavigationGlass -> RestartScopeDefaults(
        xiaomiHealth = true, nio = true, music = true, market = true, miHome = true, amap = true,
        bilibili = true, spotify = true, douyin = true,
        kuaishou = true, alipay = true, socialApps = true, qqMusic = true,
        xiaomiCommunity = true, ecommerceApps = true,
    )
    SettingsDestination.Ecommerce -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.XiaomiHealth -> RestartScopeDefaults(xiaomiHealth = true)
    SettingsDestination.Music -> RestartScopeDefaults(music = true)
    SettingsDestination.Market -> RestartScopeDefaults(market = true)
    SettingsDestination.MiHome -> RestartScopeDefaults(miHome = true)
    SettingsDestination.Amap -> RestartScopeDefaults(amap = true)
    SettingsDestination.XiaomiCommunity -> RestartScopeDefaults(xiaomiCommunity = true)
    SettingsDestination.Nio -> RestartScopeDefaults(nio = true)
    SettingsDestination.Alipay -> RestartScopeDefaults(alipay = true)
    SettingsDestination.Douyin -> RestartScopeDefaults(douyin = true)
    SettingsDestination.Kuaishou -> RestartScopeDefaults(kuaishou = true)
    SettingsDestination.Social -> RestartScopeDefaults(socialApps = true)
    SettingsDestination.QQMusic -> RestartScopeDefaults(qqMusic = true)
    SettingsDestination.Bilibili -> RestartScopeDefaults(bilibili = true)
    SettingsDestination.DouyinLite -> RestartScopeDefaults(douyin = true)
    SettingsDestination.KuaishouLite -> RestartScopeDefaults(kuaishou = true)
    SettingsDestination.Wechat -> RestartScopeDefaults(socialApps = true)
    SettingsDestination.QQ -> RestartScopeDefaults(socialApps = true)
    SettingsDestination.Pinduoduo -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.Taobao -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.Jingdong -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.Xianyu -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.DouyinMall -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.Cainiao -> RestartScopeDefaults(ecommerceApps = true)
    SettingsDestination.HomeEntries,
    SettingsDestination.AboutPhone,
    SettingsDestination.PhoneImage -> RestartScopeDefaults(systemSettings = true)
    SettingsDestination.Spotify -> RestartScopeDefaults(systemUi = true, spotify = true)
    SettingsDestination.HeadsUpNotifications,
    SettingsDestination.HeadsUpGlass,
    SettingsDestination.HeadsUpGlassAdvanced,
    SettingsDestination.Media,
    SettingsDestination.MediaConstraintSet,
    SettingsDestination.Lockscreen,
    SettingsDestination.HyperMusicCover,
    SettingsDestination.StatusBar,
    SettingsDestination.GestureHandle,
    SettingsDestination.Volume -> RestartScopeDefaults(systemUi = true)
    SettingsDestination.Foldables,
    SettingsDestination.Desktop,
    SettingsDestination.WidgetGlassApps -> RestartScopeDefaults(systemUi = true, launcher = true)
    SettingsDestination.KeyShortcuts -> RestartScopeDefaults()
    SettingsDestination.Home,
    SettingsDestination.Donation,
    SettingsDestination.About -> RestartScopeDefaults()
}

/** Mirrors Deadliner's top-bar material reveal curve for this standalone module UI. */
private fun resolveTopBarButtonMaterialProgress(
    collapsedFraction: Float?,
    scrollProgress: Float?,
    overrideProgress: Float?,
): Float {
    overrideProgress?.let { return it.coerceIn(0f, 1f) }
    if (collapsedFraction == null) return scrollProgress?.coerceIn(0f, 1f) ?: 0f
    val normalized = ((collapsedFraction.coerceIn(0f, 1f) - 0.015f) / 0.18f).coerceIn(0f, 1f)
    return normalized * normalized * (3f - 2f * normalized)
}

@Composable
fun MyHyperModifierSettingsApp() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    var settings by remember { mutableStateOf(ModifierSettingsStore.load(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            // Refresh after nested editors so home controls cannot overwrite their saved image/profile.
            if (event == Lifecycle.Event.ON_RESUME) settings = ModifierSettingsStore.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var checkingForUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var startupCheckDone by rememberSaveable { mutableStateOf(false) }
    val updateScope = androidx.compose.runtime.rememberCoroutineScope()
    fun checkForUpdate() {
        if (checkingForUpdate) return
        checkingForUpdate = true
        updateScope.launch {
            try { updateResult = UpdateChecker.checkForUi(startup = false) }
            finally { checkingForUpdate = false }
        }
    }
    LaunchedEffect(Unit) {
        if (!startupCheckDone) {
            checkingForUpdate = true
            try {
                updateResult = UpdateChecker.checkForUi(startup = true)
                    .takeUnless { it == UpdateCheckResult.Latest }
                startupCheckDone = true
            } finally { checkingForUpdate = false }
        }
    }
    PhonePresetWarmup(settings)
    var destination by remember { mutableStateOf(SettingsDestination.Home) }
    val wideHome = isWideSettingsLayout()
    var homeSection by rememberSaveable { mutableIntStateOf(0) }
    val navigationPreferences = remember(context) { context.getSharedPreferences("navigation_ui", Context.MODE_PRIVATE) }
    LaunchedEffect(navigationPreferences) {
        homeSection = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            navigationPreferences.getInt("home_section", 0).coerceIn(0, 1)
        }
    }
    val glassHomeScroll = rememberScrollState()
    val systemHomeScroll = rememberScrollState()
    val selectedHomeScroll = if (wideHome || homeSection == 0) glassHomeScroll else systemHomeScroll
    val homeMaterialScrollObserver = rememberTopBarMaterialScrollObserver(if (wideHome) 2 else homeSection)
    LaunchedEffect(homeMaterialScrollObserver, selectedHomeScroll, wideHome) {
        snapshotFlow { (if (wideHome) maxOf(glassHomeScroll.value, systemHomeScroll.value) else selectedHomeScroll.value).toFloat() }
            .collect(homeMaterialScrollObserver::updateAbsoluteScrollDistance)
    }
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    val activeScrollProgress = if (destination == SettingsDestination.Home) {
        homeMaterialScrollObserver.progress
    } else {
        scrollProgress
    }
    var restartState by remember { mutableStateOf(ScopeRestartState.Ready) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartSystemUi by remember { mutableStateOf(true) }
    var restartLauncher by remember { mutableStateOf(false) }
    var restartPlugin by remember { mutableStateOf(true) }
    var restartMiLink by remember { mutableStateOf(false) }
    var restartXiaomiHealth by remember { mutableStateOf(false) }
    var restartMusic by remember { mutableStateOf(false) }
    var restartMarket by remember { mutableStateOf(false) }
    var restartMiHome by remember { mutableStateOf(false) }
    var restartAmap by remember { mutableStateOf(false) }
    var restartXiaomiCommunity by remember { mutableStateOf(false) }
    var restartBilibili by remember { mutableStateOf(false) }
    var restartNio by remember { mutableStateOf(false) }
    var restartAlipay by remember { mutableStateOf(false) }
    var restartDouyin by remember { mutableStateOf(false) }
    var restartKuaishou by remember { mutableStateOf(false) }
    var restartSocialApps by remember { mutableStateOf(false) }
    var restartQQMusic by remember { mutableStateOf(false) }
    var restartEcommerceApps by remember { mutableStateOf(false) }
    var restartSpotify by remember { mutableStateOf(false) }
    var restartSettings by remember { mutableStateOf(false) }
    var restartSystem by remember { mutableStateOf(false) }
    val topBarButtonMaterialTarget = resolveTopBarButtonMaterialProgress(
        collapsedFraction = null,
        scrollProgress = activeScrollProgress,
        overrideProgress = null,
    )
    val topBarButtonMaterialProgress by animateFloatAsState(
        targetValue = topBarButtonMaterialTarget,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "home-top-bar-button-material",
    )
    val homeTopBarScrollBehavior = if (destination == SettingsDestination.Home) MiuixScrollBehavior(canScroll = { !wideHome }) else null
    fun update(value: ModifierSettings) { settings = value; ModifierSettingsStore.save(context, value) }

    MiuixTheme(colors = miuixColors) {
        CompositionLocalProvider(LocalTopBarButtonMaterialProgress provides topBarButtonMaterialProgress) {
            MaterialTheme(colorScheme = materialColors) {
            DeadlinerMiuixScaffold(
                scrollProgress = activeScrollProgress,
                navigationItems = if (destination.isTopLevel) floatingItems() else emptyList(),
                selectedKey = destination.key,
                navigationAction = {
                    RestartScopeGlassButton {
                        restartState = ScopeRestartState.Ready
                        showRestartDialog = true
                    }
                },
                onNavigationSelected = { selected ->
                    destination = SettingsDestination.entries.first { it.key == selected.key }
                    scrollProgress = 0f
                },
                deviceModeOverride = settings.deviceModeOverride,
                topBar = when (destination) {
                    SettingsDestination.Home -> homeTopBarScrollBehavior?.let { scrollBehavior ->
                        {
                            Column(Modifier.fillMaxWidth()) {
                                TopAppBar(
                                    title = "主页",
                                    largeTitle = "主页",
                                    color = Color.Transparent,
                                    scrollBehavior = scrollBehavior,
                                    actions = {
                                        if (wideHome) RestartScopeTopBarButton {
                                            restartState = ScopeRestartState.Ready
                                            showRestartDialog = true
                                        }
                                    },
                                )
                                if (!wideHome) TabRow(
                                    tabs = listOf("柔光玻璃", "系统界面"),
                                    selectedTabIndex = homeSection,
                                    onTabSelected = { selected ->
                                        homeSection = selected
                                        updateScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            navigationPreferences.edit().putInt("home_section", selected).apply()
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                                )

                            }
                        }
                    }
                    SettingsDestination.About -> { {
                        if (wideHome) TopAppBar(title = "关于", color = Color.Transparent, actions = {
                            RestartScopeTopBarButton {
                                restartState = ScopeRestartState.Ready
                                showRestartDialog = true
                            }
                        }) else Spacer(Modifier.fillMaxWidth().height(88.dp))
                    } }
                    else -> { { Spacer(Modifier.fillMaxWidth().height(88.dp)) } }
                },
                modifier = homeTopBarScrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier,
                bottomBar = {
                    if (destination.isTopLevel) {
                        val navigationInset = with(LocalDensity.current) {
                            WindowInsets.navigationBars.getBottom(this).toDp()
                        }
                        val maskHeight = MiuixFloatingTabBarDefaults.Height + 4.dp + 32.dp + navigationInset
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .navigationEdgeMask(coverageHeight = maskHeight)
                                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixFloatingTabBar(
                                items = floatingItems(), selectedKey = destination.key,
                                onItemSelected = { selected ->
                                    val target = SettingsDestination.entries.first { it.key == selected.key }
                                    destination = target
                                    scrollProgress = 0f
                                },
                            )
                            Spacer(Modifier.width(12.dp))
                            RestartScopeGlassButton {
                                restartState = ScopeRestartState.Ready
                                showRestartDialog = true
                            }
                        }
                    }
                },
                overlay = {
                    updateResult?.let { result ->
                        UpdateCheckDialog(result, { updateResult = null }, { url ->
                            updateResult = null
                            context.openWebPage(url)
                        })
                    }
                    DeadlinerMiuixDialog(showRestartDialog, "重新启动", restartSummary(restartState), {
                        if (restartState != ScopeRestartState.Restarting) showRestartDialog = false
                    }) {
                        RestartScopeDialogContent(
                            state = restartState,
                            restartSystemUi = restartSystemUi,
                            restartLauncher = restartLauncher,
                            restartPlugin = restartPlugin,
                            restartMiLink = restartMiLink,
                            restartXiaomiHealth = restartXiaomiHealth,
                            restartMusic = restartMusic,
                            restartMarket = restartMarket,
                            restartMiHome = restartMiHome,
                            restartAmap = restartAmap,
                            restartXiaomiCommunity = restartXiaomiCommunity,
                            restartBilibili = restartBilibili,
                            restartNio = restartNio,
                            restartAlipay = restartAlipay,
                            restartDouyin = restartDouyin,
                            restartKuaishou = restartKuaishou,
                            restartSocialApps = restartSocialApps,
                            restartQQMusic = restartQQMusic,
                            restartEcommerceApps = restartEcommerceApps,
                            restartSpotify = restartSpotify,
                            restartSettings = restartSettings,
                            restartSystem = restartSystem,
                            onSystemUiChange = { restartSystemUi = it },
                            onLauncherChange = { restartLauncher = it },
                            onPluginChange = {
                                restartPlugin = it
                                // SystemUIPlugin is hosted by SystemUI; reloading only its package
                                // leaves the currently inflated control-centre views untouched.
                                if (it) restartSystemUi = true
                            },
                            onMiLinkChange = { restartMiLink = it },
                            onXiaomiHealthChange = { restartXiaomiHealth = it },
                            onMusicChange = { restartMusic = it },
                            onMarketChange = { restartMarket = it },
                            onMiHomeChange = { restartMiHome = it },
                            onAmapChange = { restartAmap = it },
                            onXiaomiCommunityChange = { restartXiaomiCommunity = it },
                            onBilibiliChange = { restartBilibili = it },
                            onNioChange = { restartNio = it },
                            onAlipayChange = { restartAlipay = it },
                            onDouyinChange = { restartDouyin = it },
                            onKuaishouChange = { restartKuaishou = it },
                            onSocialAppsChange = { restartSocialApps = it },
                            onQQMusicChange = { restartQQMusic = it },
                            onEcommerceAppsChange = { restartEcommerceApps = it },
                            onSpotifyChange = { restartSpotify = it },
                            onSettingsChange = { restartSettings = it },
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartLauncher = false
                                    restartPlugin = false
                                    restartMiLink = false
                                    restartXiaomiHealth = false
                                    restartMusic = false
                                    restartMarket = false
                                    restartMiHome = false
                                    restartAmap = false
                                    restartXiaomiCommunity = false
                                    restartBilibili = false
                                    restartNio = false
                                    restartAlipay = false
                                    restartDouyin = false
                                    restartKuaishou = false
                                    restartSocialApps = false
                                    restartQQMusic = false
                                    restartEcommerceApps = false
                                    restartSpotify = false
                                    restartSettings = false
                                }
                            },
                            onDismiss = { showRestartDialog = false },
                            onConfirm = {
                                restartState = ScopeRestartState.Restarting
                                if (restartSystemUi || restartPlugin || restartSystem) {
                                    update(settings.copy(
                                        systemUiRetryGeneration =
                                            settings.systemUiRetryGeneration + 1,
                                    ))
                                }
                                restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartXiaomiHealth, restartMusic, restartMarket, restartMiHome, restartAmap, restartXiaomiCommunity, restartBilibili, restartSpotify, restartSystem, restartSettings, launcher = restartLauncher, douyin = restartDouyin, kuaishou = restartKuaishou, alipay = restartAlipay, socialApps = restartSocialApps, qqMusic = restartQQMusic, ecommerceApps = restartEcommerceApps, nio = restartNio) {
                                    restartState = if (it) ScopeRestartState.Succeeded else ScopeRestartState.Failed
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                when (destination) {
                    SettingsDestination.Home -> HomeDashboardPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        scrollState = selectedHomeScroll,
                        selectedSection = if (wideHome) null else homeSection,
                        systemScrollState = systemHomeScroll,
                        onNavigate = context::openDetailSettings,
                    )
                    SettingsDestination.About -> AboutSettingsPage(padding, settings, ::update, ::checkForUpdate, checkingForUpdate, onSettingsRestored = { settings = ModifierSettingsStore.load(context) }) { scrollProgress = it }
                    else -> Unit
                }
            }
            }
        }
    }
}

/** The detail host is intentionally a separate Activity so Android owns the gesture transition. */
@Composable
internal fun MyHyperModifierDetailSettingsApp(
    destination: SettingsDestination,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    var settings by remember { mutableStateOf(ModifierSettingsStore.load(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The XML editor opens as another Activity. Refresh its parent before a preset
                // switch can overwrite XML saved while the parent was paused.
                settings = ModifierSettingsStore.load(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    var glassEffectTab by rememberSaveable { mutableIntStateOf(0) }
    val lightGlassScroll = rememberScrollState()
    val darkGlassScroll = rememberScrollState()
    val glassScope = if (destination == SettingsDestination.ShadeCardGlass ||
        destination == SettingsDestination.ShadeCardGlassAdvanced) GlassParameterScope.ShadeCards else GlassParameterScope.HeadsUp
    val isGlassAdvanced = destination == SettingsDestination.HeadsUpGlassAdvanced ||
        destination == SettingsDestination.ShadeCardGlassAdvanced
    val selectedGlassTab = if (glassScope == GlassParameterScope.ShadeCards) 0 else glassEffectTab
    val selectedGlassScroll = if (selectedGlassTab == 0) lightGlassScroll else darkGlassScroll
    val glassMaterialScrollObserver = rememberTopBarMaterialScrollObserver(selectedGlassTab)
    LaunchedEffect(glassMaterialScrollObserver, selectedGlassScroll) {
        snapshotFlow { selectedGlassScroll.value.toFloat() }
            .collect(glassMaterialScrollObserver::updateAbsoluteScrollDistance)
    }
    val activeScrollProgress = if (isGlassAdvanced) {
        glassMaterialScrollObserver.progress
    } else {
        scrollProgress
    }
    val defaultScopes = remember(destination) { destination.requiredRestartScopes() }
    var restartState by remember { mutableStateOf(ScopeRestartState.Ready) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartSystemUi by remember { mutableStateOf(defaultScopes.systemUi) }
    var restartLauncher by remember { mutableStateOf(defaultScopes.launcher) }
    var restartPlugin by remember { mutableStateOf(defaultScopes.plugin) }
    var restartMiLink by remember { mutableStateOf(defaultScopes.miLink) }
    var restartXiaomiHealth by remember { mutableStateOf(defaultScopes.xiaomiHealth) }
    var restartMusic by remember { mutableStateOf(defaultScopes.music) }
    var restartMarket by remember { mutableStateOf(defaultScopes.market) }
    var restartMiHome by remember { mutableStateOf(defaultScopes.miHome) }
    var restartAmap by remember { mutableStateOf(defaultScopes.amap) }
    var restartXiaomiCommunity by remember { mutableStateOf(defaultScopes.xiaomiCommunity) }
    var restartBilibili by remember { mutableStateOf(defaultScopes.bilibili) }
    var restartNio by remember { mutableStateOf(defaultScopes.nio) }
    var restartAlipay by remember { mutableStateOf(defaultScopes.alipay) }
    var restartDouyin by remember { mutableStateOf(defaultScopes.douyin) }
    var restartKuaishou by remember { mutableStateOf(defaultScopes.kuaishou) }
    var restartSocialApps by remember { mutableStateOf(defaultScopes.socialApps) }
    var restartQQMusic by remember { mutableStateOf(defaultScopes.qqMusic) }
    var restartEcommerceApps by remember { mutableStateOf(defaultScopes.ecommerceApps) }
    var restartSpotify by remember { mutableStateOf(defaultScopes.spotify) }
    var restartSettings by remember { mutableStateOf(defaultScopes.systemSettings) }
    var restartSystem by remember { mutableStateOf(false) }
    var editingHeadsUpGlassParameter by remember { mutableStateOf<HeadsUpGlassParameterTarget?>(null) }
    var headsUpGlassPresetDialog by remember { mutableStateOf<HeadsUpGlassPresetDialogMode?>(null) }
    val topBarButtonMaterialTarget = resolveTopBarButtonMaterialProgress(
        collapsedFraction = null,
        scrollProgress = activeScrollProgress,
        overrideProgress = null,
    )
    val topBarButtonMaterialProgress by animateFloatAsState(
        targetValue = topBarButtonMaterialTarget,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "detail-top-bar-button-material",
    )
    fun update(value: ModifierSettings) { settings = value; ModifierSettingsStore.save(context, value) }

    MiuixTheme(colors = miuixColors) {
        CompositionLocalProvider(LocalTopBarButtonMaterialProgress provides topBarButtonMaterialProgress) {
            MaterialTheme(colorScheme = materialColors) {
            DeadlinerMiuixScaffold(
                scrollProgress = activeScrollProgress,
                deviceModeOverride = settings.deviceModeOverride,
                topBar = {
                    Column(Modifier.fillMaxWidth()) {
                        SmallTopAppBar(
                            title = destination.label,
                            color = Color.Transparent,
                            titleColor = MiuixTheme.colorScheme.onSurface,
                            navigationIcon = { BackIconButton(onBack) },
                            actions = {
                                if (destination != SettingsDestination.Donation) RestartScopeTopBarButton {
                                    restartState = ScopeRestartState.Ready
                                    restartSystemUi = defaultScopes.systemUi
                                    restartLauncher = defaultScopes.launcher
                                    restartPlugin = defaultScopes.plugin
                                    restartMiLink = defaultScopes.miLink
                                    restartXiaomiHealth = defaultScopes.xiaomiHealth
                                    restartMusic = defaultScopes.music
                                    restartMarket = defaultScopes.market
                                    restartMiHome = defaultScopes.miHome
                                    restartAmap = defaultScopes.amap
                                    restartXiaomiCommunity = defaultScopes.xiaomiCommunity
                                    restartBilibili = defaultScopes.bilibili
                                    restartNio = defaultScopes.nio
                                    restartAlipay = defaultScopes.alipay
                                    restartDouyin = defaultScopes.douyin
                                    restartKuaishou = defaultScopes.kuaishou
                                    restartSocialApps = defaultScopes.socialApps
                                    restartQQMusic = defaultScopes.qqMusic
                                    restartEcommerceApps = defaultScopes.ecommerceApps
                                    restartSpotify = defaultScopes.spotify
                                    restartSettings = defaultScopes.systemSettings
                                    restartSystem = false
                                    showRestartDialog = true
                                }
                            },
                        )
                        if (isGlassAdvanced && glassScope == GlassParameterScope.HeadsUp) {
                            TabRow(
                                tabs = listOf("亮色", "暗色"),
                                selectedTabIndex = glassEffectTab,
                                onTabSelected = { glassEffectTab = it },
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                            )
                        }
                    }
                },
                bottomBar = {},
                overlay = {
                    DeadlinerMiuixDialog(showRestartDialog, "重新启动", restartSummary(restartState), {
                        if (restartState != ScopeRestartState.Restarting) showRestartDialog = false
                    }) {
                        RestartScopeDialogContent(
                            state = restartState,
                            restartSystemUi = restartSystemUi,
                            restartLauncher = restartLauncher,
                            restartPlugin = restartPlugin,
                            restartMiLink = restartMiLink,
                            restartXiaomiHealth = restartXiaomiHealth,
                            restartMusic = restartMusic,
                            restartMarket = restartMarket,
                            restartMiHome = restartMiHome,
                            restartAmap = restartAmap,
                            restartXiaomiCommunity = restartXiaomiCommunity,
                            restartBilibili = restartBilibili,
                            restartNio = restartNio,
                            restartAlipay = restartAlipay,
                            restartDouyin = restartDouyin,
                            restartKuaishou = restartKuaishou,
                            restartSocialApps = restartSocialApps,
                            restartQQMusic = restartQQMusic,
                            restartEcommerceApps = restartEcommerceApps,
                            restartSpotify = restartSpotify,
                            restartSettings = restartSettings,
                            restartSystem = restartSystem,
                            onSystemUiChange = { restartSystemUi = it },
                            onLauncherChange = { restartLauncher = it },
                            onPluginChange = {
                                restartPlugin = it
                                if (it) restartSystemUi = true
                            },
                            onMiLinkChange = { restartMiLink = it },
                            onXiaomiHealthChange = { restartXiaomiHealth = it },
                            onMusicChange = { restartMusic = it },
                            onMarketChange = { restartMarket = it },
                            onMiHomeChange = { restartMiHome = it },
                            onAmapChange = { restartAmap = it },
                            onXiaomiCommunityChange = { restartXiaomiCommunity = it },
                            onBilibiliChange = { restartBilibili = it },
                            onNioChange = { restartNio = it },
                            onAlipayChange = { restartAlipay = it },
                            onDouyinChange = { restartDouyin = it },
                            onKuaishouChange = { restartKuaishou = it },
                            onSocialAppsChange = { restartSocialApps = it },
                            onQQMusicChange = { restartQQMusic = it },
                            onEcommerceAppsChange = { restartEcommerceApps = it },
                            onSpotifyChange = { restartSpotify = it },
                            onSettingsChange = { restartSettings = it },
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartLauncher = false
                                    restartPlugin = false
                                    restartMiLink = false
                                    restartXiaomiHealth = false
                                    restartMusic = false
                                    restartMarket = false
                                    restartMiHome = false
                                    restartAmap = false
                                    restartXiaomiCommunity = false
                                    restartBilibili = false
                                    restartNio = false
                                    restartAlipay = false
                                    restartDouyin = false
                                    restartKuaishou = false
                                    restartSocialApps = false
                                    restartQQMusic = false
                                    restartEcommerceApps = false
                                    restartSpotify = false
                                    restartSettings = false
                                }
                            },
                            onDismiss = { showRestartDialog = false },
                            onConfirm = {
                                restartState = ScopeRestartState.Restarting
                                if (restartSystemUi || restartPlugin || restartSystem) {
                                    update(settings.copy(
                                        systemUiRetryGeneration =
                                            settings.systemUiRetryGeneration + 1,
                                    ))
                                }
                                restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartXiaomiHealth, restartMusic, restartMarket, restartMiHome, restartAmap, restartXiaomiCommunity, restartBilibili, restartSpotify, restartSystem, restartSettings, launcher = restartLauncher, douyin = restartDouyin, kuaishou = restartKuaishou, alipay = restartAlipay, socialApps = restartSocialApps, qqMusic = restartQQMusic, ecommerceApps = restartEcommerceApps, nio = restartNio) {
                                    restartState = if (it) ScopeRestartState.Succeeded else ScopeRestartState.Failed
                                }
                            },
                        )
                    }
                    HeadsUpGlassParameterDialog(
                        target = editingHeadsUpGlassParameter,
                        settings = settings,
                        onDismissRequest = { editingHeadsUpGlassParameter = null },
                        onSave = {
                            update(it)
                            editingHeadsUpGlassParameter = null
                        },
                        scope = glassScope,
                    )
                    HeadsUpGlassPresetDialog(
                        mode = headsUpGlassPresetDialog,
                        settings = settings,
                        onDismissRequest = { headsUpGlassPresetDialog = null },
                        onImport = {
                            update(it)
                            headsUpGlassPresetDialog = null
                        },
                        scope = glassScope,
                    )
                },
            ) { padding ->
                when (destination) {
                    SettingsDestination.Donation -> DonationSettingsPage(padding) { scrollProgress = it }
                    SettingsDestination.Compatibility -> CompatibilitySettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.NotificationsControlCenter -> NotificationControlCenterSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        onOpenCardGlassSettings = { context.openDetailSettings(SettingsDestination.ShadeCardGlass) },
                        onOpenGlobalMaterialBlur = { context.openDetailSettings(SettingsDestination.GlobalMaterialBlur) },
                    ) { scrollProgress = it }
                    SettingsDestination.GlobalMaterialBlur -> GlobalMaterialBlurSettingsPage(
                        padding, settings, ::update,
                        onOpenUnifiedGlass = { context.openDetailSettings(SettingsDestination.UnifiedGlass) },
                    ) { scrollProgress = it }
                    SettingsDestination.UnifiedGlass -> UnifiedGlassSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.HeadsUpNotifications -> HeadsUpNotificationsSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        onOpenHeadsUpGlassSettings = { context.openDetailSettings(SettingsDestination.HeadsUpGlass) },
                        onOpenGlobalMaterialBlur = { context.openDetailSettings(SettingsDestination.GlobalMaterialBlur) },
                    ) { scrollProgress = it }
                    SettingsDestination.HeadsUpGlass,
                    SettingsDestination.ShadeCardGlass -> HeadsUpGlassSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        onOpenAdvanced = {
                            context.openDetailSettings(if (glassScope == GlassParameterScope.ShadeCards)
                                SettingsDestination.ShadeCardGlassAdvanced else SettingsDestination.HeadsUpGlassAdvanced)
                        },
                        onOpenPresetDialog = { headsUpGlassPresetDialog = it },
                        onOpenGlobalMaterialBlur = { context.openDetailSettings(SettingsDestination.GlobalMaterialBlur) },
                        scope = glassScope,
                    ) { scrollProgress = it }
                    SettingsDestination.HeadsUpGlassAdvanced,
                    SettingsDestination.ShadeCardGlassAdvanced -> HeadsUpGlassAdvancedSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        selectedTab = selectedGlassTab,
                        scrollState = selectedGlassScroll,
                        onEditParameter = { dark, index ->
                            editingHeadsUpGlassParameter = HeadsUpGlassParameterTarget(dark, index)
                        },
                        scope = glassScope,
                    )
                    SettingsDestination.XiaomiHealth -> XiaomiHealthSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Music -> MusicSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Market -> MarketSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.MiHome -> MiHomeSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Amap -> AmapSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Bilibili -> BilibiliSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Wechat -> SocialNavigationSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.WECHAT)
                    SettingsDestination.QQ -> SocialNavigationSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.QQ)
                    SettingsDestination.Pinduoduo -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.PINDUODUO)
                    SettingsDestination.Taobao -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.TAOBAO)
                    SettingsDestination.Jingdong -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.JINGDONG)
                    SettingsDestination.Xianyu -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.XIANYU)
                    SettingsDestination.DouyinMall -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.DOUYIN_MALL)
                    SettingsDestination.Cainiao -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it }, onlyPackage = ModuleScopePackage.CAINIAO)
                    SettingsDestination.NavigationGlass -> NavigationGlassSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Nio -> NioSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Ecommerce -> EcommerceSettingsPage(padding, settings, ::update, { scrollProgress = it })
                    SettingsDestination.Alipay -> AlipaySettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Douyin -> DouyinSettingsPage(padding, settings, ::update, { scrollProgress = it }, lite = false)
                    SettingsDestination.DouyinLite -> DouyinSettingsPage(padding, settings, ::update, { scrollProgress = it }, lite = true)
                    SettingsDestination.Kuaishou -> KuaishouSettingsPage(padding, settings, ::update, { scrollProgress = it }, lite = false)
                    SettingsDestination.KuaishouLite -> KuaishouSettingsPage(padding, settings, ::update, { scrollProgress = it }, lite = true)
                    SettingsDestination.Social -> SocialNavigationSettingsPage(padding, settings, ::update, { scrollProgress = it })
                    SettingsDestination.QQMusic -> QQMusicSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.XiaomiCommunity -> XiaomiCommunitySettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Spotify -> SpotifySettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Media -> MediaSettingsPage(
                        padding,
                        settings,
                        ::update,
                        { preset -> context.openDetailSettings(SettingsDestination.MediaConstraintSet, preset) },
                    ) { scrollProgress = it }
                    SettingsDestination.MediaConstraintSet -> MediaConstraintSetSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Lockscreen -> LockscreenSettingsPage(padding, settings, ::update,
                        { context.openDetailSettings(SettingsDestination.HyperMusicCover) }) { scrollProgress = it }
                    SettingsDestination.HyperMusicCover -> HyperMusicCoverSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Foldables -> FoldSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Desktop -> DesktopSettingsPage(padding, settings, ::update,
                        onOpenWidgetGlassApps = { context.openDetailSettings(SettingsDestination.WidgetGlassApps) }) { scrollProgress = it }
                    SettingsDestination.WidgetGlassApps -> AndroidWidgetGlassSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.StatusBar -> StatusBarSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.GestureHandle -> GestureHandleSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.AboutPhone -> AboutPhoneSettingsPage(
                        padding, settings, ::update,
                        { context.openDetailSettings(SettingsDestination.PhoneImage) },
                    ) { scrollProgress = it }
                    SettingsDestination.HomeEntries -> HomeEntriesSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.PhoneImage -> PhoneImageSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.KeyShortcuts -> KeyShortcutSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Volume -> VolumeSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Home, SettingsDestination.About -> Unit
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

@Composable
internal fun NavigationSettingItem(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
) = SettingItem(
    headlineText = title,
    supportingText = summary,
    enabled = enabled,
    onClick = onClick,
    leadingContent = leadingContent,
    trailingContent = {
        Icon(
            painter = rememberVectorPainter(MiuixIcons.ChevronForward),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(16.dp),
        )
    },
)

@Composable
private fun RestartScopeTopBarButton(onClick: () -> Unit) = AdvancedTopBarIconButton(onClick = onClick) {
    Icon(
        painter = rememberVectorPainter(MiuixIcons.Refresh),
        contentDescription = "重新启动以应用修改",
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun BackIconButton(onClick: () -> Unit) = AdvancedTopBarIconButton(onClick = onClick) {
    Icon(
        painter = rememberVectorPainter(MiuixIcons.ChevronBackward),
        contentDescription = "返回主页",
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
    )
}

/** Local copy of Deadliner's immersive top-bar action, backed by the shared glass material host. */
@Composable
private fun AdvancedTopBarIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (LocalImmersiveTopBarEnabled.current) {
        SoftGlassIconButton(
            onClick = onClick,
            materialAlpha = LocalTopBarButtonMaterialProgress.current,
            content = content,
        )
    } else {
        IconButton(onClick = onClick, content = content)
    }
}

/** Keeps scope restart as the standalone soft-glass action beside the floating bottom tab bar. */
@Composable
private fun RestartScopeGlassButton(onClick: () -> Unit) {
    val advancedMaterial = LocalAdvancedMaterialSpec.current
    SoftGlassSurface(
        modifier = Modifier
            .size(MiuixFloatingTabBarDefaults.Height)
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
                painter = rememberVectorPainter(MiuixIcons.Refresh),
                contentDescription = "重新启动以应用修改",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(androidx.compose.ui.unit.lerp(24.dp, 20.dp, LocalDockScrollFraction.current)),
            )
        }
    }
}

private data class ScopeStatus(
    val appName: String,
    val packageName: String,
    val hasModification: Boolean,
    val isActive: Boolean,
)

private fun scopeStatuses(
    settings: ModifierSettings,
    framework: ModuleFrameworkState.Snapshot,
): List<ScopeStatus> = listOf(
    ScopeStatus(
        appName = "系统框架",
        packageName = ModuleScopePackage.SYSTEM_FRAMEWORK,
        hasModification = settings.systemBarCompatibilityScrimDisabled ||
            (settings.gestureHandleEnabled && settings.gestureHandleAttachToAppTransition) ||
            settings.volumeUpPowerAction != KeyShortcutActions.STOCK || settings.doublePowerAction != KeyShortcutActions.STOCK,
        isActive = framework.isActive(ModuleScopePackage.SYSTEM_FRAMEWORK),
    ),
    ScopeStatus(
        appName = "超级小爱",
        packageName = ModuleScopePackage.VOICE_ASSIST,
        hasModification = settings.volumeUpPowerAction in listOf(KeyShortcutActions.MEMORY, KeyShortcutActions.SCREEN) ||
            settings.doublePowerAction in listOf(KeyShortcutActions.MEMORY, KeyShortcutActions.SCREEN),
        isActive = framework.isActive(ModuleScopePackage.VOICE_ASSIST),
    ),
    ScopeStatus(
        appName = "系统界面",
        packageName = ModuleScopePackage.SYSTEM_UI,
        hasModification = listOf(
            settings.gestureHandleEnabled && (settings.gestureHandlePreset != GestureHandlePresets.STOCK ||
                settings.gestureHandleAppModes.isNotEmpty() || settings.gestureHandleTouchReveal ||
                settings.gestureHandleSwipeMotion),
            settings.notificationsEnabled,
            settings.hideHeadsUpMiniBar,
            settings.headsUpBottomMarginEnabled,
            settings.headsUpGlassParametersEnabled,
            GlassParameterScope.ShadeCards.enabled(settings),
            GlassParameterScope.Unified.enabled(settings),
            !settings.disableShadeGlassHooks && settings.globalGlassBlurEnabled,
            settings.globalBackgroundDimEnabled,
            settings.shadeBackgroundScaleDisabled,
            settings.globalBackgroundBlurPercent != 100f,
            (!settings.disableMediaIslandHooks && settings.mediaEnabled),
            (!settings.disableMediaIslandHooks && (settings.mediaLayoutPreset.isNotBlank() || settings.mediaIslandLayoutPreset.isNotBlank())),
            (!settings.disableMediaIslandHooks && settings.hideAodActions),
            (!settings.disableMediaIslandHooks && settings.hideAodSeamless),
            settings.sinkLockscreenNotificationsForFingerprint,
            settings.hideLockscreenClockLeadingZero,
            settings.hideDesktopClockLeadingZero,
            settings.foldPortraitIconPercent < 100,
            (settings.foldInnerHideGestureBar && android.os.Build.DEVICE == "lhasa"),
            settings.foldPortraitDualPageEnabled,
            (!settings.disableMediaIslandHooks && settings.foldIslandPosition != 0 && android.os.Build.DEVICE == "lhasa"),
            settings.desktopClockMaterial != 0,
            settings.desktopSearch4x7Enabled,
            settings.androidWidgetStackEnabled,
            settings.androidWidgetGlassEnabled,
            settings.bypassHyperMusicCoverClockAdjustment,
            settings.hmcExpandedFingerprint,
            settings.hmcClockFontHeightEnabled,
            settings.progressiveLockscreenClockAvoidance,
            settings.hideLockscreenFingerprintIcon,
            settings.lowerLockscreenPasswordPage,
            settings.showLockscreenFingerprintIconOnAod,
            settings.aodClockWeightEnabled,
            settings.lockscreenPinKeySoftGlassEnabled,
            settings.statusBarHideMobileTypeOnWifi,
            settings.statusBarHideMobileActivity,
            settings.statusBarHideWifiActivity,
            settings.statusBarShowSeconds,
            settings.statusBarSolidBatteryFrame,
            settings.roundedNetworkIcons,
            settings.controlCenterShowBatteryPercent,
            settings.notificationClockShowSeconds,
            settings.statusBarHideClockLeadingZero,
            settings.statusBarHideWifiStandard,
            settings.statusBarMobileActivityOffsetX != 0f,
            settings.statusBarMobileActivityOffsetY != 0f,
            settings.statusBarWifiActivityOffsetX != 0f,
            settings.statusBarWifiActivityOffsetY != 0f,
            settings.statusBarNetworkSpeedMarginRight != 0f,
            settings.statusBarWifiMarginLeft != 0f,
            settings.statusBarWifiMarginRight != 0f,
            settings.statusBarMobileMarginLeft != 0f,
            settings.statusBarMobileMarginRight != 0f,
            settings.statusBarNetworkSpeedMarginLeft != 0f,
            settings.statusBarNetworkTypeEnabled,
            settings.volumePanelRadius > 0f,
            (!settings.disableMediaIslandHooks && settings.islandEnabled),
            (!settings.disableMediaIslandHooks && settings.superIslandWhitelistDisabled),
            !settings.disableMediaIslandHooks && !settings.disableShadeGlassHooks && settings.dynamicIslandOpacityEnabled,
            !settings.disableMediaIslandHooks && !settings.disableShadeGlassHooks && settings.dynamicIslandNotificationOpacityEnabled,
        ).any { it },
        isActive = framework.isActive(ModuleScopePackage.SYSTEM_UI),
    ),
    ScopeStatus(
        appName = "智能助理",
        packageName = ModuleScopePackage.PERSONAL_ASSISTANT,
        hasModification = settings.androidWidgetStackEnabled,
        isActive = framework.isActive(ModuleScopePackage.PERSONAL_ASSISTANT),
    ),
    ScopeStatus(
        appName = "系统桌面",
        packageName = ModuleScopePackage.LAUNCHER,
        hasModification = settings.foldPortraitDualPageEnabled || settings.foldPortraitIconPercent < 100 || settings.desktopSearch4x7Enabled || settings.hideDesktopClockLeadingZero || settings.desktopClockMaterial != 0 || settings.androidWidgetStackEnabled || settings.androidWidgetGlassEnabled,
        isActive = framework.isActive(ModuleScopePackage.LAUNCHER),
    ),
    ScopeStatus(
        appName = "系统界面插件",
        packageName = ModuleScopePackage.PLUGIN,
        hasModification = (!settings.disableMediaIslandHooks && settings.foldIslandPosition != 0 && android.os.Build.DEVICE == "lhasa") ||
            (!settings.disableMediaIslandHooks && !settings.disableShadeGlassHooks && settings.dynamicIslandOpacityEnabled) || settings.controlCenterEnabled || settings.globalBackgroundBlurPercent != 100f ||
            settings.globalBackgroundDimEnabled || settings.shadeBackgroundScaleDisabled || GlassParameterScope.ShadeCards.enabled(settings) ||
            (!settings.disableShadeGlassHooks && (settings.globalGlassBlurEnabled || settings.unifiedGlassEnabled)),
        isActive = framework.isActive(ModuleScopePackage.PLUGIN),
    ),
    ScopeStatus(
        appName = "小米服务框架",
        packageName = ModuleScopePackage.XMSF,
        hasModification = (!settings.disableMediaIslandHooks && settings.superIslandWhitelistDisabled),
        isActive = framework.isActive(ModuleScopePackage.XMSF),
    ),
    ScopeStatus(
        appName = "通话界面",
        packageName = ModuleScopePackage.INCALL_UI,
        hasModification = settings.globalBackgroundBlurPercent != 100f || settings.globalBackgroundDimEnabled ||
            (!settings.disableShadeGlassHooks && (settings.globalGlassBlurEnabled || settings.unifiedGlassEnabled)),
        isActive = framework.isActive(ModuleScopePackage.INCALL_UI),
    ),
    ScopeStatus(
        appName = "小米互联服务",
        packageName = ModuleScopePackage.MILINK,
        hasModification = settings.miLinkMainCardsEnabled || settings.globalBackgroundBlurPercent != 100f || settings.globalBackgroundDimEnabled || GlassParameterScope.Unified.enabled(settings),
        isActive = framework.isActive(ModuleScopePackage.MILINK),
    ),
    ScopeStatus(
        appName = "小米运动健康",
        packageName = ModuleScopePackage.XIAOMI_HEALTH,
        hasModification = settings.xiaomiHealthFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.XIAOMI_HEALTH),
    ),
    ScopeStatus(
        appName = "小米音乐手机版",
        packageName = ModuleScopePackage.MI_MUSIC,
        hasModification = settings.miMusicFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.MI_MUSIC),
    ),
    ScopeStatus(
        appName = "应用商店",
        packageName = ModuleScopePackage.MARKET,
        hasModification = settings.marketFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.MARKET),
    ),
    ScopeStatus(
        appName = "米家",
        packageName = ModuleScopePackage.MI_HOME,
        hasModification = settings.miHomeFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.MI_HOME),
    ),
    ScopeStatus(
        appName = "高德地图",
        packageName = ModuleScopePackage.AMAP,
        hasModification = settings.amapFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.AMAP),
    ),
    ScopeStatus(
        appName = "系统设置",
        packageName = ModuleScopePackage.SETTINGS,
        hasModification = settings.aboutPhoneCardsEnabled,
        isActive = framework.isActive(ModuleScopePackage.SETTINGS),
    ),
    ScopeStatus(
        appName = "Spotify",
        packageName = ModuleScopePackage.SPOTIFY,
        hasModification = settings.spotifyFloatingNavigationEnabled ||
            settings.spotifyFavoriteButtonEnabled || settings.spotifyShuffleButtonEnabled,
        isActive = framework.isActive(ModuleScopePackage.SPOTIFY),
    ),
    ScopeStatus(
        appName = "蔚来汽车",
        packageName = ModuleScopePackage.NIO,
        hasModification = settings.nioFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.NIO),
    ),
    ScopeStatus(
        appName = "支付宝",
        packageName = ModuleScopePackage.ALIPAY,
        hasModification = settings.alipayFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.ALIPAY),
    ),
    ScopeStatus(
        appName = "抖音",
        packageName = ModuleScopePackage.DOUYIN,
        hasModification = settings.douyinFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.DOUYIN),
    ),
    ScopeStatus(
        appName = "抖音极速版",
        packageName = ModuleScopePackage.DOUYIN_LITE,
        hasModification = settings.douyinLiteFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.DOUYIN_LITE),
    ),
    ScopeStatus(
        appName = "微信",
        packageName = ModuleScopePackage.WECHAT,
        hasModification = settings.wechatFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.WECHAT),
    ),
    ScopeStatus(
        appName = "Google Authenticator",
        packageName = ModuleScopePackage.AUTHENTICATOR,
        hasModification = settings.authenticatorGridEnabled,
        isActive = framework.isActive(ModuleScopePackage.AUTHENTICATOR),
    ),
    ScopeStatus(
        appName = "QQ",
        packageName = ModuleScopePackage.QQ,
        hasModification = settings.qqFloatingNavigationEnabled || settings.qqFoldSplitRatioEnabled || settings.qqFoldNavigationImmersionEnabled,
        isActive = framework.isActive(ModuleScopePackage.QQ),
    ),
    ScopeStatus(
        appName = "快手",
        packageName = ModuleScopePackage.KUAISHOU,
        hasModification = settings.kuaishouFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.KUAISHOU),
    ),
    ScopeStatus(
        appName = "快手极速版",
        packageName = ModuleScopePackage.KUAISHOU_LITE,
        hasModification = settings.kuaishouLiteFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.KUAISHOU_LITE),
    ),
    ScopeStatus(
        appName = "QQ音乐",
        packageName = ModuleScopePackage.QQ_MUSIC,
        hasModification = false,
        isActive = framework.isActive(ModuleScopePackage.QQ_MUSIC),
    ),
    ScopeStatus(
        appName = "哔哩哔哩",
        packageName = ModuleScopePackage.BILIBILI,
        hasModification = settings.bilibiliFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.BILIBILI),
    ),
    ScopeStatus(
        appName = "小米社区",
        packageName = ModuleScopePackage.XIAOMI_COMMUNITY,
        hasModification = settings.xiaomiCommunityFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.XIAOMI_COMMUNITY),
    ),
).map { scope ->
    when {
        !settings.moduleHooksEnabled -> scope.copy(hasModification = false)
        settings.systemUiCompatibilityMode && scope.packageName in setOf(
            ModuleScopePackage.SYSTEM_UI, ModuleScopePackage.PLUGIN,
        ) -> scope.copy(hasModification = false)
        settings.globalGlassBlurEnabled && !settings.disableShadeGlassHooks &&
            scope.packageName != ModuleScopePackage.SYSTEM_FRAMEWORK &&
            scope.packageName != ModuleScopePackage.DOUYIN &&
            scope.packageName != ModuleScopePackage.DOUYIN_LITE &&
            scope.packageName != ModuleScopePackage.KUAISHOU &&
            scope.packageName != ModuleScopePackage.KUAISHOU_LITE &&
            scope.packageName != ModuleScopePackage.ALIPAY &&
            scope.packageName != ModuleScopePackage.WECHAT &&
            scope.packageName != ModuleScopePackage.NIO &&
            scope.packageName != ModuleScopePackage.AUTHENTICATOR &&
            scope.packageName != ModuleScopePackage.QQ -> scope.copy(hasModification = true)
        else -> scope
    }
}

@Composable
private fun HomeDashboardPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    scrollState: ScrollState,
    selectedSection: Int?,
    systemScrollState: ScrollState = scrollState,
    showHeading: Boolean = false,
    onNavigate: (SettingsDestination) -> Unit,
) {
    if (selectedSection == null) {
        // Match Deadliner ModernHost: equal panes, 8dp outer spacing and gap, separate lists.
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                HomeDashboardPage(padding, settings, update, scrollState, 0,
                    showHeading = true, onNavigate = onNavigate)
            }
            Box(Modifier.weight(1f)) {
                HomeDashboardPage(padding, settings, update, systemScrollState, 1,
                    showHeading = true, onNavigate = onNavigate)
            }
        }
        return
    }
    SettingsScrollPage(padding, {}, scrollState) {
    if (showHeading) Text(if (selectedSection == 0) "柔光玻璃" else "系统界面",
        style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(vertical = 12.dp))
    if (selectedSection == 0) SettingsSection {
        NavigationSettingItem(
            "捐赠",
            "支持 HyperModifier 的持续开发",
            onClick = { onNavigate(SettingsDestination.Donation) },
            leadingContent = { HomeSystemIcon(R.drawable.ic_home_donation, Color(0xFFC52C79)) },
        )
    }
    if (selectedSection == 0) {
        SettingsSection {
            NavigationSettingItem("底栏柔光玻璃参数调节", "预览与自定义", onClick = { onNavigate(SettingsDestination.NavigationGlass) })
        }
        InstalledGlassApplications(onNavigate)

    }
    if (selectedSection == 1) {
        SystemHookStatus(settings) { onNavigate(SettingsDestination.Compatibility) }
        SettingsSection {
            NavigationSettingItem(
                "通知/控制中心",
                "",
                onClick = { onNavigate(SettingsDestination.NotificationsControlCenter) },
                leadingContent = { HomeSystemIcon(R.drawable.ic_home_bottom_panel_close, Color(0xFF2565E8)) },
            )
            NavigationSettingItem(
                "悬浮通知",
                "",
                onClick = { onNavigate(SettingsDestination.HeadsUpNotifications) },
                leadingContent = { HomeSystemIcon(R.drawable.ic_home_notifications_active, Color(0xFFC94E08)) },
            )
            NavigationSettingItem("全局玻璃材质与模糊", "", onClick = { onNavigate(SettingsDestination.GlobalMaterialBlur) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_apps, Color(0xFF5276C7)) })
            NavigationSettingItem("超级岛与媒体组件", "", onClick = { onNavigate(SettingsDestination.Media) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_music_note, Color(0xFF843AD4)) })
            NavigationSettingItem("锁屏", "", onClick = { onNavigate(SettingsDestination.Lockscreen) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_lock, Color(0xFF008F7D)) })
            NavigationSettingItem("系统桌面", "", onClick = { onNavigate(SettingsDestination.Desktop) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_home, Color(0xFF5276C7)) })
            NavigationSettingItem("状态栏", "", onClick = { onNavigate(SettingsDestination.StatusBar) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_signal_cellular_alt, Color(0xFF087EBA)) })
            NavigationSettingItem("手势提示线", "", onClick = { onNavigate(SettingsDestination.GestureHandle) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_gesture_handle, Color(0xFF5276C7)) })
            NavigationSettingItem("系统设置", "", onClick = { onNavigate(SettingsDestination.AboutPhone) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_settings, Color(0xFF607D8B)) })
            NavigationSettingItem("快捷键", "", onClick = { onNavigate(SettingsDestination.KeyShortcuts) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_mobile_arrow_up_right, Color(0xFFC52C79)) })
            NavigationSettingItem("音量面板", "", onClick = { onNavigate(SettingsDestination.Volume) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_volume_up, Color(0xFFC52C79)) })
            NavigationSettingItem("折叠屏专区", "", onClick = { onNavigate(SettingsDestination.Foldables) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_foldable, Color(0xFF5276C7)) })
        }
    }
}

}

@Composable
internal fun DeviceModeStatusCard(
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
) {
    val override = LargeScreenDevicePolicy.normalizeOverride(settings.deviceModeOverride)
    val detected = LargeScreenDevicePolicy.detectedMode()
    val effective = LargeScreenDevicePolicy.effectiveMode(override)
    val automatic = override == LargeScreenDevicePolicy.AUTO
    val largeScreen = LargeScreenDevicePolicy.usesLargeScreenLayout(override)
    val badgeContainer = if (largeScreen) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeContent = if (largeScreen) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val deviceIdentity = listOf(Build.DEVICE, Build.MODEL)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" / ")
    val source = if (automatic) {
        "自动识别"
    } else {
        "手动覆盖 · 自动识别为${workModeLabel(detected)}模式"
    }

    androidx.compose.runtime.CompositionLocalProvider {
        SettingItem(
            headlineText = "当前：${workModeLabel(effective)}模式",
            supportingText = listOf(source, deviceIdentity)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            trailingContent = {
                Box(
                    modifier = Modifier
                        .clip(Capsule())
                        .background(badgeContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (automatic) "自动" else "手动",
                        color = badgeContent,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            },
        )
        WindowDropdownPreference(
            title = "设备工作模式",
            summary = "识别不准时可手动覆盖；重启系统界面和相关应用，系统框架适配需重启手机",
            items = listOf("自动识别", "手机模式", "折叠屏模式", "平板模式"),
            selectedIndex = override,
            onSelectedIndexChange = {
                update(settings.copy(
                    deviceModeOverride = LargeScreenDevicePolicy.normalizeOverride(it),
                ))
            },
        )
    }
}

private fun workModeLabel(mode: Int): String = when (mode) {
    LargeScreenDevicePolicy.FOLDABLE -> "折叠屏"
    LargeScreenDevicePolicy.TABLET -> "平板"
    else -> "手机"
}

@Composable
private fun ModuleStatusHero(
    health: ModuleHealth,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    // Success and warning are app-owned semantic pairs; Miuix provides the error pair.
    val accent = when (health.indicator) {
        ModuleHealthIndicator.Ready -> if (dark) Color(0xFF38D169) else Color(0xFF20C764)
        ModuleHealthIndicator.Warning -> if (dark) Color(0xFFFFC461) else Color(0xFF996000)
        ModuleHealthIndicator.Unavailable -> MiuixTheme.colorScheme.error
        ModuleHealthIndicator.Pending -> MiuixTheme.colorScheme.onSurfaceVariantActions
    }
    val container = when (health.indicator) {
        ModuleHealthIndicator.Ready -> if (dark) Color(0xFF153D28) else Color(0xFFDDF8E6)
        ModuleHealthIndicator.Warning -> if (dark) Color(0xFF463419) else Color(0xFFFFF0D4)
        ModuleHealthIndicator.Unavailable -> MiuixTheme.colorScheme.errorContainer
        ModuleHealthIndicator.Pending -> MiuixTheme.colorScheme.surfaceVariant
    }
    val foreground = if (health.indicator == ModuleHealthIndicator.Unavailable)
        MiuixTheme.colorScheme.onErrorContainer else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .heightIn(min = 144.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(onClick = onClick),
    ) {
        // Static vector artwork reuses the existing hero rendering path.
        if (health.indicator != ModuleHealthIndicator.Pending) {
            Icon(
                painter = painterResource(when (health.indicator) {
                    ModuleHealthIndicator.Ready -> R.drawable.ic_module_status_ready
                    ModuleHealthIndicator.Warning -> R.drawable.ic_module_status_warning
                    else -> R.drawable.ic_module_status_unavailable
                }),
                contentDescription = null,
                tint = accent.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 28.dp)
                    .size(128.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text(
                text = health.title,
                color = foreground,
                style = MiuixTheme.textStyles.title3,
            )
            Text(
                text = "版本：${BuildConfig.VERSION_NAME} · 兼容与恢复",
                color = foreground,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "${health.summary} · 点击查看与恢复",
                color = foreground,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun ScopeStatusItem(
    appName: String,
    hasModification: Boolean,
    isActive: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(appName, style = MiuixTheme.textStyles.body1)
            Text(
                when {
                    !hasModification -> "未配置"
                    isActive -> "已激活"
                    else -> "未激活"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            painter = painterResource(if (isActive) R.drawable.ic_scope_enabled else R.drawable.ic_scope_disabled),
            contentDescription = if (isActive) "$appName 作用域已激活" else "$appName 作用域未激活",
            tint = if (isActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun ScopeStatusSection(settings: ModifierSettings, containerAlpha: Float = 1f) {
    val framework by ModuleFrameworkState.snapshot
    val scopes = remember(settings, framework) { scopeStatuses(settings, framework).filter { it.packageName != ModuleScopePackage.PILIPLUS } }
    var expanded by remember { mutableStateOf(false) }
    SettingsSection(topLabel = "作用域状态", containerAlpha = containerAlpha) {
        NavigationSettingItem("查看作用域", "${scopes.count { it.isActive && it.hasModification }}/${scopes.count { it.hasModification }} 个已修改作用域激活",
            onClick = { expanded = !expanded })
        if (expanded) scopes.filter { it.packageName != ModuleScopePackage.PILIPLUS }.forEach {
            ScopeStatusItem(it.appName, it.hasModification, it.isActive)
        }
    }
}

@Composable
private fun SystemHookStatus(settings: ModifierSettings, onClick: () -> Unit) {
    val context = LocalContext.current
    val framework by ModuleFrameworkState.snapshot
    val diagnostics = rememberHookDiagnostics()
    val scopes = remember(settings, framework) { scopeStatuses(settings, framework) }
    val features = remember(diagnostics) { HookDiagnosticPolicy.latestFeatures(diagnostics) }
    val modified = scopes.count { it.hasModification }
    val active = scopes.count { it.hasModification && it.isActive }
    val health = ModuleHealthPolicy.resolve(settings.moduleHooksEnabled, settings.systemUiCompatibilityMode,
        framework.connected, framework.apiVersion >= ModuleFrameworkState.MIN_SUPPORTED_API,
        modified, active, features)
    ModuleStatusHero(health, onClick)
}
