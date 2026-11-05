package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home

internal object QQMusicFloatingNavigation {
    private val labels = listOf("首页", "视频", "刷歌", "星光", "我的")
    private val activities = WeakHashMap<Activity, Unit>()
    private val chromeClearance = QQMusicChromeClearance()
    private val delegate = NativeViewBottomBarNavigation(
        NativeViewBottomBarTarget(
            logName = "QQ Music",
            testedVersion = "20.9.7.9",
            bottomEdgeMask = false,
            showNavigationShadowInLight = true,
            navigationShadowVisible = false,
            matchNativeBarHorizontalBounds = true,
            bottomBarClassNames = emptySet(),
            resolveBottomBar = ::findQQMusicBottomBar,
            tabClassNames = emptySet(),
            fallbackLabels = labels,
            enabled = {
                ModuleSettings.moduleHooksEnabled && ModuleSettings.qqMusicFloatingNavigationEnabled
            },
            useMiuixIcons = { true },
            useMonochromeIcons = { true },
            showBadges = { false },
            fallbackIcon = { label, _ -> qqMusicFallbackIcon(label) },
            resolveTabViews = { bar -> qqMusicTabViews(bar) },
            resolveTabLabel = { tab, index ->
                tab.qqMusicVisibleLabel()
                    ?: labels.getOrNull(index)
            },
            resolveSelectedIndex = { _, tabs ->
                if (tabs.isEmpty()) null else {
                    val dark = HostThemePolicy.darkFromUiMode(
                        Resources.getSystem().configuration.uiMode,
                    )
                    val byColor = if (tabs.all { it is TextView }) {
                        val scores = tabs.map { tab ->
                            val color = (tab as TextView).currentTextColor
                            if (Color.alpha(color) < 0x40) {
                                if (dark) -1f else 2f
                            } else {
                                val red = Color.red(color) / 255f
                                val green = Color.green(color) / 255f
                                val blue = Color.blue(color) / 255f
                                red * 0.2126f + green * 0.7152f + blue * 0.0722f
                            }
                        }
                        if (dark) scores.indices.maxByOrNull { scores[it] }
                        else scores.indices.minByOrNull { scores[it] }
                    } else {
                        null
                    }
                    byColor ?: tabs.indexOfFirst { it.isQQMusicSelected() }.takeIf { it >= 0 }
                }
            },
            onNativeTabClick = ::clickQQMusicTab,
            resolveDarkTheme = { _, _, _, _ ->
                HostThemePolicy.darkFromUiMode(Resources.getSystem().configuration.uiMode)
            },
            nativeBarVisible = { bar -> bar.isShown && bar.height > 0 },
            nativeRefreshIntervalMs = 200L,
            useWindowGpuBackdrop = true,
            useHardwareBackdrop = true,
            allowSoftwareBackdrop = false,
            minimumCaptureIntervalMs = 200L,
            sampleBackdropOnSourceFrame = true,
            glassTintAlpha = 0.58f,
            stableNavigationInset = true,
            lateAttachWindowMs = 30_000L,
            overlayAllowed = { activity ->
                !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus()
            },
            onNavigationBoundsChanged = { activity, bounds ->
                chromeClearance.apply(activity, bounds)
            },
            onChromeReplacement = { activity, replacing ->
                if (replacing) {
                    activity.window.decorView.post { chromeClearance.refresh(activity) }
                } else {
                    chromeClearance.restore(activity)
                }
            },
            retainNativeSuppressionDuringCover = true,
            onAttachDiagnostic = { detail ->
                if (detail == "floating navigation attached") {
                    HookDiagnostics.available(ModuleScopePackage.QQ_MUSIC, "QQ音乐悬浮底栏")
                } else {
                    HookDiagnostics.observed(ModuleScopePackage.QQ_MUSIC, "QQ音乐悬浮底栏", detail)
                }
            },
        ),
    )

    init {
        ModuleSettings.onChanged {
            activities.keys.toList().forEach { activity ->
                if (ModuleSettings.qqMusicFloatingNavigationEnabled) {
                    delegate.attach(activity)
                    delegate.refresh(activity)
                } else {
                    delegate.dispose(activity)
                }
            }
        }
    }

    @JvmStatic
    fun prepare(activity: Activity) {
        activities[activity] = Unit
        delegate.prepare(activity)
    }

    @JvmStatic
    fun attach(activity: Activity) {
        prepare(activity)
        delegate.attach(activity)
        delegate.setForeground(activity, true)
    }

    @JvmStatic
    fun setForeground(activity: Activity, foreground: Boolean) =
        delegate.setForeground(activity, foreground)

    @JvmStatic
    fun refresh(activity: Activity) = delegate.refresh(activity)

    @JvmStatic
    fun onTouchEvent(activity: Activity, event: MotionEvent) =
        delegate.onTouchEvent(activity, event)

