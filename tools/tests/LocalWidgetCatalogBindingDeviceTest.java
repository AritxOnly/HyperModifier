package com.aritxonly.myhypermodifier;

import dalvik.system.PathClassLoader;
import java.lang.reflect.*;

/** Resolve the real production Binding against an installed assistant APK, without installing
 * Hooks, registering listeners, querying providers or changing the workspace. Build with the
 * production LocalWidgetCatalog*.java files, libxposed API and a no-op HookDiagnostics test shim.
 * Only construct/rollback is exercised here; live Hook success still requires process testing.
 */
public final class LocalWidgetCatalogBindingDeviceTest {
    public static void main(String[] args) throws Throwable {
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> binding = Class.forName("com.aritxonly.myhypermodifier.LocalWidgetCatalogHooks$Binding");
        Constructor<?> constructor = binding.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object instance;
        try { instance = constructor.newInstance(null, loader, null); }
        catch (InvocationTargetException error) { throw error.getCause(); }
        // Exercise the production cancellation path with actual obfuscated Job/Continuation types.
        Field activeField = binding.getDeclaredField("jobActive"); activeField.setAccessible(true);
        Method active = (Method) activeField.get(instance);
        Field keyField = binding.getDeclaredField("jobKey"); keyField.setAccessible(true);
        Object key = keyField.get(instance);
        Class<?> continuation = Class.forName("kotlin.coroutines.e", false, loader);
        boolean[] running = {true};
        Object job = Proxy.newProxyInstance(loader, new Class<?>[]{active.getDeclaringClass()}, (self, method, values) -> {
            if (method.getName().equals("isActive")) return running[0];
            if (method.getName().equals("getKey")) return key;
            if (method.getName().equals("get")) return values[0] == key ? self : null;
            throw new AssertionError(method);
        });
        Object[] delivered = {null};
        Object target = Proxy.newProxyInstance(loader, new Class<?>[]{continuation}, (self, method, values) -> {
            if (method.getName().equals("getContext")) return job;
            if (method.getName().equals("resumeWith")) { delivered[0] = values[0]; return null; }
            throw new AssertionError(method);
        });
        Method deliver = binding.getDeclaredMethod("deliver", Object.class, Object.class); deliver.setAccessible(true);
        Object result = new Object();
        deliver.invoke(instance, target, result);
        if (delivered[0] != result) throw new AssertionError("Active caller result changed");
        running[0] = false;
        deliver.invoke(instance, target, result);
        Class<?> failure = Class.forName("kotlin.Result$Failure", false, loader);
        if (!failure.isInstance(delivered[0]) || !(failure.getField("exception").get(delivered[0])
                instanceof java.util.concurrent.CancellationException))
            throw new AssertionError("Cancelled caller must unwind with native cancellation failure");
        Method rollback = binding.getDeclaredMethod("rollback");
        rollback.setAccessible(true);
        rollback.invoke(instance);
        System.out.println("PASS: production catalogue Binding resolves every method, field, model and Flow contract; active delivery/cancelled continuation unwinding pass; no package-version query or Hook installation");
    }
}
