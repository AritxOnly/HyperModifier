package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TabHost
import android.widget.TabWidget
import android.widget.TextView
import androidx.compose.ui.graphics.vector.ImageVector
import java.lang.reflect.Method
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Carrier
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.SearchDevice

internal data class EcommerceNavigationSpec(
    val packageName: String,
    val logName: String,
    val testedVersion: String,
    val activityClassNames: List<String>,
)

internal object EcommerceNavigationTargets {
    private val specs = listOf(
        EcommerceNavigationSpec(
            packageName = "com.xunmeng.pinduoduo",
            logName = "拼多多",
            testedVersion = "8.23.0",
            activityClassNames = listOf(
                "com.xunmeng.pinduoduo.ui.activity.MainFrameActivity",
                "com.xunmeng.pinduoduo.ui.activity.HomeActivity",
            ),
        ),
        EcommerceNavigationSpec(
            packageName = "com.taobao.taobao",
            logName = "淘宝",
            testedVersion = "10.65.10",
            activityClassNames = listOf("com.taobao.tao.TBMainActivity"),
        ),
        EcommerceNavigationSpec(
            packageName = "com.jingdong.app.mall",
            logName = "京东",
            testedVersion = "15.7.50",
            activityClassNames = listOf("com.jingdong.app.mall.MainFrameActivity"),
        ),
        EcommerceNavigationSpec(
            packageName = "com.taobao.idlefish",
            logName = "闲鱼",
            testedVersion = "7.28.10",
            activityClassNames = listOf("com.taobao.idlefish.maincontainer.activity.MainActivity"),
        ),
        EcommerceNavigationSpec(
            packageName = "com.ss.android.ugc.livelite",
            logName = "抖音商城",
            testedVersion = "40.1.0",
            activityClassNames = listOf("com.ss.android.ugc.aweme.main.MainActivity"),
        ),
        EcommerceNavigationSpec(
            packageName = "com.cainiao.wireless",
            logName = "菜鸟",
            testedVersion = "8.11.508",
            activityClassNames = listOf("com.cainiao.wireless.homepage.view.activity.HomePageActivity"),
        ),
    )
    private val delegates = HashMap<String, EcommerceNavigationDelegate>()

    fun specFor(packageName: String): EcommerceNavigationSpec? =
        specs.firstOrNull { it.packageName == packageName }

    fun delegateFor(packageName: String): EcommerceNavigationDelegate? {
        val spec = specFor(packageName) ?: return null
        return synchronized(delegates) {
            delegates.getOrPut(packageName) {
                EcommerceNavigationDelegate(createTarget(spec))
            }
        }
    }

    fun enabledFor(packageName: String): Boolean = when (packageName) {
        "com.xunmeng.pinduoduo" -> ModuleSettings.pinduoduoFloatingNavigationEnabled
        "com.taobao.taobao" -> ModuleSettings.taobaoFloatingNavigationEnabled
        "com.jingdong.app.mall" -> ModuleSettings.jingdongFloatingNavigationEnabled
        "com.taobao.idlefish" -> ModuleSettings.xianyuFloatingNavigationEnabled
        "com.ss.android.ugc.livelite" -> ModuleSettings.douyinMallFloatingNavigationEnabled
        "com.cainiao.wireless" -> ModuleSettings.cainiaoFloatingNavigationEnabled
        else -> false
    }

