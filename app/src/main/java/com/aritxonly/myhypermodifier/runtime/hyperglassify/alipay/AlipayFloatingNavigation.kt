package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TabHost
import android.widget.TabWidget
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home

/** Uses Alipay's own default two-state icons, never the badge Lottie/GIF renderer. */
internal object AlipayFloatingNavigation {
    private val sessions = WeakHashMap<Activity, Session>()
    private val bars = WeakHashMap<View, Session>()
    private val contentOwners = WeakHashMap<View, Session>()
    private val tabOwners = WeakHashMap<View, Session>()
    private val icons = WeakHashMap<View, IconEntry>()
    private val main = Handler(Looper.getMainLooper())
    private val refreshQueued = AtomicBoolean()
    private lateinit var metadata: Metadata
    private val lastSettings = AtomicLong(Long.MIN_VALUE)

    private class Session(barView: View, tabWidget: TabWidget, contentView: View, tabManager: Any, backgroundView: View?) {
        val bar: WeakReference<View> = WeakReference(barView)
        val tabs: WeakReference<TabWidget> = WeakReference(tabWidget)
        val content: WeakReference<View> = WeakReference(contentView)
        val manager: WeakReference<Any> = WeakReference(tabManager)
        val background: WeakReference<View> = WeakReference(backgroundView)
        var backgroundAlpha = backgroundView?.alpha ?: 1f
        var foreground = true
        var classic = false
        var dark = false
        var replacing = false
        var styleRefreshQueued = false
        val margin = AlipayContentMargin((contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0)
    }

    private class Metadata(loader: ClassLoader) {
        val fragment = loader.loadClass("com.alipay.android.launcher.TabLauncherFragment")
        val tab = loader.loadClass("com.alipay.android.launcher.tab.TabWidgetItemView")
        private val manager = loader.loadClass("com.alipay.android.launcher.tab.TabBarManager")
        val tabWidget = fragment.getDeclaredField("tabWidget").apply { isAccessible = true }
        val bar = fragment.getDeclaredField("tabBarContainerView").apply { isAccessible = true }
        val content = fragment.getDeclaredField("tabcontent").apply { isAccessible = true }
        val managerField = fragment.getDeclaredField("tabBarManager").apply { isAccessible = true }
        val backgroundId = loader.loadClass("com.alipay.android.tablauncher.uientry.R\$id")
            .getField("tab_bar_bg_iv")
        val defaultIcon = tab.getMethod("getTabDefaultIcon")
        val text = tab.getMethod("getTextView")
        val badge = tab.getMethod("getBadgeView")
        val badgeStyle = loader.loadClass("com.alipay.mobile.mpass.badge.ui.BadgeView").getMethod("getBadgeStyle")
        val badgeNone = loader.loadClass("com.alipay.mobile.mpass.badge.model.BadgeStyle").getField("NONE").get(null)
        val normalMode = loader.loadClass("com.alipay.mobile.tablauncher.util.TLCommonUtils")
            .getMethod("isTabBarNormalMode")
        val aiMode = loader.loadClass("com.alipay.android.launcher.tab.AiTabManager")
            .getMethod("JinniModeBarStyle")
        val dark = manager.getMethod("isTabBarDarkMode")
        val restoreMargin = manager.getMethod("setTabContentBottomMargin")
    }

    private data class IconEntry(val configuration: Configuration, val dark: Boolean, val icons: NativeTabIconPair?)

