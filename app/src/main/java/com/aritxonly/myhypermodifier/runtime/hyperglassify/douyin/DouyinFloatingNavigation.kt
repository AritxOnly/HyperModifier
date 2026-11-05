package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap
import android.view.WindowInsets
import android.widget.TextView
import java.lang.reflect.Method
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArrayList
import java.lang.ref.WeakReference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Carrier
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages

/** 40.7.0: BottomTab owns selection, click analytics and account-specific tab ordering. */
internal object DouyinFloatingNavigation {
    private const val OUTER_BAR = "com.ss.android.ugc.aweme.homepage.tab.bottom.ui.tabbar.container.MainBottomTabContainer"
    private const val INNER_BAR = "X.0gnN"
    private val innerBars = WeakHashMap<View, WeakReference<View>>()
    private val tabGroups = WeakHashMap<View, WeakReference<ViewGroup>>()
    private val tabIds = WeakHashMap<View, String?>()
    private val activities = CopyOnWriteArrayList<ActivityState>()
    private val chatViews = WeakHashMap<Activity, ArrayList<WeakReference<View>>>()
    // Class metadata only: no Activity/View/Context retained by reflection caches.
    private val accessors = HashMap<Class<*>, TabAccess>()
    private var sceneDarkField: Field? = null
    private var sceneAccessResolved = false
    private val delegate = NativeViewBottomBarNavigation(
        NativeViewBottomBarTarget(
            logName = "Douyin", bottomEdgeMask = false, testedVersion = "40.7.0",
            // Outer FrameLayout has no anti-burn setAlpha override; inner state remains authoritative.
            bottomBarClassNames = emptySet(), // Only attach after the inner bar is available.
            resolveBottomBar = ::findReadyBar,
            nativeBarVisible = { outer ->
                innerBars[outer]?.get()?.let { inner ->
                    inner.isAttachedToWindow && inner.isShown && inner.alpha > 0f
                } == true
            },
            tabClassNames = setOf("com.ss.android.ugc.aweme.homepage.tab.ui.bottom.BottomTab"),
            fallbackLabels = listOf("首页", "朋友", "发布", "消息", "我"),
            enabled = { ModuleSettings.moduleHooksEnabled && ModuleSettings.douyinFloatingNavigationEnabled },
            useMiuixIcons = { true }, useMonochromeIcons = { true }, showBadges = { false },
            fallbackIcon = { label, _ ->
                when {
                    label == "发布" -> MiuixIcons.Add
                    label.contains("消息") -> MiuixIcons.Messages
                    label.contains("商城") || label.contains("购物") -> MiuixIcons.Carrier
                    label == "我" || label.contains("我的") -> MiuixIcons.ContactsCircle
                    label.contains("朋友") || label.contains("关注") -> MiuixIcons.All
                    else -> MiuixIcons.Home
                }
            },
            resolveTabLabel = { tab, index ->
                val access = access(tab)
                val id = access.id(tab)
                if (id == "homepage_publish") "发布" else
                    (access.read(access.title, tab) as? TextView)?.text?.toString()?.takeIf { it.isNotBlank() }
                        ?: DouyinNavigationPolicy.fallbackLabel(id, index)
            },
            resolveTabViews = { outer ->
                tabGroups[outer]?.get()?.let { group ->
                    buildList {
                        repeat(group.childCount.coerceAtMost(8)) { index ->
                            val tab = group.getChildAt(index)
                            if (tab.visibility != View.GONE) add(tab)
                        }
                    }
                }.orEmpty()
            },
            resolveDarkTheme = { activity, _, tabs, index ->
                val tab = tabs.getOrNull(index)
                val title = tab?.let { access(it).read(access(it).title, it) as? TextView }
                val id = tab?.let { access(it).id(it) }
                val video = DouyinNavigationPolicy.isVideoPage(id, title?.text?.toString())
                val dark = DouyinNavigationPolicy.resolveDarkTheme(
                    nativeSceneDark(tab),
                    title?.currentTextColor ?: 0,
                    activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES,
                )
                activities.firstOrNull { it.activity.get() === activity }?.let {
                    it.setVideoPage(video, id)
                    it.setDarkTheme(dark)
                }
                dark
            },
            sampleBackdropForTabs = { tabs, index ->
                val tab = tabs.getOrNull(index)
                val id = tab?.let { access(it).id(it) }
                val label = tab?.let { access(it).read(access(it).title, it) as? TextView }?.text?.toString()
                !DouyinNavigationPolicy.isVideoPage(id, label)
            },
            onNavigationBoundsChanged = { activity, bounds ->
                activities.firstOrNull { it.activity.get() === activity }?.setDockBounds(bounds)
            },
            resolveSelectedIndex = { _, tabs -> tabs.indexOfFirst { it.isSelected }.coerceAtLeast(0) },
            isDetachedAction = { tab, _ -> access(tab).id(tab) == "homepage_publish" },
            // Original listener routes navigation/publish and preserves login checks and analytics.
            onNativeTabClick = { it.performClick() },
            nativeRefreshIntervalMs = 100L,
            useNativeGpuBackdrop = true,
            useVideoSurfaceBackdrop = true,
            inTreeOverlay = true,
            gpuContentResourceName = "1mw",
            manageWindowImmersion = false,
            retainNativeSuppressionDuringCover = true,
            retainReplacementDuringCover = true,
            useHardwareBackdrop = true, allowSoftwareBackdrop = false,
            minimumCaptureIntervalMs = 250L, sampleBackdropOnSourceFrame = false,
            stableNavigationInset = true,
            // Only the per-video controls layer reserves space; the page/video remains full size.
            onChromeReplacement = { activity, replacing ->
                activities.firstOrNull { it.activity.get() === activity }?.setReplacing(replacing)
            },
            overlayAllowed = { activity ->
                activity.hasWindowFocus() && !hasVisibleChat(activity) &&
                    activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true
            },
        ),
    )

