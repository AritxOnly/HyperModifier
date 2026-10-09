package com.aritxonly.myhypermodifier

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarDefaults
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import java.util.ArrayDeque
import java.util.WeakHashMap
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared floating tabs beneath Spotify's original mini-player capsule. */
internal object SpotifyFloatingNavigation {
    private val hosts = WeakHashMap<Activity, SpotifyNavigationHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && (!ModuleSettings.moduleHooksEnabled ||
            !ModuleSettings.spotifyFloatingNavigationEnabled)) return
        val suppressor = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = { activity.findSpotifyView("navigation_bar") },
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
            if (!ModuleSettings.isLoaded() || !ModuleSettings.moduleHooksEnabled ||
                !ModuleSettings.spotifyFloatingNavigationEnabled) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            suppressor.restore()
            val host = SpotifyNavigationHost.create(activity)
            if (host == null && SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                suppressor.start()
                activity.window.decorView.postDelayed(retry, VIEW_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
            if (host == null) {
                Log.w(TAG, "Spotify navigation_bar was not found")
            } else {
                hosts[activity] = host
            }
        }
        pendingAttachments[activity] = retry
        activity.window.decorView.post(retry)
    }

    @JvmStatic fun refresh(activity: Activity) = hosts[activity]?.refresh()
    @JvmStatic fun setForeground(activity: Activity, foreground: Boolean) {
        if (!foreground) {
            pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
            startupSuppressors.remove(activity)?.restore()
        }
        hosts[activity]?.setForeground(foreground)
    }
    @JvmStatic fun onTouchEvent(activity: Activity, event: MotionEvent) =
        hosts[activity]?.onTouchEvent(event) ?: Unit

    @JvmStatic
    fun dispose(activity: Activity) {
        pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
        startupSuppressors.remove(activity)?.restore()
        hosts.remove(activity)?.dispose()
    }

    private const val SETTINGS_RETRY_MS = 32L
    private const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
    private const val VIEW_RETRY_MS = 32L
    private const val VIEW_WAIT_TIMEOUT_MS = 5_000L
    private const val TAG = "MyHyperModifier"
}

private data class SpotifyTabState(
    val key: String,
    val label: String,
    val selectedIcon: ImageBitmap,
    val unselectedIcon: ImageBitmap,
)

private data class SpotifyNavigationState(
    val tabs: List<SpotifyTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
)

private data class SpotifyAccessibilityTab(
    val key: String,
    val label: String,
    val selected: Boolean,
)

