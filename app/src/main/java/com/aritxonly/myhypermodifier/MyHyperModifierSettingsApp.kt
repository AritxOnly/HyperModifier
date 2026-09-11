package com.aritxonly.myhypermodifier

import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
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
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.core.graphics.ColorUtils

internal enum class SettingsDestination(val key: String, val label: String) {
    Home("home", "主页"),
    Notifications("notifications", "通知中心"),
    ControlCenter("control-center", "控制中心"),
    MiLink("milink", "小米互联服务"),
    XiaomiHealth("xiaomi-health", "小米运动健康"),
    Market("market", "应用商店"),
    Media("media", "媒体组件"),
    MediaConstraintSet("media-constraint-set", "媒体布局编辑"),
    Lockscreen("lockscreen", "锁屏指纹与通知"),
    StatusBar("status-bar", "状态栏"),
    Volume("volume", "音量面板"),
    About("about", "关于"),
    ;

    val isTopLevel: Boolean get() = this == Home || this == About
    val supportsRestore: Boolean get() = this != About

    companion object {
        fun fromKey(key: String?): SettingsDestination? = entries.firstOrNull { it.key == key }
    }
}
private enum class ScopeRestartState { Ready, Restarting, Succeeded, Failed }

private data class RestartScopeDefaults(
    val systemUi: Boolean = false,
    val plugin: Boolean = false,
    val miLink: Boolean = false,
    val xiaomiHealth: Boolean = false,
    val market: Boolean = false,
)

