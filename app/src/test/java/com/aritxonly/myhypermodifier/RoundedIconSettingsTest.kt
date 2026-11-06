package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class RoundedIconSettingsTest {
    private val retired = mapOf<String, Any>(
        "rounded_icon_corner_radius" to .9f, "rounded_mobile_weight" to .3f,
        "rounded_wifi_weight" to 0f, "rounded_wifi_scale" to 110f,
        "rounded_battery_width" to 80f, "rounded_battery_height" to 80f,
        "rounded_mobile_offset_y" to -2f
    )

    @Test fun oldBackupRestoresFeatureSwitchesWithoutRestoringExperimentalGeometry() {
        val context = RuntimeEnvironment.getApplication()
        val backup = retired + mapOf("rounded_network_icons" to true, "status_bar_solid_battery_frame" to true)
        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup), ModifierSettingsStore.backupSchema())
        assertEquals(setOf("rounded_network_icons", "status_bar_solid_battery_frame"), decoded.keys)
        ModifierSettingsStore.restoreBackup(context, backup)
        assertTrue(ModifierSettingsStore.load(context).roundedNetworkIcons)
        assertTrue(ModifierSettingsStore.load(context).statusBarSolidBatteryFrame)
        assertTrue(ModifierSettingsStore.backupSchema().keys.intersect(retired.keys).isEmpty())
        assertTrue(ModifierSettingsStore.backupValues(context).keys.intersect(retired.keys).isEmpty())
        val bundle = ModifierSettingsStore.toBundle(ModifierSettingsStore.load(context))
        retired.keys.forEach { assertFalse(bundle.containsKey(it)) }
    }

    @Test fun retiredKeysDoNotWeakenUnknownKeyOrTypeValidation() {
        for (invalid in listOf(mapOf("rounded_wifi_scale" to "110"), mapOf("rounded_unknown" to 1f))) {
            try {
                SettingsBackupCodec.decode(SettingsBackupCodec.encode(invalid), ModifierSettingsStore.backupSchema())
                fail("Invalid legacy value must be rejected")
            } catch (_: IllegalArgumentException) {
                // Type validation.
            } catch (_: IllegalStateException) {
                // Unknown key validation.
            }
        }
    }

    @Test fun batteryPercentPlacementRoundTripsThroughBackup() {
        val context = RuntimeEnvironment.getApplication()
        val backup = mapOf(
            "control_center_battery_percent_placement" to
                ControlCenterBatteryPercentPolicy.PLACEMENT_INSIDE,
        )
        val schema = ModifierSettingsStore.backupSchema()
        assertEquals(backup, SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup), schema))
        ModifierSettingsStore.restoreBackup(context, backup)
        assertEquals(
            ControlCenterBatteryPercentPolicy.PLACEMENT_INSIDE,
            ModifierSettingsStore.load(context).controlCenterBatteryPercentPlacement,
        )
    }
}
