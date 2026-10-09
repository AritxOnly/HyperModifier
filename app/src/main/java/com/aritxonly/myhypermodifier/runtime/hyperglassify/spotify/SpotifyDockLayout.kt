package com.aritxonly.myhypermodifier

internal object SpotifyDockLayout {
    fun reserveHeight(windowBottom: Int, tabTop: Int, nativeTabTop: Int,
                      playerBottom: Int?, gap: Int): Int {
        val nativeMargin = playerBottom?.let { nativeTabTop - it }
        return (windowBottom - tabTop + if (nativeMargin != null) gap - nativeMargin else 0)
            .coerceAtLeast(1)
    }
}
