package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Reports the lifecycle of HyperOS's native recents gesture input consumer. The consumer is the
 * system-owned boundary around recents, unlike status-bar appearance callbacks which are also
 * emitted for launcher pages and ordinary app transitions.
 */
final class RecentsInputConsumerHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String WMS = "com.android.server.wm.WindowManagerService";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private RecentsInputConsumerHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> wms = Class.forName(WMS, false, loader);
            Method create = findCreateInputConsumer(wms);
            Method destroy = wms.getDeclaredMethod("destroyInputConsumer", IBinder.class,
                    int.class);
            if (create.getReturnType() != void.class || destroy.getReturnType() != boolean.class) {
                throw new NoSuchMethodException("Unexpected WindowManagerService input consumer API");
            }
            Context context = systemContext();
            if (context == null) throw new IllegalStateException("system context unavailable");
            Map<IBinder, String> consumers =
                    Collections.synchronizedMap(new WeakHashMap<>());

            module.hook(create).setId("recents-input-consumer-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        IBinder token = (IBinder) chain.getArg(0);
                        String name = (String) chain.getArg(1);
                        int displayId = (int) chain.getArg(2);
                        if (token != null && displayId == 0
                                && RecentsHandoffContract.RECENTS_INPUT_CONSUMER.equals(name)) {
                            consumers.put(token, name);
                            send(context, RecentsHandoffContract.ACTION_RECENTS_STARTED);
                        }
                        return result;
                    });

            module.hook(destroy).setId("recents-input-consumer-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        IBinder token = (IBinder) chain.getArg(0);
                        int displayId = (int) chain.getArg(1);
                        Object result = chain.proceed();
                        String name = token == null ? null : consumers.remove(token);
                        if (displayId == 0 && Boolean.TRUE.equals(result)
                                && RecentsHandoffContract.RECENTS_INPUT_CONSUMER.equals(name)) {
                            send(context, RecentsHandoffContract.ACTION_RECENTS_FINISHED);
                        }
                        return result;
                    });
            module.log(Log.INFO, TAG, "Installed recents input-consumer handoff");
        } catch (Throwable error) {
            INSTALLED.set(false);
            module.log(Log.WARN, TAG, "Recents handoff unavailable", error);
        }
    }

    private static void send(Context context, String action) {
        try {
            Intent intent = new Intent(action)
                    .setPackage("com.android.systemui")
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            context.sendBroadcastAsUser(intent, Process.myUserHandle());
        } catch (Throwable error) {
            Log.w(TAG, "Could not report recents consumer completion", error);
        }
    }

    private static Method findCreateInputConsumer(Class<?> wms) throws NoSuchMethodException {
        for (Method method : wms.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getName().equals("createInputConsumer")
                    && method.getReturnType() == void.class
                    && types.length == 4
                    && types[0] == IBinder.class
                    && types[1] == String.class
                    && types[2] == int.class
                    && types[3].getName().equals("android.view.InputChannel")) {
                return method;
            }
        }
        throw new NoSuchMethodException("WindowManagerService.createInputConsumer");
    }

    private static Context systemContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object current = activityThread.getMethod("currentActivityThread").invoke(null);
            return current == null ? null
                    : (Context) activityThread.getMethod("getSystemContext").invoke(current);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
