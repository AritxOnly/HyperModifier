package com.aritxonly.myhypermodifier

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the semantics snapshot that Flutter already maintains for Android accessibility.
 * The overlay never changes the Dart AOT image and never relies on fixed tab coordinates.
 */
internal object PiliPlusSemanticsBridge {
    private const val FLUTTER_VIEW_CLASS = "io.flutter.embedding.android.FlutterView"
    private const val FLAG_SELECTED = 1 shl 2
    private const val FLAG_HIDDEN = 1 shl 13

    data class Snapshot(
        val candidates: List<PiliPlusNavigationCandidate>,
        val semanticNodeCount: Int,
        val labeledNodeCount: Int,
    )

    fun findFlutterView(root: View): View? {
        if (isFlutterView(root)) return root
        val group = root as? ViewGroup ?: return null
        repeat(group.childCount) {
            group.getChildAt(it).findFlutterViewRecursively()?.let { view -> return view }
        }
        return null
    }

    fun accessibilityBridge(flutterView: View): Any? = runCatching {
        viewFields(flutterView.javaClass).bridge.get(flutterView)
    }.getOrNull()

    /**
     * Flutter 3.47 updates its cached tree after the embedding renderer accepts semantics.
     * Enable both documented embedding paths plus the channel callback for older builds.
     */
    fun requestSemantics(flutterView: View, bridge: Any): Boolean {
        var enabled = false
        val viewAccess = viewFields(flutterView.javaClass)
        enabled = runCatching {
            val renderer = viewAccess.renderer?.get(flutterView)
            val method = renderer?.javaClass?.getMethod(
                "setSemanticsEnabled",
                Boolean::class.javaPrimitiveType,
            )
            method?.invoke(renderer, true)
            true
        }.getOrDefault(false) || enabled
        enabled = runCatching {
            val engine = viewAccess.engine?.get(flutterView) ?: return@runCatching false
            val flutterJni = engineFields(engine.javaClass).flutterJni.get(engine)
                ?: return@runCatching false
            flutterJni.javaClass.getMethod(
                "setSemanticsEnabled",
                Boolean::class.javaPrimitiveType,
            ).invoke(flutterJni, true)
            true
        }.getOrDefault(false) || enabled
        enabled = runCatching {
            val channelField = optionalField(bridge.javaClass, "accessibilityChannel")
                ?: return@runCatching false
            val channel = channelField.get(bridge) ?: return@runCatching false
            channel.javaClass.getMethod("onAndroidAccessibilityEnabled").invoke(channel)
            true
        }.getOrDefault(false) || enabled
        return enabled
    }

    fun accessibilityEnabled(bridge: Any): Boolean = runCatching {
        bridge.javaClass.getMethod("isAccessibilityEnabled").invoke(bridge) as Boolean
    }.getOrDefault(false)

