package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Resources
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Carrier
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Official 9.13.0: TabHost.getItemCount()/k(index) include the touch-only publish entry. */
internal object BilibiliFloatingNavigation {
    private const val TAB_HOST = "com.bilibili.lib.homepage.widget.TabHost"
    private var forwardingNativeInput = false
    private val tabMethods = WeakHashMap<Class<*>, Pair<Method, Method>>()
    private val homeStates = WeakHashMap<Activity, HomeState>()
    private val delegate = NativeViewBottomBarNavigation(
        NativeViewBottomBarTarget(
            logName = "Bilibili",
            testedVersion = "9.13.0",
            bottomBarClassNames = setOf(TAB_HOST),
            tabClassNames = emptySet(),
            fallbackLabels = listOf("首页", "动态", "发布", "会员购", "我的"),
            enabled = { ModuleSettings.bilibiliFloatingNavigationEnabled },
            useMiuixIcons = { true },
            useMonochromeIcons = { true },
            showBadges = { ModuleSettings.bilibiliNavigationBadgesEnabled },
            fallbackIcon = ::bilibiliMiuixIcon,
            selectedIndexMethodName = "getCurrentItem",
            resolveTabViews = ::tabViews,
            resolveTabLabel = { tab, _ ->
                if (publishView(tab) != null) "发布" else {
                    (tab.resourceView("tab_text") as? TextView)?.text?.toString()
                }
            },
            tabVisible = { label, _ ->
                BilibiliNavigationPolicy.isVisible(
                    label,
                    ModuleSettings.bilibiliHomeTabVisible,
                    ModuleSettings.bilibiliDynamicTabVisible,
                    ModuleSettings.bilibiliFollowTabVisible,
                    ModuleSettings.bilibiliMallTabVisible,
                    ModuleSettings.bilibiliMineTabVisible,
                    ModuleSettings.bilibiliPublishButtonVisible,
                )
            },
            isDetachedAction = { tab, _ -> publishView(tab) != null },
            onNativeTabClick = ::clickNativeTab,
            tabIconScale = { label, _ -> BilibiliNavigationPolicy.iconScale(label) },
            displayTabLabel = { label, _ -> BilibiliNavigationPolicy.displayLabel(label) },
            contentHostResourceName = "content",
            overlayAllowed = ::homeAllowsOverlay,
            nativeRefreshIntervalMs = 100L,
            useHardwareBackdrop = true,
            minimumCaptureIntervalMs = 0L,
            allowSoftwareBackdrop = false,
            matchDisplayBackdropRefreshRate = true,
            sampleBackdropOnSourceFrame = true,
            pixelCopyRetryDelayMs = 32L,
            requestImmersiveInsets = true,
            stableNavigationInset = true,
            retainNativeSuppressionDuringCover = true,
            detachedActionContainerColor = Color(0xFFFF6698),
            onChromeReplacement = { activity, replacing ->
                homeStates.getOrPut(activity) { HomeState(activity) }.immersion.setEnabled(replacing)
            },
        ),
    )

    @JvmStatic fun prepare(activity: Activity) = delegate.prepare(activity)
    @JvmStatic fun attach(activity: Activity) = delegate.attach(activity)
    @JvmStatic fun dispose(activity: Activity) {
        delegate.dispose(activity)
        homeStates.remove(activity)
    }
    @JvmStatic fun onTouchEvent(activity: Activity, event: MotionEvent) =
        delegate.onTouchEvent(activity, event)

    @JvmStatic fun setForeground(activity: Activity, foreground: Boolean) =
        delegate.setForeground(activity, foreground)

    @JvmStatic fun refresh(activity: Activity) = delegate.refresh(activity)

    @JvmStatic fun onHomeInsets(root: View) {
        homeStates.values.forEach { it.immersion.onNativeInsets(root) }
    }

    /** Called only from the app's own TabHost click and publish touch methods. */
    @JvmStatic fun blocksNativeInput(view: View): Boolean {
        if (forwardingNativeInput) return false
        var candidate: View? = view
        while (candidate != null) {
            if (candidate.javaClass.name == TAB_HOST) return delegate.isReplacingBottomBar(candidate)
            candidate = candidate.parent as? View
        }
        return false
    }

