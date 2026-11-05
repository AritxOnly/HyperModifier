package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
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
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "MyHyperModifier"

internal object PiliPlusFloatingNavigation {
    private val hosts = WeakHashMap<Activity, PiliPlusNavigationHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()

    @JvmStatic
    fun prepare(@Suppress("UNUSED_PARAMETER") activity: Activity) = Unit

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.markLoaded(activity.applicationContext)
        if (ModuleSettings.isLoaded() && !enabled()) return
        val startedAt = SystemClock.uptimeMillis()
        lateinit var retry: Runnable
        retry = Runnable {
            if (activity.isFinishing || activity.isDestroyed) {
                pendingAttachments.remove(activity)
                return@Runnable
            }
            ModuleSettings.markLoaded(activity.applicationContext)
            val settingsTimedOut =
                SystemClock.uptimeMillis() - startedAt >= SETTINGS_WAIT_TIMEOUT_MS
            if (!ModuleSettings.isLoaded() && !settingsTimedOut) {
                activity.window.decorView.postDelayed(retry, SETTINGS_RETRY_MS)
                return@Runnable
            }
            if (!enabled()) {
                pendingAttachments.remove(activity)
                return@Runnable
            }
            val decor = activity.window.decorView
            val flutterView = PiliPlusSemanticsBridge.findFlutterView(decor)
            val bridge = flutterView?.let(PiliPlusSemanticsBridge::accessibilityBridge)
            if (flutterView == null || bridge == null) {
                if (SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                    decor.postDelayed(retry, VIEW_RETRY_MS)
                } else {
                    pendingAttachments.remove(activity)
                    Log.w(TAG, "PiliPlus Flutter accessibility bridge was not found")
                }
                return@Runnable
            }
            pendingAttachments.remove(activity)
            hosts[activity] = PiliPlusNavigationHost(activity, flutterView, bridge)
            Log.i(TAG, "Attached PiliPlus 2.1.6 liquid-glass navigation")
        }
        pendingAttachments[activity] = retry
        activity.window.decorView.post(retry)
    }

    @JvmStatic
    fun setForeground(activity: Activity, foreground: Boolean) {
        hosts[activity]?.setForeground(foreground)
    }

    @JvmStatic
    fun onTouchEvent(activity: Activity, event: MotionEvent) {
        hosts[activity]?.onTouchEvent(event)
    }

    @JvmStatic
    fun refresh(activity: Activity) {
        hosts[activity]?.refresh()
    }

    @JvmStatic
    fun dispose(activity: Activity) {
        pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
        hosts.remove(activity)?.dispose()
    }

    internal fun enabled() =
        ModuleSettings.moduleHooksEnabled && ModuleSettings.piliPlusFloatingNavigationEnabled

    private const val SETTINGS_RETRY_MS = 32L
    private const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
    private const val VIEW_RETRY_MS = 80L
    private const val VIEW_WAIT_TIMEOUT_MS = 8_000L
}

private data class PiliPlusNavigationState(
    val tabs: List<PiliPlusNavigationCandidate> = emptyList(),
    val selectedLabel: String = "",
    val visible: Boolean = false,
    val navigationLiftDp: Float = 2f,
    val navigationInsetPx: Int = 0,
    val darkTheme: Boolean = false,
)

