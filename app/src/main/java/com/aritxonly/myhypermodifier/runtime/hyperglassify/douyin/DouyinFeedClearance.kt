package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 40.7.0: i6 controls and pn7 video are siblings, never resize the 1mw page shell. */
internal class DouyinFeedClearance(activity: Activity) {
    private val activity = WeakReference(activity)
    private val entries = WeakHashMap<View, Entry>()
    private val progress = DouyinProgressClearance()
    private var active = false
    private var bounds: ViewBackdropBounds? = null

    fun update(enabled: Boolean, dock: ViewBackdropBounds?) {
        val entering = enabled && !active
        active = enabled
        bounds = dock
        progress.update(enabled, dock)
        if (entering) discoverExistingPages()
        entries.values.forEach { it.apply() }
    }

    // Only on entering a video tab/enabling. New recycled pages arrive through their constructor.
    private fun discoverExistingPages() {
        val host = activity.get() ?: return
        val id = host.resources.getIdentifier("1mw", "id", host.packageName)
        val content = host.window.decorView.findViewById<View>(id) ?: return
        val stack = java.util.ArrayDeque<View>()
        stack.add(content)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 2048) {
            val view = stack.removeLast()
            if (view.javaClass.simpleName == "VideoViewHolderRootView") {
                addPage(view)
                continue
            }
            if (view.javaClass.simpleName == "DProSeekBar" || view.javaClass.simpleName == "CustomizedUISeekBar" ||
                view.javaClass.simpleName == "LineProgressBar") progress.add(view)
            if (view is ViewGroup) repeat(view.childCount) { stack.add(view.getChildAt(it)) }
        }
    }

    fun addProgress(view: View) = progress.add(view)

    fun addCollection(view: View) {
        var ancestor = view.parent as? View
        repeat(16) {
            val page = ancestor ?: return
            if (page.javaClass.name == "com.ss.android.ugc.aweme.ad.feed.VideoViewHolderRootView") {
                addPage(page)
                entries[page]?.setCollection(view)
                return
            }
            ancestor = page.parent as? View
        }
    }

    fun addPage(page: View) {
        if (entries.containsKey(page)) return
        val root = page as? ViewGroup ?: return
        // Class/parent structure checked against the supplied APK, no obfuscated ID guessing.
        var monitor: ViewGroup? = null
        repeat(root.childCount) { index ->
            val child = root.getChildAt(index)
            if (child.javaClass.simpleName == "MonitorScrollFrameLayout") monitor = child as? ViewGroup
        }
        val parent = monitor ?: return
        var controls: View? = null
        repeat(parent.childCount) { index ->
            val child = parent.getChildAt(index)
            if (child.javaClass.simpleName == "PenetrateTouchRelativeLayout") controls = child
        }
        val target = controls ?: return
        if (target.layoutParams !is ViewGroup.MarginLayoutParams) return
        val legacyId = root.resources.getIdentifier("u1k", "id", root.context.packageName)
        root.findViewById<View>(legacyId)?.takeIf {
            it.javaClass.name == "com.ss.android.ugc.aweme.feed.widget.LineProgressBar"
        }?.let(progress::add)
        val footerId = target.resources.getIdentifier("ctk", "id", target.context.packageName)
        val footerContainerId = target.resources.getIdentifier("cne", "id", target.context.packageName)
        val entry = Entry(page, parent, target, footerId, footerContainerId)
        entries[page] = entry
        page.addOnLayoutChangeListener(entry)
        parent.addOnLayoutChangeListener(entry)
        target.addOnLayoutChangeListener(entry)
        entry.apply()
    }

    fun dispose() {
        active = false
        progress.dispose()
        entries.values.forEach { it.dispose() }
        entries.clear()
    }

    private inner class Entry(page: View, parent: View, controls: View,
                              private val footerId: Int, private val footerContainerId: Int) : View.OnLayoutChangeListener, Runnable {
        private val page = WeakReference(page)
        private val parent = WeakReference(parent)
        private val controls = WeakReference(controls)
        private var footer = WeakReference<View>(null)
        private var footerContainer = WeakReference<ViewGroup>(null)
        private var collection = WeakReference<View>(null)
        private var original: Int? = null
        private var applied: Int? = null
        private val pageLocation = IntArray(2)
        private val parentLocation = IntArray(2)
        private val viewportLocation = IntArray(2)
        private val controlsLocation = IntArray(2)
        private val footerLocation = IntArray(2)
        private var queued = false

        private fun resolveFooter(owner: View): View? {
            val container = footerContainer.get()?.takeIf { it.parent === owner }
                ?: (owner as? ViewGroup)?.let { group ->
                    // cne is a direct child of i6, and ctk a direct child of cne.
                    // Limit lookup to these two shallow levels; never walk the video subtree.
                    var found: ViewGroup? = null
                    for (index in 0 until group.childCount.coerceAtMost(96)) {
                        val child = group.getChildAt(index)
                        if (child.id == footerContainerId) { found = child as? ViewGroup; break }
                    }
                    footerContainer = WeakReference(found)
                    found
                }
            val previous = footer.get()
            if (previous != null && previous.parent === container && container != null) return previous
            var next: View? = null
            if (container != null) {
                for (index in 0 until container.childCount.coerceAtMost(16)) {
                    val child = container.getChildAt(index)
                    if (child.id == footerId) { next = child; break }
                }
            }
            if (previous !== next) {
                previous?.removeCallbacks(this)
                previous?.removeOnLayoutChangeListener(this)
                footer = WeakReference(next)
                next?.addOnLayoutChangeListener(this)
            }
            return next
        }

        fun setCollection(view: View) {
            if (collection.get() === view) return
            // Only anchors inside this controls layer can define its existing bottom inset.
            var ancestor = view.parent as? View
            val owner = controls.get() ?: return
            var depth = 0
            while (ancestor != null && ancestor !== owner && depth++ < 16) {
                ancestor = ancestor.parent as? View
            }
            if (ancestor !== owner) return
            collection.get()?.removeOnLayoutChangeListener(this)
            collection = WeakReference(view)
            view.addOnLayoutChangeListener(this)
            apply()
        }

        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                                    oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
            if (!active || queued) return
            queued = true
            v.post(this)
        }
        override fun run() { queued = false; apply() }

        fun apply() {
            val view = controls.get() ?: return
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            if (!active) {
                val saved = original
                if (saved != null && params.bottomMargin == applied) {
                    params.bottomMargin = saved
                    view.layoutParams = params
                }
                original = null
                applied = null
                return
            }
            val dock = bounds ?: return
            val root = page.get() ?: return
            val host = parent.get() ?: return
            val viewport = root.parent as? View ?: return
            if (!root.isAttachedToWindow || host.height == 0 || viewport.height == 0) return
            if (original == null || params.bottomMargin != applied) original = params.bottomMargin
            root.getLocationOnScreen(pageLocation)
            host.getLocationOnScreen(parentLocation)
            viewport.getLocationOnScreen(viewportLocation)
            // ctk retains the native 47dp tab reservation inside i6. Account for that
            // existing inset rather than adding a second reservation above the glass.
            val currentFooter = resolveFooter(view)
            val anchor = collection.get()?.takeIf { it.isShown && it.height > 0 }
                ?: currentFooter?.takeIf { it.visibility != View.GONE }
            val nativeInset = anchor?.let { bottom ->
                view.getLocationOnScreen(controlsLocation)
                bottom.getLocationOnScreen(footerLocation)
                (view.height - (footerLocation[1] - controlsLocation[1] + bottom.height)).coerceIn(0, view.height)
            } ?: 0
            // Content -> 10dp -> visible progress line -> 2dp -> glass.
            val clearance = (view.resources.displayMetrics.density * 12f).toInt()
            // Subtract this page's swipe offset: every page carries a constant lift while swiping.
            val margin = DouyinNavigationPolicy.controlsBottomMargin(
                original ?: 0, viewportLocation[1], parentLocation[1], pageLocation[1], host.height, dock.top, clearance, nativeInset,
            )
            if (params.bottomMargin != margin) {
                params.bottomMargin = margin
                view.layoutParams = params
            }
            applied = margin
        }

        fun dispose() {
            page.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            parent.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            controls.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            footer.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            collection.get()?.let { it.removeCallbacks(this); it.removeOnLayoutChangeListener(this) }
            queued = false
            apply()
        }
    }
}
