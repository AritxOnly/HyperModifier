package com.aritxonly.myhypermodifier

import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import java.lang.reflect.Method

/** The large_main playback container paints a Compose background behind its AndroidView. */
internal class SpotifyPlayerContainerBackground private constructor(
    private val container: View,
    private val getColor: Method,
    private val setColor: Method,
) {
    private var containsPlayer = false
    private var originalColor: Long? = null
    private var accessible = true

    fun bind(player: View?) {
        var ancestor = player?.parent
        while (ancestor is View && ancestor !== container) ancestor = ancestor.parent
        containsPlayer = ancestor === container
    }

    fun sync(capsuleVisible: Boolean, layoutChanged: Boolean) {
        // On 9.1.80 the collapsed region uses WRAP_CONTENT. Expanded playback and the
        // transition use match-constraint (0) and Spotify assigns their own surface color.
        if (!capsuleVisible || !containsPlayer || container.layoutParams?.height != WRAP_CONTENT) {
            restore()
            return
        }
        if (!accessible || (originalColor != null && !layoutChanged)) return
        try {
            val current = getColor.invoke(container) as Long
            if (current != TRANSPARENT) {
                setColor.invoke(container, TRANSPARENT)
                originalColor = current
            } else if (originalColor == null) {
                originalColor = TRANSPARENT
            }
        } catch (_: ReflectiveOperationException) {
            accessible = false
        }
    }

    fun restore() {
        val color = originalColor ?: return
        originalColor = null
        if (color == TRANSPARENT) return
        try {
            // The app can already have installed its expanded or themed color.
            if (getColor.invoke(container) == TRANSPARENT) setColor.invoke(container, color)
        } catch (_: ReflectiveOperationException) {
            accessible = false
        }
    }

    companion object {
        // Packed sRGB Compose Color.Transparent in the host's class loader.
        private const val TRANSPARENT = 0L

        fun create(container: View): SpotifyPlayerContainerBackground? {
            val type = container.javaClass
            return try {
                val getter = type.getMethod("getBackgroundColor-0d7_KjU")
                if (getter.returnType != Long::class.javaPrimitiveType) return null
                val setter = type.getMethod("setBackgroundColor-8_81llA", Long::class.javaPrimitiveType)
                SpotifyPlayerContainerBackground(container, getter, setter)
            } catch (_: NoSuchMethodException) {
                null
            }
        }
    }
}
