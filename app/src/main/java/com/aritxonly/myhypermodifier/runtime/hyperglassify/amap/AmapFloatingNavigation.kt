package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBar
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarDefaults
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabItem
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabLayout
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.LocalAdvancedMaterialSpec
import java.lang.reflect.Field
import java.util.WeakHashMap
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AMAP_TAB_BAR_CLASS = "com.autonavi.bundle.uitemplate.tab.LiteTabBar"

/** Replaces Amap's LiteTabBar visually while retaining its native click and analytics chain. */
internal object AmapFloatingNavigation {
    private val hosts = WeakHashMap<Activity, AmapNavigationHost>()
    private val pendingAttachments = WeakHashMap<Activity, Runnable>()
    private val startupSuppressors = WeakHashMap<Activity, EarlyBottomBarSuppressor>()

    @JvmStatic
    fun prepare(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity) || startupSuppressors.containsKey(activity)
        ) return

        ModuleSettings.loadImmediately(activity.applicationContext)
        if (ModuleSettings.isLoaded() && !ModuleSettings.amapFloatingNavigationEnabled) return
        startupSuppressors[activity] = EarlyBottomBarSuppressor(
            activity = activity,
            findBottomBar = {
                activity.window.decorView.findAmapDescendantByClassName(AMAP_TAB_BAR_CLASS)
            },
        ).also(EarlyBottomBarSuppressor::start)
    }

    @JvmStatic
    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || hosts.containsKey(activity) ||
            pendingAttachments.containsKey(activity)
        ) return

        ModuleSettings.markLoaded(activity.applicationContext)
        if (ModuleSettings.isLoaded() && !ModuleSettings.amapFloatingNavigationEnabled) {
            startupSuppressors.remove(activity)?.restore()
            return
        }
        prepare(activity)
        val suppressor = startupSuppressors[activity] ?: return
        val startedAt = SystemClock.uptimeMillis()
        lateinit var retry: Runnable
        retry = Runnable {
            if (activity.isFinishing || activity.isDestroyed) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            ModuleSettings.markLoaded(activity.applicationContext)
            val settingsTimedOut = SystemClock.uptimeMillis() - startedAt >= SETTINGS_WAIT_TIMEOUT_MS
            if (!ModuleSettings.isLoaded() && !settingsTimedOut) {
                activity.window.decorView.postDelayed(retry, SETTINGS_RETRY_MS)
                return@Runnable
            }
            if (!ModuleSettings.amapFloatingNavigationEnabled) {
                pendingAttachments.remove(activity)
                startupSuppressors.remove(activity)?.restore()
                return@Runnable
            }
            suppressor.restore()
            val host = AmapNavigationHost.create(activity)
            if (host == null && SystemClock.uptimeMillis() - startedAt < VIEW_WAIT_TIMEOUT_MS) {
                suppressor.start()
                activity.window.decorView.postDelayed(retry, VIEW_RETRY_MS)
                return@Runnable
            }
            pendingAttachments.remove(activity)
            startupSuppressors.remove(activity)
            if (host == null) {
                Log.w(TAG, "Amap 17.00.0 LiteTabBar was not found")
            } else {
                hosts[activity] = host
                Log.i(TAG, "Attached Amap 17.00.0 soft-glass LiteTabBar")
            }
        }
        pendingAttachments[activity] = retry
        activity.window.decorView.post(retry)
    }

    @JvmStatic
    fun dispose(activity: Activity) {
        pendingAttachments.remove(activity)?.let(activity.window.decorView::removeCallbacks)
        startupSuppressors.remove(activity)?.restore()
        hosts.remove(activity)?.dispose()
    }

    @JvmStatic
    fun onTouchEvent(activity: Activity, event: MotionEvent) {
        hosts[activity]?.onTouchEvent(event)
    }

    private const val SETTINGS_RETRY_MS = 32L
    private const val SETTINGS_WAIT_TIMEOUT_MS = 1_500L
    private const val VIEW_RETRY_MS = 16L
    private const val VIEW_WAIT_TIMEOUT_MS = 8_000L
    private const val TAG = "MyHyperModifier"
}

private data class AmapTabState(
    val nativeIndex: Int,
    val label: String,
    val identity: String,
    val icons: NativeTabIconPair?,
    val isLongPressVoiceTab: Boolean,
)

private data class AmapNavigationState(
    val tabs: List<AmapTabState> = emptyList(),
    val selectedIndex: Int = 0,
    val visible: Boolean = false,
    val navigationLiftDp: Float = 24f,
)

