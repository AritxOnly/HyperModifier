package com.aritxonly.myhypermodifier

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsBackupCodecTest {
    private val values: Map<String, Any> = linkedMapOf("enabled" to true, "radius" to 12.5f,
        "mode" to 2, "rules" to "{\"app\":\"系统\"}", "image" to "YWJj")

    @Test fun roundTripPreservesPreferenceTypesAndEmbeddedStrings() {
        assertEquals(values, SettingsBackupCodec.decode(SettingsBackupCodec.encode(values), values))
    }

    @Test fun rejectsUnknownVersionPackageKeysAndWrongTypes() {
        val original = SettingsBackupCodec.encode(values)
        val invalid = listOf(
            JSONObject(original).put("version", 2),
            JSONObject(original).put("format", "other.module"),
            JSONObject(original).apply { getJSONObject("settings").put("diagnostic_token", "token") },
            JSONObject(original).apply { getJSONObject("settings").put("enabled", "true") },
            JSONObject(original).apply { getJSONObject("settings").put("rules", JSONObject()) },
            JSONObject(original).apply { getJSONObject("settings").put("mode", 1.5) },
            JSONObject(original).apply { getJSONObject("settings").put("mode", Long.MAX_VALUE) },
            JSONObject(original).apply { getJSONObject("settings").put("radius", 1e100) },
            JSONObject(original).put("settings", JSONObject()),
        )
        invalid.forEach { assertTrue(it.toString(), runCatching { SettingsBackupCodec.decode(it.toString(), values) }.isFailure) }
        assertTrue(runCatching { SettingsBackupCodec.decode("broken", values) }.isFailure)
    }

    @Test fun acceptsPartialOlderBackupWithoutInventingValues() {
        val partial = mapOf<String, Any>("enabled" to false)
        assertEquals(partial, SettingsBackupCodec.decode(SettingsBackupCodec.encode(partial), values))
    }

    @Test fun removedNotificationTintDoesNotBreakOlderBackupsOrReturnToTheSchema() {
        val previous = values + mapOf("dynamic_island_notification_opacity_enabled" to true,
            "dynamic_island_notification_opacity" to 65f)
        assertEquals(values, SettingsBackupCodec.decode(SettingsBackupCodec.encode(previous), values))
    }

    @Test fun rejectsOversizedFileBeforeParsing() {
        assertTrue(runCatching { SettingsBackupCodec.decode(" ".repeat(SettingsBackupCodec.MAX_BYTES + 1), values) }.isFailure)
    }
}
