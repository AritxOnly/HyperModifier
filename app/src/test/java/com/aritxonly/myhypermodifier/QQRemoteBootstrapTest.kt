package com.aritxonly.myhypermodifier

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class QQRemoteBootstrapTest {
    @Test fun splitOnlySnapshotLoadsWithoutApplicationLifecycleOrNavigationHooks() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("qq-bootstrap", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("module_hooks_enabled", true)
            .putBoolean("qq_fold_split_ratio_enabled", true)
            .putBoolean("qq_fold_navigation_immersion_enabled", true)
            .putBoolean("qq_floating_navigation_enabled", false).commit()
        ModuleSettings.setRemotePreferences(prefs)
        val loaded = CountDownLatch(1)
        ModuleSettings.onLoaded { loaded.countDown() }
        // No ensureLoaded(), Application/currentApplication lookup, Activity, or lifecycle hook.
        ModuleSettings.loadRemoteAsync()
        ModuleSettings.loadRemoteAsync()
        assertTrue("remote snapshot was never published", loaded.await(10, TimeUnit.SECONDS))
        assertTrue(ModuleSettings.isLoaded())
        assertTrue(ModuleSettings.qqFoldSplitRatioEnabled)
        assertFalse(ModuleSettings.qqFloatingNavigationEnabled)
        assertTrue(ModuleSettings.qqFoldNavigationImmersionEnabled)
    }
}
