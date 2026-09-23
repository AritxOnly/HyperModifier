package com.aritxonly.myhypermodifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Defensive reflection helpers for HyperOS classes that vary between releases. */
final class ReflectiveAccess {
    private ReflectiveAccess() {
    }

    static Object layout(Object constraintSet, int viewId) {
        if (constraintSet == null || viewId == 0) {
            return null;
        }
        try {
            Method getConstraint = constraintSet.getClass().getMethod("getConstraint", int.class);
            Object constraint = getConstraint.invoke(constraintSet, viewId);
            return fieldValue(constraint, "layout");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static void connect(Object layout, String field, int target) {
        setInt(layout, field, target);
    }

    static void setInt(Object target, String fieldName, int value) {
        if (target == null) {
            return;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            field.setInt(target, value);
        } catch (ReflectiveOperationException ignored) {
            // MIUI ships different ConstraintLayout and widget revisions across releases.
        }
    }

    static void setFloat(Object target, String fieldName, float value) {
        if (target == null) {
            return;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            field.setFloat(target, value);
        } catch (ReflectiveOperationException ignored) {
            // MIUI ships different ConstraintLayout and widget revisions across releases.
        }
    }

    static void setString(Object target, String fieldName, String value) {
        if (target == null) {
            return;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            field.set(target, value);
        } catch (ReflectiveOperationException ignored) {
            // The optional property is unavailable on this ConstraintLayout revision.
        }
    }

    static Object fieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static boolean booleanDeclaredField(Object target, String fieldName, boolean fallback) {
        if (target == null) {
            return fallback;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getBoolean(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static int intField(Object target, String fieldName, int fallback) {
        Object value = fieldValue(target, fieldName);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    static void setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        try {
            target.getClass().getField(fieldName).set(target, value);
        } catch (ReflectiveOperationException ignored) {
            // MIUI ships different holder revisions across releases.
        }
    }

    /** Sets a private field declared by a framework or SystemUI implementation class. */
    static void setDeclaredFieldValue(Object target, String fieldName, Object value) {
        if (target == null) {
            return;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }

    static void invokeBoolean(Object target, String name, boolean value) {
        try {
            target.getClass().getMethod(name, boolean.class).invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
            // Optional property on MIUI widget revisions.
        }
    }

    static void invokeInt(Object target, String name, int value) {
        try {
            target.getClass().getMethod(name, int.class).invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
            // Optional property on MIUI widget revisions.
        }
    }
}