private class PiliPlusNavigationHost(
    private val activity: Activity,
    private val flutterView: View,
    private val accessibilityBridge: Any,
) {
    private var state by mutableStateOf(PiliPlusNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val overlayParent = activity.window.decorView as ViewGroup
    private val owner = InjectedViewTreeOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val composeView = ComposeView(activity)
    private val visibility = FloatingNavigationVisibility(composeView, windowManager)
    private val refreshPolicy = NavigationRefreshPolicy(250L)
    private val stableNavigationInset = StableNavigationBarInset()
    private val density = activity.resources.displayMetrics.density
    private var composeWindowAttached = false
    private var foreground = true
    private var semanticsRequested = false
    private var ownsSemantics = false
    private var disposing = false
    private var loggedEmptyTree = false
    private var loggedUnavailableClick = false
    private val sampler = ViewBackdropSampler(
        source = overlayParent,
        excludedView = composeView,
        pixelCopyWindow = activity.window,
        usePixelCopySampling = { state.visible },
        allowSoftwareFallback = false,
        matchDisplayRefreshRate = true,
        sampleOnSourceFrame = true,
        pixelCopyRetryDelayMs = 32L,
    ) { backdropSnapshot = it }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncNavigation()
        sampler.onFrame()
        true
    }

    init {
        composeView.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PiliPlusNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = ::setBackdropScreenBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            token = activity.window.decorView.windowToken
            title = "MyHyperModifier PiliPlus liquid navigation"
            setFitInsetsTypes(0)
        }
        windowManager.addView(composeView, params)
        composeWindowAttached = true
        visibility.onAttached()
        visibility.setVisible(false)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        ModuleSettings.onLoaded {
            composeView.post { if (!disposing) syncNavigation(force = true) }
        }
        composeView.post { syncNavigation(force = true) }
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun refresh() {
        if (!disposing) composeView.post { syncNavigation(force = true) }
    }

    fun setForeground(value: Boolean) {
        if (disposing || foreground == value) return
        foreground = value
        if (value) refresh() else applyHiddenState()
    }

    fun dispose() {
        if (disposing) return
        disposing = true
        visibility.dispose()
        sampler.dispose()
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        if (composeWindowAttached) {
            runCatching { windowManager.removeViewImmediate(composeView) }
            composeWindowAttached = false
        }
        windowImmersion.dispose()
        if (semanticsRequested && ownsSemantics) {
            PiliPlusSemanticsBridge.disableSemantics(flutterView)
        }
        owner.dispose()
    }

    private fun syncNavigation(force: Boolean = false) {
        if (disposing || !composeWindowAttached || !foreground ||
            activity.isFinishing || activity.isDestroyed
        ) return
        if (!refreshPolicy.shouldRefresh(
                SystemClock.uptimeMillis(),
                force || state.tabs.isEmpty(),
            )
        ) return
        if (!PiliPlusFloatingNavigation.enabled()) {
            applyHiddenState()
            return
        }
        if (!semanticsRequested) {
            semanticsRequested = PiliPlusSemanticsBridge.requestSemantics(
                flutterView,
                accessibilityBridge,
            )
            ownsSemantics = semanticsRequested &&
                !PiliPlusSemanticsBridge.accessibilityEnabled(accessibilityBridge)
        }

        val screenHeight = flutterView.height
            .coerceAtLeast(activity.window.decorView.height)
        val snapshot = PiliPlusSemanticsBridge.readCandidates(
            accessibilityBridge,
            screenHeight,
        )
        val bar = PiliPlusNavigationPolicy.resolve(
            candidates = snapshot.candidates,
            screenHeightPx = screenHeight,
            density = density,
            previousSelectedLabel = state.selectedLabel,
        )
        val imeVisible = activity.window.decorView.rootWindowInsets
            ?.isVisible(WindowInsets.Type.ime()) == true
        val visible = bar != null && foreground && activity.hasWindowFocus() && !imeVisible
        if (!visible) {
            if (!loggedEmptyTree && snapshot.semanticNodeCount > 0 &&
                snapshot.candidates.isEmpty()
            ) {
                loggedEmptyTree = true
                Log.i(
                    TAG,
                    "PiliPlus semantics ready: nodes=${snapshot.semanticNodeCount}, " +
                        "labeled=${snapshot.labeledNodeCount}, no home navigation row",
                )
            }
            applyHiddenState()
            return
        }

        checkNotNull(bar)
        val selectionChanged = bar.selectedLabel != state.selectedLabel
        val becameVisible = !state.visible
        val next = PiliPlusNavigationState(
            tabs = bar.tabs,
            selectedLabel = bar.selectedLabel,
            visible = true,
            navigationLiftDp =
                ModuleSettings.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f),
            navigationInsetPx = stableNavigationInset.bottomPx(composeView, overlayParent),
            darkTheme = HostThemeResolver.resolve(
                activity = activity,
                nativeBar = flutterView,
                tabs = emptyList(),
                selectedIndex = -1,
            ),
        )
        if (next != state) state = next
        windowImmersion.apply(composeView)
        visibility.setVisible(true)
        sampler.setActive(true)
        if (selectionChanged || becameVisible) {
            sampler.invalidateSamplingContext()
            sampler.requestCaptureBurst()
        }
        if (becameVisible) {
            Log.i(
                TAG,
                "PiliPlus liquid navigation visible: " +
                    bar.tabs.joinToString { it.label } +
                    "; selected=${bar.selectedLabel}; lift=${next.navigationLiftDp}; " +
                    "semanticNodes=${snapshot.semanticNodeCount}",
            )
        }
    }

    private fun applyHiddenState() {
        if (state.visible) state = state.copy(visible = false)
        visibility.setVisible(false)
        sampler.setActive(false)
        windowImmersion.dispose()
    }

    private fun selectDestination(label: String) {
        val tab = state.tabs.firstOrNull { it.label == label } ?: return
        state = state.copy(selectedLabel = label)
        val clicked = PiliPlusSemanticsBridge.click(
            accessibilityBridge,
            flutterView,
            tab,
        )
        if (!clicked && !loggedUnavailableClick) {
            loggedUnavailableClick = true
            Log.w(TAG, "PiliPlus semantics and coordinate taps were unavailable")
        }
        sampler.invalidateSamplingContext()
        sampler.requestCaptureBurst()
        composeView.postDelayed({ syncNavigation(force = true) }, 48L)
    }

    private fun setBackdropScreenBounds(screenBounds: ViewBackdropBounds) {
        val decorLocation = IntArray(2)
        overlayParent.getLocationOnScreen(decorLocation)
        sampler.setNavigationBounds(
            screenBounds.copy(
                left = screenBounds.left - decorLocation[0],
                top = screenBounds.top - decorLocation[1],
            ),
        )
    }
}