private fun SettingsDestination.requiredRestartScopes(): RestartScopeDefaults = when (this) {
    SettingsDestination.ControlCenter -> RestartScopeDefaults(systemUi = true, plugin = true)
    SettingsDestination.MiLink -> RestartScopeDefaults(miLink = true)
    SettingsDestination.XiaomiHealth -> RestartScopeDefaults(xiaomiHealth = true)
    SettingsDestination.Market -> RestartScopeDefaults(market = true)
    SettingsDestination.Notifications,
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
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    var restartState by remember { mutableStateOf(ScopeRestartState.Ready) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartSystemUi by remember { mutableStateOf(true) }
    var restartPlugin by remember { mutableStateOf(true) }
    var restartMiLink by remember { mutableStateOf(false) }
    var restartXiaomiHealth by remember { mutableStateOf(false) }
    var restartMarket by remember { mutableStateOf(false) }
    var restartSystem by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    val topBarButtonMaterialTarget = resolveTopBarButtonMaterialProgress(
        collapsedFraction = null,
        scrollProgress = scrollProgress,
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
                                actions = { RestoreDefaultsIconButton { showRestoreDialog = true } },
                            )
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
                    DeadlinerMiuixDialog(showRestartDialog, "重启作用域", restartSummary(restartState), {
                        if (restartState != ScopeRestartState.Restarting) showRestartDialog = false
                    }) {
                        RestartScopeChoices(
                            state = restartState,
                            restartSystemUi = restartSystemUi,
                            restartPlugin = restartPlugin,
                            restartMiLink = restartMiLink,
                            restartXiaomiHealth = restartXiaomiHealth,
                            restartMarket = restartMarket,
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
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartPlugin = false
                                    restartMiLink = false
                                    restartXiaomiHealth = false
                                    restartMarket = false
                                }
                            },
                        )
                        DialogActions(restartState, { showRestartDialog = false }) {
                            restartState = ScopeRestartState.Restarting
                            restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartXiaomiHealth, restartMarket, restartSystem) {
                                restartState = if (it) ScopeRestartState.Succeeded else ScopeRestartState.Failed
                            }
                        }
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
                        onNavigate = context::openDetailSettings,
                        onScroll = { scrollProgress = it },
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
    val defaultScopes = remember(destination) { destination.requiredRestartScopes() }
    var restartState by remember { mutableStateOf(ScopeRestartState.Ready) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartSystemUi by remember { mutableStateOf(defaultScopes.systemUi) }
    var restartPlugin by remember { mutableStateOf(defaultScopes.plugin) }
    var restartMiLink by remember { mutableStateOf(defaultScopes.miLink) }
    var restartXiaomiHealth by remember { mutableStateOf(defaultScopes.xiaomiHealth) }
    var restartMarket by remember { mutableStateOf(defaultScopes.market) }
    var restartSystem by remember { mutableStateOf(false) }
    val topBarButtonMaterialTarget = resolveTopBarButtonMaterialProgress(
        collapsedFraction = null,
        scrollProgress = scrollProgress,
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
                scrollProgress = scrollProgress,
                topBar = {
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
                                restartSystem = false
                                showRestartDialog = true
                            }
                        },
                    )
                },
                bottomBar = {},
                overlay = {
                    DeadlinerMiuixDialog(showRestartDialog, "重启作用域", restartSummary(restartState), {
                        if (restartState != ScopeRestartState.Restarting) showRestartDialog = false
                    }) {
                        RestartScopeChoices(
                            state = restartState,
                            restartSystemUi = restartSystemUi,
                            restartPlugin = restartPlugin,
                            restartMiLink = restartMiLink,
                            restartXiaomiHealth = restartXiaomiHealth,
                            restartMarket = restartMarket,
                            restartSystem = restartSystem,
                            onSystemUiChange = { restartSystemUi = it },
                            onPluginChange = {
                                restartPlugin = it
                                if (it) restartSystemUi = true
                            },
                            onMiLinkChange = { restartMiLink = it },
                            onXiaomiHealthChange = { restartXiaomiHealth = it },
                            onMarketChange = { restartMarket = it },
                            onSystemChange = { checked ->
                                restartSystem = checked
                                if (checked) {
                                    restartSystemUi = false
                                    restartPlugin = false
                                    restartMiLink = false
                                    restartXiaomiHealth = false
                                    restartMarket = false
                                }
                            },
                        )
                        DialogActions(restartState, { showRestartDialog = false }) {
                            restartState = ScopeRestartState.Restarting
                            restartSelectedScope(restartSystemUi, restartPlugin, restartMiLink, restartXiaomiHealth, restartMarket, restartSystem) {
                                restartState = if (it) ScopeRestartState.Succeeded else ScopeRestartState.Failed
                            }
                        }
                    }
                },
            ) { padding ->
                when (destination) {
                    SettingsDestination.Notifications -> NotificationSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.ControlCenter -> ControlCenterSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.MiLink -> MiLinkSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.XiaomiHealth -> XiaomiHealthSettingsPage(padding, settings, ::update) { scrollProgress = it }
                    SettingsDestination.Market -> MarketSettingsPage(padding, settings, ::update) { scrollProgress = it }
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
private fun NavigationSettingItem(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = SettingItem(
    headlineText = title,
    supportingText = summary,
    enabled = enabled,
    onClick = onClick,
    trailingContent = {
        Text(
            "›",
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.title3,
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
        contentDescription = "重启当前页面所需作用域",
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
                contentDescription = "重启作用域",
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
    /** Null only while LSPosed's saved configuration cannot be read. */
    val isActive: Boolean?,
)

private object ModuleScopePackage {
    const val SYSTEM_UI = "com.android.systemui"
    const val PLUGIN = "miui.systemui.plugin"
    const val MILINK = "com.milink.service"
    const val XIAOMI_HEALTH = "com.mi.health"
    const val MARKET = "com.xiaomi.market"
}

private fun scopeStatuses(
    settings: ModifierSettings,
    configuration: LsposedScopeConfiguration,
): List<ScopeStatus> {
    val enabledPackages = (configuration as? LsposedScopeConfiguration.Available)?.enabledPackages
    fun enabled(packageName: String): Boolean? = enabledPackages?.contains(packageName)
    return listOf(
    ScopeStatus(
        appName = "系统界面",
        packageName = ModuleScopePackage.SYSTEM_UI,
        hasModification = listOf(
            settings.notificationsEnabled,
            settings.mediaEnabled,
            settings.hideAodActions,
            settings.hideAodSeamless,
            settings.sinkLockscreenNotificationsForFingerprint,
            settings.hideLockscreenFingerprintIcon,
            settings.showLockscreenFingerprintIconOnAod,
            settings.statusBarNetworkTypeEnabled,
            settings.volumePanelRadius > 0f,
            settings.islandEnabled,
        ).any { it },
        isActive = enabled(ModuleScopePackage.SYSTEM_UI),
    ),
    ScopeStatus(
        appName = "系统界面插件",
        packageName = ModuleScopePackage.PLUGIN,
        hasModification = settings.controlCenterEnabled,
        isActive = enabled(ModuleScopePackage.PLUGIN),
    ),
    ScopeStatus(
        appName = "小米互联服务",
        packageName = ModuleScopePackage.MILINK,
        hasModification = settings.miLinkMainCardsEnabled,
        isActive = enabled(ModuleScopePackage.MILINK),
    ),
    ScopeStatus(
        appName = "小米运动健康",
        packageName = ModuleScopePackage.XIAOMI_HEALTH,
        hasModification = settings.xiaomiHealthFloatingNavigationEnabled,
        isActive = enabled(ModuleScopePackage.XIAOMI_HEALTH),
    ),
    ScopeStatus(
        appName = "应用商店",
        packageName = ModuleScopePackage.MARKET,
        hasModification = settings.marketFloatingNavigationEnabled,
        isActive = enabled(ModuleScopePackage.MARKET),
    ),
    )
}

@Composable
private fun HomeDashboardPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    onNavigate: (SettingsDestination) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    val context = LocalContext.current
    var scopeConfiguration by remember { mutableStateOf<LsposedScopeConfiguration>(LsposedScopeConfiguration.Loading) }
    var showScopeStatus by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        while (true) {
            scopeConfiguration = withContext(Dispatchers.IO) { LsposedScopeReader.read(context) }
            delay(5_000L)
        }
    }
    val scopes = scopeStatuses(settings, scopeConfiguration)
    ModuleStatusHero(scopes, scopeConfiguration, showScopeStatus) { showScopeStatus = !showScopeStatus }
    if (showScopeStatus) {
        SettingsSection(topLabel = "作用域状态") {
            scopes.forEach { scope ->
                ScopeStatusItem(
                    appName = scope.appName,
                    packageName = scope.packageName,
                    hasModification = scope.hasModification,
                    isActive = scope.isActive,
                )
            }
        }
    }
    SettingsSection(topLabel = "HyperGlassify") {
        NavigationSettingItem("小米运动健康", "柔光玻璃悬浮底栏", onClick = { onNavigate(SettingsDestination.XiaomiHealth) })
        NavigationSettingItem("应用商店", "柔光玻璃悬浮底栏", onClick = { onNavigate(SettingsDestination.Market) })
    }
    SettingsSection(topLabel = "系统界面美化") {
        NavigationSettingItem("通知中心", "通知圆角", onClick = { onNavigate(SettingsDestination.Notifications) })
        NavigationSettingItem("控制中心", "圆角与各个表面", onClick = { onNavigate(SettingsDestination.ControlCenter) })
        NavigationSettingItem("小米互联服务", "融合设备中心卡片", onClick = { onNavigate(SettingsDestination.MiLink) })
        NavigationSettingItem("媒体组件", "锁屏媒体、灵动岛高度、进度条光效与 ConstraintSet", onClick = { onNavigate(SettingsDestination.Media) })
        NavigationSettingItem("锁屏指纹与通知", "指纹图标与通知避让", onClick = { onNavigate(SettingsDestination.Lockscreen) })
        NavigationSettingItem("状态栏", "网络类型文字", onClick = { onNavigate(SettingsDestination.StatusBar) })
        NavigationSettingItem("音量面板", "音量键弹出面板的外层圆角", onClick = { onNavigate(SettingsDestination.Volume) })
    }
}

@Composable
private fun ModuleStatusHero(
    scopes: List<ScopeStatus>,
    configuration: LsposedScopeConfiguration,
    showScopeStatus: Boolean,
    onClick: () -> Unit,
) {
    val modifiedScopes = scopes.filter { it.hasModification }
    val activeModifiedScopes = modifiedScopes.count { it.isActive == true }
    val fullyActive = modifiedScopes.isNotEmpty() && activeModifiedScopes == modifiedScopes.size
    val status = when {
        modifiedScopes.isEmpty() -> "未配置作用域"
        configuration is LsposedScopeConfiguration.Loading -> "正在读取作用域"
        configuration is LsposedScopeConfiguration.Unavailable -> "无法读取 LSPosed 作用域"
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
                configuration !is LsposedScopeConfiguration.Available -> "请确认已授予 root 权限 · 点击查看"
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
    packageName: String,
    hasModification: Boolean,
    isActive: Boolean?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(appName, style = MiuixTheme.textStyles.body1)
            Text(
                "$packageName · ${when {
                    !hasModification -> "未配置"
                    isActive == true -> "LSPosed 已启用"
                    isActive == false -> "LSPosed 未启用"
                    else -> "无法读取 LSPosed 配置"
                }}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            painter = painterResource(if (isActive == true) R.drawable.ic_scope_enabled else R.drawable.ic_scope_disabled),
            contentDescription = if (isActive == true) "$appName 作用域已激活" else "$appName 作用域未激活",
            tint = if (isActive == true) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable private fun NotificationSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "通知中心") {
        SettingsSwitchItem("通知圆角", "notification_item_bg_radius · ${settings.notificationRadius.toInt()} dp", settings.notificationsEnabled, { update(settings.copy(notificationsEnabled = it)) })
        SettingsSliderItemWithLabel("圆角大小", settings.notificationRadius, 12f..48f, { update(settings.copy(notificationRadius = it)) }, steps = 17, enabled = settings.notificationsEnabled)
    }
}

@Composable private fun ControlCenterSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
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
}

@Composable private fun MiLinkSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
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
}

