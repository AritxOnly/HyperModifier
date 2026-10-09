package com.aritxonly.myhypermodifier

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.view.View
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.nio.ByteBuffer

/** Hardware canvas is necessary for host Compose display lists and hardware images. */
internal class HardwareViewBackdropCapture : AutoCloseable {
    private var reader: ImageReader? = null
    private var renderer: HardwareRenderer? = null
    private val node = RenderNode("Injected hardware backdrop")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val readThread = HandlerThread("Spotify backdrop readback").apply { start() }
    private val readHandler = Handler(readThread.looper)
    private val readerLock = Any()
    private var pending: ((Result<Bitmap>) -> Unit)? = null
    private var pixels: ByteBuffer? = null

    fun capture(source: View, region: Rect, scale: Float, result: (Result<Bitmap>) -> Unit) {
        check(pending == null) { "Backdrop capture already in flight" }
        val width = (region.width() * scale).toInt().coerceAtLeast(1)
        val height = (region.height() * scale).toInt().coerceAtLeast(1)
        if (reader?.width != width || reader?.height != height) {
            releaseTarget()
            val target = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN)
            reader = target
            pixels = ByteBuffer.allocateDirect(width * height * 4)
            target.setOnImageAvailableListener({ available ->
                val bitmap = synchronized(readerLock) { runCatching { readBitmap(available) } }
                mainHandler.post {
                    val callback = pending
                    pending = null
                    callback?.invoke(bitmap)
                }
            }, readHandler)
            renderer = HardwareRenderer().apply {
                setOpaque(true)
                setSurface(target.surface)
                setContentRoot(node)
            }
        }
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            // A transparent fragment alone leaves holes in the blurred image:
            // the original text underneath then shows through those holes.
            // Include the window substrate before drawing the fragment layers.
            canvas.drawColor(Color.BLACK)
            canvas.scale(width.toFloat() / region.width(), height.toFloat() / region.height())
            canvas.translate(-region.left.toFloat(), -region.top.toFloat())
            val root = source.rootView
            val sourceLocation = IntArray(2)
            val rootLocation = IntArray(2)
            source.getLocationInWindow(sourceLocation)
            root.getLocationInWindow(rootLocation)
            val saved = canvas.save()
            canvas.translate((rootLocation[0] - sourceLocation[0]).toFloat(),
                (rootLocation[1] - sourceLocation[1]).toFloat())
            root.background?.draw(canvas)
            canvas.restoreToCount(saved)
            source.draw(canvas)
        } finally { node.endRecording() }
        pending = result
        try {
            // ImageReader signals completion; don't wait for GPU presentation on the UI thread.
            val status = checkNotNull(renderer).createRenderRequest()
                .setWaitForPresent(false).syncAndDraw()
            val failed = HardwareRenderer.SYNC_FRAME_DROPPED or
                HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or
                HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND
            check(status and failed == 0) { "Backdrop renderer did not submit frame: $status" }
        } catch (error: Exception) {
            pending = null
            result(Result.failure(error))
        }
    }

    private fun readBitmap(target: ImageReader): Bitmap {
        val width = target.width
        val height = target.height
        val image = checkNotNull(target.acquireLatestImage()) { "No rendered backdrop image" }
        try {
            val plane = image.planes[0]
            check(plane.pixelStride == 4) { "Unsupported backdrop pixel stride" }
            val pixels = checkNotNull(pixels).apply { clear() }
            val input = plane.buffer.duplicate()
            repeat(height) { row ->
                val rowBytes = input.duplicate()
                rowBytes.position(row * plane.rowStride)
                rowBytes.limit(row * plane.rowStride + width * 4)
                pixels.put(rowBytes)
            }
            pixels.rewind()
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(pixels)
            }
        } finally { image.close() }
    }

    private fun releaseTarget() {
        renderer?.destroy()
        renderer = null
        synchronized(readerLock) {
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
            reader = null
            pixels = null
        }
        node.discardDisplayList()
    }

    override fun close() {
        pending = null
        releaseTarget()
        readThread.quitSafely()
    }
}
