package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
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
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.ensureLoaded()
        if (ModuleSettings.isLoaded() && !ModuleSettings.xiaomiHealthFloatingNavigationEnabled) return
        val suppressor = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = {
                val id = activity.resources.getIdentifier(
                    "main_fl_bottom_container",
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
            val timedOut = SystemClock.uptimeMillis() - startedAt >= SETTINGS_WAIT_TIMEOUT_MS
            if (!ModuleSettings.isLoaded() && !timedOut) {
                activity.window.decorView.postDelayed(retry, SETTINGS_RETRY_MS)
                return@Runnable
            }
            if (!ModuleSettings.xiaomiHealthFloatingNavigationEnabled) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            suppressor.restore()
            val host = XiaomiHealthNavigationHost.create(activity)
            if (host == null && SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                suppressor.start()
                activity.window.decorView.postDelayed(retry, VIEW_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
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
    samplingView: View,
) {
    private var state by mutableStateOf(XiaomiHealthNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val previousLifecycleOwner = overlayParent.findViewTreeLifecycleOwner()
    private val previousViewModelStoreOwner = overlayParent.findViewTreeViewModelStoreOwner()
    private val previousSavedStateRegistryOwner = overlayParent.findViewTreeSavedStateRegistryOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val sampler = ViewBackdropSampler(samplingView) { backdropSnapshot = it }
    private val contentBottomPadding = InjectedScrollableContentBottomPadding(contentView)
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val originalBottomVisibility = originalBottomContainer.visibility
    private val originalDividerVisibility = originalDivider?.visibility
    private val composeView = ComposeView(activity)
    private val composeLayoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val contentInset = view.injectedNavigationContentInsetPx()
        if (contentInset > 0 && contentBottomPadding.apply(contentInset)) {
            sampler.requestCaptureBurst()
        }
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        windowImmersion.ensureApplied()
        if (contentBottomPadding.ensureApplied()) sampler.requestCaptureBurst()
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
        composeView.addOnLayoutChangeListener(composeLayoutListener)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun dispose() {
        sampler.dispose()
        composeView.removeOnLayoutChangeListener(composeLayoutListener)
        contentBottomPadding.dispose()
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
        val selectionChanged = next.selectedIndex != state.selectedIndex
        if (next != state) state = next
        if (selectionChanged) sampler.requestCaptureBurst()
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
        sampler.requestCaptureBurst()
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
            val samplingView = activity.findViewById<View>(android.R.id.content) ?: content
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
                samplingView = samplingView,
            )
        }.onFailure {
            Log.e("MyHyperModifier", "Could not attach Xiaomi Health navigation overlay", it)
        }.getOrNull()
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
    val hiddenNavigationLift = hyperGlassifyHiddenNavigationLift()
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

private const val TAB_COUNT = 4
private const val XIAOMI_HEALTH_NATIVE_ICON_SCALE = 0.90f