    private fun createTarget(spec: EcommerceNavigationSpec): NativeViewBottomBarTarget {
        val classNames = when (spec.packageName) {
            "com.xunmeng.pinduoduo" ->
                setOf("com.xunmeng.pinduoduo.ui_home_activity.widget.tab.PddTabView")
            "com.taobao.taobao" ->
                setOf("com.taobao.tao.navigation.TBFragmentTabHost")
            "com.jingdong.app.mall" ->
                setOf("com.jingdong.common.unification.navigationbar.newbar.NavigationGroup")
            "com.taobao.idlefish" ->
                setOf("com.taobao.idlefish.maincontainer.MainNavigateTabIndicator")
            "com.ss.android.ugc.livelite" ->
                setOf(
                    "com.ss.android.ugc.aweme.homepage.tab.bottom.ui.tabbar.container.MainBottomTabContainer",
                )
            "com.cainiao.wireless" ->
                setOf("com.cainiao.commonlibrary.navigation.NavigationView")
            else -> emptySet()
        }
        val fallbackLabels = when (spec.packageName) {
            "com.xunmeng.pinduoduo" ->
                listOf("首页", "推荐", "聊天", "我的", "个人中心")
            "com.taobao.taobao" ->
                listOf("首页", "视频", "逛逛", "消息", "购物车", "我的")
            "com.jingdong.app.mall" ->
                listOf("首页", "秒杀", "逛", "发现", "新品", "购物车", "我的")
            "com.taobao.idlefish" ->
                listOf("首页", "附近", "发布", "消息", "我的")
            "com.ss.android.ugc.livelite" ->
                listOf("首页", "商城", "分类", "购物车", "订单", "消息", "我的")
            else ->
                listOf(
                    "首页", "发现", "裹裹权益", "驿站生活", "学生专享", "超值购",
                    "全部快递", "积分", "消息", "我的",
                )
        }

        return NativeViewBottomBarTarget(
            logName = spec.logName,
            testedVersion = spec.testedVersion,
            bottomBarClassNames = classNames,
            resolveBottomBar = { root ->
                when (spec.packageName) {
                    "com.taobao.taobao" ->
                        root.findTaobaoTabWidget(classNames)
                            ?: root.findBestBottomBar(emptySet(), fallbackLabels)
                    "com.cainiao.wireless" ->
                        root.findCainiaoNavigation(classNames)
                            ?: root.findBestBottomBar(classNames, fallbackLabels)
                    else -> root.findBestBottomBar(classNames, fallbackLabels)
                }
            },
            tabClassNames = emptySet(),
            fallbackLabels = fallbackLabels,
            enabled = {
                ModuleSettings.moduleHooksEnabled && enabledFor(spec.packageName)
            },
            useMiuixIcons = { true },
            useMonochromeIcons = { false },
            showBadges = { spec.packageName != "com.ss.android.ugc.livelite" },
            fallbackIcon = { label, _ -> ecommerceFallbackIcon(label) },
            resolveTabViews = { bar ->
                when (spec.packageName) {
                    "com.taobao.taobao" ->
                        (bar as? TabWidget)?.let { widget ->
                            buildList {
                                repeat(widget.tabCount) { index ->
                                    widget.getChildTabViewAt(index)
                                        ?.takeIf { it.visibility != View.GONE }
                                        ?.let(::add)
                                }
                            }
                        }?.takeIf { it.size > 1 }
                            ?: bar.matchingTabViews(fallbackLabels, fallbackLabels.size)
                    "com.taobao.idlefish" ->
                        bar.matchingTabViews(fallbackLabels, fallbackLabels.size)
                            .takeIf { it.size > 1 }
                            ?: bar.invokeViewGroup("getLayout")?.let(::visibleChildren)
                            ?: bar.geometricTabViews(fallbackLabels.size)
                    "com.ss.android.ugc.livelite" ->
                        bar.classTabViews(DOUYIN_BOTTOM_TAB_CLASSES, 8)
                            .takeIf { it.size > 1 }
                            ?: bar.matchingTabViews(fallbackLabels, fallbackLabels.size)
                                .takeIf { it.size > 1 }
                            ?: bar.geometricTabViews(fallbackLabels.size)
                    "com.cainiao.wireless" ->
                        bar.directTabViews("getTabSize", "getTabView", 10)
                            .takeIf { it.size > 1 }
                            ?: bar.classTabViews(CAINIAO_TAB_CLASSES, 10, requireShown = false)
                            .takeIf { it.size > 1 }
                            ?: bar.matchingTabViews(fallbackLabels, fallbackLabels.size)
                                .takeIf { it.size > 1 }
                            ?: bar.geometricTabViews(fallbackLabels.size)
                    else ->
                        bar.matchingTabViews(fallbackLabels, fallbackLabels.size)
                            .takeIf { it.size > 1 }
                            ?: bar.genericInteractiveChildren()
                            ?: bar.geometricTabViews(fallbackLabels.size)
                }
            },
            resolveSelectedIndex = { bar, tabs ->
                when (spec.packageName) {
                    "com.taobao.taobao" ->
                        bar.findTabHostAncestor()?.currentTab?.takeIf { it in tabs.indices }
                    "com.jingdong.app.mall" -> bar.invokeInt("getCheckId")?.let { navigationId ->
                        tabs.indexOfFirst { it.id == navigationId }.takeIf { it >= 0 }
                    }
                    "com.taobao.idlefish" ->
                        bar.invokeInt("getCurrentIndex")?.takeIf { it in tabs.indices }
                    else -> null
                } ?: tabs.indexOfFirst(::isTabSelected).takeIf { it >= 0 }
                    ?: tabs.indexOfFirst { it.invokeBoolean("getStatus") }.takeIf { it >= 0 }
            },
            resolveTabLabel = { tab, index ->
                tab.findAllTextViews()
                    .mapNotNull { it.text?.toString()?.trim()?.takeIf { value -> value.isNotEmpty() } }
                    .firstOrNull()
                    ?: fallbackLabels.getOrNull(index)
            },
            resolveDarkTheme = { activity, bar, tabs, selected ->
                val explicit = when (spec.packageName) {
                    "com.ss.android.ugc.livelite" ->
                        tabs.getOrNull(selected)?.invokeBoolean("isCurrentDarkMode")
                    "com.jingdong.app.mall" ->
                        tabs.getOrNull(selected)?.invokeDeclaredBoolean("getDarkMode")
                            ?: tabs.firstOrNull()?.invokeDeclaredBoolean("getDarkMode")
                    "com.cainiao.wireless" -> CainiaoThemeAccess.isDarkMode(activity)
                    else -> null
                }
                HostThemeResolver.resolve(
                    activity = activity,
                    nativeBar = bar,
                    tabs = tabs,
                    selectedIndex = selected,
                    explicitDark = explicit,
                    // The selected labels in Jingdong and Xianyu retain a dark/red palette in
                    // both themes, so they are not reliable dark-mode votes. Prefer evidence
                    // owned by the host window before falling back to sampled label colors.
                    preferHostWindow = spec.packageName == "com.jingdong.app.mall" ||
                        spec.packageName == "com.taobao.idlefish",
                    // Cainiao paints NavigationView itself white for light mode and black for
                    // dark mode in refreshTabStripBackground(). Its fixed labels must never
                    // override that authoritative theme state.
                    preferNativeBackground = spec.packageName == "com.cainiao.wireless",
                )
            },
            nativeBarVisible = { bar ->
                // The shared host intentionally lowers native alpha after attaching.
                bar.isShown && bar.height > 0
            },
            nativeRefreshIntervalMs =
                if (spec.packageName == "com.ss.android.ugc.livelite") 240L else 160L,
            minimumCaptureIntervalMs =
                if (spec.packageName == "com.ss.android.ugc.livelite") 100L else 48L,
            allowSoftwareBackdrop = spec.packageName != "com.ss.android.ugc.livelite",
            sampleBackdropOnSourceFrame = true,
            sampleBackdropForTabs = { _, _ -> true },
            useHardwareBackdrop = spec.packageName == "com.ss.android.ugc.livelite",
            pixelCopyRetryDelayMs =
                if (spec.packageName == "com.ss.android.ugc.livelite") 1_000L else 700L,
            stableNavigationInset = true,
            lateAttachWindowMs = 30_000L,
            manageWindowImmersion = spec.packageName != "com.ss.android.ugc.livelite",
            overlayAllowed = { activity ->
                spec.packageName != "com.ss.android.ugc.livelite" || activity.hasWindowFocus()
            },
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
            onNativeTabClick = { tab ->
                val route = if (spec.packageName == "com.ss.android.ugc.livelite") {
                    tab.invokeView("getView") ?: tab
                } else {
                    tab
                }
                if (!performNativeNavigationClick(route) && route !== tab) {
                    performNativeNavigationClick(tab)
                }
            },
        )
    }

