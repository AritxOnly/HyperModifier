package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Main-thread metadata and bounded, event-driven artwork preparation. No Activity retention. */
final class SettingsHomeIconCache {
    private static final String MODULE = "com.aritxonly.myhypermodifier";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "MHM-settings-icons");
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    private static final Map<String, Drawable.ConstantState> READY = new java.util.LinkedHashMap<>(32, .75f, true);
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Map<ImageView, String> BOUND = new WeakHashMap<>();
    private static Configuration configuration;
    private static volatile int generation;
    private static int size;

    static void reset(Context context) {
        generation++;
        WORKER.getQueue().clear();
        READY.clear(); REQUESTED.clear(); FAILED.clear(); BOUND.clear();
        configuration = new Configuration(context.getResources().getConfiguration());
        int dimension = context.getResources().getIdentifier("header_icon_size", "dimen", "com.android.settings");
        size = dimension != 0 ? context.getResources().getDimensionPixelSize(dimension)
                : Math.round(28f * context.getResources().getDisplayMetrics().density);
    }

    static void prepare(Context context, String key) {
        if (key == null || key.isBlank() || !REQUESTED.add(key)) return;
        Context app = context.getApplicationContext();
        if (app == null) { REQUESTED.remove(key); return; }
        int version = generation;
        int pixels = size;
        WORKER.execute(() -> {
            if (version != generation) return;
            Drawable.ConstantState artwork = null;
            try {
                Drawable source;
                if (key.startsWith("resource:")) {
                    Context module = app.createPackageContext(MODULE, Context.CONTEXT_IGNORE_SECURITY);
                    int id = module.getResources().getIdentifier(key.substring(9), "drawable", MODULE);
                    source = module.getDrawable(id);
                } else source = app.getPackageManager().getApplicationIcon(key);
                Bitmap bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888);
                bitmap.setDensity(app.getResources().getDisplayMetrics().densityDpi);
                Canvas canvas = new Canvas(bitmap);
                Path clip = new Path();
                clip.addRoundRect(new RectF(0, 0, pixels, pixels), pixels * .28f, pixels * .28f, Path.Direction.CW);
                canvas.clipPath(clip);
                source.setBounds(0, 0, pixels, pixels);
                source.draw(canvas);
                artwork = new BitmapDrawable(app.getResources(), bitmap).getConstantState();
            } catch (Exception ignored) { /* Native fallback remains; retry after a list/config rebuild. */ }
            Drawable.ConstantState result = artwork;
            MAIN.post(() -> {
                if (version != generation) return;
                if (result == null) {
                    FAILED.add(key);
                    BOUND.entrySet().removeIf(entry -> key.equals(entry.getValue()));
                    return;
                }
                READY.put(key, result);
                if (READY.size() > 32) {
                    String oldest = READY.keySet().iterator().next();
                    READY.remove(oldest); REQUESTED.remove(oldest);
                }
                ArrayList<WeakReference<ImageView>> views = new ArrayList<>();
                for (Map.Entry<ImageView, String> entry : BOUND.entrySet()) {
                    if (key.equals(entry.getValue())) views.add(new WeakReference<>(entry.getKey()));
                }
                for (WeakReference<ImageView> ref : views) {
                    ImageView view = ref.get();
                    if (view != null && key.equals(BOUND.get(view))) {
                        apply(view, result); BOUND.remove(view);
                    }
                }
            });
        });
    }

    static void bind(ImageView view, String key) {
        if (!view.getResources().getConfiguration().equals(configuration)) reset(view.getContext());
        Drawable.ConstantState artwork = READY.get(key);
        if (artwork != null) apply(view, artwork);
        else if (!FAILED.contains(key)) { BOUND.put(view, key); prepare(view.getContext(), key); }
    }

    static boolean hasPendingBindings() { return !BOUND.isEmpty(); }

    static void unbind(ImageView view) { BOUND.remove(view); }

    private static void apply(ImageView view, Drawable.ConstantState artwork) {
        view.setImageTintList(null);
        view.clearColorFilter();
        view.setImageDrawable(artwork.newDrawable(view.getResources()));
    }
}
