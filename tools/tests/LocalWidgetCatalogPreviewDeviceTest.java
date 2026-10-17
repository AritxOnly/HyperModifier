package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.net.Uri;
import dalvik.system.PathClassLoader;
import java.lang.reflect.*;

/** Separate app_process test using installed assistant decoders and Deadliner resources.
 * Compile with LocalWidgetCatalogPolicy.java. Does not allocate/bind widgets or launch apps.
 */
public final class LocalWidgetCatalogPreviewDeviceTest {
    public static void main(String[] args) throws Throwable {
        android.os.Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Context context = new ContextWrapper(system.createPackageContext("com.miui.personalassistant", 0)) {
            @Override public Context getApplicationContext() { return this; }
        };
        int icon = context.getPackageManager().getApplicationInfo("com.aritxonly.deadliner", 0).icon;
        require(icon != 0, "Deadliner application icon missing");
        String uri = LocalWidgetCatalogPolicy.previewUri("com.aritxonly.deadliner", 0, 0, icon);
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> drawableDecoder = Class.forName("s2.e", false, loader);
        Object drawable = drawableDecoder.getConstructor(Context.class).newInstance(context);
        Class<?> poolType = Class.forName("com.bumptech.glide.load.engine.bitmap_recycle.d", false, loader);
        Object pool = Class.forName("com.bumptech.glide.load.engine.bitmap_recycle.j", false, loader).getConstructor(long.class).newInstance(0L);
        Class<?> decoderType = Class.forName("com.bumptech.glide.load.resource.bitmap.v", false, loader);
        Object decoder = decoderType.getConstructor(drawableDecoder, poolType).newInstance(drawable, pool);
        Class<?> optionsType = Class.forName("j2.e", false, loader);
        Method decode = decoderType.getMethod("a", Object.class, int.class, int.class, optionsType);
        Class<?> resourceType = Class.forName("com.bumptech.glide.load.engine.s", false, loader);
        Class<?> imageType = Class.forName("com.miui.personalassistant.image.ObserveGlideLoadStatusImageView", false, loader);
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Object unsafe = null;
        for (Field field : unsafeType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == unsafeType) {
                field.setAccessible(true); unsafe = field.get(null); break;
            }
        }
        // This listener only writes its load-success field. No fake View is attached/rendered.
        Object image = unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, imageType);
        Field ready = imageType.getDeclaredField("a"); ready.setAccessible(true);
        require(!ready.getBoolean(image), "initial load gate");
        for (String candidate : new String[]{uri, LocalWidgetCatalogPolicy.previewUri("unused", 0, 0, 0)}) {
            Object result = decode.invoke(decoder, Uri.parse(candidate), 256, 256, optionsType.getConstructor().newInstance());
            require(result != null, "native resource bitmap decoder returned null: " + candidate);
            Bitmap bitmap = (Bitmap) resourceType.getMethod("get").invoke(result);
            require(bitmap.getWidth() > 0 && bitmap.getHeight() > 0, "decoded bitmap empty");
            imageType.getMethod("b", Object.class).invoke(image, bitmap);
            require(ready.getBoolean(image), "native success listener did not release gate");
            Object allowed = Class.forName("d9.a", false, loader).getMethod("b", Context.class, android.view.View.class)
                    .invoke(null, context, image);
            require(Boolean.TRUE.equals(allowed), "actual DragHelper still blocks loaded preview");
            System.out.println("PASS: native bitmap decode + success callback + DragHelper gate: " + candidate);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
