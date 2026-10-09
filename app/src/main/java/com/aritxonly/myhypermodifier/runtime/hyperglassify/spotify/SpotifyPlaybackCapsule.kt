package com.aritxonly.myhypermodifier

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.os.Build
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import com.aritxonly.deadliner.model.AdvancedMaterialFineTuning
import com.aritxonly.deadliner.ui.material.glass.DeadlinerGlassRecipes
import com.aritxonly.deadliner.ui.material.glass.GlassRefractionShader
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import kotlin.math.roundToInt
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Decorates the actual player, without moving it out of Spotify's AndroidView holder. */
internal class SpotifyPlaybackCapsule(private val activity: Activity) {
    private var player: View? = null
    private var nativeBackground: Drawable? = null
    private var nativeOutline: ViewOutlineProvider? = null
    private var nativeClip = false
    private var nativeHeight = 0
    private var nativeForeground: Drawable? = null
    private var nativePadding = intArrayOf(0, 0, 0, 0)
    private var insetLeft = 0
    private var insetRight = 0
    private var coverContainer: View? = null
    private var nativeCoverContainerTranslationX = 0f
    var backdropBounds: ViewBackdropBounds? = null
        private set
    private var glass: SpotifyCapsuleDrawable? = null
    private val location = IntArray(2)
    private val capsuleOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(insetLeft, 0, view.width - insetRight, view.height, view.height / 2f)
        }
    }

    fun sync(dockVisible: Boolean, tabBounds: ViewBackdropBounds?) {
        val current = activity.findSpotifyView("now_playing_bar_layout")
        if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.spotifyFloatingNavigationEnabled) {
            dispose()
            return
        }
        if (!dockVisible || current == null || !current.isShown || current.alpha == 0f) {
            backdropBounds = null
            return
        }
        if (current !== player) {
            dispose()
            // Sample the content below the dock, never the cover/text/player itself.
            player = current
            nativeBackground = current.background
            nativeOutline = current.outlineProvider
            nativeClip = current.clipToOutline
            nativeHeight = current.layoutParams.height
            nativeForeground = current.foreground
            nativePadding = intArrayOf(current.paddingLeft, current.paddingTop,
                current.paddingRight, current.paddingBottom)
            current.foreground = null
            val drawable = SpotifyCapsuleDrawable(current.resources.displayMetrics.density)
            glass = drawable
            synchronized(activePlayers) { activePlayers[current] = WeakReference(this) }
            android.util.Log.i("MyHyperModifier", "Spotify native playback capsule attached")
        }
        // Spotify may assign a new track-color background after a song change.
        // Remember that latest native background so disabling restores current state.
        if (current.background !== glass) {
            nativeBackground = current.background
            current.background = glass
        }
        if (current.outlineProvider !== capsuleOutline) current.outlineProvider = capsuleOutline
        if (!current.clipToOutline) current.clipToOutline = true
        val height = if (activity.resources.configuration.fontScale <= 1.15f) {
            (50f * current.resources.displayMetrics.density).roundToInt()
        } else nativeHeight
        if (height > 0 && current.layoutParams.height != height) {
            current.layoutParams = current.layoutParams.apply { this.height = height }
        }
        current.getLocationInWindow(location)
        if (tabBounds != null && current.width > 0) {
            val left = (tabBounds.left - location[0]).coerceIn(0, current.width - 1)
            val right = (current.width - left - tabBounds.width).coerceAtLeast(0)
            if (left != insetLeft || right != insetRight) {
                insetLeft = left
                insetRight = right
                glass?.setHorizontalInsets(left, right)
                current.setPadding(nativePadding[0] + left, nativePadding[1],
                    nativePadding[2] + right, nativePadding[3])
                current.invalidateOutline()
            }
        }
        if (current.width > 0 && current.height > 0) {
            backdropBounds = ViewBackdropBounds(
                location[0], location[1], current.width, current.height,
            )
        }
        val coverId = current.resources.getIdentifier("cover_image", "id", activity.packageName)
        val currentCover = if (coverId != 0) current.findViewById<View>(coverId) else null
        // The image fills a clipped MotionLayout. Move its whole cover region,
        // otherwise translating the image alone cuts off its right edge.
        var offsetTarget = currentCover
        while (offsetTarget != null && offsetTarget.parent !== current) {
            offsetTarget = offsetTarget.parent as? View
        }
        if (offsetTarget !== coverContainer) {
            coverContainer?.translationX = nativeCoverContainerTranslationX
            coverContainer = offsetTarget
            nativeCoverContainerTranslationX = offsetTarget?.translationX ?: 0f
        }
        offsetTarget?.let { container ->
            val offset = nativeCoverContainerTranslationX +
                4f * container.resources.displayMetrics.density
            if (container.translationX != offset) container.translationX = offset
        }
    }

    fun updateBackdrop(snapshot: ViewBackdropSnapshot?) { glass?.update(snapshot) }

    fun dispose() {
        coverContainer?.translationX = nativeCoverContainerTranslationX
        coverContainer = null
        backdropBounds = null
        player?.let { view ->
            synchronized(activePlayers) { activePlayers.remove(view) }
            if (view.background === glass) view.background = nativeBackground
            view.outlineProvider = nativeOutline
            view.clipToOutline = nativeClip
            view.foreground = nativeForeground
            view.setPadding(nativePadding[0], nativePadding[1], nativePadding[2], nativePadding[3])
            if (view.layoutParams.height != nativeHeight) {
                view.layoutParams = view.layoutParams.apply { height = nativeHeight }
            }
        }
        player = null
        glass = null
        nativeBackground = null
        insetLeft = 0
        insetRight = 0
    }

    companion object {
        private val activePlayers = WeakHashMap<View, WeakReference<SpotifyPlaybackCapsule>>()

        @JvmStatic fun backgroundFor(view: View, proposed: Drawable?): Drawable? {
            val host = synchronized(activePlayers) { activePlayers[view]?.get() } ?: return proposed
            if (proposed !== host.glass) host.nativeBackground = proposed
            return host.glass ?: proposed
        }

        @JvmStatic fun foregroundFor(view: View, proposed: Drawable?): Drawable? {
            val host = synchronized(activePlayers) { activePlayers[view]?.get() } ?: return proposed
            host.nativeForeground = proposed
            return null
        }
    }
}

