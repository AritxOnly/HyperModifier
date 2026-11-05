package com.aritxonly.myhypermodifier

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SpotifyPlaybackCapsuleTest {
    @Test fun separatePaneKeepsPlayerWidthAndRestoresLatestNativeHeightOnExit() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity).also(activity::setContentView)
        val background = ColorDrawable(0xff101010.toInt())
        val player = View(activity).apply {
            this.background = background
            setPadding(4, 5, 6, 7)
        }
        host.addView(player, FrameLayout.LayoutParams(460, 96))
        host.layout(0, 0, 1200, 800)
        player.layout(700, 600, 1160, 696)
        val capsule = SpotifyPlaybackCapsule(activity, 0xff121212.toInt())
        val tab = ViewBackdropBounds(40, 620, 380, 54)
        val position = IntArray(2)
        player.getLocationInWindow(position)
        capsule.sync(player, tab.copy(left = position[0] + 20), stackedDock = true)
        assertEquals(24, player.paddingLeft)
        capsule.sync(player, tab, stackedDock = false)
        assertNotSame(background, player.background)
        assertEquals(54, player.layoutParams.height)
        assertEquals(460, player.layoutParams.width)
        assertEquals(4, player.paddingLeft)
        assertEquals(6, player.paddingRight)

        // A host reconfiguration sets a new stock height before the next dock measurement.
        player.layoutParams = player.layoutParams.apply { height = 80 }
        capsule.sync(player, tab.copy(height = 48), stackedDock = false)
        assertEquals(48, player.layoutParams.height)
        capsule.sync(null, tab, stackedDock = false)
        assertEquals(80, player.layoutParams.height)
        assertSame(background, player.background)
        assertEquals(5, player.paddingTop)
        assertEquals(7, player.paddingBottom)
    }
}
