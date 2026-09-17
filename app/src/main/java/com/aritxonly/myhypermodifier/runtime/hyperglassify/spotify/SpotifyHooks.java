package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Connects Spotify's main Activity and MediaSession to the app-owned adapters. */
final class SpotifyHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY = "com.spotify.music.SpotifyMainActivity";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SpotifyHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = mainActivity.getDeclaredMethod("onCreate", Bundle.class);
            Method onResume = mainActivity.getDeclaredMethod("onResume");
            Method onDestroy = mainActivity.getDeclaredMethod("onDestroy");

            module.hook(onCreate)
                    .setId("spotify-hyper-glassify-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            SpotifyFloatingNavigation.attach((Activity) target);
                        }
                        return result;
                    });

            module.hook(onResume)
                    .setId("spotify-hyper-glassify-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            SpotifyFloatingNavigation.attach((Activity) target);
                            SpotifyFloatingNavigation.refresh((Activity) target);
                        }
                        return result;
                    });

            module.hook(onDestroy)
                    .setId("spotify-hyper-glassify-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof Activity) {
                            SpotifyFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

            Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatchTouchEvent)
                    .setId("spotify-hyper-glassify-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (target instanceof Activity && event instanceof MotionEvent) {
                            SpotifyFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });

            installMediaSessionHooks(module, classLoader);
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Spotify hooks", throwable);
        }
    }

    private static void installMediaSessionHooks(
            XposedModule module, ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> mediaSession = Class.forName(
                "androidx.media3.session.legacy.MediaSessionCompat", false, classLoader);
        Class<?> playbackState = Class.forName(
                "androidx.media3.session.legacy.PlaybackStateCompat", false, classLoader);
        Class<?> callback = Class.forName(
                "androidx.media3.session.legacy.MediaSessionCompat$Callback", false, classLoader);

        Method setPlaybackState = mediaSession.getDeclaredMethod("setPlaybackState", playbackState);
        module.hook(setPlaybackState)
                .setId("spotify-media-session-playback-state")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object session = chain.getThisObject();
                    Object state = chain.getArg(0);
                    Object augmented = SpotifyMediaSessionBridge.augmentPlaybackState(session, state);
                    return chain.proceed(new Object[]{augmented});
                });

        Method setCallback = mediaSession.getDeclaredMethod("setCallback", callback, Handler.class);
        module.hook(setCallback)
                .setId("spotify-media-session-callback")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object session = chain.getThisObject();
                    Object callbackValue = chain.getArg(0);
                    SpotifyMediaSessionBridge.bindCallback(session, callbackValue);
                    return chain.proceed();
                });

        // Spotify's Media3 callback is version-specific, but hooking this leaf method avoids
        // intercepting every MediaSession callback in the process or wrapping Spotify's owner.
        Class<?> spotifyCallback = Class.forName("p.xic0", false, classLoader);
        Method onCustomAction = spotifyCallback.getDeclaredMethod(
                "onCustomAction", String.class, Bundle.class);
        module.hook(onCustomAction)
                .setId("spotify-media-session-custom-action")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object callbackValue = chain.getThisObject();
                    String action = (String) chain.getArg(0);
                    Bundle extras = (Bundle) chain.getArg(1);
                    if (SpotifyMediaSessionBridge.dispatchCustomAction(
                            callbackValue, action, extras)) {
                        return null;
                    }
                    return chain.proceed();
                });
    }
}
