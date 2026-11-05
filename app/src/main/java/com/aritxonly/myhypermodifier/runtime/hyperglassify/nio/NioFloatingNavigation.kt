package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Only the main UI process loads this object. View references never outlive their Activity. */
internal object NioFloatingNavigation {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = WeakHashMap<Activity, Session>()
    private val bars = WeakHashMap<View, Session>()
    private val tabOwners = WeakHashMap<View, Session>()
    private class Session(activity: Activity) {
        val activity = WeakReference(activity)
        var foreground = false
        var bar = WeakReference<View>(null)
        var content = WeakReference<View>(null)
        var labelId = 0
        var iconId = 0
        var reservation: NioContentReservation? = null
        var insetContainers: List<NioContentReservation> = emptyList()
        val edgeToEdge = NioEdgeToEdge(activity.window)
        var configuration: Configuration? = null
        val icons = HashMap<String, NativeTabIconPair?>()
        var enabledSnapshot = enabled()
        var selectedIndex = 0
        var ready = false
        val recoveryPolicy = NavigationRefreshPolicy(250L)
        var refreshQueued = false
        val refresh = Runnable {
            refreshQueued = false
            val owner = this.activity.get() ?: return@Runnable
            if (owner.isDestroyed || owner.isFinishing) return@Runnable
            delegate.dispose(owner)
            if (enabled() && foreground) delegate.attach(owner)
        }
    }
    private val delegate = NativeViewBottomBarNavigation(NativeViewBottomBarTarget(
        logName = "NIO", testedVersion = "6.9.6 (60906)",
        bottomBarClassNames = emptySet(), tabClassNames = emptySet(),
        resolveBottomBar = ::findReadyBar,
        resolveTabViews = { bar -> nativeTabs(bar) },
        resolveTabLabel = { tab, _ -> tabOwners[tab]?.let { tab.findViewById<TextView>(it.labelId)?.text?.toString() } },
        resolveSelectedIndex = { bar, tabs ->
            bars[bar]?.let { session ->
                val selected = NioNavigationViews.selectedIndex(tabs, session.labelId)
                if (selected >= 0) session.selectedIndex = selected
                session.selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
            } ?: 0
        },
        onNativeTabClick = { tab ->
            tab.performClick()
            tabOwners[tab]?.let { session ->
                session.bar.get()?.let { bar ->
                    val index = nativeTabs(bar).indexOf(tab)
                    if (index >= 0) session.selectedIndex = index
                }
            }
        },
        resolveTabIcons = { tab, _ -> tabOwners[tab]?.let { session ->
            val label = tab.findViewById<TextView>(session.labelId)?.text?.toString().orEmpty()
            session.icons[label]
        } },
        nativeBarVisible = { bar ->
            val session = bars[bar]
            if (session != null) {
                if (session.configuration != bar.resources.configuration) {
                    session.ready = false
                    queueRefresh(session)
                } else if (!session.ready && session.recoveryPolicy.shouldRefresh(SystemClock.uptimeMillis(), false)) {
                    // Recheck only while the app itself draws; no background polling/task.
                    nativeTabs(bar)
                }
            }
            session?.ready == true
        },
        // NIO has a light-only UI, even when Android is configured for night mode.
        resolveDarkTheme = { _, _, _, _ -> false },
        tabVisible = { label, _ -> NioNavigationPolicy.isVisible(label, visibilityMask()) },
        fallbackLabels = emptyList(), enabled = ::enabled,
        useMiuixIcons = { false }, useMonochromeIcons = { false }, showBadges = { false },
        // All entries must have app-owned icons before replacement becomes visible.
        fallbackIcon = { _, _ -> emptyIcon },
        resolveContentHost = { bars[it]?.content?.get() },
        resolveGpuSource = { bars[it]?.content?.get() },
        requestImmersiveInsets = true,
        lightNavigationBars = true,
        onChromeReplacement = { activity, replacing -> sessions[activity]?.let { session ->
            session.edgeToEdge.setEnabled(replacing)
            session.reservation?.replace(replacing)
            session.insetContainers.forEach { it.replace(replacing) }
        } },
        nativeRefreshIntervalMs = 48L,
        refreshContentPaddingEverySync = false,
        useNativeGpuBackdrop = true, inTreeOverlay = true, allowSoftwareBackdrop = false,
        composeContext = { InjectedNavigationContext(it) },
    ))
    private val emptyIcon by lazy { ImageVector.Builder("NIO missing native icon", 28.dp, 28.dp, 28f, 28f).build() }
    @Volatile private var lastEnabled = enabled()
    @Volatile private var lastVisibilityMask = visibilityMask()
    private val settingsRefresh = Runnable {
        val currentEnabled = enabled()
        sessions.values.forEach { session ->
            if (session.enabledSnapshot != currentEnabled) {
                session.enabledSnapshot = currentEnabled
                queueRefresh(session)
            } else if (session.foreground && currentEnabled) {
                session.activity.get()?.let(delegate::refresh)
            }
        }
    }