private class AmapNavigationHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val nativeTabBar: View,
    samplingView: View,
) {
    private var state by mutableStateOf(AmapNavigationState())
    private var backdropSnapshot by mutableStateOf<ViewBackdropSnapshot?>(null)
    private val owner = InjectedViewTreeOwner()
    private val windowImmersion = InjectedBottomNavigationImmersion(activity)
    private val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val composeView = ComposeView(activity)
    private var composeWindowAttached = false
    private var lastNavigationDiagnostic: String? = null
    private val settingsRefreshRunnable = object : Runnable {
        override fun run() {
            if (!composeWindowAttached || activity.isFinishing || activity.isDestroyed) return
            if (!ModuleSettings.isLoaded()) {
                ModuleSettings.markLoaded(activity.applicationContext)
                composeView.postDelayed(this, 250L)
            }
        }
    }
    private val sampler = ViewBackdropSampler(
        source = samplingView,
        excludedView = composeView,
        pixelCopyWindow = activity.window,
        usePixelCopySampling = { state.visible },
    ) { backdropSnapshot = it }
    private val iconSnapshotter = NativeTabIconSnapshotter(activity.resources, activity.theme)
    private val officialIconCache = mutableMapOf<String, NativeTabIconPair>()
    private val longPressVoiceLabel = activity.resources.getIdentifier(
        "amaphome_tab_long_press_chat",
        "string",
        activity.packageName,
    ).takeIf { it != 0 }?.let { runCatching { activity.getString(it) }.getOrNull() }
    private val originalBottomAlpha = nativeTabBar.alpha
    private val originalBottomAccessibility = nativeTabBar.importantForAccessibility
    private var replacingNativeChrome = false
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncNativeState()
        logNavigationGeometry()
        sampler.onFrame()
        true
    }

    init {
        composeView.apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AmapNavigationContent(
                    state = state,
                    backdropSnapshot = backdropSnapshot,
                    onBackdropBoundsChanged = ::setBackdropScreenBounds,
                    onDestinationSelected = ::selectDestination,
                )
            }
        }
        syncNativeState()
        val overlayLayoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            token = activity.window.decorView.windowToken
            title = "MyHyperModifier Amap navigation"
            setFitInsetsTypes(0)
        }
        windowManager.addView(composeView, overlayLayoutParams)
        composeWindowAttached = true
        ModuleSettings.onLoaded {
            composeView.post {
                if (composeWindowAttached) syncNativeState()
            }
        }
        composeView.post(settingsRefreshRunnable)
        overlayParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        composeView.post { sampler.requestCaptureBurst(900L) }
    }

    fun onTouchEvent(event: MotionEvent) = sampler.onTouchEvent(event)

    fun dispose() {
        sampler.dispose()
        composeView.removeCallbacks(settingsRefreshRunnable)
        if (overlayParent.viewTreeObserver.isAlive) {
            overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        if (composeWindowAttached) {
            runCatching { windowManager.removeViewImmediate(composeView) }
            composeWindowAttached = false
        }
        nativeTabBar.alpha = originalBottomAlpha
        nativeTabBar.importantForAccessibility = originalBottomAccessibility
        windowImmersion.dispose()
        owner.dispose()
    }

    private fun updateNativeChromeReplacement(enabled: Boolean) {
        if (enabled) {
            if (!replacingNativeChrome) {
                replacingNativeChrome = true
                windowImmersion.apply()
            }
            windowImmersion.ensureApplied()
            if (nativeTabBar.alpha != 0f) nativeTabBar.alpha = 0f
            nativeTabBar.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else if (replacingNativeChrome) {
            replacingNativeChrome = false
            nativeTabBar.alpha = originalBottomAlpha
            nativeTabBar.importantForAccessibility = originalBottomAccessibility
            windowImmersion.dispose()
        }
    }

    private fun syncNativeState() {
        val tabViews = nativeTabViews()
        val models = nativeTabModels()
        val currentModel = nativeTabBar.readAmapField("mCurrentTab")
        val nativeSelected = models.indexOfFirst { it === currentModel }.takeIf { it >= 0 }
            ?: tabViews.indexOfFirst { tab ->
                runCatching {
                    tab.javaClass.getMethod("isTabSelected").invoke(tab) as Boolean
                }.getOrDefault(false)
            }.takeIf { it >= 0 }
            ?: state.tabs.getOrNull(state.selectedIndex)?.nativeIndex
            ?: 0
        val tabs = tabViews.mapIndexed { index, tab ->
            tab.readState(index, nativeSelected == index, models.getOrNull(index))
        }.filterNot { tab ->
            ModuleSettings.amapHideLongPressVoiceTabEnabled && tab.isLongPressVoiceTab
        }
        val selected = tabs.indexOfFirst { it.nativeIndex == nativeSelected }.takeIf { it >= 0 }
            ?: state.selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val next = AmapNavigationState(
            tabs = tabs,
            selectedIndex = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
            visible = tabs.size > 1 && nativeTabBar.isAmapVisibleIgnoringAlpha(),
            navigationLiftDp = ModuleSettings.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f),
        )
        val selectionChanged = next.selectedIndex != state.selectedIndex
        val becameVisible = next.visible && !state.visible
        if (next != state) state = next
        if (selectionChanged) sampler.invalidateSamplingContext()
        updateNativeChromeReplacement(next.visible)
        composeView.visibility = if (next.visible) View.VISIBLE else View.GONE
        if (selectionChanged || becameVisible) sampler.requestCaptureBurst()
    }

    private fun logNavigationGeometry() {
        if (!composeWindowAttached || !state.visible) return
        val location = IntArray(2)
        composeView.getLocationOnScreen(location)
        val message = "AmapNav geometry lift=${state.navigationLiftDp} loaded=${ModuleSettings.isLoaded()} " +
            "settings=${ModuleSettings.loadStatus()} " +
            "panelY=${location[1]} panelHeight=${composeView.height} " +
            "panelNavInset=${composeView.rootWindowInsets?.systemWindowInsetBottom ?: -1}"
        if (message != lastNavigationDiagnostic) {
            lastNavigationDiagnostic = message
            Log.i("MyHyperModifier", message)
        }
    }

    private fun View.readState(index: Int, selected: Boolean, model: Any?): AmapTabState {
        val visibleLabel = findAmapTextViews().map { it.text?.toString().orEmpty().trim() }
            .firstOrNull { it.isNotEmpty() }
        val modelIdentity = model?.readAmapField("a")?.toString().orEmpty()
        val identity = modelIdentity.ifBlank { visibleLabel.orEmpty() }
        val label = visibleLabel ?: amapFallbackLabel(identity, index)
        val configuredLabels = listOf("a", "b").mapNotNull { methodName ->
            model?.invokeAmapNoArg(methodName)
                ?.readAmapField("f")
                ?.readAmapField("e")
                ?.toString()
        }
        val preferredIconId = resources.getIdentifier("tab_icon", "id", activity.packageName)
        val iconView = findNativeTabIconView(this, preferredIconId, emptySet())
        val dynamicIcons = iconSnapshotter.snapshot(this, iconView, selected)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val iconCacheKey = "$identity|$label|$dark"
        val officialIcons = officialIconCache[iconCacheKey]
            ?: loadAmapIconPair(activity, identity, label)?.also {
                officialIconCache[iconCacheKey] = it
            }
        return AmapTabState(
            nativeIndex = index,
            label = label,
            identity = identity,
            icons = officialIcons ?: dynamicIcons,
            isLongPressVoiceTab = (configuredLabels + label).any(::isLongPressVoiceLabel),
        )
    }

    private fun selectDestination(index: Int) {
        val nativeIndex = state.tabs.getOrNull(index)?.nativeIndex ?: return
        val tab = nativeTabViews().getOrNull(nativeIndex) ?: return
        if (index != state.selectedIndex) sampler.invalidateSamplingContext()
        state = state.copy(selectedIndex = index)
        tab.performClick()
        tab.post(::syncNativeState)
        sampler.requestCaptureBurst()
    }

    private fun setBackdropScreenBounds(screenBounds: ViewBackdropBounds) {
        val decorLocation = IntArray(2)
        activity.window.decorView.getLocationOnScreen(decorLocation)
        sampler.setNavigationBounds(
            screenBounds.copy(
                left = screenBounds.left - decorLocation[0],
                top = screenBounds.top - decorLocation[1],
            ),
        )
    }

    private fun nativeTabModels(): List<Any> =
        (nativeTabBar.readAmapField("mTabs") as? List<*>)?.filterNotNull().orEmpty()

    private fun nativeTabViews(): List<View> {
        val container = nativeTabBar.readAmapField("tabContainer") as? ViewGroup
        return container?.let { List(it.childCount) { index -> it.getChildAt(index) } }.orEmpty()
    }

    private fun isLongPressVoiceLabel(label: String): Boolean {
        val normalized = label.replace(" ", "")
        return label == longPressVoiceLabel ||
            (normalized.contains("长按") && normalized.contains("说话")) ||
            (normalized.contains("按住") && normalized.contains("说话"))
    }

    companion object {
        fun create(activity: Activity): AmapNavigationHost? = runCatching {
            val overlayParent = activity.window.decorView as? ViewGroup ?: return null
            val tabBar = overlayParent.findAmapDescendantByClassName(AMAP_TAB_BAR_CLASS)
                ?: return null
            AmapNavigationHost(
                activity = activity,
                overlayParent = overlayParent,
                nativeTabBar = tabBar,
                samplingView = overlayParent,
            )
        }.onFailure {
            Log.e("MyHyperModifier", "Could not attach Amap navigation overlay", it)
        }.getOrNull()
    }
}

