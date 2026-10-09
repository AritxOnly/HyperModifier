package com.aritxonly.myhypermodifier;

import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Intercept the final framework publication, shared by Spotify's legacy and Media3 paths. */
final class SpotifyHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SpotifyHooks() {}

    static void install(XposedModule module, ClassLoader classLoader) throws ReflectiveOperationException {
        if (INSTALLED.get()) return;
        Method setPlaybackState = MediaSession.class.getDeclaredMethod("setPlaybackState", PlaybackState.class);
        module.hook(setPlaybackState)
                .setId("spotify-framework-playback-state")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> chain.proceed(new Object[]{
                        SpotifyMediaSessionBridge.augmentPlaybackState(
                                chain.getThisObject(), (PlaybackState) chain.getArg(0))
                }));
        INSTALLED.set(true);
        Log.i("MyHyperModifier", "Installed Spotify framework MediaSession publication hook");
        SpotifyGlassHooks.install(module, classLoader);
    }
}