    private val DOUYIN_BOTTOM_TAB_CLASSES = setOf(
        "com.ss.android.ugc.aweme.homepage.tab.ui.bottom.BottomTab",
    )
    private val CAINIAO_TAB_CLASSES = setOf(
        "com.cainiao.commonlibrary.navigation.NavigationTabView",
    )
}

internal class EcommerceNavigationDelegate(
    target: NativeViewBottomBarTarget,
) {
    private val navigation = NativeViewBottomBarNavigation(target)
    fun prepare(activity: Activity) = navigation.prepare(activity)
    fun attach(activity: Activity) = navigation.attach(activity)
    fun dispose(activity: Activity) = navigation.dispose(activity)
    fun setForeground(activity: Activity, foreground: Boolean) =
        navigation.setForeground(activity, foreground)
    fun refresh(activity: Activity) = navigation.refresh(activity)
    fun onTouchEvent(activity: Activity, event: android.view.MotionEvent) =
        navigation.onTouchEvent(activity, event)
    fun hasHost(activity: Activity) = navigation.hasHost(activity)
    fun hasNavigationTarget(activity: Activity) = navigation.hasNavigationTarget(activity)
}

private fun View.findBestBottomBar(
    exactClassNames: Set<String>,
    labels: List<String>,
): View? {
    val exact = ArrayList<View>()
    this.collectMatchingDescendants(exactClassNames, exact)
    exact.maxByOrNull { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        location[1] + view.height
    }?.let { return it }

    val screenHeight = height.coerceAtLeast(rootView?.height ?: 0)
    val screenWidth = width.coerceAtLeast(rootView?.width ?: 0)
    val density = resources.displayMetrics.density
    val minimum = when {
        labels.size >= 5 -> 3
        else -> 2
    }
    val semantic = ArrayList<Pair<View, EcommerceTabCandidate>>()
    this.collectLabelCandidates(labels, semantic)
    val semanticRow = EcommerceNavigationPolicy.resolve(
        semantic.map { it.second },
        screenHeight,
        density,
        minimum,
    )
    if (semanticRow != null) {
        commonTabAncestor(semanticRow.tabs.mapNotNull { selected ->
            semantic.firstOrNull { it.second === selected }?.first
        })?.let { return it }
    }

    val geometric = ArrayList<Pair<View, EcommerceTabCandidate>>()
    this.collectClickableCandidates(0, geometric)
    val geometricRow = EcommerceNavigationPolicy.resolveGeometry(
        geometric.map { it.second },
        screenWidth,
        screenHeight,
        density,
        minimum,
    ) ?: return null
    return commonTabAncestor(geometricRow.tabs.mapNotNull { selected ->
        geometric.firstOrNull { it.second === selected }?.first
    })
}