@Composable
private fun AmapNavigationContent(
    state: AmapNavigationState,
    backdropSnapshot: ViewBackdropSnapshot?,
    onBackdropBoundsChanged: (ViewBackdropBounds) -> Unit,
    onDestinationSelected: (Int) -> Unit,
) {
    if (!state.visible || state.tabs.isEmpty()) return
    val dark = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val materialColors = remember(dark) { MhmPresetColors.material(dark) }
    val miuixColors = remember(dark, materialColors) { MhmPresetColors.miuix(materialColors, dark) }
    val backdrop = rememberLayerBackdrop()
    val hiddenNavigationLift = state.navigationLiftDp.coerceIn(0f, 48f).dp
    val items = state.tabs.mapIndexed { index, tab ->
        val nativeIcons = tab.icons?.takeUnless { ModuleSettings.amapMiuixIconsEnabled }
        val monochrome = nativeIcons != null && ModuleSettings.amapMonochromeIconsEnabled
        val selectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.selectedMonochrome else it.selected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(amapMiuixIcon(tab, index))
        val unselectedPainter = nativeIcons?.let {
            val bitmap = if (monochrome) it.unselectedMonochrome else it.unselected
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: rememberVectorPainter(amapMiuixIcon(tab, index))
        MiuixFloatingTabItem(
            key = index.toString(),
            label = tab.label,
            selectedIcon = selectedPainter,
            unselectedIcon = unselectedPainter,
            preserveOriginalIconColors = nativeIcons != null && !monochrome,
        )
    }

    MaterialTheme(colorScheme = materialColors) {
        MiuixTheme(colors = miuixColors) {
            CompositionLocalProvider(
                LocalAdvancedMaterialSpec provides AdvancedMaterialSpec(enabled = true),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 16.dp,
                            top = INJECTED_NAVIGATION_SHADOW_TOP_PADDING,
                            end = 16.dp,
                            bottom = 4.dp + hiddenNavigationLift,
                        )
                        .height(MiuixFloatingTabBarDefaults.Height),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewBackdropLayer(backdropSnapshot, backdrop)
                    MiuixFloatingTabBar(
                        items = items,
                        selectedKey = state.selectedIndex.toString(),
                        onItemSelected = { onDestinationSelected(it.key.toInt()) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionOnScreen()
                            onBackdropBoundsChanged(
                                ViewBackdropBounds(
                                    left = position.x.roundToInt(),
                                    top = position.y.roundToInt(),
                                    width = coordinates.size.width,
                                    height = coordinates.size.height,
                                ),
                            )
                        },
                        layout = MiuixFloatingTabLayout.Stacked,
                        backdrop = backdropSnapshot?.let { backdrop },
                    )
                }
            }
        }
    }
}

