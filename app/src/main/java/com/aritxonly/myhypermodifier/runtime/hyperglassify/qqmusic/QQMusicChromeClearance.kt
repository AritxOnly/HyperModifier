package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max

/** Moves the shared QQ Music bottom chrome without touching the animated media card. */
internal class QQMusicChromeClearance(
    private val mediaCardClassNames: Set<String> = QQMusicMediaCardClassNames,
) {
    private data class LayoutWatch(
        val root: WeakReference<View>,
        val listener: ViewTreeObserver.OnGlobalLayoutListener,
        val refresh: Runnable,
    )

    private val boundsByActivity = WeakHashMap<Activity, ViewBackdropBounds>()
    private val movedRoots = WeakHashMap<Activity, View>()
    private val originalTranslations = WeakHashMap<View, Float>()
    private val layoutWatches = WeakHashMap<Activity, LayoutWatch>()

    fun apply(activity: Activity, bounds: ViewBackdropBounds) {
        if (activity.isFinishing || activity.isDestroyed) return
        boundsByActivity[activity] = bounds
        ensureLayoutWatch(activity)
        applyNow(activity, bounds)
    }

    fun refresh(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        boundsByActivity[activity]?.let { applyNow(activity, it) }
    }

    fun restore(activity: Activity) {
        boundsByActivity.remove(activity)
        removeLayoutWatch(activity)
        restoreNow(activity)
    }

    private fun applyNow(activity: Activity, dockBounds: ViewBackdropBounds) {
        val decor = activity.window.decorView
        val bar = findQQMusicBottomBar(decor) ?: return
        val card = findMediaCard(decor, bar)
        if (card == null || !card.isShown || card.height <= 0) {
            restoreNow(activity)
            return
        }
        val movableRoot = findMovableChromeRoot(decor, bar, card) ?: return
        val previousRoot = movedRoots[activity]
        if (previousRoot != null && previousRoot !== movableRoot) restoreRoot(previousRoot)

        val originalTranslation = originalTranslations.getOrPut(movableRoot) {
            movableRoot.translationY
        }
        val appliedTranslation = movableRoot.translationY - originalTranslation
        val cardLayoutTop =
            card.getLocationOnScreenY().toFloat() - card.translationY - appliedTranslation
        val cardLayoutBottom = cardLayoutTop + card.height
        val gap = (MEDIA_CARD_GAP_DP * card.resources.displayMetrics.density).toInt()
        val overlap = max(0f, cardLayoutBottom - (dockBounds.top - gap).toFloat())
        val targetTranslation = originalTranslation - overlap
        if (abs(movableRoot.translationY - targetTranslation) > 0.5f) {
            movableRoot.translationY = targetTranslation
        }
        movedRoots[activity] = movableRoot
    }

    internal fun findMediaCard(root: View, bar: View): View? {
        var best: View? = null
        var bestArea = Int.MAX_VALUE

        fun visit(view: View) {
            if (view !== bar && view is ViewGroup && view.isShown &&
                view.width > 0 && view.height > 0 &&
                view.javaClass.name in mediaCardClassNames &&
                !bar.descendsFromChromeView(view)
            ) {
                val area = view.width * view.height
                if (area < bestArea) {
                    best = view
                    bestArea = area
                }
            }
            (view as? ViewGroup)?.let { group ->
                repeat(group.childCount) { visit(group.getChildAt(it)) }
            }
        }
        visit(root)
        return best
    }

    internal fun findMovableChromeRoot(root: View, bar: View, card: View): View? {
        var common: View? = card.parent as? View
        while (common != null && common !== root && !bar.descendsFromChromeView(common)) {
            common = common.parent as? View
        }
        if (common == null || common === root) return null

        val heightLimit = maxOf(
            common.height * MAX_CHROME_HEIGHT_RATIO,
            bar.height * MAX_CHROME_HEIGHT_RATIO,
        )
        var candidate = common.parent as? View
        while (candidate != null && candidate !== root) {
            if (candidate !== bar && candidate.width > 0 && candidate.height > 0 &&
                candidate.height <= heightLimit
            ) {
                return candidate
            }
            candidate = candidate.parent as? View
        }
        return null
    }

    private fun restoreNow(activity: Activity) {
        movedRoots.remove(activity)?.let(::restoreRoot)
    }

    private fun restoreRoot(root: View) {
        originalTranslations.remove(root)?.let { root.translationY = it }
    }

    private fun ensureLayoutWatch(activity: Activity) {
        val root = activity.window.decorView
        val existing = layoutWatches[activity]
        if (existing?.root?.get() === root) return
        if (existing != null) removeLayoutWatch(activity)
        val activityReference = WeakReference(activity)
        val refresh = Runnable {
            val current = activityReference.get() ?: return@Runnable
            if (!current.isFinishing && !current.isDestroyed) refresh(current)
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            root.removeCallbacks(refresh)
            root.postDelayed(refresh, LAYOUT_SETTLE_DELAY_MS)
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        layoutWatches[activity] = LayoutWatch(WeakReference(root), listener, refresh)
    }

    private fun removeLayoutWatch(activity: Activity) {
        layoutWatches.remove(activity)?.let { watch ->
            watch.root.get()?.let { root ->
                root.removeCallbacks(watch.refresh)
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(watch.listener)
                }
            }
        }
    }

    private fun View.getLocationOnScreenY(): Int =
        IntArray(2).also(::getLocationOnScreen)[1]

    internal fun View.descendsFromChromeView(ancestor: View): Boolean {
        var current: View? = this
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private companion object {
        const val LAYOUT_SETTLE_DELAY_MS = 120L
        const val MEDIA_CARD_GAP_DP = 0f
        const val MAX_CHROME_HEIGHT_RATIO = 2.5f
    }
}

private val QQMusicMediaCardClassNames = setOf(
    "com.tencent.qqmusic.ui.minibar.music.view.MinibarRootLayout",
)
