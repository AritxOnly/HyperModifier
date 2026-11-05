package com.aritxonly.myhypermodifier

import android.view.View

internal object SpotifyDockLayout {
    // Only vertical_main has a sibling bottom barrier constraining the tab and player stack.
    fun hasStackedPlayer(bar: View, bottomBarrier: View?): Boolean =
        bottomBarrier != null && bottomBarrier.parent != null && bottomBarrier.parent === bar.parent

    /** The native bar can end above the window bottom (insets, split screen, rotation). */
    fun reserveHeight(nativeTabTop: Int, nativeHeight: Int, tabTop: Int, overlayHeight: Int,
                      playerBottom: Int?, gap: Int): Int? {
        val occupied = nativeTabTop + nativeHeight - tabTop
        // Ignore mixed measurements while Compose and the host are laying out the new window.
        if (occupied !in 1..overlayHeight) return null
        if (playerBottom == null) return occupied
        val nativeMargin = nativeTabTop - playerBottom
        if (nativeMargin !in -gap until occupied + gap) return null
        return occupied + gap - nativeMargin
    }
}
