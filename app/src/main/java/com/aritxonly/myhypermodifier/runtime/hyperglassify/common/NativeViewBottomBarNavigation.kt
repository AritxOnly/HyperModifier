package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.TabHost
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarDefaults
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarGeometry
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.material.glass.SoftGlassFloatingActionButton
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Configuration for classic View bottom bars whose native routing must remain authoritative. */
internal data class NativeViewBottomBarTarget(
    val logName: String,
    val geometryKey: String = logName,
    val bottomEdgeMask: Boolean = true,
    val showNavigationShadowInLight: Boolean = true,
    val navigationShadowVisible: Boolean = true,
    val testedVersion: String,
    val bottomBarClassNames: Set<String>,
    val resolveBottomBar: ((View) -> View?)? = null,
    val resolveNativeChromeShells: (View) -> List<View> = { emptyList() },
    val tabClassNames: Set<String>,
    val fallbackLabels: List<String>,
    val enabled: () -> Boolean,
    val useMiuixIcons: () -> Boolean,
    val useMonochromeIcons: () -> Boolean,
    val showBadges: () -> Boolean,
    val fallbackIcon: (label: String, index: Int) -> ImageVector,
    val iconScale: () -> Float = { 1f },
    val contentHostMethodName: String? = null,
    val reservationViewMethodNames: Set<String> = emptySet(),
    val trimNativeIconTransparentPadding: Boolean = false,
    val resolveTabViews: ((View) -> List<View>)? = null,
    val resolveTabLabel: ((View, Int) -> String?)? = null,
    val resolveTabBadge: ((View) -> Boolean)? = null,
    val resolveTabIcons: ((View, Boolean) -> NativeTabIconPair?)? = null,
    val resolveContentHost: ((View) -> View?)? = null,
    val refreshContentPaddingEverySync: Boolean = true,
    val selectedIndexMethodName: String? = null,
    val resolveSelectedIndex: ((View, List<View>) -> Int?)? = null,
    val tabVisible: (String, Int) -> Boolean = { _, _ -> true },
    val isDetachedAction: (View, Int) -> Boolean = { _, _ -> false },
    val onNativeTabClick: (View) -> Unit = { it.performClick() },
    val tabIconScale: ((String, Int) -> Float)? = null,
    val tabLabelOffsetY: (String, Int) -> Dp = { _, _ -> 0.dp },
    /** Use the host bar's screen bounds for horizontal dock width instead of the 16dp design margin. */
    val matchNativeBarHorizontalBounds: Boolean = false,
    /** Optional whole-dock correction for hosts whose stacked content sits above the native icons. */
    val navigationContentOffsetY: Dp = 0.dp,
    val contentHostResourceName: String? = null,
    /** Removes a host's fixed navigation inset from the page container. */
    val removeContentHostBottomMargin: Boolean = false,
    val overlayAllowed: (Activity) -> Boolean = { true },
    val nativeBarVisible: (View) -> Boolean = { true },
    val resolveDarkTheme: ((Activity, View, List<View>, Int) -> Boolean?)? = null,
    val sampleBackdropForTabs: (List<View>, Int) -> Boolean = { _, _ -> true },
    val onNavigationBoundsChanged: (Activity, ViewBackdropBounds) -> Unit = { _, _ -> },
    val lateAttachWindowMs: Long = 8_000L,
    val onAttachDiagnostic: (String) -> Unit = {},
    // Native labels and badges rarely change without a click. Cap safety scans independently of
    // the host's animation frame rate; explicit actions still force an immediate refresh.
    val nativeRefreshIntervalMs: Long = 100L,
    val useHardwareBackdrop: Boolean = false,
    val useWindowGpuBackdrop: Boolean = false,
    val useNativeGpuBackdrop: Boolean = false,
    val useVideoSurfaceBackdrop: Boolean = false,
    val inTreeOverlay: Boolean = false,
    val composeContext: (Activity) -> Context = { it },
    val gpuContentResourceName: String? = null,
    val resolveGpuSource: ((View) -> View?)? = null,
    val manageWindowImmersion: Boolean = true,
    val minimumCaptureIntervalMs: Long = 0L,
    val allowSoftwareBackdrop: Boolean = true,
    val matchDisplayBackdropRefreshRate: Boolean = false,
    val sampleBackdropOnSourceFrame: Boolean = false,
    val continuousBackdropSampling: Boolean = false,
    val glassTintAlpha: Float? = null,
    val pixelCopyRetryDelayMs: Long = 1_000L,
    val requestImmersiveInsets: Boolean = false,
    val lightNavigationBars: Boolean? = null,
    val stableNavigationInset: Boolean = false,
    val fillNativeBarWidth: Boolean = true,
    val navigationMaximumWidth: Dp? = null,
    val navigationHeight: Dp = MiuixFloatingTabBarDefaults.Height,
    /** Lets the dock extend into the gesture area instead of reserving the system inset. */
    val ignoreBottomNavigationInset: Boolean = false,
    val resolveNavigationInset: (Activity) -> Int = { 0 },
    val onChromeReplacement: (Activity, Boolean) -> Unit = { _, _ -> },
    val retainNativeSuppressionDuringCover: Boolean = false,
    val retainReplacementDuringCover: Boolean = false,
    val detachedActionContainerColor: Color? = null,
    val displayTabLabel: (String, Int) -> String = { label, _ -> label },
)

/**
 * Suppresses compact chrome immediately around a native dock. Full-screen page containers and
 * TabHosts are intentionally excluded; only same-sized wrappers/background siblings are returned.
 */
internal fun compactNativeChromeShells(bar: View): List<View> {
    val barWidth = bar.width.takeIf { it > 0 } ?: return emptyList()
    val barHeight = bar.height.takeIf { it > 0 } ?: return emptyList()
    val maximumHeight = barHeight + (32f * bar.resources.displayMetrics.density).toInt()
    val minimumWidth = (barWidth * 0.82f).toInt()
    val barLocation = IntArray(2)
    bar.getLocationOnScreen(barLocation)
    val result = ArrayList<View>(4)

    var ancestor = bar.parent as? View
    repeat(3) {
        val group = ancestor as? ViewGroup ?: return@repeat
        if (group !is TabHost &&
            group.width >= minimumWidth &&
            group.height in 1..maximumHeight
        ) {
            result += group
            ancestor = group.parent as? View
        } else {
            return@repeat
        }
    }

    val parent = bar.parent as? ViewGroup
    if (parent != null) {
        val location = IntArray(2)
        repeat(parent.childCount) { index ->
            val sibling = parent.getChildAt(index)
            if (sibling === bar || sibling.width < minimumWidth ||
                sibling.height !in 1..maximumHeight
            ) {
                return@repeat
            }
            sibling.getLocationOnScreen(location)
            if (kotlin.math.abs(location[0] - barLocation[0]) <= 2 &&
                kotlin.math.abs(location[1] - barLocation[1]) <= 2
            ) {
                result += sibling
            }
        }
    }
    return result.distinct()
}

