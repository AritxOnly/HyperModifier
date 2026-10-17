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

    @Test fun videoClassificationIsSeparateFromNativeSceneTheme() {
        assertTrue(DouyinNavigationPolicy.isVideoPage("homepage_home", "推荐"))
        assertTrue(DouyinNavigationPolicy.isVideoPage("homepage_familiar", "朋友"))
        assertFalse(DouyinNavigationPolicy.isVideoPage("homepage_notification", "消息"))
        assertFalse(DouyinNavigationPolicy.isVideoPage("homepage_profile", "我"))
        assertTrue(DouyinNavigationPolicy.darkFromTitleColor(0xffeeeeee.toInt(), false))
        assertFalse(DouyinNavigationPolicy.darkFromTitleColor(0xff111111.toInt(), true))
        assertTrue(DouyinNavigationPolicy.darkFromTitleColor(0, true))
        // Native scenes can be light within the home tab, or dark within messages/profile.
        assertFalse(DouyinNavigationPolicy.resolveDarkTheme(false, 0xffffffff.toInt(), true))
        assertTrue(DouyinNavigationPolicy.resolveDarkTheme(true, 0xff111111.toInt(), false))
        assertTrue(DouyinNavigationPolicy.resolveDarkTheme(null, 0xffffffff.toInt(), false))
        assertFalse(DouyinNavigationPolicy.resolveDarkTheme(null, 0xff111111.toInt(), true))
        assertTrue(DouyinNavigationPolicy.resolveDarkTheme(null, 0, true))
    }

    @Test fun controlsClearanceRemainsConstantDuringPageSwipesAndPreservesAppReservation() {
        fun margin(offset: Int, original: Int = 47) = DouyinNavigationPolicy.controlsBottomMargin(
            original, 100, 100 + offset, 100 + offset, 2300, 2288, 0, 0,
        )
        assertEquals(112, margin(0))
        assertEquals(112, margin(-1800))
        assertEquals(112, margin(1800))
        assertEquals(150, margin(0, 150))
        assertEquals(47, DouyinNavigationPolicy.controlsBottomMargin(47, 100, 100, 100, 2100, 2288, 0, 0))
    }

    @Test fun footerReservationIsCountedOnceAndRemainsStableAfterRelayout() {
        for (offset in listOf(-1800, 0, 1800)) {
            val margin = DouyinNavigationPolicy.controlsBottomMargin(
                0, 0, offset, offset, 2656, 2400, 12, 153,
            )
            assertEquals(115, margin)
            // ctk is still 153px above its controls layer's bottom after the margin changes.
            assertEquals(2400 - 12, 2656 - margin - 153)
        }
        assertEquals(180, DouyinNavigationPolicy.controlsBottomMargin(
            180, 0, 0, 0, 2656, 2400, 12, 153,
        ))
    }

    @Test fun collectionCanReplaceTheEarlierFooterAnchorWithoutKeepingItsExtraInset() {
        val footerMargin = DouyinNavigationPolicy.controlsBottomMargin(0, 0, 0, 0, 2656, 2400, 12, 153)
        val collectionMargin = DouyinNavigationPolicy.controlsBottomMargin(0, 0, 0, 0, 2656, 2400, 12, 220)
        assertEquals(115, footerMargin)
        assertEquals(48, collectionMargin)
        assertEquals(2400 - 12, 2656 - collectionMargin - 220)
    }

    @Test fun lateFooterRemovesExactlyTheExtraReservationSeenOnTheDevice() {
        val before = DouyinNavigationPolicy.controlsBottomMargin(0, 0, 0, 0, 2656, 2395, 39, 0)
        val after = DouyinNavigationPolicy.controlsBottomMargin(0, 0, 0, 0, 2656, 2395, 39, 159)
        assertEquals(300, before)
        assertEquals(141, after)
        assertEquals(159, before - after)
        assertEquals(2395 - 39, 2656 - after - 159)
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
