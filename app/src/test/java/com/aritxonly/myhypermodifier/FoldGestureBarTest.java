package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class FoldGestureBarTest {
    public static final class Injector {
        public Context context;
        public boolean hide;
        public boolean gestures = true;
        public Handler handler = new Handler(Looper.getMainLooper());
    }

    @After public void reset() throws Exception {
        ModuleSettings.foldInnerHideGestureBar = false;
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        Field hidden = FoldGestureBarHooks.class.getDeclaredField("hidden");
        hidden.setAccessible(true);
        hidden.setBoolean(null, false);
    }

    private Object binding(Injector target) throws Exception {
        Class<?> lookup = Class.forName(FoldGestureBarHooks.class.getName() + "$OwnerLookup");
        Class<?> type = Class.forName(FoldGestureBarHooks.class.getName() + "$Binding");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object result = constructor.newInstance(Injector.class.getField("context"),
                Injector.class.getField("hide"), Injector.class.getField("gestures"),
                Injector.class.getField("handler"), Proxy.newProxyInstance(
                        lookup.getClassLoader(), new Class<?>[]{lookup}, (p, m, a) -> target));
        Field owner = type.getDeclaredField("owner");
        owner.setAccessible(true);
        owner.set(result, new WeakReference<>(target));
        return result;
    }

    private void apply(Object binding, Injector target, boolean inner, boolean stock) throws Exception {
        Method apply = binding.getClass().getDeclaredMethod("apply", Object.class, boolean.class, boolean.class);
        apply.setAccessible(true);
        apply.invoke(binding, target, inner, stock);
    }

    @Test public void unfoldingRemovesWindowAndFoldingRestoresLatestStockChoice() throws Exception {
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.foldInnerHideGestureBar = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        Injector target = new Injector();
        Object binding = binding(target);
        apply(binding, target, true, false);
        assertTrue(target.hide);
        assertTrue(FoldGestureBarHooks.hidden());
        assertTrue(target.handler.hasMessages(1));
        apply(binding, target, false, false);
        assertFalse(target.hide);
        assertFalse(FoldGestureBarHooks.hidden());
        assertTrue(target.handler.hasMessages(0));
        apply(binding, target, true, true);
        apply(binding, target, false, true);
        assertTrue(target.hide); // A system-hidden outer hint stays hidden.
    }

    @Test public void compatibilityGateBeatsQueuedHideAndButtonsRemainAvailable() throws Exception {
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.foldInnerHideGestureBar = true;
        Injector target = new Injector();
        Object binding = binding(target);
        apply(binding, target, true, false);
        ModuleSettings.systemUiCompatibilityMode = true;
        apply(binding, target, true, false);
        assertFalse(target.hide);
        assertFalse(FoldGestureBarHooks.hidden());
        ModuleSettings.systemUiCompatibilityMode = false;
        target.gestures = false;
        apply(binding, target, true, false);
        assertFalse(FoldGestureBarHooks.hidden());
        assertFalse(target.hide);
    }
}
