package com.aritxonly.myhypermodifier

import android.graphics.Color
import android.graphics.Insets
import android.view.WindowInsets
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NioNavigationViewsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val labelId = 0x123001
    private val iconId = 0x123002

    private fun tab(label: String, color: Int = Color.GRAY) = LinearLayout(context).apply {
        isClickable = true
        addView(TextView(context).apply { id = labelId; text = label; setTextColor(color) })
        addView(View(context).apply { id = iconId })
    }

    @Test fun clickableScrollContainerDoesNotReplaceIndividualNativeClickTargets() {
        val bar = FrameLayout(context)
        val scroll = LinearLayout(context).apply { isClickable = true }
        val tabs = listOf(tab("发现"), tab("朋友"), tab("爱车"), tab("惊喜"), tab("我的"))
        tabs.forEach { native ->
            // A custom non-clickable Lottie child remains inside the native click wrapper.
            val wrapper = FrameLayout(context).apply { isClickable = true; addView(native.apply { isClickable = false }) }
            scroll.addView(wrapper)
        }
        bar.addView(scroll)
        val result = NioNavigationViews.tabs(bar, labelId, iconId)
        assertEquals(5, result.size)
        result.forEachIndexed { index, view -> assertSame(scroll.getChildAt(index), view) }
        var routed = false
        result[2].setOnClickListener { routed = true }
        result[2].performClick()
        assertTrue(routed)
    }

    @Test fun selectionReadsNativeStateOrUniqueLabelColorAndRejectsAmbiguity() {
        val tabs = listOf(tab("发现"), tab("朋友"), tab("爱车", Color.BLACK), tab("惊喜"), tab("我的"))
        assertEquals(2, NioNavigationViews.selectedIndex(tabs, labelId))
        tabs[4].isSelected = true
        assertEquals(4, NioNavigationViews.selectedIndex(tabs, labelId))
        tabs[4].isSelected = false
        tabs[1].findViewById<View>(iconId).isSelected = true
        assertEquals(1, NioNavigationViews.selectedIndex(tabs, labelId))
        tabs[1].findViewById<View>(iconId).isSelected = false
        tabs[2].findViewById<TextView>(labelId).setTextColor(Color.GRAY)
        assertEquals(-1, NioNavigationViews.selectedIndex(tabs, labelId))
        tabs[0].findViewById<TextView>(labelId).setTextColor(Color.RED)
        tabs[1].findViewById<TextView>(labelId).setTextColor(Color.BLUE)
        assertEquals(-1, NioNavigationViews.selectedIndex(tabs, labelId))
    }

    @Test fun onlyAppOwnedKnownIconsAreMappedAndUnknownTabsKeepNativeFallback() {
        assertEquals("discover", NioNavigationViews.iconName("发现"))
        assertEquals("friend", NioNavigationViews.iconName("朋友"))
        assertEquals("car", NioNavigationViews.iconName("爱车"))
        assertEquals("surprised", NioNavigationViews.iconName("惊喜"))
        assertEquals("mine", NioNavigationViews.iconName("我的"))
        assertNull(NioNavigationViews.iconName("未知入口"))
        assertNull(NioNavigationViews.icons(tab("发现"), "发现")) // Foreign resource set lacks NIO icons.
    }

    @Test fun removesOnlyNativeNavigationPaddingAndRestoresIt() {
        val content = object : View(context) {
            override fun getRootWindowInsets(): WindowInsets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                .build()
        }.apply {
            layoutParams = RelativeLayout.LayoutParams(100, 100)
            setPadding(3, 7, 5, 31)
        }
        val reservation = NioContentReservation(content)
        reservation.replace(true)
        assertEquals(7, content.paddingBottom)
        assertEquals(3, content.paddingLeft)
        assertEquals(7, content.paddingTop)
        assertEquals(5, content.paddingRight)
        reservation.replace(true)
        assertEquals(7, content.paddingBottom)
        // The app receives a fresh inset dispatch and rewrites padding while replacement is active.
        content.setPadding(3, 7, 5, 34)
        reservation.replace(true)
        assertEquals(10, content.paddingBottom)
        reservation.replace(false)
        assertEquals(34, content.paddingBottom)
    }

    @Test fun nativeRelativeReservationRestoresAcrossRepeatedEnableDisableCycles() {
        val content = View(context).apply {
            layoutParams = RelativeLayout.LayoutParams(100, 100).apply {
                addRule(RelativeLayout.ABOVE, 123)
                addRule(RelativeLayout.BELOW, 456)
                bottomMargin = 17
            }
        }
        val reservation = NioContentReservation(content)
        repeat(3) {
            reservation.replace(true)
            reservation.replace(true)
            val params = content.layoutParams as RelativeLayout.LayoutParams
            assertEquals(0, params.getRule(RelativeLayout.ABOVE))
            assertEquals(456, params.getRule(RelativeLayout.BELOW))
            assertEquals(17, params.bottomMargin)
            reservation.replace(false)
            reservation.replace(false)
            assertEquals(123, params.getRule(RelativeLayout.ABOVE))
        }
    }
}
