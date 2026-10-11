package com.aritxonly.myhypermodifier;

import android.graphics.RectF;
import android.view.View;
import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverAdapterTest {
    // Helpers are deliberately named like R8 output; the adapter only touches retained Main.
    static class q { static boolean a = true; }
    static class RetainedMain {
        static View sContainer;
        static Float sHoldY = 700f;
        static int sFpAvoid = 2;
        static boolean sHideFp = true;
        static float glyphUnit(RectF ignored) { return 300f; }
        static View[] clockRoots() { return null; }
        static View clockTarget(View root) { return root; }
        static boolean clockHeld() { return q.a; }
        static boolean keyguardLocked() { return true; }
        static boolean onKeyguardNow() { return true; }
        static boolean controlCenterShown() { return false; }
        static float cardProgress() { return 1f; }
        static boolean screenOn() { return true; }
        static void applyHideFp() {}
        static boolean fodForKeyguard(Object view) { return true; }
        static Object fodViewOf(Object animation) { return animation; }
        static boolean isFodRing(int resource) { return true; }
    }
    static class ChangedMain { static int glyphUnit(RectF ignored) { return 300; } }
    @Test public void renamedInternalClassesDoNotAffectAdapter() throws Exception {
        HyperMusicCoverAdapter adapter = new HyperMusicCoverAdapter(RetainedMain.class);
        assertTrue(adapter.bool(adapter.clockHeld));
        q.a = false;
        assertFalse(adapter.bool(adapter.clockHeld));
        q.a = true;
        assertEquals(2, adapter.fpAvoid.getInt(null));
        assertTrue(adapter.hideFp.getBoolean(null));
        assertEquals(Float.valueOf(700f), adapter.holdY.get(null));
    }
    @Test public void missingOrChangedRetainedInterfaceIsRejected() {
        assertThrows(NoSuchMethodException.class, () -> new HyperMusicCoverAdapter(String.class));
        assertThrows(NoSuchMethodException.class, () -> new HyperMusicCoverAdapter(ChangedMain.class));
    }
}
