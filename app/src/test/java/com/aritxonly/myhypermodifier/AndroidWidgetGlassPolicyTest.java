package com.aritxonly.myhypermodifier;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class AndroidWidgetGlassPolicyTest {
    @Test public void defaultAllIncludesNewApplicationsAndProviders() {
        assertTrue(AndroidWidgetGlassPolicy.matches(AndroidWidgetGlassPolicy.DEFAULT_TARGETS, "org.example.newapp", ".NewWidget"));
        assertTrue(AndroidWidgetGlassPolicy.includesPackage(AndroidWidgetGlassPolicy.DEFAULT_TARGETS, "org.example.newapp"));
        assertFalse(AndroidWidgetGlassPolicy.matches("*", "", ".Widget"));
    }
    @Test public void exclusionsOverrideAllAtApplicationAndProviderGranularity() {
        String appExcluded = "*\n-org.example.calendar";
        assertFalse(AndroidWidgetGlassPolicy.matches(appExcluded, "org.example.calendar", ".Widget"));
        assertFalse(AndroidWidgetGlassPolicy.includesPackage(appExcluded, "org.example.calendar"));
        assertTrue(AndroidWidgetGlassPolicy.matches(appExcluded, "org.example.calendar.extra", ".Widget"));
        String providerExcluded = "*\n-org.example.calendar/org.example.calendar.Widget";
        assertFalse(AndroidWidgetGlassPolicy.matches(providerExcluded, "org.example.calendar", ".Widget"));
        assertTrue(AndroidWidgetGlassPolicy.matches(providerExcluded, "org.example.calendar", ".OtherWidget"));
        assertTrue(AndroidWidgetGlassPolicy.includesPackage(providerExcluded, "org.example.calendar"));
        assertTrue(AndroidWidgetGlassPolicy.targets("-*\n--org.example.calendar").isEmpty());
    }
    @Test public void applicationRuleIncludesFutureProvidersWithoutMatchingPackagePrefixes() {
        assertTrue(AndroidWidgetGlassPolicy.includesPackage("org.example.calendar", "org.example.calendar"));
        assertFalse(AndroidWidgetGlassPolicy.includesPackage("org.example.calendar", "org.example"));
        assertFalse(AndroidWidgetGlassPolicy.includesPackage("org.example.calendar.extra", "org.example.calendar"));
    }
    @Test public void providerRulesActivateOnlyTheirOwnApplication() {
        assertTrue(AndroidWidgetGlassPolicy.includesPackage(AndroidWidgetGlassPolicy.LEGACY_TARGET, "com.aritxonly.deadliner"));
        assertFalse(AndroidWidgetGlassPolicy.includesPackage(AndroidWidgetGlassPolicy.LEGACY_TARGET, "com.aritxonly.deadliner.fake"));
        assertEquals(Set.of(AndroidWidgetGlassPolicy.LEGACY_TARGET), AndroidWidgetGlassPolicy.targets(AndroidWidgetGlassPolicy.LEGACY_TARGET));
    }
    @Test public void providerAndPackageSelectionsHaveDifferentGranularity() {
        assertTrue(AndroidWidgetGlassPolicy.matches("org.example.calendar", "org.example.calendar", ".NewWidget"));
        assertTrue(AndroidWidgetGlassPolicy.matches("org.example.calendar/org.example.calendar.Widget", "org.example.calendar", ".Widget"));
        assertFalse(AndroidWidgetGlassPolicy.matches("org.example.calendar/org.example.calendar.Widget", "org.example.calendar", ".OtherWidget"));
        assertFalse(AndroidWidgetGlassPolicy.matches("", "com.aritxonly.deadliner", ".Widget"));
    }
    @Test public void invalidOrOversizedSelectionCannotActivateAnApplication() {
        assertTrue(AndroidWidgetGlassPolicy.targets("bad rule\norg.example/\norg.example/.Widget\norg.example//Widget").isEmpty());
        assertTrue(AndroidWidgetGlassPolicy.targets("a".repeat(AndroidWidgetGlassPolicy.MAX_BYTES + 1)).isEmpty());
        assertTrue(AndroidWidgetGlassPolicy.targets(null).isEmpty());
    }
    @Test public void serializedSelectionIsDeterministicAndEmptySelectionStaysEmpty() {
        assertEquals("org.example.a\norg.example.b", AndroidWidgetGlassPolicy.encode(Set.of("org.example.b", "org.example.a")));
        assertEquals("", AndroidWidgetGlassPolicy.encode(Set.of()));
        assertEquals(Set.of("org.example.a"), AndroidWidgetGlassPolicy.targets("org.example.a\norg.example.a"));
    }
}
