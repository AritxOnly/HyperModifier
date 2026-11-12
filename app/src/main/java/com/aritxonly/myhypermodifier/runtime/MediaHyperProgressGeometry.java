package com.aritxonly.myhypermodifier;

import android.graphics.RuntimeShader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** Recenter HyperProgressSeekBar's constructor-cached shader position after a media resize. */
final class MediaHyperProgressGeometry {
    private static final Map<View, Position> ORIGINALS = new WeakHashMap<>();
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) { return new Access(type); }
    };

    static synchronized void apply(View view) {
        if (!(view instanceof SeekBar seek)) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int height = params != null && params.height > 0 ? params.height : view.getHeight();
        if (height <= 0) return;
        int trackHeight = Math.max(0, Math.min(height, seek.getMaxHeight()));
        int padding = (height - trackHeight) / 2;
        if (view.getPaddingTop() != padding || view.getPaddingBottom() != padding) {
            view.setPadding(view.getPaddingLeft(), padding, view.getPaddingRight(), padding);
        }
        if (view.getMinimumHeight() != 0) view.setMinimumHeight(0);
        Access access = ACCESS.get(view.getClass());
        if (access.position == null) return;
        try {
            Object candidate = access.position.get(view);
            if (!(candidate instanceof float[] position) || position.length < 2) return;
            Position original = ORIGINALS.get(view);
            if (original == null) { original = new Position(position[1]); ORIGINALS.put(view, original); }
            float center = height / 2f;
            original.last = center;
            if (position[1] == center) return;
            position[1] = center;
            updateShader(view, access, position);
        } catch (IllegalAccessException | IllegalArgumentException ignored) {
            // Different OEM versions retain their native rendering path.
        }
    }

    static int trackInset(View view) {
        if (view == null) return 0;
        Access access = ACCESS.get(view.getClass());
        try {
            Object candidate = access.position == null ? null : access.position.get(view);
            return candidate instanceof float[] position && position.length >= 2 ? Math.round(position[0]) : 0;
        } catch (IllegalAccessException | IllegalArgumentException ignored) { return 0; }
    }

    static synchronized void restore(View view) {
        Position original = ORIGINALS.remove(view);
        if (original == null) return;
        Access access = ACCESS.get(view.getClass());
        try {
            Object candidate = access.position.get(view);
            if (candidate instanceof float[] position && position.length >= 2 && position[1] == original.last) {
                position[1] = original.y;
                updateShader(view, access, position);
            }
        } catch (IllegalAccessException | IllegalArgumentException ignored) { }
    }

    private static void updateShader(View view, Access access, float[] position) throws IllegalAccessException {
        if (access.shader != null && access.shader.get(view) instanceof RuntimeShader shader) {
            shader.setFloatUniform("uTrackPosition", position);
        }
        view.invalidate();
    }
    private static final class Position {
        final float y;
        float last;
        Position(float y) { this.y = y; }
    }
    private static final class Access {
        final Field position, shader;
        Access(Class<?> type) {
            position = field(type, "uTrackPosition"); shader = field(type, "runtimeShader");
        }
        private static Field field(Class<?> type, String name) {
            try { return type.getField(name); }
            catch (NoSuchFieldException ignored) { return null; }
        }
    }
}
