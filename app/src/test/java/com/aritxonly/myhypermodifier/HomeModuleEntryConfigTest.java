package com.aritxonly.myhypermodifier;

import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeModuleEntryConfigTest {
    @Test public void recognizesActualLegacyAndModernModuleMarkers() {
        assertTrue(HomeModuleEntryConfig.isModule(true, Set.of()));
        assertTrue(HomeModuleEntryConfig.isModule(false, Set.of("assets/xposed_init")));
        assertTrue(HomeModuleEntryConfig.isModule(false, Set.of("META-INF/xposed/java_init.list")));
        assertTrue(HomeModuleEntryConfig.isModule(false, Set.of("META-INF/xposed/native_init.list")));
        assertFalse(HomeModuleEntryConfig.isModule(false, Set.of("META-INF/xposed/module.prop")));
        assertFalse(HomeModuleEntryConfig.isModule(false, Set.of("res/drawable/xposed_init.png")));
    }

    @Test public void eachModuleKeepsItsPositionAndSettingsActivityAcrossChanges() {
        HomeModuleEntryConfig.Entry a = new HomeModuleEntryConfig.Entry("com.example.a", "com.example.a.Settings", "A", "bottom", "de.robv.android.xposed.category.MODULE_SETTINGS");
        HomeModuleEntryConfig.Entry b = new HomeModuleEntryConfig.Entry("com.example.b", "com.example.b.Main", "B", "device");
        String json = HomeModuleEntryConfig.update(HomeModuleEntryConfig.update("{}", a, true), b, true);
        assertEquals(2, HomeModuleEntryConfig.decode(json).size());
        HomeModuleEntryConfig.Entry roundTrip = HomeModuleEntryConfig.decode(json).get(0);
        assertEquals(a.activity, roundTrip.activity);
        assertEquals(a.category, roundTrip.category);
        assertEquals("bottom", roundTrip.position);
        assertEquals("device", HomeModuleEntryConfig.decode(json).get(1).position);
        String disabled = HomeModuleEntryConfig.update(json, a, false);
        assertEquals(1, HomeModuleEntryConfig.decode(disabled).size());
        assertEquals("com.example.b", HomeModuleEntryConfig.decode(disabled).get(0).packageName);
    }

    @Test public void corruptConfigurationsAndIncompleteEntriesDoNotCreateBrokenHeaders() {
        assertTrue(HomeModuleEntryConfig.decode(null).isEmpty());
        assertTrue(HomeModuleEntryConfig.decode("invalid").isEmpty());
        assertTrue(HomeModuleEntryConfig.decode("{\"bad\":{},\"com.example.x\":{\"title\":\"X\"}}").isEmpty());
        String json = "{\"com.example.x\":{\"activity\":\"com.example.x.Main\",\"position\":\"invalid\",\"title\":\"\"}}";
        assertEquals("middle", HomeModuleEntryConfig.decode(json).get(0).position);
        assertEquals("com.example.x", HomeModuleEntryConfig.decode(json).get(0).title);
    }
}
