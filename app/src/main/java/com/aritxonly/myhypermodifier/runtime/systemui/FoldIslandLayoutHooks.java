package com.aritxonly.myhypermodifier;

import android.util.DisplayMetrics;
import android.view.Display;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Commit the new X at the same point as the stock width result, before animations consume it. */
final class FoldIslandLayoutHooks {
    private static final String CONTENT = "miui.systemui.dynamicisland.window.content.DynamicIslandContentView";
    private static final String FEATURE = "折叠屏超级岛位置";
    private static final ClassValue<FoldIslandLayoutHooks> BINDINGS = new ClassValue<>() {
        @Override protected FoldIslandLayoutHooks computeValue(Class<?> type) {
            return new FoldIslandLayoutHooks();
        }
    };

    private final WeakHashMap<View, Boolean> views = new WeakHashMap<>(); // UI thread only
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refresh;
    private Method calculate, reposition, expandedShowing, screenWidth;
    private Object dimensions;
    private final WeakHashMap<View, Integer> cameraSlots = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> originalGravity = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> originalLeft = new WeakHashMap<>();
    private static final class Spacer {
        int stock, slot;
        Spacer(int stock) { this.stock = stock; }
    }
    private final WeakHashMap<View, Spacer> spacers = new WeakHashMap<>();
    private int realSpacerId, fakeSpacerId;
    private Method hasSmall;
    private Field margin, left, right, pairedLeft, pairedRight;
    private Field x, pairX, width, pairWidth, smallWidth, space, clockWidth;
    // Both hooks run on the SystemUI main thread. Re-identify the physical panel only on
    // display geometry changes; logical display 0 is reused, and WM density is user-editable.
    private static int metricsWidth, metricsHeight;
    private static boolean innerPanel;

    private boolean installed;
    private int lastMode;

