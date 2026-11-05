package com.aritxonly.myhypermodifier

import android.app.Activity
import android.graphics.Color
import androidx.core.view.WindowCompat
import android.view.Window
import android.view.WindowInsets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NioEdgeToEdgeTest {
    private fun fits(window: Window) = Window::class.java.getDeclaredMethod("decorFitsSystemWindows")
        .apply { isAccessible = true }.invoke(window) as Boolean

    @Test fun forcesDecorLayoutAndRestoresOriginalInsetsPolicy() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val window = controller.get().window
            window.setDecorFitsSystemWindows(true)
            window.attributes = window.attributes.apply { setFitInsetsTypes(WindowInsets.Type.systemBars()) }
            val policy = NioEdgeToEdge(window)
            repeat(3) {
                policy.setEnabled(true)
                policy.setEnabled(true)
                assertFalse(fits(window))
                assertEquals(0, window.attributes.fitInsetsTypes and WindowInsets.Type.navigationBars())
                assertNotEquals(0, window.attributes.fitInsetsTypes and WindowInsets.Type.statusBars())
                policy.setEnabled(false)
                policy.setEnabled(false)
                assertTrue(fits(window))
                assertEquals(WindowInsets.Type.systemBars(), window.attributes.fitInsetsTypes)
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test @Config(qualifiers = "night") fun lightOnlyImmersionKeepsDarkButtonsAndRestoresNativeColors() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val window = activity.window
            window.navigationBarColor = Color.RED
            val insets = WindowCompat.getInsetsController(window, window.decorView)
            insets.isAppearanceLightNavigationBars = false
            val immersion = InjectedBottomNavigationImmersion(activity, true, true)
            immersion.apply()
            repeat(5) { immersion.ensureApplied() }
            assertTrue(insets.isAppearanceLightNavigationBars)
            assertEquals(Color.TRANSPARENT, window.navigationBarColor)
            immersion.dispose()
            assertFalse(insets.isAppearanceLightNavigationBars)
            assertEquals(Color.RED, window.navigationBarColor)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun nativeWindowWritesAreScopedAndLatestRequestedValueIsRestored() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val otherController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val window = controller.get().window
            val other = otherController.get().window
            val policy = NioEdgeToEdge(window)
            policy.setEnabled(true)
            assertFalse(NioEdgeToEdge.nativeDecorFits(window, true))
            assertTrue(NioEdgeToEdge.nativeDecorFits(other, true))
            assertFalse(NioEdgeToEdge.nativeDecorFits(window, false))
            policy.setEnabled(false)
            assertFalse(fits(window))
            assertTrue(NioEdgeToEdge.nativeDecorFits(window, true))
        } finally {
            controller.pause().stop().destroy()
            otherController.pause().stop().destroy()
        }
    }
}