private fun amapMiuixIcon(tab: AmapTabState, index: Int): ImageVector {
    val identity = "${tab.identity} ${tab.label}".lowercase()
    return when {
        identity.contains("home") || identity.contains("首页") -> MiuixIcons.Home
        identity.contains("nearby") || identity.contains("附近") -> MiuixIcons.SearchDevice
        identity.contains("taxi") || identity.contains("打车") -> MiuixIcons.Stopwatch
        identity.contains("message") || identity.contains("chat") || identity.contains("消息") ->
            MiuixIcons.All
        identity.contains("my") || identity.contains("我的") -> MiuixIcons.ContactsCircle
        else -> listOf(
            MiuixIcons.Home,
            MiuixIcons.SearchDevice,
            MiuixIcons.Stopwatch,
            MiuixIcons.All,
            MiuixIcons.ContactsCircle,
        )[index.coerceIn(0, 4)]
    }
}

private fun amapFallbackLabel(identity: String, index: Int): String {
    val normalized = identity.lowercase()
    return when {
        normalized.contains("home") -> "首页"
        normalized.contains("nearby") -> "附近"
        normalized.contains("taxi") -> "打车"
        normalized.contains("message") || normalized.contains("chat") -> "消息"
        normalized.contains("my") -> "我的"
        normalized.contains("bus") -> "公交"
        normalized.contains("route") -> "路线"
        else -> listOf("首页", "附近", "打车", "消息", "我的")
            .getOrElse(index) { "入口 ${index + 1}" }
    }
}