private class SpotifyNavigationHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val nativeNavigationBar: View,
    private val navigationInsetSpace: View?,
    samplingView: View,
) {
    private var state by mutableStateOf(SpotifyNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val originalNavigationAlpha = nativeNavigationBar.alpha
    private val originalNavigationAccessibility = nativeNavigationBar.importantForAccessibility
    private val originalNavigationLayoutHeight = nativeNavigationBar.layoutParams.height
    private val originalInsetVisibility = navigationInsetSpace?.visibility
    private val bottomGradients = listOf("bottom_gradient", "bottom_gradient_black_fade",
        "bottom_gradient_window_insets").mapNotNull(activity::findSpotifyView).associateWith { it.alpha }
    private val composeView = ComposeView(activity)
    private val visibility = FloatingNavigationVisibility(composeView)
    private var samplingBounds: ViewBackdropBounds? = null
    private val sampler = ViewBackdropSampler(
        source = samplingView,
        excludedView = composeView,
        useHardwareCanvasSampling = true,
        minimumCaptureIntervalMs = 0L,
        matchDisplayRefreshRate = true,
        sampleOnSourceFrame = true,
        captureAfterDraw = true,
        onSnapshotChanged = { snapshot ->
            val region = samplingBounds
            backdropSnapshot = if (region != null && navigationBounds != null) {
                snapshot?.forTarget(region, navigationBounds!!)
            } else null
            playback.updateBackdrop(if (region != null && playback.backdropBounds != null) {
                snapshot?.forTarget(region, playback.backdropBounds!!)
            } else null)
        },
    )
    private val tabIcons = SpotifyIconLoader(activity)
    private val playback = SpotifyPlaybackCapsule(activity)
    private var foreground = true
    private var navigationBounds: ViewBackdropBounds? = null
    private var lastKnownIndex = 0
    private var cachedAccessibilityTabs: List<SpotifyAccessibilityTab> = emptyList()
    private var lastAccessibilityScanAt = Long.MIN_VALUE
    private val composeLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        if (state.visible) reserveDockHeight()
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncNativeState()
        sampler.onFrame()
        true
    }

    init {
        windowImmersion.apply()
        composeView.apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            // The module's Compose runtime must not discover a window recomposer
            // from Spotify's DecorView: its AndroidX owners use the host classloader.
            // This explicit parent stays scoped to our view and injected lifecycle.
            setParentCompositionContext(createLifecycleAwareWindowRecomposer(lifecycle = owner.lifecycle))
            setContent {
                SpotifyNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = { bounds ->
                        navigationBounds = bounds
                        updateSamplingBounds()
                        reserveDockHeight()
                    },
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        overlayParent.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        composeView.addOnLayoutChangeListener(composeLayoutListener)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        syncNativeState()
    }

    fun refresh() {
        syncNativeState()
        sampler.requestCaptureBurst()
    }

    fun onTouchEvent(event: MotionEvent) {
        sampler.onTouchEvent(event)
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) syncNativeState() else {
            sampler.setActive(false)
        }
    }

    fun dispose() {
        visibility.dispose()
        sampler.dispose()
        playback.dispose()
        composeView.removeOnLayoutChangeListener(composeLayoutListener)
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        (composeView.parent as? ViewGroup)?.removeView(composeView)
        nativeNavigationBar.alpha = originalNavigationAlpha
        nativeNavigationBar.importantForAccessibility = originalNavigationAccessibility
        restoreNavigationHeight()
        originalInsetVisibility?.let { navigationInsetSpace?.visibility = it }
        bottomGradients.forEach { (view, alpha) -> view.alpha = alpha }
        windowImmersion.dispose()
        cachedAccessibilityTabs = emptyList()
        owner.dispose()
    }

    private fun syncNativeState() {
        if (!foreground) return
        val navigationAvailable = foreground && ModuleSettings.moduleHooksEnabled &&
            ModuleSettings.spotifyFloatingNavigationEnabled &&
            nativeNavigationBar.visibility == View.VISIBLE &&
            nativeNavigationBar.height > 0 && nativeNavigationBar.isShown
        val accessibilityTabs = readAccessibilityTabs()
        val definitions = if (accessibilityTabs.size >= 3) {
            accessibilityTabs.map { it.key to it.label }
        } else {
            fallbackTabDefinitions()
        }
        val tabs = definitions.mapNotNull { (key, label) ->
            val icons = tabIcons.navigation(key) ?: return@mapNotNull null
            SpotifyTabState(key, label, icons.first, icons.second)
        }
        // Keep the native navigation usable when a version lacks required icon resources.
        val navigationVisible = navigationAvailable && tabs.size >= 3
        sampler.setActive(navigationVisible)
        playback.sync(navigationVisible, navigationBounds)
        updateSamplingBounds()
        if (navigationVisible) {
            windowImmersion.apply()
            windowImmersion.ensureApplied()
            if (nativeNavigationBar.alpha != 0f) nativeNavigationBar.alpha = 0f
            // Keep Spotify's invisible Compose semantics alive; our visual copy is not exposed to
            // accessibility, so TalkBack still invokes Spotify's own navigation actions.
            nativeNavigationBar.importantForAccessibility = originalNavigationAccessibility
            if (navigationInsetSpace?.visibility != View.GONE) navigationInsetSpace?.visibility = View.GONE
            bottomGradients.keys.forEach { it.alpha = 0f }
            reserveDockHeight()
        } else {
            nativeNavigationBar.alpha = originalNavigationAlpha
            nativeNavigationBar.importantForAccessibility = originalNavigationAccessibility
            originalInsetVisibility?.let { navigationInsetSpace?.visibility = it }
            bottomGradients.forEach { (view, alpha) -> view.alpha = alpha }
            restoreNavigationHeight()
            windowImmersion.dispose()
        }

        val selected = accessibilityTabs.indexOfFirst { it.selected }
            .takeIf { it >= 0 } ?: lastKnownIndex
        lastKnownIndex = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val next = SpotifyNavigationState(
            tabs = tabs,
            selectedIndex = lastKnownIndex,
            visible = navigationVisible,
        )
        val changed = next != state
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        state = next
        visibility.setVisible(next.visible)
        if (changed && (selectionChanged || becameVisible)) {
            sampler.requestCaptureBurst()
        }
    }

    private fun updateSamplingBounds() {
        val tab = navigationBounds ?: return
        val region = SpotifyDockBackdrop.union(tab, playback.backdropBounds)
        samplingBounds = region
        sampler.setNavigationBounds(region)
    }

    private fun selectDestination(index: Int) {
        lastKnownIndex = index
        state = state.copy(selectedIndex = index)
        val selectedBySemantics = performAccessibilityTabClick(state.tabs.getOrNull(index)?.key)
        if (!selectedBySemantics && nativeNavigationBar.width > 0 && nativeNavigationBar.height > 0) {
            val tabCount = state.tabs.size.coerceAtLeast(1)
            val x = nativeNavigationBar.width * (index + 0.5f) / tabCount
            val y = nativeNavigationBar.height / 2f
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                x,
                y,
                0,
            )
            try {
                nativeNavigationBar.dispatchTouchEvent(down)
                nativeNavigationBar.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
        }
        nativeNavigationBar.post {
            lastAccessibilityScanAt = Long.MIN_VALUE
            syncNativeState()
            sampler.requestCaptureBurst()
        }
    }

    private fun readAccessibilityTabs(force: Boolean = false): List<SpotifyAccessibilityTab> {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastAccessibilityScanAt < ACCESSIBILITY_SCAN_INTERVAL_MS) {
            return cachedAccessibilityTabs
        }
        val found = mutableListOf<SpotifyAccessibilityTab>()
        nativeNavigationBar.createAccessibilityNodeInfoSafely()?.let { rootNode ->
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(rootNode) }
            var visited = 0
            while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
                val node = queue.removeFirst()
                try {
                    val spoken = listOfNotNull(node.text, node.contentDescription)
                        .joinToString(" ").trim()
                    tabKeyFor(spoken)?.let { key ->
                        val existing = found.indexOfFirst { it.key == key }
                        if (existing < 0) {
                            found += SpotifyAccessibilityTab(
                                key,
                                labelFor(key),
                                node.isSelected,
                            )
                        } else if (node.isSelected && !found[existing].selected) {
                            found[existing] = found[existing].copy(selected = true)
                        }
                    }
                    repeat(node.childCount) { childIndex ->
                        runCatching { node.getChild(childIndex) }.getOrNull()?.let(queue::addLast)
                    }
                } finally {
                    runCatching(node::recycle)
                }
            }
            while (queue.isNotEmpty()) runCatching(queue.removeFirst()::recycle)
        }
        lastAccessibilityScanAt = now
        cachedAccessibilityTabs = found.sortedBy {
            TAB_ORDER.indexOf(it.key).takeIf { order -> order >= 0 } ?: Int.MAX_VALUE
        }
        return cachedAccessibilityTabs
    }

    private fun performAccessibilityTabClick(key: String?): Boolean {
        if (key == null) return false
        val root = nativeNavigationBar.createAccessibilityNodeInfoSafely() ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var clicked = false
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_ACCESSIBILITY_NODES) {
            val node = queue.removeFirst()
            try {
                val spoken = listOfNotNull(node.text, node.contentDescription)
                    .joinToString(" ").trim()
                if (!clicked && tabKeyFor(spoken) == key) {
                    clicked = runCatching {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }.getOrDefault(false)
                }
                repeat(node.childCount) { childIndex ->
                    runCatching { node.getChild(childIndex) }.getOrNull()?.let(queue::addLast)
                }
            } finally {
                runCatching(node::recycle)
            }
        }
        while (queue.isNotEmpty()) runCatching(queue.removeFirst()::recycle)
        return clicked
    }

    private fun fallbackTabDefinitions(): List<Pair<String, String>> = TAB_ORDER.map { key ->
        key to labelFor(key)
    }

    private fun tabKeyFor(label: String): String? {
        if (label.isBlank()) return null
        return TAB_ORDER.firstOrNull { key ->
            val expected = labelFor(key)
            label.equals(expected, ignoreCase = true) ||
                label.startsWith(expected, ignoreCase = true) ||
                ENGLISH_TAB_LABELS.getValue(key).any { english ->
                    label.contains(english, ignoreCase = true)
                }
        }
    }

    private fun labelFor(key: String): String {
        val name = TAB_LABEL_RESOURCES.getValue(key)
        val id = activity.resources.getIdentifier(name, "string", activity.packageName)
        return id.takeIf { it != 0 }?.let { activity.getString(it) }
            ?: FALLBACK_LABELS.getValue(key)
    }

    private fun reserveNavigationHeight(height: Int) {
        if (height <= 0 || nativeNavigationBar.layoutParams.height == height) return
        nativeNavigationBar.layoutParams = nativeNavigationBar.layoutParams.apply {
            this.height = height
        }
        nativeNavigationBar.requestLayout()
    }

    private fun reserveDockHeight() {
        val bounds = navigationBounds ?: return
        val rootPosition = IntArray(2)
        overlayParent.getLocationInWindow(rootPosition)
        val nativePosition = IntArray(2)
        nativeNavigationBar.getLocationInWindow(nativePosition)
        val player = activity.findSpotifyView("now_playing_bar_layout")?.takeIf { it.isShown && it.height > 0 }
        val playerPosition = IntArray(2)
        player?.getLocationInWindow(playerPosition)
        reserveNavigationHeight(SpotifyDockLayout.reserveHeight(
            rootPosition[1] + overlayParent.height, bounds.top, nativePosition[1],
            player?.let { playerPosition[1] + it.height },
            (6f * activity.resources.displayMetrics.density).roundToInt(),
        ))
    }

    private fun restoreNavigationHeight() {
        if (nativeNavigationBar.layoutParams.height == originalNavigationLayoutHeight) return
        nativeNavigationBar.layoutParams = nativeNavigationBar.layoutParams.apply {
            height = originalNavigationLayoutHeight
        }
        nativeNavigationBar.requestLayout()
    }

    companion object {
        private val TAB_ORDER = listOf("home", "search", "library", "create")
        private val TAB_LABEL_RESOURCES = mapOf(
            "home" to "navigationbar_musicappitems_home_title",
            "search" to "navigationbar_musicappitems_search_title",
            "library" to "navigationbar_musicappitems_yourlibrary_title",
            "create" to "navigationbar_musicappitems_create_title",
        )
        private val FALLBACK_LABELS = mapOf(
            "home" to "主页", "search" to "搜索", "library" to "音乐库", "create" to "创建",
        )
        private val ENGLISH_TAB_LABELS = mapOf(
            "home" to listOf("home"),
            "search" to listOf("search"),
            "library" to listOf("library"),
            "create" to listOf("create"),
        )
        private const val MAX_ACCESSIBILITY_NODES = 128
        private const val ACCESSIBILITY_SCAN_INTERVAL_MS = 250L

        fun create(activity: Activity): SpotifyNavigationHost? = runCatching {
            val navigation = activity.findSpotifyView("navigation_bar") ?: return null
            val content = activity.findSpotifyView("fragment_container") ?: return null
            val overlay = activity.window.decorView as? ViewGroup ?: return null
            SpotifyNavigationHost(
                activity = activity,
                overlayParent = overlay,
                nativeNavigationBar = navigation,
                navigationInsetSpace = activity.findSpotifyView("navigation_bar_window_insets_space"),
                samplingView = content,
            )
        }.onFailure {
            Log.e("MyHyperModifier", "Could not attach Spotify HyperGlassify overlay", it)
        }.getOrNull()
    }
}

