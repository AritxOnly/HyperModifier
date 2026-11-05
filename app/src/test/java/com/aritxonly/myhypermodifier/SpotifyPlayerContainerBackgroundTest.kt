package com.aritxonly.myhypermodifier

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SpotifyPlayerContainerBackgroundTest {
    // Mimic Spotify's packed Compose Color accessors (distinct from View.setBackgroundColor).
    class PlaybackContainer(context: Context) : FrameLayout(context) {
        var surface = 0xff1f1f1f00000000UL.toLong()
        var reads = 0
        var writes = 0

        fun `getBackgroundColor-0d7_KjU`(): Long {
            reads++
            return surface
        }

        fun `setBackgroundColor-8_81llA`(color: Long) {
            writes++
            surface = color
        }
    }

    private val context: Context = RuntimeEnvironment.getApplication()
    private val container = PlaybackContainer(context).apply {
        layoutParams = FrameLayout.LayoutParams(900, WRAP_CONTENT)
    }
    private val player = View(context).also(container::addView)
    private val background = requireNotNull(SpotifyPlayerContainerBackground.create(container))

    @Test fun hidesOnlyTheCollapsedSurfaceAndRestoresTheLatestHostColor() {
        background.bind(player)
        background.sync(true, true)
        assertEquals(0L, container.surface)
        repeat(60) { background.sync(true, false) }
        assertEquals(1, container.reads)
        assertEquals(1, container.writes)
        val themedColor = 0xff28282800000000UL.toLong()
        container.surface = themedColor
        background.sync(true, true)
        assertEquals(0L, container.surface)
        background.sync(false, false)
        assertEquals(themedColor, container.surface)
    }

    @Test fun expansionAndUnrelatedPlayersKeepTheirOwnSurfaces() {
        val stock = container.surface
        background.bind(View(context))
        background.sync(true, true)
        assertEquals(stock, container.surface)
        background.bind(player)
        background.sync(true, true)
        container.layoutParams.height = 0
        val expandedColor = 0xff12121200000000UL.toLong()
        container.surface = expandedColor
        background.sync(true, true)
        assertEquals(expandedColor, container.surface)
        assertEquals(1, container.writes)
        assertNull(SpotifyPlayerContainerBackground.create(FrameLayout(context)))
    }
}
