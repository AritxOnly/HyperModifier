package com.aritxonly.myhypermodifier

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

/** Own video layer only. Async GPU capture, cropped/downsampled, one request at a time, max 15Hz. */
internal class DouyinSurfaceBackdrop(source: View, private val publish: (ViewBackdropSnapshot?) -> Unit) : Choreographer.FrameCallback {
    private val source = WeakReference(source)
    private val main = Handler(Looper.getMainLooper())
    private val clock = Choreographer.getInstance()
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    @Volatile private var listener: Any? = null
    private var observer: ViewTreeObserver? = null
    private val surfaces = ArrayList<WeakReference<SurfaceView>>()
    private val location = IntArray(2)
    private var bounds: ViewBackdropBounds? = null
    private var active = false
    @Volatile private var disposed = false
    private var pending = false
    private var scheduled = false
    private var failed = false
    private var dirty = true
    private var epoch = 0L
    private var generation = 0L
    private var probeLogged = false
    private var requestId = 0L
    private var retryCount = 0
    private var unsupported = false
    private var watched = WeakReference<SurfaceView>(null)
    private val timeout = Runnable {
        if (pending) {
            if (++retryCount <= 2) sourceChanged("capture timeout", false)
            else fail("capture timeout", false)
        }
    }
    private val holderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = sourceChanged("surface created")
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = sourceChanged("surface changed")
        override fun surfaceDestroyed(holder: SurfaceHolder) = sourceChanged("surface destroyed")
    }
    private fun watch(view: SurfaceView) {
        if (watched.get() === view) return
        watched.get()?.holder?.removeCallback(holderCallback)
        watched = WeakReference(view)
        view.holder.addCallback(holderCallback)
    }
    private fun sourceChanged(reason: String, resetRetries: Boolean = true) {
        if (disposed || unsupported) return
        epoch++
        requestId++
        pending = false
        listener = null
        main.removeCallbacks(timeout)
        dirty = true
        failed = false
        if (resetRetries) retryCount = 0
        publish(null)
        android.util.Log.w("MyHyperModifier", "Douyin video source changed: $reason")
        schedule()
    }
    private val layout = ViewTreeObserver.OnGlobalLayoutListener {
        if (active && !failed && !pending) {
            if (surfaces.isEmpty() || surfaces.none { it.get()?.isAttachedToWindow == true }) dirty = true
            schedule()
        }
    }
    private fun observe(view: View) {
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layout)
        observer = view.viewTreeObserver.takeIf { it.isAlive }?.also { it.addOnGlobalLayoutListener(layout) }
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) { observe(view); dirty = true; schedule() }
        override fun onViewDetachedFromWindow(view: View) {
            observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layout)
            observer = null
            clock.removeFrameCallback(this@DouyinSurfaceBackdrop)
            scheduled = false
        }
    }
    init {
        samplers.add(this)
        source.addOnAttachStateChangeListener(attachment)
        if (source.isAttachedToWindow) observe(source)
    }
    fun setBounds(value: ViewBackdropBounds) {
        if (bounds == value) return
        bounds = value
        epoch++
        dirty = true
        schedule()
    }
    fun setActive(value: Boolean) {
        if (active == value || disposed) return
        active = value
        epoch++
        if (value) { dirty = true; schedule() }
        else {
            clock.removeFrameCallback(this)
            scheduled = false
            publish(null)
        }
    }
    private fun schedule() {
        if (!active || disposed || failed || scheduled || pending || bounds == null) return
        scheduled = true
        clock.postFrameCallbackDelayed(this, 66L)
    }
    private fun discover(root: View) {
        surfaces.clear()
        val stack = java.util.ArrayDeque<View>()
        stack.add(root)
        var visited = 0
        var textures = 0
        while (stack.isNotEmpty() && visited++ < 2048) {
            val view = stack.removeLast()
            if (view is android.view.TextureView) textures++
            if (view is SurfaceView) surfaces.add(WeakReference(view))
            else if (view is ViewGroup) repeat(view.childCount) { stack.add(view.getChildAt(it)) }
        }
        dirty = false
        if (!probeLogged) {
            probeLogged = true
            android.util.Log.w("MyHyperModifier", "Douyin video source probe: surfaces=${surfaces.size}, textures=$textures, root=${root.width}x${root.height}")
        }
    }
    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!active || disposed || failed || pending) return
        val target = bounds ?: return
        val root = source.get() ?: return
        val probed = dirty
        if (!root.isAttachedToWindow) return
        if (dirty) discover(root)
        val bleed = (root.resources.displayMetrics.density * 32f).toInt()
        var chosen: SurfaceView? = null
        var region: Rect? = null
        var bestArea = 0L
        var originX = 0
        var originY = 0
        for (ref in surfaces) {
            val view = ref.get() ?: continue
            if (!view.isAttachedToWindow || !view.isShown || view.width < root.width * 0.8f || view.height <= 0) continue
            view.getLocationInWindow(location)
            val left = maxOf(target.left - bleed, location[0])
            val top = maxOf(target.top - bleed, location[1])
            val right = minOf(target.left + target.width + bleed, location[0] + view.width)
            val bottom = minOf(target.top + target.height + bleed, location[1] + view.height)
            val area = (right - left).coerceAtLeast(0).toLong() * (bottom - top).coerceAtLeast(0)
            if (area > bestArea) {
                bestArea = area
                chosen = view
                region = Rect(left, top, right, bottom)
                originX = location[0]; originY = location[1]
            }
        }
        val view = chosen ?: run {
            if (!probed) { dirty = true; schedule() }
            return // Missing video: wait for native layout, no continuous discovery/polling.
        }
        val rect = region ?: return
        watch(view)
        val control = view.surfaceControl?.takeIf { it.isValid } ?: return
        val crop = Rect(rect).apply { offset(-originX, -originY) }
        val captureEpoch = epoch
        val id = ++requestId
        pending = true
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 600L)
        val handler = worker ?: HandlerThread("MHM-Douyin-LayerCapture").let {
            it.start(); thread = it; Handler(it.looper).also { handler -> worker = handler }
        }
        handler.post {
            if (disposed) return@post
            try {
                listener = NativeLayerCapture.capture(control, crop, minOf(1f, 256f / crop.width()),
                    object : NativeLayerCapture.Callback {
                        override fun onResult(bitmap: Bitmap?, status: Int) {
                            main.post {
                                if (id != requestId) { bitmap?.recycle(); return@post }
                                listener = null
                                pending = false
                                main.removeCallbacks(timeout)
                                if (disposed || !active || captureEpoch != epoch) {
                                    bitmap?.recycle() // Never published to Compose.
                                    schedule()
                                } else if (bitmap == null) {
                                    publish(null)
                                    if (++retryCount <= 2) { dirty = true; schedule() }
                                    else fail("capture callback status=$status", false)
                                } else {
                                    retryCount = 0
                                    // Published hardware bitmaps are released by reference lifetime, never
                                    // recycled while a Compose/RenderThread display list can still use them.
                                    publish(ViewBackdropSnapshot(bitmap, rect.width(), rect.height(),
                                        rect.left - target.left + (rect.width() - target.width) / 2,
                                        rect.top - target.top + (rect.height() - target.height) / 2, ++generation))
                                    if (generation == 1L || generation % 90L == 0L) android.util.Log.w("MyHyperModifier", "Douyin own-layer backdrop frame=$generation: ${bitmap.width}x${bitmap.height}, ${bitmap.config}")
                                    schedule()
                                }
                            }
                                }
                    })
            } catch (error: Throwable) {
                main.post { if (!disposed && id == requestId) fail("${error.javaClass.simpleName}: ${error.cause ?: error.message}", true) }
            }
        }
    }
    private fun fail(reason: String, permanent: Boolean) {
        unsupported = permanent
        requestId++
        epoch++
        main.removeCallbacks(timeout)
        pending = false
        listener = null
        failed = true // Unsupported/denied capture must not retry every frame.
        clock.removeFrameCallback(this)
        scheduled = false
        android.util.Log.w("MyHyperModifier", "Douyin own-layer backdrop disabled: $reason")
    }
    fun dispose() {
        disposed = true
        active = false
        epoch++
        clock.removeFrameCallback(this)
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layout)
        observer = null
        surfaces.clear()
        source.get()?.removeOnAttachStateChangeListener(attachment)
        source.clear()
        listener = null
        main.removeCallbacks(timeout)
        watched.get()?.holder?.removeCallback(holderCallback)
        watched.clear()
        samplers.remove(this)
        thread?.quitSafely()
        worker = null; thread = null
        publish(null)
    }
    companion object {
        private val samplers = java.util.Collections.newSetFromMap(java.util.WeakHashMap<DouyinSurfaceBackdrop, Boolean>())
        @JvmStatic fun onVideoPageAttached(page: View) {
            val root = page.rootView
            for (sampler in samplers) {
                if (!sampler.disposed && sampler.source.get()?.rootView === root) sampler.sourceChanged("feed page attached")
            }
        }
    }
}