/** Native View counterpart to the shared floating-navigation frost and capsule geometry. */
private class SpotifyCapsuleDrawable(private val density: Float) : Drawable() {
    private val tuning = AdvancedMaterialFineTuning.Default
    private val recipe = DeadlinerGlassRecipes.floatingNavigation(tuning)
    private val material = AdvancedMaterialSpec(enabled = true)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val gray = (tuning.glassTintDarkGray * 255).roundToInt()
        color = Color.argb((255 * tuning.navigationTintAlpha * tuning.glassTintAlphaMultiplier)
            .roundToInt(), gray, gray, gray)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = tuning.fallbackEdgeWidthDp * density
    }
    private val node = RenderNode("Spotify playback capsule")
    private var effectWidth = -1
    private var effectHeight = -1
    private var effectInsetLeft = -1
    private var effectInsetRight = -1

    private fun updateOpticalEffect() {
        if (effectWidth == bounds.width() && effectHeight == bounds.height() &&
            effectInsetLeft == insetLeft && effectInsetRight == insetRight) return
        effectWidth = bounds.width()
        effectHeight = bounds.height()
        effectInsetLeft = insetLeft
        effectInsetRight = insetRight
        // Match SoftGlassSurface: color controls -> blur -> capsule lens -> softening.
        // Its blur radius is already in pixels, whereas lens geometry uses dp.
        val saturation = ColorMatrix().apply { setSaturation(material.blurSaturation) }
        val contrast = material.blurContrast
        val lift = (material.blurBrightness + (1f - contrast) * 0.5f) * 255f
        val treatment = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, lift,
            0f, contrast, 0f, 0f, lift,
            0f, 0f, contrast, 0f, lift,
            0f, 0f, 0f, 1f, 0f,
        )).apply { postConcat(saturation) }
        var effect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(treatment))
        val radius = (material.blurRadius * recipe.blur.radiusMultiplier)
            .coerceIn(0f, recipe.blur.maxRadius)
        if (radius > 0f) {
            effect = RenderEffect.createBlurEffect(radius, radius, effect, Shader.TileMode.CLAMP)
        }
        if (Build.VERSION.SDK_INT >= 33 && recipe.refraction.enabled) {
            val shader = RuntimeShader(GlassRefractionShader).apply {
                setFloatUniform("content_origin", insetLeft.toFloat(), 0f)
                setFloatUniform("content_size",
                    (bounds.width() - insetLeft - insetRight).toFloat(), bounds.height().toFloat())
                setFloatUniform("corner_radius", bounds.height() / 2f)
                setFloatUniform("refraction_height", recipe.refraction.height.value * density)
                setFloatUniform("refraction_amount", recipe.refraction.amount.value * density)
                setFloatUniform("depth_effect", recipe.refraction.depthEffect)
                setFloatUniform("chromatic_aberration", recipe.refraction.chromaticAberration.value * density)
                setFloatUniform("noise_coefficient", material.noiseCoefficient * recipe.blur.noiseMultiplier)
                setFloatUniform("highlight_alpha", recipe.edgeOptics.highlightAlpha *
                    0.85f * recipe.edgeOptics.darkHighlightMultiplier)
                setFloatUniform("highlight_gray", recipe.edgeOptics.highlightGray)
            }
            effect = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "source"), effect)
            if (recipe.layering.postRefractionBlurEnabled) {
                val postRadius = recipe.layering.postRefractionBlurRadius.value * density
                if (postRadius > 0f) effect = RenderEffect.createBlurEffect(
                    postRadius, postRadius, effect, Shader.TileMode.CLAMP)
            }
        }
        node.setRenderEffect(effect)
    }
    private var snapshot: ViewBackdropSnapshot? = null
    private var dirty = true
    private var insetLeft = 0
    private var insetRight = 0

    fun setHorizontalInsets(left: Int, right: Int) {
        insetLeft = left
        insetRight = right
        dirty = true
        invalidateSelf()
    }

    fun update(value: ViewBackdropSnapshot?) {
        snapshot = value
        dirty = true
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) { dirty = true }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val rect = RectF(bounds).apply { left += insetLeft; right -= insetRight }
        val radius = rect.height() / 2f
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        val sample = snapshot
        if (sample != null && canvas.isHardwareAccelerated) {
            if (dirty) {
                updateOpticalEffect()
                node.setPosition(bounds.left, bounds.top, bounds.right, bounds.bottom)
                val recording = node.beginRecording(bounds.width(), bounds.height())
                try {
                    val left = (bounds.width() - sample.sourceWidthPx) / 2f + sample.alignmentOffsetXPx
                    val top = (bounds.height() - sample.sourceHeightPx) / 2f + sample.alignmentOffsetYPx
                    recording.drawBitmap(sample.bitmap, null, RectF(left, top,
                        left + sample.sourceWidthPx, top + sample.sourceHeightPx), bitmapPaint)
                } finally { node.endRecording() }
                dirty = false
            }
            canvas.drawRenderNode(node)
        }
        // Readable neutral fallback while the first backdrop is being captured.
        canvas.drawRoundRect(rect, radius, radius, tintPaint)
        val edgeAlpha = if (Build.VERSION.SDK_INT >= 33 && recipe.refraction.enabled) {
            recipe.edgeOptics.highlightAlpha * 0.85f * recipe.edgeOptics.darkHighlightMultiplier
        } else recipe.edgeOptics.fallbackDarkAlpha * 0.45f
        val gray = (recipe.edgeOptics.highlightGray * 255).roundToInt()
        edgePaint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            intArrayOf(Color.argb((edgeAlpha * 255).roundToInt(), gray, gray, gray),
                Color.argb((edgeAlpha * 0.62f * 255).roundToInt(), gray, gray, gray),
                Color.argb((edgeAlpha * 0.30f * 255).roundToInt(), gray, gray, gray)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val edge = RectF(rect).apply { inset(edgePaint.strokeWidth / 2f, edgePaint.strokeWidth / 2f) }
        canvas.drawRoundRect(edge, radius, radius, edgePaint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { node.alpha = alpha / 255f; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
