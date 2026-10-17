package com.aritxonly.myhypermodifier;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Process;
import android.view.SurfaceControl;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/** Cached hidden-API plan. Caller supplies a worker thread and an app-owned video layer. */
final class NativeLayerCapture {
    interface Callback { void onResult(Bitmap bitmap, int status); }
    private static volatile Api api;
    static Object capture(SurfaceControl control, Rect crop, float scale, Callback callback) throws Exception {
        Api plan = api;
        if (plan == null) api = plan = resolve(); // One worker per host; stable metadata only.
        Object builder = plan.builder.newInstance(control);
        plan.crop.invoke(builder, crop);
        plan.scale.invoke(builder, scale);
        plan.uid.invoke(builder, (long) Process.myUid());
        Object args = plan.build.invoke(builder);
        final Api cached = plan;
        ObjIntConsumer<Object> consumer = (result, status) -> {
            Bitmap bitmap = null;
            if (status == 0 && result != null) {
                try {
                    HardwareBuffer buffer = (HardwareBuffer) cached.buffer.invoke(result);
                    try { bitmap = Bitmap.wrapHardwareBuffer(buffer, (ColorSpace) cached.color.invoke(result)); }
                    finally { buffer.close(); }
                } catch (ReflectiveOperationException | RuntimeException error) { status = -1; }
            }
            callback.onResult(bitmap, status);
        };
        Object sink = plan.listener.getParameterTypes()[0] == ObjIntConsumer.class
                ? consumer : (Consumer<Object>) result -> consumer.accept(result, 0);
        Object listener = plan.listener.newInstance(sink);
        int status = (int) plan.capture.invoke(null, args, listener);
        if (status != 0) callback.onResult(null, status);
        return listener;
    }
    private static Api resolve() throws ReflectiveOperationException {
        if (Build.VERSION.SDK_INT < 34) return new Api(SurfaceControl.class);
        try { return new Api(Class.forName("android.window.ScreenCaptureInternal")); }
        catch (ReflectiveOperationException ignored) { return new Api(Class.forName("android.window.ScreenCapture")); }
    }
    private static final class Api {
        final Constructor<?> builder, listener;
        final Method crop, scale, uid, build, capture, buffer, color;
        Api(Class<?> captureClass) throws ReflectiveOperationException {
            String prefix = captureClass.getName();
            Class<?> builderClass = Class.forName(prefix + "$LayerCaptureArgs$Builder");
            Class<?> base = Class.forName(prefix + "$CaptureArgs$Builder");
            Class<?> args = Class.forName(prefix + "$LayerCaptureArgs");
            Class<?> listenerClass = Class.forName(prefix + "$ScreenCaptureListener");
            Class<?> result = Class.forName(prefix + "$ScreenshotHardwareBuffer");
            builder = builderClass.getConstructor(SurfaceControl.class);
            crop = base.getMethod("setSourceCrop", Rect.class);
            scale = base.getMethod("setFrameScale", float.class);
            uid = base.getMethod("setUid", long.class);
            build = builderClass.getMethod("build");
            Constructor<?> found;
            try { found = listenerClass.getConstructor(ObjIntConsumer.class); }
            catch (NoSuchMethodException ignored) { found = listenerClass.getConstructor(Consumer.class); }
            listener = found;
            capture = captureClass.getMethod("captureLayers", args, listenerClass);
            buffer = result.getMethod("getHardwareBuffer");
            color = result.getMethod("getColorSpace");
        }
    }
}
