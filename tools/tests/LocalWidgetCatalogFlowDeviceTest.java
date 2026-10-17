package com.aritxonly.myhypermodifier;

import dalvik.system.PathClassLoader;
import java.lang.reflect.*;
import java.util.Collections;

/** Runs in a separate app_process; args[0] is the installed assistant APK path.
 * Compile alongside runtime/LocalWidgetCatalogFlow.java and LocalWidgetCatalogModels.java,
 * then dex the generated classes together.
 * Does not install the module, hook or restart the assistant, or make network requests.
 */
public final class LocalWidgetCatalogFlowDeviceTest {
    public static void main(String[] args) throws Throwable {
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> flow = Class.forName("kotlinx.coroutines.flow.d", false, loader);
        Class<?> collector = Class.forName("kotlinx.coroutines.flow.e", false, loader);
        Class<?> singleton = Class.forName("kotlinx.coroutines.flow.q", false, loader);
        Class<?> continuation = Class.forName("kotlin.coroutines.e", false, loader);
        Class<?> wrapper = Class.forName("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWrapper", false, loader);
        Class<?> detail = Class.forName("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponse", false, loader);
        Class<?> tip = Class.forName("com.miui.personalassistant.picker.business.detail.bean.PickerDetailTipStr", false, loader);
        Object old = wrapper.getConstructor(java.util.List.class, String.class, String.class, String.class, int.class)
                .newInstance(Collections.emptyList(), "org.example", "", "Example", 0);
        Object originalPay = wrapper.getMethod("isPay").invoke(old);
        Object originalPrice = wrapper.getMethod("getPriceInCent").invoke(old);
        LocalWidgetCatalogModels models = new LocalWidgetCatalogModels(wrapper, detail, tip);
        Object payload = models.wrap(Collections.emptyList(), "org.example", "Example");
        require(Boolean.FALSE.equals(wrapper.getMethod("isPay").invoke(payload)), "local AppWidgets must be free");
        require(Long.valueOf(0).equals(wrapper.getMethod("getPriceInCent").invoke(payload)), "local price must be non-null zero");
        require(!booleanField(wrapper, payload, "isBought"), "local widgets must not fabricate a purchase flag");
        require(wrapper.getMethod("getInstallInfo").invoke(payload) != null, "installInfo required by UI");
        require(wrapper.getMethod("getMamlsNeedDownload").invoke(payload) != null, "download list required by UI");
        Class<?> widget = Class.forName("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWidget", false, loader);
        Object widgetData = widget.getConstructor(String.class, String.class, String.class, String.class, String.class,
                int.class, int.class, int.class, java.util.List.class, int.class, boolean.class, android.os.Bundle.class)
                .newInstance("org.example.Widget", "Widget", "", "", "", 0, 2, 2, Collections.emptyList(), 1, false, null);
        Object row = detail.getConstructor(String.class, String.class, int.class, int.class, int.class,
                String.class, String.class, String.class, String.class, int.class,
                String.class, String.class, String.class, String.class, String.class, tip,
                Class.forName("com.miui.personalassistant.picker.business.detail.bean.PickerDetailSupperAppInfo", false, loader),
                widget, Class.forName("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseMaml", false, loader),
                Long.class, Class.forName("miui.drm.DrmManager$DrmResult", false, loader))
                .newInstance("mhm-local-widget:org.example/org.example.Widget", "", 1, 1, 1, "Example", "", "org.example", "", 0,
                        "", "", "", "", "", tip.getConstructor(String.class, String.class).newInstance("", ""),
                        null, widgetData, null, Long.valueOf(0), null);
        models.prepareLocalDetail(row);
        require(Boolean.FALSE.equals(detail.getMethod("isPay").invoke(row)), "local row must be free");
        require(Boolean.TRUE.equals(detail.getMethod("isAuthorityPass").invoke(row)), "installed local AppWidget ready to add");
        require(!booleanField(detail, row, "isBought"), "no fabricated purchase state");
        require(java.util.Objects.equals(originalPay, wrapper.getMethod("isPay").invoke(old))
                && java.util.Objects.equals(originalPrice, wrapper.getMethod("getPriceInCent").invoke(old)),
                "existing remote model must stay untouched across assistant versions");
        LocalWidgetCatalogFlow adapter = new LocalWidgetCatalogFlow(singleton, flow, collector, continuation);
        Object source = singleton.getConstructor(Object.class).newInstance((Object) null);
        Object mapped = adapter.map(source, value -> payload);
        require(flow.isInstance(mapped), "repository result must remain a Flow");
        Object unit = Class.forName("kotlin.h", false, loader).getField("a").get(null);
        Object suspended = Class.forName("kotlin.coroutines.intrinsics.CoroutineSingletons", false, loader)
                .getField("COROUTINE_SUSPENDED").get(null);
        Object context = Class.forName("kotlin.coroutines.EmptyCoroutineContext", false, loader)
                .getField("INSTANCE").get(null);
        Object caller = Proxy.newProxyInstance(loader, new Class<?>[]{continuation}, (proxy, method, arguments) -> {
            if (method.getName().equals("getContext")) return context;
            throw new AssertionError("native collect must retain the caller continuation");
        });
        Method collect = flow.getMethod("d", collector, continuation);
        for (Object emitResult : new Object[]{unit, suspended}) {
            int[] emissions = {0};
            Object receiver = Proxy.newProxyInstance(loader, new Class<?>[]{collector}, (proxy, method, arguments) -> {
                require(method.getName().equals("emit"), "unexpected collector method");
                require(arguments[0] == payload && arguments[1] == caller, "payload/continuation identity");
                emissions[0]++;
                return emitResult;
            });
            require(collect.invoke(mapped, receiver, caller) == emitResult && emissions[0] == 1,
                    "native collect immediate/suspended contract");
        }
        IllegalStateException error = new IllegalStateException("collector failure");
        Object failing = Proxy.newProxyInstance(loader, new Class<?>[]{collector}, (proxy, method, arguments) -> { throw error; });
        try {
            collect.invoke(mapped, failing, caller);
            throw new AssertionError("collector failure was swallowed");
        } catch (InvocationTargetException expected) { require(expected.getCause() == error, "collector failure changed"); }
        Object failure = Class.forName("kotlin.Result$Failure", false, loader).getConstructor(Throwable.class)
                .newInstance(new java.util.concurrent.CancellationException("test"));
        require(adapter.map(failure, value -> { throw new AssertionError("must not merge cancellation"); }) == failure,
                "cancellation passthrough");
        Object foreign = Proxy.newProxyInstance(loader, new Class<?>[]{flow}, (proxy, method, arguments) -> unit);
        require(adapter.map(foreign, value -> { throw new AssertionError("must not inspect foreign flow"); }) == foreign,
                "foreign flow passthrough");
        require(adapter.map(mapped, value -> value) == mapped, "no-op must retain native Flow");
        require(singleton.getField("a").get(source) == null, "source Flow was mutated");
        System.out.println("PASS: native local detail free/price/authority state, existing model untouched across versions; Flow payload, collect, suspension, exception, cancellation and foreign-flow contracts");
    }
    private static boolean booleanField(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.getBoolean(target);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
