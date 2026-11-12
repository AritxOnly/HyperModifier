package com.aritxonly.myhypermodifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Prepare the small media layout before native Full AOD wake/shrink animation starts. */
final class MediaAodPreparation {
    private static final ClassValue<Field> PANEL = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            try { return type.getField("notifiFullAodController"); }
            catch (NoSuchFieldException ignored) { return null; }
        }
    };
    private static final ClassValue<Access> CONTROLLER = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) { return new Access(type); }
    };

    static boolean beforeWakeAnimation(Object panel, boolean appearing, Object animationType) {
        if (ModuleSettings.disableMediaIslandHooks || appearing || panel == null || ModuleSettings.inFullAod
                || !(animationType instanceof Enum<?> type) || !"FULL_AOD".equals(type.name())) return false;
        String preset = ModuleSettings.activeMediaPreset(false);
        if ("system".equals(preset) || (preset.isBlank() && !ModuleSettings.mediaEnabled
                && !ModuleSettings.customMediaConstraintSetEnabled)) return false;
        try {
            Field field = PANEL.get(panel.getClass());
            Object controller = field == null ? null : field.get(panel);
            if (controller == null) return false;
            Access access = CONTROLLER.get(controller.getClass());
            if (access.enabled == null || access.update == null || !access.enabled.getBoolean(null)) return false;
            // Native code invokes this only at completion. Invoke its existing implementation
            // before startNotificationWakeAnimation, so the first shrinking frame is already AOD.
            access.update.invoke(controller);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false; // The existing onFullAodStateChanged hook remains a safe fallback.
        }
    }
    private static final class Access {
        final Field enabled;
        final Method update;
        Access(Class<?> type) {
            Field field = null; Method method = null;
            try {
                field = type.getField("mEnableFullAod");
                method = type.getMethod("updateMediaNotificationStyle");
            } catch (ReflectiveOperationException ignored) { }
            enabled = field; update = method;
        }
    }
}
