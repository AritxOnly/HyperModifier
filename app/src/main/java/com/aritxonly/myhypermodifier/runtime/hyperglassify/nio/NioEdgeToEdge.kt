package com.aritxonly.myhypermodifier

import android.annotation.SuppressLint
import android.view.Window
import android.view.WindowInsets
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Owns only NIO Home's replacement interval; leaves the app's inset listener intact. */
// Used only inside the LSPosed target process. Exact restoration requires this hidden getter;
// unavailable access leaves the window policy untouched rather than guessing the original value.
@SuppressLint("BlockedPrivateApi")
@Suppress("DEPRECATION")
internal class NioEdgeToEdge(window: Window) {
    private val window = WeakReference(window)
    private var enabled = false
    private var unavailable = false
    private var internalWrite = false
    private var restoreDecorFits = true
    private var restoreFitTypes = 0

    fun setEnabled(value: Boolean) {
        if (value && unavailable) return
        val target = window.get() ?: return
        if (enabled != value) {
            if (value) {
                // Hidden getter is resolved once; never reflect from the frame callback.
                val original = runCatching { decorFitsGetter?.invoke(target) as? Boolean }.getOrNull()
                    ?: run { unavailable = true; return }
                restoreDecorFits = original
                restoreFitTypes = target.attributes.fitInsetsTypes
                enabled = true
                owners[target] = this
                internalWrite = true
                try { target.setDecorFitsSystemWindows(false) } finally { internalWrite = false }
                val params = target.attributes
                val types = params.fitInsetsTypes and WindowInsets.Type.navigationBars().inv()
                if (types != params.fitInsetsTypes) {
                    params.setFitInsetsTypes(types)
                    target.attributes = params
                }
            } else {
                enabled = false
                owners.remove(target)
                target.setDecorFitsSystemWindows(restoreDecorFits)
                val params = target.attributes
                if (params.fitInsetsTypes != restoreFitTypes) {
                    params.setFitInsetsTypes(restoreFitTypes)
                    target.attributes = params
                }
            }
            target.decorView.requestApplyInsets()
        }
    }

    companion object {
        private val owners = WeakHashMap<Window, NioEdgeToEdge>()
        private val decorFitsGetter by lazy {
            runCatching { Window::class.java.getDeclaredMethod("decorFitsSystemWindows").apply {
                isAccessible = true
            } }.getOrNull()
        }

        /** Called only when the app writes its Window policy, not on draw/touch callbacks. */
        @JvmStatic fun nativeDecorFits(window: Window, requested: Boolean): Boolean {
            val owner = owners[window] ?: return requested
            if (!owner.enabled) return requested
            if (!owner.internalWrite) owner.restoreDecorFits = requested
            return false
        }
    }
}
