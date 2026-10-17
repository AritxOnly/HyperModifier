package com.aritxonly.myhypermodifier

import android.view.View
import android.widget.AbsSeekBar
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Lift the independent progress overlay by its visible track, preserving native sizing. */
internal class DouyinProgressClearance {
    private val entries = WeakHashMap<View, Entry>()
    private var active = false
    private var bounds: ViewBackdropBounds? = null
    fun update(enabled: Boolean, dock: ViewBackdropBounds?) {
        active = enabled
        bounds = dock
        entries.values.forEach { it.apply() }
    }
    fun add(view: View) {
        if (entries.containsKey(view)) return
        val track = if (view.javaClass.simpleName == "DProSeekBar") {
            val id = view.resources.getIdentifier("wb4", "id", view.context.packageName)
            view.findViewById<View>(id) ?: return
        } else view
        val entry = Entry(view, track)
        entries[view] = entry
        view.addOnLayoutChangeListener(entry)
        if (view !== track) track.addOnLayoutChangeListener(entry)
        entry.apply()
    }
    fun dispose() {
        active = false
        entries.values.forEach { it.dispose() }
        entries.clear()
    }
    private inner class Entry(view: View, track: View) : View.OnLayoutChangeListener, Runnable {
        private val view = WeakReference(view)
        private val track = WeakReference(track)
        private var original: Float? = null
        private var applied: Float? = null
        private val location = IntArray(2)
        private var queued = false
        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
            if (!active || queued) return
            queued = true
            v.post(this)
        }
        override fun run() { queued = false; apply() }
        fun apply() {
            val target = view.get() ?: return
            if (!active) {
                original?.let { if (target.translationY == applied) target.translationY = it }
                original = null; applied = null
                return
            }
            val dock = bounds ?: return
            val line = track.get() ?: return
            if (!line.isAttachedToWindow || line.height == 0) return
            if (original == null || target.translationY != applied) original = target.translationY
            line.getLocationOnScreen(location)
            // ProSeekBarView draws the progress drawable after translating by paddingTop.
            // Its 18dp touch box includes 10dp bottom padding, which is not painted track.
            val drawable = (line as? AbsSeekBar)?.progressDrawable?.bounds
            val paintedBottom = if (drawable != null && !drawable.isEmpty) {
                (line.paddingTop + drawable.bottom).toFloat()
            } else (line.height - line.paddingBottom).toFloat()
            val value = DouyinNavigationPolicy.progressTranslation(
                original ?: 0f, target.translationY, location[1].toFloat(), paintedBottom,
                dock.top, target.resources.displayMetrics.density * 2f,
            )
            if (target.translationY != value) target.translationY = value
            applied = value
        }
        fun dispose() {
            view.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            track.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            queued = false
            apply()
        }
    }
}
