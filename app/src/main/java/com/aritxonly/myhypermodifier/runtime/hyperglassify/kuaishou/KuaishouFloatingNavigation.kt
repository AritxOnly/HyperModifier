package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import java.lang.reflect.Method
import java.util.WeakHashMap

internal data class KuaishouNavigationSpec(
    val packageName: String,
    val logName: String,
    val testedVersion: String,
)

internal object KuaishouNavigationTargets {
    private val specs = listOf(
        KuaishouNavigationSpec(
            packageName = ModuleScopePackage.KUAISHOU,
            logName = "快手",
            testedVersion = "14.8.10.50114",
        ),
        KuaishouNavigationSpec(
            packageName = ModuleScopePackage.KUAISHOU_LITE,
            logName = "快手极速版",
            testedVersion = "14.9.10.11957",
        ),
    )

    fun specFor(packageName: String): KuaishouNavigationSpec? =
        specs.firstOrNull { it.packageName == packageName }

    fun enabledFor(packageName: String): Boolean = when (packageName) {
        ModuleScopePackage.KUAISHOU -> ModuleSettings.kuaishouFloatingNavigationEnabled
        ModuleScopePackage.KUAISHOU_LITE -> ModuleSettings.kuaishouLiteFloatingNavigationEnabled
        else -> false
    }
}

/** Kuaishou owns tab selection and routing; the glass dock only mirrors and forwards clicks. */
internal object KuaishouFloatingNavigation {
    internal const val TAB_VIEW_CLASS =
        "com.yxcorp.gifshow.homepage.kcube.actionbar.bottom.widget.BottomTabView"
    internal const val SHOOT_VIEW_CLASS =
        "com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView"
    internal const val CENTER_TASK_VIEW_CLASS =
        "com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarCenterTaskView"

    private val specs = WeakHashMap<String, KuaishouNavigationSpec>()
    private val delegates = HashMap<String, NativeViewBottomBarNavigation>()
    private val activities = WeakHashMap<Activity, String>()

    init {
        ModuleSettings.onChanged {
            val lift = ModuleSettings.hyperGlassifyHiddenNavigationLift
            activities.keys.toList().forEach { activity ->
                val packageName = activities[activity] ?: return@forEach
                val delegate = delegates[packageName] ?: return@forEach
                if (KuaishouNavigationTargets.enabledFor(packageName)) {
                    delegate.attach(activity)
                    delegate.refresh(activity)
                } else {
                    delegate.dispose(activity)
                }
                if (lift >= 0f) delegate.refresh(activity)
            }
        }
    }

    @JvmStatic
    fun isTarget(packageName: String): Boolean =
        KuaishouNavigationTargets.specFor(packageName) != null

    @JvmStatic
    fun prepare(activity: Activity, packageName: String) {
        val spec = KuaishouNavigationTargets.specFor(packageName) ?: return
        ModuleSettings.markLoaded(activity)
        activities[activity] = packageName
        specFor(packageName, spec)
        delegateFor(spec).prepare(activity)
    }

    @JvmStatic
    fun attach(activity: Activity, packageName: String) {
        val spec = KuaishouNavigationTargets.specFor(packageName) ?: return
        prepare(activity, packageName)
        delegateFor(spec).attach(activity)
        delegateFor(spec).setForeground(activity, true)
    }

    @JvmStatic
    fun setForeground(activity: Activity, packageName: String, foreground: Boolean) {
        delegates[packageName]?.setForeground(activity, foreground)
    }

    @JvmStatic
    fun refresh(activity: Activity, packageName: String) {
        delegates[packageName]?.refresh(activity)
    }

    @JvmStatic
    fun onTouchEvent(activity: Activity, packageName: String, event: MotionEvent) {
        delegates[packageName]?.onTouchEvent(activity, event)
    }

    @JvmStatic
    fun dispose(activity: Activity, packageName: String) {
        delegates[packageName]?.dispose(activity)
        activities.remove(activity)
    }

    private fun specFor(packageName: String, spec: KuaishouNavigationSpec) {
        specs[packageName] = spec
    }

