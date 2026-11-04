package com.aritxonly.myhypermodifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiHomeNavigationPolicyTest {
    @Test fun selectionHeadersHideTheFloatingDockAcrossObfuscatedIds() {
        assertTrue(MiHomeNavigationPolicy.isSelectionHeader("已选择1项"))
        assertTrue(MiHomeNavigationPolicy.isSelectionHeader("已选择 12 项"))
        assertTrue(MiHomeNavigationPolicy.isSelectionHeader("已选3项"))
        assertTrue(MiHomeNavigationPolicy.isSelectionHeader("1 item selected"))
    }

    @Test fun ordinaryTitlesAndActionsDoNotLookLikeSelectionMode() {
        assertFalse(MiHomeNavigationPolicy.isSelectionHeader(null))
        assertFalse(MiHomeNavigationPolicy.isSelectionHeader("智能"))
        assertFalse(MiHomeNavigationPolicy.isSelectionHeader("重命名"))
        assertFalse(MiHomeNavigationPolicy.isSelectionHeader("删除"))
        assertFalse(MiHomeNavigationPolicy.isSelectionHeader("Selected devices"))
    }
}
