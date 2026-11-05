package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class DouyinNavigationPolicyTest {
    @Test fun reorderedServerTabsPreserveNativeIndicesAndSeparatePublish() {
        val ids = listOf("homepage_profile", "homepage_publish", "homepage_home", "homepage_notification")
        val labels = ids.mapIndexed { index, id -> DouyinNavigationPolicy.fallbackLabel(id, index) }
        assertEquals(listOf("我", "发布", "首页", "消息"), labels)
        assertEquals(listOf(0, 2, 3), NativeBottomBarPolicy.visibleNavigationIndices(labels, 1) { _, _ -> true })
        assertEquals(listOf(0, 1, 2), NativeBottomBarPolicy.visibleNavigationIndices(
            listOf("商城", "首页", "我"), -1) { _, _ -> true })
    }

    @Test fun moduleAndSystemPresetsRespectTheNewScope() {
        assertTrue(ModifierSettingsPresets.moduleDefault().douyinFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().douyinFloatingNavigationEnabled)
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.ss.android.ugc.aweme"))
    }

    @Test fun videoClassificationIsSeparateFromDouyinThemeSetting() {
        assertTrue(DouyinNavigationPolicy.isVideoPage("homepage_home", "推荐"))
        assertTrue(DouyinNavigationPolicy.isVideoPage("homepage_familiar", "朋友"))
        assertFalse(DouyinNavigationPolicy.isVideoPage("homepage_notification", "消息"))
        assertFalse(DouyinNavigationPolicy.isVideoPage("homepage_profile", "我"))
        assertTrue(HostThemePolicy.darkFromUiMode(0x21))
        assertFalse(HostThemePolicy.darkFromUiMode(0x11))
        assertFalse(HostThemePolicy.darkFromUiMode(0x00))
    }

    @Test fun controlsOnlyMoveUpWhenTheSettledContentCrossesTheDock() {
        assertEquals(0f, DouyinNavigationPolicy.controlsTranslation(2300f, 2400), 0f)
        assertEquals(0f, DouyinNavigationPolicy.controlsTranslation(2400f, 2400), 0f)
        assertEquals(-1f, DouyinNavigationPolicy.controlsTranslation(2401f, 2400), 0f)
        assertEquals(-141f, DouyinNavigationPolicy.controlsTranslation(2541f, 2400), 0f)
        // The policy never lowers native content that is already higher than the dock.
        assertEquals(0f, DouyinNavigationPolicy.controlsTranslation(2100f, 2400), 0f)
    }

    @Test fun progressUsesPaintedBottomAndDoesNotAccumulateItsOwnTranslation() {
        val first = DouyinNavigationPolicy.progressTranslation(5f, 5f, 2500f, 10f, 2400, 2f)
        assertEquals(-107f, first, 0f)
        assertEquals(first, DouyinNavigationPolicy.progressTranslation(
            5f, first, 2388f, 10f, 2400, 2f,
        ), 0f)
        assertEquals(2f, 2400f - (2500f + first - 5f + 10f), 0f)
        assertEquals(5f, DouyinNavigationPolicy.progressTranslation(5f, 5f, 2300f, 10f, 2400, 2f), 0f)
        // Legacy 1px LineProgressBar at a fractional density: gap is still exactly 2dp.
        val legacy = DouyinNavigationPolicy.progressTranslation(0f, 0f, 2653f, 1f, 2400, 6.5f)
        assertEquals(6.5f, 2400f - (2653f + legacy + 1f), 0f)
    }

}
