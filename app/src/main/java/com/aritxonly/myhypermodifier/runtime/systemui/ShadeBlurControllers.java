package com.aritxonly.myhypermodifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolves the two stock shade layouts once, while installing the SystemUI hooks. */
final class ShadeBlurControllers {
    private static final String PACKAGE = "com.miui.systemui.shade.blur.";

    static Class<?> blend(ClassLoader loader) throws ClassNotFoundException {
        return implementation("ShadeBlendBlurController", loader);
    }

    static Class<?> window(ClassLoader loader) throws ClassNotFoundException {
        return implementation("ShadeWindowBlurController", loader);
    }

    private static Class<?> implementation(String name, ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName(PACKAGE + name + "Impl", false, loader);
        } catch (ClassNotFoundException missingImpl) {
            return Class.forName(PACKAGE + name, false, loader);
        }
    }

    // On the Fold, R8 inlined each child's start into ShadeBlurController.start().
    static Start start(Class<?> child, String childField) throws ReflectiveOperationException {
        try {
            return new Start(child.getDeclaredMethod("start"), null);
        } catch (NoSuchMethodException inlined) {
            Class<?> parent = Class.forName(PACKAGE + "ShadeBlurController", false, child.getClassLoader());
            Field field = parent.getDeclaredField(childField);
            if (field.getType() != child) throw inlined;
            field.setAccessible(true);
            return new Start(parent.getDeclaredMethod("start"), field);
        }
    }

    static final class Start {
        final Method method;
        private final Field child;

        private Start(Method method, Field child) {
            this.method = method;
            this.child = child;
        }

        Object controller(Object receiver) throws IllegalAccessException {
            return child == null ? receiver : child.get(receiver);
        }
    }

    private ShadeBlurControllers() {}
}
