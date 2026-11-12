package com.aritxonly.myhypermodifier;

import static org.junit.Assert.*;
import org.robolectric.shadows.ShadowLooper;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Looper;
import com.aritxonly.myhypermodifier.coverfixtures.CoverFrameworkFixtures;
import java.lang.ref.WeakReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class CoverAdaptationTest {
    CoverWindowAccess access;
    CoverAdaptationHooks.Controller session;
    Object app;

    @Before public void setup() throws Exception {
        CoverFrameworkFixtures.afterResolve = null;
        access = new CoverWindowAccess(getClass().getClassLoader(), "com.aritxonly.myhypermodifier.coverfixtures");
        Context context = RuntimeEnvironment.getApplication();
        session = new CoverAdaptationHooks.Controller(null, access,
                context.getSharedPreferences("cover_test", 0), getClass().getClassLoader());
        session.context = context;
        session.wm = CoverFrameworkFixtures.wm(context);
        session.lock = access.globalLock.get(session.wm);
        session.folded = session.foldKnown = true;
        app = CoverFrameworkFixtures.activity("test.reader", 1000, 1400);
        session.focused = new WeakReference<>(app);
        CoverFrameworkFixtures.afterResolve = session::resolve;
    }

    @After public void teardown() throws Exception {
        if (session != null) session.clearLocked();
        CoverFrameworkFixtures.afterResolve = null;
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test public void fullHeightPortraitRatioLeavesEnoughSpaceForAccessibleRail() {
        assertEquals(787, CoverAdaptationPolicy.appWidth(1400));
        assertTrue(CoverAdaptationPolicy.fits(1000, 1400, 320));
        assertTrue(CoverAdaptationPolicy.fits(1168, 1712, 420));
        assertFalse(CoverAdaptationPolicy.fits(1080, 2400, 420));
        assertFalse(CoverAdaptationPolicy.fits(1400, 1000, 320));
        assertFalse(CoverAdaptationPolicy.fits(1000, 1000, 320));
        assertFalse(CoverAdaptationPolicy.fits(1000, 1700, 420));
        assertFalse(CoverAdaptationPolicy.fits(0, 1400, 320));
        assertFalse(CoverAdaptationPolicy.fits(1000, 1400, Integer.MAX_VALUE));
    }

    @Test public void overrideAndExitPreserveRequestedAndParentConfigurations() throws Exception {
        Configuration requested = new Configuration(CoverFrameworkFixtures.requested(app));
        Configuration parent = new Configuration(CoverFrameworkFixtures.parent(app));
        session.applyLocked(app);
        Configuration result = (Configuration) access.getResolved.invoke(app);
        assertEquals(new Rect(0, 0, 787, 1400), access.bounds(result));
        assertEquals(393, result.screenWidthDp);
        assertEquals(700, result.screenHeightDp);
        assertEquals(requested, CoverFrameworkFixtures.requested(app));
        assertEquals(parent, CoverFrameworkFixtures.parent(app));
        assertTrue(session.hidesBar(CoverFrameworkFixtures.window(app)));
        session.clearLocked();
        assertNull(session.targetTask);
        assertNull(session.barRecord);
        assertEquals(requested, access.getResolved.invoke(app));
        assertEquals(parent, CoverFrameworkFixtures.parent(app));
    }

    @Test public void swapUsesOppositeScreenEdgeAndStillRestoresOriginalMaxBounds() throws Exception {
        Configuration nativeConfig = new Configuration((Configuration) access.getResolved.invoke(app));
        session.applyLocked(app);
        int before = CoverFrameworkFixtures.history(app).size();
        session.left = true;
        session.applyLocked(app);
        assertEquals(before + 1, CoverFrameworkFixtures.history(app).size());
        assertEquals(new Rect(213, 0, 1000, 1400), access.bounds((Configuration) access.getResolved.invoke(app)));
        session.clearLocked();
        assertEquals(nativeConfig, access.getResolved.invoke(app));
    }

    @Test public void otherTasksAndSystemWindowsKeepTheirNativeBoundsAndVisibility() throws Exception {
        Object other = CoverFrameworkFixtures.activity("test.other", 1000, 1400);
        Configuration nativeConfig = new Configuration((Configuration) access.getResolved.invoke(other));
        session.applyLocked(app);
        session.resolve(other, CoverFrameworkFixtures.parent(other));
        assertEquals(nativeConfig, access.getResolved.invoke(other));
        assertFalse(session.hidesBar(CoverFrameworkFixtures.window(other)));
        assertFalse(session.hidesBar(CoverFrameworkFixtures.window(null)));
    }

    @Test public void shadeSuspendsSidebarAndReleasesStatusOverrideWithoutChangingRequestedState() throws Exception {
        session.applyLocked(app);
        session.focus(CoverFrameworkFixtures.policy(), CoverFrameworkFixtures.window(null));
        assertFalse(session.hidesBar(CoverFrameworkFixtures.window(app)));
        ShadowLooper.shadowMainLooper().idle();
        assertFalse(session.visible);
        assertNotNull(session.targetTask);
        session.focus(CoverFrameworkFixtures.policy(), CoverFrameworkFixtures.window(app));
        ShadowLooper.shadowMainLooper().idle();
        assertTrue(session.visible);
        assertTrue(session.hidesBar(CoverFrameworkFixtures.window(app)));
    }

    @Test public void returningHomeEndsSessionAndRestoresApplicationConfiguration() throws Exception {
        Configuration nativeConfig = new Configuration((Configuration) access.getResolved.invoke(app));
        session.applyLocked(app);
        Object home = CoverFrameworkFixtures.activity("com.miui.home", 1000, 1400);
        session.focus(CoverFrameworkFixtures.policy(), CoverFrameworkFixtures.window(home));
        ShadowLooper.shadowMainLooper().idle();
        assertNull(session.targetTask);
        assertEquals(nativeConfig, access.getResolved.invoke(app));
    }

    @Test public void legacySplitPipAndOtherDisplaysAreNotAdapted() throws Exception {
        CoverFrameworkFixtures.legacy(app);
        assertFalse(access.eligible(app));
        Object split = CoverFrameworkFixtures.activity("test.split", 1000, 1400);
        CoverFrameworkFixtures.mode(split, 6);
        assertFalse(access.eligible(split));
        CoverFrameworkFixtures.mode(split, 2);
        assertFalse(access.eligible(split));
        CoverFrameworkFixtures.mode(split, 1);
        CoverFrameworkFixtures.display(split, 2);
        assertFalse(access.eligible(split));
        Object video = CoverFrameworkFixtures.activity("test.video", 1000, 1400);
        CoverFrameworkFixtures.landscape(video);
        assertFalse(access.eligible(video));
    }

    @Test public void switchingTasksRestoresPreviousApplicationBeforeAdaptingNext() throws Exception {
        Configuration original = new Configuration((Configuration) access.getResolved.invoke(app));
        session.applyLocked(app);
        Object next = CoverFrameworkFixtures.activity("test.next", 1000, 1400);
        session.focus(CoverFrameworkFixtures.policy(), CoverFrameworkFixtures.window(next));
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(original, access.getResolved.invoke(app));
        assertEquals(new Rect(0, 0, 787, 1400), access.bounds((Configuration) access.getResolved.invoke(next)));
    }

    @Test public void unfoldingRestoresWindowAndEndsSessionImmediately() throws Exception {
        Configuration original = new Configuration((Configuration) access.getResolved.invoke(app));
        session.applyLocked(app);
        session.onFoldChanged(false);
        assertNull(session.targetTask);
        assertFalse(session.hidesBar(CoverFrameworkFixtures.window(app)));
        assertEquals(original, access.getResolved.invoke(app));
    }

    @Test public void systemUiBinderDeathRestoresLayoutBeforeAnotherSessionCanStart() throws Exception {
        android.os.Bundle extras = new android.os.Bundle();
        extras.putBinder("lease", new android.os.Binder());
        session.command(new android.content.Intent().putExtra("command", CoverAdaptationContract.ATTACH).putExtras(extras));
        Configuration original = new Configuration((Configuration) access.getResolved.invoke(app));
        session.applyLocked(app);
        assertNotNull(session.targetTask);
        session.leaseDeath.binderDied();
        ShadowLooper.shadowMainLooper().idle();
        assertNull(session.lease);
        assertNull(session.targetTask);
        assertEquals(original, access.getResolved.invoke(app));
    }

    @Test public void disabledModuleEndsSessionOnNextQueuedUpdate() throws Exception {
        Configuration original = new Configuration((Configuration) access.getResolved.invoke(app));
        session.applyLocked(app);
        session.enabled = false;
        session.queueUpdate();
        ShadowLooper.shadowMainLooper().idle();
        assertNull(session.targetTask);
        assertEquals(original, access.getResolved.invoke(app));
    }

    @Test public void sameTaskNavigationKeepsUnderlyingLayoutWithoutRedundantConfigurationReplay() throws Exception {
        session.applyLocked(app);
        int before = CoverFrameworkFixtures.history(app).size();
        Object sibling = CoverFrameworkFixtures.sibling(app);
        int siblingBefore = CoverFrameworkFixtures.history(sibling).size();
        session.focus(CoverFrameworkFixtures.policy(), CoverFrameworkFixtures.window(sibling));
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(before, CoverFrameworkFixtures.history(app).size());
        assertEquals(siblingBefore, CoverFrameworkFixtures.history(sibling).size());
        assertEquals(new Rect(0, 0, 787, 1400), access.bounds((Configuration) access.getResolved.invoke(app)));
        session.left = true;
        session.applyLocked(sibling);
        assertEquals(new Rect(213, 0, 1000, 1400), access.bounds((Configuration) access.getResolved.invoke(app)));
        assertEquals(new Rect(213, 0, 1000, 1400), access.bounds((Configuration) access.getResolved.invoke(sibling)));
    }

    @Test public void moduleAndSystemUiCommandsUseSeparateExplicitChannels() {
        Context context = RuntimeEnvironment.getApplication();
        org.robolectric.shadows.ShadowApplication shadow = org.robolectric.shadow.api.Shadow.extract(context);
        CoverAdaptationContract.send(context, CoverAdaptationContract.TOGGLE);
        var broadcasts = shadow.getBroadcastIntents();
        android.content.Intent moduleCommand = broadcasts.get(broadcasts.size() - 1);
        assertEquals(CoverAdaptationContract.COMMAND, moduleCommand.getAction());
        assertEquals("android", moduleCommand.getPackage());
        CoverAdaptationContract.sendSystem(context, CoverAdaptationContract.SWAP);
        broadcasts = shadow.getBroadcastIntents();
        android.content.Intent systemCommand = broadcasts.get(broadcasts.size() - 1);
        assertEquals(CoverAdaptationContract.SYSTEM_COMMAND, systemCommand.getAction());
        assertEquals("android", systemCommand.getPackage());
        assertNotEquals(moduleCommand.getAction(), systemCommand.getAction());
    }
}
