package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import android.content.res.TypedArray;

import java.lang.reflect.Field;

/** Resource-name based overrides shared by SystemUI, its plugin and MiLink processes. */
final class ResourceOverrides {
    private ResourceOverrides() {
    }

    /** Applies every requested dimension regardless of dimen qualifier selection. */
    static Float replacementDimension(Resources resources, int resourceId) {
        String name = resourceEntryName(resources, resourceId);
        if (name == null) {
            return null;
        }
        float dp;
        switch (name) {
            case "notification_item_bg_radius":
                if (!ModuleSettings.notificationsEnabled) return null;
                dp = ModuleSettings.notificationRadius;
                break;
            case "control_center_universal_corner_radius":
                if (!ModuleSettings.controlCenterEnabled) return null;
                dp = ModuleSettings.controlCenterRadius;
                break;
            case "detail_panel_background_corner_radius":
                if (!ModuleSettings.controlCenterEnabled
                        || !ModuleSettings.advancedControlCenterCorners) return null;
                dp = ModuleSettings.controlCenterCardRadius;
                break;
            case "miuix_recyclerview_card_group_radius":
            case "circulate_card_corner_radius":
            case "circulate_card_shape":
                if (!ModuleSettings.miLinkMainCardsEnabled) return null;
                dp = ModuleSettings.miLinkMainCardRadius;
                break;
            case "qs_media_session_height_expanded":
                if (!ModuleSettings.mediaEnabled) return null;
                dp = ModuleSettings.expandedHeight;
                break;
            case "qs_media_session_height_collapsed":
                if (!ModuleSettings.mediaEnabled) return null;
                dp = ModuleSettings.collapsedHeight;
                break;
            case "qs_media_session_height_expanded_fullAod":
                if (!ModuleSettings.mediaEnabled) return null;
                dp = ModuleSettings.fullAodHeight;
                break;
            default:
                return null;
        }
        return dp * resources.getDisplayMetrics().density;
    }

    static Float replacementTypedArrayDimension(TypedArray array, int index) {
        try {
            int resourceId = array.getResourceId(index, 0);
            if (resourceId == 0) {
                return null;
            }
            Field resourcesField = TypedArray.class.getDeclaredField("mResources");
            resourcesField.setAccessible(true);
            Object resources = resourcesField.get(array);
            return resources instanceof Resources
                    ? replacementDimension((Resources) resources, resourceId) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String resourceEntryName(Resources resources, int resourceId) {
        try {
            return resources.getResourceEntryName(resourceId);
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
    }
}
