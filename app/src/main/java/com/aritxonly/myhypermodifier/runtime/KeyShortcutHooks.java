package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import android.os.SystemClock;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Uses the system's key detector, preserving key cancellation, debounce and SOS handling. */
final class KeyShortcutHooks {
    private static final String TAG = "KeyShortcuts";
    private static final String ACTIONS = "com.miui.server.input.util.ShortCutActionsUtils";
    private static final Set<Object> VOLUME_RULES = Collections.newSetFromMap(new WeakHashMap<>());
    private static long lastLaunch;
    private static final java.util.concurrent.atomic.AtomicBoolean SCOPE_WARNING = new java.util.concurrent.atomic.AtomicBoolean();
    private static final Set<Method> RULE_HOOKS = new HashSet<>();

    static void install(XposedModule module, ClassLoader loader, SharedPreferences prefs) {
        KeyShortcutConfig config = new KeyShortcutConfig(prefs);
        try {
            Class<?> actions = Class.forName(ACTIONS, false, loader);
            int count = 0;
            for (Method method : actions.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("triggerFunction") || method.getReturnType() != boolean.class
                        || (types.length != 4 && types.length != 5) || types[0] != String.class
                        || types[1] != String.class || types[2] != Bundle.class || types[3] != boolean.class
                        || (types.length == 5 && types[4] != String.class)) continue;
                module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    String source = (String) chain.getArg(1);
                    if (!KeyShortcutActions.DOUBLE_POWER_SOURCE.equals(source)) return chain.proceed();
                    String action = config.current().doublePower;
                    if (KeyShortcutActions.STOCK.equals(action)) return chain.proceed();
                    if (KeyShortcutActions.NONE.equals(action)) return true;
                    if (KeyShortcutActions.WALLET.equals(action)) {
                        // Retain the OEM wallet's lockscreen handling and haptic feedback.
                        Object[] args = chain.getArgs().toArray();
                        args[0] = "mi_pay";
                        return chain.proceed(args);
                    }
                    if (!voiceScopeAllows(module, config, action)) return chain.proceed();
                    try {
                        Context context = (Context) field(actions, "mContext").get(chain.getThisObject());
                        if (launch(context, action, source, loader)) return true;
                    } catch (Throwable error) { module.log(Log.ERROR, TAG, "Double power launch failed", error); }
                    return chain.proceed();
                });
                count++;
            }
            if (count == 0) throw new NoSuchMethodException("triggerFunction signature");
            installFunctionLookup(module, loader, config);
            module.log(Log.INFO, TAG, "Installed double power dispatcher: " + count);
        } catch (Throwable error) { module.log(Log.WARN, TAG, "HyperOS double power hook unavailable", error); }
        // Android 17 moved KeyCombinationManager from policy to input.
        for (String name : new String[]{"com.android.server.policy.KeyCombinationManager",
                "com.android.server.input.KeyCombinationManager"}) {
            try {
                Class<?> manager = Class.forName(name, false, loader);
                Class<?> ruleBase = Class.forName(name + "$TwoKeysCombinationRule", false, loader);
                Field first = field(ruleBase, "mKeyCode1");
                Field second = field(ruleBase, "mKeyCode2");
                Method addRule = manager.getDeclaredMethod("addRule", ruleBase);
                module.hook(addRule).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object rule = chain.getArg(0);
                    if (KeyShortcutActions.isVolumePowerPair(first.getInt(rule), second.getInt(rule))) {
                        try { installRule(module, rule, config, loader); }
                        catch (Throwable error) { module.log(Log.ERROR, TAG, "Volume power rule unavailable", error); }
                    }
                    return chain.proceed();
                });
                module.log(Log.INFO, TAG, "Watching volume power rules in " + name);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) { module.log(Log.WARN, TAG, "Combination hook unavailable: " + name, error); }
        }
    }

    private static void installFunctionLookup(XposedModule module, ClassLoader loader,
            KeyShortcutConfig config) throws Throwable {
        Class<?> manager = Class.forName("com.android.server.policy.MiuiKeyShortcutManager", false, loader);
        Method lookup = manager.getDeclaredMethod("getFunction", String.class);
        if (lookup.getReturnType() != String.class) throw new NoSuchMethodException("getFunction return type");
        module.hook(lookup).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            if (KeyShortcutActions.DOUBLE_POWER_SOURCE.equals(chain.getArg(0))
                    && !KeyShortcutActions.STOCK.equals(config.current().doublePower)) {
                // Keep the native double-click detector enabled, even if stock is set to none.
                return "mi_pay";
            }
            return chain.proceed();
        });
    }

    private static synchronized void installRule(XposedModule module, Object rule,
            KeyShortcutConfig config, ClassLoader loader) throws Throwable {
        // Determine the pair once at registration, never reflect inside a shared preCondition.
        Method execute = method(rule.getClass(), "execute");
        VOLUME_RULES.add(rule);
        if (RULE_HOOKS.contains(execute)) return;
        module.hook(execute).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            String action = config.current().volumePower;
            if (KeyShortcutActions.STOCK.equals(action)) return chain.proceed();
            boolean registered;
            synchronized (KeyShortcutHooks.class) { registered = VOLUME_RULES.contains(chain.getThisObject()); }
            if (!registered) return chain.proceed();
            if (KeyShortcutActions.NONE.equals(action)) return null;
            if (!voiceScopeAllows(module, config, action)) return chain.proceed();
            try {
                Context context = ruleContext(chain.getThisObject(), loader);
                if (launch(context, action, KeyShortcutActions.VOLUME_POWER_SOURCE, loader)) return null;
            } catch (Throwable error) { module.log(Log.ERROR, TAG, "Volume power launch failed", error); }
            return chain.proceed();
        });
        RULE_HOOKS.add(execute);
        module.log(Log.INFO, TAG, "Installed volume power execute: " + rule.getClass().getName());
    }

    private static boolean voiceScopeAllows(XposedModule module, KeyShortcutConfig config, String action) {
        if (!KeyShortcutActions.MEMORY.equals(action) && !KeyShortcutActions.SCREEN.equals(action)) return true;
        if (config.current().voiceScoped) return true;
        if (SCOPE_WARNING.compareAndSet(false, true)) module.log(Log.WARN, TAG,
                "XiaoAi scope missing: enable com.miui.voiceassist, open module settings to sync scope, then reboot");
        return false;
    }

    private static Context ruleContext(Object rule, ClassLoader loader) throws Throwable {
        Object target = rule;
        for (int depth = 0; depth < 4 && target != null; depth++) {
            try { return (Context) field(target.getClass(), "mContext").get(target); }
            catch (NoSuchFieldException ignored) {}
            try { target = field(target.getClass(), "this$0").get(target); }
            catch (NoSuchFieldException ignored) { break; }
        }
        Class<?> thread = Class.forName("android.app.ActivityThread", false, loader);
        Object current = thread.getMethod("currentActivityThread").invoke(null);
        return (Context) thread.getMethod("getSystemContext").invoke(current);
    }

    private static boolean launch(Context context, String action, String source, ClassLoader loader) throws Throwable {
        if (context == null) return false;
        synchronized (KeyShortcutHooks.class) {
            long now = SystemClock.uptimeMillis();
            if (lastLaunch != 0 && now - lastLaunch < 700) return true;
            lastLaunch = now;
        }
        if (KeyShortcutActions.WALLET.equals(action)) {
            Class<?> actions = Class.forName(ACTIONS, false, loader);
            Object dispatcher = actions.getDeclaredMethod("getInstance", Context.class).invoke(null, context);
            Method trigger = actions.getDeclaredMethod("triggerFunction", String.class, String.class, Bundle.class, boolean.class);
            trigger.setAccessible(true);
            // Use the same OEM wallet launcher as double power, including current-user handling.
            return Boolean.TRUE.equals(trigger.invoke(dispatcher, "mi_pay", source, null, true));
        }
        Intent intent = new Intent(XiaoAiShortcutBridge.ACTION)
                .setClassName(ModuleScopePackage.VOICE_ASSIST, XiaoAiShortcutBridge.SERVICE)
                .putExtra(XiaoAiShortcutBridge.EXTRA_ACTION, action);
        Class<?> userHandle = Class.forName("android.os.UserHandle", false, loader);
        Object currentUser = userHandle.getField("CURRENT").get(null);
        Method start = Context.class.getMethod("startServiceAsUser", Intent.class, userHandle);
        return start.invoke(context, intent, currentUser) != null;
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field result = current.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod(name);
                if (Modifier.isAbstract(result.getModifiers())) continue;
                result.setAccessible(true); return result;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
