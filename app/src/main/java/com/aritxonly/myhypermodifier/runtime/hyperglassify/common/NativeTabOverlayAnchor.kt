package com.aritxonly.myhypermodifier

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout

/** Fits the overlay (including its touch region) to the host's tab strip within this window. */
internal class NativeTabOverlayAnchor(
    private val decor: ViewGroup,
    private val overlay: View,
    private val manager: WindowManager? = null,
) : ViewTreeObserver.OnGlobalLayoutListener {
    private val location = IntArray(2)
    private var dirty = true
    private var observing = false

    override fun onGlobalLayout() { dirty = true }

    fun dispose() {
        if (observing && decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    fun align(bar: View, tabs: List<View> = emptyList(), matchNativeHeight: Boolean = false) {
        if (!dirty || overlay.parent == null || bar.width <= 0 || bar.height <= 0) return
        val params = overlay.layoutParams ?: return
        if (!observing) {
            decor.viewTreeObserver.addOnGlobalLayoutListener(this)
            observing = true
        }
        decor.getLocationInWindow(location)
        val originX = location[0]
        val originY = location[1]
        bar.getLocationInWindow(location)
        val barLeft = location[0] - originX
        val barBottom = location[1] - originY + bar.height

        // A full-width background can contain tabs clustered inside just one pane.
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var stripBottom = Int.MIN_VALUE
        for (i in tabs.indices) {
            val tab = tabs[i]
            if (!tab.isAttachedToWindow || tab.visibility != View.VISIBLE || tab.width <= 0 || tab.height <= 0) continue
            tab.getLocationInWindow(location)
            val x = location[0] - originX
            left = minOf(left, x)
            right = maxOf(right, x + tab.width)
            stripBottom = maxOf(stripBottom, location[1] - originY + tab.height)
        }
        if (left >= right) {
            left = barLeft
            right = barLeft + bar.width
            stripBottom = barBottom
        }
        val inTree = params is FrameLayout.LayoutParams
        val start = if (inTree) decor.paddingLeft else 0
        val end = decor.width - if (inTree) decor.paddingRight else 0
        left = maxOf(left, start)
        right = minOf(right, end)
        if (right <= left) return
        val width = if (left == start && right == end) ViewGroup.LayoutParams.MATCH_PARENT else right - left
        val height = if (matchNativeHeight) bar.height else ViewGroup.LayoutParams.WRAP_CONTENT
        // Bottom-dock content already includes the system navigation inset in its own padding.
        val inset = if (matchNativeHeight) 0 else
            decor.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        val bottom = (decor.height - stripBottom - inset - if (inTree) decor.paddingBottom else 0)
            .coerceAtLeast(0)
        val x = left - start
        val gravity = Gravity.LEFT or Gravity.BOTTOM
        dirty = false
        when (params) {
            is FrameLayout.LayoutParams -> {
                if (params.width == width && params.height == height && params.leftMargin == x &&
                    params.bottomMargin == bottom && params.gravity == gravity) return
                params.width = width
                params.height = height
                params.leftMargin = x
                params.bottomMargin = bottom
                params.gravity = gravity
                overlay.layoutParams = params
            }
            is WindowManager.LayoutParams -> {
                if (params.width == width && params.height == height && params.x == x &&
                    params.y == bottom && params.gravity == gravity) return
                params.width = width
                params.height = height
                params.x = x
                params.y = bottom
                params.gravity = gravity
                manager?.updateViewLayout(overlay, params)
            }
        }
    }
}
