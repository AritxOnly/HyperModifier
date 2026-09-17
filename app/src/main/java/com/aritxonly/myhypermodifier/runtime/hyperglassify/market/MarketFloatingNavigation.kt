package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarDefaults
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import java.util.WeakHashMap
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Replaces only Market's visual tab strip; native TabViews still own navigation and analytics. */
internal object MarketFloatingNavigation {
    private val hosts = WeakHashMap<Activity, MarketNavigationHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !ModuleSettings.marketFloatingNavigationEnabled) return
        val suppressor = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = {
                val id = activity.resources.getIdentifier(
                    "tab_container_layout",
                    "id",
                    activity.packageName,
                )
                id.takeIf { it != 0 }?.let(activity::findViewById)
            },
        ).also(EarlyBottomBarSuppressor::start)
        startupSuppressors[activity] = suppressor
        val startedAt = SystemClock.uptimeMillis()
        lateinit var retry: Runnable
        retry = Runnable {
            if (activity.isFinishing || activity.isDestroyed) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            ModuleSettings.ensureLoaded()
            val settingsTimedOut = SystemClock.uptimeMillis() - startedAt >= SETTINGS_WAIT_TIMEOUT_MS
            if (!ModuleSettings.isLoaded() && !settingsTimedOut) {
                activity.window.decorView.postDelayed(retry, SETTINGS_RETRY_MS)
                return@Runnable
            }
            if (!ModuleSettings.marketFloatingNavigationEnabled) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            suppressor.restore()
            val host = MarketNavigationHost.create(activity)
            if (host == null && SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                suppressor.start()
                activity.window.decorView.postDelayed(retry, VIEW_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
            if (host == null) {
                Log.w(TAG, "Market 4.125.11 navigation views were not found")
            } else {
                hosts[activity] = host
            }
        }
        pendingAttachments[activity] = retry
        activity.window.decorView.post(retry)
    }

    @JvmStatic
    fun dispose(activity: Activity) {
        pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
        startupSuppressors.remove(activity)?.restore()
        hosts.remove(activity)?.dispose()
    }

    @JvmStatic
    fun onTouchEvent(activity: Activity, event: MotionEvent) {
        hosts[activity]?.onTouchEvent(event)
    }

    private const val SETTINGS_RETRY_MS = 32L
    private const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
    private const val VIEW_RETRY_MS = 32L
    private const val VIEW_WAIT_TIMEOUT_MS = 4_000L
    private const val TAG = "MyHyperModifier"
}

private data class MarketTabState(
    val label: String,
    val tag: String,
    val badge: Boolean,
    val icons: NativeTabIconPair?,
)

private data class MarketNavigationState(
    val tabs: List<MarketTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
)

