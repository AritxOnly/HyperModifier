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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
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
import java.lang.reflect.Method
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Configuration for classic View bottom bars whose native routing must remain authoritative. */
internal data class NativeViewBottomBarTarget(
    val logName: String,
    val testedVersion: String,
    val bottomBarClassNames: Set<String>,
    val tabClassNames: Set<String>,
    val fallbackLabels: List<String>,
    val enabled: () -> Boolean,
    val useMiuixIcons: () -> Boolean,
    val useMonochromeIcons: () -> Boolean,
    val showBadges: () -> Boolean,
    val fallbackIcon: (label: String, index: Int) -> ImageVector,
    val contentHostMethodName: String? = null,
    val reservationViewMethodNames: Set<String> = emptySet(),
    val trimNativeIconTransparentPadding: Boolean = false,
)

/**
 * Shared runtime for app-owned View bottom bars. The original bar stays laid out so its state,
 * click listeners and analytics remain authoritative. Targets may opt into removing the app's
 * fixed bottom reservation while adding equivalent end padding only to vertical scroll content.
 */
internal class NativeViewBottomBarNavigation(
    private val target: NativeViewBottomBarTarget,
) {
    private val hosts = WeakHashMap<Activity, NativeViewBottomBarHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    fun prepare(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity) || startupSuppressors.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !target.enabled()) return
        startupSuppressors[activity] = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = {
                activity.window.decorView.findDescendantByClassNames(target.bottomBarClassNames)
            },
        ).also(EarlyBottomBarSuppressor::start)
    }

    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !target.enabled()) {
            startupSuppressors.remove(activity)?.restore()
            return
        }
        prepare(activity)
        val suppressor = startupSuppressors[activity] ?: return
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
            if (!target.enabled()) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            suppressor.restore()
            val host = NativeViewBottomBarHost.create(activity, target)
            if (host == null && SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                suppressor.start()
                activity.window.decorView.postDelayed(retry, VIEW_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
            if (host == null) {
                Log.w(TAG, "${target.logName} ${target.testedVersion} bottom bar was not found")
            } else {
                hosts[activity] = host
                Log.i(TAG, "Attached ${target.logName} ${target.testedVersion} soft-glass navigation")
            }
        }
        pendingAttachments[activity] = retry
        activity.window.decorView.post(retry)
    }

    fun dispose(activity: Activity) {
        pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
        startupSuppressors.remove(activity)?.restore()
        hosts.remove(activity)?.dispose()
    }

    fun onTouchEvent(activity: Activity, event: MotionEvent) {
        hosts[activity]?.onTouchEvent(event)
    }

    private companion object {
        const val SETTINGS_RETRY_MS = 32L
        const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
        const val VIEW_RETRY_MS = 16L
        const val VIEW_WAIT_TIMEOUT_MS = 8_000L
        const val TAG = "MyHyperModifier"
    }
}

private data class NativeViewTabState(
    val label: String,
    val badge: Boolean,
    val icons: NativeTabIconPair?,
)

private data class NativeViewNavigationState(
    val tabs: List<NativeViewTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
)

