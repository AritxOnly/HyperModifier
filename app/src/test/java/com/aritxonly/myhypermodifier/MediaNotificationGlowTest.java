package com.aritxonly.myhypermodifier;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.graphics.drawable.ColorDrawable;
import androidx.constraintlayout.widget.ConstraintLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE, application=android.app.Application.class)
public class MediaNotificationGlowTest {
    public static class Holder { public View player, mediaBg; }
    public static class Data { public Boolean isPlaying = true; }
    public static class Controller { public Holder holder = new Holder(); public Data mediaData = new Data(); public Object artWorkDrawable = new ColorDrawable(0xff00aa44); }
    @Test public void nativeGlowReusesOneViewStopsOffscreenAndInAodAndRestoresOnToggle() {
        var activity = Robolectric.buildActivity(android.app.Activity.class).setup().visible();
        var root = new ConstraintLayout(activity.get());
        var bg = new ImageView(activity.get()); bg.setId(View.generateViewId());
        var original = new ColorDrawable(0xff112233); bg.setImageDrawable(original);
        var lp = new ConstraintLayout.LayoutParams(400,132); lp.topToTop=0; lp.startToStart=0;
        root.addView(bg,lp); activity.get().setContentView(root);
        Controller owner = new Controller(); owner.holder.player=root; owner.holder.mediaBg=bg;
        try {
            ModuleSettings.moduleHooksEnabled=true; ModuleSettings.systemUiCompatibilityMode=false;
            ModuleSettings.disableMediaIslandHooks=false; ModuleSettings.inFullAod=false;
            ModuleSettings.mediaNotificationGlowEnabled=false;
            MediaNotificationGlow.bind(owner); assertEquals(1,root.getChildCount());
            ModuleSettings.mediaNotificationGlowEnabled=true; MediaNotificationGlow.refresh(root);
            assertEquals(2,root.getChildCount());
            FrameLayout host=(FrameLayout)root.getChildAt(1);
            var effect=(com.mi.widget.view.MusicBgView)host.getChildAt(0);
            assertEquals(1,effect.starts); assertEquals(255,android.graphics.Color.alpha(effect.base));
            assertEquals(1,miuix.mipalette.MiPalette.initializations);
            for (int color : effect.colors) assertNotEquals("palette must not remain uninitialized", 0, color);
            root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            for (int i=0;i<root.getChildCount();i++) ((ConstraintLayout.LayoutParams)root.getChildAt(i).getLayoutParams()).resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            root.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(132,View.MeasureSpec.EXACTLY)); root.layout(0,0,400,132);
            assertEquals(132,host.getHeight()); assertSame(original,bg.getDrawable());
            MediaNotificationGlow.bind(owner); assertEquals(1,effect.starts); assertEquals(1,effect.colorUpdates);
            MediaNotificationGlow.recordSeed(owner.mediaData,0xffdd3377);
            assertEquals(2,effect.colorUpdates); assertEquals(0xffdd3377,effect.base);
            owner.mediaData.isPlaying=false; MediaNotificationGlow.bind(owner); assertEquals(1,effect.stops);
            owner.mediaData.isPlaying=true; MediaNotificationGlow.bind(owner); assertEquals(2,effect.starts);
            root.setVisibility(View.GONE); host.onVisibilityAggregated(false); assertEquals(2,effect.stops);
            root.setVisibility(View.VISIBLE); host.onVisibilityAggregated(true); assertEquals(3,effect.starts);
            ModuleSettings.inFullAod=true; MediaNotificationGlow.refresh(root); assertEquals(View.GONE,host.getVisibility()); assertEquals(3,effect.stops);
            ModuleSettings.inFullAod=false; MediaNotificationGlow.refresh(root); assertEquals(4,effect.starts);
            root.removeView(host); assertEquals(4,effect.stops);
            root.addView(host); assertEquals(5,effect.starts);
            ModuleSettings.mediaNotificationGlowEnabled=false; MediaNotificationGlow.refresh(root);
            assertEquals(1,root.getChildCount()); assertEquals(5,effect.stops); assertSame(original,bg.getDrawable());
        } finally {
            ModuleSettings.mediaNotificationGlowEnabled=false; MediaNotificationGlow.refresh(root);
            ModuleSettings.inFullAod=false; activity.pause().stop().destroy();
        }
    }
}
