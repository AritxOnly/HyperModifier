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
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.graphics.ColorUtils

internal enum class SettingsDestination(val key: String, val label: String) {
    Home("home", "主页"),
    NotificationsControlCenter("notifications-control-center", "通知/控制中心"),
    HeadsUpNotifications("heads-up-notifications", "悬浮通知"),
    HeadsUpGlass("heads-up-glass", "悬浮通知柔光玻璃"),
    HeadsUpGlassAdvanced("heads-up-glass-advanced", "高级参数调整"),
    XiaomiHealth("xiaomi-health", "小米运动健康"),
    Market("market", "应用商店"),
    MiHome("mi-home", "米家"),
    Amap("amap", "高德地图"),
    XiaomiCommunity("xiaomi-community", "小米社区"),
    Spotify("spotify", "Spotify"),
    Media("media", "媒体组件"),
    MediaConstraintSet("media-constraint-set", "高级布局编辑"),
    Lockscreen("lockscreen", "锁屏"),
    StatusBar("status-bar", "状态栏"),
    Volume("volume", "音量面板"),
    About("about", "关于"),
    ;

    val isTopLevel: Boolean get() = this == Home || this == About
    val supportsRestore: Boolean get() = this != About

    companion object {
        fun fromKey(key: String?): SettingsDestination? = entries.firstOrNull {
            it.key == key && it != Spotify
        }
    }
}
internal enum class ScopeRestartState { Ready, Restarting, Succeeded, Failed }

private data class RestartScopeDefaults(
    val systemUi: Boolean = false,
    val plugin: Boolean = false,
    val miLink: Boolean = false,
    val xiaomiHealth: Boolean = false,
    val market: Boolean = false,
    val miHome: Boolean = false,
    val amap: Boolean = false,
    val xiaomiCommunity: Boolean = false,
    val spotify: Boolean = false,
)

