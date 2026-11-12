package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Uses the real ConstraintLayout solver, including runtime side-button visibility changes. */
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class MediaLayoutGeometryTest {
    private static final String[] NAMES = {"media_bg", "album_art", "header_title", "header_artist",
            "media_seamless", "media_progress_bar", "media_elapsed_time", "media_total_time", "actions",
            "action0", "action1", "action2", "action3", "action4"};

    @Test public void largeCoverStaysInsideBackgroundAfterSpotifyBindsSideActions() {
        assertGeometry("large_cover");
        assertGeometry("large_cover", true, 320, 1f);
        assertGeometry("large_cover", false, 320, 1f);
        assertGeometry("large_cover", false, 396, 1.3f);
        assertGeometry("large_cover", true, 440, 1.3f);
    }
    @Test public void compactStaysInsideBackgroundAfterSpotifyBindsSideActions() {
        assertGeometry("compact");
    }
    private void assertGeometry(String preset) { assertGeometry(preset, false, 400, 1f); }

    private void assertGeometry(String preset, boolean island, int width, float fontScale) {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        resources.getDisplayMetrics().scaledDensity = resources.getDisplayMetrics().density * fontScale;
        String background = island ? "media_bg_view" : "media_bg";
        for (String source : NAMES) {
            String name = source.equals("media_bg") ? background : source;
            resources.getIdentifier(name, "id", context.getPackageName());
        }
        ConstraintLayout root = new ConstraintLayout(context);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (String source : NAMES) {
            String name = source.equals("media_bg") ? background : source;
            View child;
            if (name.startsWith("header_") || name.contains("time")) {
                TextView text = new TextView(context);
                text.setText(name.contains("time") ? "01:24" : "A title or artist");
                text.setTextSize(14);
                text.setSingleLine(true);
                child = text;
            } else if (name.equals("media_progress_bar")) {
                var seek = new MediaHyperProgressGeometryTest.CachedShaderSeekBar(context); seek.setMax(100); seek.setProgress(36); seek.setThumb(null);
                seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffeef1fa));
                seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x557e8aab));
                child = seek;
            } else if (name.equals("media_seamless")) {
                var output = new android.widget.ImageView(context); output.setImageResource(android.R.drawable.ic_menu_share);
                output.setColorFilter(android.graphics.Color.WHITE); child = output;
            } else if (name.startsWith("action")) child = new android.widget.ImageButton(context);
            else child = new View(context);
            if (child instanceof android.widget.ImageButton image) {
                image.setBackground(null);
                int icon = switch (name) {
                    case "action0" -> android.R.drawable.btn_star_big_on;
                    case "action1" -> android.R.drawable.ic_media_previous;
                    case "action2" -> android.R.drawable.ic_media_pause;
                    case "action3" -> android.R.drawable.ic_media_next;
                    default -> android.R.drawable.ic_menu_rotate;
                };
                image.setImageResource(icon); image.setColorFilter(android.graphics.Color.WHITE);
            }
            if (name.equals("album_art") || name.equals(background)) {
                var fill = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    name.equals("album_art") ? new int[]{0xffa3bfca, 0xff445965} : new int[]{0xff3b528b, 0xff515068});
                fill.setCornerRadius(px(context, name.equals("album_art") ? 16 : 28)); child.setBackground(fill);
            }
            if (child instanceof TextView text) text.setTextColor(android.graphics.Color.WHITE);
            child.setId(resources.id(name));
            var params = new ConstraintLayout.LayoutParams(px(context, 40), px(context, 32));
            params.startToStart = ConstraintSet.PARENT_ID;
            params.topToTop = ConstraintSet.PARENT_ID;
            if (name.equals(background)) {
                params.width = 0;
                params.height = px(context, 185);
                params.endToEnd = ConstraintSet.PARENT_ID;
            }
            if (name.equals("action0") || name.equals("action4")) {
                params.topToTop = -1;
                params.topToBottom = resources.id("media_progress_bar");
                params.topMargin = px(context, 14);
            }
            root.addView(child, params);
        }
        String previous = ModuleSettings.mediaLayoutPreset, previousIsland = ModuleSettings.mediaIslandLayoutPreset;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = preset;
            ModuleSettings.mediaIslandLayoutPreset = preset;
            ModuleSettings.largeCoverMediaHeight = 132;
            ModuleSettings.largeCoverMediaIslandHeight = 144;
            ModuleSettings.compactMediaHeight = 80;
            ModuleSettings.compactMediaXml = ModuleSettings.largeCoverMediaXml = "";
            ConstraintSet set = new ConstraintSet();
            set.clone(root);
            set.setVisibilityMode(resources.id("action0"), 1);
            MediaConstraintCustomizer.patchMediaConstraintSet(context, set, island);
            assertEquals(0, set.getVisibilityMode(resources.id("action0")));
            set.applyTo(root);
            // Reproduce Spotify binding's late visibility mutation after ConstraintSet.applyTo.
            root.findViewById(resources.id("action0")).setVisibility(View.VISIBLE);
            root.findViewById(resources.id("action4")).setVisibility(View.VISIBLE);
            MediaPresetViewProperties.apply(root);
            for (int j = 0; j < root.getChildCount(); j++) {
                ((ConstraintLayout.LayoutParams) root.getChildAt(j).getLayoutParams()).resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
            root.measure(View.MeasureSpec.makeMeasureSpec(px(context, width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px(context, 240), View.MeasureSpec.EXACTLY));
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
            View bg = root.findViewById(resources.id(background));
            View art = root.findViewById(resources.id("album_art"));
            assertTrue("cover escapes background: art=" + art.getTop() + ".." + art.getBottom()
                    + " bg=" + bg.getTop() + ".." + bg.getBottom(), art.getTop() >= bg.getTop() && art.getBottom() <= bg.getBottom());
            assertEquals(preset.equals("large_cover") && island ? View.VISIBLE : View.GONE, root.findViewById(resources.id("action0")).getVisibility());
            assertEquals(preset.equals("large_cover") && island ? View.VISIBLE : View.GONE, root.findViewById(resources.id("action4")).getVisibility());
            if (preset.equals("large_cover")) {
                assertEquals(px(context, island ? 72 : 108), art.getWidth());
                if (!island) {
                    assertEquals(px(context, 12), art.getTop() - bg.getTop());
                    assertEquals(px(context, 12), bg.getBottom() - art.getBottom());
                    assertEquals(px(context, 12), art.getLeft() - bg.getLeft());
                }
                View progress = root.findViewById(resources.id("media_progress_bar"));
                assertTrue("progress escapes background", progress.getBottom() <= bg.getBottom());
                assertEquals(progress.getHeight() / 2f, ((MediaHyperProgressGeometryTest.CachedShaderSeekBar) progress).uTrackPosition[1], .01f);
                assertEquals(View.VISIBLE, root.findViewById(resources.id("media_elapsed_time")).getVisibility());
                assertEquals(View.VISIBLE, root.findViewById(resources.id("media_total_time")).getVisibility());
                View title = root.findViewById(resources.id("header_title"));
                View artist = root.findViewById(resources.id("header_artist"));
                assertTrue("title overlaps artist", title.getBottom() <= artist.getTop());
                assertTrue("artist overlaps visible progress track: " + artist.getBottom() + " vs " + (progress.getTop() + progress.getHeight() / 2),
                        artist.getBottom() <= progress.getTop() + progress.getHeight() / 2 - px(context, 3));
                var bitmap = MediaHyperProgressGeometryTest.renderHardware(root, px(context, width), px(context, island ? 144 : 132));
                java.io.File folder = new java.io.File("build/reports/media-layout"); folder.mkdirs();
                try (var stream = new java.io.FileOutputStream(new java.io.File(folder,
                        "large-cover-" + (island ? "island-" : "normal-") + width + "-font" + fontScale + ".png"))) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                } catch (java.io.IOException error) { throw new AssertionError(error); }
                bitmap.recycle();
                int lastEnd = island ? bg.getLeft() : art.getRight();
                for (int i = island ? 0 : 1; i < (island ? 5 : 4); i++) {
                    View button = root.findViewById(resources.id("action" + i));
                    assertTrue("actions overlap horizontally: width=" + width + " font=" + fontScale + " island=" + island + " index=" + i + " left=" + button.getLeft() + " lastEnd=" + lastEnd + " btnWidth=" + button.getWidth(), button.getLeft() >= lastEnd);
                    assertTrue("actions overlap singer: " + button.getTop() + " < " + artist.getBottom(), button.getTop() >= artist.getBottom());
                    if (island) {
                        assertTrue("actions overlap top row", button.getTop() >= art.getBottom());
                        assertTrue("actions overlap progress canvas", button.getTop() >= progress.getBottom());
                        assertTrue("actions escape island", button.getBottom() <= bg.getBottom() - px(context, 12));
                        assertEquals(px(context, i == 0 || i == 4 ? 30 : i == 2 ? 40 : 36), button.getWidth());
                    } else assertTrue("actions overlap progress", button.getBottom() <= progress.getTop() + progress.getHeight() / 2 - px(context, 5));
                    lastEnd = button.getRight();
                }
            }
        } finally { ModuleSettings.mediaLayoutPreset = previous; ModuleSettings.mediaIslandLayoutPreset = previousIsland; }
    }
    public static final class Holder { public View player; }
    public static final class Controller { public Holder holder; }
    public static final class Action {
        public android.graphics.drawable.Drawable icon = new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE);
        public Runnable action = () -> {};
        public CharSequence contentDescription = "Favorite";
    }

    @Test public void spotifyLateBindingHonorsPresetAndCustomXmlVisibility() throws Exception {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        android.widget.ImageButton button = new android.widget.ImageButton(context);
        button.setId(resources.getIdentifier("action0", "id", context.getPackageName()));
        var bind = SpotifyMediaCardBinding.class.getDeclaredMethod("bind", android.widget.ImageButton.class, Object.class);
        bind.setAccessible(true);
        String previous = ModuleSettings.mediaLayoutPreset, xml = ModuleSettings.largeCoverMediaXml;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = "large_cover";
            ModuleSettings.largeCoverMediaXml = "";
            bind.invoke(null, button, new Action());
            assertEquals(View.GONE, button.getVisibility());
            ModuleSettings.largeCoverMediaXml = "<ConstraintSet xmlns:android=\"http://schemas.android.com/apk/res/android\"><Constraint android:id=\"@id/action0\" android:visibility=\"visible\" /></ConstraintSet>";
            bind.invoke(null, button, new Action());
            assertEquals(View.VISIBLE, button.getVisibility());
            ModuleSettings.mediaLayoutPreset = "system";
            bind.invoke(null, button, new Action());
            assertEquals(View.VISIBLE, button.getVisibility());
        } finally {
            ModuleSettings.mediaLayoutPreset = previous;
            ModuleSettings.largeCoverMediaXml = xml;
        }
    }

    @Test public void metadataAndAodCallbacksDoNotResurrectCompactProgress() {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        android.widget.FrameLayout root = new android.widget.FrameLayout(context);
        for (String name : new String[]{"media_bg", "media_progress_bar", "media_elapsed_time", "media_total_time"}) {
            View view = new View(context);
            view.setId(resources.getIdentifier(name, "id", context.getPackageName()));
            root.addView(view);
        }
        Controller controller = new Controller(); controller.holder = new Holder(); controller.holder.player = root;
        String previous = ModuleSettings.mediaLayoutPreset, previousIsland = ModuleSettings.mediaIslandLayoutPreset;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = "compact";
            ModuleSettings.compactMediaXml = "";
            MediaPresetViewProperties.applyController(controller);
            assertEquals(View.GONE, root.findViewById(resources.id("media_progress_bar")).getVisibility());
            root.findViewById(resources.id("media_progress_bar")).setVisibility(View.VISIBLE);
            MediaPresetViewProperties.applyController(controller);
            assertEquals(View.GONE, root.findViewById(resources.id("media_progress_bar")).getVisibility());
            // Large-cover policy never reveals a progress view hidden by native full AOD.
            ModuleSettings.mediaLayoutPreset = "large_cover";
            ModuleSettings.largeCoverMediaXml = "";
            MediaPresetViewProperties.applyController(controller);
            assertEquals(View.GONE, root.findViewById(resources.id("media_progress_bar")).getVisibility());
        } finally { ModuleSettings.mediaLayoutPreset = previous; ModuleSettings.mediaIslandLayoutPreset = previousIsland; }
    }

    private static int px(Context context, int dp) { return Math.round(dp * context.getResources().getDisplayMetrics().density); }
}
