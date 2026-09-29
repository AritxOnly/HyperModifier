package com.aritxonly.myhypermodifier;

import org.junit.Test;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class GestureHandlePolicyTest {
    @Test public void unspecifiedAppsFollowBothSystemDefaults() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.foreground("app", "app/Main", 1, 0);
        assertTrue(policy.hidden(10_000, true));
        assertFalse(policy.hidden(10_000, false));
    }

    @Test public void explicitModesOverrideSystemDefault() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.foreground("app", "app/Main", 1, 0);
        policy.setRules(Map.of("app", "show"), 0);
        assertFalse(policy.hidden(10_000, true));
        policy.setRules(Map.of("app", "hide"), 0);
        assertTrue(policy.hidden(0, false));
    }

    @Test public void repeatedTaskEventsDoNotExtendImmersiveDeadline() {
        GestureHandlePolicy policy = immersive();
        policy.foreground("app", "app/Main", 1, 2_000);
        policy.setRules(Map.of("app", "immersive", "other", "hide"), 2_000);
        assertFalse(policy.hidden(2_999, true));
        assertEquals(1, policy.remaining(2_999));
        assertTrue(policy.hidden(3_000, false));
        assertEquals(0, policy.remaining(3_000));
    }

    @Test public void activityAndTaskTransitionsEachRevealHandleAgain() {
        GestureHandlePolicy policy = immersive();
        policy.foreground("app", "app/Detail", 1, 9_000);
        assertFalse(policy.hidden(9_000, true));
        assertTrue(policy.hidden(12_000, false));
        policy.foreground("app", "app/Detail", 2, 20_000);
        assertFalse(policy.hidden(20_000, true));
        assertTrue(policy.hidden(23_000, false));
    }

    @Test public void switchingToUnconfiguredAppAndRemovingRuleRestoreSystem() {
        GestureHandlePolicy policy = immersive();
        policy.foreground("other", "other/Main", 2, 8_000);
        assertFalse(policy.hidden(9_000, false));
        assertTrue(policy.hidden(9_000, true));
        policy.foreground("app", "app/Main", 1, 10_000);
        policy.setRules(Collections.emptyMap(), 10_000);
        assertTrue(policy.hidden(10_000, true));
        assertEquals(0, policy.remaining(10_000));
    }

    @Test public void selectingImmersiveAndUnlockingStartFreshInterval() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.foreground("app", "app/Main", 1, 0);
        policy.setRules(Map.of("app", "immersive"), 30_000);
        assertFalse(policy.hidden(32_999, true));
        assertTrue(policy.hidden(33_000, false));
        policy.reveal(40_000);
        assertFalse(policy.hidden(42_999, true));
        assertTrue(policy.hidden(43_000, false));
    }

    @Test public void modulePresetClassifiesSystemScopeAndAuthorAppsAsHidden() {
        assertEquals("hide", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "android.settings", true, Set.of()));
        assertEquals("hide", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, ModuleScopePackage.AMAP, false, Set.of()));
        assertEquals("hide", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "extra.target", false, Set.of("extra.target")));
        assertEquals("hide", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "com.aritxonly.newapp", false, Set.of()));
        assertEquals("immersive", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "com.aritxonlyfake.app", false, Set.of()));
        assertEquals("immersive", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "newly.installed.app", false, Set.of()));
        assertEquals("show", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "com.tencent.mobileqq", false, Set.of()));
        assertEquals("hide", GestureHandleDefaults.mode(GestureHandlePresets.MODULE, "com.openai.chatgpt", false, Set.of()));
    }

    @Test public void globalPresetsApplyToEveryApp() {
        for (String preset : new String[] {GestureHandlePresets.SHOW, GestureHandlePresets.HIDE,
                GestureHandlePresets.IMMERSIVE}) {
            for (String app : new String[] {"android.settings", ModuleScopePackage.AMAP, "other.app"}) {
                assertEquals(preset, GestureHandleDefaults.mode(preset, app, true, Set.of()));
            }
        }
        assertEquals(GestureHandlePresets.SHOW, GestureHandlePresets.fromStored(null, false));
        assertEquals(GestureHandlePresets.SHOW, GestureHandlePresets.fromStored("system", true));
    }

    @Test public void modulePresetTimerAndPerAppOverridesUseEffectiveMode() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.configure(Map.of(), GestureHandlePresets.MODULE, Set.of(), 0);
        policy.foreground("new.app", "new.app/Main", 1, false, 0);
        assertFalse(policy.hidden(2_999, true));
        assertTrue(policy.hidden(3_000, false));
        policy.setRules(Map.of("new.app", "show"), 8_000);
        assertFalse(policy.hidden(8_000, true));
        policy.setRules(Map.of("new.app", "system"), 9_000);
        assertFalse(policy.hidden(9_000, false));
        assertTrue(policy.hidden(9_000, true));
        assertEquals(0, policy.remaining(9_000));
        policy.setRules(Map.of(), 10_000);
        assertFalse(policy.hidden(12_999, true));
        assertTrue(policy.hidden(13_000, false));
    }

    @Test public void switchingPresetsChangesAllAppsAndRestartsImmersiveTimer() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.configure(Map.of(), GestureHandlePresets.MODULE, Set.of(), 0);
        policy.foreground("app", "app/Main", 1, false, 0);
        assertTrue(policy.hidden(3_000, false));
        policy.configure(Map.of(), GestureHandlePresets.SHOW, Set.of(), 8_000);
        assertFalse(policy.hidden(8_000, true));
        policy.configure(Map.of(), GestureHandlePresets.HIDE, Set.of(), 8_500);
        assertTrue(policy.hidden(8_500, false));
        policy.configure(Map.of(), GestureHandlePresets.MODULE, Set.of(), 9_000);
        assertFalse(policy.hidden(11_999, true));
        assertTrue(policy.hidden(12_000, false));
    }

    @Test public void allImmersiveStartsNewTimerForEachActivityAndPerAppRuleWins() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.configure(Map.of(), GestureHandlePresets.IMMERSIVE, Set.of(), 0);
        policy.foreground("app", "app/Main", 1, false, 1_000);
        assertFalse(policy.hidden(3_999, true));
        assertTrue(policy.hidden(4_000, false));
        policy.foreground("app", "app/Detail", 1, false, 5_000);
        assertFalse(policy.hidden(5_000, true));
        policy.configure(Map.of("app", "show"), GestureHandlePresets.IMMERSIVE, Set.of(), 6_000);
        assertFalse(policy.hidden(20_000, true));
    }

    @Test public void touchingHiddenHandleRevealsForExactlyThreeSeconds() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.foreground("app", "app/Main", 1, 0);
        policy.setRules(Map.of("app", "hide"), 0);
        assertTrue(policy.hidden(5_000, false));
        policy.revealOnTouch(5_000);
        assertFalse(policy.hidden(7_999, false));
        assertEquals(1L, policy.remaining(7_999));
        assertTrue(policy.hidden(8_000, false));
        assertEquals(0L, policy.remaining(8_000));
    }

    @Test public void touchFromImmersiveAppOverridesHiddenDesktopUntilReleaseDeadline() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.setRules(Map.of("app", "immersive", "desktop", "hide"), 0);
        policy.foreground("app", "app/Main", 1, 0);
        policy.touchDown(5_000);
        policy.foreground("desktop", "desktop/Home", 2, 5_500);
        policy.configure(Map.of("app", "immersive", "desktop", "hide"), GestureHandlePresets.SHOW, Set.of(), 5_600);
        assertFalse(policy.hidden(6_000, false));
        policy.touchUp(6_500);
        assertFalse(policy.hidden(9_499, false));
        assertEquals(1L, policy.remaining(9_499));
        assertTrue(policy.hidden(9_500, false));
    }

    @Test public void longPressStaysVisibleAndStartsThreeSecondCountdownOnRelease() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.foreground("app", "app/Main", 1, 0);
        policy.setRules(Map.of("app", "hide"), 0);
        policy.touchDown(1_000);
        assertFalse(policy.hidden(10_000, false));
        policy.touchUp(10_000);
        assertFalse(policy.hidden(12_999, false));
        assertTrue(policy.hidden(13_000, false));
    }

    @Test public void swipeAndTouchDeadlinesAreIndependent() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.foreground("app", "app/Main", 1, 0);
        policy.setRules(Map.of("app", "hide"), 0);
        policy.revealOnTouch(1_000);
        policy.revealOnSwipe(2_000);
        policy.clearTouchReveal();
        assertFalse(policy.hidden(4_999, false));
        policy.clearSwipeReveal();
        assertTrue(policy.hidden(4_999, false));
    }

    @Test public void longSwipeKeepsHintVisibleAcrossAppChange() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.setRules(Map.of("app", "immersive", "desktop", "hide"), 0);
        policy.foreground("app", "app/Main", 1, 0);
        policy.swipeDown(5_000);
        policy.foreground("desktop", "desktop/Home", 2, 6_000);
        assertFalse(policy.hidden(10_000, false));
        policy.swipeUp(10_000);
        assertFalse(policy.hidden(12_999, false));
        assertTrue(policy.hidden(13_000, false));
    }

    @Test public void scopeRefreshAndSystemMetadataAffectCurrentAppWithoutActivityChange() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.configure(Map.of(), GestureHandlePresets.MODULE, Set.of(), 0);
        policy.foreground("app", "app/Main", 1, false, 0);
        assertFalse(policy.hidden(0, true));
        policy.configure(Map.of(), GestureHandlePresets.MODULE, Set.of("app"), 1_000);
        assertTrue(policy.hidden(1_000, false));
        policy.configure(Map.of(), GestureHandlePresets.MODULE, Set.of(), 2_000);
        assertFalse(policy.hidden(2_000, true));
        policy.foreground("app", "app/Main", 1, true, 3_000);
        assertTrue(policy.hidden(3_000, false));
        policy.foreground("app", "app/Main", 1, false, 4_000);
        assertFalse(policy.hidden(6_999, true));
        assertTrue(policy.hidden(7_000, false));
        policy.setRules(Map.of("app", "show"), 8_000);
        assertFalse(policy.hidden(8_000, true));
    }

    private static GestureHandlePolicy immersive() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.setRules(Map.of("app", "immersive"), 0);
        policy.foreground("app", "app/Main", 1, 0);
        return policy;
    }
}
