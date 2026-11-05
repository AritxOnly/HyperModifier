package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.view.LayoutInflater
import androidx.compose.ui.R as ComposeR
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class InjectedNavigationContextTest {
    class ForeignActivity : Activity() {
        var packageLookups = 0

        override fun createPackageContext(packageName: String, flags: Int): Context {
            packageLookups++
            throw PackageManager.NameNotFoundException(packageName)
        }

        @Suppress("DEPRECATION")
        override fun getResources(): Resources {
            val original = super.getResources()
            return object : Resources(original.assets, original.displayMetrics, original.configuration) {
                override fun getText(id: Int): CharSequence {
                    if (id == ComposeR.string.tab) throw NotFoundException("Foreign bundle has no Compose tab string")
                    return original.getText(id)
                }
            }
        }
    }

    @Test fun accessibilityStringsUseModuleResourcesWhileWindowServicesStayWithActivity() {
        val controller = Robolectric.buildActivity(ForeignActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertThrows(Resources.NotFoundException::class.java) { activity.getString(ComposeR.string.tab) }
            assertThrows(PackageManager.NameNotFoundException::class.java) {
                activity.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            }
            val module = RuntimeEnvironment.getApplication()
            val context = InjectedNavigationContext(activity, module.resources)
            repeat(20) {
                assertEquals(module.getString(ComposeR.string.tab), context.getString(ComposeR.string.tab))
            }
            assertEquals(1, activity.packageLookups)
            assertSame(module.resources, context.resources)
            assertSame(module.assets, context.assets)
            assertSame(activity.getSystemService(Context.WINDOW_SERVICE), context.getSystemService(Context.WINDOW_SERVICE))
            assertSame(context, LayoutInflater.from(context).context)
            assertNotNull(context.theme)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