internal fun View.findCainiaoNavigation(classNames: Set<String>): View? {
    val resourceContext = context ?: return null
    val resourcePackage = resourceContext.packageName
    val navigationId = synchronized(cainiaoNavigationIdCache) {
        cainiaoNavigationIdCache.getOrPut(resourcePackage) {
            resources.getIdentifier("navigation_tab_view", "id", resourcePackage)
        }
    }
    if (navigationId != View.NO_ID) {
        findViewById<View>(navigationId)?.let { candidate ->
            return candidate.takeIf { it.matchesClassHierarchy(classNames) }
                ?: candidate.findDescendantByClassHierarchy(classNames)
        }
    }
    val matches = ArrayList<View>()
    collectMatchingDescendants(classNames, matches, requireShown = false)
    return matches.minByOrNull { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        location[1]
    }
}

private val cainiaoNavigationIdCache = HashMap<String, Int>()

private object CainiaoThemeAccess {
    @Volatile
    private var cached: Access? = null

    fun isDarkMode(context: Context): Boolean? = runCatching {
        val classLoader = context.javaClass.classLoader ?: return null
        val access = cached?.takeIf { it.classLoader === classLoader }
            ?: synchronized(this) {
                cached?.takeIf { it.classLoader === classLoader }
                    ?: runCatching {
                        val type = Class.forName(
                            "com.cainiao.wireless.dpl.utils.e",
                            false,
                            classLoader,
                        )
                        val helper = type.getField("duI").get(null)
                        Access(
                            classLoader = classLoader,
                            helper = helper,
                            method = type.getMethod("isDarkMode", Context::class.java),
                        )
                    }.getOrNull()?.also { cached = it }
            } ?: return null
        access.method.invoke(access.helper, context) as? Boolean
    }.getOrNull()