    init {
        ModuleSettings.onChanged {
            val enabled = ModuleSettings.moduleHooksEnabled && ModuleSettings.douyinFloatingNavigationEnabled
            val lift = ModuleSettings.hyperGlassifyHiddenNavigationLift
            activities.forEach { it.schedule(enabled, lift) }
        }
    }

    @JvmStatic fun prepare(activity: Activity) {
        ModuleSettings.markLoaded(activity)
        if (activities.none { it.activity.get() === activity }) activities.add(ActivityState(activity))
        delegate.prepare(activity)
    }
    @JvmStatic fun attach(activity: Activity) {
        prepare(activity)
        delegate.attach(activity)
        delegate.setForeground(activity, true)
    }
    @JvmStatic fun focus(activity: Activity, focused: Boolean) {
        if (focused) attach(activity)
        delegate.refresh(activity)
        if (focused) activities.firstOrNull { it.activity.get() === activity }?.refreshImmersion()
    }
    @JvmStatic fun pause(activity: Activity) = delegate.setForeground(activity, false)
    @JvmStatic fun dispose(activity: Activity) {
        chatViews.remove(activity)
        // Restore chrome while its weak state is still registered.
        delegate.dispose(activity)
        activities.removeAll { state ->
            if (state.activity.get() === activity) {
                state.disposeClearance()
                activity.window.decorView.removeCallbacks(state)
                true
            } else state.activity.get() == null
        }
    }
    @JvmStatic fun onChatViewCreated(activity: Activity, view: View) {
        val views = chatViews.getOrPut(activity) { ArrayList(1) }
        if (views.none { it.get() === view }) views.add(WeakReference(view))
        delegate.refresh(activity)
    }

