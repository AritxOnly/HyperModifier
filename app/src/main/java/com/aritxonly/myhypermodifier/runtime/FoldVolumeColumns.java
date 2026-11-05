package com.aritxonly.myhypermodifier;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Replay the OEM's column outline, progress clip and glass update together on a preference edit. */
final class FoldVolumeColumns {
    private static final String COLUMN = "com.android.systemui.miui.volume.VolumeColumn";
    private static final Set<Class<?>> installed = new HashSet<>();
    private static final boolean FOLD = "lhasa".equals(Build.DEVICE);

    static boolean isTarget(String name) { return FOLD && COLUMN.equals(name); }

    static void install(XposedModule module, ClassLoader loader) {
        if (!FOLD) return;
        try {
            install(module, Class.forName(COLUMN, false, loader));
        } catch (ClassNotFoundException ignored) { /* The plugin loader may arrive later. */ }
    }

    static synchronized void install(XposedModule module, Class<?> type) {
        if (!installed.add(type)) return;
        try {
            Method apply = type.getDeclaredMethod("setRadius", boolean.class);
            apply.setAccessible(true);
            Field popup = type.getDeclaredField("isNeedShowDialog");
            popup.setAccessible(true);
            FoldVolumeColumns columns = new FoldVolumeColumns(apply);
            // Both resolver and private setter can be inlined into these callers by ART.
            module.deoptimize(apply);
            module.deoptimize(type.getDeclaredMethod("setSize", boolean.class, boolean.class));
            module.hook(apply).setId("fold-volume-column-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object column = chain.getThisObject();
                        columns.remember(column, popup.getBoolean(column), (Boolean) chain.getArg(0));
                        return result;
                    });
            ModuleSettings.onChanged(columns::onSettingsChanged);
        } catch (Throwable error) {
            installed.remove(type);
            Log.w("MyHyperModifier", "Fold volume column refresh unavailable", error);
        }
    }

    private final Method apply;
    // OEM views are updated on the main thread. Weak keys also cover detached, reusable columns.
    private final WeakHashMap<Object, Boolean> popups = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refresh;
    private float previous = Float.NaN;

    FoldVolumeColumns(Method apply) { this.apply = apply; }

    void remember(Object column, boolean popup, boolean mode) {
        if (popup) popups.put(column, mode);
        else popups.remove(column);
    }

    synchronized void onSettingsChanged() {
        float current = ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                ? ModuleSettings.volumePanelRadius : 0f;
        if (Float.compare(current, previous) == 0) return;
        previous = current;
        main.removeCallbacks(refresh);
        main.post(refresh);
    }

    private void refresh() {
        for (Object column : popups.keySet().toArray()) {
            Boolean mode = popups.get(column);
            if (mode == null) continue;
            try {
                apply.invoke(column, mode.booleanValue());
            } catch (ReflectiveOperationException error) {
                Log.w("MyHyperModifier", "Could not refresh volume column", error);
            }
        }
    }
}
