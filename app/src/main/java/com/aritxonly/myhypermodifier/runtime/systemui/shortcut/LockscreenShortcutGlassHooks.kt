package com.aritxonly.myhypermodifier

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object LockscreenShortcutGlassHooks {
    const val KEY = "lockscreen_shortcut_soft_glass_enabled"
    private val main = Handler(Looper.getMainLooper())
    private val queued = AtomicBoolean()
    private val sources = WeakHashMap<View, Source>()
    private val controllers = WeakHashMap<View, WeakReference<HmcShortcutController>>()
    private lateinit var action: Method
    private lateinit var stateDelegate: java.lang.reflect.Field
    private lateinit var stateLazy: ForeignLazyAccess
    private lateinit var state: java.lang.reflect.Field
    private lateinit var doze: java.lang.reflect.Field
    private lateinit var material: HmcShortcutMaterial
    private var ensureRecorder: (() -> Unit)? = null
    @Volatile private var lastSignature = 0L
    private var installed = false
    private val createdObserved = AtomicBoolean()
    private val attachedObserved = AtomicBoolean()

    @JvmStatic fun enabled() = ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode &&
        !ModuleSettings.disableShadeGlassHooks && ModuleSettings.lockscreenShortcutSoftGlassEnabled

    @JvmStatic fun install(module: XposedModule, loader: ClassLoader) {
        val type = Class.forName("com.android.keyguard.shortcut.MiuiShortcutController", false, loader)
        action = type.getDeclaredMethod("onSystemUIAction\$1", Bundle::class.java, String::class.java).apply { isAccessible = true }
        stateDelegate = type.getDeclaredField("statusBarStateController\$delegate").apply { isAccessible = true }
        stateLazy = ForeignLazyAccess(stateDelegate.type)
        val status = Class.forName("com.android.systemui.statusbar.StatusBarStateControllerImpl", false, loader)
        state = status.getDeclaredField("mState").apply { isAccessible = true }
        doze = status.getDeclaredField("mIsDozing").apply { isAccessible = true }
        material = HmcShortcutMaterial(loader)
        ensureRecorder = {
            ensureRecorder = null
            try { material.installRecorder(module, loader) }
            catch (error: Exception) { HookDiagnostics.failure("com.android.systemui", "快捷玻璃实时配方（使用原生回退）", error) }
        }
        val add = type.getDeclaredMethod("addShortcutViews", FrameLayout::class.java)
        module.hook(add).setId("lockscreen-hmc-shortcut-discs")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                val root = chain.getArg(0) as View
                if (createdObserved.compareAndSet(false, true)) HookDiagnostics.observed("com.android.systemui", "快捷玻璃创建回调", "已命中 addShortcutViews")
                adoptExisting(chain.thisObject, root)
                result
            }
        installed = true
        lastSignature = signature()
    }

    @JvmStatic fun adoptExisting(controller: Any, root: View) {
        if (!installed) return
        val source = sources[root] ?: Source(root).also { sources[root] = it; root.addOnAttachStateChangeListener(it) }
        source.controller = WeakReference(controller)
        source.scheduleInstall()
    }

    private fun signature() = if (enabled()) 0x100000000L or
        java.lang.Float.floatToIntBits(LockscreenBottomPolicy.size(ModuleSettings.lockscreenShortcutGlassSize)).toLong() else 0L

    @JvmStatic fun refresh() {
        if (signature() == lastSignature || !queued.compareAndSet(false, true)) return
        main.post {
            queued.set(false)
            val now = signature()
            if (now != lastSignature) {
                lastSignature = now
                if (sources.isEmpty() && enabled()) LockscreenTargetBootstrap.request()
                // A source can exist before either remote settings or plugin views are ready.
                for (source in sources.values.toList()) source.attach()
            }
        }
    }

    @JvmStatic fun shortcutTopIn(root: View): Float =
        controllers[root]?.get()?.shortcutTopIn(root) ?: Float.NaN

    private class Source(root: View) : View.OnAttachStateChangeListener {
        private val root = WeakReference(root)
        var controller: WeakReference<Any>? = null
        private var held: WeakReference<HmcShortcutController>? = null
        private var heldHost: WeakReference<View>? = null
        private val install = Runnable { attach() }
        fun scheduleInstall() {
            val view = root.get() ?: return
            // HMC scheduleInstall: immediate, next queue turn and one bounded 250ms retry.
            view.removeCallbacks(install)
            main.removeCallbacks(install)
            attach()
            view.post(install)
            main.postDelayed(install, 250L)
        }
        fun attach() {
            val root = root.get() ?: return
            if (!root.isAttachedToWindow) return
            val host = root.rootView as? ViewGroup ?: return
            val old = controllers[host]?.get()
            if (!enabled()) {
                old?.refresh()
                if (ModuleSettings.lockscreenShortcutSoftGlassEnabled) HookDiagnostics.unavailable("com.android.systemui", "快捷玻璃功能门禁",
                    "enabled=" + ModuleSettings.moduleHooksEnabled + ", compatibility=" + ModuleSettings.systemUiCompatibilityMode + ", disableGlassHooks=" + ModuleSettings.disableShadeGlassHooks)
                return
            }
            try {
                ensureRecorder?.invoke()
                val controller = controller?.get()
                val pair = resolveShortcuts(controller) ?: findShortcuts(root)
                if (pair == null) { HookDiagnostics.unavailable("com.android.systemui", "快捷玻璃目标", "已找到快捷容器，但未取得左右按钮"); return }
                val (left, right) = pair
                if (left === right) return
                if (old != null && old.owns(left, right) && old.canReuse() && held?.get() === old) { old.refresh(); return }
                old?.destroy()
                val status = controller?.let { stateLazy.value(stateDelegate.get(it)) }
                if (status == null) {
                    HookDiagnostics.unavailable("com.android.systemui", "快捷玻璃目标", "状态控制器未取得：controller=" + (controller != null) + "，通过 SystemUI 自身 Lazy 接口访问")
                    return
                }
                val replacement = HmcShortcutController(host, left, right, material,
                    { state.getInt(status) != 0 }, { doze.getBoolean(status) })
                controllers[host] = WeakReference(replacement)
                held = WeakReference(replacement)
                heldHost = WeakReference(host)
                if (attachedObserved.compareAndSet(false, true)) HookDiagnostics.observed("com.android.systemui", "快捷玻璃目标", "已取得左右按钮并注册绘制跟随；left=" + left.javaClass.simpleName + ", right=" + right.javaClass.simpleName + ", attached=" + left.isAttachedToWindow + "/" + right.isAttachedToWindow + ", state=" + state.getInt(status))
            } catch (error: Exception) { HookDiagnostics.failure("com.android.systemui", "快捷玻璃目标", error) }
        }
        override fun onViewAttachedToWindow(v: View) { scheduleInstall() }
        override fun onViewDetachedFromWindow(v: View) {
            v.removeCallbacks(install)
            main.removeCallbacks(install)
            val old = held?.get()
            old?.destroy()
            heldHost?.get()?.let { if (controllers[it]?.get() === old) controllers.remove(it) }
            held = null
            heldHost = null
        }
    }

    // HMC resolveShortcuts: prefer the actual icon supplied by the shortcut controller; the
    // resource-name fallback is generic View, deliberately not restricted to FrameLayout.
    private fun resolveShortcuts(controller: Any?): Pair<View, View>? {
        if (controller == null) return null
        fun get(left: Boolean) = action.invoke(controller,
            Bundle().apply { putBoolean("isLeftShortcutView", left) }, "getShortcutView") as? View
        return try { (get(true) ?: return null) to (get(false) ?: return null) }
        catch (_: ReflectiveOperationException) { null }
    }
    internal fun findShortcuts(root: View): Pair<View, View>? {
        var left: View? = null
        var right: View? = null
        fun visit(view: View) {
            when (HmcShortcutController.resourceName(view)) {
                "shortcut_view_left_layout" -> left = view
                "shortcut_view_right_layout" -> right = view
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        return (left ?: return null) to (right ?: return null)
    }
}
