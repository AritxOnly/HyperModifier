package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TabHost
import android.widget.TabWidget
import android.widget.TextView
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import java.lang.reflect.Method
import java.lang.ref.WeakReference

internal enum class SocialNavigationKind {
    WeChat,
    QQ,
}

internal data class SocialNavigationSpec(
    val packageName: String,
    val kind: SocialNavigationKind,
    val logName: String,
    val testedVersion: String,
    val activityClassNames: Set<String>,
)

/** Focused adapters for the two messaging apps; neither target installs generic View hooks. */
internal object SocialNavigationTargets {
    private val specs = listOf(
        SocialNavigationSpec(
            packageName = "com.tencent.mm",
            kind = SocialNavigationKind.WeChat,
            logName = "微信",
            testedVersion = "8.0.78",
            activityClassNames = setOf("com.tencent.mm.ui.LauncherUI"),
        ),
        SocialNavigationSpec(
            packageName = "com.tencent.mobileqq",
            kind = SocialNavigationKind.QQ,
            logName = "QQ",
            testedVersion = "9.1.25",
            activityClassNames = setOf("com.tencent.mobileqq.activity.SplashActivity"),
        ),
    )
    private val delegates = HashMap<String, SocialNavigationDelegate>()

    fun specFor(packageName: String): SocialNavigationSpec? =
        specs.firstOrNull { it.packageName == packageName }

    fun delegateFor(packageName: String): SocialNavigationDelegate? {
        val spec = specFor(packageName) ?: return null
        return synchronized(delegates) {
            delegates.getOrPut(packageName) {
                SocialNavigationDelegate(createTarget(spec))
            }
        }
    }

    fun enabledFor(packageName: String): Boolean = when (packageName) {
        "com.tencent.mm" -> ModuleSettings.weChatFloatingNavigationEnabled
        "com.tencent.mobileqq" -> ModuleSettings.qqFloatingNavigationEnabled
        else -> false
    }