private class NativeViewBottomBarHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val nativeBottomBar: View,
    private val target: NativeViewBottomBarTarget,
    contentHost: View?,
    reservationViews: List<View>,
) {
    private var state by mutableStateOf(NativeViewNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val previousLifecycleOwner = overlayParent.findViewTreeLifecycleOwner()
    private val previousViewModelStoreOwner = overlayParent.findViewTreeViewModelStoreOwner()
    private val previousSavedStateRegistryOwner = overlayParent.findViewTreeSavedStateRegistryOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val composeView = ComposeView(activity)
    private var composeWindowAttached = false
    private val sampler = ViewBackdropSampler(
        source = overlayParent,
        excludedView = composeView,
        usePixelCopySampling = { false },
    ) { backdropSnapshot = it }
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val trimmedIconCache = WeakHashMap<View, TrimmedNativeIconEntry>()
    private val contentBottomPadding = contentHost?.let(::InjectedScrollableContentBottomPadding)
    private val originalContentBottomMargins = IdentityHashMap<View, Int>().apply {
        reservationViews.forEach { view ->
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { put(view, it.bottomMargin) }
        }
    }
    private val originalBottomAlpha = nativeBottomBar.alpha
    private val originalBottomAccessibility = nativeBottomBar.importantForAccessibility
    private var replacingNativeChrome = false
    private val composeLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val contentInset = view.injectedNavigationContentInsetPx()
        if (replacingNativeChrome && contentInset > 0 &&
            contentBottomPadding?.apply(contentInset) == true
        ) {
            sampler.requestCaptureBurst()
        }
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (replacingNativeChrome) {
            ensureContentReservationRemoved()
            if (contentBottomPadding?.ensureApplied() == true) sampler.requestCaptureBurst()
        }
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
                NativeViewBottomBarContent(
                    state = state,
                    target = target,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = ::setBackdropScreenBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        syncNativeState()
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
            title = "MyHyperModifier ${target.logName} navigation"
            setFitInsetsTypes(0)
        }
        windowManager.addView(composeView, params)
        composeWindowAttached = true
        composeView.addOnLayoutChangeListener(composeLayoutListener)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        composeView.post { sampler.requestCaptureBurst(900L) }
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun dispose() {
        sampler.dispose()
        composeView.removeOnLayoutChangeListener(composeLayoutListener)
        contentBottomPadding?.dispose()
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        if (composeWindowAttached) {
            runCatching { windowManager.removeViewImmediate(composeView) }
            composeWindowAttached = false
        }
        nativeBottomBar.alpha = originalBottomAlpha
        nativeBottomBar.importantForAccessibility = originalBottomAccessibility
        restoreContentReservation()
        windowImmersion.dispose()
        overlayParent.setViewTreeLifecycleOwner(previousLifecycleOwner)
        overlayParent.setViewTreeViewModelStoreOwner(previousViewModelStoreOwner)
        overlayParent.setViewTreeSavedStateRegistryOwner(previousSavedStateRegistryOwner)
        owner.dispose()
    }

    private fun syncNativeState() {
        val nativeTabs = nativeTabViews()
        val selected = nativeTabs.indexOfFirst { it.isSelected || it.invokeBoolean("getStatus") }
            .takeIf { it >= 0 }
            ?: nativeBottomBar.invokeInt("getSelectPosition")?.takeIf { it in nativeTabs.indices }
            ?: state.selectedIndex.coerceIn(0, (nativeTabs.size - 1).coerceAtLeast(0))
        val next = NativeViewNavigationState(
            tabs = nativeTabs.mapIndexed { index, tab -> tab.readTabState(index, index == selected) },
            selectedIndex = selected.coerceIn(0, (nativeTabs.size - 1).coerceAtLeast(0)),
            visible = nativeTabs.size > 1 && nativeBottomBar.isVisibleIgnoringAlpha(),
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (next != state) state = next
        if (selectionChanged) sampler.invalidateSamplingContext()
        updateNativeChromeReplacement(next.visible)
        composeView.visibility = if (next.visible) View.VISIBLE else View.GONE
        if (selectionChanged || becameVisible) sampler.requestCaptureBurst()
    }

    private fun View.readTabState(index: Int, selected: Boolean): NativeViewTabState {
        val textViews = descendantsAndSelf().filterIsInstance<TextView>()
            .filter { it.visibility == View.VISIBLE && !it.text.isNullOrBlank() }
        val label = textViews.firstOrNull()?.text?.toString()?.trim().orEmpty()
            .ifBlank { target.fallbackLabels.getOrElse(index) { "入口 ${index + 1}" } }
        val reflectedIcon = invokeNoArg("getIcon") as? ImageView
        val icon = reflectedIcon ?: findNativeTabIconView(this, 0, badgeViewIds())
        val rawIcons = iconSnapshotter.snapshot(this, icon, selected)
        val icons = if (target.trimNativeIconTransparentPadding && rawIcons != null) {
            trimmedIconCache[this]?.takeIf { it.source == rawIcons }?.trimmed
                ?: rawIcons.trimTransparentPadding().also { trimmed ->
                    trimmedIconCache[this] = TrimmedNativeIconEntry(rawIcons, trimmed)
                }
        } else {
            rawIcons
        }
        return NativeViewTabState(
            label = label,
            badge = target.showBadges() && hasVisibleBadge(icon),
            icons = icons,
        )
    }

    private fun badgeViewIds(): Set<Int> = nativeBottomBar.descendantsAndSelf()
        .filter { it.looksLikeBadge() }
        .map(View::getId)
        .filter { it != View.NO_ID }
        .toSet()

    private fun View.hasVisibleBadge(icon: ImageView?): Boolean {
        val reflectedBadge = invokeNoArg("getRedDotView") as? View
        if (reflectedBadge?.isActuallyVisible() == true) return true
        return descendantsAndSelf().any { candidate ->
            candidate !== this && candidate !== icon && candidate.looksLikeBadge() &&
                candidate.isActuallyVisible()
        }
    }

    private fun updateNativeChromeReplacement(enabled: Boolean) {
        if (enabled) {
            if (!replacingNativeChrome) {
                replacingNativeChrome = true
                windowImmersion.apply()
            }
            windowImmersion.ensureApplied()
            if (nativeBottomBar.alpha != 0f) nativeBottomBar.alpha = 0f
            nativeBottomBar.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            ensureContentReservationRemoved()
            val contentInset = composeView.injectedNavigationContentInsetPx()
            if (contentInset > 0 && contentBottomPadding?.apply(contentInset) == true) {
                sampler.requestCaptureBurst()
            }
        } else if (replacingNativeChrome) {
            replacingNativeChrome = false
            nativeBottomBar.alpha = originalBottomAlpha
            nativeBottomBar.importantForAccessibility = originalBottomAccessibility
            contentBottomPadding?.dispose()
            restoreContentReservation()
            windowImmersion.dispose()
        }
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
        val tab = nativeTabViews().getOrNull(index) ?: return
        if (index != state.selectedIndex) sampler.invalidateSamplingContext()
        state = state.copy(selectedIndex = index)
        tab.performClick()
        tab.post(::syncNativeState)
        sampler.requestCaptureBurst()
    }

    private fun nativeTabViews(): List<View> = buildList {
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
        val decorLocation = IntArray(2)
        activity.window.decorView.getLocationOnScreen(decorLocation)
        sampler.setNavigationBounds(
            screenBounds.copy(
                left = screenBounds.left - decorLocation[0],
                top = screenBounds.top - decorLocation[1],
            ),
        )
    }

    companion object {
        private const val MAX_TABS = 8

        fun create(activity: Activity, target: NativeViewBottomBarTarget): NativeViewBottomBarHost? =
            runCatching {
                val overlayParent = activity.window.decorView as? ViewGroup ?: return null
                val bar = overlayParent.findDescendantByClassNames(target.bottomBarClassNames)
                    ?: return null
                val contentHost = target.contentHostMethodName?.let(activity::invokeNoArg) as? View
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
                )
            }.onFailure {
                Log.e("MyHyperModifier", "Could not attach ${target.logName} navigation", it)
            }.getOrNull()
    }
}

@Composable
private fun NativeViewBottomBarContent(
    state: NativeViewNavigationState,
    target: NativeViewBottomBarTarget,
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
        val nativeIcons = tab.icons?.takeUnless { target.useMiuixIcons() }
        val monochrome = nativeIcons != null && target.useMonochromeIcons()
        val selectedPainter = nativeIcons?.let {
            remember(if (monochrome) it.selectedMonochrome else it.selected) {
                BitmapPainter(if (monochrome) it.selectedMonochrome else it.selected)
            }
        } ?: rememberVectorPainter(target.fallbackIcon(tab.label, index))
        val unselectedPainter = nativeIcons?.let {
            remember(if (monochrome) it.unselectedMonochrome else it.unselected) {
                BitmapPainter(if (monochrome) it.unselectedMonochrome else it.unselected)
            }
        } ?: rememberVectorPainter(target.fallbackIcon(tab.label, index))
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
                    )
                }
            }
        }
    }
}

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
