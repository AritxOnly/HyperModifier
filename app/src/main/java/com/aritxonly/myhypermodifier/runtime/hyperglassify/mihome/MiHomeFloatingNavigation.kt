package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
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
import java.lang.reflect.Field
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MI_HOME_TAB_LAYOUT_CLASS =
    "com.xiaomi.smarthome.newui.buttomtab.TabPageIndicatorNew"
private const val MI_HOME_VIEW_PAGER_CLASS = "com.xiaomi.smarthome.ui.LinearViewPager"

/** Replaces Mi Home's visual tab strip while keeping its native routing and analytics intact. */
internal object MiHomeFloatingNavigation {
    private val hosts = WeakHashMap<Activity, MiHomeNavigationHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    /** Starts immersion before Mi Home inflates its first frame. */
    @JvmStatic
    fun prepare(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity) || startupSuppressors.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !ModuleSettings.miHomeFloatingNavigationEnabled) return
        val startupLayout = MiHomeEarlyImmersiveLayout(activity)
        startupSuppressors[activity] = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = {
                activity.window.decorView.findDescendantByClassName(MI_HOME_TAB_LAYOUT_CLASS)
            },
            onSuppress = startupLayout::suppress,
            onRestore = startupLayout::restore,
        ).also(EarlyBottomBarSuppressor::start)
    }

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !ModuleSettings.miHomeFloatingNavigationEnabled) {
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
            if (ModuleSettings.isLoaded() && !ModuleSettings.miHomeFloatingNavigationEnabled) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            // Restore and create in one main-thread turn. This lets the host capture the genuine
            // native alpha while keeping the original bar out of every rendered frame.
            suppressor.restore()
            val host = MiHomeNavigationHost.create(activity)
            if (host == null && SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                suppressor.start()
                activity.window.decorView.postDelayed(retry, VIEW_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
            if (host == null) {
                Log.w(TAG, "Mi Home 11.8.605 navigation views were not found")
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

    private const val VIEW_RETRY_MS = 16L
    private const val VIEW_WAIT_TIMEOUT_MS = 6_000L
    private const val TAG = "MyHyperModifier"
}

private data class MiHomeTabState(
    val label: String,
    val tag: String,
    val badge: Boolean,
    val icons: NativeTabIconPair?,
    val lottie: MiHomeLottieSpec?,
)

private data class MiHomeLottieSpec(
    val viewClass: Class<*>,
    val selectedResourceId: Int,
    val unselectedResourceId: Int,
    val settleWithoutAnimationResourceId: Int?,
)

private data class MiHomeNavigationState(
    val tabs: List<MiHomeTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
)

private class MiHomeNavigationHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val nativeTabLayout: View,
    private val contentView: View,
    samplingView: View,
) {
    private var state by mutableStateOf(MiHomeNavigationState())
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
        source = samplingView,
        excludedView = composeView,
        revealMiHomeMaterialCardFallbacks = true,
        pixelCopyWindow = activity.window,
        usePixelCopySampling = { state.selectedIndex == MI_HOME_PAGE_INDEX },
    ) { backdropSnapshot = it }
    private val contentBottomPadding = InjectedScrollableContentBottomPadding(contentView)
    private val iconSnapshotter = MiHomeTabIconSnapshotter(activity)
    private val startupCaptureRunnables = STARTUP_CAPTURE_DELAYS_MS.map {
        Runnable {
            if (composeWindowAttached && state.visible) sampler.requestCapture()
        }
    }
    private val originalBottomAlpha = nativeTabLayout.alpha
    private val originalBottomAccessibility = nativeTabLayout.importantForAccessibility
    private val originalContentBottomMargin =
        (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin
    private val originalContentAboveRule =
        (contentView.layoutParams as? RelativeLayout.LayoutParams)?.getRule(RelativeLayout.ABOVE) ?: 0
    private var replacingNativeChrome = false
    private val composeLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val contentInset = view.injectedNavigationContentInsetPx()
        if (replacingNativeChrome && contentInset > 0 && contentBottomPadding.apply(contentInset)) {
            sampler.requestCaptureBurst()
        }
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (replacingNativeChrome && contentBottomPadding.ensureApplied()) {
            sampler.requestCaptureBurst()
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
                MiHomeNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = ::setBackdropScreenBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        syncNativeState()
        val overlayLayoutParams = WindowManager.LayoutParams(
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
            title = "MyHyperModifier Mi Home navigation"
            // Compose consumes navigationBarsPadding below. Do not let WindowManager position
            // this attached panel above the same navigation inset first.
            setFitInsetsTypes(0)
        }
        windowManager.addView(composeView, overlayLayoutParams)
        composeWindowAttached = true
        composeView.addOnLayoutChangeListener(composeLayoutListener)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        startupCaptureRunnables.forEachIndexed { index, runnable ->
            composeView.postDelayed(runnable, STARTUP_CAPTURE_DELAYS_MS[index])
        }
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun dispose() {
        sampler.dispose()
        startupCaptureRunnables.forEach(composeView::removeCallbacks)
        composeView.removeOnLayoutChangeListener(composeLayoutListener)
        contentBottomPadding.dispose()
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        if (composeWindowAttached) {
            runCatching { windowManager.removeViewImmediate(composeView) }
            composeWindowAttached = false
        }
        nativeTabLayout.alpha = originalBottomAlpha
        nativeTabLayout.importantForAccessibility = originalBottomAccessibility
        restoreContentReservation()
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
            if (nativeTabLayout.alpha != 0f) nativeTabLayout.alpha = 0f
            nativeTabLayout.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            removeContentReservation()
            val contentInset = composeView.injectedNavigationContentInsetPx()
            if (contentInset > 0 && contentBottomPadding.apply(contentInset)) {
                sampler.requestCaptureBurst()
            }
        } else if (replacingNativeChrome) {
            replacingNativeChrome = false
            nativeTabLayout.alpha = originalBottomAlpha
            nativeTabLayout.importantForAccessibility = originalBottomAccessibility
            contentBottomPadding.dispose()
            restoreContentReservation()
            windowImmersion.dispose()
        }
    }

    private fun removeContentReservation() {
        removeMiHomeContentReservation(contentView)
    }

    private fun restoreContentReservation() {
        when (val params = contentView.layoutParams) {
            is RelativeLayout.LayoutParams -> {
                var changed = false
                if (params.getRule(RelativeLayout.ABOVE) != originalContentAboveRule) {
                    params.removeRule(RelativeLayout.ABOVE)
                    changed = true
                }
                if (originalContentAboveRule != 0 &&
                    params.getRule(RelativeLayout.ABOVE) != originalContentAboveRule
                ) {
                    params.addRule(RelativeLayout.ABOVE, originalContentAboveRule)
                }
                originalContentBottomMargin?.let { margin ->
                    if (params.bottomMargin != margin) {
                        params.bottomMargin = margin
                        changed = true
                    }
                }
                if (changed) contentView.layoutParams = params
            }
            is ViewGroup.MarginLayoutParams -> originalContentBottomMargin?.let { margin ->
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
            nativeTabLayout.javaClass.getMethod("getSelectedTabIndex")
                .invoke(nativeTabLayout) as Int
        }.getOrDefault(state.selectedIndex).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val shouldRefreshIcons = state.tabs.size != tabs.size ||
            state.tabs.isEmpty() || selected != state.selectedIndex
        val next = MiHomeNavigationState(
            tabs = tabs.mapIndexed { index, tab ->
                val cachedIcon = state.tabs.getOrNull(index)?.takeUnless { shouldRefreshIcons }
                    ?.let { MiHomeNativeIcon(it.icons, it.lottie) }
                tab.readState(index, index == selected, cachedIcon)
            },
            selectedIndex = selected,
            visible = nativeTabLayout.visibility == View.VISIBLE &&
                nativeTabLayout.isShown && tabs.size > 1,
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (next != state) state = next
        if (selectionChanged) sampler.invalidateSamplingContext()
        updateNativeChromeReplacement(next.visible)
        composeView.visibility = if (next.visible) View.VISIBLE else View.GONE
        if (selectionChanged || becameVisible) sampler.requestCaptureBurst()
    }

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

    private fun View.readState(
        index: Int,
        selected: Boolean,
        cachedIcon: MiHomeNativeIcon? = null,
    ): MiHomeTabState {
        val label = (readField("mText") as? TextView)?.text?.toString().orEmpty()
            .ifBlank { FALLBACK_LABELS.getOrElse(index) { "入口 ${index + 1}" } }
        val tag = readField("mViewTag")?.toString().orEmpty()
        val badge = runCatching {
            javaClass.getMethod("getShowPoint").invoke(this) as Boolean
        }.getOrDefault(false)
        val nativeIcon = cachedIcon ?: iconSnapshotter.read(this, selected)
        return MiHomeTabState(
            label = label,
            tag = tag,
            badge = ModuleSettings.miHomeNavigationBadgesEnabled && badge,
            icons = nativeIcon.icons,
            lottie = nativeIcon.lottie,
        )
    }

    private fun selectDestination(index: Int) {
        val tab = nativeTabViews().getOrNull(index) ?: return
        if (index != state.selectedIndex) sampler.invalidateSamplingContext()
        state = state.copy(selectedIndex = index)
        tab.performClick()
        tab.post(::syncNativeState)
        sampler.requestCaptureBurst()
    }

    private fun nativeTabViews(): List<View> = runCatching {
        (nativeTabLayout.javaClass.getMethod("getTabViewList")
            .invoke(nativeTabLayout) as? List<*>)?.filterIsInstance<View>().orEmpty()
    }.getOrDefault(emptyList()).ifEmpty {
        (nativeTabLayout as? ViewGroup)?.let { parent ->
            List(parent.childCount) { parent.getChildAt(it) }
        }.orEmpty()
    }

    companion object {
        private val FALLBACK_LABELS = listOf("设备", "智能", "发现", "我的")
        private val STARTUP_CAPTURE_DELAYS_MS = longArrayOf(120L, 320L, 700L, 1_200L, 1_800L)

        fun create(activity: Activity): MiHomeNavigationHost? = runCatching {
            val overlayParent = activity.window.decorView as? ViewGroup ?: return null
            val tabLayout = overlayParent.findDescendantByClassName(MI_HOME_TAB_LAYOUT_CLASS)
                ?: return null
            val content = overlayParent.findDescendantByClassName(MI_HOME_VIEW_PAGER_CLASS)
                ?: return null
            MiHomeNavigationHost(
                activity = activity,
                overlayParent = overlayParent,
                nativeTabLayout = tabLayout,
                contentView = content,
                samplingView = overlayParent,
            )
        }.onFailure {
            Log.e("MyHyperModifier", "Could not attach Mi Home navigation overlay", it)
        }.getOrNull()
    }
}

/** Removes Mi Home's native bottom reservation before its first frame, then restores for handoff. */
private class MiHomeEarlyImmersiveLayout(private val activity: Activity) {
    private val immersion = InjectedBottomNavigationImmersion(activity)
    private val originalReservations = IdentityHashMap<View, MiHomeContentReservation>()
    private var currentContent: View? = null

    fun suppress() {
        immersion.apply()
        immersion.ensureApplied()
        val content = currentContent?.takeIf(View::isAttachedToWindow)
            ?: activity.window.decorView.findDescendantByClassName(MI_HOME_VIEW_PAGER_CLASS)
                ?.also { currentContent = it }
            ?: return
        originalReservations.getOrPut(content) {
            val params = content.layoutParams
            MiHomeContentReservation(
                bottomMargin = (params as? ViewGroup.MarginLayoutParams)?.bottomMargin,
                aboveRule = (params as? RelativeLayout.LayoutParams)
                    ?.getRule(RelativeLayout.ABOVE) ?: 0,
            )
        }
        removeMiHomeContentReservation(content)
    }

    fun restore() {
        originalReservations.forEach { (content, original) ->
            when (val params = content.layoutParams) {
                is RelativeLayout.LayoutParams -> {
                    params.removeRule(RelativeLayout.ABOVE)
                    if (original.aboveRule != 0) {
                        params.addRule(RelativeLayout.ABOVE, original.aboveRule)
                    }
                    original.bottomMargin?.let { params.bottomMargin = it }
                    content.layoutParams = params
                }
                is ViewGroup.MarginLayoutParams -> original.bottomMargin?.let { bottomMargin ->
                    params.bottomMargin = bottomMargin
                    content.layoutParams = params
                }
            }
        }
        originalReservations.clear()
        currentContent = null
        immersion.dispose()
    }
}

private data class MiHomeContentReservation(
    val bottomMargin: Int?,
    val aboveRule: Int,
)

private fun removeMiHomeContentReservation(content: View) {
    when (val params = content.layoutParams) {
        is RelativeLayout.LayoutParams -> {
            var changed = false
            if (params.getRule(RelativeLayout.ABOVE) != 0) {
                params.removeRule(RelativeLayout.ABOVE)
                changed = true
            }
            if (params.bottomMargin != 0) {
                params.bottomMargin = 0
                changed = true
            }
            if (changed) content.layoutParams = params
        }
        is ViewGroup.MarginLayoutParams -> if (params.bottomMargin != 0) {
            params.bottomMargin = 0
            content.layoutParams = params
        }
    }
}

@Composable
private fun MiHomeNavigationContent(
    state: MiHomeNavigationState,
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
        val useMiuixIcons = ModuleSettings.miHomeMiuixIconsEnabled
        val nativeIcons = tab.icons?.takeUnless { useMiuixIcons }
        val nativeLottie = tab.lottie?.takeUnless { useMiuixIcons }
        val monochrome = (nativeIcons != null || nativeLottie != null) &&
            ModuleSettings.miHomeMonochromeIconsEnabled
        val selectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.selectedMonochrome else it.selected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(miHomeIcon(tab, index))
        val unselectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.unselectedMonochrome else it.unselected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(miHomeIcon(tab, index))
        val liveIconContent: (@Composable (Boolean, Color) -> Unit)? =
            nativeLottie?.let { spec ->
                { selected, tint ->
                    MiHomeLottieIcon(
                        spec = spec,
                        selected = selected,
                        tint = if (monochrome) tint else Color.Unspecified,
                    )
                }
            }
        MiuixFloatingTabItem(
            key = index.toString(),
            label = tab.label,
            selectedIcon = selectedPainter,
            unselectedIcon = unselectedPainter,
            preserveOriginalIconColors = (nativeIcons != null || nativeLottie != null) && !monochrome,
            badge = if (tab.badge) "" else null,
            iconContent = liveIconContent,
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

private fun miHomeIcon(tab: MiHomeTabState, index: Int): ImageVector {
    val identity = "${tab.tag} ${tab.label}".lowercase()
    return when {
        identity.contains("device") || identity.contains("home") ||
            identity.contains("设备") || identity.contains("首页") -> MiuixIcons.Home
        identity.contains("smart") || identity.contains("scene") ||
            identity.contains("智能") || identity.contains("场景") -> MiuixIcons.All
        identity.contains("discover") || identity.contains("shop") ||
            identity.contains("发现") || identity.contains("商城") -> MiuixIcons.AppRecording
        identity.contains("mine") || identity.contains("profile") ||
            identity.contains("我的") -> MiuixIcons.ContactsCircle
        else -> listOf(
            MiuixIcons.Home,
            MiuixIcons.All,
            MiuixIcons.AppRecording,
            MiuixIcons.ContactsCircle,
            MiuixIcons.SearchDevice,
        )[index.coerceIn(0, 4)]
    }
}

private fun View.findDescendantByClassName(className: String): View? {
    if (javaClass.name == className) return this
    if (this is ViewGroup) {
        repeat(childCount) { index ->
            getChildAt(index).findDescendantByClassName(className)?.let { return it }
        }
    }
    return null
}

private fun Any.readField(name: String): Any? {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            val field: Field = type.getDeclaredField(name)
            field.isAccessible = true
            return field.get(this)
        } catch (_: NoSuchFieldException) {
            type = type.superclass
        } catch (_: Throwable) {
            return null
        }
    }
    return null
}

/**
 * Mi Home mixes ordinary ImageViews with LottieAnimationViews whose model IDs point to styles,
 * not drawables. Snapshot the live native renderer chosen by TabView instead of decoding IDs.
 */
private data class MiHomeNativeIcon(
    val icons: NativeTabIconPair?,
    val lottie: MiHomeLottieSpec?,
)

private class MiHomeTabIconSnapshotter(activity: Activity) {
    private val resources = activity.resources
    private val delegate = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val animationViewId = activity.resources.getIdentifier("e46", "id", activity.packageName)
    private val staticViewId = activity.resources.getIdentifier("e48", "id", activity.packageName)

    fun read(tab: View, selected: Boolean): MiHomeNativeIcon {
        val animationView = (tab.readField("mAnimView") as? ImageView)
            ?: animationViewId.takeIf { it != 0 }?.let(tab::findViewById)
        val staticView = (tab.readField("mImageView") as? ImageView)
            ?: staticViewId.takeIf { it != 0 }?.let(tab::findViewById)
        // DeviceAnimTabView owns separate Lottie files for selected and unselected states. Keep
        // both: caching only getCurrentJson() freezes the Compose copy in whichever state happened
        // to be active when it was first read.
        val index = (tab.invokeNoArg("getIndex") as? Int) ?: 0
        val pinned = (tab.readField("Oooo00o") as? Boolean) == true
        val currentResource = (tab.invokeNoArg("getCurrentJson") as? Int).rawResourceOrNull()
        val loadedResource = (tab.readField("Oooo00O") as? Int).rawResourceOrNull()
        val ordinaryUnselected = (tab.readField("OooOoo") as? Int).rawResourceOrNull()
        val selectedCandidate = if (index == 0) {
            tab.readField(if (pinned) "OooOooO" else "OooOooo") as? Int
        } else {
            tab.readField(if (pinned) "OooOoOO" else "OooOoo0") as? Int
        }
        val unselectedCandidate = if (index == 0) {
            tab.readField("Oooo000") as? Int
        } else {
            tab.readField("OooOoo") as? Int
        }
        val selectedResource = selectedCandidate.rawResourceOrNull()
            ?: currentResource.takeIf { selected }
            ?: loadedResource
        val unselectedResource = unselectedCandidate.rawResourceOrNull()
            ?: currentResource.takeIf { !selected }
            ?: loadedResource
        if (animationView != null && selectedResource != null && unselectedResource != null) {
            return MiHomeNativeIcon(
                icons = null,
                lottie = MiHomeLottieSpec(
                    viewClass = animationView.javaClass,
                    selectedResourceId = selectedResource,
                    unselectedResourceId = unselectedResource,
                    settleWithoutAnimationResourceId = ordinaryUnselected,
                ),
            )
        }
        val source = listOfNotNull(animationView, staticView)
            .filter { it.drawable != null || it.background != null }
            .maxByOrNull { view ->
                var score = if (view.visibility == View.VISIBLE) 10_000 else 0
                if (view.javaClass.name.contains("LottieAnimationView")) score += 1_000
                score + maxOf(1, view.width) * maxOf(1, view.height)
            }
        return MiHomeNativeIcon(
            icons = delegate.snapshot(tab, source, selected),
            lottie = null,
        )
    }

    private fun Int?.rawResourceOrNull(): Int? = this?.takeIf { resourceId ->
        resourceId > 0 && runCatching {
            resources.getResourceTypeName(resourceId) == "raw"
        }.getOrDefault(false)
    }
}

private fun Any.invokeNoArg(name: String): Any? {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            return type.getDeclaredMethod(name).apply { isAccessible = true }.invoke(this)
        } catch (_: NoSuchMethodException) {
            type = type.superclass
        } catch (_: Throwable) {
            return null
        }
    }
    return null
}

@Composable
private fun MiHomeLottieIcon(spec: MiHomeLottieSpec, selected: Boolean, tint: Color) {
    AndroidView(
        factory = { context -> createMiHomeLottieView(context, spec.viewClass) },
        modifier = Modifier.fillMaxWidth().height(MiuixFloatingTabBarDefaults.TabIconSize),
        update = { imageView ->
            val previous = imageView.tag as? MiHomeLottieViewState
            val resourceId = if (selected) {
                spec.selectedResourceId
            } else {
                spec.unselectedResourceId
            }
            if (previous?.resourceId != resourceId || previous.selected != selected) {
                runCatching {
                    imageView.javaClass.getMethod("cancelAnimation").invoke(imageView)
                    imageView.javaClass.getMethod(
                        "setAnimation",
                        Int::class.javaPrimitiveType,
                    ).invoke(imageView, resourceId)
                    imageView.javaClass.getMethod("setRepeatCount", Int::class.javaPrimitiveType)
                        .invoke(imageView, 0)
                    // Match Mi Home: initial icons and the ordinary unselected resource rest on
                    // their final frame; selection (and the home-tab reverse resource) animates.
                    if (previous == null || resourceId == spec.settleWithoutAnimationResourceId) {
                        imageView.javaClass.getMethod(
                            "setProgress",
                            Float::class.javaPrimitiveType,
                        ).invoke(imageView, 1f)
                    } else {
                        imageView.javaClass.getMethod("playAnimation").invoke(imageView)
                    }
                }
            }
            val tintArgb = tint.takeUnless { it == Color.Unspecified }?.toArgb()
            if (previous?.tintArgb != tintArgb) {
                if (tintArgb == null) {
                    imageView.setLayerType(View.LAYER_TYPE_NONE, null)
                } else {
                    imageView.setLayerType(
                        View.LAYER_TYPE_HARDWARE,
                        Paint().apply {
                            colorFilter = PorterDuffColorFilter(tintArgb, PorterDuff.Mode.SRC_IN)
                        },
                    )
                }
            }
            imageView.tag = MiHomeLottieViewState(resourceId, selected, tintArgb)
        },
    )
}

private data class MiHomeLottieViewState(
    val resourceId: Int,
    val selected: Boolean,
    val tintArgb: Int?,
)

private fun createMiHomeLottieView(context: Context, viewClass: Class<*>): ImageView =
    runCatching {
        (viewClass.getConstructor(Context::class.java).newInstance(context) as ImageView).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }.getOrElse {
        ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
    }

private const val MI_HOME_PAGE_INDEX = 0
