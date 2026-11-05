package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TabHost
import android.widget.TabWidget
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class EcommerceNativeViewResolutionTest {
    private class TestTabHost(context: Context) : TabHost(context)
    private class TestCainiaoNavigation(context: Context) : FrameLayout(context) {
        fun getTabSize(): Int = childCount
        fun getTabView(index: Int): View = getChildAt(index)
    }

    @Test
    fun taobaoResolvesTheTabWidgetInsteadOfTheFullScreenTabHost() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val host = TestTabHost(activity)
        root.addView(
            host,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.setContentView(root)
        host.addView(TabWidget(activity).apply { id = android.R.id.tabs })
        host.addView(FrameLayout(activity).apply { id = android.R.id.tabcontent })
        host.setup()
        repeat(3) { index ->
            host.tabWidget.addView(View(activity).apply { contentDescription = "tab-$index" })
        }
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2400)

        assertEquals(3, host.tabWidget.tabCount)
        val resolved = root.findTaobaoTabWidget(setOf(TestTabHost::class.java.name))

        assertNotNull(resolved)
        assertSame(host.tabWidget, resolved)
        assertSame(host, resolved?.findTabHostAncestor())
    }

    @Test
    fun cainiaoUsesStableTabMethodsBeforeLayout() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val navigation = TestCainiaoNavigation(activity)
        repeat(5) { index ->
            navigation.addView(View(activity).apply { contentDescription = "tab-$index" })
        }
        root.addView(navigation)
        activity.setContentView(root)

        val resolved = root.findCainiaoNavigation(
            setOf(TestCainiaoNavigation::class.java.name),
        )
        val tabs = navigation.directTabViews("getTabSize", "getTabView", 10)

        assertSame(navigation, resolved)
        assertEquals(5, tabs.size)
        assertSame(navigation.getChildAt(0), tabs.first())
        assertSame(navigation.getChildAt(4), tabs.last())
    }

    @Test
    fun qqFallsBackToTheHostTabWidgetWhenTheConcreteClassIsRenamed() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val host = TestTabHost(activity)
        root.addView(
            host,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.setContentView(root)
        host.addView(TabWidget(activity).apply { id = android.R.id.tabs })
        host.addView(FrameLayout(activity).apply { id = android.R.id.tabcontent })
        host.setup()
        repeat(4) { index ->
            host.tabWidget.addView(View(activity).apply { contentDescription = "tab-$index" })
        }

        assertSame(host.tabWidget, findQQTabWidget(root))
    }

    @Test
    fun xianyuNeverRunsHomeNavigationDiscoveryOnProductDetails() {
        assertFalse(
            EcommerceNavigationTargets.specFor("com.taobao.idlefish")
                ?.allowSecondaryActivities ?: true,
        )
    }

    @Test
    fun taobaoAllowsDynamicHomeContainerActivities() {
        assertTrue(
            EcommerceNavigationTargets.specFor("com.taobao.taobao")
                ?.allowSecondaryActivities ?: false,
        )
    }

    @Test
    fun otherEcommerceHomeNavigationOnlyRunsOnMainActivities() {
        listOf(
            "com.xunmeng.pinduoduo",
            "com.jingdong.app.mall",
            "com.taobao.idlefish",
            "com.ss.android.ugc.livelite",
            "com.cainiao.wireless",
        ).forEach { packageName ->
            assertFalse(
                EcommerceNavigationTargets.specFor(packageName)
                    ?.allowSecondaryActivities ?: true,
            )
        }
    }

    @Test
    fun taobaoProductDetailActionLabelsAreRejectedEvenWhenOneIsSelected() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val labels = listOf("现在下单 承诺", "店铺", "限时补贴44元", "商品评价")
        repeat(4) { index ->
            root.addView(
                View(activity).apply {
                    isClickable = true
                    isSelected = index == 0
                    contentDescription = labels[index]
                },
            )
        }
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2400)
        repeat(4) { index ->
            root.getChildAt(index).layout(index * 270, 2200, index * 270 + 240, 2380)
        }

        assertNull(
            root.findBestBottomBar(
                exactClassNames = emptySet(),
                labels = listOf("首页", "视频", "逛逛", "消息", "购物车", "我的"),
            ),
        )
    }

    @Test
    fun taobaoHomeNavigationFallsBackToASelectedBottomRow() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        repeat(5) { index ->
            root.addView(
                View(activity).apply {
                    isClickable = true
                    isSelected = index == 0
                    contentDescription = "home-tab-$index"
                },
            )
        }
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2400)
        repeat(5) { index ->
            root.getChildAt(index).layout(index * 210, 2200, index * 210 + 190, 2380)
        }

        assertNotNull(
            root.findBestBottomBar(
                exactClassNames = emptySet(),
                labels = listOf("首页", "视频", "逛逛", "消息", "购物车", "我的"),
            ),
        )
    }

    @Test
    fun compactChromeShellsStayLocalAndSkipTheFullScreenHost() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val pageHost = FrameLayout(activity)
        val chrome = FrameLayout(activity)
        val bar = View(activity)
        chrome.addView(bar, FrameLayout.LayoutParams(1080, 180))
        pageHost.addView(chrome, FrameLayout.LayoutParams(1080, 180))
        pageHost.addView(
            View(activity),
            FrameLayout.LayoutParams(1080, 180).apply { topMargin = 2200 },
        )
        root.addView(pageHost, FrameLayout.LayoutParams(1080, 2400))
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1080, 2400)
        pageHost.layout(0, 0, 1080, 2400)
        chrome.layout(0, 2200, 1080, 2380)
        bar.layout(0, 0, 1080, 180)

        val shells = compactNativeChromeShells(bar)

        assertSame(chrome, shells.first())
        assertFalse(shells.contains(pageHost))
    }

    @Test
    fun taobaoCompactTabHostIsHiddenWithItsNavigationBackground() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = TestTabHost(activity)
        val bar = View(activity)
        host.addView(bar)
        activity.setContentView(host)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(182, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 1080, 182)
        bar.layout(0, 26, 1080, 182)

        val shells = findTaobaoChromeShells(bar)

        assertTrue(shells.contains(host))
    }
}