    @JvmStatic
    fun dispose(activity: Activity) {
        activities.remove(activity)
        delegate.dispose(activity)
    }
}

internal fun findQQMusicBottomBar(root: View): View? {
    findQQMusicNavigationContainer(root)?.let { return it }
    val tabs = qqMusicTabViews(root)
    if (tabs.size >= 3) {
        val firstAncestors = generateSequence(tabs.first().parent as? View) { it.parent as? View }
        firstAncestors
            .firstOrNull { ancestor -> tabs.all { it.isQQMusicDescendantOf(ancestor) } }
            ?.takeIf { bar ->
                bar.width > 0 && bar.height > 0 &&
                    bar.getLocationOnScreenY() >= root.height * 0.60f
            }
            ?.let { return it }
    }
    return root.findBestBottomBar(emptySet(), QQMusicLabels.toList())
        ?.takeIf { it.getLocationOnScreenY() >= root.height * 0.60f }
}

internal fun qqMusicTabViews(root: View): List<View> {
    if (root.javaClass.name == QQMusicNavigationContainerClass) {
        val directTabs = visibleChildren(root as ViewGroup)
            .filter { it.isClickable || it.hasClickableDescendant() }
        if (directTabs.size in 3..6) return directTabs
    }
    val minimumY = root.height * 0.60f
    val semantic = buildList {
        fun visit(view: View) {
            if (view.isShown && view.getLocationOnScreenY() >= minimumY) {
                val label = view.qqMusicVisibleLabel()
                if (label != null && QQMusicLabels.any { it.equals(label, ignoreCase = true) }) {
                    add(view)
                }
            }
            (view as? ViewGroup)?.let { group ->
                repeat(group.childCount) { visit(group.getChildAt(it)) }
            }
        }
        visit(root)
    }.distinct().sortedBy { it.getLocationOnScreenX() }
    return semantic.takeIf { it.size >= 3 }
        ?: root.geometricTabViews(QQMusicLabels.size, requireSelection = false)
            .filter { it.getLocationOnScreenY() >= minimumY }
        .distinct()
}

private fun clickQQMusicTab(tab: View) {
    val bar = findQQMusicBottomBar(tab.rootView) ?: return
    val barLocation = IntArray(2)
    val tabLocation = IntArray(2)
    bar.getLocationOnScreen(barLocation)
    tab.getLocationOnScreen(tabLocation)
    val x = tabLocation[0] - barLocation[0] + tab.width / 2f
    val y = tabLocation[1] - barLocation[1] + tab.height / 2f
    val downAt = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(downAt, downAt + 80L, MotionEvent.ACTION_UP, x, y, 0)
    try {
        bar.dispatchTouchEvent(down)
        bar.dispatchTouchEvent(up)
    } finally {
        down.recycle()
        up.recycle()
    }
}

private fun View.isQQMusicDescendantOf(ancestor: View): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

private fun findQQMusicNavigationContainer(root: View): View? {
    val matches = ArrayList<View>()
    fun visit(view: View) {
        if (view.javaClass.name == QQMusicNavigationContainerClass) matches += view
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { visit(group.getChildAt(it)) }
        }
    }
    visit(root)
    return matches.maxByOrNull { view ->
        var score = view.getLocationOnScreenY().toLong()
        if (view.isShown && view.height > 0) score += 1_000_000L
        score
    }
}

private fun View.getLocationOnScreenX(): Int = IntArray(2).also(::getLocationOnScreen)[0]
private fun View.getLocationOnScreenY(): Int = IntArray(2).also(::getLocationOnScreen)[1]

private fun View.qqMusicVisibleLabel(): String? {
    val text = (this as? TextView)?.text?.toString()?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (text != null) return text
    return contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun View.isQQMusicSelected(): Boolean {
    if (isSelected || stateDescription?.toString()?.contains("选中") == true) return true
    return (this as? ViewGroup)?.let { group ->
        (0 until group.childCount).any { group.getChildAt(it).isQQMusicSelected() }
    } == true
}

private fun View.hasClickableDescendant(): Boolean {
    if (isClickable) return true
    return (this as? ViewGroup)?.let { group ->
        (0 until group.childCount).any { group.getChildAt(it).hasClickableDescendant() }
    } == true
}

private fun qqMusicFallbackIcon(label: String): ImageVector = when {
    label == "首页" -> MiuixIcons.Home
    label == "我的" -> MiuixIcons.ContactsCircle
    else -> MiuixIcons.All
}

private val QQMusicLabels = setOf("首页", "视频", "刷歌", "星光", "我的")
private const val QQMusicNavigationContainerClass =
    "com.tencent.qqmusic.ui.minibar.navigation.MainDeskNavigateContainer"
