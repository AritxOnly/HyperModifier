package com.aritxonly.myhypermodifier;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForeignLazyAccessTest {
    @Test public void systemUiLazyFromSeparateKotlinRuntimeCannotBeCastButCanBeRead() throws Exception {
        URL kotlinRuntime = kotlin.Lazy.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader systemUi = new URLClassLoader(new URL[] {kotlinRuntime}, null)) {
            Class<?> foreignLazy = Class.forName("kotlin.Lazy", true, systemUi);
            assertNotSame(kotlin.Lazy.class, foreignLazy);
            Object statusController = new Object();
            Object delegate = Proxy.newProxyInstance(systemUi, new Class<?>[] {foreignLazy},
                    (proxy, method, args) -> method.getName().equals("getValue") ? statusController
                            : method.getName().equals("isInitialized") ? true : null);
            // The previous `as? Lazy<*>` quietly returned null here, skipping the controller.
            assertFalse(delegate instanceof kotlin.Lazy);
            assertSame(statusController, new ForeignLazyAccess(foreignLazy).value(delegate));
        }
    }
    @Test public void uninitializedNullableDelegateIsDistinctFromClassLoaderFailure() throws Exception {
        assertNull(new ForeignLazyAccess(kotlin.Lazy.class).value(null));
    }
}
