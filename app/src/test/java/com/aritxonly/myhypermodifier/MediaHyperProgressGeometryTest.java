package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.view.ViewGroup;
import android.widget.SeekBar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaHyperProgressGeometryTest {
    /** Mirrors the baseline widget's constructor-cached position and shader drawing contract. */
    public static final class CachedShaderSeekBar extends SeekBar {
        public final float[] uTrackPosition;
        public final RuntimeShader runtimeShader;
        private final Paint paint = new Paint();
        public CachedShaderSeekBar(Context context) { this(context, false); }
        public CachedShaderSeekBar(Context context, boolean glow) {
            super(context);
            float density = context.getResources().getDisplayMetrics().density;
            uTrackPosition = new float[]{4 * density, 19 * density};
            runtimeShader = new RuntimeShader("uniform float2 uTrackPosition; uniform float uWidth; uniform float uProgress; half4 main(float2 p) { if(abs(p.y-uTrackPosition.y)>3.0) "
                    + (glow ? "return half4(1.0) * (1.0-smoothstep(3.0,18.0,abs(p.y-uTrackPosition.y))) * 0.25; " : "return half4(0.0); ")
                    + "return p.x < uWidth*uProgress ? half4(1.0) : half4(0.4,0.4,0.4,1.0); }");
            runtimeShader.setFloatUniform("uTrackPosition", uTrackPosition);
            runtimeShader.setFloatUniform("uWidth", new float[]{100f});
            runtimeShader.setFloatUniform("uProgress", new float[]{.36f});
            paint.setShader(runtimeShader);
            setMinHeight(Math.round(6 * density)); setMaxHeight(Math.round(6 * density));
            setPadding(0, Math.round(16 * density), 0, Math.round(16 * density));
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (runtimeShader != null) runtimeShader.setFloatUniform("uWidth", new float[]{w});
        }
        @Override protected synchronized void onDraw(Canvas canvas) { canvas.drawRect(0, 0, getWidth(), getHeight(), paint); }
    }
    @Test public void recenterUpdatesActualShaderPixelsAndRestoresItsOriginalPosition() {
        CachedShaderSeekBar seek = new CachedShaderSeekBar(RuntimeEnvironment.getApplication());
        seek.setLayoutParams(new ViewGroup.LayoutParams(100, 18)); seek.layout(0, 0, 100, 18);
        Bitmap before = renderHardware(seek, 100, 18);
        assertEquals(0, Color.alpha(before.getPixel(10, 9)));
        float originalX = seek.uTrackPosition[0], originalY = seek.uTrackPosition[1];
        MediaHyperProgressGeometry.apply(seek);
        assertEquals(originalX, seek.uTrackPosition[0], 0f);
        assertEquals(9f, seek.uTrackPosition[1], 0f);
        assertEquals(6, seek.getPaddingTop());
        Bitmap after = renderHardware(seek, 100, 18);
        assertEquals(255, Color.alpha(after.getPixel(10, 9)));
        assertEquals(Color.WHITE, after.getPixel(10, 9));
        assertTrue(Color.red(after.getPixel(70, 9)) < Color.red(after.getPixel(10, 9)));
        MediaHyperProgressGeometry.apply(seek);
        MediaHyperProgressGeometry.restore(seek);
        assertEquals(originalY, seek.uTrackPosition[1], 0f);
        before.recycle(); after.recycle();
    }
    @Test public void nativeDragHeightRemainsInsideTheCompactCanvas() {
        CachedShaderSeekBar seek = new CachedShaderSeekBar(RuntimeEnvironment.getApplication());
        seek.setLayoutParams(new ViewGroup.LayoutParams(100, 18));
        seek.setMaxHeight(12); // Native height animation can grow the bar from 6dp to 12dp.
        MediaHyperProgressGeometry.apply(seek);
        assertEquals(3, seek.getPaddingTop());
        assertTrue(seek.getPaddingTop() + seek.getMaxHeight() <= 18);
        assertEquals(9f, seek.uTrackPosition[1], 0f);
        MediaHyperProgressGeometry.restore(seek);
    }
    @Test public void leavingLargeCoverRestoresNativePaddingAndShaderPosition() {
        var resources = new MediaConstraintPlanTest.TestResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public android.content.res.Resources getResources() { return resources; }
        };
        var root = new android.widget.FrameLayout(context);
        var bg = new android.view.View(context);
        bg.setId(resources.getIdentifier("media_bg", "id", context.getPackageName()));
        root.addView(bg);
        CachedShaderSeekBar seek = new CachedShaderSeekBar(context);
        seek.setId(resources.getIdentifier("media_progress_bar", "id", context.getPackageName()));
        root.addView(seek, new android.widget.FrameLayout.LayoutParams(100, 18));
        int originalPadding = seek.getPaddingTop();
        float originalPosition = seek.uTrackPosition[1];
        String preset = ModuleSettings.mediaLayoutPreset;
        boolean disabled = ModuleSettings.disableMediaIslandHooks;
        try {
            ModuleSettings.disableMediaIslandHooks = false;
            ModuleSettings.mediaLayoutPreset = "large_cover";
            MediaLargeCoverStyle.apply(root);
            assertEquals(9f, seek.uTrackPosition[1], 0f);
            assertEquals(6, seek.getPaddingTop());
            MediaLargeCoverStyle.apply(root); // Rebinding must retain the first native snapshot.
            ModuleSettings.mediaLayoutPreset = "system";
            MediaLargeCoverStyle.apply(root);
            assertEquals(originalPosition, seek.uTrackPosition[1], 0f);
            assertEquals(originalPadding, seek.getPaddingTop());
            assertEquals(originalPadding, seek.getPaddingBottom());
        } finally {
            ModuleSettings.mediaLayoutPreset = preset;
            ModuleSettings.disableMediaIslandHooks = disabled;
        }
    }
    @Test public void native38dpCanvasLeavesSpaceForTheOuterGlowOnBothSides() {
        CachedShaderSeekBar seek = new CachedShaderSeekBar(RuntimeEnvironment.getApplication(), true);
        seek.setLayoutParams(new ViewGroup.LayoutParams(100, 18)); seek.layout(0, 0, 100, 18);
        MediaHyperProgressGeometry.apply(seek);
        Bitmap clipped = renderHardware(seek, 100, 18);
        assertTrue("18dp clips visible glow at its boundary", Color.alpha(clipped.getPixel(10, 0)) > 5);
        seek.setLayoutParams(new ViewGroup.LayoutParams(100, 38)); seek.layout(0, 0, 100, 38);
        MediaHyperProgressGeometry.apply(seek);
        assertEquals(19f, seek.uTrackPosition[1], 0f);
        Bitmap full = renderHardware(seek, 100, 38);
        assertEquals(0, Color.alpha(full.getPixel(10, 0)));
        assertEquals(0, Color.alpha(full.getPixel(10, 37)));
        assertTrue("glow exists above the track", Color.alpha(full.getPixel(10, 10)) > 5);
        assertTrue("glow exists below the track", Color.alpha(full.getPixel(10, 28)) > 5);
        assertEquals(Color.WHITE, full.getPixel(10, 19));
        MediaHyperProgressGeometry.restore(seek);
        clipped.recycle(); full.recycle();
    }
    static Bitmap renderHardware(android.view.View view, int width, int height) {
        android.graphics.RenderNode node = new android.graphics.RenderNode("media-test");
        node.setPosition(0, 0, width, height);
        Canvas canvas = node.beginRecording(width, height);
        view.draw(canvas);
        node.endRecording();
        try {
            var method = android.graphics.HardwareRenderer.class.getDeclaredMethod("createHardwareBitmap",
                    android.graphics.RenderNode.class, int.class, int.class);
            method.setAccessible(true);
            Bitmap hardware = (Bitmap) method.invoke(null, node, width, height);
            Bitmap readable = hardware.copy(Bitmap.Config.ARGB_8888, false);
            hardware.recycle();
            return readable;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { node.discardDisplayList(); }
    }

}
