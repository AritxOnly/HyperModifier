package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiliPlusNavigationPolicyTest {
    @Test
    fun readsCurrentOrderAndSelectionFromFlutterBottomRow() {
        val bar = PiliPlusNavigationPolicy.resolve(
            candidates = listOf(
                tab(3, "我的", false, 800),
                tab(1, "首页", true, 200),
                tab(2, "动态", false, 500),
            ),
            screenHeightPx = 2400,
            density = 3f,
        )

        assertEquals(listOf("首页", "动态", "我的"), bar?.tabs?.map { it.label })
        assertEquals("首页", bar?.selectedLabel)
    }

    @Test
    fun detailRouteWithoutHomeNavigationStaysHidden() {
        assertNull(
            PiliPlusNavigationPolicy.resolve(
                candidates = listOf(tab(1, "首页", false, 200)),
                screenHeightPx = 2400,
                density = 3f,
            ),
        )
    }

    @Test
    fun contentAboveBottomZoneIsRejected() {
        assertNull(
            PiliPlusNavigationPolicy.resolve(
                candidates = listOf(
                    tab(1, "首页", true, 200, top = 900, bottom = 1010),
                    tab(2, "动态", false, 500, top = 900, bottom = 1010),
                    tab(3, "我的", false, 800, top = 900, bottom = 1010),
                ),
                screenHeightPx = 2400,
                density = 3f,
            ),
        )
    }

    @Test
    fun separatorsAndAccessibilitySuffixesResolveLabels() {
        assertEquals("首页", PiliPlusNavigationPolicy.roleForLabel("首页，已选中"))
        assertEquals("动态", PiliPlusNavigationPolicy.roleForLabel("动态\n未选中"))
        assertEquals("我的", PiliPlusNavigationPolicy.roleForLabel("我的：标签 3，共 3"))
        assertNull(PiliPlusNavigationPolicy.roleForLabel("首页推荐"))
    }

    @Test
    fun missingSelectionKeepsPreviousDestination() {
        val bar = PiliPlusNavigationPolicy.resolve(
            candidates = listOf(
                tab(1, "首页", false, 200),
                tab(2, "动态", false, 500),
                tab(3, "我的", false, 800),
            ),
            screenHeightPx = 2400,
            density = 3f,
            previousSelectedLabel = "动态",
        )
        assertEquals("动态", bar?.selectedLabel)
    }

    @Test
    fun moduleDefaultAndScopeExposePiliPlus() {
        assertTrue(ModifierSettingsPresets.moduleDefault().piliPlusFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().piliPlusFloatingNavigationEnabled)
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.example.piliplus"))
    }

    private fun tab(
        id: Int,
        label: String,
        selected: Boolean,
        left: Int,
        top: Int = 2230,
        bottom: Int = 2340,
    ) = PiliPlusNavigationCandidate(
        nodeId = id,
        label = label,
        selected = selected,
        left = left,
        top = top,
        right = left + 200,
        bottom = bottom,
    )
}
