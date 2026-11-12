package com.aritxonly.myhypermodifier;

import java.lang.reflect.Method;

/** Calls the owner's Lazy interface; module and SystemUI Kotlin runtimes need not share identity. */
final class ForeignLazyAccess {
    private final Method getValue;
    ForeignLazyAccess(Class<?> declaredType) throws NoSuchMethodException {
        getValue = declaredType.getMethod("getValue");
    }
    Object value(Object lazy) throws ReflectiveOperationException {
        return lazy == null ? null : getValue.invoke(lazy);
    }
}