    private fun delegateFor(spec: KuaishouNavigationSpec): NativeViewBottomBarNavigation =
        synchronized(delegates) {
            delegates.getOrPut(spec.packageName) {
                NativeViewBottomBarNavigation(
                    NativeViewBottomBarTarget(
                        logName = spec.logName,
                        testedVersion = spec.testedVersion,
                        bottomEdgeMask = false,
                        showNavigationShadowInLight = true,
                        bottomBarClassNames = setOf(TAB_VIEW_CLASS),
                        resolveBottomBar = ::findKuaishouBottomBar,
                        resolveNativeChromeShells = ::findKuaishouActionChrome,
                        tabClassNames = setOf(TAB_VIEW_CLASS),
                        fallbackLabels = listOf("首页", "精选", "发布", "消息", "我"),
                        enabled = {
                            ModuleSettings.moduleHooksEnabled &&
                                KuaishouNavigationTargets.enabledFor(spec.packageName)
                        },
                        useMiuixIcons = { true },
                        useMonochromeIcons = { true },
                        showBadges = { true },
                        fallbackIcon = { label, _ -> kuaishouFallbackIcon(label) },
                        resolveTabViews = { bar ->
                            val tabs = collectKuaishouTabs(bar, visibleOnly = true)
                                .sortedBy { it.left }
                            val action = findKuaishouDetachedAction(bar.rootView)
                            if (action == null) {
                                tabs
                            } else {
                                tabs.take(2) + action + tabs.drop(2)
                            }
                        },
                        resolveTabLabel = { tab, index ->
                            kuaishouTabLabel(tab)
                                ?: listOf("首页", "精选", "发布", "消息", "我")
                                    .getOrNull(index)
                        },
                        resolveTabBadge = { tab ->
                            invokeInt(tab, "getCurrentBadgeStatus")?.let { it > 0 } == true
                        },
                        resolveSelectedIndex = { _, tabs ->
                            tabs.indexOfFirst(::isKuaishouTabSelected).takeIf { it >= 0 }
                        },
                        isDetachedAction = { tab, index ->
                            tab.javaClass.name == SHOOT_VIEW_CLASS ||
                                tab.javaClass.name == CENTER_TASK_VIEW_CLASS ||
                                kuaishouTabLabel(tab) == "发布"
                        },
                        onNativeTabClick = { tab -> tab.performClick() },
                        resolveDarkTheme = { activity, _, _, _ ->
                            HostThemePolicy.darkFromUiMode(
                                Resources.getSystem().configuration.uiMode,
                            )
                        },
                        nativeBarVisible = { bar -> bar.isShown && bar.height > 0 },
                        nativeRefreshIntervalMs = 160L,
                        useWindowGpuBackdrop = true,
                        useHardwareBackdrop = true,
                        allowSoftwareBackdrop = false,
                        minimumCaptureIntervalMs = 200L,
                        sampleBackdropOnSourceFrame = true,
                        continuousBackdropSampling = true,
                        glassTintAlpha = 0.58f,
                        stableNavigationInset = true,
                        lateAttachWindowMs = 30_000L,
                        overlayAllowed = { activity ->
                            !activity.isFinishing && !activity.isDestroyed &&
                                activity.hasWindowFocus()
                        },
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
                    ),
                )
            }
        }
}

internal fun findKuaishouBottomBar(root: View): View? {
    val tabs = collectKuaishouTabs(root, visibleOnly = true)
    if (tabs.size < 2) return null
    val firstAncestors = generateSequence(tabs.first().parent as? View) { it.parent as? View }
    return firstAncestors
        .firstOrNull { ancestor -> tabs.all { it.isInside(ancestor) } }
        ?.takeIf { it.width > 0 && it.height > 0 }
}

private fun View.isInside(ancestor: View): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

private fun collectKuaishouTabs(root: View, visibleOnly: Boolean): List<View> = buildList {
    fun visit(view: View) {
        if (visibleOnly && (!view.isShown || view.visibility == View.GONE)) return
        if (view.javaClass.name == KuaishouFloatingNavigation.TAB_VIEW_CLASS) add(view)
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { visit(group.getChildAt(it)) }
        }
    }
    visit(root)
}

private fun findKuaishouDetachedAction(root: View): View? {
    val actions = collectKuaishouActions(root)
    return actions.firstOrNull { it.javaClass.name == KuaishouFloatingNavigation.SHOOT_VIEW_CLASS }
        ?: actions.firstOrNull {
            it.javaClass.name == KuaishouFloatingNavigation.CENTER_TASK_VIEW_CLASS
        }
}

private fun findKuaishouActionChrome(bar: View): List<View> =
    collectKuaishouActions(bar.rootView)

private fun collectKuaishouActions(root: View): List<View> = buildList {
    fun visit(view: View) {
        if (view.javaClass.name == KuaishouFloatingNavigation.SHOOT_VIEW_CLASS ||
            view.javaClass.name == KuaishouFloatingNavigation.CENTER_TASK_VIEW_CLASS
        ) {
            if (view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0) {
                add(view)
            }
        }
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { visit(group.getChildAt(it)) }
        }
    }
    visit(root)
}

private fun kuaishouTabLabel(tab: View): String? =
    runCatching {
        tab.javaClass.getMethod("getText").invoke(tab) as? CharSequence
    }.getOrNull()?.toString()?.trim()?.takeIf { it.isNotEmpty() }

private fun isKuaishouTabSelected(tab: View): Boolean {
    if (tab.isSelected) return true
    if (tab is CheckedTextView && tab.isChecked) return true
    if (tab is ViewGroup) {
        repeat(tab.childCount) { index ->
            val child = tab.getChildAt(index)
            if (child is CheckedTextView && child.isChecked) return true
            if (child.isSelected) return true
        }
    }
    return false
}

private fun invokeInt(target: Any, name: String): Int? {
    var type: Class<*>? = target.javaClass
    while (type != null) {
        try {
            val method: Method = type.getDeclaredMethod(name).apply { isAccessible = true }
            return method.invoke(target) as? Int
        } catch (_: NoSuchMethodException) {
            type = type.superclass
        } catch (_: Throwable) {
            return null
        }
    }
    return null
}

private fun kuaishouFallbackIcon(label: String): ImageVector = when {
    label.contains("发布") || label.contains("拍摄") -> MiuixIcons.Add
    label.contains("消息") -> MiuixIcons.Messages
    label.contains("我") -> MiuixIcons.ContactsCircle
    label.contains("精选") || label.contains("发现") -> MiuixIcons.All
    else -> MiuixIcons.Home
}
