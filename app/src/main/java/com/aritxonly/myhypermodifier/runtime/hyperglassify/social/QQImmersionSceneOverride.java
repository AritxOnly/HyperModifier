package com.aritxonly.myhypermodifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Publish complete scene snapshots; native readers never see an in-place list mutation. */
final class QQImmersionSceneOverride {
    private final Object lazy;
    private final Field value;
    private final Constructor<?> pair;
    private final Method first, second;
    private List<?> originalList, writtenList;
    private Object originalPair, writtenPair;

    QQImmersionSceneOverride(Object initializedLazy, Class<?> pairType) throws ReflectiveOperationException {
        lazy = initializedLazy;
        if (!"kotlin.SynchronizedLazyImpl".equals(lazy.getClass().getName())) {
            throw new IllegalStateException("QQ 沉浸 Lazy 实现不匹配");
        }
        value = lazy.getClass().getDeclaredField("_value");
        if (value.getType() != Object.class || !Modifier.isVolatile(value.getModifiers())) {
            throw new NoSuchFieldException("QQ 沉浸快照不是 volatile 引用");
        }
        value.setAccessible(true);
        entries(); // The caller initializes through QQ's public Lazy API first.
        pair = pairType.getConstructor(Object.class, Object.class);
        first = pairType.getMethod("getFirst");
        second = pairType.getMethod("getSecond");
    }

    boolean enable() throws ReflectiveOperationException {
        List<?> currentList = entries();
        int index = commonIndex(currentList);
        Object current = index < 0 ? null : currentList.get(index);
        if (currentList == writtenList && current == writtenPair && writtenPair != null) return false;
        // Missing scene defaults to mode 1. Respect cloud-off 0, native 2 and all other scenes.
        int mode = current == null ? 1 : ((Number) second.invoke(current)).intValue();
        if (mode != 1) return false;
        Object replacement = pair.newInstance("common", 2);
        ArrayList<Object> next = new ArrayList<>(currentList);
        if (index < 0) next.add(replacement);
        else next.set(index, replacement);
        value.set(lazy, next); // Volatile publication; do not change final helper field b.
        originalList = currentList;
        originalPair = current;
        writtenList = next;
        writtenPair = replacement;
        return true;
    }

    boolean restore() throws IllegalAccessException {
        if (writtenList == null) return false;
        List<?> currentList = entries();
        boolean changed = false;
        if (writtenList != null && currentList == writtenList) {
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i) != writtenPair) continue;
                ArrayList<Object> restored = new ArrayList<>(currentList);
                if (originalPair == null) restored.remove(i);
                else restored.set(i, originalPair);
                // Retain changes to other scenes made by another owner in our published list.
                value.set(lazy, sameEntries(restored, originalList) ? originalList : restored);
                changed = true;
                break;
            }
        }
        originalList = writtenList = null;
        originalPair = writtenPair = null;
        return changed;
    }

    private List<?> entries() throws IllegalAccessException {
        Object entries = value.get(lazy);
        if (!(entries instanceof ArrayList<?>)) {
            throw new IllegalStateException("QQ 沉浸场景快照结构不匹配");
        }
        return (List<?>) entries;
    }

    private int commonIndex(List<?> entries) throws ReflectiveOperationException {
        for (int i = 0; i < entries.size(); i++) {
            if ("common".equals(first.invoke(entries.get(i)))) return i;
        }
        return -1;
    }

    private static boolean sameEntries(List<?> left, List<?> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) return false;
        }
        return true;
    }
}
