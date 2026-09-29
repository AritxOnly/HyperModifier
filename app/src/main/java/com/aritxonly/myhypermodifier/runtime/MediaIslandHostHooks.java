package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Keeps the plugin's backdrop/animation geometry aligned with the SystemUI media player. */
final class MediaIslandHostHooks {
    static final String CONTENT_CLASS =
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView";
    private static final String PLAYER_CLASS =
            "com.android.systemui.statusbar.notification.mediaisland.PlayerIslandConstraintLayout";
    private static final String TAG = "MyHyperModifier";
    private static final Set<Method> INSTALLED = ConcurrentHashMap.newKeySet();

    static void install(XposedModule module, ClassLoader loader) {
        try {
            installLoaded(module, Class.forName(CONTENT_CLASS, false, loader));
        } catch (Throwable error) {
            Log.w(TAG, "Media island plugin geometry hook unavailable", error);
        }
    }

    static void installLoaded(XposedModule module, Class<?> type) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"updateExpandedSize".equals(method.getName()) || parameters.length != 3
                    || parameters[0] != int.class || parameters[1] != int.class
                    || !"com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"
                    .equals(parameters[2].getName())) continue;
            // These fields are resolved once, outside the callback. Do not silently continue
            // with a broken height cap on a different plugin revision.
            Field maximum = type.getDeclaredField("expandedViewMaxHeight");
            Field actual = type.getDeclaredField("expandedViewHeight");
            maximum.setAccessible(true);
            actual.setAccessible(true);
            if (!INSTALLED.add(method)) continue;
            try {
                module.hook(method)
                        .setId("media-island-plugin-expanded-size")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object data = chain.getArg(2);
                            View player = mediaPlayer(data);
                            if (player == null) return chain.proceed();
                            ModuleSettings.ensureLoaded();
                            int height = MediaConstraintCustomizer.requestedMediaIslandHeight(
                                    player.getContext());
                            if (height <= 0) return chain.proceed();
                            Object host = chain.getThisObject();
                            int stockMaximum = maximum.getInt(host);
                            int previous = actual.getInt(host);
                            // The stock media branch always supplies the 168dp maximum, not the
                            // player's measured height. Override this event only. Restore the cap
                            // so the same host remains stock for 12306 and other future events.
                            maximum.setInt(host, Math.max(stockMaximum, height));
                            Object result;
                            try {
                                result = chain.proceed(new Object[]{chain.getArg(0), height, data});
                            } finally {
                                maximum.setInt(host, stockMaximum);
                            }
                            int applied = actual.getInt(host);
                            if (previous != applied) {
                                Log.i(TAG, "Media island host height: " + previous + " -> "
                                        + applied + ", requested=" + height
                                        + ", player=" + player.getHeight());
                            }
                            return result;
                        });
                Log.i(TAG, "Installed media island plugin geometry hook");
            } catch (Throwable error) {
                INSTALLED.remove(method);
                throw error;
            }
        }
    }

    private static View mediaPlayer(Object data) {
        if (data == null) return null;
        try {
            Object view = data.getClass().getMethod("getView").invoke(data);
            return view instanceof View ? findPlayer((View) view, 0) : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static View findPlayer(View view, int depth) {
        if (depth > 12) return null;
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            if (PLAYER_CLASS.equals(type.getName())) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View player = findPlayer(group.getChildAt(index), depth + 1);
                if (player != null) return player;
            }
        }
        return null;
    }
}
