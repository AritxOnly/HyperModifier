package com.aritxonly.myhypermodifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QQNavigationPolicyTest {
    @Test
    fun hidesForSearchOrChatEditors() {
        assertFalse(qqNavigationShouldShow(
            barVisible = true,
            drawerOpen = false,
            editorVisible = true,
        ))
    }

    @Test
    fun hidesWhileTheAccountDrawerIsOpen() {
        assertFalse(qqNavigationShouldShow(
            barVisible = true,
            drawerOpen = true,
            editorVisible = false,
        ))
    }

    @Test
    fun showsOnlyForTheTopLevelNativeBar() {
        assertTrue(qqNavigationShouldShow(
            barVisible = true,
            drawerOpen = false,
            editorVisible = false,
        ))
        assertFalse(qqNavigationShouldShow(
            barVisible = false,
            drawerOpen = false,
            editorVisible = false,
        ))
    }

    @Test
    fun compactsOnlyTheQqDockOnTablets() {
        assertTrue(qqUsesCompactTabletDock(
            LargeScreenDevicePolicy.TABLET,
            SocialNavigationKind.QQ,
        ))
        assertFalse(qqUsesCompactTabletDock(
            LargeScreenDevicePolicy.FOLDABLE,
            SocialNavigationKind.QQ,
        ))
        assertFalse(qqUsesCompactTabletDock(
            LargeScreenDevicePolicy.TABLET,
            SocialNavigationKind.WeChat,
        ))
    }
}
