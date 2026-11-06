package com.aritxonly.myhypermodifier;

import android.graphics.Canvas;
import android.graphics.BlendMode;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Replaces only the digit-free icon with the packaged SystemUI geometry, never a theme overlay. */
final class StatusBarBatteryFrameHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final float BODY_WIDTH_SCALE = .98f;
    private static final float BODY_HEIGHT_SCALE = .94f;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final AtomicBoolean PERCENT_PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Main-thread only; values hold no View/Context/Drawable references back to weak keys.
    private static final WeakHashMap<View, Frame> FRAMES = new WeakHashMap<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static MethodHandle getTint, isInAreas, updateResources;
    private static boolean lastEnabled;

    private StatusBarBatteryFrameHooks() {}

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.statusBarSolidBatteryFrame;
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        Class<?> type = Class.forName(SYSTEM_UI + ".statusbar.views.MiuiBatteryMeterIconView", false, loader);
        for (String name : new String[]{"mLevel", "mCharging", "mQuickCharging", "mPerformanceMode",
                "mPowerSave", "mLow", "mUseTint", "mTintAreas", "mTintColor", "mDarkIntensity",
                "mLightColor", "mDarkColor", "mMiuiOptimizationEnabled"}) {
            FIELDS.put(name, type.getField(name));
        }
        Class<?> dispatcher = Class.forName(SYSTEM_UI + ".plugins.DarkIconDispatcher", false, loader);
        getTint = MethodHandles.lookup().unreflect(
                dispatcher.getDeclaredMethod("getTint", Collection.class, View.class, int.class));
        isInAreas = MethodHandles.lookup().unreflect(
                dispatcher.getDeclaredMethod("isInAreas", Collection.class, View.class));
        // Resolve the complete contract before installing any hook. No global Canvas/View hooks.
        Method draw = type.getDeclaredMethod("onDraw", Canvas.class);
        Method update = type.getDeclaredMethod("onDarkChangeInternal");
        Method clearTint = type.getDeclaredMethod("clearTint");
        Method refreshResources = type.getDeclaredMethod("updateResources$10");
        Method inflate = type.getDeclaredMethod("onFinishInflate");
        Method attach = type.getDeclaredMethod("onAttachedToWindow");
        Method detach = type.getDeclaredMethod("onDetachedFromWindow");
        updateResources = MethodHandles.lookup().unreflect(refreshResources);
        module.hook(inflate).setId("solid-battery-frame-inflate")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    ModuleSettings.ensureLoaded();
                    View view = (View) chain.getThisObject();
                    if (!FRAMES.containsKey(view)) FRAMES.put(view, null);
                    lastEnabled = enabled();
                    return lastEnabled ? null : chain.proceed();
                });
        module.hook(draw).setId("solid-battery-frame-draw")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    View view = (View) chain.getThisObject();
                    Frame frame = FRAMES.get(view);
                    // Stock attach establishes the isolated hardware layer used by its own CLEAR drawing.
                    // If another module changes that contract, never fall back to the themed drawable.
                    if (frame == null || view.getLayerType() != View.LAYER_TYPE_HARDWARE) return null;
                    frame.draw((Canvas) chain.getArg(0), view.getWidth(), view.getHeight());
                    return null;
                });
        module.hook(clearTint).setId("solid-battery-frame-clear-tint")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                        enabled() ? null : chain.proceed());
        module.hook(update).setId("solid-battery-frame-state")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    update((View) chain.getThisObject());
                    return null;
                });
        module.hook(refreshResources).setId("solid-battery-frame-resources")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!enabled()) return chain.proceed();
                    update((View) chain.getThisObject());
                    return null;
                });
        module.hook(attach).setId("solid-battery-frame-attach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    ModuleSettings.ensureLoaded();
                    lastEnabled = enabled();
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    // Track even when disabled so a later settings load can invalidate attached icons.
                    if (!FRAMES.containsKey(view)) FRAMES.put(view, null);
                    if (enabled()) update(view);
                    return result;
                });
        module.hook(detach).setId("solid-battery-frame-detach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    FRAMES.remove(chain.getThisObject());
                    return chain.proceed();
                });
        HookDiagnostics.available(SYSTEM_UI, "无数字电池实心框");
    }

    private static boolean flag(View view, String name) throws IllegalAccessException {
        return FIELDS.get(name).getBoolean(view);
    }

    private static int number(View view, String name) throws IllegalAccessException {
        return FIELDS.get(name).getInt(view);
    }

    private static void update(View view) {
        if (!view.isAttachedToWindow()) return;
        Frame frame = FRAMES.get(view);
        if (frame == null) {
            frame = new Frame(view);
            FRAMES.put(view, frame);
        }
        try {
            boolean charging = flag(view, "mCharging");
            boolean quick = flag(view, "mQuickCharging");
            boolean performance = flag(view, "mPerformanceMode");
            boolean tint = flag(view, "mUseTint");
            Collection<?> areas = (Collection<?>) FIELDS.get("mTintAreas").get(view);
            float darkIntensity = FIELDS.get("mDarkIntensity").getFloat(view);
            boolean inDarkArea = (boolean) isInAreas.invokeExact(areas, view);
            int base = Frame.color(view, inDarkArea && darkIntensity > 0f
                    ? frame.darkColorId : frame.lightColorId);
            int normal = base;
            if (tint) {
                base = (int) getTint.invokeExact(areas, view, number(view, "mTintColor"));
                normal = number(view, "mLightColor");
                if (inDarkArea) {
                    normal = androidx.core.graphics.ColorUtils.blendARGB(normal,
                            number(view, "mDarkColor"), darkIntensity);
                }
            }
            int fill = normal;
            if (!tint || flag(view, "mMiuiOptimizationEnabled")) {
                if (quick || charging) fill = Frame.color(view, frame.chargingColorId);
                else if (flag(view, "mPowerSave")) fill = Frame.color(view, frame.powerSaveColorId);
                else if (performance) fill = Frame.color(view, frame.performanceColorId);
                else if (flag(view, "mLow")) fill = Frame.color(view, frame.lowColorId);
            }
            frame.configure(number(view, "mLevel"), base, fill, charging, quick, performance,
                    frame.progressStartsAtStart(view),
                    ControlCenterBatteryPercentHooks.shouldShowPercentInside());
            view.invalidate();
        } catch (Throwable error) {
            FRAMES.put(view, null);
            HookDiagnostics.failure(SYSTEM_UI, "无数字电池实心框", error);
        }
    }

    private static void restoreSystemResources(View view) {
        try {
            updateResources.invoke(view);
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "无数字电池实心框", error);
        }
    }

    /** Settings notifications are coalesced, and unrelated settings never traverse the view registry. */
    static void refresh() {
        if (!PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            PENDING.set(false);
            boolean active = enabled();
            if (active == lastEnabled) return;
            lastEnabled = active;
            // update only replaces values for existing keys; it does not structurally alter this map.
            for (View view : FRAMES.keySet()) {
                if (view == null) continue;
                if (active) update(view);
                else {
                    restoreSystemResources(view);
                    FRAMES.put(view, null);
                    view.invalidate(); // Next frame immediately uses the untouched stock drawable.
                }
            }
        });
    }

    static void refreshControlCenterPercent() {
        if (!PERCENT_PENDING.compareAndSet(false, true)) return;
        Runnable refresh = () -> {
            PERCENT_PENDING.set(false);
            for (View view : FRAMES.keySet()) {
                if (view != null) update(view);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run();
        else MAIN.post(refresh);
    }

    private static final class Frame {
        final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint foreground = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint markClear = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint markFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint percentClear = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint percentTint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path mark;
        final Path mask = new Path();
        final Path filled = new Path();
        final Path empty = new Path();
        final int lightColorId, darkColorId, chargingColorId, lowColorId;
        final int powerSaveColorId, performanceColorId, gravityStartId;
        int lastLevel = -1;
        boolean lastStart;
        boolean percentInside;
        String levelText = "";
        final float percentBaseline;

        Frame(View view) {
            // Same transparent erasure semantics as MiuiHollowBatteryMeterIconView's
            // hollowTextPaint; erase the stroke halo too, then restore only the glyph fill.
            markClear.setBlendMode(BlendMode.CLEAR);
            markClear.setStyle(Paint.Style.FILL_AND_STROKE);
            markClear.setStrokeWidth(2f);
            markClear.setStrokeJoin(Paint.Join.ROUND);
            markClear.setStrokeCap(Paint.Cap.ROUND);
            percentClear.setBlendMode(BlendMode.CLEAR);
            percentClear.setTextAlign(Paint.Align.CENTER);
            percentClear.setTextSize(8.6f);
            percentClear.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            percentTint.setTextAlign(Paint.Align.CENTER);
            percentTint.setTextSize(8.6f);
            percentTint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            Paint.FontMetrics metrics = percentClear.getFontMetrics();
            percentBaseline = -(metrics.ascent + metrics.descent) / 2f;
            lightColorId = view.getResources().getIdentifier("light_mode_icon_color_single_tone", "color", SYSTEM_UI);
            darkColorId = view.getResources().getIdentifier("dark_mode_icon_color_single_tone", "color", SYSTEM_UI);
            chargingColorId = view.getResources().getIdentifier("status_bar_battery_charging", "color", SYSTEM_UI);
            lowColorId = view.getResources().getIdentifier("status_bar_battery_low", "color", SYSTEM_UI);
            powerSaveColorId = view.getResources().getIdentifier("status_bar_battery_power_save", "color", SYSTEM_UI);
            performanceColorId = view.getResources().getIdentifier(
                    "status_bar_battery_performance", "color", SYSTEM_UI);
            gravityStartId = view.getResources().getIdentifier(
                    "battery_meter_progress_gravity_start", "bool", SYSTEM_UI);
        }

        static int color(View view, int id) {
            return view.getResources().getColor(id, view.getContext().getTheme());
        }

        boolean progressStartsAtStart(View view) {
            return gravityStartId != 0 && view.getResources().getBoolean(gravityStartId);
        }

        void configure(int level, int base, int fill, boolean charging, boolean quick,
                       boolean performance, boolean start, boolean percentInside) {

            background.setColor(base);
            background.setAlpha(Math.round(Color.alpha(base) * .4f));
            foreground.setColor(fill);
            markFill.setColor(base);
            int contrast = androidx.core.graphics.ColorUtils.calculateLuminance(base) > .5
                    ? Color.BLACK : Color.WHITE;
            percentTint.setColor(Color.argb(26,
                    Color.red(contrast), Color.green(contrast), Color.blue(contrast)));
            this.percentInside = percentInside;
            if (level != lastLevel) levelText = String.valueOf(level);
            mark = BatteryFrameGeometry.mark(charging, quick, performance);
            if (level == lastLevel && start == lastStart) return;
            Path body = BatteryFrameGeometry.body();
            float shift = StatusBarBatteryFramePolicy.maskShift(level, start);
            mask.rewind();
            mask.addRoundRect(2.6f + shift, 3.88f, 25.594f + shift, 16.12f,
                    1f, 1f, Path.Direction.CW);
            if (!filled.op(body, mask, Path.Op.INTERSECT)
                    || !empty.op(body, mask, Path.Op.DIFFERENCE)) {
                throw new IllegalStateException("battery level path");
            }
            lastLevel = level;
            lastStart = start;
        }

        void draw(Canvas canvas, int width, int height) {
            if (width <= 0 || height <= 0) return;
            int root = canvas.save();
            try {
                canvas.scale(width / 28f, height / 20f);
                // Scale visual content around its center without changing status-bar layout spacing.
                canvas.scale(BODY_WIDTH_SCALE, BODY_HEIGHT_SCALE, 14f, 10f);
                canvas.drawPath(empty, background);
                canvas.drawPath(filled, foreground);
                if (mark != null) {
                    canvas.drawPath(mark, markClear);
                    canvas.drawPath(mark, markFill);
                }
                if (percentInside && !levelText.isEmpty()) {
                    float baseline = 10f + percentBaseline;
                    canvas.drawText(levelText, 14f, baseline, percentClear);
                    canvas.drawText(levelText, 14f, baseline, percentTint);
                }
            } finally {
                canvas.restoreToCount(root);
            }
        }
    }
}
