package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.view.WindowCompat
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarDefaults
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Owns the Compose overlay while Xiaomi Health continues to own all destination fragments. */
internal object XiaomiHealthFloatingNavigation {
    private val hosts = WeakHashMap<Activity, XiaomiHealthNavigationHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        val startedAt = SystemClock.uptimeMillis()
        lateinit var retry: Runnable
        retry = Runnable {
            if (activity.isFinishing || activity.isDestroyed) {
                pendingAttachments.remove(activity)
                return@Runnable
            }
            ModuleSettings.ensureLoaded()
            val timedOut = SystemClock.uptimeMillis() - startedAt >= SETTINGS_WAIT_TIMEOUT_MS
            if (!ModuleSettings.isLoaded() && !timedOut) {
                activity.window.decorView.postDelayed(retry, SETTINGS_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            if (!ModuleSettings.xiaomiHealthFloatingNavigationEnabled) return@Runnable
            val host = XiaomiHealthNavigationHost.create(activity)
            if (host == null) {
                Log.w(TAG, "Xiaomi Health 3.59.1 navigation views were not found")
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
        hosts.remove(activity)?.dispose()
    }

    @JvmStatic
    fun onTouchEvent(activity: Activity, event: MotionEvent) {
        hosts[activity]?.onTouchEvent(event)
    }

    private const val SETTINGS_RETRY_MS = 100L
    private const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
    private const val TAG = "MyHyperModifier"
}

private data class XiaomiHealthNavigationState(
    val selectedIndex: Int = 0,
    val badges: List<Boolean> = List(TAB_COUNT) { false },
    val icons: List<NativeTabIconPair?> = List(TAB_COUNT) { null },
)

private class XiaomiHealthNavigationHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val originalBottomContainer: View,
    private val originalDivider: View?,
    private val nativeTabLayout: ViewGroup,
    contentView: View,
) {
    private var state by mutableStateOf(XiaomiHealthNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val previousLifecycleOwner = overlayParent.findViewTreeLifecycleOwner()
    private val previousViewModelStoreOwner = overlayParent.findViewTreeViewModelStoreOwner()
    private val previousSavedStateRegistryOwner = overlayParent.findViewTreeSavedStateRegistryOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val sampler = ViewBackdropSampler(contentView) { backdropSnapshot = it }
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val originalBottomVisibility = originalBottomContainer.visibility
    private val originalDividerVisibility = originalDivider?.visibility
    private val composeView = ComposeView(activity)
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        windowImmersion.ensureApplied()
        syncNativeState()
        sampler.onFrame()
        true
    }

    init {
        windowImmersion.apply()
        syncNativeState()
        // A ComposeView attached directly to DecorView creates a window-level Recomposer. It
        // resolves owners from that window root, not only from the ComposeView itself.
        overlayParent.setViewTreeLifecycleOwner(owner)
        overlayParent.setViewTreeViewModelStoreOwner(owner)
        overlayParent.setViewTreeSavedStateRegistryOwner(owner)
        composeView.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                XiaomiHealthNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = sampler::setNavigationBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        originalBottomContainer.visibility = View.GONE
        originalDivider?.visibility = View.GONE
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
        originalBottomContainer.visibility = originalBottomVisibility
        originalDividerVisibility?.let { originalDivider?.visibility = it }
        windowImmersion.dispose()
        overlayParent.setViewTreeLifecycleOwner(previousLifecycleOwner)
        overlayParent.setViewTreeViewModelStoreOwner(previousViewModelStoreOwner)
        overlayParent.setViewTreeSavedStateRegistryOwner(previousSavedStateRegistryOwner)
        owner.dispose()
    }

    private fun syncNativeState() {
        val next = readNativeState()
        if (next != state) state = next
    }

    private fun readNativeState(): XiaomiHealthNavigationState {
        val tabs = nativeTabViews()
        val selected = tabs.indexOfFirst(View::isSelected).takeIf { it >= 0 }
            ?: stateOrDefaultSelectedIndex()
        val selectedIndex = selected.coerceIn(0, TAB_COUNT - 1)
        val redPointId = activity.resources.getIdentifier("red_point", "id", activity.packageName)
        val iconId = activity.resources.getIdentifier("icon", "id", activity.packageName)
        val badges = List(TAB_COUNT) { index ->
            if (redPointId == 0) false
            else tabs.getOrNull(index)?.findViewById<View>(redPointId)?.visibility == View.VISIBLE
        }
        val icons = List(TAB_COUNT) { index ->
            val tab = tabs.getOrNull(index) ?: return@List null
            val iconRoot = nativeCustomTabView(index) ?: tab
            val iconView = findNativeTabIconView(
                root = iconRoot,
                preferredId = iconId,
                excludedIds = setOf(redPointId).filterTo(mutableSetOf()) { it != 0 },
            )
            iconSnapshotter.snapshot(tab, iconView, index == selectedIndex)
        }
        return XiaomiHealthNavigationState(selectedIndex, badges, icons)
    }

    private fun stateOrDefaultSelectedIndex(): Int =
        runCatching { state.selectedIndex }.getOrDefault(0)

    private fun selectDestination(index: Int) {
        val tab = nativeTabViews().getOrNull(index) ?: return
        state = state.copy(selectedIndex = index)
        tab.performClick()
        tab.post(::syncNativeState)
        sampler.requestCapture()
    }

    private fun nativeTabViews(): List<View> {
        val strip = nativeTabLayout.getChildAt(0) as? ViewGroup ?: return emptyList()
        return List(strip.childCount) { strip.getChildAt(it) }
    }

    /** Material TabLayout keeps Xiaomi's main_view_tab hierarchy on Tab.customView. */
    private fun nativeCustomTabView(index: Int): View? = runCatching {
        val getTabAt = nativeTabLayout.javaClass.getMethod(
            "getTabAt",
            Int::class.javaPrimitiveType,
        )
        val tab = getTabAt.invoke(nativeTabLayout, index) ?: return@runCatching null
        tab.javaClass.getMethod("getCustomView").invoke(tab) as? View
    }.getOrNull()

    companion object {
        fun create(activity: Activity): XiaomiHealthNavigationHost? = runCatching {
            val resources = activity.resources
            fun id(name: String): Int = resources.getIdentifier(name, "id", activity.packageName)

            val bottom = activity.findViewById<View>(id("main_fl_bottom_container")) ?: return null
            val tabLayout = activity.findViewById<ViewGroup>(id("main_tl_bottom")) ?: return null
            val content = activity.findViewById<View>(id("main_fl_content")) ?: return null
            val overlayParent = activity.window.decorView as? ViewGroup ?: return null
            val root = bottom.parent as? ViewGroup ?: return null
            val bottomIndex = root.indexOfChild(bottom)
            val divider = if (bottomIndex > 0) root.getChildAt(bottomIndex - 1) else null
            XiaomiHealthNavigationHost(
                activity = activity,
                overlayParent = overlayParent,
                originalBottomContainer = bottom,
                originalDivider = divider,
                nativeTabLayout = tabLayout,
                contentView = content,
            )
        }.onFailure {
            Log.e("MyHyperModifier", "Could not attach Xiaomi Health navigation overlay", it)
        }.getOrNull()
    }
}

/**
 * Requests the legacy bottom-only layout flag used by Xiaomi's own View stack. Using the all-bars
 * WindowCompat edge-to-edge switch here would make Miuix apply its status-bar inset twice.
 */
@Suppress("DEPRECATION")
internal class InjectedBottomNavigationImmersion(
    private val activity: Activity,
) {
    private val window = activity.window
    private val decorView = window.decorView
    private val originalNavigationBarColor = window.navigationBarColor
    private val originalNavigationBarDividerColor = window.navigationBarDividerColor
    private val originalNavigationBarContrastEnforced = window.isNavigationBarContrastEnforced
    private val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    private val originalLightNavigationBars = insetsController.isAppearanceLightNavigationBars
    private val originalManagedLayoutFlags = decorView.systemUiVisibility and MANAGED_LAYOUT_FLAGS
    private var applied = false

    fun apply() {
        if (applied) return
        applied = true
        ensureApplied()
    }

    fun ensureApplied() {
        if (!applied) return
        val layoutFlags = decorView.systemUiVisibility
        if (layoutFlags and MANAGED_LAYOUT_FLAGS != MANAGED_LAYOUT_FLAGS) {
            decorView.systemUiVisibility = layoutFlags or MANAGED_LAYOUT_FLAGS
        }
        if (window.navigationBarColor != AndroidColor.TRANSPARENT) {
            window.navigationBarColor = AndroidColor.TRANSPARENT
        }
        if (window.navigationBarDividerColor != AndroidColor.TRANSPARENT) {
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }
        if (window.isNavigationBarContrastEnforced) {
            window.isNavigationBarContrastEnforced = false
        }
        val dark = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        insetsController.isAppearanceLightNavigationBars = !dark
    }

    fun dispose() {
        if (!applied) return
        applied = false
        decorView.systemUiVisibility =
            (decorView.systemUiVisibility and MANAGED_LAYOUT_FLAGS.inv()) or originalManagedLayoutFlags
        window.navigationBarColor = originalNavigationBarColor
        window.navigationBarDividerColor = originalNavigationBarDividerColor
        window.isNavigationBarContrastEnforced = originalNavigationBarContrastEnforced
        insetsController.isAppearanceLightNavigationBars = originalLightNavigationBars
    }

    private companion object {
        const val MANAGED_LAYOUT_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}

@Composable
private fun XiaomiHealthNavigationContent(
    state: XiaomiHealthNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int) -> Unit,
) {
    val dark = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    val backdrop = rememberLayerBackdrop()
    val icons = listOf(
        MiuixIcons.Home,
        MiuixIcons.Stopwatch,
        MiuixIcons.SearchDevice,
        MiuixIcons.ContactsCircle,
    )
    val labels = listOf("健康", "运动", "设备", "我的")
    val items = labels.mapIndexed { index, label ->
        val nativeIcons = state.icons.getOrNull(index)
            ?.takeUnless { ModuleSettings.xiaomiHealthMiuixIconsEnabled }
        val monochrome = nativeIcons != null &&
            ModuleSettings.xiaomiHealthMonochromeIconsEnabled
        val selectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.selectedMonochrome else it.selected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(icons[index])
        val unselectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.unselectedMonochrome else it.unselected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(icons[index])
        MiuixFloatingTabItem(
            key = index.toString(),
            label = label,
            selectedIcon = selectedPainter,
            unselectedIcon = unselectedPainter,
            iconScale = if (nativeIcons != null) XIAOMI_HEALTH_NATIVE_ICON_SCALE else 1f,
            preserveOriginalIconColors = nativeIcons != null && !monochrome,
            badge = if (state.badges.getOrElse(index) { false }) "" else null,
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
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
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

@Composable
internal fun ViewBackdropLayer(snapshot: ViewBackdropSnapshot?, backdrop: LayerBackdrop) {
    if (snapshot == null) return
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .graphicsLayer(alpha = BACKDROP_SOURCE_ALPHA)
            .requiredSize(
                width = (snapshot.sourceWidthPx / density.density).dp,
                height = (snapshot.sourceHeightPx / density.density).dp,
            )
            .layerBackdrop(backdrop),
    ) {
        Image(
            bitmap = snapshot.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal data class ViewBackdropBounds(val left: Int, val top: Int, val width: Int, val height: Int)

internal data class ViewBackdropSnapshot(
    val bitmap: Bitmap,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val generation: Long,
)

/** Downsamples only the fragment host, avoiding a recursive capture of the Compose overlay. */
internal class ViewBackdropSampler(
    private val source: View,
    private val onSnapshotChanged: (ViewBackdropSnapshot?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var bounds: ViewBackdropBounds? = null
    private var captureScheduled = false
    private var lastCaptureAt = Long.MIN_VALUE
    private var keepCapturingUntil = Long.MIN_VALUE
    private var generation = 0L
    private val buffers = arrayOfNulls<Bitmap>(2)
    private var displayedBitmap: Bitmap? = null
    private val captureRunnable = Runnable {
        captureScheduled = false
        capture()
    }

    fun setNavigationBounds(value: ViewBackdropBounds) {
        if (bounds == value) return
        bounds = value
        requestCapture()
    }

    fun onTouchEvent(event: MotionEvent) {
        val now = SystemClock.uptimeMillis()
        keepCapturingUntil = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> now + ACTIVE_CAPTURE_GRACE_MS
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> now + FLING_CAPTURE_MS
            else -> keepCapturingUntil
        }
        requestCapture()
    }

    fun onFrame() {
        if (SystemClock.uptimeMillis() < keepCapturingUntil) requestCapture()
    }

    fun requestCapture() {
        if (captureScheduled || bounds == null || !source.isAttachedToWindow) return
        val elapsed = if (lastCaptureAt == Long.MIN_VALUE) Long.MAX_VALUE
        else SystemClock.uptimeMillis() - lastCaptureAt
        captureScheduled = true
        handler.postDelayed(captureRunnable, (CAPTURE_INTERVAL_MS - elapsed).coerceAtLeast(0L))
    }

    fun dispose() {
        handler.removeCallbacks(captureRunnable)
        captureScheduled = false
        displayedBitmap = null
        buffers.forEach { it?.recycle() }
        onSnapshotChanged(null)
    }

    private fun capture() {
        val target = bounds ?: return
        if (!source.isAttachedToWindow || source.width <= 0 || source.height <= 0) {
            retryInitialCapture()
            return
        }
        val sourceLocation = IntArray(2)
        source.getLocationInWindow(sourceLocation)
        val bleed = ceil(source.resources.displayMetrics.density * SAMPLE_BLEED_DP).toInt()
        val sourceRect = Rect(
            target.left - sourceLocation[0] - bleed,
            target.top - sourceLocation[1] - bleed,
            target.left - sourceLocation[0] + target.width + bleed,
            target.top - sourceLocation[1] + target.height + bleed,
        )
        if (!sourceRect.intersect(0, 0, source.width, source.height) || sourceRect.isEmpty) {
            retryInitialCapture()
            return
        }

        val bitmap = obtainBuffer(
            max(1, (sourceRect.width() * SAMPLE_SCALE).roundToInt()),
            max(1, (sourceRect.height() * SAMPLE_SCALE).roundToInt()),
        )
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.scale(SAMPLE_SCALE, SAMPLE_SCALE)
        canvas.translate(-sourceRect.left.toFloat(), -sourceRect.top.toFloat())
        try {
            source.draw(canvas)
        } catch (_: Throwable) {
            retryInitialCapture()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (generation == 0L) {
            // LayerBackdrop registers its producer and consumer on adjacent Compose frames.
            // A short startup burst guarantees a second sample without waiting for user input.
            keepCapturingUntil = maxOf(keepCapturingUntil, now + INITIAL_CAPTURE_BURST_MS)
        }
        lastCaptureAt = now
        displayedBitmap = bitmap
        generation += 1
        onSnapshotChanged(
            ViewBackdropSnapshot(bitmap, sourceRect.width(), sourceRect.height(), generation),
        )
        if (now < keepCapturingUntil) requestCapture()
    }

    private fun retryInitialCapture() {
        if (generation != 0L || captureScheduled) return
        captureScheduled = true
        handler.postDelayed(captureRunnable, CAPTURE_INTERVAL_MS)
    }

    private fun obtainBuffer(width: Int, height: Int): Bitmap {
        buffers.forEach { bitmap ->
            if (bitmap !== displayedBitmap && bitmap != null && !bitmap.isRecycled &&
                bitmap.width == width && bitmap.height == height
            ) return bitmap
        }
        val index = buffers.indexOfFirst { it !== displayedBitmap }.coerceAtLeast(0)
        buffers[index]?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { buffers[index] = it }
    }

    private companion object {
        const val SAMPLE_BLEED_DP = 32f
        const val SAMPLE_SCALE = 0.25f
        const val CAPTURE_INTERVAL_MS = 32L
        const val INITIAL_CAPTURE_BURST_MS = 240L
        const val ACTIVE_CAPTURE_GRACE_MS = 120L
        const val FLING_CAPTURE_MS = 1_500L
    }
}

/** Compose cannot safely assume that the injected Activity exposes compatible AndroidX owners. */
internal class InjectedViewTreeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun dispose() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

private const val TAB_COUNT = 4
private const val BACKDROP_SOURCE_ALPHA = 0.001f
private const val XIAOMI_HEALTH_NATIVE_ICON_SCALE = 0.90f
