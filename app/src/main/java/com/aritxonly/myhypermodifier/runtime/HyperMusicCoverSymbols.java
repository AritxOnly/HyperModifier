package com.aritxonly.myhypermodifier;

import android.content.Context;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Metadata is resolved on a worker once; hot hooks only use cached typed members. */
final class HyperMusicCoverSymbols {
    private static volatile Class<?> resolvedMain;
    private static volatile HyperMusicCoverDexSymbols resolved;
    private HyperMusicCoverSymbols() { }
    static boolean split(Class<?> main) {
        try { main.getDeclaredMethod("glyphUnit", android.graphics.RectF.class); return false; }
        catch (NoSuchMethodException missing) { return true; }
    }
    static HyperMusicCoverDexSymbols cached(Class<?> main) { return resolvedMain == main ? resolved : null; }
    static final class SourcePending extends Exception {
        SourcePending() { super("HMC source not yet available; waiting for clock attachment"); }
    }
    static void load(Class<?> main, Context fallback) throws Exception {
        String apk = null;
        try { apk = HyperMusicCoverApkSource.loadedApk(main.getClassLoader(), main.getName()); }
        catch (IOException unavailableResources) { /* DexPathList or host application may still be ready. */ }
        if (apk == null) apk = HyperMusicCoverApkSource.dexApk(main.getClassLoader(), main.getName());
        if (apk == null) {
            Context context = fallback;
            if (context == null) {
                try {
                    Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                    if (app instanceof Context) context = (Context) app;
                } catch (ReflectiveOperationException unavailableApplication) { /* Defer until attachment. */ }
            }
            if (context == null) throw new SourcePending();
            apk = context.getPackageManager().getApplicationInfo("com.os4.musiccover", 0).sourceDir;
        }
        HyperMusicCoverDexSymbols symbols = HyperMusicCoverDexSymbols.read(apk);
        resolved = symbols; resolvedMain = main;
    }
    static Method method(Class<?> main, HyperMusicCoverDexSymbols symbols, String role,
                         Class<?> result, Class<?>... args) throws ReflectiveOperationException {
        HyperMusicCoverDexSymbols.Member member = member(symbols, role);
        return HyperMusicCoverAdapter.method(owner(main, member), member.name, result, args);
    }
    static Field field(Class<?> main, HyperMusicCoverDexSymbols symbols, String role, Class<?> type)
            throws ReflectiveOperationException {
        HyperMusicCoverDexSymbols.Member member = member(symbols, role);
        Field f = owner(main, member).getDeclaredField(member.name);
        if (f.getType() != type || !Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()))
            throw new NoSuchFieldException("HMC " + role + " type changed");
        f.setAccessible(true); return f;
    }
    private static Class<?> owner(Class<?> main, HyperMusicCoverDexSymbols.Member member) throws ClassNotFoundException {
        String descriptor = member.owner;
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) throw new ClassNotFoundException(descriptor);
        return Class.forName(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), false, main.getClassLoader());
    }
    private static HyperMusicCoverDexSymbols.Member member(HyperMusicCoverDexSymbols symbols, String role)
            throws NoSuchMethodException {
        if (symbols == null) throw new NoSuchMethodException("HMC split API metadata not ready: " + role);
        HyperMusicCoverDexSymbols.Member member = symbols.members.get(role);
        if (member == null) throw new NoSuchMethodException("HMC split API missing: " + role);
        return member;
    }
}
