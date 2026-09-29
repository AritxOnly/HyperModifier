package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class BilibiliNavigationPolicyTest {
    @Test fun hiddenMiddleTabsKeepTheNativeMineIndex() {
        val labels = listOf("首页", "动态", "发布", "会员购", "我的")
        val indices = NativeBottomBarPolicy.visibleNavigationIndices(labels, 2) { label, _ ->
            BilibiliNavigationPolicy.isVisible(label, true, false, false, true, true)
        }
        assertEquals(listOf(0, 4), indices)
        assertEquals("我的", labels[indices[1]])
    }

    @Test fun optionalPublishSlotDoesNotChangeRoleVisibility() {
        val labels = listOf("我的", "会员购", "首页", "动态")
        val indices = NativeBottomBarPolicy.visibleNavigationIndices(labels, -1) { label, _ ->
            BilibiliNavigationPolicy.isVisible(label, false, true, false, true, false)
        }
        assertEquals(listOf(0, 3), indices)
    }

    @Test fun invalidEmptyConfigurationKeepsANavigationEntryAndExcludesPublish() {
        val labels = listOf("发布", "首页", "动态")
        assertEquals(
            listOf(1),
            NativeBottomBarPolicy.visibleNavigationIndices(labels, 0) { _, _ -> false },
        )
        assertTrue(NativeBottomBarPolicy.visibleNavigationIndices(listOf("发布"), 0) { _, _ -> true }.isEmpty())
    }

    @Test fun originalBarAdaptersKeepAllIndicesByDefault() {
        assertEquals(
            listOf(0, 1, 2, 3),
            NativeBottomBarPolicy.visibleNavigationIndices(listOf("首页", "社区", "消息", "我的"), -1) { _, _ -> true },
        )
    }

    @Test fun unknownServerEntriesRemainReachable() {
        assertTrue(BilibiliNavigationPolicy.isVisible("活动", false, false, false, false, false))
        assertEquals(BilibiliNavigationPolicy.Role.OTHER, BilibiliNavigationPolicy.role(null))
    }

    @Test fun publishVisibilityIsIndependentOfNavigationAndIconsMatchHyperPiliPlusScale() {
        assertFalse(BilibiliNavigationPolicy.isVisible("发布", true, true, true, true, false))
        assertTrue(BilibiliNavigationPolicy.isVisible("动态", true, true, true, true, false))
        assertEquals(0.95f, BilibiliNavigationPolicy.iconScale("首页"), 0f)
        assertEquals(1f, BilibiliNavigationPolicy.iconScale("动态"), 0f)
        assertEquals(1f, BilibiliNavigationPolicy.iconScale("我的"), 0f)
    }

    @Test fun systemPresetDisablesReplacementAndModuleScopeIncludesOfficialApp() {
        assertTrue(ModifierSettingsPresets.moduleDefault().bilibiliFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().bilibiliFloatingNavigationEnabled)
        assertTrue(ModuleScopePackage.PACKAGES.contains("tv.danmaku.bili"))
    }

    @Test fun followHasItsOwnRoleAndVisibilityWithoutChangingNativeIndices() {
        assertEquals(BilibiliNavigationPolicy.Role.FOLLOW, BilibiliNavigationPolicy.role("关注"))
        assertEquals(BilibiliNavigationPolicy.Role.FOLLOW, BilibiliNavigationPolicy.role("Following"))
        val labels = listOf("首页", "关注", "发布", "会员购", "我的")
        assertEquals(listOf(0, 3, 4), NativeBottomBarPolicy.visibleNavigationIndices(labels, 2) { label, _ ->
            BilibiliNavigationPolicy.isVisible(label, true, true, false, true, true, true)
        })
        assertTrue(ModifierSettingsPresets.moduleDefault().bilibiliFollowTabVisible)
        assertEquals("动态", BilibiliNavigationPolicy.displayLabel("关注"))
        assertEquals("动态", BilibiliNavigationPolicy.displayLabel("动态"))
        assertFalse(BilibiliNavigationPolicy.isVisible("关注", true, true, false, true, true, true))
    }

    @Test fun splashAndStartupCoversAlwaysHideTheOverlay() {
        assertFalse(BilibiliNavigationPolicy.overlayAllowed(true, true, false, false))
        assertFalse(BilibiliNavigationPolicy.overlayAllowed(true, false, true, false))
        assertTrue(BilibiliNavigationPolicy.overlayAllowed(true, false, false, false))
    }

    @Test fun backgroundAndKeyboardHideTheOverlayUntilHomeIsInteractive() {
        assertFalse(BilibiliNavigationPolicy.overlayAllowed(false, false, false, false))
        assertFalse(BilibiliNavigationPolicy.overlayAllowed(true, false, false, true))
        assertTrue(BilibiliNavigationPolicy.overlayAllowed(true, false, false, false))
    }
}