    private data class Access(
        val classLoader: ClassLoader,
        val helper: Any,
        val method: Method,
    )
}

private fun View.findDescendantByClassHierarchy(classNames: Set<String>): View? {
    if (matchesClassHierarchy(classNames)) return this
    if (this !is ViewGroup) return null
    repeat(childCount) { index ->
        getChildAt(index).findDescendantByClassHierarchy(classNames)?.let { return it }
    }
    return null
}

private fun View.collectMatchingDescendants(
    classes: Set<String>,
    target: MutableList<View>,
    requireShown: Boolean = true,
) {
    if (classes.isEmpty()) return
    if (matchesClassHierarchy(classes) && (!requireShown || isShown)) target += this
    if (this is ViewGroup) {
        repeat(childCount) {
            getChildAt(it).collectMatchingDescendants(classes, target, requireShown)
        }
    }
}

private fun View.collectLabelCandidates(
    expectedLabels: List<String>,
    target: MutableList<Pair<View, EcommerceTabCandidate>>,
) {
    if (!isShown || alpha <= 0f) return
    val viewLabel = (this as? TextView)?.text?.toString()?.trim()
        ?.takeIf { EcommerceNavigationPolicy.labelsMatch(it, expectedLabels) }
    if (viewLabel != null) {
        val clickable = this.nearestClickable()
        if (clickable != null) {
            val clickLocation = IntArray(2)
            clickable.getLocationOnScreen(clickLocation)
            target += clickable to EcommerceTabCandidate(
                label = viewLabel,
                left = clickLocation[0],
                top = clickLocation[1],
                right = clickLocation[0] + clickable.width,
                bottom = clickLocation[1] + clickable.height,
                clickable = true,
                selected = isTabSelected(clickable),
            )
        }
    }
    if (this is ViewGroup) {
        repeat(childCount) {
            getChildAt(it).collectLabelCandidates(expectedLabels, target)
        }
    }
}

private fun View.collectClickableCandidates(
    traversalIndex: Int,
    target: MutableList<Pair<View, EcommerceTabCandidate>>,
) : Int {
    if (!isShown || alpha <= 0f) return traversalIndex
    var next = traversalIndex
    if (isClickable && width > 0 && height > 0) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        target += this to EcommerceTabCandidate(
            label = "tab-$next",
            location[0],
            location[1],
            location[0] + width,
            location[1] + height,
            true,
            isTabSelected(this),
        )
        next++
    }
    if (this is ViewGroup) {
        repeat(childCount) {
            next = getChildAt(it).collectClickableCandidates(next, target)
        }
    }
    return next
}

