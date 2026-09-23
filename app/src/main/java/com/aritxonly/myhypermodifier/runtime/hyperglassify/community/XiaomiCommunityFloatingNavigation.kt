package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.MotionEvent
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages

/** Xiaomi Community 6.6.9 adapter backed by its BottomNavView/NavItemView click chain. */
internal object XiaomiCommunityFloatingNavigation {
    private val delegate = NativeViewBottomBarNavigation(
        NativeViewBottomBarTarget(
            logName = "Xiaomi Community",
            testedVersion = "6.6.9",
            bottomBarClassNames = setOf(
                "com.xiaomi.vipaccount.ui.widget.tab.BottomNavView",
            ),
            tabClassNames = setOf(
                "com.xiaomi.vipaccount.ui.widget.tab.NavItemView",
            ),
            fallbackLabels = listOf("首页", "社区", "消息", "我的"),
            enabled = { ModuleSettings.xiaomiCommunityFloatingNavigationEnabled },
            useMiuixIcons = { ModuleSettings.xiaomiCommunityMiuixIconsEnabled },
            useMonochromeIcons = { ModuleSettings.xiaomiCommunityMonochromeIconsEnabled },
            showBadges = { ModuleSettings.xiaomiCommunityNavigationBadgesEnabled },
            fallbackIcon = ::communityMiuixIcon,
            iconScale = { 0.95f },
            contentHostMethodName = "M0",
            reservationViewMethodNames = setOf("M0", "l1"),
            trimNativeIconTransparentPadding = true,
        ),
    )

    @JvmStatic fun prepare(activity: Activity) = delegate.prepare(activity)
    @JvmStatic fun attach(activity: Activity) = delegate.attach(activity)
    @JvmStatic fun dispose(activity: Activity) = delegate.dispose(activity)
    @JvmStatic fun onTouchEvent(activity: Activity, event: MotionEvent) =
        delegate.onTouchEvent(activity, event)
}

private fun communityMiuixIcon(label: String, index: Int): ImageVector {
    val normalized = label.lowercase()
    return when {
        normalized.contains("首页") || normalized.contains("推荐") ||
            normalized.contains("home") -> MiuixIcons.Home
        normalized.contains("社区") || normalized.contains("圈") ||
            normalized.contains("forum") -> MiuixIcons.All
        normalized.contains("发布") || normalized.contains("创作") -> MiuixIcons.AppRecording
        normalized.contains("消息") || normalized.contains("message") ||
            normalized.contains("chat") -> MiuixIcons.Messages
        normalized.contains("我的") || normalized.contains("mine") ||
            normalized.contains("profile") -> MiuixIcons.ContactsCircle
        else -> listOf(
            MiuixIcons.Home,
            MiuixIcons.All,
            MiuixIcons.AppRecording,
            MiuixIcons.ContactsCircle,
        )[index.coerceIn(0, 3)]
    }
}
