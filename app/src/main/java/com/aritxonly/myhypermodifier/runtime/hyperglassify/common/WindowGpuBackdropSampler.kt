package com.aritxonly.myhypermodifier

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import java.lang.ref.WeakReference
import android.os.Process
import android.os.SystemClock
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.view.Window
import android.view.ViewTreeObserver
import kotlin.math.ceil

/** App-owned window + Surface children -> cropped hardware bitmap. No View.draw or CPU readback. */
internal class WindowGpuBackdropSampler(
    private val source: View,
    private val excludedView: View,
    private val window: Window,
    private val minimumCaptureIntervalMs: Long = 0L,
    private val continuousSampling: Boolean = false,
    private val publish: (ViewBackdropSnapshot?) -> Unit,
) : NavigationGlassObserver {
    private val self = WeakReference(this)
    private var controlWaits = 0
    private val gate = GpuBackdropFrameGate()
    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    @Volatile private var disposed = false
    @Volatile private var listener: Any? = null
    private var active = false
    @Volatile private var failed = false
    private var scheduled = false
    private var commitPending = false
    private var burstUntil = 0L
    private var lastCaptureAt = Long.MIN_VALUE
    private var bounds: ViewBackdropBounds? = null
    private var generation = 0L
    private var sourceControl: SurfaceControl? = null
    private var excludedControl: SurfaceControl? = null
    private var observer: ViewTreeObserver? = null
    private var legacy: ViewBackdropSampler? = null
    private var bleedPx = NavigationGlassRuntime.paddingPx(source.resources.displayMetrics.density, 48f)
    private val location = IntArray(2)
    private val crop = Rect()
    private val timeout = Runnable { if (!disposed && !failed) fallback("GPU callback timeout") }
    private val captureTask = Runnable {
        scheduled = false
        capture()
    }
    private val committed = Runnable {
        commitPending = false
        if (active && !failed && !disposed) { gate.request(); schedule() }
    }
    private val draw = ViewTreeObserver.OnDrawListener {
        // Wait for this native frame's submission, not just View invalidation / a stale RenderNode.
        if (active && !failed && !disposed && !commitPending) {
            commitPending = true
            observer?.registerFrameCommitCallback(committed)
        }
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) { observe(); invalidateSamplingContext() }
        override fun onViewDetachedFromWindow(view: View) {
            observer?.takeIf { it.isAlive }?.let {
                it.removeOnDrawListener(draw)
                it.unregisterFrameCommitCallback(committed)
            }
            commitPending = false
            observer = null
            setActive(false)
        }
    }
    init {
        NavigationGlassRuntime.observe(this)
        source.addOnAttachStateChangeListener(attachment)
        if (source.isAttachedToWindow) observe()
    }
    private fun observe() {
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnDrawListener(draw)
            it.unregisterFrameCommitCallback(committed)
        }
        commitPending = false
        observer = source.viewTreeObserver.takeIf { it.isAlive }?.also { it.addOnDrawListener(draw) }
    }
    fun setActive(value: Boolean) {
        if (disposed || active == value) return
        active = value
        gate.setActive(value)
        legacy?.setActive(value)
        if (!value) {
            main.removeCallbacks(captureTask); scheduled = false
            observer?.takeIf { it.isAlive }?.unregisterFrameCommitCallback(committed)
            commitPending = false; burstUntil = 0L
            publish(null)
        } else {
            // Refresh borrowed controls after Activity/surface recreation, never cache a ViewRoot.
            sourceControl = null; excludedControl = null; controlWaits = 0
            schedule()
        }
    }
    fun setNavigationBounds(value: ViewBackdropBounds) {
        if (bounds == value || disposed) return
        bounds = value
        legacy?.setNavigationBounds(value)
        invalidateSamplingContext()
    }
    fun invalidateSamplingContext() {
        if (disposed) return
        gate.invalidate()
        legacy?.invalidateSamplingContext()
        publish(null)
        schedule()
    }
    fun requestCaptureBurst(durationMs: Long = 240L) {
        if (!active || disposed) return
        // SurfaceView buffers (maps/video) can update without any ViewRoot draw. Follow a finite
        // native transition/gesture burst, never run an idle polling loop.
        if (!failed) burstUntil = maxOf(burstUntil, SystemClock.uptimeMillis() + durationMs.coerceIn(0L, 1200L))
        requestCapture()
    }
    fun requestCapture() {
        if (!active || disposed) return
        legacy?.let { it.requestCapture(); return }
        gate.request(); schedule()
    }
    fun onTouchEvent(event: MotionEvent) {
        legacy?.onTouchEvent(event)
        if (!active || disposed || failed) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> requestCaptureBurst(240L)
            MotionEvent.ACTION_UP -> requestCaptureBurst(1000L)
            MotionEvent.ACTION_CANCEL -> requestCaptureBurst(120L)
        }
    }
    fun onFrame() { legacy?.onFrame() }
    private fun schedule() {
        if (disposed || failed || scheduled || bounds == null || !gate.ready()) return
        scheduled = true
        // Runs after traversal, not another postOnAnimation frame later.
        val interval = maxOf(
            16L,
            minimumCaptureIntervalMs,
            BackdropCaptureCadence.intervalMs(source.display?.refreshRate ?: 60f),
        )
        val delay = if (lastCaptureAt == Long.MIN_VALUE) 0L else
            (interval - (SystemClock.uptimeMillis() - lastCaptureAt)).coerceAtLeast(0L)
        main.postDelayed(captureTask, delay)
    }
    private fun capture() {
        if (disposed || failed || !active || !gate.ready()) return
        val target = bounds ?: return
        if (!source.isAttachedToWindow || !excludedView.isAttachedToWindow || source.width <= 0) {
            waitForWindow()
            return
        }
        try {
            val app = sourceControl?.takeIf { it.isValid } ?: control(source).also { sourceControl = it }
            val overlay = excludedControl?.takeIf { it.isValid }
                ?: control(excludedView).also { excludedControl = it }
            if (app == null || overlay == null || !app.isValid || !overlay.isValid) {
                waitForWindow()
                return
            }
            check(source.rootView !== excludedView.rootView && app !== overlay) { "Not independent windows" }
            controlWaits = 0
            source.getLocationInWindow(location)
            val bleed = bleedPx
            crop.set(target.left - location[0] - bleed, target.top - location[1] - bleed,
                target.left - location[0] + target.width + bleed,
                target.top - location[1] + target.height + bleed)
            if (!crop.intersect(0, 0, source.width, source.height) || crop.isEmpty) return
            val region = Rect(crop)
            val alignmentX = location[0] + region.left - target.left + (region.width() - target.width) / 2
            val alignmentY = location[1] + region.top - target.top + (region.height() - target.height) / 2
            val id = gate.begin()
            if (id == 0L) return
            val handler = worker ?: HandlerThread("MHM-WindowGpuBackdrop", Process.THREAD_PRIORITY_BACKGROUND).let {
                it.start(); thread = it; Handler(it.looper).also { worker = it }
            }
            lastCaptureAt = SystemClock.uptimeMillis()
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, 750L)
            handler.post {
                if (disposed) return@post
                try {
                    val sink = NativeLayerCapture.captureWindow(app, region,
                        minOf(1f, 384f / region.width()), overlay,
                        CaptureCallback(self, id, region.width(), region.height(), alignmentX, alignmentY))
                    // The sink must stay alive until the asynchronous native callback arrives.
                    main.post { if (!disposed && !failed && gate.owns(id)) listener = sink }
                } catch (error: Throwable) {
                    main.post { if (!disposed && gate.owns(id)) fallback(error.javaClass.simpleName) }
                }
            }
        } catch (error: Throwable) { fallback(error.javaClass.simpleName) }
    }
    private fun waitForWindow() {
        // A panel can attach after the app's only startup frame. Bound readiness retries so that
        // neither a missing attach/draw event nor an unsupported surface leaves a frozen dock.
        if (++controlWaits <= 3) { scheduled = true; main.postDelayed(captureTask, 32L) }
        else fallback("Window surface not ready")
    }
    private class CaptureCallback(
        private val owner: WeakReference<WindowGpuBackdropSampler>,
        private val token: Long, private val width: Int, private val height: Int,
        private val alignmentX: Int, private val alignmentY: Int,
    ) : NativeLayerCapture.Callback {
        override fun onResult(bitmap: Bitmap?, status: Int) {
            val host = owner.get()
            if (host == null) bitmap?.recycle()
            else host.deliver(token, width, height, alignmentX, alignmentY, bitmap, status)
        }
    }
    private fun deliver(id: Long, width: Int, height: Int, x: Int, y: Int, bitmap: Bitmap?, status: Int) {
        main.post result@{
            if (!gate.owns(id)) { bitmap?.recycle(); return@result }
            main.removeCallbacks(timeout)
            listener = null
            val valid = gate.complete(id)
            if (disposed || failed || !valid) { bitmap?.recycle(); schedule(); return@result }
            if (bitmap == null || status != 0 || bitmap.config != Bitmap.Config.HARDWARE) {
                bitmap?.recycle(); fallback("GPU status=$status"); return@result
            }
            // Display-list users may retain published buffers. Recycle only unpublished results.
            publish(ViewBackdropSnapshot(bitmap, width, height, x, y, ++generation))
            if (generation == 1L) android.util.Log.i("MyHyperModifier",
                "Window GPU backdrop active: ${bitmap.width}x${bitmap.height}, HARDWARE")
            if (continuousSampling || SystemClock.uptimeMillis() < burstUntil) gate.request()
            schedule()
        }
    }
    private fun fallback(reason: String) {
        if (disposed || failed) return
        failed = true
        gate.setActive(false); gate.cancel()
        main.removeCallbacks(captureTask); scheduled = false
        main.removeCallbacks(timeout)
        listener = null
        thread?.quitSafely(); thread = null; worker = null
        publish(null)
        android.util.Log.w("MyHyperModifier", "Window GPU backdrop unavailable; using legacy PixelCopy: $reason")
        legacy = ViewBackdropSampler(source = source, excludedView = excludedView,
            pixelCopyWindow = window, usePixelCopySampling = { active },
            allowSoftwareFallback = false, matchDisplayRefreshRate = true,
            sampleOnSourceFrame = true, pixelCopyRetryDelayMs = 32L, onSnapshotChanged = publish)
            .also { sampler ->
                sampler.setActive(active)
                bounds?.let(sampler::setNavigationBounds)
            }
    }
    override fun onNavigationGlassChanged() {
        val padding = NavigationGlassRuntime.paddingPx(source.resources.displayMetrics.density, 48f)
        if (padding == bleedPx || disposed) return
        bleedPx = padding
        invalidateSamplingContext()
        requestCaptureBurst()
    }
    fun dispose() {
        if (disposed) return
        NavigationGlassRuntime.remove(this)
        disposed = true; active = false; gate.setActive(false); gate.cancel()
        main.removeCallbacks(captureTask); main.removeCallbacks(timeout)
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnDrawListener(draw)
            it.unregisterFrameCommitCallback(committed)
        }
        commitPending = false
        source.removeOnAttachStateChangeListener(attachment)
        observer = null; listener = null
        legacy?.dispose(); legacy = null
        thread?.quitSafely(); thread = null; worker = null
        sourceControl = null; excludedControl = null
        publish(null)
    }
    // Deliberate Xposed-only access to the host's own ViewRoot. Target app / ROM hidden-API
    // policy can reject it: capture() catches that and selects PixelCopy once for this host.
    // Never alter VM-wide exemptions or attempt capture in the module's ordinary app UI.
    @SuppressLint("BlockedPrivateApi")
    companion object {
        private val rootMethod by lazy { View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true } }
        private val surfaceMethod by lazy { Class.forName("android.view.ViewRootImpl").getDeclaredMethod("getSurfaceControl").apply { isAccessible = true } }
        private fun control(view: View): SurfaceControl? =
            rootMethod.invoke(view)?.let { surfaceMethod.invoke(it) as? SurfaceControl }
    }
}