    private val delegate = NativeViewBottomBarNavigation(NativeViewBottomBarTarget(
        logName = "Alipay classic",
        testedVersion = "12.12.36.8000 / 212370",
        bottomBarClassNames = emptySet(),
        resolveBottomBar = { root ->
            val tabs = root.findViewById<TabWidget>(android.R.id.tabs)
            sessions.values.firstOrNull { it.tabs.get() === tabs && it.classic }?.bar?.get()
        },
        tabClassNames = emptySet(),
        resolveTabViews = { bar ->
            val session = bars[bar]
            val tabs = session?.tabs?.get()
            if (session == null || tabs == null || !session.classic) emptyList() else {
                val children = (0 until tabs.childCount).map { tabs.getChildAt(it).also { tab -> tabOwners[tab] = session } }
                // Fail open to the original dock if this version cannot supply recognizable icons.
                children.takeIf { it.size in 2..6 && it.all { tab -> metadata.tab.isInstance(tab) && staticIcons(tab, session.dark) != null } }
                    ?: emptyList()
            }
        },
        resolveTabBadge = { tab ->
            (metadata.badge.invoke(tab) as? View)?.let {
                it.visibility == View.VISIBLE && it.alpha > 0f && it.width > 0 && it.height > 0 &&
                    metadata.badgeStyle.invoke(it)?.let { style -> style !== metadata.badgeNone } == true
            } == true
        },
        resolveTabLabel = { tab, _ -> (metadata.text.invoke(tab) as? TextView)?.text?.toString() },
        resolveTabIcons = { tab, _ -> staticIcons(tab, tabOwners[tab]?.dark ?: false) },
        // Alipay's custom scroll/refresh containers own their end-of-list geometry.
        // Do not add generic bottom padding or change their clipToPadding: continued
        // pulling at the bottom otherwise exposes a rectangular reservation background.
        // The content reference below is only a GPU source; margin ownership is separate.
        resolveSelectedIndex = { bar, _ ->
            val tabs = bars[bar]?.tabs?.get()
            var parent = tabs?.parent
            while (parent is View && parent !is TabHost) parent = parent.parent
            (parent as? TabHost)?.currentTab ?: -1
        },
        fallbackLabels = listOf("首页", "理财", "生活", "消息", "我的"),
        enabled = { enabled() },
        useMiuixIcons = { false },
        useMonochromeIcons = { false },
        showBadges = { true },
        fallbackIcon = { _, _ -> MiuixIcons.Home },
        nativeBarVisible = { bars[it]?.let { session -> session.foreground && session.classic } == true },
        resolveDarkTheme = { activity, _, _, _ -> sessions[activity]?.dark },
        nativeRefreshIntervalMs = 100L,
        stableNavigationInset = true,
        // Same-window retained display list, as used by Douyin/Spotify. Never read pixels back.
        useNativeGpuBackdrop = true,
        resolveGpuSource = { bars[it]?.content?.get() },
        inTreeOverlay = true,
        useHardwareBackdrop = false,
        allowSoftwareBackdrop = false,
        minimumCaptureIntervalMs = 250L,
        sampleBackdropOnSourceFrame = false,
        // Alipay's dark classic theme may keep a fitted, opaque navigation-bar region.
        // Own bottom-only edge-to-edge while replacing the dock, with lifecycle restoration.
        manageWindowImmersion = true,
        requestImmersiveInsets = true,
        onChromeReplacement = { activity, replacing ->
            sessions[activity]?.let { session ->
                val background = session.background.get()
                if (session.replacing != replacing) {
                    if (replacing) session.backgroundAlpha = background?.alpha ?: 1f
                    else background?.alpha = session.backgroundAlpha
                    session.replacing = replacing
                    session.content.get()?.let { content ->
                        val params = content.layoutParams as? ViewGroup.MarginLayoutParams
                        if (params != null) {
                            val bottom = session.margin.replace(replacing, params.bottomMargin)
                            if (params.bottomMargin != bottom) {
                                params.bottomMargin = bottom
                                content.layoutParams = params
                            }
                        }
                    }
                    if (!replacing && !activity.isDestroyed) {
                        session.manager.get()?.let { runCatching { metadata.restoreMargin.invoke(it) } }
                    }
                }
                if (replacing && background != null && background.alpha != 0f) background.alpha = 0f
            }
        },
    ))

    @JvmStatic fun initialize(loader: ClassLoader) {
        metadata = Metadata(loader)
        ModuleSettings.onChanged {
            val signature = (ModuleSettings.hyperGlassifyHiddenNavigationLift.toRawBits().toLong() shl 1) or
                (if (enabled()) 1L else 0L)
            if (lastSettings.getAndSet(signature) != signature && refreshQueued.compareAndSet(false, true)) main.post {
                refreshQueued.set(false)
                sessions.keys.toList().forEach { activity ->
                    if (enabled() && sessions[activity]?.let { it.foreground && it.classic } == true) {
                        delegate.attach(activity)
                        delegate.refresh(activity)
                    } else delegate.dispose(activity)
                }
            }
        }
    }

