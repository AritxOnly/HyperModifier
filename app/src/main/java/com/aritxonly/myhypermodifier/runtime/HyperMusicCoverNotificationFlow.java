package com.aritxonly.myhypermodifier;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Extra combine input makes expansion/collapse emit through SystemUI's own coroutine. */
final class HyperMusicCoverNotificationFlow {
    private final WeakHashMap<Object, Boolean> attached = new WeakHashMap<>();
    private final List<WeakReference<Object>> policies = new ArrayList<>();
    private int lastMode = Integer.MIN_VALUE;
    private String lastInputs;
    private final String flowPackage;

    HyperMusicCoverNotificationFlow(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> transform = findTransform(loader);
        Class<?> combined = loader.loadClass(transform.getName().substring(0, transform.getName().length() - 2));
        Class<?> flow = null;
        for (Field field : combined.getDeclaredFields())
            if (field.getType().isArray() && field.getType().getComponentType().isInterface()) {
                flow = field.getType().getComponentType(); break;
            }
        if (flow == null) throw new NoSuchFieldException("notification Flow[] source");
        flowPackage = flow.getPackage().getName(); // Host name: R8 cannot rewrite it to our Kotlin copy.
        Method invoke = null;
        for (Method method : transform.getDeclaredMethods())
            if ("invoke".equals(method.getName()) && method.getParameterCount() == 3) { invoke = method; break; }
        if (invoke == null) throw new NoSuchMethodException("notification transform invoke(3)");
        module.hook(invoke).setId("hmc-extension-notification-inputs")
                .setPriority(XposedInterface.PRIORITY_LOWEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length == 3 && args[1] instanceof Object[] && HyperMusicCoverExtensions.bound()) {
                        HyperMusicCoverExtensions.refreshNow();
                        Object[] source = (Object[]) args[1];
                        Object[] effective = HyperMusicCoverExtensionPolicy.notificationInputs(source,
                                HyperMusicCoverExtensions.expanded(), HyperMusicCoverExtensions.hmcAvoidance(),
                                ModuleSettings.sinkLockscreenNotificationsForFingerprint);
                        recordInputs(module, source, effective);
                        args[1] = effective;
                        return chain.proceed(args);
                    }
                    return chain.proceed();
                });
        module.deoptimize(invoke);
        for (Method method : combined.getDeclaredMethods()) if ("collect".equals(method.getName())) {
            module.hook(method).setId("hmc-extension-notification-collect")
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        attach(chain.getThisObject()); return chain.proceed();
                    });
            module.deoptimize(method);
        }
        Class<?> sharing = loader.loadClass(flowPackage + ".FlowKt__ShareKt$launchSharing$1");
        Field upstream = sharing.getDeclaredField("$upstream"); upstream.setAccessible(true);
        for (Method method : sharing.getDeclaredMethods()) if ("invokeSuspend".equals(method.getName())) {
            module.hook(method).setId("hmc-extension-notification-sharing")
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object source = upstream.get(chain.getThisObject());
                        if (combined.isInstance(source)) attach(source);
                        return chain.proceed();
                    });
        }
        for (Method method : sharing.getDeclaredMethods()) module.deoptimize(method);
        Class<?> combineKt = loader.loadClass(flowPackage + ".internal.CombineKt");
        for (Method method : combineKt.getDeclaredMethods()) if ("combineInternal".equals(method.getName())
                && method.getParameterCount() == 5) {
            module.hook(method).setId("hmc-extension-notification-combine")
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (transform.isInstance(args[2]) && args[4] instanceof Object[]) {
                            Object[] sources = attach(args[1]);
                            if (sources != null) args[4] = sources;
                            return chain.proceed(args);
                        }
                        return chain.proceed();
                    });
            module.deoptimize(method);
        }
        HookDiagnostics.available("com.android.systemui", "HMC 展开通知下沉");
    }
    private synchronized void recordInputs(XposedModule module, Object[] source, Object[] effective) {
        if (source.length < 7) return;
        String snapshot = "stack=" + HyperMusicCoverStackState.current()
                + " auto=" + HyperMusicCoverExtensions.expanded()
                + " hmc=" + HyperMusicCoverExtensions.hmcAvoidance()
                + " global=" + ModuleSettings.sinkLockscreenNotificationsForFingerprint
                + " source=" + source[5] + "/" + source[6]
                + " effective=" + effective[5] + "/" + effective[6];
        if (snapshot.equals(lastInputs)) return;
        lastInputs = snapshot;
        module.log(android.util.Log.INFO, "MyHyperModifier", "HMC notification policy: " + snapshot);
    }
    private synchronized Object[] attach(Object factory) throws ReflectiveOperationException {
        ModuleSettings.ensureLoaded();
        if (!ModuleSettings.hmcExpandedFingerprint && !attached.containsKey(factory)) return null;
        for (Field field : factory.getClass().getDeclaredFields()) {
            if (!field.getType().isArray() || !field.getType().getComponentType().isInterface()) continue;
            field.setAccessible(true);
            Object[] original = (Object[]) field.get(factory);
            if (original == null) continue;
            if (attached.containsKey(factory)) return original;
            // Seven OEM inputs; eight when recent HMC already appended its own policy.
            if (original.length != 7 && original.length != 8) continue;
            Class<?> flow = field.getType().getComponentType();
            Object policy = flow.getClassLoader().loadClass(flowPackage + ".StateFlowKt")
                    .getMethod("MutableStateFlow", Object.class).invoke(null, effectiveMode());
            Object[] sources = (Object[]) Array.newInstance(flow, original.length + 1);
            System.arraycopy(original, 0, sources, 0, original.length); sources[original.length] = policy;
            field.set(factory, sources); attached.put(factory, true); policies.add(new WeakReference<>(policy));
            return sources;
        }
        return null;
    }
    private int effectiveMode() {
        if (!HyperMusicCoverExtensions.bound()) return 0;
        int base = HyperMusicCoverExtensions.hmcAvoidance();
        return HyperMusicCoverExtensions.expanded() ? 1 : base != 0 ? base
                : ModuleSettings.sinkLockscreenNotificationsForFingerprint ? 1 : 0;
    }
    synchronized void checkAttached() {
        if (ModuleSettings.hmcExpandedFingerprint && policies.isEmpty())
            HookDiagnostics.unavailable("com.android.systemui", "HMC 展开通知下沉",
                    "未捕获首次通知 Flow 收集，请启用扩展后重启系统界面");
    }
    synchronized void refresh() throws ReflectiveOperationException {
        int mode = effectiveMode();
        if (mode == lastMode) return;
        lastMode = mode;
        for (int i = policies.size() - 1; i >= 0; i--) {
            Object policy = policies.get(i).get();
            if (policy == null) { policies.remove(i); continue; }
            Method setter = policy.getClass().getMethod("setValue", Object.class);
            setter.setAccessible(true); setter.invoke(policy, mode);
        }
    }
    /** Search the OEM DEX only; the lambda number varies between SystemUI builds. */
    private static Class<?> findTransform(ClassLoader loader) throws ReflectiveOperationException {
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            Field paths;
            try { paths = field(current.getClass(), "pathList"); }
            catch (NoSuchFieldException missing) { continue; }
            Object pathList = paths.get(current);
            Object[] elements = (Object[]) field(pathList.getClass(), "dexElements").get(pathList);
            for (Object element : elements) {
                Object dex = field(element.getClass(), "dexFile").get(element);
                if (dex == null) continue;
                Enumeration<?> entries = (Enumeration<?>) dex.getClass().getMethod("entries").invoke(dex);
                while (entries.hasMoreElements()) {
                    String name = String.valueOf(entries.nextElement());
                    if (name.startsWith("com.android.keyguard.panel.KeyguardPanelViewController$")
                            && name.contains("nsslLockYPosition") && name.contains("inlined$combine")
                            && name.endsWith("$1$3")) return loader.loadClass(name);
                }
            }
        }
        throw new ClassNotFoundException("SystemUI notification bound combine");
    }
    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException missing) { /* Parent BaseDexClassLoader. */ }
        }
        throw new NoSuchFieldException(name);
    }
}