private fun commonTabAncestor(views: List<View>): View? {
    if (views.size < 2) return null
    var ancestor = views.first().parent as? ViewGroup
    while (ancestor != null) {
        if (views.all { it.isDescendantOf(ancestor!!) }) return ancestor
        ancestor = ancestor.parent as? ViewGroup
    }
    return views.first()
}

private fun View.nearestClickable(): View? {
    var current: View? = this
    repeat(6) {
        val view = current ?: return null
        if (view.isClickable) return view
        current = view.parent as? View
    }
    return null
}

private fun View.isDescendantOf(ancestor: ViewGroup): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

private fun isTabSelected(view: View): Boolean {
    if (view.isSelected) return true
    if (view is ViewGroup) {
        if (view.findAllTextViews().any { it.isSelected }) return true
        repeat(view.childCount) {
            if (isTabSelected(view.getChildAt(it))) return true
        }
    }
    val selectedMethod = runCatching {
        view.javaClass.getMethod("isClicked").invoke(view) as? Boolean
    }.getOrNull()
    return selectedMethod == true
}

private fun visibleChildren(group: ViewGroup): List<View> =
    buildList {
        repeat(group.childCount) { index ->
            group.getChildAt(index).takeIf {
                it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
            }?.let(::add)
        }
    }

private fun View.matchingTabViews(labels: List<String>, limit: Int): List<View> {
    val matches = ArrayList<Pair<View, EcommerceTabCandidate>>()
    collectLabelCandidates(labels, matches)
    return matches.map { it.first }
        .distinct()
        .sortedBy { it.screenCenterX() }
        .take(limit.coerceAtLeast(1))
}

private fun View.classTabViews(
    classNames: Set<String>,
    limit: Int,
    requireShown: Boolean = true,
): List<View> {
    val matches = ArrayList<View>()
    fun collect(view: View) {
        if (view !== this && (!requireShown || view.isShown) &&
            view.matchesClassHierarchy(classNames)
        ) {
            matches += view
            return
        }
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { collect(group.getChildAt(it)) }
        }
    }
    (this as? ViewGroup)?.let { group ->
        repeat(group.childCount) { collect(group.getChildAt(it)) }
    }
    return matches.distinct().sortedBy(View::screenCenterX).take(limit.coerceAtLeast(1))
}

internal fun View.directTabViews(
    countMethod: String,
    viewMethod: String,
    limit: Int,
): List<View> {
    val count = invokeInt(countMethod) ?: return emptyList()
    return buildList {
        repeat(count.coerceIn(0, limit)) { index ->
            invokeView(viewMethod, index)
                ?.takeIf { it.visibility != View.GONE }
                ?.let(::add)
        }
    }.distinct()
}

private fun View.matchesClassHierarchy(classNames: Set<String>): Boolean =
    generateSequence(javaClass as Class<*>?) { it.superclass }.any { it.name in classNames }

internal fun View.findTaobaoTabWidget(hostClassNames: Set<String>): View? {
    val hosts = ArrayList<View>()
    fun collect(view: View) {
        if (view.javaClass.name in hostClassNames) hosts += view
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { collect(group.getChildAt(it)) }
        }
    }
    collect(this)
    return hosts.asSequence()
        .mapNotNull { (it as? TabHost)?.tabWidget }
        .firstOrNull { it.tabCount > 1 }
}

internal fun View.findTabHostAncestor(): TabHost? {
    var current: View? = this
    repeat(8) {
        val host = current as? TabHost
        if (host != null) return host
        current = current?.parent as? View
    }
    return null
}

private fun View.genericInteractiveChildren(): List<View>? {
    val group = this as? ViewGroup ?: return null
    val children = visibleChildren(group)
    val clickable = children.filter { child ->
        child.isClickable || child.hasClickableDescendant()
    }
    return clickable.takeIf { it.size in 2..8 }
}