private fun SettingsDestination.requiredRestartScopes(): RestartScopeDefaults = when (this) {
    SettingsDestination.NotificationsControlCenter -> RestartScopeDefaults(systemUi = true, plugin = true, miLink = true)
    SettingsDestination.XiaomiHealth -> RestartScopeDefaults(xiaomiHealth = true)
    SettingsDestination.Market -> RestartScopeDefaults(market = true)
    SettingsDestination.MiHome -> RestartScopeDefaults(miHome = true)
    SettingsDestination.Amap -> RestartScopeDefaults(amap = true)
    SettingsDestination.XiaomiCommunity -> RestartScopeDefaults(xiaomiCommunity = true)
    SettingsDestination.Spotify -> RestartScopeDefaults(spotify = true)
    SettingsDestination.HeadsUpNotifications,
    SettingsDestination.HeadsUpGlass,
    SettingsDestination.HeadsUpGlassAdvanced,
    SettingsDestination.Media,
    SettingsDestination.MediaConstraintSet,
    SettingsDestination.Lockscreen,
    SettingsDestination.StatusBar,
    SettingsDestination.Volume -> RestartScopeDefaults(systemUi = true)
    SettingsDestination.Home,
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
    var destination by remember { mutableStateOf(SettingsDestination.Home) }
    var homeSection by rememberSaveable { mutableIntStateOf(0) }
    val glassHomeScroll = rememberScrollState()
    val systemHomeScroll = rememberScrollState()
    val selectedHomeScroll = if (homeSection == 0) glassHomeScroll else systemHomeScroll
    val homeMaterialScrollObserver = rememberTopBarMaterialScrollObserver(homeSection)
    LaunchedEffect(homeMaterialScrollObserver, selectedHomeScroll) {
        snapshotFlow { selectedHomeScroll.value.toFloat() }
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
    var restartPlugin by remember { mutableStateOf(true) }
    var restartMiLink by remember { mutableStateOf(false) }
    var restartXiaomiHealth by remember { mutableStateOf(false) }
    var restartMarket by remember { mutableStateOf(false) }
    var restartMiHome by remember { mutableStateOf(false) }
    var restartAmap by remember { mutableStateOf(false) }
    var restartXiaomiCommunity by remember { mutableStateOf(false) }
    var restartSpotify by remember { mutableStateOf(false) }
    var restartSystem by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
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
    val homeTopBarScrollBehavior = if (destination == SettingsDestination.Home) MiuixScrollBehavior() else null
    fun update(value: ModifierSettings) { settings = value; ModifierSettingsStore.save(context, value) }

    MiuixTheme(colors = miuixColors) {
        CompositionLocalProvider(LocalTopBarButtonMaterialProgress provides topBarButtonMaterialProgress) {
            MaterialTheme(colorScheme = materialColors) {
            DeadlinerMiuixScaffold(
                scrollProgress = activeScrollProgress,
                topBar = when (destination) {
                    SettingsDestination.Home -> homeTopBarScrollBehavior?.let { scrollBehavior ->
                        {
                            Column(Modifier.fillMaxWidth()) {
                                TopAppBar(
                                    title = "主页",
                                    largeTitle = "主页",
                                    color = Color.Transparent,
                                    titleColor = MiuixTheme.colorScheme.onSurface,
                                    largeTitleColor = MiuixTheme.colorScheme.onSurface,
                                    scrollBehavior = scrollBehavior,
                                    actions = { RestoreDefaultsIconButton { showRestoreDialog = true } },
                                )
                                TabRow(
                                    tabs = listOf("柔光玻璃", "系统界面"),
                                    selectedTabIndex = homeSection,
                                    onTabSelected = { homeSection = it },
                                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                    SettingsDestination.About -> { { Spacer(Modifier.fillMaxWidth().height(88.dp)) } }
                    else -> { { Spacer(Modifier.fillMaxWidth().height(88.dp)) } }
                },
                modifier = homeTopBarScrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier,
                bottomBar = {
                    if (destination.isTopLevel) {
                        Row(
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
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
                                layout = MiuixFloatingTabLayout.Stacked,
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
                    DeadlinerMiuixDialog(showRestartDialog, "重新启动", restartSummary(restartState), {
                        if (restartState != ScopeRestartState.Restarting) showRestartDialog = false
                    }) {
                        RestartScopeDialogContent(
                            state = restartState,
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
                            onSystemUiChange = { restartSystemUi = it },
                            onPluginChange = {
                                restartPlugin = it
                                // SystemUIPlugin is hosted by SystemUI; reloading only its package
                                // leaves the currently inflated control-centre views untouched.
                                if (it) restartSystemUi = true
                            },
                            onMiLinkChange = { restartMiLink = it },
                            onXiaomiHealthChange = { restartXiaomiHealth = it },
                            onMarketChange = { restartMarket = it },
                            onMiHomeChange = { restartMiHome = it },
                            onAmapChange = { restartAmap = it },
                            onXiaomiCommunityChange = { restartXiaomiCommunity = it },
                            onSpotifyChange = { restartSpotify = it },
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartPlugin = false
                                    restartMiLink = false
                                    restartXiaomiHealth = false
                                    restartMarket = false
                                    restartMiHome = false
                                    restartAmap = false
                                    restartXiaomiCommunity = false
                                    restartSpotify = false
                                }
                            },
                            onDismiss = { showRestartDialog = false },
                            onConfirm = {
                                restartState = ScopeRestartState.Restarting
                                restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartXiaomiHealth, restartMarket, restartMiHome, restartAmap, restartXiaomiCommunity, restartSpotify, restartSystem) {
                                    restartState = if (it) ScopeRestartState.Succeeded else ScopeRestartState.Failed
                                }
                            },
                        )
                    }
                    RestoreDefaultsDialog(
                        show = showRestoreDialog,
                        onDismissRequest = { showRestoreDialog = false },
                        onRestoreSystemDefault = { update(ModifierSettingsPresets.systemDefault()); showRestoreDialog = false },
                        onRestoreModuleDefault = { update(ModifierSettingsPresets.moduleDefault()); showRestoreDialog = false },
                    )
                },
            ) { padding ->
                when (destination) {
                    SettingsDestination.Home -> HomeDashboardPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        selectedSection = homeSection,
                        scrollState = if (homeSection == 0) glassHomeScroll else systemHomeScroll,
                        onNavigate = context::openDetailSettings,
                    )
                    SettingsDestination.About -> AboutSettingsPage(padding) { scrollProgress = it }
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
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    var glassEffectTab by rememberSaveable { mutableIntStateOf(0) }
    val lightGlassScroll = rememberScrollState()
    val darkGlassScroll = rememberScrollState()
    val selectedGlassScroll = if (glassEffectTab == 0) lightGlassScroll else darkGlassScroll
    val glassMaterialScrollObserver = rememberTopBarMaterialScrollObserver(glassEffectTab)
    LaunchedEffect(glassMaterialScrollObserver, selectedGlassScroll) {
        snapshotFlow { selectedGlassScroll.value.toFloat() }
            .collect(glassMaterialScrollObserver::updateAbsoluteScrollDistance)
    }
    val activeScrollProgress = if (destination == SettingsDestination.HeadsUpGlassAdvanced) {
        glassMaterialScrollObserver.progress
    } else {
        scrollProgress
    }
    val defaultScopes = remember(destination) { destination.requiredRestartScopes() }
    var restartState by remember { mutableStateOf(ScopeRestartState.Ready) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartSystemUi by remember { mutableStateOf(defaultScopes.systemUi) }
    var restartPlugin by remember { mutableStateOf(defaultScopes.plugin) }
    var restartMiLink by remember { mutableStateOf(defaultScopes.miLink) }
    var restartXiaomiHealth by remember { mutableStateOf(defaultScopes.xiaomiHealth) }
    var restartMarket by remember { mutableStateOf(defaultScopes.market) }
    var restartMiHome by remember { mutableStateOf(defaultScopes.miHome) }
    var restartAmap by remember { mutableStateOf(defaultScopes.amap) }
    var restartXiaomiCommunity by remember { mutableStateOf(defaultScopes.xiaomiCommunity) }
    var restartSpotify by remember { mutableStateOf(defaultScopes.spotify) }
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
                topBar = {
                    Column(Modifier.fillMaxWidth()) {
                        SmallTopAppBar(
                            title = destination.label,
                            color = Color.Transparent,
                            titleColor = MiuixTheme.colorScheme.onSurface,
                            navigationIcon = { BackIconButton(onBack) },
                            actions = {
                                RestartScopeTopBarButton {
                                    restartState = ScopeRestartState.Ready
                                    restartSystemUi = defaultScopes.systemUi
                                    restartPlugin = defaultScopes.plugin
                                    restartMiLink = defaultScopes.miLink
                                    restartXiaomiHealth = defaultScopes.xiaomiHealth
                                    restartMarket = defaultScopes.market
                                    restartMiHome = defaultScopes.miHome
                                    restartAmap = defaultScopes.amap
                                    restartXiaomiCommunity = defaultScopes.xiaomiCommunity
                                    restartSpotify = defaultScopes.spotify
                                    restartSystem = false
                                    showRestartDialog = true
                                }
                            },
                        )
                        if (destination == SettingsDestination.HeadsUpGlassAdvanced) {
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
                            restartPlugin = restartPlugin,
                            restartMiLink = restartMiLink,
                            restartXiaomiHealth = restartXiaomiHealth,
                            restartMarket = restartMarket,
                            restartMiHome = restartMiHome,
                            restartAmap = restartAmap,
                            restartXiaomiCommunity = restartXiaomiCommunity,
                            restartSpotify = restartSpotify,
                            restartSystem = restartSystem,
                            onSystemUiChange = { restartSystemUi = it },
                            onPluginChange = {
                                restartPlugin = it
                                if (it) restartSystemUi = true
                            },
                            onMiLinkChange = { restartMiLink = it },
                            onXiaomiHealthChange = { restartXiaomiHealth = it },
                            onMarketChange = { restartMarket = it },
                            onMiHomeChange = { restartMiHome = it },
                            onAmapChange = { restartAmap = it },
                            onXiaomiCommunityChange = { restartXiaomiCommunity = it },
                            onSpotifyChange = { restartSpotify = it },
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartPlugin = false
                                    restartMiLink = false
                                    restartXiaomiHealth = false
                                    restartMarket = false
                                    restartMiHome = false
                                    restartAmap = false
                                    restartXiaomiCommunity = false
                                    restartSpotify = false
                                }
                            },
                            onDismiss = { showRestartDialog = false },
                            onConfirm = {
                                restartState = ScopeRestartState.Restarting
                                restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartXiaomiHealth, restartMarket, restartMiHome, restartAmap, restartXiaomiCommunity, restartSpotify, restartSystem) {
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
                    )
                    HeadsUpGlassPresetDialog(
                        mode = headsUpGlassPresetDialog,
                        settings = settings,
                        onDismissRequest = { headsUpGlassPresetDialog = null },
                        onImport = {
                            update(it)
                            headsUpGlassPresetDialog = null
                        },
                    )
                },
            ) { padding ->
                when (destination) {
                    SettingsDestination.NotificationsControlCenter -> NotificationControlCenterSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                    ) { scrollProgress = it }
                    SettingsDestination.HeadsUpNotifications -> HeadsUpNotificationsSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        onOpenHeadsUpGlassSettings = { context.openDetailSettings(SettingsDestination.HeadsUpGlass) },
                    ) { scrollProgress = it }
                    SettingsDestination.HeadsUpGlass -> HeadsUpGlassSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        onOpenAdvanced = {
                            context.openDetailSettings(SettingsDestination.HeadsUpGlassAdvanced)
                        },
                        onOpenPresetDialog = { headsUpGlassPresetDialog = it },
                    ) { scrollProgress = it }
                    SettingsDestination.HeadsUpGlassAdvanced -> HeadsUpGlassAdvancedSettingsPage(
                        padding = padding,
                        settings = settings,
                        update = ::update,
                        selectedTab = glassEffectTab,
                        scrollState = selectedGlassScroll,
                        onEditParameter = { dark, index ->
                            editingHeadsUpGlassParameter = HeadsUpGlassParameterTarget(dark, index)
                        },
                    )
                    SettingsDestination.XiaomiHealth -> XiaomiHealthSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Market -> MarketSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.MiHome -> MiHomeSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Amap -> AmapSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.XiaomiCommunity -> XiaomiCommunitySettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Spotify -> SpotifySettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Media -> MediaSettingsPage(
                        padding,
                        settings,
                        ::update,
                        { context.openDetailSettings(SettingsDestination.MediaConstraintSet) },
                    ) { scrollProgress = it }
                    SettingsDestination.MediaConstraintSet -> MediaConstraintSetSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Lockscreen -> LockscreenSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.StatusBar -> StatusBarSettingsPage(padding, settings, ::update) { scrollProgress = it }
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
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(16.dp),
        )
    },
)

@Composable
private fun RestoreDefaultsIconButton(onClick: () -> Unit) = AdvancedTopBarIconButton(onClick = onClick) {
    Icon(
        painter = rememberVectorPainter(MiuixIcons.Reset),
        contentDescription = "恢复预设（Reset）",
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
    )
}

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
            shadowRadiusScale = 0.34f,
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
                painter = rememberVectorPainter(MiuixIcons.Refresh),
                contentDescription = "重新启动以应用修改",
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RestoreDefaultsDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onRestoreSystemDefault: () -> Unit,
    onRestoreModuleDefault: () -> Unit,
) = DeadlinerMiuixDialog(
    show = show,
    title = "恢复预设",
    summary = "系统默认会停用全部修改；模块默认会恢复 HyperModifier 的推荐预设。保存后请重启相关作用域。",
    onDismissRequest = onDismissRequest,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            "恢复系统默认",
            onRestoreSystemDefault,
            modifier = Modifier.weight(1f),
        )
        Button(
            onRestoreModuleDefault,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("恢复模块默认") }
    }
}

private data class ScopeStatus(
    val appName: String,
    val packageName: String,
    val hasModification: Boolean,
    val isActive: Boolean,
)

private object ModuleScopePackage {
    const val SYSTEM_UI = "com.android.systemui"
    const val PLUGIN = "miui.systemui.plugin"
    const val MILINK = "com.milink.service"
    const val XIAOMI_HEALTH = "com.mi.health"
    const val MARKET = "com.xiaomi.market"
    const val MI_HOME = "com.xiaomi.smarthome"
    const val AMAP = "com.autonavi.minimap"
    const val XIAOMI_COMMUNITY = "com.xiaomi.vipaccount"
}

private fun scopeStatuses(
    settings: ModifierSettings,
    framework: ModuleFrameworkState.Snapshot,
): List<ScopeStatus> = listOf(
    ScopeStatus(
        appName = "系统界面",
        packageName = ModuleScopePackage.SYSTEM_UI,
        hasModification = listOf(
            settings.notificationsEnabled,
            settings.hideHeadsUpMiniBar,
            settings.headsUpBottomMarginEnabled,
            settings.headsUpGlassParametersEnabled,
            settings.globalBackgroundBlurPercent != 100f,
            settings.mediaEnabled,
            settings.hideAodActions,
            settings.hideAodSeamless,
            settings.sinkLockscreenNotificationsForFingerprint,
            settings.hideLockscreenFingerprintIcon,
            settings.lowerLockscreenPasswordPage,
            settings.showLockscreenFingerprintIconOnAod,
            settings.aodClockWeightEnabled,
            settings.lockscreenPinKeySoftGlassEnabled,
            settings.statusBarNetworkTypeEnabled,
            settings.volumePanelRadius > 0f,
            settings.islandEnabled,
        ).any { it },
        isActive = framework.isActive(ModuleScopePackage.SYSTEM_UI),
    ),
    ScopeStatus(
        appName = "系统界面插件",
        packageName = ModuleScopePackage.PLUGIN,
        hasModification = settings.controlCenterEnabled || settings.globalBackgroundBlurPercent != 100f,
        isActive = framework.isActive(ModuleScopePackage.PLUGIN),
    ),
    ScopeStatus(
        appName = "小米互联服务",
        packageName = ModuleScopePackage.MILINK,
        hasModification = settings.miLinkMainCardsEnabled || settings.globalBackgroundBlurPercent != 100f,
        isActive = framework.isActive(ModuleScopePackage.MILINK),
    ),
    ScopeStatus(
        appName = "小米运动健康",
        packageName = ModuleScopePackage.XIAOMI_HEALTH,
        hasModification = settings.xiaomiHealthFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.XIAOMI_HEALTH),
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
        appName = "小米社区",
        packageName = ModuleScopePackage.XIAOMI_COMMUNITY,
        hasModification = settings.xiaomiCommunityFloatingNavigationEnabled,
        isActive = framework.isActive(ModuleScopePackage.XIAOMI_COMMUNITY),
    ),
)

@Composable
private fun HomeDashboardPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    selectedSection: Int,
    scrollState: ScrollState,
    onNavigate: (SettingsDestination) -> Unit,
) = SettingsScrollPage(padding, {}, scrollState) {
    var showScopeStatus by remember { mutableStateOf(false) }
    val framework by ModuleFrameworkState.snapshot
    val scopes = scopeStatuses(settings, framework)

    ModuleStatusHero(scopes, framework, showScopeStatus) { showScopeStatus = !showScopeStatus }
    if (showScopeStatus) {
        SettingsSection {
            scopes.forEach { scope ->
                ScopeStatusItem(
                    appName = scope.appName,
                    hasModification = scope.hasModification,
                    isActive = scope.isActive,
                )
            }
        }
    }
    if (selectedSection == 0) {
        SettingsSection {
            SettingsSliderItemWithLabel(
                label = "底栏额外抬高",
                value = settings.hyperGlassifyHiddenNavigationLift,
                valueRange = 0f..48f,
                onValueChange = { update(settings.copy(hyperGlassifyHiddenNavigationLift = it)) },
                steps = 47,
            )
        }
        SettingsSection {
            NavigationSettingItem("小米运动健康", "", onClick = { onNavigate(SettingsDestination.XiaomiHealth) }, leadingContent = { HomeAppIcon(ModuleScopePackage.XIAOMI_HEALTH) })
            NavigationSettingItem("应用商店", "", onClick = { onNavigate(SettingsDestination.Market) }, leadingContent = { HomeAppIcon(ModuleScopePackage.MARKET) })
            NavigationSettingItem("米家", "", onClick = { onNavigate(SettingsDestination.MiHome) }, leadingContent = { HomeAppIcon(ModuleScopePackage.MI_HOME) })
            NavigationSettingItem("高德地图", "实验功能", onClick = { onNavigate(SettingsDestination.Amap) }, leadingContent = { HomeAppIcon(ModuleScopePackage.AMAP) })
            NavigationSettingItem("小米社区", "", onClick = { onNavigate(SettingsDestination.XiaomiCommunity) }, leadingContent = { HomeAppIcon(ModuleScopePackage.XIAOMI_COMMUNITY) })
        }
    } else {
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
            NavigationSettingItem("媒体组件", "", onClick = { onNavigate(SettingsDestination.Media) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_music_note, Color(0xFF843AD4)) })
            NavigationSettingItem("锁屏", "", onClick = { onNavigate(SettingsDestination.Lockscreen) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_lock, Color(0xFF008F7D)) })
            NavigationSettingItem("状态栏", "", onClick = { onNavigate(SettingsDestination.StatusBar) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_signal_cellular_alt, Color(0xFF087EBA)) })
            NavigationSettingItem("音量面板", "", onClick = { onNavigate(SettingsDestination.Volume) }, leadingContent = { HomeSystemIcon(R.drawable.ic_home_volume_up, Color(0xFFC52C79)) })
        }
    }
}

