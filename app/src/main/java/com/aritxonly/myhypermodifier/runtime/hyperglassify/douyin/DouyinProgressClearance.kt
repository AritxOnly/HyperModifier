package com.aritxonly.myhypermodifier

import android.view.View
import android.widget.AbsSeekBar
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs

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
        val entry = Entry(view)
        entries[view] = entry
        view.addOnLayoutChangeListener(entry)
        entry.apply()
    }

    fun dispose() {
        active = false
        entries.values.forEach { it.dispose() }
        entries.clear()
    }

    private inner class Entry(private val target: View) : View.OnLayoutChangeListener {
        private val targetRef = WeakReference(target)
        private var track = WeakReference<View>(null)
        private var trackId = View.NO_ID
        private var original: Float? = null
        private var applied: Float? = null
        private val location = IntArray(2)

        private fun resolveTrack(): View? {
            val owner = targetRef.get() ?: return null
            val previous = track.get()
            if (previous != null && previous.parent != null) return previous
            if (owner.javaClass.simpleName != "DProSeekBar") {
                track = WeakReference(owner)
                return owner
            }
            if (trackId == View.NO_ID) {
                trackId = owner.resources.getIdentifier("wb4", "id", owner.context.packageName)
            }
            val next = if (trackId != View.NO_ID) owner.findViewById<View>(trackId) else null
            if (previous !== next) {
                previous?.removeOnLayoutChangeListener(this)
                track = WeakReference(next)
                next?.addOnLayoutChangeListener(this)
            }
            return next
        }

        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                                    oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
            apply()
        }

        fun apply() {
            val view = targetRef.get() ?: return
            if (!active) {
                restore(view)
                return
            }
            val dock = bounds ?: return
            val line = resolveTrack() ?: return
            if (!line.isAttachedToWindow || !line.isShown || line.height == 0) return
            val current = view.translationY
            if (original == null || (applied != null && abs(current - applied!!) > 0.5f)) {
                original = current
            }
            line.getLocationOnScreen(location)
            // ProSeekBarView draws the progress drawable after translating by paddingTop.
            // Its 18dp touch box includes 10dp bottom padding, which is not painted track.
            val drawable = (line as? AbsSeekBar)?.progressDrawable?.bounds
            val paintedBottom = if (drawable != null && !drawable.isEmpty) {
                (line.paddingTop + drawable.bottom).toFloat()
            } else {
                (line.height - line.paddingBottom).toFloat()
            }
            val value = DouyinNavigationPolicy.progressTranslation(
                original ?: 0f,
                current,
                location[1].toFloat(),
                paintedBottom,
                dock.top,
                view.resources.displayMetrics.density * 2f,
            )
            if (abs(current - value) > 0.5f) view.translationY = value
            applied = value
        }

        private fun restore(view: View) {
            val saved = original
            val last = applied
            if (saved != null && last != null && abs(view.translationY - last) <= 0.5f) {
                view.translationY = saved
            }
            original = null
            applied = null
        }

        fun dispose() {
            targetRef.get()?.removeOnLayoutChangeListener(this)
            track.get()?.removeOnLayoutChangeListener(this)
            apply()
        }
    }
}
