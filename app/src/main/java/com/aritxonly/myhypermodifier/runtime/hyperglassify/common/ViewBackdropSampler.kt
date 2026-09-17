package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.view.WindowCompat
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarDefaults
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
internal fun ViewBackdropLayer(
    snapshot: ViewBackdropSnapshot?,
    backdrop: LayerBackdrop,
) {
    if (snapshot == null) return
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                IntOffset(snapshot.alignmentOffsetXPx, snapshot.alignmentOffsetYPx)
            }
            .graphicsLayer(alpha = BACKDROP_SOURCE_ALPHA)
            .requiredSize(
                width = (snapshot.sourceWidthPx / density.density).dp,
                height = (snapshot.sourceHeightPx / density.density).dp,
            )
            .layerBackdrop(backdrop),
    ) {
        Image(
            bitmap = snapshot.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal data class ViewBackdropBounds(val left: Int, val top: Int, val width: Int, val height: Int)

internal data class ViewBackdropSnapshot(
    val bitmap: Bitmap,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val alignmentOffsetXPx: Int,
    val alignmentOffsetYPx: Int,
    val generation: Long,
)

/** Downsamples an app-owned view root, optionally excluding the injected Compose overlay. */
internal class ViewBackdropSampler(
    private val source: View,
    private val excludedView: View? = null,
    private val revealMiHomeMaterialCardFallbacks: Boolean = false,
    private val pixelCopyWindow: Window? = null,
    private val usePixelCopySampling: () -> Boolean = { false },
    private val onSnapshotChanged: (ViewBackdropSnapshot?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var bounds: ViewBackdropBounds? = null
    private var captureScheduled = false
    private var lastCaptureAt = Long.MIN_VALUE
    private var keepCapturingUntil = Long.MIN_VALUE
    private var generation = 0L
    private val buffers = arrayOfNulls<Bitmap>(2)
    private var displayedBitmap: Bitmap? = null
    private var pendingPixelCopyBitmap: Bitmap? = null
    private var pixelCopyInFlight = false
    private var pixelCopyRefreshPending = false
    private var pixelCopyFallbackUntil = Long.MIN_VALUE
    private var samplingContextGeneration = 0L
    private var disposed = false
    private val hyperCardClassCache = mutableMapOf<Class<*>, Boolean>()
    private val miHomeMaterialCardClassCache = mutableMapOf<Class<*>, Boolean>()
    private val miHomeMaterialPaintCache = mutableMapOf<Class<*>, MiHomeMaterialPaintAccessor?>()
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        if (usePixelCopySampling()) {
            keepCapturingUntil = maxOf(
                keepCapturingUntil,
                SystemClock.uptimeMillis() + PIXEL_COPY_SCROLL_SETTLE_MS,
            )
        }
        requestCapture()
    }
    private val captureRunnable = Runnable {
        captureScheduled = false
        capture()
    }

    init {
        if (source.viewTreeObserver.isAlive) {
            source.viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
        }
    }

    fun setNavigationBounds(value: ViewBackdropBounds) {
        if (bounds == value) return
        bounds = value
        requestCaptureBurst(INITIAL_CAPTURE_BURST_MS)
    }

    fun onTouchEvent(event: MotionEvent) {
        val now = SystemClock.uptimeMillis()
        keepCapturingUntil = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> now + ACTIVE_CAPTURE_GRACE_MS
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> now + FLING_CAPTURE_MS
            else -> keepCapturingUntil
        }
        requestCapture()
    }

    fun onFrame() {
        if (SystemClock.uptimeMillis() < keepCapturingUntil) requestCapture()
    }

    fun requestCapture() {
        if (disposed || bounds == null || !source.isAttachedToWindow) return
        if (pixelCopyInFlight) {
            // PixelCopy cannot be cancelled. Remember that the visible content advanced while
            // this request was in flight, then capture the latest frame as soon as it completes.
            pixelCopyRefreshPending = true
            return
        }
        if (captureScheduled) return
        val elapsed = if (lastCaptureAt == Long.MIN_VALUE) Long.MAX_VALUE
        else SystemClock.uptimeMillis() - lastCaptureAt
        val captureInterval = if (usePixelCopySampling()) {
            PIXEL_COPY_CAPTURE_INTERVAL_MS
        } else {
            CAPTURE_INTERVAL_MS
        }
        captureScheduled = true
        handler.postDelayed(captureRunnable, (captureInterval - elapsed).coerceAtLeast(0L))
    }

    fun requestCaptureBurst(durationMs: Long = PAGE_CHANGE_CAPTURE_BURST_MS) {
        keepCapturingUntil = maxOf(
            keepCapturingUntil,
            SystemClock.uptimeMillis() + durationMs.coerceAtLeast(0L),
        )
        requestCapture()
    }

    fun invalidateSamplingContext() {
        samplingContextGeneration += 1
        requestCapture()
    }

    fun dispose() {
        disposed = true
        handler.removeCallbacks(captureRunnable)
        captureScheduled = false
        if (source.viewTreeObserver.isAlive) {
            source.viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
        }
        displayedBitmap = null
        buffers.forEach { bitmap ->
            if (bitmap !== pendingPixelCopyBitmap) bitmap?.recycle()
        }
        onSnapshotChanged(null)
    }

    private fun capture() {
        val target = bounds ?: return
        if (!source.isAttachedToWindow || source.width <= 0 || source.height <= 0) {
            retryInitialCapture()
            return
        }
        val sourceLocation = IntArray(2)
        source.getLocationInWindow(sourceLocation)
        val bleed = ceil(source.resources.displayMetrics.density * SAMPLE_BLEED_DP).toInt()
        val sourceRect = Rect(
            target.left - sourceLocation[0] - bleed,
            target.top - sourceLocation[1] - bleed,
            target.left - sourceLocation[0] + target.width + bleed,
            target.top - sourceLocation[1] + target.height + bleed,
        )
        if (!sourceRect.intersect(0, 0, source.width, source.height) || sourceRect.isEmpty) {
            retryInitialCapture()
            return
        }
        val targetLeftInSource = target.left - sourceLocation[0]
        val targetTopInSource = target.top - sourceLocation[1]
        val targetOffsetX = targetLeftInSource - sourceRect.left
        val targetOffsetY = targetTopInSource - sourceRect.top
        val alignmentOffsetX = (
            (sourceRect.width() - target.width) / 2f - targetOffsetX
            ).roundToInt()
        val alignmentOffsetY = (
            (sourceRect.height() - target.height) / 2f - targetOffsetY
            ).roundToInt()

        val now = SystemClock.uptimeMillis()
        if (pixelCopyWindow != null && usePixelCopySampling() &&
            now >= pixelCopyFallbackUntil &&
            requestPixelCopy(
                window = pixelCopyWindow,
                sourceRect = sourceRect,
                sourceLocation = sourceLocation,
                sampleWidth = sourceRect.width(),
                sampleHeight = sourceRect.height(),
                alignmentOffsetXPx = alignmentOffsetX,
                alignmentOffsetYPx = alignmentOffsetY,
            )
        ) return

        val bitmap = obtainBuffer(
            max(1, (sourceRect.width() * SAMPLE_SCALE).roundToInt()),
            max(1, (sourceRect.height() * SAMPLE_SCALE).roundToInt()),
        )
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.scale(SAMPLE_SCALE, SAMPLE_SCALE)
        canvas.translate(-sourceRect.left.toFloat(), -sourceRect.top.toFloat())
        val excludedAlpha = excludedView?.alpha
        val revealedFallbacks = if (revealMiHomeMaterialCardFallbacks) {
            revealHyperCardFallbacks(sourceRect, sourceLocation)
        } else {
            emptyList()
        }
        val revealedMiHomeCards = if (revealMiHomeMaterialCardFallbacks) {
            revealMiHomeMaterialCardFallbacks(sourceRect, sourceLocation)
        } else {
            emptyList()
        }
        try {
            // Mi Home composes part of its home background outside android.R.id.content.
            // Its sampler therefore draws DecorView, where our injected ComposeView is also
            // attached. Hide only that view for this synchronous off-screen draw so the
            // backdrop includes the app's native layers without recursively sampling itself.
            if (excludedAlpha != null) excludedView.alpha = 0f
            source.draw(canvas)
        } catch (_: Throwable) {
            retryInitialCapture()
            return
        } finally {
            revealedMiHomeCards.forEach { state -> state.paint.alpha = state.alpha }
            revealedFallbacks.forEach { state -> state.drawable.alpha = state.alpha }
            if (excludedAlpha != null) excludedView.alpha = excludedAlpha
        }

        publishSnapshot(
            bitmap = bitmap,
            sourceWidthPx = sourceRect.width(),
            sourceHeightPx = sourceRect.height(),
            alignmentOffsetXPx = alignmentOffsetX,
            alignmentOffsetYPx = alignmentOffsetY,
        )
    }

    private fun requestPixelCopy(
        window: Window,
        sourceRect: Rect,
        sourceLocation: IntArray,
        sampleWidth: Int,
        sampleHeight: Int,
        alignmentOffsetXPx: Int,
        alignmentOffsetYPx: Int,
    ): Boolean {
        val windowRoot = window.decorView
        if (windowRoot.width <= 0 || windowRoot.height <= 0) return false
        val requestRect = Rect(sourceRect).apply {
            offset(sourceLocation[0], sourceLocation[1])
        }
        if (!requestRect.intersect(0, 0, windowRoot.width, windowRoot.height) ||
            requestRect.width() != sampleWidth || requestRect.height() != sampleHeight
        ) return false

        val bitmap = obtainBuffer(
            max(1, (sampleWidth * PIXEL_COPY_SAMPLE_SCALE).roundToInt()),
            max(1, (sampleHeight * PIXEL_COPY_SAMPLE_SCALE).roundToInt()),
        )
        val requestGeneration = samplingContextGeneration
        pixelCopyRefreshPending = false
        pixelCopyInFlight = true
        pendingPixelCopyBitmap = bitmap
        lastCaptureAt = SystemClock.uptimeMillis()
        return runCatching {
            PixelCopy.request(window, requestRect, bitmap, { result ->
                pixelCopyInFlight = false
                pendingPixelCopyBitmap = null
                val refreshLatestFrame = pixelCopyRefreshPending
                pixelCopyRefreshPending = false
                if (disposed) {
                    bitmap.recycle()
                    return@request
                }
                if (requestGeneration != samplingContextGeneration || !usePixelCopySampling()) {
                    requestCapture()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    publishSnapshot(
                        bitmap = bitmap,
                        sourceWidthPx = sampleWidth,
                        sourceHeightPx = sampleHeight,
                        alignmentOffsetXPx = alignmentOffsetXPx,
                        alignmentOffsetYPx = alignmentOffsetYPx,
                    )
                    if (refreshLatestFrame) requestCapture()
                } else {
                    // Fall back briefly to View.draw() so a transient compositor failure does not
                    // leave the glass empty. The next burst retries hardware sampling.
                    pixelCopyFallbackUntil = SystemClock.uptimeMillis() +
                        PIXEL_COPY_RETRY_DELAY_MS
                    requestCapture()
                }
            }, handler)
        }.onFailure {
            pixelCopyInFlight = false
            pendingPixelCopyBitmap = null
            pixelCopyRefreshPending = false
            pixelCopyFallbackUntil = SystemClock.uptimeMillis() + PIXEL_COPY_RETRY_DELAY_MS
        }.isSuccess
    }

    private fun publishSnapshot(
        bitmap: Bitmap,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        alignmentOffsetXPx: Int,
        alignmentOffsetYPx: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        if (generation == 0L) {
            // LayerBackdrop registers its producer and consumer on adjacent Compose frames.
            // A short startup burst guarantees a second sample without waiting for user input.
            keepCapturingUntil = maxOf(keepCapturingUntil, now + INITIAL_CAPTURE_BURST_MS)
        }
        lastCaptureAt = now
        displayedBitmap = bitmap
        generation += 1
        onSnapshotChanged(
            ViewBackdropSnapshot(
                bitmap = bitmap,
                sourceWidthPx = sourceWidthPx,
                sourceHeightPx = sourceHeightPx,
                alignmentOffsetXPx = alignmentOffsetXPx,
                alignmentOffsetYPx = alignmentOffsetYPx,
                generation = generation,
            ),
        )
        if (now < keepCapturingUntil) requestCapture()
    }

    private fun retryInitialCapture() {
        if (generation != 0L || captureScheduled) return
        captureScheduled = true
        handler.postDelayed(captureRunnable, CAPTURE_INTERVAL_MS)
    }

    private fun revealHyperCardFallbacks(
        sourceRect: Rect,
        sourceLocation: IntArray,
    ): List<DrawableAlphaState> = buildList {
        val location = IntArray(2)
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE || view.alpha <= 0f) return
            if (isMiuiHyperCard(view) && view.width > 0 && view.height > 0) {
                view.getLocationInWindow(location)
                val left = location[0] - sourceLocation[0]
                val top = location[1] - sourceLocation[1]
                if (left < sourceRect.right && left + view.width > sourceRect.left &&
                    top < sourceRect.bottom && top + view.height > sourceRect.top
                ) {
                    view.background?.takeIf { it.alpha < 255 }?.let { drawable ->
                        add(DrawableAlphaState(drawable, drawable.alpha))
                        drawable.alpha = 255
                    }
                }
            }
            (view as? ViewGroup)?.let { parent ->
                repeat(parent.childCount) { index -> visit(parent.getChildAt(index)) }
            }
        }
        visit(source)
    }

    private fun isMiuiHyperCard(view: View): Boolean = hyperCardClassCache.getOrPut(view.javaClass) {
        generateSequence(view.javaClass as Class<*>?) { it.superclass }
            .any { it.name == MIUI_HYPER_CARD_VIEW_CLASS }
    }

    /**
     * Mi Home's home cards use a hardware-only background blur. During View.draw(Canvas), their
     * SketchSmoothDelegate contributes only a very translucent fallback paint, so the sampler
     * sees the wallpaper through an otherwise white card. Strengthen that existing paint only for
     * the synchronous off-screen draw; children are still drawn normally and the paint is restored
     * before the next real frame.
     */
    private fun revealMiHomeMaterialCardFallbacks(
        sourceRect: Rect,
        sourceLocation: IntArray,
    ): List<PaintAlphaState> = buildList {
        val location = IntArray(2)
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE || view.alpha <= 0f) return
            if (isMiHomeMaterialCard(view) && view.width > 0 && view.height > 0) {
                view.getLocationInWindow(location)
                val left = location[0] - sourceLocation[0]
                val top = location[1] - sourceLocation[1]
                if (left < sourceRect.right && left + view.width > sourceRect.left &&
                    top < sourceRect.bottom && top + view.height > sourceRect.top
                ) {
                    findMiHomeMaterialPaint(view)?.let { paint ->
                        val originalAlpha = paint.alpha
                        if (originalAlpha in 1 until MI_HOME_LIGHT_CARD_SAMPLE_ALPHA) {
                            val color = paint.color
                            val brightness = (
                                AndroidColor.red(color) * 299 +
                                    AndroidColor.green(color) * 587 +
                                    AndroidColor.blue(color) * 114
                                ) / 1000
                            val sampleAlpha = if (brightness >= 128) {
                                MI_HOME_LIGHT_CARD_SAMPLE_ALPHA
                            } else {
                                MI_HOME_DARK_CARD_SAMPLE_ALPHA
                            }
                            add(PaintAlphaState(paint, originalAlpha))
                            paint.alpha = sampleAlpha
                        }
                    }
                }
            }
            (view as? ViewGroup)?.let { parent ->
                repeat(parent.childCount) { index -> visit(parent.getChildAt(index)) }
            }
        }
        visit(source)
    }

    private fun isMiHomeMaterialCard(view: View): Boolean =
        miHomeMaterialCardClassCache.getOrPut(view.javaClass) {
            generateSequence(view.javaClass as Class<*>?) { it.superclass }
                .any { it.name in MI_HOME_MATERIAL_CARD_CLASSES }
        }

    private fun findMiHomeMaterialPaint(view: View): Paint? {
        val accessor = miHomeMaterialPaintCache.getOrPut(view.javaClass) {
            runCatching {
                val delegateField = generateSequence(view.javaClass as Class<*>?) { it.superclass }
                    .flatMap { it.declaredFields.asSequence() }
                    .first { it.type.name == MI_HOME_SKETCH_DELEGATE_CLASS }
                    .apply { isAccessible = true }
                val paintField = delegateField.type.declaredFields
                    .first { Paint::class.java.isAssignableFrom(it.type) }
                    .apply { isAccessible = true }
                MiHomeMaterialPaintAccessor(delegateField, paintField)
            }.getOrNull()
        } ?: return null
        return runCatching {
            accessor.paintField.get(accessor.delegateField.get(view)) as? Paint
        }.getOrNull()
    }

    private fun obtainBuffer(width: Int, height: Int): Bitmap {
        buffers.forEach { bitmap ->
            if (bitmap !== displayedBitmap && bitmap !== pendingPixelCopyBitmap &&
                bitmap != null && !bitmap.isRecycled &&
                bitmap.width == width && bitmap.height == height
            ) return bitmap
        }
        val index = buffers.indexOfFirst {
            it !== displayedBitmap && it !== pendingPixelCopyBitmap
        }.coerceAtLeast(0)
        buffers[index]?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { buffers[index] = it }
    }

    private companion object {
        const val MIUI_HYPER_CARD_VIEW_CLASS = "miuix.cardview.HyperCardView"
        const val MI_HOME_SKETCH_DELEGATE_CLASS =
            "com.xiaomi.smarthome.smoothcard.SketchSmoothDelegate"
        val MI_HOME_MATERIAL_CARD_CLASSES = setOf(
            "com.xiaomi.smarthome.newui.widget.topnavi.widgets.SketchCardView",
            "com.xiaomi.smarthome.newui.widget.topnavi.widgets.CardConstraintLayout",
            "com.xiaomi.smarthome.newui.widget.CardLinearLayout",
        )
        const val MI_HOME_LIGHT_CARD_SAMPLE_ALPHA = 235
        const val MI_HOME_DARK_CARD_SAMPLE_ALPHA = 205
        const val SAMPLE_BLEED_DP = 32f
        const val SAMPLE_SCALE = 0.18f
        const val PIXEL_COPY_SAMPLE_SCALE = 0.14f
        const val PIXEL_COPY_CAPTURE_INTERVAL_MS = 16L
        const val PIXEL_COPY_SCROLL_SETTLE_MS = 240L
        const val PIXEL_COPY_RETRY_DELAY_MS = 1_000L
        const val CAPTURE_INTERVAL_MS = 16L
        const val INITIAL_CAPTURE_BURST_MS = 350L
        const val PAGE_CHANGE_CAPTURE_BURST_MS = 500L
        const val ACTIVE_CAPTURE_GRACE_MS = 100L
        // Actual fling movement is followed by OnScrollChangedListener; this is only a short
        // fallback for custom containers that dispatch their scroll callback one frame late.
        const val FLING_CAPTURE_MS = 160L
    }

    private data class DrawableAlphaState(val drawable: Drawable, val alpha: Int)
    private data class PaintAlphaState(val paint: Paint, val alpha: Int)
    private data class MiHomeMaterialPaintAccessor(
        val delegateField: java.lang.reflect.Field,
        val paintField: java.lang.reflect.Field,
    )
}


private const val BACKDROP_SOURCE_ALPHA = 0.001f
