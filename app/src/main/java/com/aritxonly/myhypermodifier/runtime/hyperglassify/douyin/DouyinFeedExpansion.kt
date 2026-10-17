package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Collapse only the native feed spacer, before measurement. Never resize video/pager ancestors. */
internal object DouyinFeedExpansion {
    private val spaces = WeakHashMap<View, Reservation>()
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            val reservation = spaces[view] ?: return
            // Preinflated/recycled feed views can carry an application-backed Context.
            // The attached root has the actual window owner, before the first measure.
            val owner = homepage(view.rootView.context) ?: homepage(view.context)
            bind(view, reservation, owner)
        }
        override fun onViewDetachedFromWindow(view: View) = Unit
    }

    /** Also covers feed views preinflated before the constructor hooks were installed. */
    fun registerExisting(owner: Activity) {
        val stack = java.util.ArrayDeque<View>()
        stack.add(owner.window.decorView)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 2048) {
            val view = stack.removeLast()
            when (view.javaClass.name) {
                "com.ss.android.ugc.aweme.feed.ui.bottom.BottomSpace" -> {
                    registerSpacer(view)
                    spaces[view]?.let { bind(view, it, owner) }
                }
                // Their large child trees cannot contain feed-level spacers.
                "com.ss.android.ugc.aweme.ad.feed.VideoViewHolderRootView",
                "com.ss.android.ugc.aweme.homepage.tab.bottom.ui.tabbar.container.MainBottomTabContainer" -> continue
                else -> if (view is ViewGroup) repeat(view.childCount) { stack.add(view.getChildAt(it)) }
            }
        }
    }

    private fun bind(view: View, reservation: Reservation, owner: Activity?) {
        reservation.owner = WeakReference(owner)
        reservation.enabled = owner != null && ModuleSettings.moduleHooksEnabled &&
            ModuleSettings.douyinFloatingNavigationEnabled
        apply(view, reservation)
    }

    @JvmStatic fun registerSpacer(view: View) {
        if (spaces.containsKey(view)) return
        val owner = homepage(view.context)
        val reservation = Reservation(owner, view.visibility)
        spaces[view] = reservation
        view.addOnAttachStateChangeListener(attachment)
        reservation.enabled = owner != null && ModuleSettings.moduleHooksEnabled && ModuleSettings.douyinFloatingNavigationEnabled
        apply(view, reservation)
        if (view.isAttachedToWindow) attachment.onViewAttachedToWindow(view)
    }

    /** BottomSpace.LIZIZ is shared by its setVisibility override and direct native callers. */
    @JvmStatic fun nativeVisibility(view: View, requested: Int): Int {
        val reservation = spaces[view] ?: return requested
        if (reservation.writing) return requested
        reservation.nativeVisibility = requested
        return if (reservation.enabled) View.GONE else requested
    }

    fun setEnabled(owner: Activity, enabled: Boolean) {
        for ((view, reservation) in spaces) {
            if (reservation.owner.get() !== owner || reservation.enabled == enabled) continue
            reservation.enabled = enabled
            apply(view, reservation)
        }
    }

    fun dispose(owner: Activity) {
        val iterator = spaces.entries.iterator()
        while (iterator.hasNext()) {
            val (view, reservation) = iterator.next()
            if (reservation.owner.get() !== owner) continue
            reservation.enabled = false
            apply(view, reservation)
            view.removeOnAttachStateChangeListener(attachment)
            iterator.remove()
        }
    }

    private fun apply(view: View, reservation: Reservation) {
        val desired = if (reservation.enabled) View.GONE else reservation.nativeVisibility
        if (view.visibility == desired) return
        reservation.writing = true
        try { view.visibility = desired }
        finally { reservation.writing = false }
    }

    private fun homepage(context: Context): Activity? {
        var current = context
        repeat(12) {
            if (current is Activity) {
                return current.takeIf {
                    it.javaClass.name == "com.ss.android.ugc.aweme.main.MainActivity" ||
                        it.javaClass.name == "com.ss.android.ugc.aweme.splash.SplashActivity"
                }
            }
            val wrapper = current as? ContextWrapper ?: return null
            val next = wrapper.baseContext
            if (next === current) return null
            current = next
        }
        return null
    }

    private class Reservation(owner: Activity?, var nativeVisibility: Int) {
        var owner = WeakReference(owner)
        var enabled = false
        var writing = false
    }
}
