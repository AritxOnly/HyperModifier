package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Same renderer in the calibration page and in the hooked Settings process. */
public final class PhoneImageView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private Bitmap bitmap;
    private Rect source;
    private float x, y, scale = 1f;
    private int foreground = 0xffeeeeee;

    public PhoneImageView(Context context) { super(context); }
    public void setImage(Bitmap image) {
        if (bitmap == image) return;
        bitmap = image;
        source = image == null ? null : opaqueBounds(image);
        invalidate();
    }
    public void setTransform(float x, float y, float scale) {
        this.x = PhoneImageGeometry.offset(x); this.y = PhoneImageGeometry.offset(y);
        this.scale = PhoneImageGeometry.scale(scale); invalidate();
    }
    public void setForegroundColor(int value) { foreground = value; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        paint.setColor(0x187f7f7f);
        canvas.drawCircle(w / 2, h / 2, Math.min(w, h) / 2, paint);
        int saved = canvas.save();
        // Keep calibration and runtime inside the same circular container.
        clip.reset();
        clip.addCircle(w / 2, h / 2, Math.min(w, h) / 2, Path.Direction.CW);
        canvas.clipPath(clip);
        if (bitmap != null) {
            float[] box = PhoneImageGeometry.destination(w, h, source.width(), source.height(), x, y, scale);
            paint.setAlpha(255);
            canvas.drawBitmap(bitmap, source, new RectF(box[0], box[1], box[2], box[3]), paint);
        } else {
            canvas.translate(x * w / 64f, y * h / 64f);
            canvas.scale(scale, scale, w / 2, h / 2);
            paint.setColor(foreground);
            canvas.drawRoundRect(w * .33f, h * .19f, w * .67f, h * .81f, w * .05f, w * .05f, paint);
            paint.setColor(0xff6688ff);
            canvas.drawRoundRect(w * .355f, h * .22f, w * .645f, h * .77f, w * .025f, w * .025f, paint);
            paint.setColor(0xffc9d4ff);
            canvas.drawCircle(w * .5f, h * .5f, w * .12f, paint);
        }
        canvas.restoreToCount(saved);
    }

    private static Rect opaqueBounds(Bitmap bitmap) {
        int left = bitmap.getWidth(), top = bitmap.getHeight(), right = 0, bottom = 0;
        int[] row = new int[bitmap.getWidth()];
        for (int y = 0; y < bitmap.getHeight(); y++) {
            bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int x = 0; x < row.length; x++) {
                if ((row[x] >>> 24) < 128) continue;
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
            }
        }
        return right > left && bottom > top ? new Rect(left, top, right, bottom)
                : new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    }
}
