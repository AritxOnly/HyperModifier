package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Opt Spotify into HyperOS's session-action path so native custom actions reach the card. */
final class SpotifyMediaCardHooks {
    private static final String SPOTIFY = "com.spotify.music";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LOGGED = new AtomicBoolean();
    private static final AtomicReference<String> LAST_MODEL = new AtomicReference<>();

    private SpotifyMediaCardHooks() {}

    static void install(XposedModule module, ClassLoader classLoader) throws ReflectiveOperationException {
        if (INSTALLED.get()) return;
        Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
        Method useSessionActions = statusBarManager.getDeclaredMethod(
                "useMediaSessionActionsForApp", String.class, UserHandle.class);
        module.hook(useSessionActions)
                .setId("spotify-systemui-session-actions")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (!SPOTIFY.equals(chain.getArg(0))) return chain.proceed();
                    ModuleSettings.ensureLoaded();
                    if (!ModuleSettings.moduleHooksEnabled
                            || (!ModuleSettings.spotifyFavoriteButtonEnabled
                            && !ModuleSettings.spotifyShuffleButtonEnabled)) return chain.proceed();
                    if (LOGGED.compareAndSet(false, true)) {
                        Log.i("MyHyperModifier", "Spotify media card: enabled SystemUI MediaSession action path");
                    }
                    return true;
                });
        INSTALLED.set(true);
        Log.i("MyHyperModifier", "Installed Spotify SystemUI MediaSession action gate");
        try {
            SpotifyMediaCardBinding.install(module, classLoader);
        } catch (ReflectiveOperationException error) {
            HookDiagnostics.failure(SPOTIFY, "Spotify 卡片按钮绑定", error);
        }
        try {
            installActionModelHook(module, classLoader);
        } catch (ReflectiveOperationException error) {
            HookDiagnostics.failure(SPOTIFY, "Spotify 原生按钮图标", error);
        }
    }
    private static void installActionModelHook(XposedModule module, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> actions = Class.forName(
                "com.android.systemui.media.controls.domain.pipeline.MediaActionsKt", false, classLoader);
        for (Method method : actions.getDeclaredMethods()) {
            if (!method.getName().equals("createActionsFromState")
                    || method.getParameterCount() != 5
                    || method.getParameterTypes()[1] != String.class) continue;
            module.hook(method)
                    .setId("spotify-systemui-action-model")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (SPOTIFY.equals(chain.getArg(1))) {
                            ModuleSettings.ensureLoaded();
                            if (result != null && ModuleSettings.moduleHooksEnabled
                                    && (ModuleSettings.spotifyFavoriteButtonEnabled
                                    || ModuleSettings.spotifyShuffleButtonEnabled)) {
                                try {
                                    result = restoreIcons(result, (Context) chain.getArg(0),
                                            (UserHandle) chain.getArg(4));
                                } catch (ReflectiveOperationException | RuntimeException error) {
                                    Log.w("MyHyperModifier", "Could not restore Spotify native action icons", error);
                                }
                            }
                            String detail = "null";
                            if (result != null) {
                                try {
                                    java.lang.reflect.Field first = result.getClass().getDeclaredField("custom0");
                                    java.lang.reflect.Field second = result.getClass().getDeclaredField("custom1");
                                    first.setAccessible(true);
                                    second.setAccessible(true);
                                    detail = "custom0=" + (first.get(result) != null)
                                            + ", custom1=" + (second.get(result) != null)
                                            + ", icon0=" + hasIcon(first.get(result))
                                            + ", icon1=" + hasIcon(second.get(result));
                                } catch (ReflectiveOperationException error) {
                                    detail = "model=" + result.getClass().getName();
                                }
                            }
                            if (!detail.equals(LAST_MODEL.getAndSet(detail))) {
                                Log.i("MyHyperModifier", "Spotify SystemUI action model: " + detail);
                            }
                        }
                        return result;
                    });
            return;
        }
        throw new NoSuchMethodException("createActionsFromState");
    }

    // HyperOS's hidden-custom-actions list can erase only the icon while retaining
    // the native command. The callback also checks that list, so send its exact
    // captured command directly, without changing the process-wide list.
    private static Object restoreIcons(Object model, Context context,
                                       UserHandle user) throws ReflectiveOperationException {
        Object first = read(model, "custom0");
        Object second = read(model, "custom1");
        Object restoredFirst = restoreIcon(first, context, user);
        Object restoredSecond = restoreIcon(second, context, user);
        if (restoredFirst == first && restoredSecond == second) return model;
        Class<?> actionClass = model.getClass().getDeclaredField("custom0").getType();
        return model.getClass().getDeclaredConstructor(actionClass, actionClass, actionClass,
                actionClass, actionClass, boolean.class, boolean.class).newInstance(
                read(model, "playOrPause"), read(model, "nextOrCustom"), read(model, "prevOrCustom"),
                restoredFirst, restoredSecond, read(model, "reserveNext"), read(model, "reservePrev"));
    }

    private static Object restoreIcon(Object action, Context context,
                                      UserHandle user) throws ReflectiveOperationException {
        if (action == null || hasIcon(action)) return action;
        Object callback = read(action, "action");
        if (callback == null) return action;
        PlaybackState.CustomAction nativeAction = null;
        MediaController controller = null;
        // Recover the exact object captured by Xiaomi's callback, including its
        // resource ID and extras. Do not identify actions by translated labels.
        for (Field field : callback.getClass().getDeclaredFields()) {
            if (field.getType() == PlaybackState.CustomAction.class) {
                field.setAccessible(true);
                nativeAction = (PlaybackState.CustomAction) field.get(callback);
            } else if (field.getType() == MediaController.class) {
                field.setAccessible(true);
                controller = (MediaController) field.get(callback);
            }
        }
        if (nativeAction == null || controller == null || nativeAction.getIcon() == 0) return action;
        Icon resource = Icon.createWithResource(SPOTIFY, nativeAction.getIcon());
        Method load = Icon.class.getDeclaredMethod("loadDrawableAsUser", Context.class, int.class);
        load.setAccessible(true);
        Method userId = UserHandle.class.getDeclaredMethod("getIdentifier");
        userId.setAccessible(true);
        Drawable icon = (Drawable) load.invoke(resource, context, userId.invoke(user));
        if (icon == null) return action;
        PlaybackState.CustomAction command = nativeAction;
        MediaController nativeController = controller;
        Runnable dispatch = () -> nativeController.getTransportControls()
                .sendCustomAction(command, command.getExtras());
        return action.getClass().getDeclaredConstructor(Drawable.class, Runnable.class,
                CharSequence.class, Drawable.class, Integer.class).newInstance(
                icon, dispatch, read(action, "contentDescription"), read(action, "background"),
                read(action, "rebindId"));
    }

    private static boolean hasIcon(Object action) throws ReflectiveOperationException {
        return action != null && read(action, "icon") != null;
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
