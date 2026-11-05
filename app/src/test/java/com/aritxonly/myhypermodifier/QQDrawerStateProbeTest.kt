package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class QQDrawerStateProbeTest {
    private class TestDrawer(context: Context) : FrameLayout(context) {
        var open = false
        fun q(): Boolean = open
        fun p(): Boolean = false
    }

    private class TestPanel(context: Context) : FrameLayout(context)

    @Test
    fun tracksTheSideDrawerPanelFromClosedToOpenAndBack() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val drawer = TestDrawer(activity)
        val panel = TestPanel(activity)
        val bar = View(activity)
        panel.addView(bar, FrameLayout.LayoutParams(200, 100))
        drawer.addView(panel, FrameLayout.LayoutParams(300, 200))
        layout(drawer, 300, 200)
        layout(panel, 300, 200)
        panel.translationX = -300f
        val probe = probe()

        assertFalse(probe.isOpen(bar))

        panel.translationX = -120f
        assertTrue(probe.isOpen(bar))

        panel.translationX = 0f
        assertTrue(probe.isOpen(bar))

        panel.translationX = -300f
        assertFalse(probe.isOpen(bar))
    }

    @Test
    fun startsHiddenWhenBoundWhileTheDrawerIsAlreadyOpen() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val drawer = TestDrawer(activity).apply { open = true }
        val panel = TestPanel(activity)
        val bar = View(activity)
        panel.addView(bar, FrameLayout.LayoutParams(200, 100))
        drawer.addView(panel, FrameLayout.LayoutParams(300, 200))
        layout(drawer, 300, 200)
        layout(panel, 300, 200)

        assertTrue(probe().isOpen(bar))
    }

    private fun probe() = QQDrawerStateProbe(
        TestDrawer::class.java.name,
        TestPanel::class.java.name,
    )

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }
}
