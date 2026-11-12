package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;

/** One native island glow per ordinary player. All module work occurs at bind/layout boundaries. */
final class MediaNotificationGlow {
    private static final Map<View, WeakReference<Host>> HOSTS = new WeakHashMap<>();
    private static final Map<View, WeakReference<Object>> OWNERS = new WeakHashMap<>();
    private static final Map<Object, Integer> SEEDS = new WeakHashMap<>();
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> result = new HashMap<>();
            for (Field field : type.getFields()) result.put(field.getName(), field);
            return result;
        }
    };
    private static final ClassValue<Api> API = new ClassValue<>() {
        @Override protected Api computeValue(Class<?> type) { return new Api(type.getClassLoader()); }
    };
    private static final ClassValue<Constructor<?>> PARAMS = new ClassValue<>() {
        @Override protected Constructor<?> computeValue(Class<?> type) {
            try { return type.getConstructor(int.class, int.class); }
            catch (NoSuchMethodException ignored) { return null; }
        }
    };
    private static Object read(Object object, String name) throws IllegalAccessException {
        Field field = object == null ? null : FIELDS.get(object.getClass()).get(name);
        return field == null ? null : field.get(object);
    }
    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && !ModuleSettings.disableMediaIslandHooks && ModuleSettings.mediaNotificationGlowEnabled;
    }
    static void bind(Object controller) {
        try {
            Object holder = read(controller, "holder");
            if (holder == null) return; // Notification controller has no playback owner; island uses different holders.
            Object player = read(holder, "player"), background = read(holder, "mediaBg");
            if (!(player instanceof ViewGroup root) || !(background instanceof View bg)) return;
            OWNERS.put(root, new WeakReference<>(controller));
            Host host = obtain(root, bg, controller.getClass());
            if (host == null) return;
            Object data = read(controller, "mediaData");
            host.playing = Boolean.TRUE.equals(read(data, "isPlaying"));
            Object artwork = read(controller, "artWorkDrawable");
            host.color(artwork, SEEDS.get(data));
            host.update(bg);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    static void refresh(View root) {
        WeakReference<Host> ref = HOSTS.get(root);
        Host host = ref == null ? null : ref.get();
        if (host == null) {
            WeakReference<Object> owner = OWNERS.get(root);
            if (enabled() && owner != null && owner.get() != null) bind(owner.get());
            return;
        }
        if (!enabled()) { host.dispose(); HOSTS.remove(root); return; }
        host.update(host.background);
    }
    static void recordSeed(Object data, int color) {
        if (!enabled() || data == null) return;
        SEEDS.put(data, color);
        for (var entry : OWNERS.entrySet()) {
            Object owner = entry.getValue().get();
            try {
                if (owner != null && read(owner, "mediaData") == data) bind(owner);
            } catch (IllegalAccessException ignored) { }
        }
    }
    private static Host obtain(ViewGroup root, View bg, Class<?> controllerType) throws ReflectiveOperationException {
        WeakReference<Host> ref = HOSTS.get(root);
        Host host = ref == null ? null : ref.get();
        if (!enabled()) {
            if (host != null) host.dispose();
            HOSTS.remove(root); return null;
        }
        if (host != null) return host;
        Api api = API.get(controllerType);
        if (api.constructor == null || PARAMS.get(bg.getLayoutParams().getClass()) == null) return null;
        host = new Host(root.getContext(), api, bg);
        host.setId(View.generateViewId());
        host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        host.setClickable(false); host.setFocusable(false);
        host.updateGeometry(bg);
        root.addView(host, root.indexOfChild(bg) + 1);
        HOSTS.put(root, new WeakReference<>(host));
        return host;
    }
    private static final class Api {
        Constructor<?> constructor;
        Method start, resume, stop, colors, palette;
        Api(ClassLoader loader) {
            try {
                Class<?> type = Class.forName("com.mi.widget.view.MusicBgView", false, loader);
                constructor = type.getConstructor(Context.class);
                start = type.getMethod("start"); resume = type.getMethod("resume"); stop = type.getMethod("stop");
                colors = type.getMethod("setGradientColor", int.class, int[].class);
                try {
                    Class<?> paletteType = Class.forName("miuix.mipalette.MiPalette", false, loader);
                    palette = paletteType.getMethod("getPaletteColor", int.class, String.class, int.class);
                    Field roles = paletteType.getDeclaredField("map"); roles.setAccessible(true);
                    Object value = roles.get(null);
                    if (!(value instanceof Map<?, ?> map) || !map.containsKey("primary")) {
                        paletteType.getMethod("init").invoke(null);
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) { palette = null; }

            } catch (ReflectiveOperationException ignored) { constructor = null; }
        }
    }
    private static final class Host extends FrameLayout {
        final Api api;
        View effect;
        final View background;
        int[] currentColors;
        Object lastArtwork;
        int lastGeneration = -1, seed = 0xff527bff;
        Integer lastNativeSeed;
        boolean colored, playing, running, failed, syncing;
        final ViewOutlineProvider outline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        };
        float radius;
        Host(Context context, Api api, View bg) throws ReflectiveOperationException {
            super(context); this.api = api; background = bg;
            setOutlineProvider(outline); setClipToOutline(true);
        }
        void color(Object artwork, Integer nativeSeed) throws ReflectiveOperationException {
            int generation = artwork instanceof BitmapDrawable d && !d.getBitmap().isRecycled() ? d.getBitmap().getGenerationId() : 0;
            if (colored && artwork == lastArtwork && generation == lastGeneration && java.util.Objects.equals(nativeSeed, lastNativeSeed)) return;
            lastNativeSeed = nativeSeed;
            lastArtwork = artwork; lastGeneration = generation;
            int next = seed;
            if (nativeSeed != null) next = nativeSeed;
            else if (artwork instanceof ColorDrawable d) next = d.getColor();
            else if (artwork instanceof BitmapDrawable d) {
                Bitmap b = d.getBitmap();
                if (!b.isRecycled() && b.getConfig() != Bitmap.Config.HARDWARE) {
                    long red = 0, green = 0, blue = 0;
                    for (int y = 1; y <= 5; y++) for (int x = 1; x <= 5; x++) {
                        int c = b.getPixel(b.getWidth() * x / 6, b.getHeight() * y / 6);
                        red += Color.red(c); green += Color.green(c); blue += Color.blue(c);
                    }
                    next = Color.rgb((int) red / 25, (int) green / 25, (int) blue / 25);
                }
            }
            if (colored && seed == next) return;
            seed = next; colored = true;
            int[] colors = {seed, seed, seed};
            if (api.palette != null) try {
                colors[0] = (Integer) api.palette.invoke(null, seed, "primary", 12);
                colors[1] = (Integer) api.palette.invoke(null, seed, "primary", 10);
                colors[2] = (Integer) api.palette.invoke(null, seed, "tertiary", 12);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                colors[0] = colors[1] = colors[2] = seed;
            }
            currentColors = colors;
            if (effect != null) api.colors.invoke(effect, seed | 0xff000000, colors); // Native shader multiplies the entire glow by the main-color alpha.
        }
        void updateGeometry(View bg) throws ReflectiveOperationException {
            ViewGroup.LayoutParams source = bg.getLayoutParams();
            if (getLayoutParams() == null) {
                Constructor<?> copy = PARAMS.get(source.getClass());
                Map<String, Field> fields = FIELDS.get(source.getClass());
                if (copy == null || !fields.containsKey("startToStart")) { failed = true; return; }
                ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) copy.newInstance(0, 0);
                params.width = params.height = 0;
                for (String name : new String[]{"leftToLeft", "leftToRight", "rightToLeft", "rightToRight",
                        "startToStart", "startToEnd", "endToStart", "endToEnd", "topToTop", "topToBottom",
                        "bottomToTop", "bottomToBottom", "baselineToBaseline", "baselineToTop", "baselineToBottom"}) {
                    Field field = fields.get(name); if (field != null) field.setInt(params, -1);
                }
                for (String name : new String[]{"startToStart", "endToEnd", "topToTop", "bottomToBottom"}) fields.get(name).setInt(params, bg.getId());
                Field ratio = fields.get("dimensionRatio"); if (ratio != null) ratio.set(params, null);
                if (params instanceof ViewGroup.MarginLayoutParams margins) {
                    margins.setMargins(0,0,0,0); margins.setMarginStart(0); margins.setMarginEnd(0);
                }
                setLayoutParams(params);
            }
            float next = Math.max(0, ModuleSettings.notificationRadius) * getResources().getDisplayMetrics().density;
            if (radius != next) { radius = next; invalidateOutline(); }
        }
        void update(View bg) {
            try { updateGeometry(bg); }
            catch (ReflectiveOperationException | RuntimeException error) { failed = true; }
            setVisibility(ModuleSettings.inFullAod || !playing ? View.GONE : View.VISIBLE);
            sync();
        }
        void sync() {
            if (syncing) return;
            boolean desired = enabled() && !ModuleSettings.inFullAod && !failed && playing && isAttachedToWindow() && isShown();
            if (desired == running) return;
            syncing = true;
            try {
                if (desired) {
                    if (effect == null) {
                        effect = (View) api.constructor.newInstance(getContext());
                        addView(effect, new FrameLayout.LayoutParams(-1, -1));
                        if (currentColors != null) api.colors.invoke(effect, seed | 0xff000000, currentColors);
                    }
                    api.start.invoke(effect); api.resume.invoke(effect);
                }
                else api.stop.invoke(effect);
                running = desired;
            } catch (ReflectiveOperationException | RuntimeException ignored) { failed = true; running = false; }
            finally { syncing = false; }
        }
        @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); if (api != null) sync(); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); sync(); }
        private void stopNow() {
            if (!running) return;
            try { api.stop.invoke(effect); } catch (ReflectiveOperationException | RuntimeException ignored) { }
            running = false;
        }
        @Override protected void onDetachedFromWindow() { stopNow(); super.onDetachedFromWindow(); }
        void dispose() {
            stopNow();
            if (getParent() instanceof ViewGroup parent) parent.removeView(this);
        }
    }
}