    private fun createTarget(spec: SocialNavigationSpec): NativeViewBottomBarTarget {
        val qqDrawerState = if (spec.kind == SocialNavigationKind.QQ) {
            QQDrawerStateProbe()
        } else {
            null
        }
        val fallbackLabels = when (spec.kind) {
            SocialNavigationKind.WeChat -> listOf("微信", "通讯录", "发现", "我")
            SocialNavigationKind.QQ -> listOf("消息", "联系人", "动态", "我的")
        }
        val showBadges = {
            when (spec.kind) {
                SocialNavigationKind.WeChat -> ModuleSettings.weChatNavigationBadgesEnabled
                SocialNavigationKind.QQ -> ModuleSettings.qqNavigationBadgesEnabled
            }
        }
        return NativeViewBottomBarTarget(
            logName = spec.logName,
            testedVersion = spec.testedVersion,
            bottomBarClassNames = setOf(
                when (spec.kind) {
                    SocialNavigationKind.WeChat -> "com.tencent.mm.ui.LauncherUIBottomTabView"
                    // QQTabHost also owns page content. The TabWidget is the only safe alpha target.
                    SocialNavigationKind.QQ -> "com.tencent.mobileqq.widget.QQTabWidget"
                },
                if (spec.kind == SocialNavigationKind.QQ)
                    "com.tencent.mobileqq.widget.QQTabLayout" else "",
                if (spec.kind == SocialNavigationKind.QQ)
                    "com.tencent.biz.qui.quipagetabbar.QUIPageTabBar" else "",
            ),
            tabClassNames = emptySet(),
            resolveBottomBar = { root ->
                when (spec.kind) {
                    SocialNavigationKind.QQ -> findQQTabWidget(root)
                    SocialNavigationKind.WeChat -> null
                }
            },
            resolveNativeChromeShells = { bar ->
                if (spec.kind == SocialNavigationKind.QQ) {
                    qqNavigationChromeShells(bar)
                } else {
                    emptyList()
                }
            },
            fallbackLabels = fallbackLabels,
            enabled = {
                ModuleSettings.moduleHooksEnabled && enabledFor(spec.packageName)
            },
            useMiuixIcons = { true },
            useMonochromeIcons = { true },
            showBadges = showBadges,
            fallbackIcon = { label, _ -> socialFallbackIcon(label) },
            resolveTabViews = { bar ->
                directSocialTabViews(bar).takeIf { it.size > 1 }
                    ?: bar.geometricTabViews(fallbackLabels.size)
            },
            resolveTabLabel = { tab, index ->
                tab.findAllTextViews()
                    .asSequence()
                    .mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                    .firstOrNull()
                    ?: tab.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)
                    ?: fallbackLabels.getOrNull(index)
            },
            resolveSelectedIndex = { bar, tabs ->
                when (spec.kind) {
                    SocialNavigationKind.WeChat ->
                        bar.invokeSocialInt("getCurIdx")?.takeIf { it in tabs.indices }
                    SocialNavigationKind.QQ ->
                        bar.invokeSocialInt("getCurrentTab")
                            ?.takeIf { it in tabs.indices }
                            ?: bar.invokeSocialInt("getCurrentPosition")
                                ?.takeIf { it in tabs.indices }
                            ?: bar.findSocialTabHostAncestor()?.currentTab
                                ?.takeIf { it in tabs.indices }
                } ?: tabs.indexOfFirst(View::isSelected).takeIf { it >= 0 }
            },
            onNativeTabClick = { tab ->
                performNativeNavigationClick(tab)
            },
            resolveDarkTheme = { activity, _, _, _ -> HostThemeResolver.resolve(activity) },
            nativeBarVisible = { bar ->
                bar.isShown && bar.height > 0 && qqDrawerState?.isOpen(bar) != true
            },
            nativeRefreshIntervalMs =
                if (spec.kind == SocialNavigationKind.QQ) 240L else 160L,
            minimumCaptureIntervalMs = 100L,
            allowSoftwareBackdrop = spec.kind == SocialNavigationKind.QQ,
            // QQ's light page already supplies its own bottom surface. The shared light-mode
            // edge fade becomes a visible gray wash behind the floating dock.
            bottomEdgeMask = spec.kind != SocialNavigationKind.QQ,
            showNavigationShadowInLight = spec.kind != SocialNavigationKind.QQ,
            sampleBackdropOnSourceFrame = true,
            useHardwareBackdrop = true,
            useNativeGpuBackdrop = false,
            // QQ rebuilds its Decor after first frame; a tree child is removed with the old shell.
            inTreeOverlay = false,
            pixelCopyRetryDelayMs = 1_000L,
            stableNavigationInset = true,
            lateAttachWindowMs = 30_000L,
            manageWindowImmersion = false,
            // The application-panel window can briefly move focus away from its own Activity.
            // Lifecycle foreground state already prevents this overlay from living over another
            // app, so consulting Activity.hasWindowFocus() makes the new QQ panel hide itself.
            overlayAllowed = { activity -> !activity.isFinishing && !activity.isDestroyed },
            retainNativeSuppressionDuringCover = true,
            onAttachDiagnostic = { detail ->
                if (detail == "floating navigation attached") {
                    HookDiagnostics.available(spec.packageName, spec.logName + "悬浮底栏")
                } else {
                    HookDiagnostics.observed(
                        spec.packageName,
                        spec.logName + "悬浮底栏",
                        detail,
                    )
                }
            },
        )
    }
}

internal class SocialNavigationDelegate(target: NativeViewBottomBarTarget) {
    private val navigation = NativeViewBottomBarNavigation(target)

    fun prepare(activity: Activity) = navigation.prepare(activity)
    fun attach(activity: Activity) = navigation.attach(activity)
    fun attach(activity: Activity, preferredBar: View?) =
        navigation.attach(activity, preferredBar)
    fun dispose(activity: Activity) = navigation.dispose(activity)
    fun setForeground(activity: Activity, foreground: Boolean) =
        navigation.setForeground(activity, foreground)
    fun refresh(activity: Activity) = navigation.refresh(activity)
    fun onTouchEvent(activity: Activity, event: android.view.MotionEvent) =
        navigation.onTouchEvent(activity, event)
    fun hasHost(activity: Activity) = navigation.hasHost(activity)
    fun hasNavigationTarget(activity: Activity) = navigation.hasNavigationTarget(activity)
}

/**
 * QQ keeps its side drawer mounted behind the current page. Its full-screen drawer panel moves
 * from a cached negative translation to zero as the gesture begins, so no polling, capture or
 * repeated reflection is needed after the first bind.
 */
