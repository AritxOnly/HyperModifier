package com.aritxonly.myhypermodifier

import android.graphics.Color
import android.graphics.RenderNode
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.lang.ref.WeakReference
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

/** Retains app-produced display lists. No View.draw, Bitmap, GPU readback or forced source draw. */
internal class NativeGpuBackdrop private constructor(sourceView: View, consumerView: View, retainedNode: RenderNode) {
    private val source = WeakReference(sourceView)
    private val consumer = WeakReference(consumerView)
    var frameVersion by mutableIntStateOf(0)
        private set
    private var sourceNode: RenderNode? = retainedNode
    val node = RenderNode("MyHyperModifier retained backdrop")
    var geometry by mutableStateOf<ViewBackdropBounds?>(null)
        private set
    private val location = IntArray(2)
    private var active = false
    private var disposed = false
    private var bounds: ViewBackdropBounds? = null
    private var sourceLeft = Int.MIN_VALUE
    private var sourceTop = Int.MIN_VALUE
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceViewLeft = 0
    private var sourceViewTop = 0
    private var observer: ViewTreeObserver? = null
    private var redrawPosted = false
    private var separateWindow: Boolean? = null
    private fun usesSeparateWindow(): Boolean {
        separateWindow?.let { return it }
        val target = consumer.get() ?: return false
        if (!target.isAttachedToWindow) return false
        val original = source.get() ?: return false
        return (original.rootView !== target.rootView).also { separateWindow = it }
    }
    private val redraw = Runnable {
        redrawPosted = false
        if (!disposed && active) consumer.get()?.invalidate()
    }
    private val sourceDraw = ViewTreeObserver.OnDrawListener {
        // Same-window consumers share the app's frame. Re-invalidating them here would create
        // a self-sustaining loop because this observer also sees the injected view's draws.
        if (!disposed && active && !redrawPosted && usesSeparateWindow()) {
            consumer.get()?.let { view -> redrawPosted = true; view.postOnAnimation(redraw) }
        }
    }

