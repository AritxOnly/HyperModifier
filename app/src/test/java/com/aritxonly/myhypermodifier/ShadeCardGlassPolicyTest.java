package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShadeCardGlassPolicyTest {
    @Test public void customBaseIsLimitedToOneHundredWithoutClampingStockGlass() {
        assertArrayEquals(new int[]{100, 100}, ShadeCardGlassPolicy.effectiveGlassRadii(40, 500, true, 700, 100));
        assertArrayEquals(new int[]{100, 100}, ShadeCardGlassPolicy.effectiveGlassRadii(40, 500, true, 100, 100));
        assertArrayEquals(new int[]{200, 200}, ShadeCardGlassPolicy.effectiveGlassRadii(40, 500, true, 1000, 200));
        assertArrayEquals(new int[]{40, 500}, ShadeCardGlassPolicy.effectiveGlassRadii(40, 500, false, 100, 100));
        assertArrayEquals(new int[]{0, 0}, ShadeCardGlassPolicy.effectiveGlassRadii(40, 500, true, -1, 100));
        assertArrayEquals(new int[]{80, 1000}, ShadeCardGlassPolicy.effectiveGlassRadii(40, 500, false, 0, 200));
        assertEquals(500, ShadeCardGlassPolicy.scaleBackgroundRadius(700, 100));
    }
    @Test public void savedCustomRadiusIsNormalizedIntoTheEditableRange() {
        assertEquals(100f, ShadeCardGlassPolicy.normalize(700f, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS, 20f), 0f);
        assertEquals(0f, ShadeCardGlassPolicy.normalize(-1f, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS, 20f), 0f);
        assertEquals(20f, ShadeCardGlassPolicy.normalize(Float.NaN, ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS, 20f), 0f);
    }
    @Test public void glassRadiusMatchesTheProviderExcludedByTheCardRecipeHook() {
        String provider = "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$BlurProvider";
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{provider}));
        assertEquals(1, ShadeCardGlassPolicy.radiusCallKind(new String[]{provider}));
        assertEquals(1, ShadeCardGlassPolicy.radiusCallKind(new String[]{
                "com.android.systemui.shade.NotificationPanelViewController"}));
        assertEquals(2, ShadeCardGlassPolicy.radiusCallKind(new String[]{provider,
                "miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder"}));
        assertEquals(0, ShadeCardGlassPolicy.radiusCallKind(new String[]{
                "com.android.keyguard.blur.MiuiKeyguardBlurInteractor"}));
        assertEquals(0, ShadeCardGlassPolicy.radiusCallKind(new String[]{
                "com.milink.service.BlurUtils"}));
    }

    @Test public void absoluteGlassRadiusFollowsHyperChangerEvenForZeroIncomingRadius() {
        assertArrayEquals(new int[]{20, 20},
                ShadeCardGlassPolicy.effectiveGlassRadii(0, 0, true, 20, 100));
        assertArrayEquals(new int[]{0, 0},
                ShadeCardGlassPolicy.effectiveGlassRadii(0, 0, false, 20, 200));
    }
    @Test public void inheritedGlassBlurCanBeScaledWithoutANativeCardSetterCall() {
        assertArrayEquals(new int[]{50, 150},
                ShadeCardGlassPolicy.effectiveGlassRadii(100, 300, false, 20, 50));
        assertArrayEquals(new int[]{0, 0},
                ShadeCardGlassPolicy.effectiveGlassRadii(100, 300, false, 20, 0));
        assertArrayEquals(new int[]{10, 10},
                ShadeCardGlassPolicy.effectiveGlassRadii(100, 300, true, 20, 50));
        assertArrayEquals(new int[]{0, 0},
                ShadeCardGlassPolicy.effectiveGlassRadii(100, 300, true, 0, 200));
        assertArrayEquals(new int[]{80, 80},
                ShadeCardGlassPolicy.effectiveGlassRadii(100, 300, true, 40, 200));
    }

    @Test public void notificationBlurUtilityFramesAreNotLimitedToEffectApply() {
        assertEquals(1, ShadeCardGlassPolicy.callKind(new String[]{
                "com.android.systemui.statusbar.notification.utils.NotificationUtil"}));
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{
                "com.android.systemui.statusbar.notification.utils.NotificationUtil",
                "com.android.keyguard.KeyguardView"}));
    }
    @Test public void relativeBlurUsesNativeRadiusAndPreservesZero() {
        assertEquals(120, ShadeCardGlassPolicy.scaleBackgroundRadius(120, 100));
        assertEquals(60, ShadeCardGlassPolicy.scaleBackgroundRadius(120, 50));
        assertEquals(0, ShadeCardGlassPolicy.scaleBackgroundRadius(120, 0));
        assertEquals(0, ShadeCardGlassPolicy.scaleBackgroundRadius(0, 200));
        assertEquals(500, ShadeCardGlassPolicy.scaleBackgroundRadius(400, 200));
        assertEquals(20f, ShadeCardGlassPolicy.normalize(Float.NaN, 40f, 20f), 0f);
    }
    @Test public void baselineIsANoopForDifferentNativeCards() {
        float[] original = ShadeCardGlassPolicy.defaults();
        original[11] = .2f;
        original[14] = .6f;
        original[21] = 20f;
        assertArrayEquals(original, ShadeCardGlassPolicy.tune(original, ShadeCardGlassPolicy.defaults()), 0f);
    }

    @Test public void editsOnlyChangedSlotsAndNeverMutatesNativeArrays() {
        float[] original = ShadeCardGlassPolicy.defaults();
        float[] configured = ShadeCardGlassPolicy.defaults();
        configured[6] += .1f;
        configured[32] += .5f;
        float[] tuned = ShadeCardGlassPolicy.tune(original, configured);
        assertEquals(original[6] + .1f, tuned[6], .00001f);
        assertEquals(original[32] + .5f, tuned[32], .00001f);
        assertEquals(original[11], tuned[11], 0f);
        assertEquals(-.02f, original[6], 0f);
        assertNotSame(original, tuned);
    }

    @Test public void clearAndUnknownRecipesAreLeftUntouched() {
        assertNull(ShadeCardGlassPolicy.tune(new float[42], ShadeCardGlassPolicy.defaults()));
        assertNull(ShadeCardGlassPolicy.tune(new float[43], ShadeCardGlassPolicy.defaults()));
        float[] invalid = ShadeCardGlassPolicy.defaults();
        invalid[2] = Float.NaN;
        assertNull(ShadeCardGlassPolicy.tune(invalid, ShadeCardGlassPolicy.defaults()));
    }

    @Test public void unsignedSlotsDoNotGoNegative() {
        float[] original = ShadeCardGlassPolicy.defaults();
        original[14] = .01f;
        float[] configured = ShadeCardGlassPolicy.defaults();
        configured[14] = 0f;
        assertEquals(0f, ShadeCardGlassPolicy.tune(original, configured)[14], 0f);
    }

    @Test public void scopesIncludePluginCardsButExcludeHeadsUpBackgroundsAndKeyguard() {
        String effect = "com.android.systemui.statusbar.notification.style.vieweffect.NotificationRowGlassEffect";
        String control = "miui.systemui.controlcenter.panel.main.qs.QSItemViewHolder";
        assertEquals(1, ShadeCardGlassPolicy.callKind(new String[]{effect}));
        assertEquals(2, ShadeCardGlassPolicy.callKind(new String[]{control, "miui.systemui.util.MiBackgroundStyle"}));
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{effect, "HeadsUpNotificationGlassEffect"}));
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{control, "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl"}));
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{effect, "com.android.keyguard.KeyguardView"}));
        assertEquals(2, ShadeCardGlassPolicy.callKind(new String[]{control, "com.android.keyguard.KeyguardView"}));
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{"com.milink.service.BlurUtils"}));
    }

    @Test public void asynchronousMaterialUpdatesRequireControlCenterOwnership() {
        String[] animation = {"miui.systemui.util.MiBackgroundStyle$animateBionics$1"};
        assertEquals(0, ShadeCardGlassPolicy.callKind(animation, false));
        assertEquals(2, ShadeCardGlassPolicy.callKind(animation, true));
        assertEquals(0, ShadeCardGlassPolicy.callKind(new String[]{animation[0],
                "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl"}, true));
    }
}