/**
 * Shared runtime for app-owned View bottom bars. The original bar stays laid out so its state,
 * click listeners and analytics remain authoritative. Targets may opt into removing the app's
 * fixed bottom reservation while adding equivalent end padding only to vertical scroll content.
 */
internal class NativeViewBottomBarNavigation(
    private val target: NativeViewBottomBarTarget,
) {
    // Attached View listeners/composition own the host; this index must not own its Activity.
    private val hosts = WeakHashMap<Activity, WeakReference<NativeViewBottomBarHost>>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    fun prepare(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts[activity]?.get() != null ||
            pendingAttachments.containsKey(activity) || startupSuppressors.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !target.enabled()) return
        startupSuppressors[activity] = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = {
                val root = activity.window.decorView
                val candidate = target.resolveBottomBar?.invoke(root)
                    ?: root.findDescendantByClassNames(target.bottomBarClassNames)
                candidate?.takeIf {
                    it.isVisibleWithin(root) && target.nativeBarVisible(it)
                }
            },
        ).also(EarlyBottomBarSuppressor::start)
    }

    fun attach(activity: Activity, preferredBar: View? = null) {
        if (activity.isFinishing || activity.isDestroyed ||
            pendingAttachments.containsKey(activity)
        ) return
        val existing = hosts[activity]?.get()
        if (existing?.shouldKeepBinding(activity.window.decorView, preferredBar) == true) return
        if (existing != null) {
            existing.dispose()
            hosts.remove(activity)
        }

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !target.enabled()) {
            target.onAttachDiagnostic("floating navigation disabled by settings")
            startupSuppressors.remove(activity)?.restore()
            return
        }
        prepare(activity)
        val suppressor = startupSuppressors[activity] ?: return
        val startedAt = SystemClock.uptimeMillis()
        var reportedLateAttach = false
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
            if (!target.enabled()) {
                target.onAttachDiagnostic("floating navigation disabled by settings")
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            suppressor.restore()
            val host = NativeViewBottomBarHost.create(activity, target, preferredBar) {
                attach(activity)
            }
            val elapsed = SystemClock.uptimeMillis() - startedAt
            if (host == null && elapsed < target.lateAttachWindowMs) {
                if (!reportedLateAttach && elapsed >= VIEW_WAIT_TIMEOUT_MS) {
                    reportedLateAttach = true
                    target.onAttachDiagnostic("waiting for native bottom bar after ${elapsed} ms")
                }
                if (elapsed < VIEW_WAIT_TIMEOUT_MS) suppressor.start()
                activity.window.decorView.postDelayed(
                    retry,
                    if (elapsed < VIEW_WAIT_TIMEOUT_MS) VIEW_RETRY_MS else LATE_VIEW_RETRY_MS,
                )
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
            if (host == null) {
                val root = activity.window.decorView
                val barFound = target.resolveBottomBar?.invoke(root)
                    ?: root.findDescendantByClassNames(target.bottomBarClassNames)
                target.onAttachDiagnostic(
                    if (barFound == null) "native bottom bar not found after ${elapsed} ms"
                    else "navigation host unavailable after ${elapsed} ms",
                )
                Log.w(TAG, "${target.logName} ${target.testedVersion} bottom bar was not found")
            } else {
                target.onAttachDiagnostic("floating navigation attached")
                hosts[activity] = WeakReference(host)
                Log.i(TAG, "Attached ${target.logName} ${target.testedVersion} soft-glass navigation")
            }
        }
        pendingAttachments[activity] = retry
        activity.window.decorView.post(retry)
    }

    fun dispose(activity: Activity) {
        pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
        startupSuppressors.remove(activity)?.restore()
        hosts.remove(activity)?.get()?.dispose()
    }

    fun onTouchEvent(activity: Activity, event: MotionEvent) {
        hosts[activity]?.get()?.onTouchEvent(event)
    }

    fun isReplacingBottomBar(view: View): Boolean = hosts.values.any { it.get()?.isReplacing(view) == true }

    fun setForeground(activity: Activity, foreground: Boolean) {
        hosts[activity]?.get()?.setForeground(foreground)
    }

    fun invalidateContentPadding(activity: Activity) {
        hosts[activity]?.get()?.invalidateContentPadding()
    }

    fun refresh(activity: Activity) {
        hosts[activity]?.get()?.refresh()
    }

    fun hasHost(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return hosts[activity]?.get()?.isBoundTo(activity.window.decorView) == true
    }

    fun hasNavigationTarget(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return runCatching {
            val root = activity.window.decorView
            val candidate = target.resolveBottomBar?.invoke(root)
                ?: root.findDescendantByClassNames(target.bottomBarClassNames)
            candidate?.takeIf {
                it.isVisibleWithin(root) && target.nativeBarVisible(it)
            }
        }.getOrNull() != null
    }

    private companion object {
        const val SETTINGS_RETRY_MS = 32L
        const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
        const val VIEW_RETRY_MS = 16L
        const val VIEW_WAIT_TIMEOUT_MS = 8_000L
        const val LATE_VIEW_RETRY_MS = 750L
        const val TAG = "MyHyperModifier"
    }
}

private data class NativeViewTabState(
    val nativeIndex: Int,
    val label: String,
    val badge: Boolean,
    val icons: NativeTabIconPair?,
)

private data class NativeViewNavigationState(
    val tabs: List<NativeViewTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
    val detachedAction: NativeViewTabState? = null,
    val nativeBarBounds: ViewBackdropBounds? = null,
    val navigationLiftDp: Float = 0f,
    val navigationInsetPx: Int = 0,
    val darkTheme: Boolean? = null,
    val sampleBackdrop: Boolean = true,
)

/**
 * The dock reserves transparent top space for its shadow. Touches in that space must continue
 * to the host view below, otherwise seek bars placed immediately above the glass become dead.
 */
private class ShadowPassThroughFrameLayout(context: Context) : FrameLayout(context) {
    var topPassThroughPx: Int = 0
    var forwardShadowTouches: Boolean = false
    var forwardShadowTouch: ((MotionEvent) -> Unit)? = null
    private var trackingGesture = false
    private var forwardingGesture = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                forwardingGesture = forwardShadowTouches && event.y < topPassThroughPx
                if (forwardingGesture) {
                    forwardShadowTouch?.invoke(event)
                    return true
                }
                trackingGesture = event.y >= topPassThroughPx
                if (!trackingGesture) return false
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (forwardingGesture) {
                    forwardShadowTouch?.invoke(event)
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        forwardingGesture = false
                    }
                    return true
                }
                if (!trackingGesture) return false
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            trackingGesture = false
        }
        return handled
    }
}

