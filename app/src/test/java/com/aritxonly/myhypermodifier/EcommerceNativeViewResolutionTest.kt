package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TabHost
import android.widget.TabWidget
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
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
}