internal class QQDrawerStateProbe(
    private val drawerClassName: String = QQ_DRAWER_CLASS,
    private val panelClassName: String = QQ_DRAWER_PANEL_CLASS,
) {
    private var drawerReference: WeakReference<View>? = null
    private var panelReference: WeakReference<View>? = null
    private var openMethod: Method? = null
    private var animatingMethod: Method? = null
    private var closedTranslationX = Float.NaN

    fun isOpen(bar: View): Boolean {
        val drawer = resolveDrawer(bar) ?: return false
        val panel = resolvePanel(drawer) ?: return false
        if (panel.width <= 0) return false
        val translationX = panel.translationX
        if (closedTranslationX.isNaN()) {
            val drawerOpen = drawer.invokeQQDrawerBoolean(openMethod) ||
                drawer.invokeQQDrawerBoolean(animatingMethod)
            closedTranslationX = when {
                drawerOpen -> -panel.width.toFloat()
                translationX < -0.5f -> translationX
                else -> return false
            }
        } else if (translationX < closedTranslationX - 0.5f) {
            // Orientation or window metrics can change the fully hidden offset.
            closedTranslationX = translationX
        }
        return translationX > closedTranslationX + 0.5f
    }

    private fun resolveDrawer(bar: View): View? {
        val cached = drawerReference?.get()
        if (cached?.isAttachedToWindow == true) return cached
        var current: View? = bar
        repeat(16) {
            if (current?.javaClass?.name == drawerClassName) {
                drawerReference = WeakReference(current)
                openMethod = current.findQQDrawerBooleanMethod("q")
                animatingMethod = current.findQQDrawerBooleanMethod("p")
                return current
            }
            current = current?.parent as? View
        }
        return null
    }

    private fun resolvePanel(drawer: View): View? {
        val cached = panelReference?.get()
        if (cached?.parent === drawer) return cached
        val directChild = (drawer as? ViewGroup)?.let { group ->
            (0 until group.childCount)
                .map { group.getChildAt(it) }
                .firstOrNull { it.javaClass.name == panelClassName }
        }
        if (directChild != null) {
            panelReference = WeakReference(directChild)
            return directChild
        }
        var match: View? = null
        fun visit(view: View) {
            if (match != null) return
            if (view.javaClass.name == panelClassName) {
                match = view
                return
            }
            (view as? ViewGroup)?.let { group ->
                repeat(group.childCount) { visit(group.getChildAt(it)) }
            }
        }
        visit(drawer)
        return match?.also { panelReference = WeakReference(it) }
    }

    private fun View.invokeQQDrawerBoolean(method: Method?): Boolean =
        runCatching { method?.invoke(this) as? Boolean }.getOrNull() == true

    private fun View.findQQDrawerBooleanMethod(name: String): Method? =
        runCatching { javaClass.getMethod(name) }
            .getOrNull()
            ?.takeIf { it.returnType == Boolean::class.javaPrimitiveType }

    companion object {
        private const val QQ_DRAWER_CLASS =
            "com.tencent.mobileqq.activity.recent.DrawerFrame"
        private const val QQ_DRAWER_PANEL_CLASS =
            "com.tencent.mobileqq.widget.QQSettingMeRelativeLayout"
    }
}

private fun directSocialTabViews(bar: View): List<View> {
    materialTabViews(bar)?.let { return it }
    if (bar is TabWidget) {
        return buildList {
            repeat(bar.tabCount) { index ->
                bar.getChildTabViewAt(index)
                    ?.takeIf { it.visibility != View.GONE }
                    ?.let(::add)
            }
        }
    }
    val container = (bar as? ViewGroup)
        ?.let(::visibleChildren)
        ?.firstOrNull { it is ViewGroup && it.childCount in 2..8 }
        as? ViewGroup
        ?: return emptyList()
    return visibleChildren(container)
        .filter { tab -> tab.isClickable || tab.hasClickableDescendant() }
        .take(8)
}

private fun materialTabViews(bar: View): List<View>? {
    val isMaterialTabs = generateSequence(bar.javaClass as Class<*>?) { it.superclass }
        .any { it.name == "com.google.android.material.tabs.TabLayout" }
    if (!isMaterialTabs) return null
    return runCatching {
        val count = bar.javaClass.getMethod("getTabCount").invoke(bar) as Int
        buildList {
            repeat(count.coerceIn(0, 8)) { index ->
                val tab = bar.javaClass.getMethod("getTabAt", Int::class.javaPrimitiveType)
                    .invoke(bar, index) ?: return@repeat
                val view = tab.javaClass.getMethod("getView").invoke(tab) as? View
                    ?: return@repeat
                if (view.visibility != View.GONE) add(view)
            }
        }
    }.getOrNull()
}