    private fun hasVisibleChat(activity: Activity): Boolean {
        val views = chatViews[activity] ?: return false
        var index = 0
        while (index < views.size) {
            val view = views[index].get()
            if (view == null) { views.removeAt(index); continue }
            if (view.isAttachedToWindow && view.isShown && view.alpha > 0f) return true
            index++
        }
        return false
    }
    @JvmStatic fun onFeedPageCreated(page: View) {
        // One registration after attach; constructors can delegate to each other.
        if (!feedPages.add(page)) return
        page.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private val register = Runnable {
                val root = page.rootView
                activities.firstOrNull { it.activity.get()?.window?.decorView === root }?.let { state ->
                    if (page.javaClass.simpleName == "VideoViewHolderRootView") {
                        state.addFeedPage(page)
                        DouyinSurfaceBackdrop.onVideoPageAttached(page)
                    }
                    else state.addProgress(page)
                }
            }
            override fun onViewAttachedToWindow(view: View) { view.post(register) }
            override fun onViewDetachedFromWindow(view: View) { view.removeCallbacks(register) }
        })
    }
    private val feedPages = java.util.Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val collectionViews = java.util.Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    @JvmStatic fun onCollectionCreated(view: View) {
        if (!collectionViews.add(view)) return
        val attachment = object : View.OnAttachStateChangeListener {
            private val register = Runnable {
                val root = view.rootView
                activities.firstOrNull { it.activity.get()?.window?.decorView === root }?.addCollection(view)
            }
            override fun onViewAttachedToWindow(v: View) { v.post(register) }
            override fun onViewDetachedFromWindow(v: View) { v.removeCallbacks(register) }
        }
        view.addOnAttachStateChangeListener(attachment)
        if (view.isAttachedToWindow) attachment.onViewAttachedToWindow(view)
    }

    @JvmStatic fun onTouchEvent(activity: Activity, event: MotionEvent) = delegate.onTouchEvent(activity, event)
    @JvmStatic fun blocksNativeInput(tab: View): Boolean {
        var view: View? = tab
        while (view != null) {
            if (delegate.isReplacingBottomBar(view)) return true
            view = view.parent as? View
        }
        return false
    }

    private fun findReadyBar(root: View): View? {
        if (root.javaClass.name == OUTER_BAR) {
            val inner = findInnerBar(root) ?: return null
            if (!sceneAccessResolved) {
                sceneAccessResolved = true
                // 40.7.0: LIZIZ(Integer, boolean dark, boolean transparent) stores dark in n.
                sceneDarkField = runCatching { inner.javaClass.getDeclaredField("n").apply { isAccessible = true } }.getOrNull()
            }
            val groupId = root.resources.getIdentifier("root_view", "id", root.context.packageName)
            val group = inner.findViewById<View>(groupId) as? ViewGroup ?: return null
            if (group.childCount < 2) return null
            innerBars[root] = WeakReference(inner)
            tabGroups[root] = WeakReference(group)
            return root
        }
        if (root is ViewGroup) repeat(root.childCount) { index ->
            findReadyBar(root.getChildAt(index))?.let { return it }
        }
        return null
    }
    private fun findInnerBar(root: View): View? {
        if (root.javaClass.name == INNER_BAR) return root
        if (root is ViewGroup) repeat(root.childCount) { index ->
            findInnerBar(root.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun nativeSceneDark(tab: View?): Boolean? {
        val field = sceneDarkField ?: return null
        var view = tab
        repeat(8) {
            val current = view ?: return null
            if (current.javaClass.name == INNER_BAR) {
                return try { field.getBoolean(current) }
                catch (_: IllegalAccessException) { sceneDarkField = null; null }
            }
            view = current.parent as? View
        }
        return null
    }

    private fun access(tab: View) = accessors.getOrPut(tab.javaClass) {
        TabAccess(
            runCatching { tab.javaClass.getMethod("getCurrentTabId") }.getOrNull(),
            runCatching { tab.javaClass.getMethod("getTabTitle") }.getOrNull(),
        )
    }
    private class TabAccess(val tabId: Method?, val title: Method?) {
        fun read(method: Method?, tab: View): Any? = try {
            method?.invoke(tab)
        } catch (_: ReflectiveOperationException) { null }
        fun id(tab: View): String? {
            if (!tabIds.containsKey(tab)) tabIds[tab] = read(tabId, tab) as? String
            return tabIds[tab]
        }
    }

    /** Settings thread posts only relevant changes; one reusable task per weak Activity. */
    private class ActivityState(activity: Activity) : Runnable {
        val activity = WeakReference(activity)
        private var enabled = ModuleSettings.moduleHooksEnabled && ModuleSettings.douyinFloatingNavigationEnabled
        private var lift = ModuleSettings.hyperGlassifyHiddenNavigationLift
        private var darkTheme = false
        private var videoPage = false
        private var pageKey: String? = null
        private var replacing = false
        private var dockBounds: ViewBackdropBounds? = null
        private val windowRef = WeakReference(activity.window)
        private val managedWindowFlags = android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        private val originalWindowFlags = activity.window.attributes.flags and managedWindowFlags
        private var immersive = false
        private val immersion = InjectedBottomNavigationImmersion(activity, true)
        private val feedClearance = DouyinFeedClearance(activity)

        init {
            DouyinFeedExpansion.registerExisting(activity)
            setImmersive(enabled)
            DouyinFeedExpansion.setEnabled(activity, enabled)
        }

        fun addFeedPage(page: View) = feedClearance.addPage(page)
        fun addProgress(view: View) = feedClearance.addProgress(view)
        fun addCollection(view: View) = feedClearance.addCollection(view)
        fun setVideoPage(video: Boolean, key: String?) {
            if (videoPage == video && pageKey == key) return
            videoPage = video
            pageKey = key
            refreshImmersion()
            setDividerHidden(replacing)
            applyPlaybackClearance()
        }
        fun setDockBounds(bounds: ViewBackdropBounds) {
            if (dockBounds == bounds) return
            dockBounds = bounds
            applyPlaybackClearance()
        }
        fun setReplacing(value: Boolean) {
            if (replacing == value) return
            replacing = value
            if (value) refreshImmersion()
            setDividerHidden(value)
            applyPlaybackClearance()
        }
        private fun applyPlaybackClearance() {
            feedClearance.update(replacing && videoPage, dockBounds)
        }

        fun setDarkTheme(value: Boolean) {
            if (darkTheme == value) return
            darkTheme = value
            refreshImmersion()
        }
        fun refreshImmersion() {
            if (!immersive) return
            immersion.ensureApplied()
            activity.get()?.let { host ->
                val controller = androidx.core.view.WindowCompat.getInsetsController(host.window, host.window.decorView)
                if (controller.isAppearanceLightNavigationBars == darkTheme)
                    controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
        private fun setImmersive(value: Boolean) {
            if (immersive == value) return
            val window = windowRef.get() ?: return
            immersive = value
            if (value) {
                DouyinWindowGuard.register(window)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                immersion.apply()
                refreshImmersion()
            } else {
                DouyinWindowGuard.unregister(window)
                immersion.dispose()
                window.setFlags(originalWindowFlags, managedWindowFlags)
            }
        }
        fun disposeClearance() { feedClearance.dispose(); activity.get()?.let(DouyinFeedExpansion::dispose); setImmersive(false) }

        private var dividerResolved = false
        private var divider = WeakReference<View>(null)
        private var dividerAlpha = 1f

        fun setDividerHidden(hidden: Boolean) {
            if (hidden && !dividerResolved) {
                dividerResolved = true
                activity.get()?.let { host ->
                    val id = host.resources.getIdentifier("cvf", "id", host.packageName)
                    val view = host.window.decorView.findViewById<View>(id)
                    divider = WeakReference(view)
                    dividerAlpha = view?.alpha ?: 1f
                }
            }
            divider.get()?.let { view ->
                val alpha = if (hidden) 0f else dividerAlpha
                if (view.alpha != alpha) view.alpha = alpha
            }
        }
        @Synchronized fun schedule(nextEnabled: Boolean, nextLift: Float) {
            if (enabled == nextEnabled && lift == nextLift) return
            enabled = nextEnabled
            lift = nextLift
            activity.get()?.window?.decorView?.let { root ->
                root.removeCallbacks(this)
                root.post(this)
            }
        }
        override fun run() {
            val host = activity.get() ?: return
            if (host.isDestroyed || host.isFinishing) return
            val enabled = ModuleSettings.moduleHooksEnabled && ModuleSettings.douyinFloatingNavigationEnabled
            setImmersive(enabled)
            DouyinFeedExpansion.setEnabled(host, enabled)
            if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.douyinFloatingNavigationEnabled) {
                delegate.dispose(host)
            } else if (host.hasWindowFocus()) {
                delegate.attach(host)
                delegate.refresh(host)
            }
        }
    }
}
