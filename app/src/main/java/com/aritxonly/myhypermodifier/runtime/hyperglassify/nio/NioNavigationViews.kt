package com.aritxonly.myhypermodifier

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asImageBitmap

/** Resource names verified in NIO 6.9.6; no copied assets or replacement icon library. */
internal object NioNavigationViews {
    fun iconName(label: String): String? = when (label.trim()) {
        "发现", "首页", "社区" -> "discover"
        "朋友", "消息" -> "friend"
        "爱车", "车辆", "我的车", "购车" -> "car"
        "惊喜", "商城", "好物" -> "surprised"
        "我的", "我" -> "mine"
        else -> null
    }

    fun resourceId(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", ModuleScopePackage.NIO)

    fun tabs(bar: View, labelId: Int, iconId: Int): List<View> = buildList {
        fun visit(view: View) {
            val before = size
            (view as? ViewGroup)?.let { group ->
                repeat(group.childCount) { visit(group.getChildAt(it)) }
            }
            // Innermost clickable wrapper owns the native route; ScrollTab itself may be clickable.
            if (size == before && view !== bar && view.visibility != View.GONE && view.isClickable &&
                view.findViewById<View>(labelId) != null && view.findViewById<View>(iconId) != null
            ) add(view)
        }
        visit(bar)
    }

    fun selectedIndex(tabs: List<View>, labelId: Int): Int {
        fun hasSelectedView(view: View): Boolean {
            if (view.isSelected) return true
            (view as? ViewGroup)?.let { group ->
                repeat(group.childCount) { if (hasSelectedView(group.getChildAt(it))) return true }
            }
            return false
        }
        val selected = tabs.indexOfFirst(::hasSelectedView)
        if (selected >= 0) return selected
        // The native labels may express selection with color rather than View.selected.
        // Only accept a single outlier among >= 3 labels; never infer from brightness/theme.
        if (tabs.size < 3) return -1
        for (index in tabs.indices) {
            val label = tabs[index].findViewById<TextView>(labelId) ?: return -1
            val other = tabs[if (index == 0) 1 else 0].findViewById<TextView>(labelId) ?: return -1
            if (label.currentTextColor == other.currentTextColor) continue
            if (tabs.indices.all { it == index ||
                    tabs[it].findViewById<TextView>(labelId)?.currentTextColor == other.currentTextColor }) return index
        }
        return -1
    }

    fun icons(tab: View, label: String): NativeTabIconPair? = runCatching {
        val name = iconName(label) ?: return null
        fun render(state: String): androidx.compose.ui.graphics.ImageBitmap? {
            val id = tab.resources.getIdentifier("lg_widget_core_icon_${name}_28_$state", "drawable", ModuleScopePackage.NIO)
            if (id == 0) return null
            val drawable = tab.resources.getDrawable(id, tab.context.theme).mutate()
            val bitmap = Bitmap.createBitmap(84, 84, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            return bitmap.asImageBitmap()
        }
        val normal = render("normal") ?: return null
        val focused = render("focused") ?: return null
        NativeTabIconPair(focused, normal, focused, normal)
    }.getOrNull()
}

/** The app reserves its bar with RelativeLayout.ABOVE, not a bottom margin. */
internal class NioContentReservation(content: View) {
    private val content = java.lang.ref.WeakReference(content)
    private var originalAbove: Int? = null
    private var originalBottomPadding: Int? = null
    private var appliedBottomPadding: Int? = null

    fun replace(enabled: Boolean) {
        val view = content.get() ?: return
        if (enabled) {
            // Only re-read insets when native code changes padding. Preserve non-navigation space.
            if (originalBottomPadding == null || view.paddingBottom != appliedBottomPadding) {
                originalBottomPadding = view.paddingBottom
                val navigation = view.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
                appliedBottomPadding = (view.paddingBottom - navigation).coerceAtLeast(0)
            }
            val bottom = appliedBottomPadding ?: view.paddingBottom
            if (view.paddingBottom != bottom) view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
        } else {
            originalBottomPadding?.takeIf { view.paddingBottom == appliedBottomPadding }?.let { bottom ->
                if (view.paddingBottom != bottom) view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
            }
            originalBottomPadding = null
            appliedBottomPadding = null
        }
        val params = view.layoutParams as? RelativeLayout.LayoutParams ?: return
        if (enabled) {
            if (originalAbove == null) originalAbove = params.getRule(RelativeLayout.ABOVE)
            if (params.getRule(RelativeLayout.ABOVE) == 0) return
            params.removeRule(RelativeLayout.ABOVE)
        } else {
            val original = originalAbove ?: return
            originalAbove = null
            if (params.getRule(RelativeLayout.ABOVE) == original) return
            params.addRule(RelativeLayout.ABOVE, original)
        }
        view.layoutParams = params
    }
}
