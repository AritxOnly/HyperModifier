package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.drawable.Drawable;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Activity-scoped glass adaptation; playback session hooks remain independent. */
final class SpotifyGlassHooks {
    private SpotifyGlassHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> main = Class.forName("com.spotify.music.SpotifyMainActivity", false, loader);
            installPlayerSurfaceHooks(module);
            hookLifecycle(module, main, "onResume");
            hookLifecycle(module, main, "onPause");
            hookLifecycle(module, main, "onDestroy");
            Method touch = Activity.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            module.hook(touch).setId("spotify-glass-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (main.isInstance(chain.getThisObject())) {
                            SpotifyFloatingNavigation.onTouchEvent((Activity) chain.getThisObject(),
                                    (MotionEvent) chain.getArg(0));
                        }
                        return result;
                    });
            Log.i("MyHyperModifier", "Installed Spotify glass dock lifecycle hooks");
        } catch (ReflectiveOperationException error) {
            HookDiagnostics.failure("com.spotify.music", "Spotify 柔光玻璃", error);
        }
    }

    private static void installPlayerSurfaceHooks(XposedModule module) throws ReflectiveOperationException {
        module.hook(View.class.getDeclaredMethod("setBackgroundDrawable", Drawable.class))
                .setId("spotify-capsule-background")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> chain.proceed(new Object[]{SpotifyPlaybackCapsule.backgroundFor(
                        (View) chain.getThisObject(), (Drawable) chain.getArg(0))}));
        module.hook(View.class.getDeclaredMethod("setForeground", Drawable.class))
                .setId("spotify-capsule-foreground")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> chain.proceed(new Object[]{SpotifyPlaybackCapsule.foregroundFor(
                        (View) chain.getThisObject(), (Drawable) chain.getArg(0))}));
    }

    private static void hookLifecycle(XposedModule module, Class<?> main, String name)
            throws ReflectiveOperationException {
        module.hook(main.getDeclaredMethod(name)).setId("spotify-glass-" + name)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = (Activity) chain.getThisObject();
                    if (name.equals("onDestroy")) SpotifyFloatingNavigation.dispose(activity);
                    if (name.equals("onPause")) SpotifyFloatingNavigation.setForeground(activity, false);
                    Object result = chain.proceed();
                    if (name.equals("onResume")) {
                        SpotifyFloatingNavigation.attach(activity);
                        SpotifyFloatingNavigation.setForeground(activity, true);
                    }
                    return result;
                });
    }
}
