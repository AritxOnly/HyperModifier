package com.aritxonly.myhypermodifier

/** Presentation-only mask; native positions and click targets are never reordered. */
internal object NioNavigationPolicy {
    fun mask(discover: Boolean, friends: Boolean, car: Boolean, mall: Boolean, mine: Boolean): Int =
        (if (discover) 1 else 0) or (if (friends) 2 else 0) or
            (if (car) 4 else 0) or (if (mall) 8 else 0) or (if (mine) 16 else 0)

    fun isVisible(label: String, mask: Int): Boolean {
        val bit = when (NioNavigationViews.iconName(label)) {
            "discover" -> 1
            "friend" -> 2
            "car" -> 4
            "surprised" -> 8
            "mine" -> 16
            else -> return true
        }
        return mask and bit != 0
    }
}
