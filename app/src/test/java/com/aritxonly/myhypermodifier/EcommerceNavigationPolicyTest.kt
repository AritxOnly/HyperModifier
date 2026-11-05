package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcommerceNavigationPolicyTest {
    @Test
    fun resolvesBottomTabRowAndSelectedItem() {
        val bar = EcommerceNavigationPolicy.resolve(
            candidates = listOf(
                tab("我的", 800, selected = false),
                tab("首页", 200, selected = true),
                tab("消息", 500, selected = false),
            ),
            screenHeightPx = 2400,
            density = 3f,
            minimumTabs = 3,
        )

        assertEquals(listOf("首页", "消息", "我的"), bar?.tabs?.map { it.label })
        assertEquals(0, bar?.selectedIndex)
    }

    @Test
    fun rejectsTabBarInsideContent() {
        assertNull(
            EcommerceNavigationPolicy.resolve(
                candidates = listOf(
                    tab("首页", 200, top = 900, bottom = 1010),
                    tab("消息", 500, top = 900, bottom = 1010),
                    tab("我的", 800, top = 900, bottom = 1010),
                ),
                screenHeightPx = 2400,
                density = 3f,
                minimumTabs = 3,
            ),
        )
    }

    @Test
    fun duplicateRowsPreferTheLowestClickableCopy() {
        val bar = EcommerceNavigationPolicy.resolve(
            candidates = listOf(
                tab("首页", 200, bottom = 2290),
                tab("首页", 200, bottom = 2340),
                tab("购物车", 500),
                tab("我的", 800),
            ),
            screenHeightPx = 2400,
            density = 3f,
            minimumTabs = 3,
        )

        assertEquals(2340, bar?.tabs?.first()?.bottom)
        assertEquals(3, bar?.tabs?.size)
    }

    @Test
    fun labelMatchingRejectsSearchContent() {
        val labels = listOf("首页", "消息", "我的")
        assertTrue(EcommerceNavigationPolicy.labelsMatch("首页，已选中", labels))
        assertTrue(EcommerceNavigationPolicy.labelsMatch("消息\n未读", labels))
        assertFalse(EcommerceNavigationPolicy.labelsMatch("首页推荐", labels))
    }

    @Test
    fun geometryFindsIconOnlyBottomNavigation() {
        val bar = EcommerceNavigationPolicy.resolveGeometry(
            candidates = (0 until 5).map { index ->
                geometryTab(
                    left = 20 + index * 210,
                    top = 2180,
                    bottom = 2300,
                )
            },
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            minimumTabs = 3,
        )

        assertEquals(5, bar?.tabs?.size)
        assertEquals(listOf(120, 330, 540, 750, 960), bar?.tabs?.map { (it.left + it.right) / 2 })
    }

    @Test
    fun geometryRejectsProductCardRowInsideContent() {
        assertNull(
            EcommerceNavigationPolicy.resolveGeometry(
                candidates = (0 until 4).map { index ->
                    geometryTab(left = 20 + index * 260, top = 1050, bottom = 1260)
                },
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                density = 3f,
                minimumTabs = 3,
            ),
        )
    }

    @Test
    fun geometryCollapsesNestedClickTargetsInTheSameTab() {
        val parent = geometryTab(left = 20, top = 2180, bottom = 2300)
        val child = geometryTab(left = 70, top = 2200, bottom = 2280)
        val bar = EcommerceNavigationPolicy.resolveGeometry(
            candidates = listOf(
                parent,
                child,
                geometryTab(left = 300, top = 2180, bottom = 2300),
                geometryTab(left = 580, top = 2180, bottom = 2300),
                geometryTab(left = 860, top = 2180, bottom = 2300),
            ),
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            minimumTabs = 3,
        )

        assertEquals(4, bar?.tabs?.size)
    }

    @Test
    fun moduleDefaultsEnableAllSixAndSystemPresetDisablesThem() {
        val module = ModifierSettingsPresets.moduleDefault()
        assertTrue(module.pinduoduoFloatingNavigationEnabled)
        assertTrue(module.taobaoFloatingNavigationEnabled)
        assertTrue(module.jingdongFloatingNavigationEnabled)
        assertTrue(module.xianyuFloatingNavigationEnabled)
        assertTrue(module.douyinMallFloatingNavigationEnabled)
        assertTrue(module.cainiaoFloatingNavigationEnabled)

        val system = ModifierSettingsPresets.systemDefault()
        assertFalse(system.pinduoduoFloatingNavigationEnabled)
        assertFalse(system.taobaoFloatingNavigationEnabled)
        assertFalse(system.jingdongFloatingNavigationEnabled)
        assertFalse(system.xianyuFloatingNavigationEnabled)
        assertFalse(system.douyinMallFloatingNavigationEnabled)
        assertFalse(system.cainiaoFloatingNavigationEnabled)
    }

    @Test
    fun moduleScopeContainsAllAdaptedPackages() {
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.xunmeng.pinduoduo"))
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.taobao.taobao"))
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.jingdong.app.mall"))
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.taobao.idlefish"))
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.ss.android.ugc.livelite"))
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.cainiao.wireless"))
    }

    private fun tab(
        label: String,
        left: Int,
        selected: Boolean = false,
        top: Int = 2230,
        bottom: Int = 2340,
    ) = EcommerceTabCandidate(
        label = label,
        left = left,
        top = top,
        right = left + 200,
        bottom = bottom,
        clickable = true,
        selected = selected,
    )

    private fun geometryTab(
        left: Int,
        top: Int,
        bottom: Int,
    ) = EcommerceTabCandidate(
        label = "tab-$left",
        left = left,
        top = top,
        right = left + 200,
        bottom = bottom,
        clickable = true,
        selected = false,
    )
}