    fun readCandidates(bridge: Any, screenHeightPx: Int): Snapshot = runCatching {
        val bridgeAccess = bridgeFields(bridge.javaClass)
        val tree = bridgeAccess.tree.get(bridge) as? Map<*, *> ?: return Snapshot(emptyList(), 0, 0)
        val nodes = nodeFields(bridgeAccess.nodeClass)
        val result = ArrayList<PiliPlusNavigationCandidate>(3)
        var labeled = 0
        for (value in tree.values) {
            if (value == null || !bridgeAccess.nodeClass.isInstance(value)) continue
            val rawLabel = nodes.labelText(value)
            if (rawLabel.isNullOrBlank()) continue
            labeled += 1
            val role = PiliPlusNavigationPolicy.roleForLabel(rawLabel) ?: continue
            if (nodes.readFlags(value) and FLAG_HIDDEN != 0) continue

            var clickTarget: Any? = value
            var selected = nodes.readFlags(value) and FLAG_SELECTED != 0
            var depth = 0
            while (clickTarget != null && depth < MAX_PARENT_DEPTH) {
                selected = selected || nodes.readFlags(clickTarget) and FLAG_SELECTED != 0
                if (nodes.readActions(clickTarget) and AccessibilityNodeInfo.ACTION_CLICK != 0) {
                    val bounds = (nodes.globalRect.get(clickTarget) as? Rect)
                        ?: (nodes.globalRect.get(value) as? Rect)
                    if (bounds != null && !bounds.isEmpty && bounds.centerY() <= screenHeightPx + 1) {
                        result += PiliPlusNavigationCandidate(
                            nodeId = nodes.id.getInt(clickTarget),
                            label = role,
                            selected = selected,
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    }
                    break
                }
                clickTarget = nodes.parent.get(clickTarget)
                depth += 1
            }
        }
        Snapshot(result, tree.size, labeled)
    }.getOrDefault(Snapshot(emptyList(), 0, 0))

    fun click(bridge: Any, flutterView: View, tab: PiliPlusNavigationCandidate): Boolean {
        val performed = runCatching {
            bridge.javaClass.getMethod(
                "performAction",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Bundle::class.java,
            ).invoke(
                bridge,
                tab.nodeId,
                AccessibilityNodeInfo.ACTION_CLICK,
                Bundle.EMPTY,
            ) as Boolean
        }.getOrDefault(false)
        if (performed) return true
        return dispatchTap(flutterView, tab)
    }

    fun disableSemantics(flutterView: View) {
        runCatching {
            val engine = viewFields(flutterView.javaClass).engine?.get(flutterView) ?: return
            val flutterJni = engineFields(engine.javaClass).flutterJni.get(engine) ?: return
            flutterJni.javaClass.getMethod(
                "setSemanticsEnabled",
                Boolean::class.javaPrimitiveType,
            ).invoke(flutterJni, false)
        }
    }

    private fun dispatchTap(flutterView: View, tab: PiliPlusNavigationCandidate): Boolean {
        if (!flutterView.isShown || flutterView.width <= 0 || flutterView.height <= 0) return false
        val location = IntArray(2)
        flutterView.getLocationOnScreen(location)
        val x = (tab.centerX - location[0]).toFloat()
            .coerceIn(0f, flutterView.width.toFloat())
        val y = (tab.centerY - location[1]).toFloat()
            .coerceIn(0f, flutterView.height.toFloat())
        val downAt = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downAt, downAt, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downAt, downAt + 24L, MotionEvent.ACTION_UP, x, y, 0)
        return try {
            flutterView.dispatchTouchEvent(down)
            flutterView.dispatchTouchEvent(up)
            true
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun View.findFlutterViewRecursively(): View? {
        if (isFlutterView(this)) return this
        val group = this as? ViewGroup ?: return null
        repeat(group.childCount) {
            group.getChildAt(it).findFlutterViewRecursively()?.let { view -> return view }
        }
        return null
    }

    private fun isFlutterView(view: View): Boolean =
        generateSequence(view.javaClass as Class<*>?) { it.superclass }
            .any { it.name == FLUTTER_VIEW_CLASS }

    private fun viewFields(type: Class<*>): ViewFields = viewFieldCache.getOrPut(type) {
        ViewFields(
            bridge = requiredField(type, "accessibilityBridge"),
            engine = optionalField(type, "flutterEngine"),
            renderer = optionalField(type, "flutterRenderer"),
        )
    }

    private fun engineFields(type: Class<*>): EngineFields = engineFieldCache.getOrPut(type) {
        EngineFields(flutterJni = requiredField(type, "flutterJNI"))
    }

    private fun bridgeFields(type: Class<*>): BridgeFields = bridgeFieldCache.getOrPut(type) {
        val nodeType = type.declaredClasses.firstOrNull { it.simpleName == "SemanticsNode" }
            ?: error("Missing Flutter SemanticsNode on ${type.name}")
        BridgeFields(
            tree = requiredField(type, "flutterSemanticsTree"),
            nodeClass = nodeType,
        )
    }

    private fun nodeFields(type: Class<*>): NodeFields = nodeFieldCache.getOrPut(type) {
        NodeFields(
            id = requiredField(type, "id"),
            flags = requiredField(type, "flags"),
            actions = requiredField(type, "actions"),
            label = requiredField(type, "label"),
            parent = requiredField(type, "parent"),
            globalRect = requiredField(type, "globalRect"),
            value = optionalField(type, "value"),
            hint = optionalField(type, "hint"),
            identifier = optionalField(type, "identifier"),
        )
    }

    private fun requiredField(type: Class<*>, name: String): Field =
        optionalField(type, name)
            ?: error("Missing Flutter field $name on ${type.name}")

    private fun optionalField(type: Class<*>, name: String): Field? =
        generateSequence(type as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name == name }
            ?.apply { isAccessible = true }

    private data class ViewFields(
        val bridge: Field,
        val engine: Field?,
        val renderer: Field?,
    )
    private data class EngineFields(val flutterJni: Field)
    private data class BridgeFields(val tree: Field, val nodeClass: Class<*>)
    private data class NodeFields(
        val id: Field,
        val flags: Field,
        val actions: Field,
        val label: Field,
        val parent: Field,
        val globalRect: Field,
        val value: Field?,
        val hint: Field?,
        val identifier: Field?,
    ) {
        fun labelText(node: Any): String? {
            for (field in listOf(label, value, hint, identifier)) {
                val text = field?.get(node) as? String
                if (!text.isNullOrBlank()) return text
            }
            return null
        }

        fun readFlags(node: Any): Int = (flags.get(node) as Number).toInt()
        fun readActions(node: Any): Int = actions.getInt(node)
    }

    private val viewFieldCache = ConcurrentHashMap<Class<*>, ViewFields>()
    private val engineFieldCache = ConcurrentHashMap<Class<*>, EngineFields>()
    private val bridgeFieldCache = ConcurrentHashMap<Class<*>, BridgeFields>()
    private val nodeFieldCache = ConcurrentHashMap<Class<*>, NodeFields>()
    private const val MAX_PARENT_DEPTH = 6
}