    static int mode() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && !ModuleSettings.disableMediaIslandHooks ? ModuleSettings.foldIslandPosition : 0;
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> content = Class.forName(CONTENT, false, loader);
            BINDINGS.get(content).bind(module, content);
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure("com.android.systemui", FEATURE, error);
        }
    }

    private synchronized void bind(XposedModule module, Class<?> content) throws ReflectiveOperationException {
        if (installed) return;
        Class<?> base = content.getSuperclass();
        width = field(base, "bigIslandViewWidth");
        pairWidth = field(base, "bigIslandViewWidthHasSmallIsland");
        x = field(base, "bigIslandX");
        pairX = field(base, "bigIslandXHasSmallIsland");
        smallWidth = field(base, "smallIslandViewWidth");
        space = field(base, "space");
        clockWidth = field(base, "clockWidth");
        margin = field(base, "bigIslandMarginWidth");
        left = field(base, "bigIslandLeftWidth");
        right = field(base, "bigIslandRightWidth");
        pairedLeft = field(base, "bigIslandLeftWidthHasSmallIsland");
        pairedRight = field(base, "bigIslandRightWidthHasSmallIsland");
        ClassLoader loader = content.getClassLoader();
        Class<?> ids = Class.forName("miui.systemui.dynamicisland.R$id", false, loader);
        realSpacerId = ids.getField("area_cutout").getInt(null);
        fakeSpacerId = ids.getField("fake_area_cutout").getInt(null);
        Class<?> template = Class.forName("miui.systemui.dynamicisland.template.IslandTemplateBuilder", false, loader);
        Field cutout = field(template, "cutout");
        Class<?> factory = Class.forName("miui.systemui.dynamicisland.template.IslandTemplateFactory", false, loader);
        Class<?> data = base.getDeclaredMethod("getCurrentIslandData").getReturnType();
        Method updateSlot = factory.getDeclaredMethod("updateCutoutWidth", data, int.class, boolean.class);
        hasSmall = base.getDeclaredMethod("hasSmallIsland");
        calculate = content.getDeclaredMethod("calculateBigIslandWidth");
        reposition = content.getDeclaredMethod("refreshBigIslandPosition");
        expandedShowing = base.getDeclaredMethod("isExpandedShowing");
        Class<?> utils = Class.forName("miui.systemui.dynamicisland.DynamicIslandUtils", false, content.getClassLoader());
        dimensions = utils.getField("INSTANCE").get(null);
        screenWidth = utils.getDeclaredMethod("getScreenWidthOld", android.content.Context.class);
        // The last write in calculateBigIslandWidth precedes updateBigIslandLayout. The
        // latter is also called by swipe animations, so hook only the measurement commit.
        Method layout = base.getDeclaredMethod("updateBigIslandLayoutWithAnim", FrameLayout.class,
                FrameLayout.class, LinearLayout.class, FrameLayout.class, content, boolean.class);
        Method animate = base.getDeclaredMethod("doBigIslandAnim", int.class, int.class, int.class,
                float.class, FrameLayout.class, FrameLayout.class, LinearLayout.class, FrameLayout.class,
                content, boolean.class, int.class);
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            handles.add(module.hook(template.getDeclaredMethod("updateCutoutWidth", int.class))
                    .setId("fold-island-template-cutout").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (spacers.isEmpty() || mode() == 0) return chain.proceed();
                        View view = (View) cutout.get(chain.getThisObject());
                        Spacer state = spacers.get(view);
                        if (state == null || !inner(view)) return chain.proceed();
                        state.stock = (Integer) chain.getArg(0);
                        return state.stock == state.slot ? chain.proceed() : chain.proceed(new Object[]{state.slot});
                    }));
            module.deoptimize(updateSlot);
            handles.add(module.hook(animate).setId("fold-island-content-origin")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        View real = (View) chain.getArg(8);
                        View host = (View) chain.getArg(7);
                        LinearLayout row = (LinearLayout) chain.getArg(6);
                        if (host != null && real != null && row != null) {
                            alignContent(real, host, row, (Boolean) chain.getArg(9), (Float) chain.getArg(3));
                        }
                        return chain.proceed();
                    }));
            module.deoptimize(layout);
            bindSatellite(module, content, handles);
            handles.add(module.hook(base.getDeclaredMethod("setBigIslandXHasSmallIsland", int.class)).setId("fold-island-position")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        View view = (View) chain.getThisObject();
                        if (!views.containsKey(view)) views.put(view, true);
                        int mode = mode();
                        if (mode != 0) place(view, mode);
                        else cameraSlots.remove(view);
                        return result;
                    }));
            module.deoptimize(calculate);
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (var handle : handles) handle.unhook();
            throw error;
        }
        installed = true;
        lastMode = mode();
        ModuleSettings.onChanged(this::settingsChanged);
        HookDiagnostics.available("com.android.systemui", FEATURE);
    }

    static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void place(View view, int mode) throws ReflectiveOperationException {
        Display display = view.getDisplay();
        if (display == null) display = view.getContext().getDisplay();
        if (display == null || display.getDisplayId() != Display.DEFAULT_DISPLAY) return;
        if (!inner(view)) {
            cameraSlots.remove(view);
            return; // Stock cover coordinates already align the gap, including asymmetric two-island layouts.
        }
        int screen = (int) screenWidth.invoke(dimensions, view.getContext());
        boolean inner = true;
        int camera = -1;
        int holeLeft = 0;
        if (inner && mode == FoldIslandPlacement.CAMERA) {
            WindowInsets insets = view.getRootWindowInsets();
            DisplayCutout cutout = insets == null ? null : insets.getDisplayCutout();
            if (cutout == null) cutout = display.getCutout();
            if (cutout != null) {
                Rect hole = cutout.getBoundingRectTop();
                if (hole.isEmpty()) hole = cutout.getBoundingRectRight();
                if (!hole.isEmpty() && hole.top == 0) { camera = hole.centerX(); holeLeft = hole.left; }
            }
        }
        int gap = Math.max(1, Math.round(4 * view.getResources().getDisplayMetrics().density));
        boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int clock = Math.round(clockWidth.getFloat(view));
        if (inner && (camera > screen / 2 || clock > 0)) {
            // The ROM independently writes a fixed 30dp spacer into both template rows.
            // Keep the measured content widths; place the corner exclusion AFTER both
            // content areas so a long title cannot push trailing text under the lens.
            int slot = FoldIslandPlacement.cornerSlot(screen, holeLeft, camera, gap);
            cameraSlots.put(view, slot);
            margin.setInt(view, slot);
            width.setInt(view, left.getInt(view) + right.getInt(view) + slot);
            if (pairWidth.getInt(view) > 0) pairWidth.setInt(view, pairedLeft.getInt(view) + pairedRight.getInt(view) + slot);
            if (slot > 0) {
                int end = screen - gap;
                x.setInt(view, Math.max(0, end - width.getInt(view)));
                pairX.setInt(view, Math.max(0, end - pairWidth.getInt(view)));
                return;
            }
        } else cameraSlots.remove(view);
        x.setInt(view, FoldIslandPlacement.left(mode, inner, screen, width.getInt(view), 0,
                rtl, clock, camera, gap, x.getInt(view)));
        int small = smallWidth.getInt(view);
        if (small > 0 && pairWidth.getInt(view) > 0) {
            pairX.setInt(view, FoldIslandPlacement.left(mode, inner, screen, pairWidth.getInt(view),
                    small + space.getInt(view), rtl, clock, camera, gap, pairX.getInt(view)));
        }
    }

    private void alignContent(View real, View host, LinearLayout row, boolean fake, float nativeTx)
            throws ReflectiveOperationException {
        Integer slot = mode() == 0 || !inner(real) ? null : cameraSlots.get(real);
        if (!(host.getLayoutParams() instanceof FrameLayout.LayoutParams lp)) return;
        if (slot == null && !originalGravity.containsKey(host)) return;
        boolean rtl = row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        // Standard and transition templates are left area, spacer, right area.
        if (row.getChildCount() == 3) {
            View spacer = row.findViewById(fake ? fakeSpacerId : realSpacerId);
            if (spacer != null) {
                var params = spacer.getLayoutParams();
                int desired;
                if (slot != null) {
                    Spacer state = spacers.get(spacer);
                    if (state == null) spacers.put(spacer, state = new Spacer(params.width));
                    state.slot = desired = slot;
                } else {
                    Spacer state = spacers.remove(spacer);
                    desired = state == null ? params.width : state.stock;
                }
                if (params.width != desired) {
                    params.width = desired;
                    spacer.setLayoutParams(params);
                }
            }
            int index = slot != null && slot > 0 ? (rtl ? 0 : 2) : 1;
            if (spacer != null && spacer.getParent() == row && row.indexOfChild(spacer) != index) {
                row.removeView(spacer);
                row.addView(spacer, index);
            }
        }
        int gravity, offset;
        if (slot == null) {
            gravity = originalGravity.remove(host);
            offset = originalLeft.remove(host);
        } else {
            originalGravity.putIfAbsent(host, lp.gravity);
            originalLeft.putIfAbsent(host, lp.leftMargin);
            gravity = (lp.gravity & ~Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) | Gravity.LEFT;
            offset = FoldIslandPlacement.contentOrigin(((boolean) hasSmall.invoke(real) ? pairX : x).getInt(real), nativeTx);
        }
        if (lp.gravity != gravity || lp.leftMargin != offset) {
            lp.gravity = gravity;
            lp.leftMargin = offset;
            host.setLayoutParams(lp);
        }
    }

    private void bindSatellite(XposedModule module, Class<?> content,
                               ArrayList<XposedInterface.HookHandle> handles) throws ReflectiveOperationException {
        String prefix = "miui.systemui.dynamicisland.";
        ClassLoader loader = content.getClassLoader();
        Class<?> anim = Class.forName(prefix + "anim.DynamicIslandAnimationController", false, loader);
        Class<?> controller = Class.forName(prefix + "window.DynamicIslandWindowViewController", false, loader);
        Method callback = anim.getDeclaredMethod("getCallback");
        callback.setAccessible(true);
        Method handler = callback.getReturnType().getDeclaredMethod("getBigIslandStateHandler");
        Method current = handler.getReturnType().getDeclaredMethod("getCurrent");
        Method windowView = controller.getMethod("getView");
        Method active = Class.forName(prefix + "window.DynamicIslandWindowView", false, loader)
                .getDeclaredMethod("getCurrentBigIslandState");
        Field appX = field(controller, "appAnimSmallX");
        handles.add(module.hook(anim.getDeclaredMethod("getSmallIslandX")).setId("fold-camera-satellite")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (mode() != FoldIslandPlacement.CAMERA || cameraSlots.isEmpty()) return chain.proceed();
                    View view = (View) current.invoke(handler.invoke(callback.invoke(chain.getThisObject())));
                    Integer at = satelliteX(view);
                    return at == null ? chain.proceed() : at;
                }));
        handles.add(module.hook(controller.getDeclaredMethod("getSmallIslandX", content)).setId("fold-camera-app-satellite")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (mode() != FoldIslandPlacement.CAMERA || cameraSlots.isEmpty()) return chain.proceed();
                    View view = (View) chain.getArg(0);
                    if (view == null) view = (View) active.invoke(windowView.invoke(chain.getThisObject()));
                    Integer at = satelliteX(view);
                    if (at == null) return chain.proceed();
                    appX.setInt(chain.getThisObject(), at);
                    return at;
                }));
    }

    private Integer satelliteX(View view) throws IllegalAccessException {
        Integer slot = cameraSlots.get(view);
        return slot == null || slot == 0 ? null
                : pairX.getInt(view) - space.getInt(view) - smallWidth.getInt(view);
    }

    static boolean inner(View view) {
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        if (metricsWidth != metrics.widthPixels || metricsHeight != metrics.heightPixels) {
            Display display = view.getDisplay();
            if (display == null) display = view.getContext().getDisplay();
            if (display == null || display.getDisplayId() != Display.DEFAULT_DISPLAY) return false;
            Display.Mode mode = display.getMode();
            innerPanel = FoldIslandPlacement.isInnerPanel(mode.getPhysicalWidth(), mode.getPhysicalHeight());
            metricsWidth = metrics.widthPixels;
            metricsHeight = metrics.heightPixels;
        }
        return innerPanel;
    }

    private void settingsChanged() {
        int next = mode();
        if (lastMode == next) return;
        lastMode = next;
        main.removeCallbacks(refresh);
        main.post(refresh);
    }

    private void refresh() {
        for (View view : views.keySet().toArray(new View[0])) {
            if (view == null || !view.isAttachedToWindow()) continue;
            try {
                // Recalculate from current ROM data, including when reverting to stock.
                calculate.invoke(view);
                if (view.isShown() && !((boolean) expandedShowing.invoke(view))) reposition.invoke(view);
            } catch (ReflectiveOperationException e) {
                Log.w("MyHyperModifier", "Fold island layout refresh failed", e);
            }
        }
    }
}
