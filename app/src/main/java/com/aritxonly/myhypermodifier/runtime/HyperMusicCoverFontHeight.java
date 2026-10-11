package com.aritxonly.myhypermodifier;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Native TimeView HGHT shaping, plus baseline units for HMC's uniform pose mapping. */
final class HyperMusicCoverFontHeight {
    private final Field height, weight, width, size, text, paint;
    private final Method variation;
    private final HyperMusicCoverClockLayout layout;
    private final HyperMusicCoverClockExit exit;
    private final XposedModule module;
    private final WeakHashMap<View, HyperMusicCoverClockFramePolicy.Gate> frames = new WeakHashMap<>();
    private HyperMusicCoverExtensionPolicy.FontReference sessionReference;
    private final WeakHashMap<View, Ink> ink = new WeakHashMap<>();
    private final WeakHashMap<View, Float> lockWeights = new WeakHashMap<>();
    private static final class Ink {
        HyperMusicCoverExtensionPolicy.FontReference reference = new HyperMusicCoverExtensionPolicy.FontReference();
        final Paint paint = new Paint();
        final Path path = new Path();
        final RectF bounds = new RectF();
        float baseline, shaped, progress;
        float selectedWeight, selectedWidth, selectedHeight, selectedSize;
        float[] position;
        RectF plannedBounds;
        float requestedRatio = Float.NaN;
        float solvedWeight = Float.NaN;
        android.graphics.Typeface solvedTypeface;
        HyperMusicCoverFontShapePolicy.Axes solution;
    }
    HyperMusicCoverFontHeight(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = Class.forName("com.miui.clock.allInOne.TimeView", false, loader);
        this.module = module;
        exit = new HyperMusicCoverClockExit(module);
        layout = new HyperMusicCoverClockLayout(module, loader);
        height = field(owner, "height"); weight = field(owner, "weight"); width = field(owner, "width");
        size = field(owner, "textSizePx"); text = field(owner, "mText"); paint = field(owner, "textPaint");
        Class<?> utils = Class.forName("com.miui.clock.allInOne.TextMeasureUtils", false, loader);
        variation = utils.getDeclaredMethod("applyFontVariation", float.class, float.class, float.class, Paint.class);
        variation.setAccessible(true);
        Method draw = owner.getDeclaredMethod("onDraw", android.graphics.Canvas.class);
        module.hook(draw).setId("hmc-extension-font-hght")
                // Restore OEM fields before HMC's after-draw placement callback can drive setters.
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    View tv = (View) chain.getThisObject();
                    HyperMusicCoverExtensions.refreshNow();
                    float originalValue = height.getFloat(tv);
                    Ink box = ink.get(tv);
                    if (box == null) { box = new Ink(); ink.put(tv, box); }
                    Float progress = HyperMusicCoverExtensions.fontProgress(tv);
                    if (!exit.retains(tv)) observeReference(box, tv, progress != null);
                    if (progress == null) { layout.restore(tv); box.baseline = box.shaped = 0f; return chain.proceed(); }
                    float originalWidth = width.getFloat(tv), originalSize = size.getFloat(tv);
                    float originalWeight = weight.getFloat(tv);
                    try {
                        prepare(tv, progress);
                        if (box.position != null) layout.apply(tv, box.position);
                        weight.setFloat(tv, box.selectedWeight);
                        height.setFloat(tv, box.selectedHeight);
                        width.setFloat(tv, box.selectedWidth);
                        size.setFloat(tv, box.selectedSize);
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        weight.setFloat(tv, originalWeight);
                        height.setFloat(tv, originalValue);
                        width.setFloat(tv, originalWidth);
                        size.setFloat(tv, originalSize);
                        layout.restore(tv);
                        ink.remove(tv);
                        HookDiagnostics.failure("com.android.systemui", "HMC 原生字体高度", error);
                        return chain.proceed();
                    }
                    try { return chain.proceed(); }
                    finally {
                        weight.setFloat(tv, originalWeight);
                        height.setFloat(tv, originalValue);
                        width.setFloat(tv, originalWidth);
                        size.setFloat(tv, originalSize);
                    }
                });
        module.deoptimize(draw);
        Method bounds = owner.getDeclaredMethod("getTextBoundsWithPosition");
        module.hook(bounds).setId("hmc-extension-clock-frame-bounds")
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View tv = (View) chain.getThisObject();
                    Float progress = HyperMusicCoverExtensions.fontProgress(tv);
                    if (progress != null) {
                        prepare(tv, progress);
                        Ink box = ink.get(tv);
                        if (box != null && box.plannedBounds != null) return new RectF(box.plannedBounds);
                    }
                    return chain.proceed();
                });
        module.deoptimize(bounds);

        Class<?> animation = Class.forName("com.android.keyguard.clock.animation.allinone.AllInOneClockAnimation", false, loader);
        Class<?> resultClass = Class.forName("com.miui.interfaces.keyguard.data.ClockResult", false, loader);
        Field targetWeight = field(resultClass, "timeWeight");
        Field container = inheritedField(animation, "mAllContainer");
        Method target = animation.getDeclaredMethod("updateLockScreenStyle", resultClass);
        module.hook(target).setId("hmc-extension-font-lock-target")
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object params = chain.getArgs().get(0);
                    View root = (View) container.get(chain.getThisObject());
                    if (root != null && params != null) {
                        float value = ((Number) targetWeight.get(params)).floatValue();
                        if (Float.isFinite(value) && value > 0f) lockWeights.put(root, value);
                    }
                    return chain.proceed();
                });
        module.deoptimize(target);

        // Internal batch setters are the path used by AllInOneBase.setClockViewRect.
        for (String name : new String[]{"setHeight", "setWidth", "setWeight", "setSizeInternal", "setClockParams", "setClockParamsInternal"}) {
            Method setter = name.startsWith("setClockParams")
                    ? owner.getDeclaredMethod(name, float.class, float.class, float.class)
                    : owner.getDeclaredMethod(name, float.class);
            module.hook(setter).setId("hmc-extension-font-reference-" + name)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        View tv = (View) chain.getThisObject();
                        Ink box = ink.get(tv);
                        if (box == null) { box = new Ink(); ink.put(tv, box); }
                        boolean active = HyperMusicCoverExtensions.fontProgress(tv) != null;
                        observeReference(box, tv, active);
                        Object result = chain.proceed();
                        if (!active) observeReference(box, tv, false);
                        return result;
                    });
            module.deoptimize(setter);
        }
    }
    private Ink ink(View view) {
        Ink value = ink.get(view);
        if (value == null) { value = new Ink(); ink.put(view, value); }
        return value;
    }
    private void prepare(View tv, float progress) throws ReflectiveOperationException {
        if (exit.retains(tv)) return;
        View owner = layout.owner(tv);
        View key = owner == null ? tv : owner;
        HyperMusicCoverClockFramePolicy.Gate gate = frames.get(key);
        if (gate == null) { gate = new HyperMusicCoverClockFramePolicy.Gate(); frames.put(key, gate); }
        // Android locks AnimationUtils' clock for the complete Choreographer frame.
        // Geometry queries and the later draws must share that same prepared clock frame.
        if (!gate.begin(android.view.animation.AnimationUtils.currentAnimationTimeMillis())) return;
        boolean prepared = false;
        try {
            View[] parts = owner == null ? new View[]{tv} : layout.parts(owner);
            View anchor = null;
            for (View part : parts) {
                if (part == null || part.getVisibility() == View.GONE) continue;
                Ink box = ink(part);
                observeReference(box, part, true);
                shape(part, box, progress);
                if (anchor == null) anchor = part;
            }
            if (anchor != null) layout.place(anchor, ink(anchor).paint);
            for (View part : parts) {
                if (part == null || part.getVisibility() == View.GONE) continue;
                Ink box = ink(part);
                box.position = layout.position(part);
                box.plannedBounds = new RectF(box.bounds);
                box.plannedBounds.offset(box.position[0], box.position[1]);
            }
            prepared = true;
        } finally { if (!prepared) gate.abort(); }
    }
    private void shape(View tv, Ink box, float progress) throws ReflectiveOperationException {
        float selectedHeight = HyperMusicCoverExtensionPolicy.axisHeight(box.reference.height, ModuleSettings.hmcClockFontHeight, progress);

        box.paint.set((Paint) paint.get(tv));
        box.paint.setTextSize(box.reference.size);
        String digits = (String) text.get(tv);
        float targetWeight = lockWeight(tv, box.reference.weight);
        float currentWeight = HyperMusicCoverExtensionPolicy.axisHeight(box.reference.weight, targetWeight, progress);
        boolean fitRatio = ModuleSettings.hmcClockFontRatioEnabled;
        float referenceWidth = box.reference.width;
        variation.invoke(null, box.reference.weight, referenceWidth, box.reference.height, box.paint);
        box.baseline = measure(box, digits);
        float selectedWidth = referenceWidth, selectedSize = box.reference.size;
        if (fitRatio) {
            float requested = ModuleSettings.hmcClockFontRatio;
            if (box.solution == null || requested != box.requestedRatio
                    || box.solvedTypeface != box.paint.getTypeface() || box.solvedWeight != targetWeight) {
                box.paint.setTextSize(100f);
                Ink measured = box;
                box.solution = HyperMusicCoverFontShapePolicy.solve(requested, referenceWidth, (w, h) -> {
                    variation.invoke(null, targetWeight, w, h, measured.paint);
                    measure(measured, "00");
                    return measured.bounds.width() / measured.bounds.height();
                });
                box.requestedRatio = requested;
                box.solvedWeight = targetWeight;
                box.solvedTypeface = box.paint.getTypeface();
                module.log(android.util.Log.INFO, "MyHyperModifier", "HMC native font ratio: requested="
                        + requested + " reachable=" + box.solution.ratio + " wdth="
                        + box.solution.width + " HGHT=" + box.solution.height);
            }
            selectedWidth = HyperMusicCoverExtensionPolicy.axisHeight(referenceWidth, box.solution.width, progress);
            selectedHeight = HyperMusicCoverExtensionPolicy.axisHeight(box.reference.height, box.solution.height, progress);
            box.paint.setTextSize(box.reference.size);
            variation.invoke(null, box.reference.weight, referenceWidth, box.reference.height, box.paint);
            measure(box, "00");
            float naturalWidth = box.bounds.width();
            variation.invoke(null, currentWeight, selectedWidth, selectedHeight, box.paint);
            measure(box, "00");
            selectedSize = HyperMusicCoverFontShapePolicy.fontSizeForWidth(box.reference.size,
                    naturalWidth, box.bounds.width());
        }
        box.paint.setTextSize(selectedSize);
        variation.invoke(null, currentWeight, selectedWidth, selectedHeight, box.paint);
        box.shaped = measure(box, digits);
        box.selectedWeight = currentWeight; box.selectedWidth = selectedWidth;
        box.selectedHeight = selectedHeight; box.selectedSize = selectedSize;
        box.progress = progress;
    }
    void observeTargets(HyperMusicCoverAdapter adapter) throws ReflectiveOperationException {
        exit.clearInvisible();
        if (HyperMusicCoverExtensions.fontLeaving()) return;
        for (View root : adapter.roots()) if (root != null) {
            View target = (View) adapter.clockTarget.invoke(null, root);
            if (largestInk(target) != null) exit.observe(target);
        }
    }
    void beginUnlock() {
        try { exit.begin(); } catch (ReflectiveOperationException error) {
            HookDiagnostics.failure("com.android.systemui", "HMC 时钟退出", error);
        }
    }
    void finishUnlock() {
        try { exit.finish(); } catch (ReflectiveOperationException error) {
            HookDiagnostics.failure("com.android.systemui", "HMC 时钟退出", error);
        }
        frames.clear();
    }
    boolean retainingExit() { return exit.retained(); }
    Float exitProgress(View view) {
        Ink box = ink.get(view);
        return box != null && box.plannedBounds != null && exit.retains(view) ? box.progress : null;
    }
    private void observeReference(Ink box, View tv, boolean active) throws ReflectiveOperationException {
        if (active) {
            if (sessionReference == null) {
                sessionReference = new HyperMusicCoverExtensionPolicy.FontReference();
                sessionReference.observe(height.getFloat(tv), width.getFloat(tv), weight.getFloat(tv), size.getFloat(tv), true);
            }
            if (box.reference != sessionReference) { box.reference = sessionReference; box.solution = null; }
        } else if (!HyperMusicCoverExtensions.fontSessionHeld()) {
            if (box.reference == sessionReference) box.reference = new HyperMusicCoverExtensionPolicy.FontReference();
            box.reference.observe(height.getFloat(tv), width.getFloat(tv), weight.getFloat(tv), size.getFloat(tv), false);
            box.solution = null;
        }
    }
    void syncSession(boolean held) {
        if (!held && sessionReference != null) {
            sessionReference = null;
            frames.clear();
            exit.clearObserved();
            for (Ink box : ink.values()) {
                box.reference = new HyperMusicCoverExtensionPolicy.FontReference();
                box.baseline = box.shaped = 0f; box.solution = null; box.plannedBounds = null; box.position = null;
            }
        }
    }

    private float measure(Ink box, String digits) {
        box.path.reset();
        box.paint.getTextPath(digits, 0, digits.length(), 0, 0, box.path);
        box.path.computeBounds(box.bounds, true);
        return box.bounds.height();
    }
    float baselineUnit(HyperMusicCoverAdapter adapter, float original) throws ReflectiveOperationException {
        float baseline = 0f;
        for (View root : adapter.roots()) {
            if (root == null || !root.isShown()) continue;
            View target = (View) adapter.clockTarget.invoke(null, root);
            Ink candidate = largestInk(target);
            if (candidate != null) baseline = Math.max(baseline, candidate.baseline);
        }
        // HMC publishes the largest individual native glyph height, in local pixels.
        // Return the equivalent reference height directly: never divide this frame's unit
        // by a ratio computed while another digit or the previous frame was drawing.
        return baseline > 0f ? baseline : original;
    }
    private float lockWeight(View tv, float fallback) {
        for (View v = tv; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            Float target = lockWeights.get(v);
            if (target != null && Float.isFinite(target) && target > 0f) return target;
        }
        return fallback;
    }
    private Ink largestInk(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        Ink best = ink.get(v);
        if (best != null && (best.shaped <= 0 || HyperMusicCoverExtensions.fontProgress(v) == null)) best = null;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                Ink next = largestInk(group.getChildAt(i));
                if (next != null && (best == null || next.baseline > best.baseline)) best = next;
            }
        }
        return best;
    }
    void invalidate() {
        // A View property change alone reuses the old glyph display list.
        for (View tv : ink.keySet().toArray(new View[0])) if (tv != null) tv.invalidate();
    }
    private static Field inheritedField(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try { return field(type, name); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
