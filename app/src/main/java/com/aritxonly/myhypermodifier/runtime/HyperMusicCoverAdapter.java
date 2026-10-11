package com.aritxonly.myhypermodifier;

import android.graphics.RectF;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Binds only retained Main members, never a mapped/obfuscated helper class name. */
final class HyperMusicCoverAdapter {
    final Method glyphUnit, clockRoots, clockTarget, clockHeld, locked, shown, controlCenter,
            progress, screenOn, applyHide, fodForKeyguard, fodView, fodRing;
    final Field container, holdY, fpAvoid, hideFp;
    HyperMusicCoverAdapter(Class<?> main) throws ReflectiveOperationException {
        glyphUnit = method(main, "glyphUnit", float.class, RectF.class);
        clockRoots = method(main, "clockRoots", View[].class);
        clockTarget = method(main, "clockTarget", View.class, View.class);
        clockHeld = method(main, "clockHeld", boolean.class);
        locked = method(main, "keyguardLocked", boolean.class);
        shown = method(main, "onKeyguardNow", boolean.class);
        controlCenter = method(main, "controlCenterShown", boolean.class);
        progress = method(main, "cardProgress", float.class);
        screenOn = method(main, "screenOn", boolean.class);
        applyHide = method(main, "applyHideFp", void.class);
        fodForKeyguard = method(main, "fodForKeyguard", boolean.class, Object.class);
        fodView = method(main, "fodViewOf", Object.class, Object.class);
        fodRing = method(main, "isFodRing", boolean.class, int.class);
        container = field(main, "sContainer", View.class);
        holdY = field(main, "sHoldY", Float.class);
        fpAvoid = field(main, "sFpAvoid", int.class);
        hideFp = field(main, "sHideFp", boolean.class);
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
