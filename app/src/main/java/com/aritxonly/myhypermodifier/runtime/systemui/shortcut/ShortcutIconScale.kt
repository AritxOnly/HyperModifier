package com.aritxonly.myhypermodifier

import android.graphics.Matrix
import android.view.View

/** HMC's independent animation-matrix path; system scale/translation properties remain intact. */
internal class ShortcutIconScale(private val view: View) {
    private val original = Matrix()
    private val applied = Matrix()
    private val work = Matrix()
    private var originalPresent = false
    private var owned = false
    private var factor = Float.NaN
    private var width = -1
    private var height = -1
    private var pivotX = Float.NaN
    private var pivotY = Float.NaN

    fun update(requested: Float) {
        if (!requested.isFinite() || kotlin.math.abs(requested - 1f) < 0.0005f) {
            restore()
            return
        }
        if (view.width <= 0 || view.height <= 0) return
        // Same pivot as HMC squeezeButton; native property translation precedes this matrix.
        val x = view.translationX + view.width / 2f
        val y = view.translationY + view.height / 2f
        val configurationChanged = requested != factor || view.width != width || view.height != height
        if (owned && !configurationChanged && x == pivotX && y == pivotY) return
        if (!owned || configurationChanged) {
            // Android's getter creates a Matrix. Read it only on ownership/size changes, never
            // on the per-frame stable/translation-follow path.
            val current = view.animationMatrix
            if (!owned || current == null || current != applied) {
                originalPresent = current != null
                if (current != null) original.set(current) else original.reset()
            }
        }
        work.setScale(requested, requested, x, y)
        if (originalPresent) work.postConcat(original)
        if (!owned || work != applied) {
            view.animationMatrix = work
            applied.set(work)
            owned = true
        }
        factor = requested
        width = view.width
        height = view.height
        pivotX = x
        pivotY = y
    }

    fun restore() {
        if (!owned) return
        // A later write by the system/another module belongs to it, not to our old snapshot.
        val current = view.animationMatrix
        if (current != null && current == applied) view.animationMatrix = if (originalPresent) original else null
        owned = false
        factor = Float.NaN
        width = -1
        height = -1
    }
}