private fun View.geometricTabViews(limit: Int): List<View> {
    val matches = ArrayList<Pair<View, EcommerceTabCandidate>>()
    collectClickableCandidates(0, matches)
    val rootHeight = height.coerceAtLeast(rootView?.height ?: 0)
    val rootWidth = width.coerceAtLeast(rootView?.width ?: 0)
    val row = EcommerceNavigationPolicy.resolveGeometry(
        matches.map { it.second },
        rootWidth,
        rootHeight,
        resources.displayMetrics.density,
        2,
    ) ?: return emptyList()
    return row.tabs.mapNotNull { selected ->
        matches.firstOrNull { it.second === selected }?.first
    }.take(limit)
}

private fun View.screenCenterX(): Int {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return location[0] + width / 2
}

private fun View.hasClickableDescendant(): Boolean {
    if (isClickable) return true
    if (this !is ViewGroup) return false
    repeat(childCount) {
        if (getChildAt(it).hasClickableDescendant()) return true
    }
    return false
}

private fun View.findAllTextViews(): List<TextView> = buildList {
    if (this@findAllTextViews is TextView) add(this@findAllTextViews)
    if (this@findAllTextViews is ViewGroup) {
        fun visit(view: View) {
            if (view is TextView) add(view)
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        repeat(childCount) { visit(getChildAt(it)) }
    }
}

private fun performNativeNavigationClick(tab: View): Boolean {
    val candidates = buildList {
        if (tab.isClickable) add(tab)
        tab.findFirstClickableDescendant()?.let(::add)
        tab.nearestClickable()?.let(::add)
        add(tab)
    }.distinct()
    return candidates.any(View::performClick)
}

private fun View.findFirstClickableDescendant(): View? {
    if (isClickable) return this
    if (this !is ViewGroup) return null
    repeat(childCount) {
        getChildAt(it).findFirstClickableDescendant()?.let { return it }
    }
    return null
}

private fun View.invokeBoolean(methodName: String): Boolean = runCatching {
    javaClass.getMethod(methodName).invoke(this) as? Boolean
}.getOrNull() == true

private fun View.invokeDeclaredBoolean(methodName: String): Boolean? {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            val method = type.getDeclaredMethod(methodName).apply { isAccessible = true }
            return method.invoke(this) as? Boolean
        } catch (_: NoSuchMethodException) {
            type = type.superclass
        } catch (_: Throwable) {
            return null
        }
    }
    return null
}

private fun View.invokeInt(methodName: String): Int? = runCatching {
    javaClass.getMethod(methodName).invoke(this) as? Int
}.getOrNull()

private fun View.invokeView(methodName: String): View? = runCatching {
    javaClass.getMethod(methodName).invoke(this) as? View
}.getOrNull()

private fun View.invokeView(methodName: String, index: Int): View? = runCatching {
    javaClass.getMethod(methodName, Int::class.javaPrimitiveType!!).invoke(this, index) as? View
}.getOrNull()

private fun View.invokeViewGroup(methodName: String): ViewGroup? = runCatching {
    javaClass.getMethod(methodName).invoke(this) as? ViewGroup
}.getOrNull()

private fun ecommerceFallbackIcon(label: String): ImageVector {
    val normalized = EcommerceNavigationPolicy.normalize(label)
    return when {
        normalized.contains("消息") || normalized.contains("聊天") -> MiuixIcons.Messages
        normalized.contains("发布") || normalized.contains("加号") -> MiuixIcons.Add
        normalized.contains("购物") || normalized.contains("订单") -> MiuixIcons.Carrier
        normalized.contains("搜索") || normalized.contains("发现") ||
            normalized.contains("逛") || normalized.contains("分类") -> MiuixIcons.SearchDevice
        normalized.contains("我的") || normalized.contains("个人") -> MiuixIcons.ContactsCircle
        else -> MiuixIcons.Home
    }
}
