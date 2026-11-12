package com.aritxonly.myhypermodifier;

import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/** Only owns weights it actually changed; never retains an Activity, View or LayoutParams. */
final class QQFoldSplitWeights {
    private final Field weight;
    private final WeakReference<View>[] views;
    private final float[] original = new float[3];
    private boolean applied;

    @SuppressWarnings("unchecked")
    QQFoldSplitWeights(Field weight, View left, View chat, View function) {
        this.weight = weight;
        views = new WeakReference[] {new WeakReference<>(left), new WeakReference<>(chat),
                new WeakReference<>(function)};
    }

    boolean apply() throws IllegalAccessException {
        // All three must be the native equal split. Leave another module's ratio untouched.
        for (int i = 0; i < 3; i++) {
            View view = views[i].get();
            if (view == null) return false;
            float current = weight.getFloat(view.getLayoutParams());
            if (applied && current == desired(i)) continue;
            if (current != 0.5f) return false;
        }
        for (int i = 0; i < 3; i++) {
            View view = views[i].get();
            float current = weight.getFloat(view.getLayoutParams());
            if (!applied || current != desired(i)) original[i] = current;
            if (current != desired(i)) {
                weight.setFloat(view.getLayoutParams(), desired(i));
                view.requestLayout();
            }
        }
        applied = true;
        return true;
    }

    void restore() throws IllegalAccessException {
        if (!applied) return;
        for (int i = 0; i < 3; i++) {
            View view = views[i].get();
            if (view != null && weight.getFloat(view.getLayoutParams()) == desired(i)) {
                weight.setFloat(view.getLayoutParams(), original[i]);
                view.requestLayout();
            }
        }
        applied = false;
    }

    private static float desired(int index) { return index == 0 ? 0.4f : 0.6f; }
}