    private val sourceLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        bounds?.let(::setBounds)
    }

    private fun observe(view: View) {
        observer?.takeIf { it.isAlive }?.removeOnDrawListener(sourceDraw)
        observer = view.viewTreeObserver.takeIf { it.isAlive }?.also { it.addOnDrawListener(sourceDraw) }
        separateWindow = null
    }
    private val sourceAttachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = observe(view)
        override fun onViewDetachedFromWindow(view: View) {
            observer?.takeIf { it.isAlive }?.removeOnDrawListener(sourceDraw)
            observer = null
            separateWindow = null
            consumer.get()?.removeCallbacks(redraw)
            redrawPosted = false
        }
    }
    init {
        // Observe after attachment: a floating ViewTreeObserver is merged and killed at attach.
        // Holding that obsolete observer would prevent removal of the merged draw listener.
        sourceView.addOnLayoutChangeListener(sourceLayout)
        sourceView.addOnAttachStateChangeListener(sourceAttachment)
        if (sourceView.isAttachedToWindow) observe(sourceView)
    }

    fun setActive(value: Boolean) {
        if (disposed || active == value) return
        active = value
        if (!value) {
            consumer.get()?.removeCallbacks(redraw)
            redrawPosted = false
        } else consumer.get()?.invalidate()
    }

    /** Called on native state/layout changes; records only when crop/geometry changes. */
    fun setBounds(value: ViewBackdropBounds) {
        if (disposed) return
        val view = source.get() ?: return
        view.getLocationInWindow(location)
        if (bounds == value && sourceLeft == location[0] && sourceTop == location[1] &&
            sourceWidth == view.width && sourceHeight == view.height &&
            sourceViewLeft == view.left && sourceViewTop == view.top && node.hasDisplayList()) return
        bounds = value
        sourceLeft = location[0]; sourceTop = location[1]
        sourceWidth = view.width; sourceHeight = view.height
        sourceViewLeft = view.left; sourceViewTop = view.top
        val bleed = (32f * view.resources.displayMetrics.density).toInt()
        val left = (value.left - bleed).coerceAtLeast(sourceLeft)
        val top = (value.top - bleed).coerceAtLeast(sourceTop)
        val right = (value.left + value.width + bleed).coerceAtMost(sourceLeft + sourceWidth)
        val bottom = (value.top + value.height + bleed).coerceAtMost(sourceTop + sourceHeight)
        if (right <= left || bottom <= top) { geometry = null; return }
        val width = right - left
        val height = bottom - top
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            canvas.drawColor(Color.BLACK)
            canvas.translate((sourceLeft - left - view.left).toFloat(),
                (sourceTop - top - view.top).toFloat())
            sourceNode?.let(canvas::drawRenderNode)
        } finally { node.endRecording() }
        geometry = ViewBackdropBounds(left, top, width, height)
    }

    fun targetBounds(): ViewBackdropBounds? = bounds

    fun dispose() {
        if (disposed) return
        disposed = true
        active = false
        consumer.get()?.removeCallbacks(redraw)
        observer?.takeIf { it.isAlive }?.removeOnDrawListener(sourceDraw)
        observer = null
        node.discardDisplayList()
        sourceNode = null
        frameConsumers.removeAll { it.get() == null || it.get() === this }
        source.get()?.removeOnLayoutChangeListener(sourceLayout)
        source.get()?.removeOnAttachStateChangeListener(sourceAttachment)
        source.clear(); consumer.clear()
        geometry = null
    }

    companion object {
        private val frameConsumers = java.util.concurrent.CopyOnWriteArrayList<WeakReference<NativeGpuBackdrop>>()
        @JvmStatic fun onNativeTextureFrame(view: View) {
            val root = view.rootView
            var index = 0
            while (index < frameConsumers.size) {
                val scene = frameConsumers[index++].get() ?: continue
                if (!scene.disposed && scene.active && scene.source.get()?.rootView === root) scene.frameVersion++
            }
        }
        // A cached access plan, not a cached View. Read the existing node without asking the
        // app to run updateDisplayListIfDirty a second time inside its own frame traversal.
        private val field by lazy {
            runCatching { View::class.java.getDeclaredField("mRenderNode").apply { isAccessible = true } }.getOrNull()
        }
        fun create(source: View, consumer: View, nativeTextureFrames: Boolean = false): NativeGpuBackdrop? {
            if (source.isAttachedToWindow && !source.isHardwareAccelerated) return null
            var ancestor: View? = consumer
            while (ancestor != null) {
                if (ancestor === source) return null // Never capture our own chrome.
                ancestor = ancestor.parent as? View
            }
            val node = runCatching { field?.get(source) as? RenderNode }.getOrNull() ?: return null
            android.util.Log.i("MyHyperModifier", "Retained GPU backdrop: ${source.javaClass.name}")
            return NativeGpuBackdrop(source, consumer, node).also { if (nativeTextureFrames) frameConsumers.add(WeakReference(it)) }
        }
    }
}

/** Publishes the retained native scene into the existing canonical Miuix optical pipeline. */
@Composable
internal fun NativeGpuBackdropLayer(
    scene: NativeGpuBackdrop,
    backdrop: LayerBackdrop,
    target: ViewBackdropBounds?,
) {
    val region = scene.geometry ?: return
    val targetBounds = target ?: return
    val density = LocalDensity.current.density
    val x = region.left - targetBounds.left + (region.width - targetBounds.width) / 2
    val y = region.top - targetBounds.top + (region.height - targetBounds.height) / 2
    Box(Modifier.offset { IntOffset(x, y) }
        .graphicsLayer(alpha = 0.001f)
        .requiredSize((region.width / density).dp, (region.height / density).dp)
        .layerBackdrop(backdrop)
        .drawWithContent {
            drawIntoCanvas { canvas ->
                if (scene.frameVersion >= 0 && canvas.nativeCanvas.isHardwareAccelerated && scene.node.hasDisplayList()) {
                    canvas.nativeCanvas.drawRenderNode(scene.node)
                }
            }
        })
}