    init {
        ModuleSettings.onChanged {
            val next = enabled()
            val mask = visibilityMask()
            if (lastEnabled != next || lastVisibilityMask != mask) {
                lastEnabled = next
                lastVisibilityMask = mask
                main.removeCallbacks(settingsRefresh)
                main.post(settingsRefresh)
            }
        }
    }

    private fun queueRefresh(session: Session) {
        if (session.refreshQueued) return
        session.refreshQueued = true
        main.post(session.refresh)
    }

    private fun findReadyBar(root: View): View? {
        val activity = sessions.keys.firstOrNull { it.window.decorView === root } ?: return null
        val session = sessions[activity] ?: return null
        val bar = root.findViewById<View>(NioNavigationViews.resourceId(root, "home_bottom_tab_layout")) ?: return null
        if (bar.findViewById<View>(NioNavigationViews.resourceId(root, "home_bottom_scroll_tab")) == null) return null
        session.labelId = NioNavigationViews.resourceId(root, "custom_tab_tv")
        session.iconId = NioNavigationViews.resourceId(root, "custom_tab_img")
        val content = root.findViewById<View>(NioNavigationViews.resourceId(root, "home_content_layout")) ?: return null
        if (session.content.get() !== content) {
            session.reservation = NioContentReservation(content)
            session.insetContainers = listOfNotNull(
                root.findViewById<View>(NioNavigationViews.resourceId(root, "rl_home_root")),
                root.findViewById<View>(android.R.id.content),
            ).distinct().filter { it !== content }.map(::NioContentReservation)
        }
        session.content = WeakReference(content)
        session.bar = WeakReference(bar)
        bars[bar] = session
        val tabs = nativeTabs(bar, checkIcons = false)
        session.ready = false
        if (tabs.size !in 3..7) return null
        // Resolve/rasterize resources once per label/configuration, never during frame sync.
        val configuration = root.resources.configuration
        if (session.configuration != configuration) {
            session.icons.clear()
            session.configuration = Configuration(configuration)
        }
        for (tab in tabs) {
            val label = tab.findViewById<TextView>(session.labelId)?.text?.toString().orEmpty()
            if (!session.icons.containsKey(label)) session.icons[label] = NioNavigationViews.icons(tab, label)
            if (session.icons[label] == null) return null
        }
        session.ready = true
        return bar
    }

    private fun nativeTabs(bar: View, checkIcons: Boolean = true): List<View> {
        val session = bars[bar] ?: return emptyList()
        // Restricted to the small bottom strip, at most once per 48 ms during rendering.
        return NioNavigationViews.tabs(bar, session.labelId, session.iconId).also { tabs ->
            tabs.forEach { tabOwners[it] = session }
            if (checkIcons) {
                session.ready = tabs.size in 3..7
                for (tab in tabs) {
                    val label = tab.findViewById<TextView>(session.labelId)?.text?.toString().orEmpty()
                    if (session.icons[label] == null) session.ready = false
                    if (!session.icons.containsKey(label)) queueRefresh(session)
                }
            }
        }
    }

    @JvmStatic fun resume(activity: Activity) {
        val session = sessions.getOrPut(activity) { Session(activity) }
        session.foreground = true
        delegate.setForeground(activity, true)
        queueRefresh(session)
    }
    @JvmStatic fun pause(activity: Activity) {
        sessions[activity]?.let { it.foreground = false; main.removeCallbacks(it.refresh); it.refreshQueued = false }
        delegate.setForeground(activity, false)
        // Cancel late attachment retries while another screen covers HomeActivity.
        delegate.dispose(activity)
    }
    @JvmStatic fun dispose(activity: Activity) {
        delegate.dispose(activity)
        sessions.remove(activity)?.let { session ->
            main.removeCallbacks(session.refresh)
            session.reservation?.replace(false)
            session.insetContainers.forEach { it.replace(false) }
            session.bar.get()?.let(bars::remove)
            tabOwners.entries.removeAll { it.value === session }
        }
    }
    private fun visibilityMask() = NioNavigationPolicy.mask(
        ModuleSettings.nioDiscoverTabVisible, ModuleSettings.nioFriendsTabVisible,
        ModuleSettings.nioCarTabVisible, ModuleSettings.nioMallTabVisible, ModuleSettings.nioMineTabVisible,
    )
    private fun enabled() = ModuleSettings.moduleHooksEnabled && ModuleSettings.nioFloatingNavigationEnabled
}