@Composable
private fun PiliPlusNavigationContent(
    state: PiliPlusNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (String) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    val dark = state.darkTheme
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    val backdrop = rememberLayerBackdrop()
    val navigationPadding = if (state.navigationInsetPx > 0) {
        Modifier.padding(bottom = with(LocalDensity.current) {
            state.navigationInsetPx.toDp()
        })
    } else {
        Modifier.navigationBarsPadding()
    }
    val items = state.tabs.map { tab ->
        val icon = piliPlusIcon(tab.label)
        MiuixFloatingTabItem(
            key = tab.label,
            label = tab.label,
            selectedIcon = rememberVectorPainter(icon),
            unselectedIcon = rememberVectorPainter(icon),
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
                        .then(navigationPadding)
                        .padding(
                            start = 16.dp,
                            top = INJECTED_NAVIGATION_SHADOW_TOP_PADDING,
                            end = 16.dp,
                            bottom = 4.dp + state.navigationLiftDp.dp,
                        )
                        .height(MiuixFloatingTabBarDefaults.Height),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MiuixFloatingTabBarDefaults.ContainerShape)
                            .background(materialColors.surface.copy(alpha = 0.84f)),
                    )
                    ViewBackdropLayer(backdropSnapshot, backdrop)
                    MiuixFloatingTabBar(
                        items = items,
                        selectedKey = state.selectedLabel,
                        onItemSelected = { onDestinationSelected(it.key) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionOnScreen()
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
                        fillAvailableWidth = true,
                    )
                }
            }
        }
    }
}

private fun piliPlusIcon(label: String): ImageVector = when (label) {
    "首页" -> MiuixIcons.Home
    "动态" -> MiuixIcons.Messages
    "我的" -> MiuixIcons.ContactsCircle
    else -> MiuixIcons.Home
}
