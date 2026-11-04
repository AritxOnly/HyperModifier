package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.yxcorp.gifshow.homepage.kcube.actionbar.bottom.widget.BottomTabView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class KuaishouNavigationPolicyTest {
    @Test
    fun bothKuaishouPackagesAreDeclaredAndEnabledByDefault() {
        assertTrue(ModuleScopePackage.PACKAGES.contains(ModuleScopePackage.KUAISHOU))
        assertTrue(ModuleScopePackage.PACKAGES.contains(ModuleScopePackage.KUAISHOU_LITE))
        assertTrue(KuaishouNavigationTargets.enabledFor(ModuleScopePackage.KUAISHOU))
        assertTrue(KuaishouNavigationTargets.enabledFor(ModuleScopePackage.KUAISHOU_LITE))
    }

    @Test
    fun bottomBarUsesTheCommonParentOfNativeTabViews() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val bar = LinearLayout(activity)
        val labels = listOf("首页", "精选", "发布", "消息", "我")
        labels.forEach { label ->
            bar.addView(BottomTabView(activity, label), LinearLayout.LayoutParams(200, 180))
        }
        root.addView(bar, FrameLayout.LayoutParams(1000, 180))
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1200, 2400)

        assertEquals(5, bar.childCount)
        assertSame(bar, findKuaishouBottomBar(root))
    }
}
