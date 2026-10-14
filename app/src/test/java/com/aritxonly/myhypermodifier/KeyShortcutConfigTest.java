package com.aritxonly.myhypermodifier;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyShortcutConfigTest {
    @Test public void inputDecisionsNeverReadRemotePreferences() {
        Map<String, Object> values = new HashMap<>();
        values.put(KeyShortcutConfig.SCOPE_KEY, "system\ncom.miui.voiceassist");
        values.put(KeyShortcutActions.DOUBLE_POWER_KEY, "memory");
        values.put(KeyShortcutActions.VOLUME_POWER_KEY, "wallet");
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<SharedPreferences.OnSharedPreferenceChangeListener> listener = new AtomicReference<>();
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("registerOnSharedPreferenceChangeListener")) {
                        listener.set((SharedPreferences.OnSharedPreferenceChangeListener) args[0]); return null;
                    }
                    if (method.getName().equals("getBoolean") || method.getName().equals("getString")) {
                        reads.incrementAndGet(); return values.getOrDefault(args[0], args[1]);
                    }
                    throw new AssertionError("Unexpected preference access: " + method.getName());
                });
        KeyShortcutConfig config = new KeyShortcutConfig(prefs);
        int initialReads = reads.get();
        for (int i = 0; i < 10_000; i++) {
            assertEquals("wallet", config.current().volumePower);
            assertEquals("memory", config.current().doublePower);
            assertTrue(config.current().allows("memory"));
        }
        assertEquals(initialReads, reads.get());
        listener.get().onSharedPreferenceChanged(prefs, "notification_radius");
        assertEquals(initialReads, reads.get());
        values.put("module_hooks_enabled", false);
        listener.get().onSharedPreferenceChanged(prefs, "module_hooks_enabled");
        assertEquals("stock", config.current().doublePower);
        assertEquals("stock", config.current().volumePower);
        assertFalse(config.current().allows("memory"));
    }
    @Test public void unknownValuesAndMissingPreferencesRemainStock() {
        KeyShortcutConfig config = new KeyShortcutConfig(null);
        assertEquals("stock", config.current().volumePower);
        assertEquals("stock", config.current().doublePower);
        KeyShortcutConfig.Snapshot snapshot = new KeyShortcutConfig.Snapshot(true, "unknown", "none", true);
        assertEquals("stock", snapshot.volumePower);
        assertEquals("none", snapshot.doublePower);
        assertFalse(snapshot.allows("none"));
        assertFalse(new KeyShortcutConfig.Snapshot(true, "wallet", "memory", false).allows("memory"));
    }
}
