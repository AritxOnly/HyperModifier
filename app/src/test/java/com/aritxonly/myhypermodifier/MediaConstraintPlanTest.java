package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class MediaConstraintPlanTest {
    public static final class TestResources extends Resources {
        final Map<String, Integer> ids = new HashMap<>();
        int lookups;
        TestResources(Resources source) { super(source.getAssets(), source.getDisplayMetrics(), source.getConfiguration()); }
        @Override public int getIdentifier(String name, String type, String pkg) {
            lookups++;
            if (!"id".equals(type)) return 0;
            return ids.computeIfAbsent(name, key -> ids.size() + 1);
        }
        int id(String name) { return ids.get(name); }
    }
    public static final class Layout {
        public int mWidth, mHeight, startToStart = 999, startToEnd = 999, endToStart = 999,
                endToEnd = 999, leftToLeft = 999, leftToRight = 999, rightToLeft = 999,
                rightToRight = 999, topToTop = 999, topToBottom = 999, bottomToTop = 999,
                bottomToBottom = 999, baselineToBaseline = 999, startMargin, endMargin,
                leftMargin, rightMargin, topMargin, bottomMargin, goneBottomMargin,
                horizontalChainStyle, verticalChainStyle;
        public float horizontalBias, verticalBias;
    }
    public static final class PropertySet { public int visibility = 0; }
    public static final class Constraint {
        public Layout layout = new Layout();
        public PropertySet propertySet = new PropertySet();
    }
    public static class ConstraintSet {
        final Map<Integer, Constraint> items = new HashMap<>();
        public Constraint getConstraint(int id) { return items.computeIfAbsent(id, key -> new Constraint()); }
        Constraint item(TestResources resources, String name) { return getConstraint(resources.id(name)); }
    }
    private Context context(TestResources resources) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Resources getResources() { return resources; }
        };
    }

    @Test public void compactClearsStockAnchorsAndUses80dpWithHiddenProgress() {
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = context(resources);
        ConstraintSet set = new ConstraintSet();
        ModuleSettings.disableMediaIslandHooks = false;
        String previous = ModuleSettings.mediaLayoutPreset;
        try {
            ModuleSettings.mediaLayoutPreset = "compact";
            ModuleSettings.compactMediaXml = ModuleSettings.compactMediaIslandXml = "";
            ModuleSettings.compactMediaHeight = 80;
            MediaConstraintCustomizer.patchMediaConstraintSet(context, set, false);
            assertEquals(80, set.item(resources, "media_bg").layout.mHeight / resources.getDisplayMetrics().density, .01);
            assertEquals(62, set.item(resources, "album_art").layout.mWidth / resources.getDisplayMetrics().density, .01);
            assertEquals(-1, set.item(resources, "album_art").layout.leftToLeft);
            assertEquals(-1, set.item(resources, "header_title").layout.topToBottom);
            assertEquals(8, set.item(resources, "media_progress_bar").propertySet.visibility);
            assertEquals(0, set.item(resources, "action2").propertySet.visibility);
            int lookups = resources.lookups;
            assertTrue(MediaConstraintPlan.apply(context, new ConstraintSet(), MediaPresetDefaults.COMPACT_XML, false));
            assertEquals(lookups, resources.lookups);
        } finally { ModuleSettings.mediaLayoutPreset = previous; }
    }

    @Test public void largeCoverResolvesIslandBackgroundAndNestedImageAndRestoresProgress() {
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = context(resources);
        ConstraintSet set = new ConstraintSet();
        assertTrue(MediaConstraintPlan.apply(context, set, MediaPresetDefaults.COMPACT_XML, true));
        assertEquals(8, set.item(resources, "media_progress_bar").propertySet.visibility);
        assertTrue(MediaConstraintPlan.apply(context, set, MediaPresetDefaults.LARGE_COVER_ISLAND_XML, true));
        assertNotNull(resources.ids.get("media_bg_view"));
        assertNull(resources.ids.get("media_bg"));
        assertEquals(72, set.item(resources, "album_art_image").layout.mWidth / resources.getDisplayMetrics().density, .01);
        assertEquals(-1, set.item(resources, "header_title").layout.topToBottom);
        assertEquals(resources.id("media_bg_view"), set.item(resources, "header_title").layout.topToTop);
        assertEquals(0, set.item(resources, "media_progress_bar").propertySet.visibility);
        assertEquals(0, set.item(resources, "media_elapsed_time").propertySet.visibility);
        assertEquals(0, set.item(resources, "action4").propertySet.visibility);
        assertTrue(MediaConstraintPlan.apply(context, set, MediaPresetDefaults.COMPACT_XML, true));
        assertEquals(8, set.item(resources, "media_progress_bar").propertySet.visibility);
        assertEquals(62, set.item(resources, "album_art_image").layout.mWidth / resources.getDisplayMetrics().density, .01);
    }

    @Test public void malformedXmlNeverPartiallyMutatesAConstraintSet() {
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        ConstraintSet set = new ConstraintSet();
        assertFalse(MediaConstraintPlan.apply(context(resources), set,
                "<ConstraintSet xmlns:android=\"http://schemas.android.com/apk/res/android\"><Constraint android:id=\"@id/album_art\" android:layout_width=\"99dp\"/><bad", false));
        assertTrue(set.items.isEmpty());
    }
    @Test public void configurationChangeRebuildsResolvedDimensions() {
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = context(resources);
        assertTrue(MediaConstraintPlan.apply(context, new ConstraintSet(), MediaPresetDefaults.LARGE_COVER_XML, false));
        int lookups = resources.lookups;
        resources.getConfiguration().fontScale += .1f;
        ConstraintSet refreshed = new ConstraintSet();
        assertTrue(MediaConstraintPlan.apply(context, refreshed, MediaPresetDefaults.LARGE_COVER_XML, false));
        assertTrue(resources.lookups > lookups);
        assertEquals(108, refreshed.item(resources, "album_art").layout.mWidth / resources.getDisplayMetrics().density, .01);
    }

    @Test public void unrelatedSettingsDoNotInvalidateMediaRefreshState() throws Exception {
        var method = RuntimeRefreshRegistry.class.getDeclaredMethod("mediaSettingsChanged");
        method.setAccessible(true);
        var state = RuntimeRefreshRegistry.class.getDeclaredField("lastMediaSettings");
        state.setAccessible(true);
        Object previous = state.get(null);
        String xml = ModuleSettings.largeCoverMediaXml;
        float radius = ModuleSettings.controlCenterRadius;
        try {
            state.set(null, null);
            assertEquals(true, method.invoke(null));
            assertEquals(false, method.invoke(null));
            ModuleSettings.controlCenterRadius = radius + 1;
            assertEquals(false, method.invoke(null));
            ModuleSettings.largeCoverMediaXml = "<changed />";
            assertEquals(true, method.invoke(null));
            assertEquals(false, method.invoke(null));
        } finally {
            ModuleSettings.largeCoverMediaXml = xml;
            ModuleSettings.controlCenterRadius = radius;
            state.set(null, previous);
        }
    }

    public static final class RefreshSet extends ConstraintSet {
        int loads, applies, loadedXml;
        public void load(Context context, int xml) {
            loads++;
            loadedXml = xml;
            items.clear();
            TestResources resources = (TestResources) context.getResources();
            getConstraint(resources.getIdentifier("media_progress_bar", "id", context.getPackageName()));
            getConstraint(resources.getIdentifier("album_art_image", "id", context.getPackageName())).layout.mWidth = 53;
            MediaConstraintCustomizer.patchMediaConstraintSet(context, this, false);
        }
        public void applyTo(android.view.View target) { applies++; }
    }

    @Test public void queuedRefreshesMergeAndSwitchingToSystemReloadsStockConstraints() throws Exception {
        var activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().visible();
        android.view.View target = new android.view.View(activity.get());
        activity.get().setContentView(target);
        assertTrue(target.isAttachedToWindow());
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = context(resources);
        RefreshSet set = new RefreshSet();
        String previous = ModuleSettings.mediaLayoutPreset;
        boolean disabled = ModuleSettings.disableMediaIslandHooks;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = "compact";
            set.load(context, 0x7f180016);
            assertEquals(8, set.item(resources, "media_progress_bar").propertySet.visibility);
            var pending = new RuntimeRefreshRegistry.PendingMediaConstraint(context, 0x7f180016);
            pending.addTarget(target, RefreshSet.class.getMethod("applyTo", android.view.View.class));
            pending.reapply(set);
            pending.reapply(set);
            // A pending task reads the newest settings, including a switch back to stock.
            ModuleSettings.mediaLayoutPreset = "system";
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(2, set.loads);
            assertEquals(1, set.applies);
            assertEquals(0x7f180016, set.loadedXml);
            assertEquals(0, set.item(resources, "media_progress_bar").propertySet.visibility);
            assertEquals(53, set.item(resources, "album_art_image").layout.mWidth);
        } finally {
            ModuleSettings.mediaLayoutPreset = previous;
            ModuleSettings.disableMediaIslandHooks = disabled;
            activity.pause().stop().destroy();
        }
    }

    @Test public void compactButtonPaddingAndScaleRestoreWhenLeavingThePreset() {
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = context(resources);
        android.widget.FrameLayout root = new android.widget.FrameLayout(context);
        android.widget.ImageView button = new android.widget.ImageView(context);
        button.setId(resources.getIdentifier("action1", "id", context.getPackageName()));
        button.setPadding(10, 5, 10, 5);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        root.addView(button);
        String previous = ModuleSettings.mediaLayoutPreset;
        boolean disabled = ModuleSettings.disableMediaIslandHooks;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = "compact";
            MediaPresetViewProperties.apply(root);
            assertEquals(0, button.getPaddingLeft());
            assertEquals(android.widget.ImageView.ScaleType.CENTER_INSIDE, button.getScaleType());
            ModuleSettings.mediaLayoutPreset = "large_cover";
            MediaPresetViewProperties.apply(root);
            ModuleSettings.mediaLayoutPreset = "system";
            MediaPresetViewProperties.apply(root);
            assertEquals(10, button.getPaddingLeft());
            assertEquals(5, button.getPaddingTop());
            assertEquals(android.widget.ImageView.ScaleType.FIT_CENTER, button.getScaleType());
            ModuleSettings.mediaLayoutPreset = "compact";
            MediaPresetViewProperties.apply(root);
            ModuleSettings.disableMediaIslandHooks = true;
            MediaPresetViewProperties.apply(root);
            assertEquals(10, button.getPaddingLeft());
        } finally {
            ModuleSettings.mediaLayoutPreset = previous;
            ModuleSettings.disableMediaIslandHooks = disabled;
        }
    }

    @Test public void normalOnlyEditsDoNotInvalidateAnIndependentIsland() throws Exception {
        var changed = RuntimeRefreshRegistry.class.getDeclaredMethod("mediaSettingsChanged"); changed.setAccessible(true);
        var normal = RuntimeRefreshRegistry.class.getDeclaredField("normalMediaChanged"); normal.setAccessible(true);
        var island = RuntimeRefreshRegistry.class.getDeclaredField("islandMediaChanged"); island.setAccessible(true);
        String oldNormal = ModuleSettings.mediaLayoutPreset, oldIsland = ModuleSettings.mediaIslandLayoutPreset;
        int oldHeight = ModuleSettings.largeCoverMediaHeight, oldIslandHeight = ModuleSettings.largeCoverMediaIslandHeight;
        try {
            ModuleSettings.mediaLayoutPreset = "large_cover"; ModuleSettings.mediaIslandLayoutPreset = "standard";
            ModuleSettings.largeCoverMediaIslandHeight = 136;
            changed.invoke(null); changed.invoke(null);
            ModuleSettings.largeCoverMediaHeight = 144;
            assertEquals(true, changed.invoke(null));
            assertEquals(true, normal.get(null)); assertEquals(false, island.get(null));
            ModuleSettings.mediaIslandLayoutPreset = "compact";
            assertEquals(true, changed.invoke(null));
            assertEquals(false, normal.get(null)); assertEquals(true, island.get(null));
        } finally {
            ModuleSettings.mediaLayoutPreset = oldNormal; ModuleSettings.mediaIslandLayoutPreset = oldIsland;
            ModuleSettings.largeCoverMediaHeight = oldHeight; ModuleSettings.largeCoverMediaIslandHeight = oldIslandHeight;
        }
    }
    @Test public void islandStandardLayoutDoesNotDependOnTheNormalMediaSwitch() {
        TestResources resources = new TestResources(RuntimeEnvironment.getApplication().getResources());
        ConstraintSet set = new ConstraintSet();
        String oldNormal = ModuleSettings.mediaLayoutPreset, oldIsland = ModuleSettings.mediaIslandLayoutPreset;
        boolean oldMedia = ModuleSettings.mediaEnabled, oldEnabled = ModuleSettings.islandEnabled;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = "system"; ModuleSettings.mediaIslandLayoutPreset = "standard";
            ModuleSettings.mediaEnabled = false; ModuleSettings.islandEnabled = true;
            ModuleSettings.standardMediaIslandHeight = 158;
            MediaConstraintCustomizer.patchMediaConstraintSet(context(resources), set, true);
            assertEquals(resources.id("action4"), set.item(resources, "header_title").layout.endToStart);
            assertEquals(158, set.item(resources, "media_bg_view").layout.mHeight / resources.getDisplayMetrics().density, .01f);
        } finally {
            ModuleSettings.mediaLayoutPreset = oldNormal; ModuleSettings.mediaIslandLayoutPreset = oldIsland;
            ModuleSettings.mediaEnabled = oldMedia; ModuleSettings.islandEnabled = oldEnabled;
            ModuleSettings.standardMediaIslandHeight = 0;
        }
    }

}
