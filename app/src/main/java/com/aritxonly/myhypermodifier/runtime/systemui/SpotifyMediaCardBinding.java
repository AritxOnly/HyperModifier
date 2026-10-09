package com.aritxonly.myhypermodifier;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Completes binding of Spotify's two native semantic actions in HyperOS's side slots. */
final class SpotifyMediaCardBinding {
    private static final String TAG = "MyHyperModifier";
    private static final WeakHashMap<Object, String> LAST_STATE = new WeakHashMap<>();

    private SpotifyMediaCardBinding() {}

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> controller = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl", false, loader);
        Class<?> data = Class.forName("com.android.systemui.media.controls.shared.model.MediaData", false, loader);
        Method bind = controller.getDeclaredMethod("bindMediaData", data);
        module.hook(bind)
                .setId("spotify-systemui-side-action-binding")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object target = chain.getThisObject();
                    Object mediaData = chain.getArg(0);
                    boolean spotify = enabled(mediaData);
                    Object result = chain.proceed();
                    if (spotify) restore(target, mediaData, true);
                    return result;
                });
        Log.i(TAG, "Installed Spotify HyperOS side-action binding");
    }

    private static boolean enabled(Object data) {
        try {
            ModuleSettings.ensureLoaded();
            return ModuleSettings.moduleHooksEnabled
                    && (ModuleSettings.spotifyFavoriteButtonEnabled || ModuleSettings.spotifyShuffleButtonEnabled)
                    && "com.spotify.music".equals(read(data, "packageName"));
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private static void restore(Object controller, Object data, boolean post) {
        try {
            if (!enabled(data) || read(controller, "mediaData") != data) return;
            Object model = read(data, "semanticActions");
            Object holder = read(controller, "holder");
            if (model == null || holder == null) return;
            ImageButton first = (ImageButton) read(holder, "action0");
            ImageButton last = (ImageButton) read(holder, "action4");
            String before = state(first) + "; " + state(last);
            bind(first, read(model, "custom0"));
            bind(last, read(model, "custom1"));
            String detail = "before=" + before + ", after=" + state(first) + "; " + state(last);
            synchronized (LAST_STATE) {
                if (!detail.equals(LAST_STATE.put(controller, detail))) {
                    Log.i(TAG, "Spotify side-action views: " + detail);
                }
            }
            // Stock binding may defer image work to its animation handler; restore visibility
            // once after that work, without a process-wide View or layout hook.
            if (post && first != null) first.post(() -> restore(controller, data, false));
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Could not bind Spotify side-action views", error);
        }
    }

    private static void bind(ImageButton view, Object action) throws ReflectiveOperationException {
        if (view == null || action == null) return;
        Drawable icon = (Drawable) read(action, "icon");
        Runnable callback = (Runnable) read(action, "action");
        if (icon == null || callback == null) return;
        view.setImageDrawable(icon);
        view.setContentDescription((CharSequence) read(action, "contentDescription"));
        view.setOnClickListener(ignored -> {
            try { callback.run(); }
            catch (RuntimeException error) { Log.w(TAG, "Spotify native side action failed", error); }
        });
        view.setEnabled(true);
        view.setAlpha(1f);
        if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
    }

    private static String state(ImageButton view) {
        if (view == null) return "missing";
        return "visibility=" + view.getVisibility() + ", alpha=" + view.getAlpha()
                + ", size=" + view.getWidth() + "x" + view.getHeight()
                + ", icon=" + (view.getDrawable() != null);
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
