package com.aritxonly.myhypermodifier;

import java.util.Arrays;

/** Settings-only fingerprint; owns scalar/recipe metadata, never a View or Context. */
final class GlassRecipeRefreshState {
    private boolean initialized;
    private boolean enabled;
    private float[] parameters;

    synchronized boolean changed(boolean nextEnabled, float[] nextParameters) {
        if (initialized && enabled == nextEnabled
                && (!nextEnabled || Arrays.equals(parameters, nextParameters))) return false;
        initialized = true;
        enabled = nextEnabled;
        parameters = nextEnabled && nextParameters != null ? nextParameters.clone() : null;
        return true;
    }
}
