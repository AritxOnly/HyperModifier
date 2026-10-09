package com.aritxonly.myhypermodifier

/** A single sampled region for both dock surfaces, with window-aligned local crops. */
internal object SpotifyDockBackdrop {
    fun union(tab: ViewBackdropBounds, player: ViewBackdropBounds?): ViewBackdropBounds {
        if (player == null) return tab
        val left = minOf(tab.left, player.left)
        val top = minOf(tab.top, player.top)
        val right = maxOf(tab.left + tab.width, player.left + player.width)
        val bottom = maxOf(tab.top + tab.height, player.top + player.height)
        return ViewBackdropBounds(left, top, right - left, bottom - top)
    }

    fun offset(region: ViewBackdropBounds, target: ViewBackdropBounds): Pair<Int, Int> =
        (region.left - target.left + (region.width - target.width) / 2) to
            (region.top - target.top + (region.height - target.height) / 2)
}

internal fun ViewBackdropSnapshot.forTarget(
    region: ViewBackdropBounds,
    target: ViewBackdropBounds,
): ViewBackdropSnapshot {
    val (x, y) = SpotifyDockBackdrop.offset(region, target)
    return copy(alignmentOffsetXPx = alignmentOffsetXPx + x,
        alignmentOffsetYPx = alignmentOffsetYPx + y)
}