private fun loadAmapIconPair(context: Context, identity: String, label: String): NativeTabIconPair? {
    val normalized = "$identity $label".lowercase()
    val base = when {
        normalized.contains("home") || normalized.contains("首页") -> "home"
        normalized.contains("nearby") || normalized.contains("附近") -> "nearby"
        normalized.contains("taxi") || normalized.contains("打车") -> "taxi"
        normalized.contains("message") || normalized.contains("消息") -> "message"
        normalized.contains("chat") -> "chat"
        normalized.contains("my") || normalized.contains("我的") -> "my"
        normalized.contains("bus") || normalized.contains("公交") -> "bus"
        normalized.contains("route") || normalized.contains("路线") -> "routes_int"
        else -> return null
    }
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val selectedName = "img_tabbar_${base}${if (dark) "_dark" else ""}"
    val unselectedName = "img_tabbar_${base}_unselected${if (dark) "_dark" else ""}"
    val selected = context.renderAmapDrawable(selectedName) ?: return null
    val unselected = context.renderAmapDrawable(unselectedName) ?: selected
    return NativeTabIconPair(
        selected = selected,
        unselected = unselected,
        selectedMonochrome = selected.toAmapMonochrome(),
        unselectedMonochrome = unselected.toAmapMonochrome(),
    )
}

private fun Context.renderAmapDrawable(name: String): ImageBitmap? = runCatching {
    val id = resources.getIdentifier(name, "drawable", packageName)
    if (id == 0) return@runCatching null
    val drawable = resources.getDrawable(id, null).mutate()
    val fallback = (resources.displayMetrics.density * 28f).roundToInt()
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: fallback
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: fallback
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap()
}.getOrNull()

private fun ImageBitmap.toAmapMonochrome(): ImageBitmap {
    val source = asAndroidBitmap()
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    pixels.indices.forEach { index -> pixels[index] = (pixels[index] ushr 24) shl 24 }
    return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

private fun View.findAmapTextViews(): List<TextView> = buildList {
    fun visit(view: View) {
        if (view is TextView && view.visibility == View.VISIBLE && !view.text.isNullOrBlank()) add(view)
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { index -> visit(group.getChildAt(index)) }
        }
    }
    visit(this@findAmapTextViews)
}

private fun View.findAmapDescendantByClassName(className: String): View? {
    if (javaClass.name == className) return this
    (this as? ViewGroup)?.let { group ->
        repeat(group.childCount) { index ->
            group.getChildAt(index).findAmapDescendantByClassName(className)?.let { return it }
        }
    }
    return null
}

private fun View.isAmapVisibleIgnoringAlpha(): Boolean {
    if (!isAttachedToWindow || visibility != View.VISIBLE || width <= 0 || height <= 0) return false
    var ancestor = parent
    while (ancestor is View) {
        if (ancestor.visibility != View.VISIBLE) return false
        ancestor = ancestor.parent
    }
    return true
}

private fun Any.readAmapField(name: String): Any? = runCatching {
    var current: Class<*>? = javaClass
    var field: Field? = null
    while (current != null && field == null) {
        field = current.declaredFields.firstOrNull { it.name == name }
        current = current.superclass
    }
    field?.apply { isAccessible = true }?.get(this)
}.getOrNull()

private fun Any.invokeAmapNoArg(name: String): Any? = runCatching {
    var current: Class<*>? = javaClass
    while (current != null) {
        try {
            return@runCatching current.getDeclaredMethod(name).apply { isAccessible = true }.invoke(this)
        } catch (_: NoSuchMethodException) {
            current = current.superclass
        }
    }
    null
}.getOrNull()
