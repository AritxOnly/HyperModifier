// Shortcut-only extraction of HyperMusicCover MiniPlayerRuntime.kt's disc controller.
package com.aritxonly.myhypermodifier

import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.roundToInt

internal class HmcShortcutController(
    private val host: ViewGroup,
    private val left: View,
    private val right: View,
    private val material: ShortcutMaterial,
    private val locked: () -> Boolean,
    private val dozing: () -> Boolean,
) : View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
    private val discs = arrayOfNulls<ShortcutDisc>(2)
    private val iconScales = arrayOf(ShortcutIconScale(left), ShortcutIconScale(right))
    private var iconFactor = 1f
    private val discToScreen = Matrix()
    private val discInverse = Matrix()
    private val scratch = Matrix()
    private val discPoint = FloatArray(2)
    private val discUnit = FloatArray(4)
    private var followRoot: WeakReference<View>? = null
    private var observing: ViewTreeObserver? = null
    private var destroyed = false
    private var failure = false
    private var diameter = 0
    private var wanted = false
    private var renderObserved = false
    private val dress: (ImageView) -> Unit = { material.material(it) }
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (wanted && !destroyed && !failure) {
            try { updateDiscs() }
            catch (error: Exception) {
                failure = true
                stop()
                HookDiagnostics.failure("com.android.systemui", "快捷玻璃材质应用", error)
            }
        }
        true
    }

    init {
        var v: View? = left
        while (v != null && v !== host) {
            if (resourceName(v) == "keyguard_root_view") followRoot = WeakReference(v)
            v = v.parent as? View
        }
        host.addOnAttachStateChangeListener(this)
        host.addOnLayoutChangeListener(this)
        left.addOnLayoutChangeListener(this)
        right.addOnLayoutChangeListener(this)
        refresh()
    }

    fun canReuse() = !destroyed && !failure
    fun owns(l: View, r: View) = left === l && right === r
    fun root(): View = host

    fun refresh() {
        if (destroyed) return
        wanted = LockscreenShortcutGlassHooks.enabled()
        iconFactor = LockscreenBottomPolicy.iconScale(ModuleSettings.lockscreenShortcutGlassSize)
        diameter = (LockscreenBottomPolicy.size(ModuleSettings.lockscreenShortcutGlassSize) * host.resources.displayMetrics.density).roundToInt()
        if (!wanted || failure) { stop(); return }
        try { material.generation(host.context) }
        catch (error: Exception) {
            failure = true
            stop()
            HookDiagnostics.failure("com.android.systemui", "快捷玻璃材质准备", error)
            return
        }
        if (host.isAttachedToWindow && observing == null) {
            observing = host.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
        }
        host.invalidate()
        LockscreenNotificationBottomHooks.geometryChanged()
    }

    // HMC updateDiscs, without the mini-player's squeeze/holdButtons behaviour. The lockscreen's
    // own button transforms/fades remain authoritative; there is no second animation clock.
    private fun updateDiscs() {
        val d = diameter
        val frame = (d * 1.6f).toInt()
        val onKeyguard = locked()
        if (!onKeyguard) {
            for (side in 0..1) {
                iconScales[side].restore()
                discs[side]?.let { if (it.visibility != View.GONE) it.visibility = View.GONE }
            }
            return
        }
        val parent = discParent()
        discToScreen.reset()
        parent.transformMatrixToGlobal(discToScreen)
        val placed = discToScreen.invert(discInverse)
        for (side in 0..1) {
            val button = button(side)
            val shown = placed && onKeyguard && button.isAttachedToWindow && button.isShown && button.width > 0 && button.height > 0
            var disc = discs[side]
            if (!shown) {
                if (disc != null && disc.visibility != View.GONE) disc.visibility = View.GONE
                iconScales[side].restore()
                continue
            }
            if (disc == null) {
                disc = ShortcutDisc(host.context)
                discs[side] = disc
            }
            if (!discInPlace(disc, parent)) {
                (disc.parent as? ViewGroup)?.removeView(disc)
                parent.addView(disc, layerSlot(parent), ViewGroup.LayoutParams(frame, frame))
            }
            resizeSmallIsland(disc, frame, frame)
            disc.setShape(d, d)
            disc.dress(material.currentGeneration(), dress)
            if (disc.visibility != View.VISIBLE) disc.visibility = View.VISIBLE
            if (!drawnCentre(button, discPoint, discInverse)) continue
            val zoom = drawnZoom(button, discInverse)
            val fade = if (dozing() && chainFade(button) < 0.08f) 0f
                else chainFade(button, disc.parent as? View ?: host)
            var tx = discPoint[0] - frame / 2f - disc.left
            var ty = discPoint[1] - frame / 2f - disc.top
            if (abs(zoom - 1f) < 0.0005f) { tx = round(tx); ty = round(ty) }
            setIfChanged(disc, tx, ty, zoom, zoom, fade)
            iconScales[side].update(iconFactor)
            if (!renderObserved && fade > 0f) {
                renderObserved = true
                HookDiagnostics.observed("com.android.systemui", "快捷玻璃材质应用", "圆盘已挂载，直径=" + d + "px，alpha=" + fade + "，parent=" + parent.javaClass.simpleName)
            }
        }
    }

    private fun button(side: Int) = if (side == 0) left else right

    /** Unanimated layout position, as HMC stackFloor/stackTopInHost use. */
    fun shortcutTopIn(root: View): Float {
        if (!wanted || failure || destroyed || !locked()) return Float.NaN
        var top = Float.POSITIVE_INFINITY
        for (side in 0..1) {
            val button = button(side)
            if (!button.isAttachedToWindow || button.visibility != View.VISIBLE || button.height <= 0) continue
            val origin = layoutTopIn(button, root)
            if (origin.isFinite()) top = minOf(top, origin + button.height / 2f - diameter / 2f)
        }
        return if (top.isFinite()) top else Float.NaN
    }

    // Copied HMC matrix geometry and placement routines.
    private fun drawnCentre(v: View, out: FloatArray, inverse: Matrix): Boolean {
        if (!v.isAttachedToWindow || v.width <= 0) return false
        scratch.reset()
        v.transformMatrixToGlobal(scratch)
        scratch.postConcat(inverse)
        out[0] = v.width / 2f
        out[1] = v.height / 2f
        scratch.mapPoints(out)
        return true
    }
    private fun drawnZoom(v: View, inverse: Matrix): Float {
        scratch.reset()
        v.transformMatrixToGlobal(scratch)
        scratch.postConcat(inverse)
        discUnit[0] = 0f; discUnit[1] = 0f; discUnit[2] = 1f; discUnit[3] = 0f
        scratch.mapPoints(discUnit)
        return hypot(discUnit[2] - discUnit[0], discUnit[3] - discUnit[1])
    }
    private fun chainFade(v: View, upTo: View = host): Float {
        var fade = 1f
        var cur: View? = v
        while (cur != null && cur !== upTo && cur !== host) {
            fade *= cur.alpha * ShortcutViewAccess.transitionAlpha(cur)
            cur = cur.parent as? View
        }
        return fade
    }
    private fun setIfChanged(v: View, tx: Float, ty: Float, sx: Float, sy: Float, alpha: Float) {
        if (abs(v.translationX - tx) > 0.25f) v.translationX = tx
        if (abs(v.translationY - ty) > 0.25f) v.translationY = ty
        if (abs(v.scaleX - sx) > 0.0005f) v.scaleX = sx
        if (abs(v.scaleY - sy) > 0.0005f) v.scaleY = sy
        if (abs(v.alpha - alpha) > 0.002f) v.alpha = alpha
    }
    private fun layerSlot(parent: ViewGroup): Int {
        var v: View = left
        while (true) {
            val up = v.parent as? View ?: return parent.childCount
            if (up === parent) {
                val at = parent.indexOfChild(v)
                return if (at < 0) parent.childCount else at
            }
            v = up
        }
    }
    private fun discParent(): ViewGroup {
        val root = followRoot?.get() as? ViewGroup ?: return host
        var v: View? = left
        while (v != null && v !== host) {
            if (v === root) return root
            v = v.parent as? View
        }
        return host
    }
    private fun discInPlace(disc: View, parent: ViewGroup): Boolean {
        if (disc.parent !== parent) return false
        val slot = layerSlot(parent)
        val at = parent.indexOfChild(disc)
        if (at < 0 || at >= slot) return false
        for (i in at + 1 until slot) if (parent.getChildAt(i) !is ShortcutDisc) return false
        return true
    }
    private fun resizeSmallIsland(small: ShortcutDisc, width: Int, height: Int) {
        if (small.layoutParams.width != width || small.layoutParams.height != height) {
            small.layoutParams = small.layoutParams.apply { this.width = width; this.height = height }
        }
        if (small.width == width && small.height == height) return
        val cx = small.x + small.width / 2f
        val cy = small.y + small.height / 2f
        small.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        small.layout(small.left, small.top, small.left + width, small.top + height)
        small.translationX = cx - small.left - width / 2f
        small.translationY = cy - small.top - height / 2f
    }
    private fun stop() {
        observing?.let { if (it.isAlive) it.removeOnPreDrawListener(preDraw) }
        observing = null
        for (side in 0..1) {
            iconScales[side].restore()
            discs[side]?.let { (it.parent as? ViewGroup)?.removeView(it) }
            discs[side] = null
        }
        LockscreenNotificationBottomHooks.geometryChanged()
    }
    fun destroy() {
        if (destroyed) return
        destroyed = true
        stop()
        host.removeOnAttachStateChangeListener(this)
        host.removeOnLayoutChangeListener(this)
        left.removeOnLayoutChangeListener(this)
        right.removeOnLayoutChangeListener(this)
    }
    override fun onViewAttachedToWindow(v: View) { refresh() }
    override fun onViewDetachedFromWindow(v: View) { destroy() }
    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
        if (l != ol || t != ot || r != or || b != ob) {
            if (v === host) refresh()
            LockscreenNotificationBottomHooks.geometryChanged()
        }
    }
    companion object {
        internal fun resourceName(v: View): String? = if (v.id == View.NO_ID) null else
            try { v.resources.getResourceEntryName(v.id) } catch (_: android.content.res.Resources.NotFoundException) { null }
        internal fun layoutTopIn(view: View, root: View): Float {
            var v = view
            var y = 0f
            while (v !== root) {
                y += v.top
                v = v.parent as? View ?: return Float.NaN
            }
            return y
        }
    }
}
