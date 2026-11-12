package com.aritxonly.myhypermodifier;

import android.view.View;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Cached hidden API access used by the copied HMC fade calculation. */
final class ShortcutViewAccess {
    private static MethodHandle transition = findTransition();
    private static MethodHandle findTransition() {
        try {
            return MethodHandles.lookup().unreflect(View.class.getMethod("getTransitionAlpha"))
                    .asType(MethodType.methodType(float.class, View.class));
        } catch (ReflectiveOperationException error) { return null; }
    }
    static float transitionAlpha(View view) {
        MethodHandle getter = transition;
        if (getter == null) return 1f;
        try { return (float) getter.invokeExact(view); }
        catch (Throwable error) { transition = null; return 1f; }
    }
}