internal fun findQQTabWidget(root: View): View? {
    val exact = ArrayList<View>()
    val tabWidgets = ArrayList<View>()
    val exactClasses = setOf(
        "com.tencent.mobileqq.widget.QQTabWidget",
        "com.tencent.mobileqq.widget.QQTabLayout",
        "com.tencent.mobileqq.widget.CustomedTabWidget",
        "com.tencent.biz.qui.quipagetabbar.QUIPageTabBar",
    )

    fun collect(view: View) {
        if (view.javaClass.name in exactClasses) exact += view
        val host = view as? TabHost
        val widget = host?.tabWidget
        if (widget != null && widget.tabCount >= 2) tabWidgets += widget
        if (view is ViewGroup) {
            repeat(view.childCount) { collect(view.getChildAt(it)) }
        }
    }
    collect(root)

    return (exact + tabWidgets)
        .distinct()
        .filter { candidate -> qqCandidateTabCount(candidate) >= 2 }
        .maxByOrNull { widget ->
            val location = IntArray(2)
            widget.getLocationOnScreen(location)
            var score = location[1].toLong() + widget.height
            if (widget.isAttachedToWindow) score += 1_000_000L
            if (widget.isShown && widget.height > 0) score += 10_000_000L
            score
        }
}

/**
 * QQ wraps the actual tab implementation in a compact single-child chrome container. Hiding only
 * the inner QUIPageTabBar leaves that wrapper's light background visible on light pages.
 */
private fun qqNavigationChromeShells(bar: View): List<View> {
    val barHeight = bar.height.takeIf { it > 0 } ?: return emptyList()
    val density = bar.resources.displayMetrics.density
    val maximumHeight = barHeight + (24f * density).toInt()
    val minimumWidth = (bar.width * 0.8f).toInt().coerceAtLeast(1)
    val parent = bar.parent as? ViewGroup
    val siblingBlurWrappers = if (parent == null) {
        emptyList()
    } else {
        buildList {
            repeat(parent.childCount) { index ->
                val sibling = parent.getChildAt(index)
                if (sibling !== bar &&
                    generateSequence(sibling.javaClass as Class<*>?) { it.superclass }
                        .any { it.name == "com.tencent.qui.quiblurview.QQBlurViewWrapper" } &&
                    sibling.width == bar.width &&
                    sibling.height == bar.height
                ) {
                    add(sibling)
                }
            }
        }
    }
    val compactShells = compactNativeChromeShells(bar)
    return (siblingBlurWrappers + compactShells + buildList {
        var candidate = bar.parent as? View
        repeat(3) {
            val group = candidate as? ViewGroup ?: return@buildList
            if (group.childCount == 1 &&
                group.width >= minimumWidth &&
                group.height in 1..maximumHeight &&
                group !is TabHost
            ) {
                add(group)
                candidate = group.parent as? View
            } else {
                return@buildList
            }
        }
    }).distinct()
}

private fun qqCandidateTabCount(view: View): Int {
    if (view.javaClass.name == "com.tencent.biz.qui.quipagetabbar.QUIPageTabBar") {
        return directSocialTabViews(view).size
    }
    if (view is TabWidget) return view.tabCount
    val isMaterialTabs = generateSequence(view.javaClass as Class<*>?) { it.superclass }
        .any { it.name == "com.google.android.material.tabs.TabLayout" }
    if (isMaterialTabs) {
        return runCatching {
            view.javaClass.getMethod("getTabCount").invoke(view) as Int
        }.getOrDefault(0)
    }
    return (view as? ViewGroup)?.childCount ?: 0
}

private fun socialFallbackIcon(label: String): ImageVector {
    val normalized = label.replace("\u200B", "").trim()
    return when {
        normalized.contains("消息") || normalized.contains("微信") -> MiuixIcons.Messages
        normalized.contains("通讯录") || normalized.contains("联系人") ||
            normalized.contains("我的") || normalized == "我" -> MiuixIcons.ContactsCircle
        normalized.contains("发现") || normalized.contains("动态") ||
            normalized.contains("看点") || normalized.contains("频道") -> MiuixIcons.SearchDevice
        else -> MiuixIcons.Home
    }
}

private fun View.findSocialTabHostAncestor(): TabHost? {
    var current: View? = this
    repeat(8) {
        val host = current as? TabHost
        if (host != null) return host
        current = current?.parent as? View
    }
    return null
}

private fun View.hasClickableDescendant(): Boolean {
    if (isClickable) return true
    if (this !is ViewGroup) return false
    repeat(childCount) {
        if (getChildAt(it).hasClickableDescendant()) return true
    }
    return false
}

private fun View.invokeSocialInt(methodName: String): Int? {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            val method: Method = type.getDeclaredMethod(methodName).apply { isAccessible = true }
            return method.invoke(this) as? Int
        } catch (_: NoSuchMethodException) {
            type = type.superclass
        } catch (_: Throwable) {
            return null
        }
    }
    return null
}
