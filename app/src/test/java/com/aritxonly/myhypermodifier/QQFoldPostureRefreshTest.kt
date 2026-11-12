package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import android.provider.Settings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class QQFoldPostureRefreshTest {
    private lateinit var manufacturer: String
    private var master = false
    private var split = false

    object Native {
        var directions = 0
        var splits = 0
        var received: Configuration? = null
        @JvmStatic fun orientation(activity: Activity) {
            directions++
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        @JvmStatic fun configuration(config: Configuration, activity: Activity, show: Boolean) {
            splits++
            received = config
        }
    }

    @Before fun setup() {
        manufacturer = Build.MANUFACTURER
        master = ModuleSettings.moduleHooksEnabled
        split = ModuleSettings.qqFoldSplitRatioEnabled
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Xiaomi")
        ModuleSettings.moduleHooksEnabled = true
        ModuleSettings.qqFoldSplitRatioEnabled = true
        Native.directions = 0; Native.splits = 0; Native.received = null
        QQFoldPostureRefresh.prepare(
            Native::class.java.getDeclaredMethod("orientation", Activity::class.java),
            Native::class.java.getDeclaredMethod("configuration", Configuration::class.java,
                Activity::class.java, Boolean::class.javaPrimitiveType))
    }

    @After fun cleanup() {
        QQFoldPostureRefresh.detach()
        ModuleSettings.moduleHooksEnabled = master
        ModuleSettings.qqFoldSplitRatioEnabled = split
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
    }

    @Test fun coalescesPostureNotificationsAndUsesNativeOrientationAndCurrentConfiguration() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = host.get()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            QQFoldPostureRefresh.attach(activity)
            QQFoldPostureRefresh.attach(activity)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, Native.directions)
            assertEquals(0, Native.splits) // Startup must preserve a native fullscreen page.
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
            host.pause().stop() // Posture updates must also work when QQ is in the background.
            repeat(2) {
                activity.contentResolver.notifyChange(Settings.Global.getUriFor("device_posture"), null)
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, Native.directions)
            assertEquals(1, Native.splits)
            assertSame(activity.resources.configuration, Native.received)
        } finally { QQFoldPostureRefresh.detach(); host.destroy() }
    }

    @Test fun disablingAndDetachingCancelQueuedNativeWork() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = host.get()
            QQFoldPostureRefresh.attach(activity)
            QQFoldPostureRefresh.detach(activity)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, Native.directions)
            QQFoldPostureRefresh.attach(activity)
            ModuleSettings.qqFoldSplitRatioEnabled = false
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, Native.directions)
            assertEquals(0, Native.splits)
        } finally { QQFoldPostureRefresh.detach(); host.pause().stop().destroy() }
    }
}
