package com.aritxonly.myhypermodifier

import android.app.Activity
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Removes only 9.13.0's bottom inset, retaining its status/side insets and native listener. */
internal class BilibiliHomeImmersion(activity: Activity) {
    private val root = WeakReference(activity.findViewById<View>(resourceId(activity, "drawer")))
    private val content = WeakReference(activity.findViewById<View>(resourceId(activity, "content_layout")))
    private var enabled = false
    private var originalBottomPadding: Int? = null
    private var background = WeakReference<Drawable>(null)
    private var strip = WeakReference<Drawable>(null)
    private var accessor: InsetsAccessor? = null
    private var originalInsets: Any? = null

    fun setEnabled(value: Boolean) {
        if (value) {
            enabled = true
            apply(false)
        } else if (enabled) {
            enabled = false
            content.get()?.let { view ->
                originalBottomPadding?.let { bottom ->
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
                }
            }
            val drawable = strip.get()
            if (drawable != null) runCatching {
                originalInsets?.let { accessor?.update?.invoke(drawable, it) }
            }
            originalBottomPadding = null
            originalInsets = null
        }
    }

    fun onNativeInsets(view: View) {
        if (enabled && view === root.get()) apply(true)
    }

    private fun apply(nativeInsetsChanged: Boolean) {
        content.get()?.let { view ->
            if (originalBottomPadding == null || nativeInsetsChanged || view.paddingBottom != 0) {
                originalBottomPadding = view.paddingBottom
            }
            if (view.paddingBottom != 0) {
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
            }
        }
        val currentBackground = root.get()?.background ?: return
        if (background.get() !== currentBackground) {
            background = WeakReference(currentBackground)
            val found = navigationStrip(currentBackground)
            strip = WeakReference(found)
            accessor = found?.let { InsetsAccessor.create(it) }
            originalInsets = null
        }
        val drawable = strip.get() ?: return
        val access = accessor ?: return
        runCatching {
            val insets = access.current.get(drawable) ?: return@runCatching
            val bottom = access.bottom.getInt(insets)
            if (originalInsets == null || nativeInsetsChanged || bottom != 0) originalInsets = insets
            if (bottom != 0) {
                val immersive = access.of.invoke(
                    null, access.left.getInt(insets), access.top.getInt(insets),
                    access.right.getInt(insets), 0,
                )
                access.update.invoke(drawable, immersive)
            }
        }
    }

    private class InsetsAccessor(
        val current: Field,
        val left: Field,
        val top: Field,
        val right: Field,
        val bottom: Field,
        val of: Method,
        val update: Method,
    ) {
        companion object {
            fun create(drawable: Drawable): InsetsAccessor? = runCatching {
                // Host AndroidX Insets belongs to the app's ClassLoader, not the module's.
                val current = drawable.javaClass.getDeclaredField("b").apply { isAccessible = true }
                val type = current.type
                InsetsAccessor(
                    current, type.getField("left"), type.getField("top"), type.getField("right"),
                    type.getField("bottom"),
                    type.getMethod("of", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                    drawable.javaClass.getMethod("updateInsets", type),
                )
            }.getOrNull()
        }
    }

    private companion object {
        fun resourceId(activity: Activity, name: String): Int =
            activity.resources.getIdentifier(name, "id", activity.packageName)

        fun navigationStrip(drawable: Drawable): Drawable? {
            if (drawable.javaClass.name ==
                "com.bilibili.lib.ui.windowinsets.NavigationBarInsetsBackgroundDrawable"
            ) return drawable
            if (drawable is LayerDrawable) {
                for (index in drawable.numberOfLayers - 1 downTo 0) {
                    navigationStrip(drawable.getDrawable(index))?.let { return it }
                }
            }
            return null
        }
    }
}
