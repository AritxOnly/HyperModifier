package com.aritxonly.myhypermodifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Adapter to the retained HMC entry class; never loads another copy of the module. */
final class HyperMusicCoverClockPolicy {
    final Method roomForRows;
    final Method roomForRowsNow;
    final Method reassertClockRoom;
    private final Field holdY;
    private final Field selfDriving;
    private final Field roomEasing;
    private final Field roomShown;
    private final Field roomRaw;
    private final Field roomSoft;

    HyperMusicCoverClockPolicy(Class<?> main) throws ReflectiveOperationException {
        roomForRows = method(main, "roomForRows", float.class, float.class);
        roomForRowsNow = method(main, "roomForRowsNow", float.class, float.class);
        reassertClockRoom = method(main, "reassertClockRoom", void.class);
        holdY = field(main, "sHoldY", Float.class);
        selfDriving = field(main, "sSelfDriving", boolean.class);
        roomEasing = field(main, "sRoomEasing", boolean.class);
        roomShown = field(main, "sRoomShown", float.class);
        roomRaw = field(main, "sRoomRaw", float.class);
        roomSoft = field(main, "sRoomSoft", float.class);
    }

    boolean shouldBypass(boolean enabled) throws IllegalAccessException {
        return enabled && holdY.get(null) == null && !selfDriving.getBoolean(null);
    }

    void clearRoomEase() throws IllegalAccessException {
        // Stop an already scheduled ROOM_FRAME and avoid stale easing when the switch is off again.
        roomEasing.setBoolean(null, false);
        roomShown.setFloat(null, Float.NaN);
        roomRaw.setFloat(null, Float.NaN);
        roomSoft.setFloat(null, 0f);
    }

    private static Field field(Class<?> owner, String name, Class<?> type)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || !Modifier.isStatic(field.getModifiers())
                || Modifier.isFinal(field.getModifiers())) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, args);
        if (method.getReturnType() != result || !Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(name);
        method.setAccessible(true);
        return method;
    }
}
