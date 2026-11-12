package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationWidthPolicyTest {
    @Test public void ceilingNeverWidensNativeCardsAndRoundsOddPixelsInward() {
        assertEquals(24, NotificationWidthPolicy.sidePadding(360, 24, 1, 400));
        assertEquals(300, NotificationWidthPolicy.sidePadding(1000, 24, 1, 400));
        assertEquals(301, NotificationWidthPolicy.sidePadding(1001, 24, 1, 400));
        assertEquals(200, NotificationWidthPolicy.sidePadding(1200, 24, 2, 400));
        assertEquals(24, NotificationWidthPolicy.sidePadding(0, 24, 2, 400));
        assertEquals(24, NotificationWidthPolicy.sidePadding(1000, 24, Float.NaN, 400));
    }
    @Test public void sceneLimitsAreIndependentAndCollapsedShadePreservesHeadsUp() {
        assertEquals(300, limit(true, false, 1, false), 0);
        assertEquals(400, limit(true, false, 0, true), 0);
        assertEquals(400, limit(true, false, 2, true), 0);
        assertEquals(0, limit(true, false, 0, false), 0);
        assertEquals(0, limit(false, false, 1, true), 0);
        assertEquals(0, limit(true, true, 1, true), 0);
        assertEquals(0, NotificationWidthPolicy.activeLimit(true, false, 1, true, true, 400, false, 300), 0);
    }
    private static float limit(boolean enabled, boolean compatibility, int scene, boolean expanded) {
        return NotificationWidthPolicy.activeLimit(enabled, compatibility, scene, expanded, true, 400, true, 300);
    }
    @Test public void invalidBackupValuesAreNormalized() {
        assertEquals(400, NotificationWidthPolicy.normalize(Float.NaN), 0);
        assertEquals(400, NotificationWidthPolicy.normalize(Float.POSITIVE_INFINITY), 0);
        assertEquals(240, NotificationWidthPolicy.normalize(-1), 0);
        assertEquals(840, NotificationWidthPolicy.normalize(9999), 0);
    }
    @Test public void marginsAreAbsoluteAndZeroIsEnabledRatherThanNativePadding() {
        int zero = NotificationWidthPolicy.settingCode(true, NotificationWidthPolicy.MARGINS, 400, 0);
        assertNotEquals(0, zero);
        assertEquals(0, NotificationWidthPolicy.configuredPadding(400, 14, 1, zero));
        int margin = NotificationWidthPolicy.settingCode(true, NotificationWidthPolicy.MARGINS, 400, 20);
        assertEquals(20, NotificationWidthPolicy.configuredPadding(400, 14, 1, margin));
        assertEquals(40, NotificationWidthPolicy.configuredPadding(1000, 28, 2, margin));
        assertEquals(14, NotificationWidthPolicy.configuredPadding(400, 14, 1, 0));
    }
    @Test public void largeMarginsKeepContentUsableAndFixedModePreservesPriorCeiling() {
        int margins = NotificationWidthPolicy.settingCode(true, NotificationWidthPolicy.MARGINS, 400, 160);
        assertEquals(60, NotificationWidthPolicy.configuredPadding(360, 14, 1, margins));
        assertEquals(0, NotificationWidthPolicy.configuredPadding(200, 14, 1, margins));
        int fixed = NotificationWidthPolicy.settingCode(true, NotificationWidthPolicy.FIXED, 840, 0);
        assertEquals(14, NotificationWidthPolicy.configuredPadding(504, 14, 1, fixed));
    }
    @Test public void onlyTheCurrentModesValueContributesToRefreshSignature() {
        assertEquals(NotificationWidthPolicy.settingCode(true, 0, 400, 10), NotificationWidthPolicy.settingCode(true, 0, 400, 90));
        assertEquals(NotificationWidthPolicy.settingCode(true, 1, 400, 10), NotificationWidthPolicy.settingCode(true, 1, 700, 10));
        assertNotEquals(NotificationWidthPolicy.settingCode(true, 0, 400, 10), NotificationWidthPolicy.settingCode(true, 1, 400, 10));
        assertEquals(0, NotificationWidthPolicy.settingCode(false, 1, 400, 0));
        int shade = NotificationWidthPolicy.settingCode(true, 1, 400, 0);
        int lock = NotificationWidthPolicy.settingCode(true, 0, 300, 40);
        long signature = ((long) shade << 32) | (lock & 0xffffffffL);
        assertEquals(shade, NotificationWidthPolicy.selected(signature, 0, true));
        assertEquals(lock, NotificationWidthPolicy.selected(signature, 1, false));
        assertEquals(0, NotificationWidthPolicy.selected(signature, 0, false));
    }
    @Test public void modesAndMarginsHaveSafeLegacyDefaults() {
        assertEquals(NotificationWidthPolicy.FIXED, NotificationWidthPolicy.mode(99));
        assertEquals(14, NotificationWidthPolicy.normalizeMargin(Float.NaN), 0);
        assertEquals(14, NotificationWidthPolicy.normalizeMargin(Float.POSITIVE_INFINITY), 0);
        assertEquals(0, NotificationWidthPolicy.normalizeMargin(-20), 0);
        assertEquals(160, NotificationWidthPolicy.normalizeMargin(999), 0);
    }

}
