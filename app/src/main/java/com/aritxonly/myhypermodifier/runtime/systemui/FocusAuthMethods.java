package com.aritxonly.myhypermodifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exact signature discovery for obfuscated XMS Focus-auth methods; ambiguity disables the hook. */
final class FocusAuthMethods {
    final Method failure;
    final Method success;
    private FocusAuthMethods(Method failure, Method success) { this.failure = failure; this.success = success; }
    static FocusAuthMethods find(Class<?> session, Class<?> error, Class<?> result) throws ReflectiveOperationException {
        Method failure = null, success = null;
        for (Method method : session.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers) || method.getReturnType() != result) continue;
            if (method.getParameterCount() == 0) {
                if (success != null) throw new NoSuchMethodException("Ambiguous XMS Focus success methods");
                success = method;
            } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == error) {
                if (failure != null) throw new NoSuchMethodException("Ambiguous XMS Focus error methods");
                failure = method;
            }
        }
        if (failure == null || success == null) throw new NoSuchMethodException("Unsupported XMS Focus authentication signatures");
        failure.setAccessible(true); success.setAccessible(true);
        return new FocusAuthMethods(failure, success);
    }
}
