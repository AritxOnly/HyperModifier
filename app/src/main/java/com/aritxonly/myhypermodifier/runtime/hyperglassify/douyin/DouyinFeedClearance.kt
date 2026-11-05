package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs

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
        val footerId = target.resources.getIdentifier("ctk", "id", target.context.packageName)
        val footerContainerId = target.resources.getIdentifier("cne", "id", target.context.packageName)
        val descriptionContainerId = target.resources.getIdentifier(
            "etb",
            "id",
            target.context.packageName,
        )
        val descriptionTextId = target.resources.getIdentifier(
            "desc",
            "id",
            target.context.packageName,
        )
        val entry = Entry(
            page,
            parent,
            target,
            footerId,
            footerContainerId,
            descriptionContainerId,
            descriptionTextId,
        )
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

    private inner class Entry(
        page: View,
        parent: View,
        controls: View,
        private val footerId: Int,
        private val footerContainerId: Int,
        private val descriptionContainerId: Int,
        private val descriptionTextId: Int,
    ) : View.OnLayoutChangeListener {
        private val page = WeakReference(page)
        private val parent = WeakReference(parent)
        private val controls = WeakReference(controls)
        private var footer = WeakReference<View>(null)
        private var footerContainer = WeakReference<ViewGroup>(null)
        private var collection = WeakReference<View>(null)
        private var descriptionContainer = WeakReference<View>(null)
        private var descriptionText = WeakReference<View>(null)
        private var originalTranslation: Float? = null
        private var appliedTranslation: Float? = null
        private val pageLocation = IntArray(2)
        private val parentLocation = IntArray(2)
        private val viewportLocation = IntArray(2)
        private val controlsLocation = IntArray(2)
        private val footerLocation = IntArray(2)

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
            if (previous != null && previous.parent === container) return previous
            var next: View? = null
            if (container != null) {
                for (index in 0 until container.childCount.coerceAtMost(16)) {
                    val child = container.getChildAt(index)
                    if (child.id == footerId) { next = child; break }
                }
            }
            if (previous !== next) {
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

        private fun resolveDescriptionAnchor(owner: View): View? {
            val previousContainer = descriptionContainer.get()
            val container = if (previousContainer?.parent != null) {
                previousContainer
            } else {
                owner.findViewById<View>(descriptionContainerId)?.also { next ->
                    if (previousContainer !== next) {
                        previousContainer?.removeOnLayoutChangeListener(this)
                        descriptionContainer = WeakReference(next)
                        descriptionText = WeakReference(null)
                        next.addOnLayoutChangeListener(this)
                    }
                }
            }
            val previousText = descriptionText.get()
            val text = if (previousText?.parent != null) {
                previousText
            } else {
                (container ?: owner).findViewById<View>(descriptionTextId)?.also { next ->
                    if (previousText !== next) {
                        previousText?.removeOnLayoutChangeListener(this)
                        descriptionText = WeakReference(next)
                        next.addOnLayoutChangeListener(this)
                    }
                }
            }
            return text?.takeIf { it.isUsableAnchor() }
                ?: container?.takeIf { it.isUsableAnchor() }
        }

        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                                    oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
            // translationY does not request another layout, so applying it synchronously avoids
            // the old margin write -> relayout -> posted write feedback loop.
            apply()
        }

        fun apply() {
            val view = controls.get() ?: return
            if (!active) {
                restoreTranslation(view)
                return
            }
            val dock = bounds ?: return
            val root = page.get() ?: return
            val host = parent.get() ?: return
            val viewport = root.parent as? View ?: return
            if (!root.isAttachedToWindow || host.height == 0 || viewport.height == 0) {
                restoreTranslation(view)
                return
            }
            // ctk is the stable footer container. The collection card is only a fallback while
            // ctk is absent, so normal feed animations cannot switch the inset back and forth.
            val anchor = resolveFooter(view)?.takeIf { it.isUsableAnchor() }
                ?: collection.get()?.takeIf { it.isUsableAnchor() }
                ?: resolveDescriptionAnchor(view)
            if (anchor == null) {
                restoreTranslation(view)
                return
            }
            root.getLocationOnScreen(pageLocation)
            host.getLocationOnScreen(parentLocation)
            viewport.getLocationOnScreen(viewportLocation)
            view.getLocationOnScreen(controlsLocation)
            anchor.getLocationOnScreen(footerLocation)
            val nativeInset = (
                view.height - (footerLocation[1] - controlsLocation[1] + anchor.height)
            ).coerceIn(0, view.height)
            // Use the settled layout bottom, not the translated screen position. This keeps the
            // result constant during page swipes and while the module translation is active.
            val settledControlsBottom =
                viewportLocation[1] + parentLocation[1] - pageLocation[1] + view.height
            val untranslatedContentBottom = settledControlsBottom - nativeInset
            val translation = DouyinNavigationPolicy.controlsTranslation(
                untranslatedContentBottom.toFloat(),
                dock.top,
            )
            val current = view.translationY
            val previousApplied = appliedTranslation
            if (originalTranslation == null ||
                (previousApplied != null && abs(current - previousApplied) > 0.5f)
            ) {
                originalTranslation = current
            }
            val target = (originalTranslation ?: 0f) + translation
            if (abs(current - target) > 0.5f) view.translationY = target
            appliedTranslation = target
        }

        private fun restoreTranslation(view: View) {
            val original = originalTranslation
            val applied = appliedTranslation
            if (original != null && applied != null && abs(view.translationY - applied) <= 0.5f) {
                view.translationY = original
            }
            originalTranslation = null
            appliedTranslation = null
        }

        private fun View.isUsableAnchor(): Boolean =
            isAttachedToWindow && isShown && height > 0

        fun dispose() {
            page.get()?.removeOnLayoutChangeListener(this)
            parent.get()?.removeOnLayoutChangeListener(this)
            controls.get()?.removeOnLayoutChangeListener(this)
            footer.get()?.removeOnLayoutChangeListener(this)
            collection.get()?.removeOnLayoutChangeListener(this)
            descriptionContainer.get()?.removeOnLayoutChangeListener(this)
            descriptionText.get()?.removeOnLayoutChangeListener(this)
            apply()
        }
    }
}
