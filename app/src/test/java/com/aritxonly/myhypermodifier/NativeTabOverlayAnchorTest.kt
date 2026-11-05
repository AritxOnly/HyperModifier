package com.aritxonly.myhypermodifier

import android.app.Activity
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NativeTabOverlayAnchorTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val root = FrameLayout(activity).also(activity::setContentView)
    private val bar = FrameLayout(activity).also { root.addView(it) }
    private val overlay = View(activity).also { root.addView(it) }
    private val params get() = overlay.layoutParams as FrameLayout.LayoutParams
    private val anchor = NativeTabOverlayAnchor(root, overlay)

    @Test fun fullWidthBackgroundWithTabsInOnePaneFollowsActualItems() {
        val first = View(activity).also { bar.addView(it) }
        val last = View(activity).also { bar.addView(it) }
        root.layout(20, 40, 1220, 840)
        bar.layout(0, 720, 1200, 800)
        // RTL visual order, with the tab strip inset from the left window edge.
        first.layout(240, 0, 440, 80)
        last.layout(40, 0, 240, 80)
        anchor.align(bar, listOf(first, last))
        assertEquals(40, params.leftMargin)
        assertEquals(400, params.width)
        assertEquals(WRAP_CONTENT, params.height)

        first.layout(960, 0, 1160, 80)
        last.layout(760, 0, 960, 80)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        anchor.align(bar, listOf(first, last))
        assertEquals(760, params.leftMargin)
        assertEquals(400, params.width)

        root.layout(0, 0, 400, 800)
        bar.layout(0, 720, 400, 800)
        first.layout(200, 0, 400, 80)
        last.layout(0, 0, 200, 80)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        anchor.align(bar, listOf(first, last))
        assertEquals(0, params.leftMargin)
        assertEquals(MATCH_PARENT, params.width)
        anchor.dispose()
    }

    @Test fun amapPanelMovesItsTouchableWindowOnlyWhenNativeGeometryChanges() {
        val panel = View(activity)
        val panelParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            token = activity.window.decorView.windowToken
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val manager = activity.windowManager
        var updates = 0
        val countingManager = object : WindowManager by manager {
            override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
                updates++
                manager.updateViewLayout(view, params)
            }
        }
        manager.addView(panel, panelParams)
        shadowOf(Looper.getMainLooper()).idle()
        root.layout(0, 0, 1200, 800)
        bar.layout(16, 720, 496, 800)
        val originalFlags = panelParams.flags
        val panelAnchor = NativeTabOverlayAnchor(root, panel, countingManager)
        panelAnchor.align(bar)
        assertEquals(480, panelParams.width)
        assertEquals(16, panelParams.x)
        assertEquals(0, panelParams.y)
        assertEquals(originalFlags, panelParams.flags)
        panelAnchor.onGlobalLayout()
        panelAnchor.align(bar)
        assertEquals(1, updates)

        bar.layout(660, 720, 1140, 800)
        panelAnchor.onGlobalLayout()
        panelAnchor.align(bar)
        assertEquals(660, panelParams.x)
        assertEquals(2, updates)
        panelAnchor.dispose()
        manager.removeViewImmediate(panel)
    }

    @Test fun onlyTheNativeStackMayReserveSpaceForThePlayer() {
        val barrier = View(activity)
        assertEquals(false, SpotifyDockLayout.hasStackedPlayer(bar, null))
        bar.addView(barrier)
        assertEquals(false, SpotifyDockLayout.hasStackedPlayer(bar, barrier))
        bar.removeView(barrier)
        root.addView(barrier)
        assertEquals(true, SpotifyDockLayout.hasStackedPlayer(bar, barrier))
    }

    @Test fun spotifySidePaneUsesNativeHeightAndPositionWithoutResizingItsBar() {
        root.layout(20, 40, 1220, 840)
        bar.layout(32, 72, 392, 136)
        val originalHeight = bar.layoutParams.height
        anchor.align(bar, matchNativeHeight = true)
        assertEquals(32, params.leftMargin)
        assertEquals(360, params.width)
        assertEquals(64, params.height)
        assertEquals(664, params.bottomMargin)
        assertEquals(Gravity.BOTTOM or Gravity.LEFT, params.gravity)
        assertEquals(originalHeight, bar.layoutParams.height)
        anchor.dispose()
    }
}
