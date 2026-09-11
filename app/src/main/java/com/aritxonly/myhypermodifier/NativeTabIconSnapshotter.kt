package com.aritxonly.myhypermodifier

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.DrawableCompat
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Immutable Compose-ready selected and unselected images copied from a target app's TabView. */
internal data class NativeTabIconPair(
    val selected: ImageBitmap,
    val unselected: ImageBitmap,
    val selectedMonochrome: ImageBitmap,
    val unselectedMonochrome: ImageBitmap,
)

/**
 * Copies target-app Drawables into small bitmaps without retaining them in a Compose Painter.
 * Stateful drawables are rendered for both states immediately. Apps that swap two standalone
 * drawables are learned one state at a time as their native tab selection changes.
 */
internal class NativeTabIconSnapshotter(
    private val resources: Resources,
    private val theme: Resources.Theme? = null,
) {
    private val entries = WeakHashMap<View, Entry>()

    fun snapshot(tab: View, iconView: ImageView?, selected: Boolean): NativeTabIconPair? {
        val view = iconView ?: return null
        val drawable = view.drawable ?: view.background ?: return null
        val key = SourceKey(
            drawable = drawable,
            constantState = drawable.constantState,
            level = drawable.level,
            tint = view.imageTintList,
            tintMode = view.imageTintMode,
            colorFilter = view.colorFilter,
            layoutDirection = view.layoutDirection,
        )
        val entry = entries.getOrPut(tab) { Entry() }

        if (drawable.isStateful) {
            if (entry.statefulKey != key) {
                entry.statefulKey = key
                entry.selected = render(view, drawable, SELECTED_STATE)
                entry.unselected = render(view, drawable, UNSELECTED_STATE)
            }
        } else if (selected) {
            if (entry.selectedKey != key) {
                entry.selectedKey = key
                entry.selected = render(view, drawable, SELECTED_STATE)
            }
        } else if (entry.unselectedKey != key) {
            entry.unselectedKey = key
            entry.unselected = render(view, drawable, UNSELECTED_STATE)
        }

        val selectedImage = entry.selected ?: entry.unselected ?: return null
        val unselectedImage = entry.unselected ?: entry.selected ?: return null
        return NativeTabIconPair(
            selected = selectedImage.color,
            unselected = unselectedImage.color,
            selectedMonochrome = selectedImage.monochrome,
            unselectedMonochrome = unselectedImage.monochrome,
        )
    }

    private fun render(iconView: ImageView, source: Drawable, state: IntArray): RenderedIcon? =
        runCatching {
            val copy = source.constantState?.newDrawable(resources, theme)?.mutate()
            val drawable = copy ?: source
            val usesOriginal = drawable === source
            val originalState = if (usesOriginal) source.state.clone() else null
            val originalBounds = if (usesOriginal) Rect(source.bounds) else null
            try {
                DrawableCompat.setLayoutDirection(drawable, iconView.layoutDirection)
                iconView.imageTintList?.let { DrawableCompat.setTintList(drawable, it) }
                iconView.imageTintMode?.let { DrawableCompat.setTintMode(drawable, it) }
                iconView.colorFilter?.let(drawable::setColorFilter)
                drawable.level = source.level
                drawable.state = state

                val fallbackSize = (resources.displayMetrics.density * FALLBACK_SIZE_DP).roundToInt()
                val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 }
                    ?: source.bounds.width().takeIf { it > 0 }
                    ?: iconView.width.takeIf { it > 0 }
                    ?: fallbackSize
                val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 }
                    ?: source.bounds.height().takeIf { it > 0 }
                    ?: iconView.height.takeIf { it > 0 }
                    ?: fallbackSize
                val scale = minOf(1f, MAX_BITMAP_SIZE_PX / maxOf(intrinsicWidth, intrinsicHeight).toFloat())
                val width = maxOf(1, (intrinsicWidth * scale).roundToInt())
                val height = maxOf(1, (intrinsicHeight * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(Canvas(bitmap))
                RenderedIcon(
                    color = bitmap.asImageBitmap(),
                    monochrome = createMonochromeMask(bitmap).asImageBitmap(),
                )
            } finally {
                if (usesOriginal) {
                    originalState?.let(source::setState)
                    originalBounds?.let(source::setBounds)
                }
            }
        }.getOrNull()

    /**
     * Builds a full-strength alpha mask for Compose tinting. Xiaomi Health encodes some inactive
     * icons with a globally reduced alpha and draws the Device icon's inner cutout using the page
     * background color instead of transparency; direct SrcIn tinting would preserve both defects.
     */
    private fun createMonochromeMask(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val maxAlpha = pixels.maxOfOrNull { it ushr 24 }?.coerceAtLeast(1) ?: 1
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        pixels.indices.forEach { index ->
            val color = pixels[index]
            val sourceAlpha = color ushr 24
            if (sourceAlpha == 0) {
                pixels[index] = 0
                return@forEach
            }
            val red = color shr 16 and 0xff
            val green = color shr 8 and 0xff
            val blue = color and 0xff
            val maximum = maxOf(red, green, blue)
            val minimum = minOf(red, green, blue)
            val neutral = maximum - minimum <= MAX_NEUTRAL_CHANNEL_DELTA
            val luminance = (red * 299 + green * 587 + blue * 114) / 1000f
            val topologyAlpha = when {
                !neutral -> 1f
                dark -> ((luminance - DARK_CUTOUT_LUMINANCE) /
                    (DARK_FOREGROUND_LUMINANCE - DARK_CUTOUT_LUMINANCE)).coerceIn(0f, 1f)
                else -> ((LIGHT_CUTOUT_LUMINANCE - luminance) /
                    (LIGHT_CUTOUT_LUMINANCE - LIGHT_FOREGROUND_LUMINANCE)).coerceIn(0f, 1f)
            }
            val normalizedAlpha = (sourceAlpha * 255f / maxAlpha * topologyAlpha)
                .roundToInt()
                .coerceIn(0, 255)
            pixels[index] = normalizedAlpha shl 24
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private data class RenderedIcon(
        val color: ImageBitmap,
        val monochrome: ImageBitmap,
    )

    private data class Entry(
        var statefulKey: SourceKey? = null,
        var selectedKey: SourceKey? = null,
        var unselectedKey: SourceKey? = null,
        var selected: RenderedIcon? = null,
        var unselected: RenderedIcon? = null,
    )

    private data class SourceKey(
        val drawable: Drawable,
        val constantState: Drawable.ConstantState?,
        val level: Int,
        val tint: android.content.res.ColorStateList?,
        val tintMode: android.graphics.PorterDuff.Mode?,
        val colorFilter: android.graphics.ColorFilter?,
        val layoutDirection: Int,
    )

    private companion object {
        val SELECTED_STATE = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected)
        val UNSELECTED_STATE = intArrayOf(android.R.attr.state_enabled)
        const val FALLBACK_SIZE_DP = 26f
        const val MAX_BITMAP_SIZE_PX = 256
        const val MAX_NEUTRAL_CHANNEL_DELTA = 18
        const val LIGHT_FOREGROUND_LUMINANCE = 214f
        const val LIGHT_CUTOUT_LUMINANCE = 250f
        const val DARK_CUTOUT_LUMINANCE = 31f
        const val DARK_FOREGROUND_LUMINANCE = 78f
    }
}

/** Finds the actual custom-tab icon and avoids red-point/badge ImageViews with app-specific IDs. */
internal fun findNativeTabIconView(
    root: View,
    preferredId: Int,
    excludedIds: Set<Int>,
): ImageView? {
    val candidates = buildList {
        fun visit(view: View) {
            if (view is ImageView && view.id !in excludedIds &&
                (view.drawable != null || view.background != null)
            ) {
                add(view)
            }
            if (view is ViewGroup) {
                repeat(view.childCount) { visit(view.getChildAt(it)) }
            }
        }
        visit(root)
    }
    return candidates.maxByOrNull { view ->
        val resourceName = runCatching { view.resources.getResourceEntryName(view.id) }
            .getOrDefault("").lowercase()
        var score = 0L
        if (preferredId != 0 && view.id == preferredId) score += 1_000_000L
        if (resourceName == "icon" || resourceName.endsWith("_icon")) score += 100_000L
        if (resourceName.contains("icon")) score += 10_000L
        if (resourceName.contains("red") || resourceName.contains("badge") ||
            resourceName.contains("point") || resourceName.contains("indicator")
        ) score -= 1_000_000L
        if (view.visibility == View.VISIBLE) score += 1_000L
        score + maxOf(1, view.width) * maxOf(1, view.height)
    }
}