private class MarketNavigationHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val originalBottomContainer: View,
    private val basicModeContainer: View?,
    private val nativeTabLayout: View,
    private val contentView: View,
    private val navigationBarPlaceholder: View?,
    samplingView: View,
) {
    private var state by mutableStateOf(MarketNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val previousLifecycleOwner = overlayParent.findViewTreeLifecycleOwner()
    private val previousViewModelStoreOwner = overlayParent.findViewTreeViewModelStoreOwner()
    private val previousSavedStateRegistryOwner = overlayParent.findViewTreeSavedStateRegistryOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val sampler = ViewBackdropSampler(samplingView) { backdropSnapshot = it }
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val originalBottomAlpha = originalBottomContainer.alpha
    private val originalBottomAccessibility = originalBottomContainer.importantForAccessibility
    private val originalContentBottomMargin =
        (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin
    private val originalNavigationPlaceholderVisibility = navigationBarPlaceholder?.visibility
    private var replacingNativeChrome = false
    private val composeView = ComposeView(activity)
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncNativeState()
        sampler.onFrame()
        true
    }

    init {
        overlayParent.setViewTreeLifecycleOwner(owner)
        overlayParent.setViewTreeViewModelStoreOwner(owner)
        overlayParent.setViewTreeSavedStateRegistryOwner(owner)
        composeView.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MarketNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = sampler::setNavigationBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        syncNativeState()
        overlayParent.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun dispose() {
        sampler.dispose()
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        (composeView.parent as? ViewGroup)?.removeView(composeView)
        originalBottomContainer.alpha = originalBottomAlpha
        originalBottomContainer.importantForAccessibility = originalBottomAccessibility
        originalContentBottomMargin?.let { margin ->
            (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.bottomMargin = margin
                contentView.layoutParams = params
            }
        }
        originalNavigationPlaceholderVisibility?.let { navigationBarPlaceholder?.visibility = it }
        windowImmersion.dispose()
        overlayParent.setViewTreeLifecycleOwner(previousLifecycleOwner)
        overlayParent.setViewTreeViewModelStoreOwner(previousViewModelStoreOwner)
        overlayParent.setViewTreeSavedStateRegistryOwner(previousSavedStateRegistryOwner)
        owner.dispose()
    }

    private fun updateNativeChromeReplacement(enabled: Boolean) {
        if (enabled) {
            if (!replacingNativeChrome) {
                replacingNativeChrome = true
                windowImmersion.apply()
            }
            windowImmersion.ensureApplied()
            if (originalBottomContainer.alpha != 0f) originalBottomContainer.alpha = 0f
            originalBottomContainer.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (navigationBarPlaceholder?.visibility != View.GONE) {
                navigationBarPlaceholder?.visibility = View.GONE
            }
            removeContentReservation()
        } else if (replacingNativeChrome) {
            replacingNativeChrome = false
            originalBottomContainer.alpha = originalBottomAlpha
            originalBottomContainer.importantForAccessibility = originalBottomAccessibility
            restoreContentReservation()
            originalNavigationPlaceholderVisibility?.let { navigationBarPlaceholder?.visibility = it }
            windowImmersion.dispose()
        }
    }

    private fun removeContentReservation() {
        (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            if (params.bottomMargin != 0) {
                params.bottomMargin = 0
                contentView.layoutParams = params
            }
        }
    }

    private fun restoreContentReservation() {
        originalContentBottomMargin?.let { margin ->
            (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                if (params.bottomMargin != margin) {
                    params.bottomMargin = margin
                    contentView.layoutParams = params
                }
            }
        }
    }

    private fun syncNativeState() {
        val tabs = nativeTabViews()
        val selected = runCatching {
            nativeTabLayout.javaClass.getMethod("getSelectedIndex").invoke(nativeTabLayout) as Int
        }.getOrDefault(state.selectedIndex).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val next = MarketNavigationState(
            tabs = tabs.mapIndexed { index, tab -> tab.readState(index, index == selected) },
            selectedIndex = selected,
            visible = originalBottomContainer.visibility == View.VISIBLE &&
                nativeTabLayout.visibility == View.VISIBLE &&
                basicModeContainer?.visibility != View.VISIBLE && tabs.size > 1,
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (next != state) state = next
        updateNativeChromeReplacement(next.visible)
        composeView.visibility = if (next.visible) View.VISIBLE else View.GONE
        if (selectionChanged || becameVisible) sampler.requestCaptureBurst()
    }

    private fun View.readState(index: Int, selected: Boolean): MarketTabState {
        val label = runCatching {
            (javaClass.getMethod("getTitleView").invoke(this) as? TextView)?.text?.toString()
        }.getOrNull().orEmpty().ifBlank { FALLBACK_LABELS.getOrElse(index) { "入口 ${index + 1}" } }
        val tag = runCatching {
            javaClass.getMethod("getTabViewTag").invoke(this)?.toString()
        }.getOrNull().orEmpty()
        val hasRedPoint = runCatching {
            javaClass.getMethod("hasRedPoint").invoke(this) as Boolean
        }.getOrDefault(false)
        val number = runCatching {
            javaClass.getMethod("getNumber").invoke(this) as Int
        }.getOrDefault(0)
        val iconView = runCatching {
            javaClass.getMethod("getIconView").invoke(this) as? ImageView
        }.getOrNull()
        return MarketTabState(
            label = label,
            tag = tag,
            badge = ModuleSettings.marketNavigationBadgesEnabled && (hasRedPoint || number > 0),
            icons = iconSnapshotter.snapshot(this, iconView, selected),
        )
    }

    private fun selectDestination(index: Int) {
        val tab = nativeTabViews().getOrNull(index) ?: return
        state = state.copy(selectedIndex = index)
        tab.performClick()
        tab.post(::syncNativeState)
        sampler.requestCaptureBurst()
    }

    @Suppress("UNCHECKED_CAST")
    private fun nativeTabViews(): List<View> = runCatching {
        (nativeTabLayout.javaClass.getMethod("getTabViews").invoke(nativeTabLayout) as? List<*>)
            ?.filterIsInstance<View>().orEmpty()
    }.getOrDefault(emptyList())

    companion object {
        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")

        fun create(activity: Activity): MarketNavigationHost? = runCatching {
            val resources = activity.resources
            fun id(name: String): Int = resources.getIdentifier(name, "id", activity.packageName)

            val bottom = activity.findViewById<View>(id("tab_container_layout")) ?: return null
            val tabLayout = activity.findViewById<View>(id("tab_container")) ?: return null
            val content = activity.findViewById<View>(id("fragment_container")) ?: return null
            val samplingView = activity.findViewById<View>(android.R.id.content) ?: content
            val overlayParent = activity.window.decorView as? ViewGroup ?: return null
            MarketNavigationHost(
                activity = activity,
                overlayParent = overlayParent,
                originalBottomContainer = bottom,
                basicModeContainer = activity.findViewById(id("tab_basic_mode_container_layout")),
                nativeTabLayout = tabLayout,
                contentView = content,
                navigationBarPlaceholder = activity.findViewById(id("navigation_bar_placeholder")),
                samplingView = samplingView,
            )
        }.onFailure {
            Log.e("MyHyperModifier", "Could not attach Market navigation overlay", it)
        }.getOrNull()
    }
}

@Composable
private fun MarketNavigationContent(
    state: MarketNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    val dark = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    val backdrop = rememberLayerBackdrop()
    val hiddenNavigationLift = hyperGlassifyHiddenNavigationLift()
    val items = state.tabs.mapIndexed { index, tab ->
        val nativeIcons = tab.icons?.takeUnless { ModuleSettings.marketMiuixIconsEnabled }
        val monochrome = nativeIcons != null && ModuleSettings.marketMonochromeIconsEnabled
        val selectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.selectedMonochrome else it.selected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(marketIcon(tab, index))
        val unselectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.unselectedMonochrome else it.unselected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(marketIcon(tab, index))
        MiuixFloatingTabItem(
            key = index.toString(),
            label = tab.label,
            selectedIcon = selectedPainter,
            unselectedIcon = unselectedPainter,
            preserveOriginalIconColors = nativeIcons != null && !monochrome,
            badge = if (tab.badge) "" else null,
        )
    }

    MaterialTheme(colorScheme = materialColors) {
        MiuixTheme(colors = miuixColors) {
            CompositionLocalProvider(
                LocalAdvancedMaterialSpec provides AdvancedMaterialSpec(enabled = true),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 16.dp,
                            top = INJECTED_NAVIGATION_SHADOW_TOP_PADDING,
                            end = 16.dp,
                            bottom = 4.dp + hiddenNavigationLift,
                        )
                        .height(MiuixFloatingTabBarDefaults.Height),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewBackdropLayer(backdropSnapshot, backdrop)
                    MiuixFloatingTabBar(
                        items = items,
                        selectedKey = state.selectedIndex.toString(),
                        onItemSelected = { onDestinationSelected(it.key.toInt()) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            onBackdropBoundsChanged(
                                ViewBackdropBounds(
                                    left = position.x.roundToInt(),
                                    top = position.y.roundToInt(),
                                    width = coordinates.size.width,
                                    height = coordinates.size.height,
                                ),
                            )
                        },
                        layout = MiuixFloatingTabLayout.Stacked,
                        backdrop = backdropSnapshot?.let { backdrop },
                    )
                }
            }
        }
    }
}

private fun marketIcon(tab: MarketTabState, index: Int): ImageVector {
    val identity = "${tab.tag} ${tab.label}".lowercase()
    return when {
        identity.contains("home") || identity.contains("首页") || identity.contains("推荐") ->
            MiuixIcons.Home
        identity.contains("game") || identity.contains("游戏") -> MiuixIcons.AppRecording
        identity.contains("rank") || identity.contains("榜") || identity.contains("排行") ->
            MiuixIcons.TopDownloads
        identity.contains("mine") || identity.contains("我的") || identity.contains("账户") ->
            MiuixIcons.ContactsCircle
        else -> listOf(
            MiuixIcons.Home,
            MiuixIcons.AppRecording,
            MiuixIcons.TopDownloads,
            MiuixIcons.ContactsCircle,
            MiuixIcons.All,
        )[index.coerceIn(0, 4)]
    }
}
