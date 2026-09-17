package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

/** Control Center-specific corner-radius routing and XML drawable patching. */
final class ControlCenterAppearance {
    private ControlCenterAppearance() {
    }

    static float cornerPixels(Object receiver, ControlCenterSurface surface, float fallback) {
        if (!ModuleSettings.controlCenterEnabled) {
            return fallback;
        }
        Resources resources = receiver instanceof View
                ? ((View) receiver).getContext().getResources() : Resources.getSystem();
        return controlCenterRadius(surface) * resources.getDisplayMetrics().density;
    }

    static float controlCenterRadius(ControlCenterSurface surface) {
        if (!ModuleSettings.advancedControlCenterCorners) {
            return ModuleSettings.controlCenterRadius;
        }
        switch (surface) {
            case TILE:
                return ModuleSettings.controlCenterTileRadius;
            case CARD:
                return ModuleSettings.controlCenterCardRadius;
            case SLIDER:
                return ModuleSettings.controlCenterSliderRadius;
            case DETAIL_SLIDER:
                return ModuleSettings.controlCenterDetailSliderRadius;
            case MEDIA:
                return ModuleSettings.controlCenterMediaRadius;
            case EXTERNAL_ENTRY:
                return ModuleSettings.controlCenterExternalEntryRadius;
            default:
                return ModuleSettings.controlCenterRadius;
        }
    }

    static void patchPluginDrawable(Resources resources, int resourceId, Object result) {
        if (!(result instanceof Drawable) || !ModuleSettings.controlCenterEnabled) {
            return;
        }
        ControlCenterSurface surface = drawableSurface(
                ResourceOverrides.resourceEntryName(resources, resourceId));
        if (surface == null) {
            return;
        }
        float pixels = controlCenterRadius(surface) * resources.getDisplayMetrics().density;
        setDrawableCornerRadius((Drawable) result, pixels);
    }

    /** Every XML shape in the reference plugin that uses control_center_universal_corner_radius. */
    static ControlCenterSurface drawableSurface(String resourceName) {
        if (resourceName == null) {
            return null;
        }
        switch (resourceName) {
            case "qs_card_background_disabled":
            case "qs_card_background_enabled":
            case "qs_card_background_restricted":
            case "qs_card_background_unavailable":
                return ControlCenterSurface.CARD;
            case "toggle_slider_background":
                return ControlCenterSurface.SLIDER;
            case "toggle_slider_detail_background":
                return ControlCenterSurface.DETAIL_SLIDER;
            case "media_player_background":
                return ControlCenterSurface.MEDIA;
            case "external_entry_background":
                return ControlCenterSurface.EXTERNAL_ENTRY;
            default:
                return null;
        }
    }

    static void setDrawableCornerRadius(Drawable drawable, float pixels) {
        Drawable mutable = drawable.mutate();
        if (mutable instanceof GradientDrawable) {
            ((GradientDrawable) mutable).setCornerRadius(pixels);
        } else if (mutable instanceof InsetDrawable) {
            setDrawableCornerRadius(((InsetDrawable) mutable).getDrawable(), pixels);
        } else if (mutable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) mutable;
            for (int index = 0; index < layers.getNumberOfLayers(); index++) {
                setDrawableCornerRadius(layers.getDrawable(index), pixels);
            }
        }
    }
}

enum ControlCenterSurface {
    TILE,
    CARD,
    SLIDER,
    DETAIL_SLIDER,
    MEDIA,
    EXTERNAL_ENTRY
}