@Composable
private fun XiaomiHealthSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "小米运动健康") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "读取应用自身的两态图标，用 Compose 渲染四个主入口，并让页面沉浸到透明导航栏下方",
            settings.xiaomiHealthFloatingNavigationEnabled,
            { update(settings.copy(xiaomiHealthFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用 MIUIX 图标",
            "关闭时优先使用小米运动健康原生底栏图标",
            settings.xiaomiHealthMiuixIconsEnabled,
            { update(settings.copy(xiaomiHealthMiuixIconsEnabled = it)) },
            enabled = settings.xiaomiHealthFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "使用单色图标",
            "将应用原生图标固定为柔光玻璃底栏的默认前景色",
            settings.xiaomiHealthMonochromeIconsEnabled,
            { update(settings.copy(xiaomiHealthMonochromeIconsEnabled = it)) },
            enabled = settings.xiaomiHealthFloatingNavigationEnabled &&
                !settings.xiaomiHealthMiuixIconsEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "小米运动健康 3.59.1；修改开关后需重启小米运动健康",
        )
    }
}

@Composable
private fun MarketSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "应用商店") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏",
            "动态读取原生主入口、两态图标并用 Compose 渲染，页面同时沉浸到透明导航栏下方",
            settings.marketFloatingNavigationEnabled,
            { update(settings.copy(marketFloatingNavigationEnabled = it)) },
        )
        SettingsSwitchItem(
            "使用 MIUIX 图标",
            "关闭时优先使用应用商店原生底栏图标",
            settings.marketMiuixIconsEnabled,
            { update(settings.copy(marketMiuixIconsEnabled = it)) },
            enabled = settings.marketFloatingNavigationEnabled,
        )
        SettingsSwitchItem(
            "使用单色图标",
            "将应用原生图标固定为柔光玻璃底栏的默认前景色",
            settings.marketMonochromeIconsEnabled,
            { update(settings.copy(marketMonochromeIconsEnabled = it)) },
            enabled = settings.marketFloatingNavigationEnabled &&
                !settings.marketMiuixIconsEnabled,
        )
        SettingsSwitchItem(
            "显示底栏角标",
            "同步应用商店原生 Tab 的红点和数字状态；关闭后不会清除应用内未读信息",
            settings.marketNavigationBadgesEnabled,
            { update(settings.copy(marketNavigationBadgesEnabled = it)) },
            enabled = settings.marketFloatingNavigationEnabled,
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem(
            "当前适配版本",
            "应用商店 4.125.11；基础模式和无底栏页面保持原样，修改开关后需重启应用商店",
        )
    }
}