private class SpotifyIconLoader(private val activity: Activity) {
    private val cache = mutableMapOf<String, Pair<ImageBitmap, ImageBitmap>?>()

    fun navigation(key: String): Pair<ImageBitmap, ImageBitmap>? = cache.getOrPut("nav:$key") {
        when (key) {
            "home" -> pair("encore_icon_home_active_24", "encore_icon_home_24")
            "search" -> pair("encore_icon_search_active_24", "encore_icon_search_24")
            "library" -> pair("ic_eis_your_library", "ic_eis_your_library")
            "create" -> pair("encore_icon_plus_alt_active_24", "encore_icon_plus_alt_24")
            else -> null
        }
    }

    private fun pair(selected: String, unselected: String): Pair<ImageBitmap, ImageBitmap>? {
        val selectedBitmap = render(selected) ?: return null
        return selectedBitmap to (render(unselected) ?: selectedBitmap)
    }

    private fun render(name: String): ImageBitmap? = runCatching {
        val id = activity.resources.getIdentifier(name, "drawable", activity.packageName)
        if (id == 0) return@runCatching null
        val drawable = ResourcesCompat.getDrawable(activity.resources, id, activity.theme)?.mutate()
            ?: return@runCatching null
        val fallback = (24 * activity.resources.displayMetrics.density).roundToInt()
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: fallback
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: fallback
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun SpotifyNavigationContent(
    state: SpotifyNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    // Spotify's content remains dark even when Android uses a light theme.
    val dark = true
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    val backdrop = rememberLayerBackdrop()
    val hiddenNavigationLift = hyperGlassifyHiddenNavigationLift()

    MaterialTheme(colorScheme = materialColors) {
        MiuixTheme(colors = miuixColors) {
            CompositionLocalProvider(LocalAdvancedMaterialSpec provides AdvancedMaterialSpec(enabled = true)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 20.dp,
                            top = INJECTED_NAVIGATION_SHADOW_TOP_PADDING,
                            end = 20.dp,
                            bottom = 4.dp + hiddenNavigationLift,
                        )
                        .height(MiuixFloatingTabBarDefaults.Height),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewBackdropLayer(backdropSnapshot, backdrop)
                    MiuixFloatingTabBar(
                        items = state.tabs.mapIndexed { index, tab ->
                            MiuixFloatingTabItem(
                                key = index.toString(),
                                label = tab.label,
                                selectedIcon = remember(tab.selectedIcon) { BitmapPainter(tab.selectedIcon) },
                                unselectedIcon = remember(tab.unselectedIcon) { BitmapPainter(tab.unselectedIcon) },
                                preserveOriginalIconColors = false,
                            )
                        },
                        selectedKey = state.selectedIndex.toString(),
                        onItemSelected = { onDestinationSelected(it.key.toInt()) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            onBackdropBoundsChanged(
                                ViewBackdropBounds(
                                    position.x.roundToInt(),
                                    position.y.roundToInt(),
                                    coordinates.size.width,
                                    coordinates.size.height,
                                ),
                            )
                        },
                        layout = MiuixFloatingTabLayout.Stacked,
                        fillAvailableWidth = true,
                        backdrop = backdropSnapshot?.let { backdrop },
                    )
                }
            }
        }
    }
}

internal fun Activity.findSpotifyView(name: String): View? {
    val id = resources.getIdentifier(name, "id", packageName)
    return id.takeIf { it != 0 }?.let(::findViewById)
}

private fun View.createAccessibilityNodeInfoSafely(): AccessibilityNodeInfo? =
    runCatching(::createAccessibilityNodeInfo).getOrNull()
