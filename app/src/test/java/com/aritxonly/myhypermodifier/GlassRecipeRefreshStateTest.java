package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class GlassRecipeRefreshStateTest {
    @Test public void unrelatedSettingsAndReparsedIdenticalRecipesDoNotRefreshCards() {
        GlassRecipeRefreshState state = new GlassRecipeRefreshState();
        float[] parameters = ShadeCardGlassPolicy.defaults();
        assertTrue(state.changed(false, parameters));
        parameters[14] = .83f;
        assertFalse(state.changed(false, parameters));
        assertTrue(state.changed(true, parameters));
        assertFalse(state.changed(true, parameters.clone()));
        // Retain a value snapshot so even an in-place caller edit is detected.
        parameters[14] = .17f;
        assertTrue(state.changed(true, parameters));
        assertFalse(state.changed(true, parameters));
        assertTrue(state.changed(false, parameters));
        parameters[14] = 0f;
        assertFalse(state.changed(false, parameters));
        assertTrue(state.changed(true, parameters));
    }
}
