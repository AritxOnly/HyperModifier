package com.aritxonly.myhypermodifier;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Runs inside XiaoAi: a service start also initializes the native screenshot/memory pipeline. */
final class XiaoAiShortcutBridge {
    static final String ACTION = "com.aritxonly.myhypermodifier.XIAOAI_SHORTCUT";
    static final String EXTRA_ACTION = "shortcut_action";
    static final String SERVICE = "com.xiaomi.voiceassistant.VoiceService";
    private static final Map<Service, ShortcutServiceSession> SESSIONS = new WeakHashMap<>();
    private static final Map<Service, Runnable> CLEANUPS = new WeakHashMap<>();
    private static final String TAG = "XiaoAiShortcut";

    static void install(XposedModule module, ClassLoader loader, SharedPreferences prefs) throws Throwable {
        Class<?> service = Class.forName(SERVICE, false, loader);
        KeyShortcutConfig config = new KeyShortcutConfig(prefs);
        Handler main = new Handler(Looper.getMainLooper());
        // stopSelfResult clears only our started state. Existing bindings keep the service
        // alive through Android's lifecycle; a bind must not make our private start permanent.
        try {
            Method destroy = lifecycleMethod(service, "onDestroy");
            module.hook(destroy).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (!service.isInstance(chain.getThisObject())) return chain.proceed();
                Service target = (Service) chain.getThisObject();
                cancelCleanup(target, main);
                SESSIONS.remove(target);
                return chain.proceed();
            });
        } catch (NoSuchMethodException ignored) {}
        Method start = service.getDeclaredMethod("onStartCommand", Intent.class, int.class, int.class);
        module.hook(start).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Service target = (Service) chain.getThisObject();
            ShortcutServiceSession state = session(target);
            Intent intent = (Intent) chain.getArg(0);
            if (intent == null || !ACTION.equals(intent.getAction())) {
                state.nativeUse();
                cancelCleanup(target, main);
                return chain.proceed();
            }
            int startId = (Integer) chain.getArg(2);
            state.privateStart(startId);
            cancelCleanup(target, main);
            String action = KeyShortcutActions.normalize(intent.getStringExtra(EXTRA_ACTION));
            if (!config.current().allows(action)) {
                if (state.canStop(startId)) target.stopSelfResult(startId);
                return Service.START_NOT_STICKY;
            }
            // Let native start initialization finish. The previous version skipped this callback.
            chain.proceed();
            main.post(() -> {
                if (!config.current().allows(action) || SESSIONS.get(target) != state) {
                    if (state.canStop(startId)) target.stopSelfResult(startId);
                    return;
                }
                boolean invoked = false;
                try {
                    if (KeyShortcutActions.MEMORY.equals(action)) {
                        Method memory = memoryMethod(service);
                        memory.invoke(target, "com.miui.voiceassist.query&&ai_physical_key", "MyHyperModifier shortcut");
                    } else {
                        openScreenRecognition(target, loader);
                    }
                    invoked = true;
                    // This confirms invocation only; native memory completion is asynchronous.
                    module.log(Log.INFO, TAG, "Native request dispatched: " + action);
                } catch (Throwable error) {
                    Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                            && error.getCause() != null ? error.getCause() : error;
                    module.log(Log.ERROR, TAG, "Native XiaoAi request failed: " + action, cause);
                    Toast.makeText(target, "小爱快捷操作失败：" + cause.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                } finally {
                    if (state.canStop(startId)) {
                        if (!invoked) {
                            target.stopSelfResult(startId);
                        } else {
                            // One bounded cleanup, no polling or permanent keep-alive. Give native
                            // screenshot/NLP dispatch time; a later native start revokes ownership.
                            Runnable cleanup = () -> {
                                CLEANUPS.remove(target);
                                if (state.canStop(startId)) target.stopSelfResult(startId);
                            };
                            CLEANUPS.put(target, cleanup);
                            main.postDelayed(cleanup, 20_000);
                        }
                    }
                }
            });
            return Service.START_NOT_STICKY;
        });
        module.log(Log.INFO, TAG, "Installed native memory/screen bridge with bounded service lifetime");
    }

    private static ShortcutServiceSession session(Service service) {
        return SESSIONS.computeIfAbsent(service, ignored -> new ShortcutServiceSession());
    }
    private static void cancelCleanup(Service service, Handler main) {
        Runnable pending = CLEANUPS.remove(service);
        if (pending != null) main.removeCallbacks(pending);
    }
    private static Method lifecycleMethod(Class<?> service, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> type = service; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, args);
                if (Modifier.isAbstract(method.getModifiers())) continue;
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static Method memoryMethod(Class<?> service) throws NoSuchMethodException {
        for (Class<?> type = service; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("triggerMemoryWithScreenshot", String.class, String.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException("VoiceService.triggerMemoryWithScreenshot(String,String)");
    }

    private static void openScreenRecognition(Service service, ClassLoader loader) throws Throwable {
        // The supplied hook.apk uses these native caches. Capture before opening the Activity
        // so recognition sees the user's previous screen rather than its own empty overlay.
        Class<?> db = Class.forName("com.xiaomi.voiceassistant.db", false, loader);
        Field singleton = KeyShortcutHooks.field(db, "a");
        Field bitmapCache = KeyShortcutHooks.field(db, "A");
        Field rectCache = KeyShortcutHooks.field(db, "B");
        Method capture = db.getDeclaredMethod("getScreenCaptureInner");
        capture.setAccessible(true);
        if (!Modifier.isStatic(singleton.getModifiers()) || !Modifier.isStatic(bitmapCache.getModifiers())
                || !Modifier.isStatic(rectCache.getModifiers()) || bitmapCache.getType() != Bitmap.class
                || rectCache.getType() != RectF.class || capture.getReturnType() != Bitmap.class) {
            throw new NoSuchFieldException("Unsupported XiaoAi screenshot cache signature");
        }
        Object instance = singleton.get(null);
        if (instance == null) throw new IllegalStateException("XiaoAi screenshot helper not initialized");
        Bitmap shot = (Bitmap) capture.invoke(instance);
        if (shot == null || shot.isRecycled()) throw new IllegalStateException("Native screenshot unavailable");
        bitmapCache.set(null, shot);
        rectCache.set(null, new RectF(0, 0, shot.getWidth(), shot.getHeight()));
        try {
            service.startActivity(new Intent().setClassName(ModuleScopePackage.VOICE_ASSIST,
                    "com.xiaomi.voiceassistant.screenrecognition.ScreenRecognitionActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable error) {
            bitmapCache.set(null, null);
            rectCache.set(null, null);
            throw error;
        }
    }
}
