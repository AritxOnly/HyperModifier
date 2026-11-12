// Extracted from HyperMusicCover MiniPlayerSqueeze.kt (user-contributed shortcut path).
// Only the unrelated small-island text/artwork path is omitted.
package com.aritxonly.myhypermodifier

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.min

internal class ShortcutDisc(context: Context) : FrameLayout(context) {
    private val element = MaterialElement(context)
    private var dressWith: ((ImageView) -> Unit)? = null
    private var dressedAs = -1
    private var shapeW = 0
    private var shapeH = 0
    private var shapeDx = 0

    /**
     * The disc is squeezed by its shape, not by a scale: a frame wider than any squeeze with a
     * rounded rectangle cut out of its middle, as tall and wide as the squeeze makes it. An
     * oval clip under an uneven scale was drawn with jagged edges (filmed 2026-09-24); a
     * rounded rectangle at its own pixels keeps them smooth. Round at rest, a capsule when
     * squeezed either way.
     */
    private val frameShape = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val l = (view.width - shapeW) / 2 + shapeDx
            val t = (view.height - shapeH) / 2
            outline.setRoundRect(l, t, l + shapeW, t + shapeH, min(shapeW, shapeH) / 2f)
        }
    }
    private val elementShape = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) =
            outline.setRoundRect(0, 0, view.width, view.height, min(view.width, view.height) / 2f)
    }

    init {
        // In keyguard_root_view it is a ConstraintLayout's child, and every child there needs an
        // id: the OEM's ConstraintSet.clone of that view, on each change of the lock screen's
        // blueprint, threw for one without and took SystemUI down - nine times on 2026-09-29.
        id = View.generateViewId()
        outlineProvider = frameShape
        clipToOutline = true
        outlineAmbientShadowColor = Color.TRANSPARENT
        outlineSpotShadowColor = Color.TRANSPARENT
        element.scaleType = ImageView.ScaleType.FIT_XY
        // Its own circle, whoever sets another (MaterialElement): the AOD's dim sets the card's.
        element.shape = elementShape
        addView(element, LayoutParams(0, 0))
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** The card's material, applied again only when the card has been dressed anew. */
    fun dress(generation: Int, apply: (ImageView) -> Unit) {
        dressWith = apply
        if (generation == dressedAs) return
        dressedAs = generation
        if (shapeW > 1 && shapeH > 1) applyDress()
    }

    /**
     * The disc's shape this frame, in pixels: centred in the frame, or [dx] off centre - a
     * small island leaving the pill's place, still where the pill was.
     */
    /** The width setShape last gave it: what it is drawn as, whatever its frame. */
    val shapeWidth: Int get() = shapeW

    fun setShape(w: Int, h: Int, dx: Int = 0) {
        if (w == shapeW && h == shapeH && dx == shapeDx) return
        val first = shapeW <= 1 || shapeH <= 1
        shapeW = w
        shapeH = h
        shapeDx = dx
        placeElement()
        invalidateOutline()
        element.invalidateOutline()
        // A material applied before there was a size draws nothing; it goes on again now.
        if (first && w > 1 && h > 1) applyDress()
    }


    private fun placeElement() {
        val l = (width - shapeW) / 2 + shapeDx
        val t = (height - shapeH) / 2
        element.layout(l, t, l + shapeW, t + shapeH)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) =
        placeElement()

    private fun applyDress() {
        dressWith?.invoke(element)
        element.outlineProvider = elementShape
        element.clipToOutline = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
