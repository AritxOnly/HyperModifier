package com.aritxonly.myhypermodifier;

/** Runs the production resolver against an official APK without Android or apktool. */
public final class HyperMusicCoverDexCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        for (String apk : args) {
            long start = System.nanoTime();
            try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                    new java.net.URL[]{new java.io.File(apk).toURI().toURL()}, null)) {
                String source = HyperMusicCoverApkSource.loadedApk(loader, "com.os4.musiccover.Main");
                if (source == null || !new java.io.File(source).getCanonicalFile().equals(new java.io.File(apk).getCanonicalFile()))
                    throw new IllegalStateException("HMC APK resource could not be located without a View: " + apk);
                System.out.println("  loader-owned APK source PASS (no clock View/Context)");
            }
            HyperMusicCoverDexSymbols symbols = HyperMusicCoverDexSymbols.read(apk);
            System.out.println(apk + " PASS " + symbols.members.size() + " roles " + (System.nanoTime() - start) / 1000000 + "ms");
            symbols.members.forEach((role, member) -> System.out.println("  " + role + "=" + member));
        }
    }
}