    @JvmStatic fun created(fragment: Any, activity: Activity) {
        dispose(activity)
        val tabs = metadata.tabWidget.get(fragment) as? TabWidget ?: return
        val bar = metadata.bar.get(fragment) as? View ?: return
        val content = metadata.content.get(fragment) as? View ?: return
        val manager = metadata.managerField.get(fragment) ?: return
        val session = Session(bar, tabs, content, manager, bar.rootView.findViewById(metadata.backgroundId.getInt(null)))
        sessions[activity] = session
        bars[bar] = session
        contentOwners[content] = session
        updateStyle(session)
        if (session.classic && enabled()) delegate.attach(activity)
    }

    @JvmStatic fun resume(activity: Activity) {
        val session = sessions[activity] ?: return
        session.foreground = true
        updateStyle(session)
        if (session.classic && enabled()) {
            delegate.attach(activity)
            delegate.setForeground(activity, true)
            delegate.refresh(activity)
        } else delegate.dispose(activity)
    }

    @JvmStatic fun pause(activity: Activity) {
        sessions[activity]?.foreground = false
        // Remove pending attachment and all capture callbacks while another Activity covers home.
        delegate.dispose(activity)
    }

    @JvmStatic fun dispose(activity: Activity) {
        delegate.dispose(activity)
        sessions.remove(activity)?.let { session ->
            session.bar.get()?.let(bars::remove)
            session.content.get()?.let(contentOwners::remove)
            tabOwners.entries.removeAll { it.value === session }
            session.tabs.get()?.let { tabs -> repeat(tabs.childCount) { icons.remove(tabs.getChildAt(it)) } }
        }
    }

    @JvmStatic fun styleChanged(manager: Any) {
        sessions.entries.firstOrNull { it.value.manager.get() === manager }?.let { (activity, session) ->
            if (session.styleRefreshQueued) return
            session.styleRefreshQueued = true
            val owner = WeakReference(activity)
            main.post {
                session.styleRefreshQueued = false
                val current = owner.get() ?: return@post
                if (sessions[current] !== session) return@post
                updateStyle(session)
                if (session.classic && session.foreground && enabled()) {
                    delegate.attach(current)
                    delegate.refresh(current)
                } else delegate.dispose(current)
            }
        }
    }

    @JvmStatic fun invalidateIcon(tab: View) { icons.remove(tab) }

    private fun updateStyle(session: Session) {
        val manager = session.manager.get() ?: return
        // "YoungStyle" is icon typography and is also used by classic mode.
        // Gate on the app's actual normal/Jinni mode instead.
        session.classic = runCatching {
            metadata.normalMode.invoke(null) == true && metadata.aiMode.invoke(null) == false
        }.getOrDefault(false)
        session.dark = runCatching { metadata.dark.invoke(manager) == true }.getOrDefault(false)
    }

    /** Called at Alipay's own margin write, before any raised layout can be submitted. */
    @JvmStatic fun nativeBottomMargin(content: View, requested: Int): Int {
        val session = contentOwners[content] ?: return requested
        return session.margin.nativeRequest(requested)
    }

    private fun staticIcons(tab: View, dark: Boolean): NativeTabIconPair? {
        val configuration = tab.resources.configuration
        icons[tab]?.takeIf { it.configuration == configuration && it.dark == dark }?.let { return it.icons }
        val pair = runCatching {
            val defaults = metadata.defaultIcon.invoke(tab) as? Pair<*, *> ?: return@runCatching null
            val normal = defaults.first as? Drawable ?: return@runCatching null
            val selected = defaults.second as? Drawable ?: return@runCatching null
            val drawable = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), selected)
                addState(intArrayOf(), normal)
            }
            val image = ImageView(tab.context).apply { setImageDrawable(drawable) }
            // A temporary snapshotter prevents app-owned Drawables/Context from entering static caches.
            NativeTabIconSnapshotter(
                tab.resources, tab.context.theme, maxBitmapSizePx = 96, includeMonochrome = false,
            ).snapshot(tab, image, false)
        }.getOrNull()
        icons[tab] = IconEntry(Configuration(configuration), dark, pair)
        return pair
    }

    private fun enabled() = ModuleSettings.moduleHooksEnabled && ModuleSettings.alipayFloatingNavigationEnabled
}
