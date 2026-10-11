package com.aritxonly.myhypermodifier;

import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;

/** The OEM's segmented clock placement, using the same Paint as the shaped native draw. */
final class HyperMusicCoverClockLayout {
    private final Class<?> base;
    private final WeakHashMap<View, float[]> nativePositions = new WeakHashMap<>();
    private final Field hour, minute, colon, rect, doubleRow, lengths, all, noColon;
    private final Field drawX, drawY, advance, colonAdvance;
    HyperMusicCoverClockLayout(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        base = Class.forName("com.miui.clock.allInOne.AllInOneBase", false, loader);
        Class<?> time = Class.forName("com.miui.clock.allInOne.TimeView", false, loader);
        hour = field(base, "mHourView"); minute = field(base, "mMinuteView"); colon = field(base, "mColonView");
        rect = field(base, "mClockViewRect"); doubleRow = field(base, "mDoubleRow");
        lengths = field(base, "mSegmentLengths"); all = field(base, "mAllTimeText"); noColon = field(base, "mAllTimeTextNoColon");
        drawX = field(time, "mDrawTextX"); drawY = field(time, "mDrawTextY");
        advance = field(time, "mAdvanceWidth"); colonAdvance = field(time, "mColonAdvanceWidth");
        String[] setters = {"setDrawTextX", "setDrawTextY", "setAdvanceWidth", "setColonAdvanceWidth"};
        for (int i = 0; i < setters.length; i++) {
            final int component = i;
            Method setter = time.getDeclaredMethod(setters[i], float.class);
            module.hook(setter).setId("hmc-extension-font-layout-" + setters[i]).intercept(chain -> {
                Object result = chain.proceed();
                float[] saved = nativePositions.get((View) chain.getThisObject());
                if (saved != null) saved[component] = ((Number) chain.getArgs().get(0)).floatValue();
                return result;
            });
            module.deoptimize(setter);
        }
    }
    View owner(View digit) {
        View owner = digit;
        while (owner != null && !base.isInstance(owner))
            owner = owner.getParent() instanceof View ? (View) owner.getParent() : null;
        return owner;
    }
    View[] parts(View owner) throws IllegalAccessException {
        return new View[]{(View) hour.get(owner), (View) minute.get(owner), (View) colon.get(owner)};
    }
    float[] position(View view) throws IllegalAccessException {
        return new float[]{drawX.getFloat(view), drawY.getFloat(view), advance.getFloat(view), colonAdvance.getFloat(view)};
    }
    void apply(View view, float[] values) throws IllegalAccessException {
        drawX.setFloat(view, values[0]); drawY.setFloat(view, values[1]);
        advance.setFloat(view, values[2]); colonAdvance.setFloat(view, values[3]);
    }
    void place(View digit, Paint paint) throws ReflectiveOperationException {
        View owner = owner(digit);
        if (owner == null) return;
        Rect area = (Rect) rect.get(owner);
        int[] segments = (int[]) lengths.get(owner);
        if (area == null || area.width() <= 0 || segments == null || segments.length < 3) return;
        String full = (String) (segments[1] > 0 ? all.get(owner) : noColon.get(owner));
        int count = segments[0] + segments[1] + segments[2];
        if (full == null || count != full.length()) return;
        float[] advances = new float[full.length()];
        paint.getTextWidths(full, advances);
        float[] widths = new float[3];
        int offset = 0;
        for (int i = 0; i < 3; i++)
            for (int end = offset + segments[i]; offset < end; offset++) widths[i] += advances[offset];
        float[] positions = HyperMusicCoverClockLayoutPolicy.positions(area.left, area.width(),
                widths[0], widths[1], widths[2], doubleRow.getBoolean(owner));
        View hours = (View) hour.get(owner);
        float baseline = nativeY(hours);
        float minuteBaseline = nativeY((View) minute.get(owner));
        set((View) hour.get(owner), positions[0], baseline, widths[0]);
        View minutes = (View) minute.get(owner);
        set(minutes, positions[1], minuteBaseline, widths[1] + widths[2]);
        if (minutes != null) colonAdvance.setFloat(minutes, widths[1]);
        set((View) colon.get(owner), positions[2], baseline, widths[1]);
    }
    private float nativeY(View view) throws IllegalAccessException {
        if (view == null) return 0f;
        float[] saved = nativePositions.get(view);
        return saved == null ? drawY.getFloat(view) : saved[1];
    }
    private void set(View view, float x, float y, float width) throws IllegalAccessException {
        if (view == null || view.getVisibility() == View.GONE) return;
        if (!nativePositions.containsKey(view)) nativePositions.put(view,
                new float[]{drawX.getFloat(view), drawY.getFloat(view), advance.getFloat(view), colonAdvance.getFloat(view)});
        drawX.setFloat(view, x); drawY.setFloat(view, y); advance.setFloat(view, width);
    }
    void restore(View view) throws IllegalAccessException {
        float[] saved = nativePositions.remove(view);
        if (saved == null) return;
        drawX.setFloat(view, saved[0]); drawY.setFloat(view, saved[1]);
        advance.setFloat(view, saved[2]); colonAdvance.setFloat(view, saved[3]);
    }
    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
}
