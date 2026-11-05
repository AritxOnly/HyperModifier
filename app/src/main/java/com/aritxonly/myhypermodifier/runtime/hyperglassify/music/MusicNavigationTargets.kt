package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import java.util.concurrent.ConcurrentHashMap

/** Xiaomi Music 4.48.0.1 phone adapter. Native tab clicks remain authoritative. */
internal object MusicNavigationTargets {
    private val delegate = MusicNavigationDelegate(createTarget())
    private val resourceIds = ConcurrentHashMap<String, Int>()

    private val phoneTabNames = listOf(
        "menu_view_home",
        "menu_view_long_audio",
        "menu_view_quick_play",
        "menu_view_free_mode",
        "menu_view_my",
    )

    @JvmStatic
    fun delegate(): MusicNavigationDelegate = delegate

    private fun createTarget() = NativeViewBottomBarTarget(
        logName = "Xiaomi Music",
        testedVersion = "4.48.0.1",
        bottomBarClassNames = setOf(
            "com.tencent.qqmusiclite.ui.BottomNavigationView",
        ),
        tabClassNames = emptySet(),
        resolveBottomBar = { root -> root.phoneMusicBottomBar() },
        resolveTabViews = { bar -> bar.phoneMusicTabViews() },
        resolveSelectedIndex = { _, tabs ->
            tabs.indexOfFirst(View::isSelected).coerceAtLeast(0)
        },
        resolveTabBadge = View::hasPhoneMusicBadge,
        onNativeTabClick = { tab -> tab.clickPhoneMusicTab() },
        fallbackLabels = listOf("发现", "听书", "免费听", "赚钱", "我的"),
        enabled = {
            ModuleSettings.moduleHooksEnabled &&
                ModuleSettings.miMusicFloatingNavigationEnabled
        },
        useMiuixIcons = { false },
        useMonochromeIcons = { true },
        showBadges = { true },
        fallbackIcon = { label, _ -> musicFallbackIcon(label) },
        trimNativeIconTransparentPadding = true,
        nativeBarVisible = ::musicNavigationVisible,
        resolveDarkTheme = { activity, _, _, _ -> HostThemeResolver.resolve(activity) },
        nativeRefreshIntervalMs = 120L,
        minimumCaptureIntervalMs = 100L,
        allowSoftwareBackdrop = true,
        sampleBackdropOnSourceFrame = true,
        useHardwareBackdrop = true,
        pixelCopyRetryDelayMs = 800L,
        lateAttachWindowMs = 30_000L,
        manageWindowImmersion = false,
        bottomEdgeMask = false,
        overlayAllowed = { activity -> !activity.isFinishing && !activity.isDestroyed },
        onAttachDiagnostic = { detail ->
            if (detail == "floating navigation attached") {
                HookDiagnostics.available(ModuleScopePackage.MI_MUSIC, "小米音乐手机版悬浮底栏")
            } else {
                HookDiagnostics.observed(
                    ModuleScopePackage.MI_MUSIC,
                    "小米音乐手机版悬浮底栏",
                    detail,
                )
            }
        },
    )

    private fun View.phoneMusicBottomBar(): View? {
        val bar: View = resourceId("view_bottom_navigation")
            .takeIf { it != 0 }
            ?.let { id -> findViewById<View>(id) }
            ?: return null
        return bar.takeIf {
            phoneTabNames.all { name ->
                bar.resourceId(name).takeIf { id -> id != 0 }
                    ?.let { id -> bar.findViewById<View>(id) } != null
            }
        }
    }

    private fun View.phoneMusicTabViews(): List<View> = phoneTabNames
        .mapNotNull { name ->
            resourceId(name).takeIf { it != 0 }?.let { id ->
                findViewById<View>(id)
            }
        }
        .filter { it.visibility == View.VISIBLE }

    private fun View.resourceId(name: String): Int = resourceIds.computeIfAbsent(name) {
        resources.getIdentifier(name, "id", "com.miui.player")
    }

    private fun View.clickPhoneMusicTab() {
        val clickTarget = resourceId("cl_root")
            .takeIf { it != 0 }
            ?.let { id -> findViewById<View>(id) }
            ?.takeIf(View::isClickable)
            ?: this
        clickTarget.performClick()
    }

    private fun musicFallbackIcon(label: String): ImageVector = when {
        label.contains("我的") || label.contains("Profile") -> MiuixIcons.ContactsCircle
        label.contains("听书") || label.contains("长音频") -> MiuixIcons.All
        label.contains("免费") || label.contains("听") || label.contains("赚钱") ||
            label.contains("福利") -> MiuixIcons.Music
        else -> MiuixIcons.Home
    }
}

internal fun musicNavigationVisible(view: View): Boolean =
    musicNavigationVisible(view.isShown, view.height, view.translationY)

internal fun musicNavigationVisible(
    shown: Boolean,
    height: Int,
    translationY: Float,
): Boolean = shown && height > 0 && translationY < height * 0.8f

private fun View.hasPhoneMusicBadge(): Boolean {
    val visible = isShown && alpha > 0f && width > 0 && height > 0
    if (visible && tag == "BADGE_VIEW") return true
    return (this as? ViewGroup)?.let { group ->
        (0 until group.childCount).any { group.getChildAt(it).hasPhoneMusicBadge() }
    } == true
}

internal class MusicNavigationDelegate(target: NativeViewBottomBarTarget) {
    private val navigation = NativeViewBottomBarNavigation(target)

    fun prepare(activity: Activity) = navigation.prepare(activity)
    fun attach(activity: Activity) = navigation.attach(activity)
    fun attach(activity: Activity, preferredBar: View?) =
        navigation.attach(activity, preferredBar)
    fun dispose(activity: Activity) = navigation.dispose(activity)
    fun setForeground(activity: Activity, foreground: Boolean) =
        navigation.setForeground(activity, foreground)
    fun refresh(activity: Activity) = navigation.refresh(activity)
    fun onTouchEvent(activity: Activity, event: MotionEvent) =
        navigation.onTouchEvent(activity, event)
}