    private fun tabViews(bar: View): List<View> = runCatching {
        val (getCount, getTab) = tabMethods.getOrPut(bar.javaClass) {
            bar.javaClass.getMethod("getItemCount") to
                bar.javaClass.getMethod("k", Int::class.javaPrimitiveType)
        }
        val count = getCount.invoke(bar) as Int
        (0 until count.coerceIn(0, 8)).map { index -> getTab.invoke(bar, index) as View }
    }.getOrDefault(emptyList())

    private fun publishView(tab: View): View? = tab.resourceView("home_publish_icon")
        ?.takeIf { it.visibility == View.VISIBLE }

    private fun clickNativeTab(tab: View) {
        forwardingNativeInput = true
        try {
            forwardNativeTab(tab)
        } finally {
            forwardingNativeInput = false
        }
    }

    private fun forwardNativeTab(tab: View) {
        val publish = publishView(tab)
        if (publish == null) {
            tab.performClick()
            return
        }
        // HomeTabPublishView has no click listener: DOWN/UP invokes its own onTouch callback,
        // retaining the official publish panel, login gating and click analytics.
        val now = SystemClock.uptimeMillis()
        val x = publish.width / 2f
        val y = publish.height / 2f
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 16L, MotionEvent.ACTION_UP, x, y, 0)
        try {
            down.source = InputDevice.SOURCE_TOUCHSCREEN
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            publish.dispatchTouchEvent(down)
            publish.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun homeAllowsOverlay(activity: Activity): Boolean {
        val home = homeStates.getOrPut(activity) { HomeState(activity) }
        return BilibiliNavigationPolicy.overlayAllowed(
            !activity.isFinishing && !activity.isDestroyed,
            home.splashShowing(activity),
            home.startupShowing(activity),
            activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true,
        )
    }

    /** Cache the two official cover layers; no recursive page-tree search in the frame callback. */
    private class HomeState(activity: Activity) {
        val immersion = BilibiliHomeImmersion(activity)
        private val splashMethod = runCatching { activity.javaClass.getMethod("isSplashShowing") }.getOrNull()
        private var splash = WeakReference(activity.window.decorView.resourceView("splash_layout"))
        private var startup = WeakReference(activity.window.decorView.resourceView("startup_page"))

        fun splashShowing(activity: Activity): Boolean {
            val reported = splashMethod?.let { method ->
                runCatching { method.invoke(activity) as Boolean }.getOrDefault(true)
            } == true
            if (reported) return true
            var view = splash.get()
            if (view == null || !view.isAttachedToWindow) {
                view = activity.window.decorView.resourceView("splash_layout")
                splash = WeakReference(view)
            }
            return view?.isShown == true
        }

        fun startupShowing(activity: Activity): Boolean {
            var view = startup.get()
            if (view == null || !view.isAttachedToWindow) {
                view = activity.window.decorView.resourceView("startup_page")
                startup = WeakReference(view)
            }
            return view?.isShown == true
        }
    }
}

private val bilibiliResourceIds = WeakHashMap<Resources, MutableMap<String, Int>>()

private fun View.resourceView(name: String): View? {
    val id = bilibiliResourceIds.getOrPut(resources) { mutableMapOf() }.getOrPut(name) {
        resources.getIdentifier(name, "id", context.packageName)
    }
    return if (id != 0) findViewById(id) else null
}

private fun bilibiliMiuixIcon(label: String, index: Int): ImageVector =
    when (BilibiliNavigationPolicy.role(label)) {
        BilibiliNavigationPolicy.Role.HOME -> MiuixIcons.Home
        BilibiliNavigationPolicy.Role.DYNAMIC -> MiuixIcons.Messages
        BilibiliNavigationPolicy.Role.FOLLOW -> MiuixIcons.Messages
        BilibiliNavigationPolicy.Role.MALL -> MiuixIcons.Carrier
        BilibiliNavigationPolicy.Role.MINE -> MiuixIcons.ContactsCircle
        BilibiliNavigationPolicy.Role.PUBLISH -> MiuixIcons.Add
        BilibiliNavigationPolicy.Role.OTHER -> MiuixIcons.Home
    }