@Composable
private fun ModuleStatusHero(
    scopes: List<ScopeStatus>,
    framework: ModuleFrameworkState.Snapshot,
    showScopeStatus: Boolean,
    onClick: () -> Unit,
) {
    val modifiedScopes = scopes.filter { it.hasModification }
    val activeModifiedScopes = modifiedScopes.count { it.isActive }
    val fullyActive = modifiedScopes.isNotEmpty() && activeModifiedScopes == modifiedScopes.size
    val status = when {
        modifiedScopes.isEmpty() -> "未配置作用域"
        !framework.connected -> "模块未连接 LSPosed"
        framework.apiVersion < ModuleFrameworkState.MIN_SUPPORTED_API -> "LSPosed API 版本过低"
        activeModifiedScopes == 0 -> "有修改的作用域未激活"
        fullyActive -> "作用域已激活"
        else -> "作用域激活不完全"
    }
    val dark = isSystemInDarkTheme()
    // Miuix has no success token. This app-owned pair deliberately represents module health.
    val accent = if (dark) Color(0xFF38D169) else Color(0xFF20C764)
    val successContainer = if (dark) Color(0xFF153D28) else Color(0xFFDDF8E6)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(176.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (fullyActive) successContainer else MiuixTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        // Draw first so the oversized check stays behind the status copy.
        if (fullyActive) {
            Icon(
                painter = painterResource(R.drawable.ic_module_status_ready),
                contentDescription = "模块工作中",
                tint = accent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 32.dp, y = 32.dp)
                    .size(154.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text(
                text = status,
                style = MiuixTheme.textStyles.title2,
            )
            Text(
                text = "版本：${BuildConfig.VERSION_NAME}",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = when {
                showScopeStatus -> "点击收起作用域状态"
                modifiedScopes.isEmpty() -> "从下方选择修改位置"
                !framework.connected -> "请确认模块已在 LSPosed 中启用 · 点击查看"
                framework.apiVersion < ModuleFrameworkState.MIN_SUPPORTED_API ->
                    "需要 LSPosed API ${ModuleFrameworkState.MIN_SUPPORTED_API} 或更高版本"
                else -> "${activeModifiedScopes}/${modifiedScopes.size} 个已修改作用域激活 · 点击查看"
            },
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 22.dp),
        )
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
