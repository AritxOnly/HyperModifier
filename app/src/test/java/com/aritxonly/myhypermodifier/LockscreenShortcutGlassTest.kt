package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class LockscreenShortcutGlassTest {
    private val oldEnabled = ModuleSettings.moduleHooksEnabled
    private val oldCompat = ModuleSettings.systemUiCompatibilityMode
    private val oldDisabled = ModuleSettings.disableShadeGlassHooks
    private val oldGlass = ModuleSettings.lockscreenShortcutSoftGlassEnabled
    private val oldSize = ModuleSettings.lockscreenShortcutGlassSize
    @After fun restore() {
        ModuleSettings.moduleHooksEnabled = oldEnabled
        ModuleSettings.systemUiCompatibilityMode = oldCompat
        ModuleSettings.disableShadeGlassHooks = oldDisabled
        ModuleSettings.lockscreenShortcutSoftGlassEnabled = oldGlass
        ModuleSettings.lockscreenShortcutGlassSize = oldSize
    }
    @Test fun settingsRoundTripAndPresets() {
        val context = RuntimeEnvironment.getApplication()
        val selected = ModifierSettings(lockscreenShortcutSoftGlassEnabled = true, lockscreenShortcutGlassSize = 72f,
            lockscreenNotificationBottomEnabled = true, lockscreenNotificationBottomAnchor = LockscreenBottomPolicy.SHORTCUTS,
            lockscreenNotificationBottomGap = 36f)
        ModifierSettingsStore.save(context, selected)
        assertTrue(ModifierSettingsStore.load(context).lockscreenShortcutSoftGlassEnabled)
        assertEquals(72f, ModifierSettingsStore.load(context).lockscreenShortcutGlassSize)
        val bundle = ModifierSettingsStore.toBundle(selected)
        assertTrue(bundle.getBoolean(LockscreenShortcutGlassHooks.KEY))
        assertEquals(72f, bundle.getFloat("lockscreen_shortcut_glass_size"))
        assertTrue(bundle.getBoolean("lockscreen_notification_bottom_enabled"))
        assertEquals(1, bundle.getInt("lockscreen_notification_bottom_anchor"))
        assertEquals(36f, bundle.getFloat("lockscreen_notification_bottom_gap"))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        assertFalse(ModifierSettingsStore.load(context).lockscreenShortcutSoftGlassEnabled)
        assertFalse(ModifierSettingsStore.load(context).lockscreenNotificationBottomEnabled)
        ModifierSettingsStore.restoreBackup(context, backup)
        val restored = ModifierSettingsStore.load(context)
        assertTrue(restored.lockscreenNotificationBottomEnabled)
        assertEquals(36f, restored.lockscreenNotificationBottomGap)
        assertEquals(LockscreenBottomPolicy.SHORTCUTS, restored.lockscreenNotificationBottomAnchor)
        assertEquals(72f, restored.lockscreenShortcutGlassSize)
        assertFalse(ModifierSettingsPresets.moduleDefault().lockscreenShortcutSoftGlassEnabled)
    }
    @Test fun copiedDiscKeepsGeneratedIdOutlineAndMaterialGeneration() {
        val disc = ShortcutDisc(RuntimeEnvironment.getApplication())
        assertTrue(disc.id != View.NO_ID)
        assertFalse(disc.isClickable)
        assertFalse(disc.isFocusable)
        disc.layout(0, 0, 160, 160)
        disc.setShape(100, 100)
        val element = disc.getChildAt(0) as MaterialElement
        assertEquals(30, element.left)
        assertEquals(100, element.width)
        val shape = element.outlineProvider
        var applies = 0
        val dress: (ImageView) -> Unit = { it.outlineProvider = ViewOutlineOverride; applies++ }
        disc.dress(1, dress)
        disc.dress(1, dress)
        assertEquals(1, applies)
        assertSame(shape, element.outlineProvider)
        disc.dress(2, dress)
        assertEquals(2, applies)
        disc.setShape(120, 120)
        assertEquals(120, element.width)
        assertEquals(2, applies)
    }
    private object ViewOutlineOverride : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setEmpty()
    }
    @Test fun rootSiblingDiscsFollowGenericButtonsAndCleanUpOnDisable() {
        ModuleSettings.moduleHooksEnabled = true
        ModuleSettings.systemUiCompatibilityMode = false
        ModuleSettings.disableShadeGlassHooks = false
        ModuleSettings.lockscreenShortcutSoftGlassEnabled = true
        ModuleSettings.lockscreenShortcutGlassSize = 56f
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = FrameLayout(activity.get())
        val branch = FrameLayout(activity.get())
        host.addView(branch, FrameLayout.LayoutParams(-1, -1))
        val left = View(activity.get())
        val right = View(activity.get())
        val background = ColorDrawable(0xff444444.toInt())
        left.background = background
        branch.addView(left, FrameLayout.LayoutParams(40, 40))
        branch.addView(right, FrameLayout.LayoutParams(40, 40))
        activity.get().setContentView(host)
        host.layout(0, 0, 500, 1000)
        branch.layout(0, 0, 500, 1000)
        left.layout(30, 800, 70, 840)
        right.layout(430, 800, 470, 840)
        var applications = 0
        var generation = 1
        val material = object : ShortcutMaterial {
            override fun generation(context: Context) = generation
            override fun currentGeneration() = generation
            override fun material(view: ImageView) { applications++ }
        }
        var locked = true
        val controller = HmcShortcutController(host, left, right, material, { locked }, { false })
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(3, host.childCount)
        assertSame(branch, host.getChildAt(2))
        val disc = host.getChildAt(0) as ShortcutDisc
        assertTrue(left !is android.view.ViewGroup)
        assertSame(background, left.background)
        assertEquals(2, applications)
        assertNotNull(left.animationMatrix)
        repeat(10) { host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(2, applications)
        assertEquals(820f - 28f * host.resources.displayMetrics.density, controller.shortcutTopIn(host), .1f)
        left.translationX = 15f
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(50f + 15f, disc.x + disc.width / 2f, 1f)
        generation++
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(4, applications)
        ModuleSettings.lockscreenShortcutGlassSize = 72f
        controller.refresh()
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals((72f * host.resources.displayMetrics.density).toInt(), disc.shapeWidth)
        locked = false
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(View.GONE, disc.visibility)
        assertNull(left.animationMatrix)
        assertNull(right.animationMatrix)
        ModuleSettings.lockscreenShortcutSoftGlassEnabled = false
        controller.refresh()
        assertEquals(1, host.childCount)
        assertSame(branch, host.getChildAt(0))
        assertSame(background, left.background)
        assertNull(left.animationMatrix)
        controller.destroy()
        activity.pause().stop().destroy()
    }
    @Test fun materialFailureCleansRootLayersAndStopsFrameRetries() {
        ModuleSettings.moduleHooksEnabled = true
        ModuleSettings.systemUiCompatibilityMode = false
        ModuleSettings.disableShadeGlassHooks = false
        ModuleSettings.lockscreenShortcutSoftGlassEnabled = true
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val host = FrameLayout(activity.get())
        val branch = FrameLayout(activity.get())
        host.addView(branch)
        val left = View(activity.get())
        val right = View(activity.get())
        branch.addView(left)
        branch.addView(right)
        activity.get().setContentView(host)
        host.layout(0, 0, 500, 1000)
        branch.layout(0, 0, 500, 1000)
        left.layout(30, 800, 70, 840)
        right.layout(430, 800, 470, 840)
        var attempts = 0
        val material = object : ShortcutMaterial {
            override fun generation(context: Context) = 1
            override fun currentGeneration() = 1
            override fun material(view: ImageView) {
                attempts++
                throw ReflectiveOperationException("unsupported native material")
            }
        }
        val controller = HmcShortcutController(host, left, right, material, { true }, { false })
        repeat(5) { host.viewTreeObserver.dispatchOnPreDraw() }
        assertEquals(1, attempts)
        assertEquals(1, host.childCount)
        assertSame(branch, host.getChildAt(0))
        controller.destroy()
        controller.refresh()
        assertTrue(controller.shortcutTopIn(host).isNaN())
        activity.pause().stop().destroy()
    }
}
