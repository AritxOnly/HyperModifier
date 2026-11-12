package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class MediaLargeCoverStyleTest {
    @After public void reset() { ModuleSettings.mediaLayoutPreset = "standard"; ModuleSettings.mediaIslandLayoutPreset = ""; ModuleSettings.largeCoverMediaIslandHeight = 0; ModuleSettings.notificationRadius = 28; ModuleSettings.notificationsEnabled = true; }
    @Test public void radiusTracksNotificationInsetAndCapsAtHalfTheImageSize() {
        assertEquals(16f, MediaLargeCoverStyle.coverRadius(28, 12, 16, 104, 104), 0f);
        assertEquals(28f, MediaLargeCoverStyle.coverRadius(40, 12, 16, 104, 104), 0f);
        assertEquals(0f, MediaLargeCoverStyle.coverRadius(8, 12, 16, 104, 104), 0f);
        assertEquals(52f, MediaLargeCoverStyle.coverRadius(100, 12, 16, 104, 104), 0f);
    }
    @Test public void islandInnerImageIsResizedRoundedAndRestoredWithText() {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        float density = resources.getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(context);
        View bg = new View(context); bg.setId(resources.getIdentifier("media_bg_view", "id", context.getPackageName())); root.addView(bg);
        FrameLayout cover = new FrameLayout(context); cover.setId(resources.getIdentifier("album_art", "id", context.getPackageName()));
        FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(Math.round(104*density), Math.round(104*density)); outer.setMarginStart(Math.round(12*density)); root.addView(cover, outer);
        ImageView image = new ImageView(context); image.setId(resources.getIdentifier("album_art_image", "id", context.getPackageName()));
        cover.addView(image, new FrameLayout.LayoutParams(53, 53));
        ViewOutlineProvider original = image.getOutlineProvider(); boolean clipped = image.getClipToOutline();
        TextView title = new TextView(context); title.setId(resources.getIdentifier("header_title", "id", context.getPackageName()));
        title.setPadding(3, 9, 4, 11); title.setIncludeFontPadding(true); title.setTextSize(22); root.addView(title);
        float originalSize = title.getTextSize();
        ModuleSettings.disableMediaIslandHooks = false; ModuleSettings.mediaLayoutPreset = "large_cover";
        ModuleSettings.largeCoverMediaHeight = 136; ModuleSettings.largeCoverMediaIslandHeight = 136; ModuleSettings.mediaIslandLayoutPreset = "large_cover"; ModuleSettings.notificationRadius = 28;
        MediaLargeCoverStyle.apply(root);
        assertEquals(Math.round(104*density), image.getLayoutParams().width);
        assertEquals(0, title.getPaddingTop()); assertFalse(title.getIncludeFontPadding());
        assertEquals(17 * resources.getDisplayMetrics().scaledDensity, title.getTextSize(), .01f);
        image.layout(0, 0, Math.round(104*density), Math.round(104*density));
        Outline outline = new Outline(); image.getOutlineProvider().getOutline(image, outline);
        assertEquals(16*density, outline.getRadius(), .01f);
        ViewOutlineProvider managed = image.getOutlineProvider();
        ModuleSettings.notificationRadius = 40; MediaLargeCoverStyle.apply(root);
        assertSame(managed, image.getOutlineProvider());
        image.getOutlineProvider().getOutline(image, outline); assertEquals(28*density, outline.getRadius(), .01f);
        ModuleSettings.mediaLayoutPreset = "system"; ModuleSettings.mediaIslandLayoutPreset = "system"; MediaLargeCoverStyle.apply(root);
        assertEquals(53, image.getLayoutParams().width); assertSame(original, image.getOutlineProvider());
        assertEquals(clipped, image.getClipToOutline()); assertEquals(9, title.getPaddingTop());
        assertTrue(title.getIncludeFontPadding()); assertEquals(originalSize, title.getTextSize(), .01f);
    }
    @Test public void largerActionIconsUseFullButtonAreaAndRestoreTheirScaleType() {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        FrameLayout root = new FrameLayout(context);
        ImageView image = new ImageView(context); image.setId(resources.getIdentifier("action0", "id", context.getPackageName()));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE); image.setPadding(6, 2, 6, 2); root.addView(image);
        ModuleSettings.disableMediaIslandHooks = false; ModuleSettings.mediaLayoutPreset = "large_cover";
        MediaPresetViewProperties.apply(root);
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.getScaleType());
        assertEquals(0, image.getPaddingLeft());
        ModuleSettings.mediaLayoutPreset = "system"; MediaPresetViewProperties.apply(root);
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, image.getScaleType());
        assertEquals(6, image.getPaddingLeft());
    }

}