private class NativeViewBottomBarHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val nativeBottomBar: View,
    private val target: NativeViewBottomBarTarget,
    private val contentHost: View?,
    reservationViews: List<View>,
    private val onInvalid: () -> Unit,
) {
    private var state by mutableStateOf(NativeViewNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private var videoSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity, target.requestImmersiveInsets, target.lightNavigationBars)
    private val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayView = ShadowPassThroughFrameLayout(activity)
    private val composeView = ComposeView(target.composeContext(activity))
    private val visibility = FloatingNavigationVisibility(overlayView, windowManager.takeUnless { target.inTreeOverlay })
    private val overlayAnchor = NativeTabOverlayAnchor(overlayParent, overlayView, windowManager.takeUnless { target.inTreeOverlay })
    private var composeWindowAttached = false
    private val gpuSource = target.resolveGpuSource?.invoke(nativeBottomBar)
        ?: target.gpuContentResourceName?.let { name ->
        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
        overlayParent.findViewById<View>(id)
    } ?: contentHost.takeIf { target.inTreeOverlay }
        ?: overlayParent.takeUnless { target.inTreeOverlay }
    private val gpuBackdrop = if (target.useNativeGpuBackdrop && gpuSource != null)
        NativeGpuBackdrop.create(gpuSource, composeView, target.useVideoSurfaceBackdrop) else null
    private val videoSampler = if (target.useVideoSurfaceBackdrop && gpuSource != null)
        DouyinSurfaceBackdrop(gpuSource) { videoSnapshot = it } else null
    private val windowGpuBackdrop = if (target.useWindowGpuBackdrop) {
        WindowGpuBackdropSampler(
            source = overlayParent,
            excludedView = overlayView,
            window = activity.window,
            minimumCaptureIntervalMs = target.minimumCaptureIntervalMs,
            continuousSampling = target.continuousBackdropSampling,
        ) { backdropSnapshot = it }
    } else null
    private val sampler = if (target.useNativeGpuBackdrop || target.useWindowGpuBackdrop) null else ViewBackdropSampler(
        source = overlayParent,
        excludedView = overlayView,
        pixelCopyWindow = activity.window.takeIf { target.useHardwareBackdrop },
        usePixelCopySampling = { target.useHardwareBackdrop },
        minimumCaptureIntervalMs = target.minimumCaptureIntervalMs,
        allowSoftwareFallback = target.allowSoftwareBackdrop,
        matchDisplayRefreshRate = target.matchDisplayBackdropRefreshRate,
        sampleOnSourceFrame = target.sampleBackdropOnSourceFrame,
        continuousSampling = target.continuousBackdropSampling,
        pixelCopyRetryDelayMs = target.pixelCopyRetryDelayMs,
    ) { backdropSnapshot = it }
    private val refreshPolicy = NavigationRefreshPolicy(target.nativeRefreshIntervalMs)
    private var foreground = true
    private var disposed = false
    private val stableNavigationInset = StableNavigationBarInset()
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val trimmedIconCache = WeakHashMap<View, TrimmedNativeIconEntry>()
    private val contentPaddingPolicy = NavigationContentPaddingPolicy()
    private val contentLayoutListener = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        if (r - l != oldR - oldL || b - t != oldB - oldT) contentPaddingPolicy.invalidate()
    }
    private val contentBottomPadding = contentHost?.let(::InjectedScrollableContentBottomPadding)
    private val originalContentHostBottomMargin: Int? = contentHost?.layoutParams
        ?.let { it as? ViewGroup.MarginLayoutParams }?.bottomMargin
    private var contentHostBottomMarginRemoved = false
    private val nativeBottomBarAttachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            if (!disposed && (overlayParent !== activity.window.decorView ||
                    view.rootView !== overlayParent)
            ) {
                activity.window.decorView.post(onInvalid)
            }
        }

        override fun onViewDetachedFromWindow(view: View) {
            if (!disposed) activity.window.decorView.post(onInvalid)
        }
    }
    private val originalContentBottomMargins = IdentityHashMap<View, Int>().apply {
        reservationViews.forEach { view ->
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { put(view, it.bottomMargin) }
        }
    }
    private val originalBottomAlpha = nativeBottomBar.alpha
    private val originalBottomAccessibility = nativeBottomBar.importantForAccessibility
    private val nativeChromeShells = runCatching {
        target.resolveNativeChromeShells(nativeBottomBar)
    }.getOrDefault(emptyList()).filter { it !== nativeBottomBar }.distinct()
    private val nativeChromeShellsToMakeInvisible = nativeChromeShells.filterNot {
        it.containsDescendant(nativeBottomBar)
    }
    private val originalChromeShellAlphas = IdentityHashMap<View, Float>()
    private val originalChromeShellAccessibility = IdentityHashMap<View, Int>()
    private val originalChromeShellVisibility = IdentityHashMap<View, Int>()
    private var nativeChromeShellsSuppressed = false
    private var replacingNativeChrome = false
    private var nativeBarSuppressed = false
    private var layoutTabs = emptyList<View>()
    private var nativeTabsReady = false
    private var cachedBadgeIds: Set<Int>? = null
    private var pendingSelectionIndex: Int? = null
    private var pendingSelectionDeadlineMs = 0L
    private val composeLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val contentInset = view.injectedNavigationContentInsetPx()
        if (replacingNativeChrome && applyContentPadding(contentInset)) {
            sampler?.requestCaptureBurst()
        }
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (disposed) return@OnPreDrawListener true
        syncNativeState()
        if (gpuBackdrop == null && state.sampleBackdrop) sampler?.onFrame()
        if (gpuBackdrop == null && state.sampleBackdrop) windowGpuBackdrop?.onFrame()
        true
    }

    init {
        overlayView.forwardShadowTouches = !target.inTreeOverlay
        overlayView.forwardShadowTouch = ::forwardShadowTouch
        overlayView.setViewTreeLifecycleOwner(owner)
        overlayView.setViewTreeViewModelStoreOwner(owner)
        overlayView.setViewTreeSavedStateRegistryOwner(owner)
        overlayView.topPassThroughPx = (
            INJECTED_NAVIGATION_SHADOW_TOP_PADDING.value * activity.resources.displayMetrics.density
        ).roundToInt()
        overlayView.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        composeView.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // The foreign Decor has no module LifecycleOwner. Its window recomposer lookup
            // starts at Decor rather than this ComposeView, so supply our owned lifecycle.
            if (target.inTreeOverlay) {
                setParentCompositionContext(createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle))
            }
            setContent {
                NativeViewBottomBarContent(
                    state = state,
                    target = target,
                    backdropSnapshot = videoSnapshot ?: if (state.sampleBackdrop) backdropSnapshot else null,
                    gpuBackdrop = gpuBackdrop.takeUnless { videoSnapshot != null },
                    onBackdropBoundsChanged = ::setBackdropScreenBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        if (target.inTreeOverlay) {
            overlayParent.addView(overlayView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ))
        } else {
            val params = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
                token = activity.window.decorView.windowToken
                title = "MyHyperModifier ${target.logName} navigation"
                setFitInsetsTypes(0)
            }
            windowManager.addView(overlayView, params)
            composeWindowAttached = true
            visibility.onAttached()
        }
        syncNativeState()
        if (!target.refreshContentPaddingEverySync) contentHost?.addOnLayoutChangeListener(contentLayoutListener)
        nativeBottomBar.addOnAttachStateChangeListener(nativeBottomBarAttachment)
        overlayView.addOnLayoutChangeListener(composeLayoutListener)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        sampler?.let { legacySampler -> composeView.post { legacySampler.requestCaptureBurst(900L) } }
        if (target.removeContentHostBottomMargin) {
            val params = contentHost?.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null && params.bottomMargin != 0) {
                params.bottomMargin = 0
                contentHost.layoutParams = params
                contentHostBottomMarginRemoved = true
            }
        }
    }

    fun onTouchEvent(event: MotionEvent) {
        if (gpuBackdrop == null && state.visible && state.sampleBackdrop) sampler?.onTouchEvent(event)
        if (gpuBackdrop == null && state.visible && state.sampleBackdrop) {
            windowGpuBackdrop?.onTouchEvent(event)
        }
    }

    private fun forwardShadowTouch(event: MotionEvent) {
        val decor = activity.window.decorView
        if (!decor.isAttachedToWindow) return
        val overlayLocation = IntArray(2)
        val decorLocation = IntArray(2)
        overlayView.getLocationOnScreen(overlayLocation)
        decor.getLocationOnScreen(decorLocation)
        val forwarded = MotionEvent.obtain(event)
        forwarded.offsetLocation(
            (overlayLocation[0] - decorLocation[0]).toFloat(),
            (overlayLocation[1] - decorLocation[1]).toFloat(),
        )
        runCatching { decor.dispatchTouchEvent(forwarded) }
        forwarded.recycle()
    }

    fun setForeground(value: Boolean) {
        if (disposed) return
        foreground = value
        refresh()
    }

    fun invalidateContentPadding() = contentPaddingPolicy.invalidate()

    fun isBoundTo(root: View): Boolean =
        !disposed && overlayParent === root && nativeBottomBar.isAttachedToWindow &&
            nativeBottomBar.rootView === overlayParent

    fun shouldKeepBinding(root: View, preferredBar: View?): Boolean {
        if (!isBoundTo(root) || !nativeBottomBar.isVisibleWithin(root)) return false
        if (preferredBar == null || preferredBar === nativeBottomBar) return true
        if (!preferredBar.isVisibleWithin(root)) return true
        val current = IntArray(2)
        val preferred = IntArray(2)
        nativeBottomBar.getLocationOnScreen(current)
        preferredBar.getLocationOnScreen(preferred)
        return current[1] + nativeBottomBar.height >= preferred[1] + preferredBar.height
    }

    fun refresh() {
        if (!disposed) syncNativeState(force = true)
    }

    fun isReplacing(view: View): Boolean = view === nativeBottomBar && nativeBarSuppressed

    fun dispose() {
        if (disposed) return
        disposed = true
        pendingSelectionIndex = null
        target.onChromeReplacement(activity, false)
        overlayAnchor.dispose()
        visibility.dispose()
        sampler?.dispose()
        windowGpuBackdrop?.dispose()
        composeView.removeCallbacks(null)
        videoSampler?.dispose()
        gpuBackdrop?.dispose()
        if (contentHostBottomMarginRemoved) {
            val params = contentHost?.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = originalContentHostBottomMargin ?: 0
                contentHost.layoutParams = params
            }
            contentHostBottomMarginRemoved = false
        }
        if (!target.refreshContentPaddingEverySync) contentHost?.removeOnLayoutChangeListener(contentLayoutListener)
        nativeBottomBar.removeOnAttachStateChangeListener(nativeBottomBarAttachment)
        overlayView.removeOnLayoutChangeListener(composeLayoutListener)
        contentBottomPadding?.dispose()
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        if (target.inTreeOverlay) {
            (overlayView.parent as? ViewGroup)?.removeView(overlayView)
        } else if (composeWindowAttached) {
            runCatching { windowManager.removeViewImmediate(overlayView) }
            composeWindowAttached = false
        }
        restoreNativeChromeShells()
        nativeBottomBar.alpha = originalBottomAlpha
        nativeBottomBar.importantForAccessibility = originalBottomAccessibility
        restoreContentReservation()
        windowImmersion.dispose()
        owner.dispose()
    }

    private fun syncNativeState(force: Boolean = false) {
        if (disposed) return
        // Cheap visibility checks run on every frame even when native-tab synchronization is capped.
        // This prevents a low-frequency state refresh from covering an ad's skip target for a frame.
        if (!foreground || !target.enabled() ||
            !nativeBottomBar.isVisibleWithin(overlayParent) ||
            !target.nativeBarVisible(nativeBottomBar) ||
            !target.overlayAllowed(activity)
        ) {
            layoutTabs = emptyList()
            if (state.visible) state = state.copy(visible = false)
            sampler?.setActive(false)
            gpuBackdrop?.setActive(false)
            windowGpuBackdrop?.setActive(false)
            videoSampler?.setActive(false)
            visibility.setVisible(false)
            updateNativeChromeReplacement(false)
            return
        }
        if (!refreshPolicy.shouldRefresh(SystemClock.uptimeMillis(), force || !state.visible)) {
            overlayAnchor.align(nativeBottomBar, layoutTabs)
            // App animations may restore the native alpha between state refreshes.
            updateNativeBarSuppression(true)
            return
        }
        val nativeTabs = nativeTabViews()
        layoutTabs = nativeTabs
        overlayAnchor.align(nativeBottomBar, nativeTabs)
        if (nativeTabs.size > 1) nativeTabsReady = true
        val selected = target.resolveSelectedIndex?.invoke(nativeBottomBar, nativeTabs)
            ?.takeIf { it in nativeTabs.indices }
            ?: target.selectedIndexMethodName?.let(nativeBottomBar::invokeInt)
            ?.takeIf { it in nativeTabs.indices }
            ?: nativeTabs.indexOfFirst { it.isSelected || it.invokeBoolean("getStatus") }
            .takeIf { it >= 0 }
            ?: nativeBottomBar.invokeInt("getSelectPosition")?.takeIf { it in nativeTabs.indices }
            ?: state.selectedIndex.coerceIn(0, (nativeTabs.size - 1).coerceAtLeast(0))
        val now = SystemClock.uptimeMillis()
        val pending = pendingSelectionIndex
        if (pending != null &&
            (pending !in nativeTabs.indices || pending == selected ||
                now >= pendingSelectionDeadlineMs)
        ) {
            pendingSelectionIndex = null
        }
        val effectiveSelected = pendingSelectionIndex
            ?.takeIf { it in nativeTabs.indices }
            ?: selected
        val actionIndex = nativeTabs.indices.firstOrNull {
            target.isDetachedAction(nativeTabs[it], it)
        } ?: -1
        val allTabs = nativeTabs.mapIndexed { index, tab ->
            tab.readTabState(index, index == effectiveSelected)
        }
        // A preset from an older or hand-edited configuration must never leave an empty dock.
        val displayedTabs = NativeBottomBarPolicy.visibleNavigationIndices(
            allTabs.map { it.label }, actionIndex,
        ) { label, index -> target.tabVisible(label, index) }.map { allTabs[it] }
        val nativeBarBounds = if (target.matchNativeBarHorizontalBounds) {
            val location = IntArray(2)
            nativeBottomBar.getLocationOnScreen(location)
            ViewBackdropBounds(
                left = location[0],
                top = location[1],
                width = nativeBottomBar.width,
                height = nativeBottomBar.height,
            )
        } else {
            null
        }
        val next = NativeViewNavigationState(
            tabs = displayedTabs,
            selectedIndex = effectiveSelected.coerceIn(
                0,
                (nativeTabs.size - 1).coerceAtLeast(0),
            ),
            visible = target.enabled() && nativeTabs.size > 1 && displayedTabs.isNotEmpty() &&
                nativeBottomBar.isVisibleIgnoringAlpha() && target.nativeBarVisible(nativeBottomBar),
            detachedAction = allTabs.getOrNull(actionIndex)?.takeIf {
                target.tabVisible(it.label, it.nativeIndex)
            },
            nativeBarBounds = nativeBarBounds,
            darkTheme = target.resolveDarkTheme?.invoke(
                activity,
                nativeBottomBar,
                nativeTabs,
                effectiveSelected,
            ),
            sampleBackdrop = target.sampleBackdropForTabs(nativeTabs, effectiveSelected),
            navigationLiftDp = ModuleSettings.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f),
            navigationInsetPx = if (target.ignoreBottomNavigationInset) {
                0
            } else if (target.stableNavigationInset) {
                stableNavigationInset.bottomPx(overlayView, activity.window.decorView,
                    target.resolveNavigationInset(activity))
            } else {
                0
            },
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (selectionChanged || becameVisible) contentPaddingPolicy.invalidate()
        if (next != state) state = next
        if (selectionChanged) sampler?.invalidateSamplingContext()
        updateNativeChromeReplacement(next.visible)
        visibility.setVisible(next.visible)
        sampler?.setActive(gpuBackdrop == null && next.visible && next.sampleBackdrop)
        windowGpuBackdrop?.setActive(next.visible && next.sampleBackdrop)
        gpuBackdrop?.setActive(next.visible)
        videoSampler?.setActive(
            next.visible && (!next.sampleBackdrop || target.logName == "Douyin Lite"),
        )
        if (selectionChanged || becameVisible) sampler?.requestCaptureBurst()
    }

    private fun View.readTabState(index: Int, selected: Boolean): NativeViewTabState {
        val resolvedLabel = target.resolveTabLabel?.invoke(this, index)?.takeIf { it.isNotBlank() }
            ?: descendantsAndSelf().filterIsInstance<TextView>()
                .firstOrNull { it.visibility == View.VISIBLE && !it.text.isNullOrBlank() }
                ?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: target.fallbackLabels.getOrElse(index) { "入口 ${index + 1}" }
        // MIUIX vectors do not need a native bitmap, icon reflection or whole-bar badge-id scan.
        val icon = if (target.useMiuixIcons() || target.resolveTabIcons != null) null else {
            (invokeNoArg("getIcon") as? ImageView)
                ?: findNativeTabIconView(this, 0, badgeViewIds())
        }
        val rawIcons = when {
            target.useMiuixIcons() -> null
            target.resolveTabIcons != null -> target.resolveTabIcons.invoke(this, selected)
            else -> iconSnapshotter.snapshot(this, icon, selected)
        }
        val icons = if (target.trimNativeIconTransparentPadding && rawIcons != null) {
            trimmedIconCache[this]?.takeIf { it.source == rawIcons }?.trimmed
                ?: rawIcons.trimTransparentPadding().also { trimmed ->
                    trimmedIconCache[this] = TrimmedNativeIconEntry(rawIcons, trimmed)
                }
        } else {
            rawIcons
        }
        return NativeViewTabState(
            nativeIndex = index,
            label = resolvedLabel,
            badge = target.showBadges() && (target.resolveTabBadge?.invoke(this) ?: hasVisibleBadge(icon)),
            icons = icons,
        )
    }

    private fun badgeViewIds(): Set<Int> = cachedBadgeIds ?: nativeBottomBar.descendantsAndSelf()
        .asSequence()
        .filter { it.looksLikeBadge() }
        .map(View::getId)
        .filter { it != View.NO_ID }
        .toSet()
        .also { cachedBadgeIds = it }

    private fun View.hasVisibleBadge(icon: ImageView?): Boolean {
        val reflectedBadge = invokeNoArg("getRedDotView") as? View
        if (reflectedBadge?.isActuallyVisible() == true) return true
        return descendantsAndSelf().any { candidate ->
            candidate !== this && candidate !== icon && candidate.looksLikeBadge() &&
                candidate.isActuallyVisible()
        }
    }

    private fun updateNativeChromeReplacement(enabled: Boolean) {
        updateNativeBarSuppression(enabled)
        val retain = target.retainReplacementDuringCover && target.enabled() && nativeTabsReady
        if (enabled || retain) {
            if (!replacingNativeChrome) {
                replacingNativeChrome = true
                if (target.manageWindowImmersion) windowImmersion.apply(overlayView)
            }
            if (target.manageWindowImmersion) windowImmersion.ensureApplied()
            target.onChromeReplacement(activity, true)
            ensureContentReservationRemoved()
            val contentInset = overlayView.injectedNavigationContentInsetPx()
            if (applyContentPadding(contentInset)) {
                sampler?.requestCaptureBurst()
            }
        } else if (replacingNativeChrome) {
            replacingNativeChrome = false
            contentBottomPadding?.dispose()
            contentPaddingPolicy.invalidate()
            restoreContentReservation()
            windowImmersion.dispose()
            target.onChromeReplacement(activity, false)
        }
    }

    private fun applyContentPadding(inset: Int): Boolean =
        contentBottomPadding != null &&
            contentPaddingPolicy.shouldApply(inset, target.refreshContentPaddingEverySync) &&
            contentBottomPadding.apply(inset)

    private fun updateNativeBarSuppression(overlayVisible: Boolean) {
        val suppress = NativeBottomBarPolicy.shouldSuppressNative(
            target.enabled() && nativeTabsReady,
            overlayVisible,
            target.retainNativeSuppressionDuringCover,
        )
        if (suppress) {
            nativeBarSuppressed = true
            if (nativeBottomBar.alpha != 0f) nativeBottomBar.alpha = 0f
            if (nativeBottomBar.importantForAccessibility !=
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            ) {
                nativeBottomBar.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            if (!nativeChromeShellsSuppressed) {
                nativeChromeShellsSuppressed = true
                nativeChromeShells.forEach { shell ->
                    originalChromeShellAlphas[shell] = shell.alpha
                    originalChromeShellAccessibility[shell] = shell.importantForAccessibility
                    originalChromeShellVisibility[shell] = shell.visibility
                }
            }
            // Some hosts animate or recreate their tab chrome after attachment. Reasserting these
            // few values before draw prevents a previously fixed background from reappearing
            // without adding another view tree traversal or capture layer.
            nativeChromeShells.forEach { shell ->
                if (shell.alpha != 0f) shell.alpha = 0f
                if (shell.importantForAccessibility !=
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                ) {
                    shell.importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }
            nativeChromeShellsToMakeInvisible.forEach { shell ->
                if (shell.visibility != View.INVISIBLE) shell.visibility = View.INVISIBLE
            }
        } else if (nativeBarSuppressed) {
            nativeBarSuppressed = false
            nativeBottomBar.alpha = originalBottomAlpha
            nativeBottomBar.importantForAccessibility = originalBottomAccessibility
            restoreNativeChromeShells()
        }
    }

    private fun restoreNativeChromeShells() {
        if (!nativeChromeShellsSuppressed) return
        nativeChromeShellsSuppressed = false
        nativeChromeShells.forEach { shell ->
            shell.alpha = originalChromeShellAlphas[shell] ?: shell.alpha
            shell.importantForAccessibility =
                originalChromeShellAccessibility[shell] ?: shell.importantForAccessibility
            shell.visibility = originalChromeShellVisibility[shell] ?: shell.visibility
        }
        originalChromeShellAlphas.clear()
        originalChromeShellAccessibility.clear()
        originalChromeShellVisibility.clear()
    }

    private fun ensureContentReservationRemoved() {
        originalContentBottomMargins.keys.forEach { view ->
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            if (params.bottomMargin != 0) {
                params.bottomMargin = 0
                view.layoutParams = params
            }
        }
    }

    private fun restoreContentReservation() {
        originalContentBottomMargins.forEach { (view, bottomMargin) ->
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            if (params.bottomMargin != bottomMargin) {
                params.bottomMargin = bottomMargin
                view.layoutParams = params
            }
        }
    }

    private fun selectDestination(index: Int) {
        if (disposed) return
        val tab = nativeTabViews().getOrNull(index) ?: return
        if (index != state.selectedIndex) sampler?.invalidateSamplingContext()
        if (!target.isDetachedAction(tab, index)) {
            // Native hosts may finish routing asynchronously. Pin the latest tap through a
            // short burst so a rapid sequence cannot be pulled back by an older result.
            pendingSelectionIndex = index
            pendingSelectionDeadlineMs =
                SystemClock.uptimeMillis() + PENDING_SELECTION_TIMEOUT_MS
            state = state.copy(selectedIndex = index)
        }
        target.onNativeTabClick(tab)
        tab.post { if (!disposed) syncNativeState(force = true) }
        sampler?.requestCaptureBurst()
    }

    private fun nativeTabViews(): List<View> = target.resolveTabViews?.invoke(nativeBottomBar)
        ?.take(MAX_TABS) ?: buildList {
        fun collect(view: View) {
            if (view !== nativeBottomBar && view.hasClassInHierarchy(target.tabClassNames)) {
                add(view)
                return
            }
            (view as? ViewGroup)?.let { group ->
                repeat(group.childCount) { collect(group.getChildAt(it)) }
            }
        }
        collect(nativeBottomBar)
    }.distinct().filter { it.visibility != View.GONE }.take(MAX_TABS)

    private fun setBackdropScreenBounds(screenBounds: ViewBackdropBounds) {
        target.onNavigationBoundsChanged(activity, screenBounds)
        val decorLocation = IntArray(2)
        activity.window.decorView.getLocationOnScreen(decorLocation)
        val windowBounds = screenBounds.copy(
            left = screenBounds.left - decorLocation[0],
            top = screenBounds.top - decorLocation[1],
        )
        gpuBackdrop?.setBounds(windowBounds)
        videoSampler?.setBounds(windowBounds)
        sampler?.setNavigationBounds(windowBounds)
        windowGpuBackdrop?.setNavigationBounds(windowBounds)
    }

    companion object {
        private const val MAX_TABS = 8
        private const val PENDING_SELECTION_TIMEOUT_MS = 900L

        fun create(
            activity: Activity,
            target: NativeViewBottomBarTarget,
            preferredBar: View?,
            onInvalid: () -> Unit,
        ): NativeViewBottomBarHost? =
            runCatching {
                val overlayParent = activity.window.decorView as? ViewGroup ?: return null
                val bar = (preferredBar?.takeIf {
                    it.isAttachedToWindow && it.rootView === overlayParent
                } ?: target.resolveBottomBar?.invoke(overlayParent)
                    ?: overlayParent.findDescendantByClassNames(target.bottomBarClassNames))
                    ?.takeIf {
                        it.isVisibleWithin(overlayParent) && target.nativeBarVisible(it)
                    } ?: return null
                val contentHost = target.resolveContentHost?.invoke(bar)
                    ?: (target.contentHostMethodName?.let(activity::invokeNoArg) as? View)
                    ?: target.contentHostResourceName?.let { name ->
                        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
                        (bar.parent as? View)?.findViewById<View>(id)
                    }
                val reservationViews = target.reservationViewMethodNames.mapNotNull { methodName ->
                    activity.invokeNoArg(methodName) as? View
                }.distinct()
                NativeViewBottomBarHost(
                    activity,
                    overlayParent,
                    bar,
                    target,
                    contentHost,
                    reservationViews,
                    onInvalid,
                )
            }.onFailure {
                Log.e("MyHyperModifier", "Could not attach ${target.logName} navigation", it)
            }.getOrNull()
    }
}

private fun View.containsDescendant(candidate: View): Boolean {
    var current: View? = candidate
    while (current != null) {
        if (current === this) return true
        current = current.parent as? View
    }
    return false
}

/** Ignore our own alpha suppression while still rejecting a bar translated off-screen. */
private fun View.isVisibleWithin(root: View): Boolean {
    if (!isAttachedToWindow || !root.isAttachedToWindow ||
        visibility != View.VISIBLE || width <= 0 || height <= 0
    ) {
        return false
    }
    val viewLocation = IntArray(2)
    val rootLocation = IntArray(2)
    getLocationOnScreen(viewLocation)
    root.getLocationOnScreen(rootLocation)
    val viewRight = viewLocation[0] + width
    val viewBottom = viewLocation[1] + height
    val rootRight = rootLocation[0] + root.width
    val rootBottom = rootLocation[1] + root.height
    val visibleWidth = (minOf(viewRight, rootRight) - maxOf(viewLocation[0], rootLocation[0]))
        .coerceAtLeast(0)
    val visibleHeight = (minOf(viewBottom, rootBottom) - maxOf(viewLocation[1], rootLocation[1]))
        .coerceAtLeast(0)
    return visibleWidth * 2 >= width && visibleHeight * 2 >= height
}

@Composable
private fun NativeViewBottomBarContent(
    state: NativeViewNavigationState,
    target: NativeViewBottomBarTarget,
    backdropSnapshot: ViewBackdropSnapshot?,
    gpuBackdrop: NativeGpuBackdrop?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    val dark = state.darkTheme ?: ((androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    val backdrop = rememberLayerBackdrop()
    var nativeBackdropBounds by remember { mutableStateOf<ViewBackdropBounds?>(null) }
    val opticalBackdrop = if (gpuBackdrop?.geometry != null || backdropSnapshot != null) backdrop else null
    val hiddenNavigationLift = hyperGlassifyHiddenNavigationLift()
    val navigationGeometry = MiuixFloatingTabBarGeometry.resolve(target.geometryKey)
    val resolvedNavigationHeight = navigationGeometry.heightDp?.dp ?: target.navigationHeight
    val localConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    val navigationPadding = if (target.ignoreBottomNavigationInset) {
        Modifier
    } else if (target.stableNavigationInset && state.navigationInsetPx > 0) {
        Modifier.padding(bottom = with(LocalDensity.current) { state.navigationInsetPx.toDp() })
    } else {
        Modifier.navigationBarsPadding()
    }
    val horizontalPadding = if (target.matchNativeBarHorizontalBounds &&
        state.nativeBarBounds != null && state.nativeBarBounds.width > 0
    ) {
        val windowWidthPx = hostView.width.takeIf { it > 0 }
            ?: with(LocalDensity.current) {
                localConfiguration.screenWidthDp.dp.toPx().roundToInt()
            }
        val startPx = state.nativeBarBounds.left.coerceAtLeast(0)
        val endPx = (windowWidthPx - startPx - state.nativeBarBounds.width).coerceAtLeast(0)
        Modifier.padding(
            start = with(LocalDensity.current) { startPx.toDp() },
            end = with(LocalDensity.current) { endPx.toDp() },
        )
    } else {
        Modifier.padding(start = 16.dp, end = 16.dp)
    }
    val contentOffsetModifier = if (target.navigationContentOffsetY == 0.dp) {
        Modifier
    } else {
        Modifier.offset(y = target.navigationContentOffsetY)
    }
    val glassMaterial = navigationGlassMaterial()
    val resolvedGlassMaterial = remember(glassMaterial, target.glassTintAlpha) {
        target.glassTintAlpha?.let { alpha ->
            glassMaterial.copy(navigationTintAlpha = alpha.coerceIn(0.2f, 1f))
        } ?: glassMaterial
    }
    val items = state.tabs.map { tab ->
        val nativeIcons = tab.icons?.takeUnless { target.useMiuixIcons() }
        val monochrome = nativeIcons != null && target.useMonochromeIcons()
        val selectedPainter = nativeIcons?.let {
            remember(if (monochrome) it.selectedMonochrome else it.selected) {
                BitmapPainter(if (monochrome) it.selectedMonochrome else it.selected)
            }
        } ?: rememberVectorPainter(target.fallbackIcon(tab.label, tab.nativeIndex))
        val unselectedPainter = nativeIcons?.let {
            remember(if (monochrome) it.unselectedMonochrome else it.unselected) {
                BitmapPainter(if (monochrome) it.unselectedMonochrome else it.unselected)
            }
        } ?: rememberVectorPainter(target.fallbackIcon(tab.label, tab.nativeIndex))
        MiuixFloatingTabItem(
            key = tab.nativeIndex.toString(),
            label = target.displayTabLabel(tab.label, tab.nativeIndex),
            selectedIcon = selectedPainter,
            unselectedIcon = unselectedPainter,
            preserveOriginalIconColors = nativeIcons != null && !monochrome,
            iconScale = (target.tabIconScale?.invoke(tab.label, tab.nativeIndex)
                ?: target.iconScale()).coerceIn(0.75f, 1.25f),
            labelOffsetY = target.tabLabelOffsetY(tab.label, tab.nativeIndex),
            badge = if (tab.badge) "" else null,
        )
    }

    MaterialTheme(colorScheme = materialColors) {
        MiuixTheme(colors = miuixColors) {
            CompositionLocalProvider(
                LocalAdvancedMaterialSpec provides resolvedGlassMaterial,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationEdgeMask(enabled = target.bottomEdgeMask)
                        .then(navigationPadding)
                        .then(horizontalPadding)
                        .padding(
                            top = INJECTED_NAVIGATION_SHADOW_TOP_PADDING,
                            bottom = 4.dp + hiddenNavigationLift,
                        )
                        .height(resolvedNavigationHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (gpuBackdrop != null) NativeGpuBackdropLayer(gpuBackdrop, backdrop, nativeBackdropBounds)
                    else ViewBackdropLayer(backdropSnapshot, backdrop)
                    val boundsModifier = Modifier.onGloballyPositioned { coordinates ->
                        val position = coordinates.positionOnScreen()
                        val bounds = ViewBackdropBounds(
                                left = position.x.roundToInt(),
                                top = position.y.roundToInt(),
                                width = coordinates.size.width,
                                height = coordinates.size.height,
                            )
                        onBackdropBoundsChanged(bounds)
                        nativeBackdropBounds = gpuBackdrop?.targetBounds() ?: bounds
                    }
                    val positionedBoundsModifier = contentOffsetModifier.then(boundsModifier)
                    val action = state.detachedAction
                    if (action == null) {
                        MiuixFloatingTabBar(
                            items = items,
                            selectedKey = state.selectedIndex.toString(),
                            onItemSelected = { onDestinationSelected(it.key.toInt()) },
                            modifier = positionedBoundsModifier,
                            layout = MiuixFloatingTabLayout.Stacked,
                            selectionVisible = items.any { it.key == state.selectedIndex.toString() },
                            backdrop = opticalBackdrop,
                            fillAvailableWidth = target.matchNativeBarHorizontalBounds &&
                                target.fillNativeBarWidth,
                            maximumWidth = target.navigationMaximumWidth,
                            height = resolvedNavigationHeight,
                            geometryKey = target.geometryKey,
                            shadowVisible = target.navigationShadowVisible &&
                                (dark || target.showNavigationShadowInLight),
                        )
                    } else {
                        // Deadliner 4d1b755 MiuixFloatingNavigationDock geometry; native Views own routing.
                        val maxDockWidth = detachedDockMaxWidth(items.size)
                        Row(
                            modifier = positionedBoundsModifier.widthIn(max = maxDockWidth).fillMaxWidth()
                                .height(resolvedNavigationHeight).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiuixFloatingTabBar(
                                items = items,
                                selectedKey = state.selectedIndex.toString(),
                                onItemSelected = { onDestinationSelected(it.key.toInt()) },
                                modifier = Modifier.weight(1f),
                                layout = MiuixFloatingTabLayout.Stacked,
                                selectionVisible = items.any { it.key == state.selectedIndex.toString() },
                                backdrop = opticalBackdrop,
                                geometryKey = target.geometryKey,
                                shadowVisible = target.navigationShadowVisible &&
                                    (dark || target.showNavigationShadowInLight),
                            )
                            SoftGlassFloatingActionButton(
                                onClick = { onDestinationSelected(action.nativeIndex) },
                                containerColor = target.detachedActionContainerColor
                                    ?: MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                                size = resolvedNavigationHeight,
                                backdrop = opticalBackdrop,
                            ) {
                                Icon(
                                    imageVector = target.fallbackIcon(action.label, action.nativeIndex),
                                    contentDescription = action.label,
                                    modifier = Modifier.size(MiuixFloatingTabBarDefaults.DetachedIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The detached dock keeps Deadliner's 80dp cap even when only one tab remains. */
internal fun detachedDockMaxWidth(tabCount: Int): Dp = minOf(
    442.dp,
    minOf(
        MiuixFloatingTabBarDefaults.MaximumWidth,
        MiuixFloatingTabBarDefaults.MaximumItemWidth * tabCount.coerceAtLeast(0),
    ) + MiuixFloatingTabBarDefaults.Height + 24.dp,
)

private fun View.findDescendantByClassNames(classNames: Set<String>): View? {
    if (javaClass.name in classNames) return this
    (this as? ViewGroup)?.let { group ->
        repeat(group.childCount) { index ->
            group.getChildAt(index).findDescendantByClassNames(classNames)?.let { return it }
        }
    }
    return null
}

private fun View.hasClassInHierarchy(classNames: Set<String>): Boolean =
    generateSequence(javaClass as Class<*>?) { it.superclass }.any { it.name in classNames }

private fun View.descendantsAndSelf(): List<View> = buildList {
    fun collect(view: View) {
        add(view)
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { collect(group.getChildAt(it)) }
        }
    }
    collect(this@descendantsAndSelf)
}

private fun View.looksLikeBadge(): Boolean {
    val resourceName = runCatching { resources.getResourceEntryName(id) }
        .getOrDefault("").lowercase()
    val className = javaClass.name.lowercase()
    return resourceName.contains("badge") || resourceName.contains("red_dot") ||
        resourceName.contains("reddot") || resourceName.contains("unread") ||
        resourceName.contains("notify") || className.contains("badge") ||
        className.contains("notifyicon")
}

private fun View.isActuallyVisible(): Boolean =
    visibility == View.VISIBLE && alpha > 0f && width > 0 && height > 0

private fun View.isVisibleIgnoringAlpha(): Boolean {
    if (!isAttachedToWindow || visibility != View.VISIBLE || width <= 0 || height <= 0) return false
    var ancestor = parent
    while (ancestor is View) {
        if (ancestor.visibility != View.VISIBLE) return false
        ancestor = ancestor.parent
    }
    return true
}

private fun Any.invokeNoArg(name: String): Any? {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            val method: Method = type.getDeclaredMethod(name)
            method.isAccessible = true
            return method.invoke(this)
        } catch (_: NoSuchMethodException) {
            type = type.superclass
        } catch (_: Throwable) {
            return null
        }
    }
    return null
}

private fun Any.invokeBoolean(name: String): Boolean = invokeNoArg(name) as? Boolean ?: false
private fun Any.invokeInt(name: String): Int? = invokeNoArg(name) as? Int

private data class TrimmedNativeIconEntry(
    val source: NativeTabIconPair,
    val trimmed: NativeTabIconPair,
)

private fun NativeTabIconPair.trimTransparentPadding(): NativeTabIconPair = copy(
    selected = selected.trimTransparentPadding(),
    unselected = unselected.trimTransparentPadding(),
    selectedMonochrome = selectedMonochrome.trimTransparentPadding(),
    unselectedMonochrome = unselectedMonochrome.trimTransparentPadding(),
)

private fun ImageBitmap.trimTransparentPadding(): ImageBitmap = runCatching {
    val source = asAndroidBitmap()
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    var left = source.width
    var top = source.height
    var right = -1
    var bottom = -1
    pixels.forEachIndexed { index, color ->
        if (color ushr 24 <= ICON_ALPHA_THRESHOLD) return@forEachIndexed
        val x = index % source.width
        val y = index / source.width
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        if (y > bottom) bottom = y
    }
    if (right < left || bottom < top) return@runCatching this
    val visibleSize = max(right - left + 1, bottom - top + 1)
    val padding = max(1, (visibleSize * ICON_TRIM_PADDING_FRACTION).roundToInt())
    left = (left - padding).coerceAtLeast(0)
    top = (top - padding).coerceAtLeast(0)
    right = (right + padding).coerceAtMost(source.width - 1)
    bottom = (bottom + padding).coerceAtMost(source.height - 1)
    if (left == 0 && top == 0 && right == source.width - 1 && bottom == source.height - 1) {
        return@runCatching this
    }
    Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1).asImageBitmap()
}.getOrDefault(this)

private const val ICON_ALPHA_THRESHOLD = 8
private const val ICON_TRIM_PADDING_FRACTION = 0.06f
