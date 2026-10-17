package com.aritxonly.myhypermodifier;

import android.graphics.RectF;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Retained Main API plus typed helper references resolved from the installed release DEX. */
final class HyperMusicCoverAdapter {
    final Method glyphUnit, clockRoots, clockTarget, clockHeld, locked, shown, controlCenter,
            screenOn, applyHide, fodForKeyguard;
    private final Method legacyProgress;
    private final Field progressField;
    private final java.util.Map<Class<?>, java.util.Optional<Field>> fodFields = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Boolean> ringResources = new java.util.concurrent.ConcurrentHashMap<>();
    final Field container, holdY, fpAvoid, hideFp;
    HyperMusicCoverAdapter(Class<?> main) throws ReflectiveOperationException {
        this(main, HyperMusicCoverSymbols.cached(main));
    }
    HyperMusicCoverAdapter(Class<?> main, HyperMusicCoverDexSymbols symbols) throws ReflectiveOperationException {
        boolean split = symbols != null || HyperMusicCoverSymbols.split(main);
        glyphUnit = split ? HyperMusicCoverSymbols.method(main, symbols, "glyphUnit", float.class, RectF.class)
                : method(main, "glyphUnit", float.class, RectF.class);
        clockRoots = method(main, "clockRoots", View[].class);
        clockTarget = method(main, "clockTarget", View.class, View.class);
        clockHeld = method(main, "clockHeld", boolean.class);
        locked = method(main, "keyguardLocked", boolean.class);
        shown = method(main, "onKeyguardNow", boolean.class);
        controlCenter = method(main, "controlCenterShown", boolean.class);
        legacyProgress = split ? null : method(main, "cardProgress", float.class);
        progressField = split ? HyperMusicCoverSymbols.field(main, symbols, "progress", float.class) : null;
        Method cachedScreen;
        try { cachedScreen = method(main, "screenOnCached", boolean.class); }
        catch (NoSuchMethodException old) { cachedScreen = method(main, "screenOn", boolean.class); }
        screenOn = cachedScreen;
        applyHide = split ? HyperMusicCoverSymbols.method(main, symbols, "applyHide", void.class) : method(main, "applyHideFp", void.class);
        fodForKeyguard = split ? HyperMusicCoverSymbols.method(main, symbols, "fodForKeyguard", boolean.class, Object.class)
                : method(main, "fodForKeyguard", boolean.class, Object.class);
        container = field(main, "sContainer", View.class);
        holdY = field(main, "sHoldY", Float.class);
        fpAvoid = split ? HyperMusicCoverSymbols.field(main, symbols, "fpAvoid", int.class) : field(main, "sFpAvoid", int.class);
        hideFp = split ? HyperMusicCoverSymbols.field(main, symbols, "hideFp", boolean.class) : field(main, "sHideFp", boolean.class);
    }
    float progress() throws ReflectiveOperationException {
        return progressField != null ? progressField.getFloat(null) : ((Number) legacyProgress.invoke(null)).floatValue();
    }
    Object fodView(Object animation) throws ReflectiveOperationException {
        java.util.Optional<Field> cached = fodFields.get(animation.getClass());
        if (cached == null) {
            Field found = null;
            for (Class<?> c = animation.getClass(); c != null; c = c.getSuperclass()) {
                try { found = c.getDeclaredField("mMiuiGxzwAnimationView"); found.setAccessible(true); break; }
                catch (NoSuchFieldException missing) { /* Native field may live on a parent. */ }
            }
            cached = java.util.Optional.ofNullable(found); fodFields.put(animation.getClass(), cached);
        }
        return cached.isPresent() ? cached.get().get(animation) : null;
    }
    boolean isFodRing(int resource) throws ReflectiveOperationException {
        Boolean known = ringResources.get(resource);
        if (known != null) return known;
        View view = (View) container.get(null);
        if (view == null) return false;
        boolean ring = false;
        try {
            String name = view.getResources().getResourceEntryName(resource);
            ring = name.startsWith("finger_circle") || name.contains("fingerprint_circle");
        } catch (android.content.res.Resources.NotFoundException missing) { /* Cache non-SystemUI resources too. */ }
        ringResources.put(resource, ring); return ring;
    }
    boolean bool(Method method, Object... args) throws ReflectiveOperationException {
        return (Boolean) method.invoke(null, args);
    }
    View[] roots() throws ReflectiveOperationException { return (View[]) clockRoots.invoke(null); }
    static Method method(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, args);
        if (method.getReturnType() != result || !Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(name + " signature changed");
        method.setAccessible(true);
        return method;
    }
    private static Field field(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || !Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(name + " signature changed");
        field.setAccessible(true);
        return field;
    }
}
