package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaAodLayoutTest {
    /** Stand-in for the native load hook: always reload the original before patching. */
    public static final class ReloadingSet extends ConstraintSet {
        final ConstraintSet stock;
        final boolean island;
        int loads;
        ReloadingSet(ConstraintLayout root, boolean island) {
            stock = new ConstraintSet(); stock.clone(root); this.island = island;
        }
        @Override public void load(Context context, int xml) {
            loads++; clone(stock);
            MediaConstraintCustomizer.patchMediaConstraintSet(context, this, island);
        }
        @Override public void applyTo(ConstraintLayout root) {
            super.applyTo(root);
            MediaPresetViewProperties.apply(root);
        }
    }
    @Test public void aodReloadsSmallCoverCentersMetadataAndRestoresExpandedLayoutWithoutReplayingIsland() throws Exception {
        var activity = Robolectric.buildActivity(android.app.Activity.class).setup().visible();
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(activity.get()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        ConstraintLayout root = new ConstraintLayout(context);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (String name : new String[]{"media_bg", "album_art", "header_title", "header_artist", "media_seamless",
                "media_progress_bar", "media_elapsed_time", "media_total_time", "actions", "action0", "action1", "action2", "action3", "action4"}) {
            View view;
            if (name.startsWith("header") || name.contains("time")) {
                TextView text = new TextView(context); text.setText("Metadata"); text.setTextSize(name.equals("header_title") ? 22 : 14); text.setSingleLine(); view = text;
            } else if (name.equals("album_art")) view = new ConstraintLayout(context);
            else view = new ImageView(context);
            view.setId(resources.getIdentifier(name, "id", context.getPackageName()));
            var params = new ConstraintLayout.LayoutParams(36, 28);
            params.startToStart = ConstraintSet.PARENT_ID; params.topToTop = ConstraintSet.PARENT_ID;
            if (name.equals("media_bg")) { params.width = 0; params.height = 185; params.endToEnd = ConstraintSet.PARENT_ID; }
            if (name.equals("album_art")) { params.width = params.height = 53; params.topMargin = 10; params.setMarginStart(10); }
            if (name.startsWith("header")) {
                params.width = 0; params.height = -2;
                params.startToStart = -1; params.startToEnd = resources.id("album_art");
                params.endToEnd = ConstraintSet.PARENT_ID; params.setMarginStart(12);
            }
            root.addView(view, params);
        }
        var art = (ConstraintLayout) root.findViewById(resources.id("album_art"));
        var image = new ImageView(context); image.setId(resources.getIdentifier("album_art_image", "id", context.getPackageName()));
        art.addView(image, new ConstraintLayout.LayoutParams(53, 53));
        activity.get().setContentView(root);
        assertTrue(root.isAttachedToWindow());
        ReloadingSet normal = new ReloadingSet(root, false), album = new ReloadingSet(art, false);
        ReloadingSet island = new ReloadingSet(root, true);
        var apply = ReloadingSet.class.getMethod("applyTo", ConstraintLayout.class);
        var normalPending = new RuntimeRefreshRegistry.PendingMediaConstraint(context, 0x7f180019);
        var albumPending = new RuntimeRefreshRegistry.PendingMediaConstraint(context, 0x7f180016);
        var islandPending = new RuntimeRefreshRegistry.PendingMediaConstraint(context, 0x7f180018);
        normalPending.addTarget(root, apply); albumPending.addTarget(art, apply); islandPending.addTarget(root, apply);
        RuntimeRefreshRegistry.PENDING_MEDIA_CONSTRAINTS.put(normal, normalPending);
        RuntimeRefreshRegistry.PENDING_MEDIA_CONSTRAINTS.put(album, albumPending);
        RuntimeRefreshRegistry.PENDING_MEDIA_CONSTRAINTS.put(island, islandPending);
        String previous = ModuleSettings.mediaLayoutPreset, oldIsland = ModuleSettings.mediaIslandLayoutPreset;
        try {
            ModuleSettings.disableMediaIslandHooks = false; ModuleSettings.mediaEnabled = true;
            ModuleSettings.mediaLayoutPreset = "large_cover"; ModuleSettings.mediaIslandLayoutPreset = "standard";
            ModuleSettings.largeCoverMediaHeight = 132; ModuleSettings.fullAodHeight = 80;
            ModuleSettings.largeCoverMediaXml = ""; ModuleSettings.hideAodActions = true;
            ModuleSettings.inFullAod = false;
            normal.load(context, 0x7f180019); normal.applyTo(root);
            album.load(context, 0x7f180016); album.applyTo(art);
            assertEquals(108, art.getLayoutParams().height); assertEquals(108, image.getLayoutParams().height);
            var panel = new MediaAodPreparationTest.Panel();
            MediaAodPreparationTest.Controller.mEnableFullAod = true;
            panel.notifiFullAodController.applyStyle = () -> {
                ModuleSettings.inFullAod = true;
                MediaPresetViewProperties.apply(root);
                RuntimeRefreshRegistry.refreshMediaAodLayouts();
            };
            for (int cycle = 0; cycle < 2; cycle++) {
                assertTrue(MediaAodPreparation.beforeWakeAnimation(panel, false,
                        MediaAodPreparationTest.AnimationType.FULL_AOD));
                // The following bounds are ready before the native parent animation proceeds.
                measure(root, 80);
                View bg = root.findViewById(resources.id("media_bg"));
                TextView title = root.findViewById(resources.id("header_title"));
                TextView artist = root.findViewById(resources.id("header_artist"));
                assertEquals(80, bg.getHeight()); assertEquals(53, art.getHeight());
                assertEquals(53, image.getLayoutParams().height);
                assertTrue(art.getTop() >= bg.getTop() && art.getBottom() <= bg.getBottom());
                assertEquals((bg.getTop() + bg.getBottom()) / 2f, (art.getTop() + art.getBottom()) / 2f, 1f);
                assertEquals((art.getTop() + art.getBottom()) / 2f, (title.getTop() + artist.getBottom()) / 2f, 1f);
                assertEquals(22 * resources.getDisplayMetrics().scaledDensity, title.getTextSize(), .01f);
                assertEquals(View.GONE, root.findViewById(resources.id("action2")).getVisibility());
                assertEquals(View.GONE, root.findViewById(resources.id("media_progress_bar")).getVisibility());
                assertEquals(0, island.loads);
                ModuleSettings.inFullAod = false;
                RuntimeRefreshRegistry.refreshMediaAodLayouts();
                measure(root, 132);
                assertEquals(108, art.getHeight()); assertEquals(108, image.getLayoutParams().height);
                assertEquals(132, bg.getHeight());
                assertEquals(View.GONE, root.findViewById(resources.id("action0")).getVisibility());
                assertEquals(View.VISIBLE, root.findViewById(resources.id("action2")).getVisibility());
            }
        } finally {
            RuntimeRefreshRegistry.PENDING_MEDIA_CONSTRAINTS.clear();
            ModuleSettings.inFullAod = false; ModuleSettings.mediaLayoutPreset = previous; ModuleSettings.mediaIslandLayoutPreset = oldIsland;
            activity.pause().stop().destroy();
        }
    }
    private static void measure(ConstraintLayout root, int height) {
        for (int i = 0; i < root.getChildCount(); i++) {
            ((ConstraintLayout.LayoutParams) root.getChildAt(i).getLayoutParams()).resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, height);
    }
}
