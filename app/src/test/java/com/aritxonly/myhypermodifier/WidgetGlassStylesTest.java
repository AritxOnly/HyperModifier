package com.aritxonly.myhypermodifier;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class WidgetGlassStylesTest {
    @Test public void compiledRulesMatchStringResolverAndDetectOnlyChangedSources() {
        String styles = "com.example/com.example.Widget#42\t0\ncom.example/com.example.Widget\t2";
        WidgetGlassStyles.Rules rules = new WidgetGlassStyles.Rules(styles, "*", "*");
        assertTrue(rules.matchesSources(new String(styles), "*", "*"));
        assertFalse(rules.matchesSources(styles, "-*", "*"));
        for (int id : new int[]{0, 42, 43}) {
            assertEquals(WidgetGlassStyles.resolve(styles, "*", true, "*", "com.example", "com.example.Widget", id),
                WidgetGlassStyles.resolve(rules.styles, rules.glass, true, rules.clock, "com.example", "com.example.Widget", id));
        }
        assertEquals(WidgetGlassStyles.OFF, WidgetGlassStyles.resolve(rules.styles, rules.glass, true, rules.clock,
                "com.example", "com.example.Widget", 42));
    }

    private static final String PKG = "org.example.widgets", WIDGET = PKG + "/" + PKG + ".Widget";
    private int mode(String styles, int id) { return WidgetGlassStyles.resolve(styles, "*", true, "*", PKG, PKG + ".Widget", id); }
    @Test public void instanceBeatsProviderAndApplicationWithoutChangingAnotherInstance() {
        String rules = PKG + "\t1\n" + WIDGET + "\t2\n" + WIDGET + "#42\t0";
        assertEquals(0, WidgetGlassStyles.resolve(rules, "*", true, "*", PKG, "Widget", 42));
        assertEquals(0, mode(rules, 42)); assertEquals(2, mode(rules, 43)); assertEquals(2, mode(rules, 0));
        assertEquals(1, WidgetGlassStyles.resolve(rules, "*", true, "*", PKG, PKG + ".Other", 42));
    }
    @Test public void legacyRulesKeepDisabledTintedAndClearSelections() {
        assertEquals(2, mode("", 42));
        assertEquals(1, WidgetGlassStyles.resolve("", "*", false, "*", PKG, ".Widget", 42));
        assertEquals(0, WidgetGlassStyles.resolve("", "*\n-" + WIDGET, true, "*", PKG, ".Widget", 42));
        assertEquals(1, WidgetGlassStyles.resolve("", "*", true, "*\n-" + WIDGET, PKG, ".Widget", 42));
    }
    @Test public void uniformAppSelectionClearsProviderDraftsButRetainsInstanceChoices() {
        String rules = PKG + "\t3\n" + WIDGET + "\t0\n" + WIDGET + "#42\t2\norg.example.other\t0";
        Map<String, Integer> result = WidgetGlassStyles.setApplication(rules, PKG, 1);
        assertFalse(result.containsKey(WIDGET)); assertEquals(Integer.valueOf(2), result.get(WIDGET + "#42"));
        assertEquals(Integer.valueOf(0), result.get("org.example.other"));
        assertEquals(1, mode(WidgetGlassStyles.encode(result), 43)); assertEquals(2, mode(WidgetGlassStyles.encode(result), 42));
    }
    @Test public void customAppInheritsFallbackForFutureTypesAndAllowsExplicitProvider() {
        assertEquals(2, mode(PKG + "\t3", 42));
        assertEquals(0, mode(PKG + "\t3\n" + WIDGET + "\t0", 42));
    }
    @Test public void invalidStylesCannotBecomeActiveOverrides() {
        assertTrue(WidgetGlassStyles.parse(WIDGET + "#0\t2\n" + WIDGET + "#2147483648\t2\n" + WIDGET + "\t3\n-*\t2\n" + PKG + "\t4").isEmpty());
        assertTrue(WidgetGlassStyles.parse("x".repeat(16385)).isEmpty());
        assertEquals(PKG + "\t1\n" + WIDGET + "#42\t2", WidgetGlassStyles.encode(Map.of(WIDGET + "#42", 2, PKG, 1)));
    }
}