@Composable private fun MediaSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onOpenConstraintEditor: () -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "锁屏媒体") {
        SettingsSwitchItem("启用媒体组件修改", "展开 ${settings.expandedHeight.toInt()} dp · 收起 ${settings.collapsedHeight.toInt()} dp", settings.mediaEnabled, { update(settings.copy(mediaEnabled = it)) })
        SettingsSliderItemWithLabel("展开高度", settings.expandedHeight, 120f..200f, { update(settings.copy(expandedHeight = it)) }, steps = 19, enabled = settings.mediaEnabled)
        SettingsSliderItemWithLabel("息屏收起高度", settings.collapsedHeight, 80f..160f, { update(settings.copy(collapsedHeight = it)) }, steps = 19, enabled = settings.mediaEnabled)
        SettingsSliderItemWithLabel("Full AOD 高度", settings.fullAodHeight, 56f..120f, { update(settings.copy(fullAodHeight = it)) }, steps = 15, enabled = settings.mediaEnabled)
        NavigationSettingItem("编辑 ConstraintSet XML", "预览并编辑普通 media_session 的布局约束", enabled = settings.mediaEnabled, onClick = onOpenConstraintEditor)
        SettingsSwitchItem("AOD 隐藏操作按钮", "退出 Full AOD 后自动恢复 action0–action4", settings.hideAodActions, { update(settings.copy(hideAodActions = it)) })
        SettingsSwitchItem("AOD 隐藏设备切换", "隐藏 media_seamless；退出 Full AOD 后自动恢复", settings.hideAodSeamless, { update(settings.copy(hideAodSeamless = it)) })
    }
    SettingsSection(topLabel = "灵动岛媒体") {
        SettingsSwitchItem("启用灵动岛高度", "expanded_island_height_dp · ${settings.islandHeight.toInt()} dp", settings.islandEnabled, { update(settings.copy(islandEnabled = it)) })
        SettingsSliderItemWithLabel("灵动岛高度", settings.islandHeight, 96f..200f, { update(settings.copy(islandHeight = it)) }, steps = 25, enabled = settings.islandEnabled)
        SettingsSwitchItem(
            "进度条光效",
            "普通媒体组件使用灵动岛 SeekProgressBar 光效",
            settings.islandProgressBar,
            { update(settings.copy(islandProgressBar = it)) },
            enabled = settings.islandEnabled,
        )
    }
}

