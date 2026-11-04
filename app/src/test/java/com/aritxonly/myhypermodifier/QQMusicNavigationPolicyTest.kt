package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.qqmusic.ui.minibar.music.view.MinibarRootLayout
import com.tencent.qqmusic.ui.minibar.navigation.MainDeskNavigateContainer
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
class QQMusicNavigationPolicyTest {
    @Test
    fun packageIsDeclaredAndEnabledByDefault() {
        assertTrue(ModuleScopePackage.PACKAGES.contains(ModuleScopePackage.QQ_MUSIC))
        assertTrue(ModifierSettings().qqMusicFloatingNavigationEnabled)
    }

    @Test
    fun bottomBarIsTheCommonParentOfTheFiveNativeLabels() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val bar = LinearLayout(activity)
        listOf("首页", "视频", "刷歌", "星光", "我的").forEach { label ->
            bar.addView(TextView(activity).apply { text = label }, LinearLayout.LayoutParams(200, 90))
        }
        root.addView(bar, FrameLayout.LayoutParams(1000, 90)).also {
            (bar.layoutParams as FrameLayout.LayoutParams).topMargin = 2050
        }
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 1200, 2400)
        bar.layout(0, 2200, 1200, 2290)

        assertEquals(5, bar.childCount)
        assertSame(bar, findQQMusicBottomBar(root))
    }

    @Test
    fun fallsBackToABottomClickableRowWhenLabelsChange() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val bar = LinearLayout(activity)
        repeat(5) { index ->
            bar.addView(
                TextView(activity).apply {
                    text = "nav-$index"
                    isClickable = true
                },
                LinearLayout.LayoutParams(200, 90),
            )
        }
        root.addView(bar, FrameLayout.LayoutParams(1000, 90))
        activity.setContentView(root)
        layout(root, 1200, 2400)
        bar.layout(0, 2200, 1200, 2290)

        assertEquals(5, qqMusicTabViews(root).size)
        assertSame(bar, findQQMusicBottomBar(root))
    }

    @Test
    fun exactQQMusicNavigationContainerDoesNotDependOnLabels() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val bar = MainDeskNavigateContainer(activity)
        repeat(5) {
            bar.addView(
                View(activity).apply { isClickable = true },
                LinearLayout.LayoutParams(200, 90),
            )
        }
        root.addView(bar, FrameLayout.LayoutParams(1000, 90))
        activity.setContentView(root)
        layout(root, 1200, 2400)
        bar.layout(0, 2200, 1200, 2290)

        assertSame(bar, findQQMusicBottomBar(root))
        assertEquals(5, qqMusicTabViews(bar).size)
    }

    @Test
    fun movesSharedChromeRootWithoutChangingMediaCardTranslation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val chrome = FrameLayout(activity)
        val shared = FrameLayout(activity)
        val mediaCard = MinibarRootLayout(activity).apply { translationY = 14f }
        val bar = MainDeskNavigateContainer(activity)
        repeat(5) {
            bar.addView(
                TextView(activity).apply { text = "nav-$it" },
                LinearLayout.LayoutParams(200, 90),
            )
        }
        shared.addView(mediaCard, FrameLayout.LayoutParams(1200, 221))
        shared.addView(bar, FrameLayout.LayoutParams(1168, 189).apply { topMargin = 197 })
        chrome.addView(shared, FrameLayout.LayoutParams(1200, 386))
        root.addView(chrome, FrameLayout.LayoutParams(1200, 386).apply { topMargin = 2219 })
        activity.setContentView(root)
        layout(root, 1200, 2605)
        chrome.layout(0, 2219, 1200, 2605)
        shared.layout(0, 0, 1200, 386)
        mediaCard.layout(0, 0, 1200, 221)
        bar.layout(16, 197, 1184, 386)
        assertSame(bar, findQQMusicBottomBar(activity.window.decorView))
        assertTrue(mediaCard.isShown)
        val clearance = QQMusicChromeClearance(
            setOf(MinibarRootLayout::class.java.name),
        )
        assertSame(mediaCard, clearance.findMediaCard(activity.window.decorView, bar))
        assertSame(shared, bar.parent)
        assertSame(chrome, shared.parent)
        assertTrue(with(clearance) { bar.descendsFromChromeView(shared) })
        assertTrue(with(clearance) { mediaCard.descendsFromChromeView(shared) })
        assertSame(chrome, clearance.findMovableChromeRoot(activity.window.decorView, bar, mediaCard))

        clearance.apply(activity, ViewBackdropBounds(16, 2400, 1168, 189))

        assertEquals(-40f, chrome.translationY, 0.1f)
        assertEquals(14f, mediaCard.translationY, 0f)

        clearance.restore(activity)
        assertEquals(0f, chrome.translationY, 0f)
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }
}
