package com.aritxonly.myhypermodifier;

import android.view.View;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Keep the held clock's own pose until its unlock fade is no longer visible. */
final class HyperMusicCoverClockExit {
    private final WeakHashMap<View, float[]> observed = new WeakHashMap<>();
    private final WeakHashMap<View, HyperMusicCoverClockFramePolicy.ExitPose> pending = new WeakHashMap<>();
    private final Method[] setters = new Method[6];
    private boolean applying;
    HyperMusicCoverClockExit(XposedModule module) throws ReflectiveOperationException {
        String[] names = {"setScaleX", "setScaleY", "setPivotX", "setPivotY", "setTranslationX", "setTranslationY"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            setters[i] = View.class.getDeclaredMethod(names[i], float.class);
            module.hook(setters[i]).setId("hmc-extension-clock-exit-" + names[i])
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        View view = (View) chain.getThisObject();
                        HyperMusicCoverClockFramePolicy.ExitPose pose = pending.get(view);
                        if (pose == null || applying) return chain.proceed();
                        Object[] args = chain.getArgs().toArray();
                        args[0] = pose.request(index, ((Number) args[0]).floatValue(), visible(view));
                        return chain.proceed(args);
                    });
            module.deoptimize(setters[i]);
        }
    }
    void observe(View view) {
        if (view != null && visible(view) && !pending.containsKey(view)) observed.put(view, read(view));
    }
    void clearObserved() { observed.clear(); }
    void begin() throws ReflectiveOperationException {
        try {
            applying = true;
            for (View view : observed.keySet().toArray(new View[0])) {
                if (view == null || !visible(view)) continue;
                HyperMusicCoverClockFramePolicy.ExitPose pose =
                        new HyperMusicCoverClockFramePolicy.ExitPose(observed.get(view), read(view));
                pending.put(view, pose);
                // Also covers an OEM/HMC reset that happened before the going-away callback.
                for (int i = 0; i < setters.length; i++) setters[i].invoke(view, pose.held[i]);
            }
        } finally { applying = false; }
    }
    boolean retains(View view) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null)
            if (pending.containsKey(v) && visible(v)) return true;
        return false;
    }
    boolean retained() {
        for (View v : pending.keySet()) if (v != null && visible(v)) return true;
        return false;
    }
    void clearInvisible() throws ReflectiveOperationException {
        for (View v : pending.keySet().toArray(new View[0])) if (v != null && !visible(v)) release(v);
    }
    void finish() throws ReflectiveOperationException {
        for (View v : pending.keySet().toArray(new View[0])) if (v != null) release(v);
        observed.clear();
    }
    private void release(View view) throws ReflectiveOperationException {
        HyperMusicCoverClockFramePolicy.ExitPose pose = pending.remove(view);
        if (pose != null) for (int i = 0; i < setters.length; i++) setters[i].invoke(view, pose.nativeValues[i]);
        observed.remove(view);
    }
    private static float[] read(View v) {
        return new float[]{v.getScaleX(), v.getScaleY(), v.getPivotX(), v.getPivotY(), v.getTranslationX(), v.getTranslationY()};
    }
    private static boolean visible(View view) {
        float alpha = 1f;
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) alpha *= v.getAlpha();
        return HyperMusicCoverClockFramePolicy.retainExit(true, true, view.isShown(), alpha);
    }
}