@Composable private fun LockscreenSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "锁屏通知") {
        SettingsSwitchItem(
            "通知下沉",
            "开启后通知保持标准位置；关闭后为屏下指纹预留通知区域",
            settings.sinkLockscreenNotificationsForFingerprint,
            { update(settings.copy(sinkLockscreenNotificationsForFingerprint = it)) },
        )
    }
    SettingsSection(topLabel = "锁屏指纹") {
        SettingsSwitchItem(
            "隐藏锁屏指纹图标",
            "保留指纹触控区域；AOD 显示由下方选项控制",
            settings.hideLockscreenFingerprintIcon,
            { update(settings.copy(hideLockscreenFingerprintIcon = it)) },
        )
        SettingsSwitchItem(
            "AOD 显示锁屏指纹图标",
            "息屏常显时恢复指纹图标；普通锁屏仍按上方选项处理",
            settings.showLockscreenFingerprintIconOnAod,
            { update(settings.copy(showLockscreenFingerprintIconOnAod = it)) },
            enabled = settings.hideLockscreenFingerprintIcon,
        )
    }
}

@Composable private fun StatusBarSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "状态栏网络类型") {
        SettingsSwitchItem(
            "独立 4G/5G 标识",
            "恢复状态栏中的 5G / 4G 文字；修改后需重启 SystemUI",
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
                "与系统信号图标保持更清晰的视觉层级",
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

@Composable private fun VolumeSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "音量键弹出面板") {
        SettingsSliderItemWithLabel(
            "外层圆角",
            settings.volumePanelRadius,
            0f..64f,
            { update(settings.copy(volumePanelRadius = it)) },
            steps = 63,
            valueText = { if (it == 0f) "系统默认" else "${settingNumber(it)} dp" },
        )
        Text(
            "仅修改音量键唤起面板的外层容器，不会修改内部滑杆或滑杆填充圆角。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun MediaConstraintSetSettingsPage(padding: PaddingValues, settings: ModifierSettings, update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit) = SettingsScrollPage(padding, onScroll) {
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
            "仅覆盖普通 media_session；错误 XML 会安全回退模块默认布局",
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
private fun AboutSettingsPage(padding: PaddingValues, onScroll: (Float) -> Unit) {
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

@Composable private fun SettingsScrollPage(padding: PaddingValues, onScroll: (Float) -> Unit, content: @Composable () -> Unit) {
    val scroll = rememberScrollState(); val progress by remember(scroll) { derivedStateOf { (scroll.value / 32f).coerceIn(0f, 1f) } }; LaunchedEffect(progress) { onScroll(progress) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding).padding(horizontal = 16.dp).padding(bottom = 82.dp)) { content() }
}

@Composable private fun AboutAppHeader() = Column(Modifier.fillMaxWidth().height(404.dp).padding(top = 180.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Card(cornerRadius = 24.dp) { Image(painter = painterResource(R.drawable.ic_launcher_mhm), contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Fit) }
    Text("HyperModifier", style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text("v${BuildConfig.VERSION_NAME}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

private fun settingNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

private fun Context.openDetailSettings(destination: SettingsDestination) {
    if (destination.isTopLevel) return
    val intent = Intent(this, DetailSettingsActivity::class.java)
        .putExtra(DetailSettingsActivity.EXTRA_DESTINATION, destination.key)
    startActivity(intent)
}

@Composable private fun RestartScopeChoices(
    state: ScopeRestartState,
    restartSystemUi: Boolean,
    restartPlugin: Boolean,
    restartMiLink: Boolean,
    restartXiaomiHealth: Boolean,
    restartMarket: Boolean,
    restartSystem: Boolean,
    onSystemUiChange: (Boolean) -> Unit,
    onPluginChange: (Boolean) -> Unit,
    onMiLinkChange: (Boolean) -> Unit,
    onXiaomiHealthChange: (Boolean) -> Unit,
    onMarketChange: (Boolean) -> Unit,
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
    SettingItem(
        "小米运动健康",
        "停止 com.mi.health；下次打开时重新注入底栏",
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
        "停止 com.xiaomi.market；下次打开时重新注入底栏",
        enabled = editable && !restartSystem,
        trailingContent = {
            DeadlinerCheckbox(
                restartMarket,
                onMarketChange,
                enabled = editable && !restartSystem,
            )
        },
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
private fun restartSelectedScope(
    systemUi: Boolean,
    plugin: Boolean,
    miLink: Boolean,
    xiaomiHealth: Boolean,
    market: Boolean,
    system: Boolean,
    onCompleted: (Boolean) -> Unit,
) {
    Thread {
        // `am force-stop` merely marks the package stopped, and it does not recreate the control
        // centre plugin views.  Killing the SystemUI host lets system_server immediately start it
        // again and rebuilds both MIUISystemUI and MIUISystemUIPlugin content.
        val command = when {
            system -> "reboot"
            systemUi || plugin || miLink || xiaomiHealth || market -> buildList {
                if (systemUi || plugin) add("killall com.android.systemui")
                if (miLink) add("am force-stop com.milink.service")
                if (xiaomiHealth) add("am force-stop com.mi.health")
                if (market) add("am force-stop com.xiaomi.market")
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
