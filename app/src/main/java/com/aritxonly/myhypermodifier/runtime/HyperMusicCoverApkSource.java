package com.aritxonly.myhypermodifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Find the APK of the live module loader without requiring any SystemUI View or Context. */
final class HyperMusicCoverApkSource {
    private static final String ENTRY = "META-INF/xposed/java_init.list";
    private HyperMusicCoverApkSource() { }
    static String loadedApk(ClassLoader loader, String entryClass) throws IOException {
        if (loader == null) return null;
        Enumeration<URL> resources = loader.getResources(ENTRY);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (!"jar".equals(url.getProtocol())) continue;
            try {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                connection.setUseCaches(false);
                URL archive = connection.getJarFileURL();
                if (!"file".equals(archive.getProtocol())) continue;
                try (InputStream stream = connection.getInputStream()) {
                    if (declares(stream, entryClass)) return new java.io.File(archive.toURI()).getAbsolutePath();
                }
            } catch (java.net.URISyntaxException | IOException ignored) {
                // A stale parent resource must not prevent trying the live module's own APK.
            }
        }
        return null;
    }
    static String dexApk(ClassLoader loader, String entryClass) {
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            try {
                Object paths = field(current.getClass(), "pathList").get(current);
                Object[] elements = (Object[]) field(paths.getClass(), "dexElements").get(paths);
                for (Object element : elements) {
                    Object dex = field(element.getClass(), "dexFile").get(element);
                    if (dex == null) continue;
                    Object name = dex.getClass().getMethod("getName").invoke(dex);
                    if (!(name instanceof String) || !((String) name).endsWith(".apk")) continue;
                    String path = (String) name;
                    try (ZipFile zip = new ZipFile(path)) {
                        ZipEntry entry = zip.getEntry(ENTRY);
                        if (entry == null) continue;
                        try (InputStream stream = zip.getInputStream(entry)) {
                            if (declares(stream, entryClass)) return path;
                        }
                    } catch (IOException ignored) { /* Try another loader-owned archive. */ }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Custom LSPosed/in-memory loaders may not expose DexPathList at all.
            }
        }
        return null;
    }
    private static boolean declares(InputStream stream, String entryClass) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (String line; (line = reader.readLine()) != null;) if (entryClass.equals(line.trim())) return true;
        return false;
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException missing) { /* The path list may be inherited. */ }
        }
        throw new NoSuchFieldException(name);
    }
}
