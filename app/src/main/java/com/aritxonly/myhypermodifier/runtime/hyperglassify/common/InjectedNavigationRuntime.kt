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

/** Adds scrollable end space without permanently lifting the visible page above the floating bar. */
internal class InjectedScrollableContentBottomPadding(private val contentHost: View) {
    private data class OriginalPadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val clipToPadding: Boolean?,
    )

    private val originalPaddings = WeakHashMap<View, OriginalPadding>()
    private var appliedInset = 0

    fun apply(bottomInset: Int): Boolean {
        val nextInset = bottomInset.coerceAtLeast(0)
        appliedInset = nextInset
        var changed = false
        scrollableTargets().forEach { target ->
            val original = originalPaddings.getOrPut(target) {
                OriginalPadding(
                    target.paddingLeft,
                    target.paddingTop,
                    target.paddingRight,
                    target.paddingBottom,
                    (target as? ViewGroup)?.clipToPadding,
                )
            }
            val expectedBottom = original.bottom + nextInset
            if (target.paddingLeft != original.left || target.paddingTop != original.top ||
                target.paddingRight != original.right || target.paddingBottom != expectedBottom
            ) {
                target.setPadding(original.left, original.top, original.right, expectedBottom)
                changed = true
            }
            (target as? ViewGroup)?.let { group ->
                if (group.clipToPadding) {
                    group.clipToPadding = false
                    changed = true
                }
            }
        }
        return changed
    }

    fun ensureApplied(): Boolean = if (appliedInset > 0) apply(appliedInset) else false

    fun dispose() {
        appliedInset = 0
        originalPaddings.forEach { (target, original) ->
            target.setPadding(original.left, original.top, original.right, original.bottom)
            original.clipToPadding?.let { (target as? ViewGroup)?.clipToPadding = it }
        }
        originalPaddings.clear()
    }

    private fun scrollableTargets(): List<View> = buildList {
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view.isVerticalScrollContainer()) {
                add(view)
                // Padding the outer scrolling viewport is sufficient and avoids double-padding
                // RecyclerViews embedded inside a NestedScrollView.
                return
            }
            (view as? ViewGroup)?.let { parent ->
                repeat(parent.childCount) { index -> collect(parent.getChildAt(index)) }
            }
        }
        collect(contentHost)
    }

    private fun View.isVerticalScrollContainer(): Boolean {
        val hierarchyNames = generateSequence(javaClass as Class<*>?) { it.superclass }
            .map(Class<*>::getName)
            .toList()
        if (hierarchyNames.any { it == "android.widget.HorizontalScrollView" }) return false
        if (hierarchyNames.any {
                it == "android.widget.ScrollView" ||
                    it == "android.widget.AbsListView" ||
                    it == "android.webkit.WebView" ||
                    it.endsWith("NestedScrollView")
            }
        ) return true
        if (hierarchyNames.none { it.endsWith("RecyclerView") }) return false

        // RecyclerView's own canScrollVertically() reports current range, while its layout
        // manager reports orientation even before the adapter has enough rows to scroll.
        return runCatching {
            val layoutManager = javaClass.getMethod("getLayoutManager").invoke(this)
                ?: return@runCatching false
            layoutManager.javaClass.getMethod("canScrollVertically")
                .invoke(layoutManager) as? Boolean ?: false
        }.getOrDefault(false)
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



internal val INJECTED_NAVIGATION_SHADOW_TOP_PADDING = 24.dp
private const val PREVIOUS_INJECTED_NAVIGATION_TOP_PADDING_DP = 12f

internal fun View.injectedNavigationContentInsetPx(): Int {
    val extraShadowSpacePx =
        (resources.displayMetrics.density *
            (INJECTED_NAVIGATION_SHADOW_TOP_PADDING.value -
                PREVIOUS_INJECTED_NAVIGATION_TOP_PADDING_DP))
            .roundToInt()
    return (height - extraShadowSpacePx).coerceAtLeast(0)
}
