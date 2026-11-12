package com.aritxonly.myhypermodifier

import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class LockscreenTargetBootstrapTest {
    // Same typed dependency path as MiuiStub, with objects built before any observer exists.
    fun interface LazyValue { fun get(): Any? }
    class Stub(@JvmField val mSysUIProvider: Provider?) {
        companion object { @JvmField var INSTANCE: Stub? = null }
    }
    class Provider(@JvmField val mCentralSurfaces: LazyValue)
    class Surfaces(@JvmField val mStackScrollerController: StackController?)
    class StackController(@JvmField val mNsslControllerInjector: Injector)
    class Injector(@JvmField val keyguardPanelViewController: LazyValue)
    class Panel(@JvmField val miuiShortcutControllerLazy: LazyValue)
    class Shortcuts(@JvmField val shortcutContainerView: View?)
    private fun access() = LockscreenTargetBootstrap.Access(Stub::class.java, Surfaces::class.java,
        StackController::class.java, Injector::class.java, Panel::class.java, Shortcuts::class.java)

    @Test fun discoversAlreadyCreatedTargetsWithoutCreationOrBoundsCallbacks() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val shortcuts = Shortcuts(root)
        val panel = Panel { shortcuts }
        val injector = Injector { panel }
        val surfaces = Surfaces(StackController(injector))
        Stub.INSTANCE = Stub(Provider { surfaces })
        try {
            val snapshot = access().discover()
            assertNotNull(snapshot)
            assertSame(injector, snapshot.injector)
            assertSame(shortcuts, snapshot.shortcutController)
            assertSame(root, snapshot.shortcutRoot)
        } finally { Stub.INSTANCE = null }
    }
    @Test fun missingShortcutPluginStillReturnsNotificationController() {
        val injector = Injector { Panel { Shortcuts(null) } }
        Stub.INSTANCE = Stub(Provider { Surfaces(StackController(injector)) })
        try {
            val snapshot = access().discover()
            assertSame(injector, snapshot.injector)
            assertNull(snapshot.shortcutRoot)
        } finally { Stub.INSTANCE = null }
    }
    @Test fun cachedMetadataReadsCurrentTargetsAfterRecreation() {
        val access = access()
        assertNull(access.discover())
        val first = Injector { null }
        val second = Injector { null }
        var current = Surfaces(StackController(first))
        Stub.INSTANCE = Stub(Provider { current })
        try {
            assertSame(first, access.discover().injector)
            current = Surfaces(StackController(second))
            assertSame(second, access.discover().injector)
        } finally { Stub.INSTANCE = null }
    }
    @Test fun screenBottomOnlyDoesNotInitializeShortcutOrKeyguardLazyDependencies() {
        var panelRequests = 0
        val injector = Injector { panelRequests++; throw AssertionError("Shortcut dependency not needed") }
        Stub.INSTANCE = Stub(Provider { Surfaces(StackController(injector)) })
        try {
            assertSame(injector, access().discover(false).injector)
            assertEquals(0, panelRequests)
        } finally { Stub.INSTANCE = null }
    }

}
