package com.aritxonly.myhypermodifier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class HyperMusicCoverApkSourceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File apk(String name, String entry) throws Exception {
        File file = temp.newFile(name);
        try (ZipOutputStream zip = new ZipOutputStream(new java.io.FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("META-INF/xposed/java_init.list"));
            zip.write((entry + "\n").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return file;
    }
    @Test public void loadedApkIsAvailableBeforeAnyClockViewOrContextExists() throws Exception {
        File apk = apk("hmc.apk", "com.os4.musiccover.Main");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{apk.toURI().toURL()}, null)) {
            assertEquals(apk.getAbsolutePath(), HyperMusicCoverApkSource.loadedApk(loader, "com.os4.musiccover.Main"));
        }
    }
    @Test public void parentModuleArchiveDoesNotReplaceHmcArchiveAndEscapedPathIsDecoded() throws Exception {
        File other = apk("other.apk", "com.aritxonly.myhypermodifier.MyHyperModifier");
        File hmc = apk("HMC release with spaces.apk", "com.os4.musiccover.Main");
        try (URLClassLoader parent = new URLClassLoader(new URL[]{other.toURI().toURL()}, null);
             URLClassLoader loader = new URLClassLoader(new URL[]{hmc.toURI().toURL()}, parent)) {
            assertEquals(hmc.getAbsolutePath(), HyperMusicCoverApkSource.loadedApk(loader, "com.os4.musiccover.Main"));
        }
    }
    @Test public void loaderWithoutArchiveDefersRatherThanAssumingAContainer() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[0], null)) {
            assertNull(HyperMusicCoverApkSource.loadedApk(loader, "com.os4.musiccover.Main"));
            assertNull(HyperMusicCoverApkSource.dexApk(loader, "com.os4.musiccover.Main"));
        }
    }
}
