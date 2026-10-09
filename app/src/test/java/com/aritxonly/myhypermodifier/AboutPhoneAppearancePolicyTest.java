package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class AboutPhoneAppearancePolicyTest {
    @Test public void storageLabelRemovesOnlyTheExpansionSuffix() {
        assertEquals("426.8GB/528GB", AboutPhoneAppearancePolicy.storageLabel("426.8GB/528GB（扩容16GB）"));
        assertEquals("426.8GB/528GB", AboutPhoneAppearancePolicy.storageLabel("426.8GB/528GB  (扩容 16GB)"));
        assertEquals("172 GB/512 GB", AboutPhoneAppearancePolicy.storageLabel("172 GB/512 GB"));
        assertEquals("512 GB（其他信息）", AboutPhoneAppearancePolicy.storageLabel("512 GB（其他信息）"));
        assertEquals("", AboutPhoneAppearancePolicy.storageLabel(null));
        String clean = AboutPhoneAppearancePolicy.storageLabel("1GB/2GB（扩容1GB）");
        assertEquals(clean, AboutPhoneAppearancePolicy.storageLabel(clean));
    }

    @Test public void ringUsesUsedStorageAndHandlesImpossibleMeasurements() {
        assertEquals(.75f, AboutPhoneAppearancePolicy.storageFraction(512, 128), 0f);
        assertEquals(0f, AboutPhoneAppearancePolicy.storageFraction(0, 128), 0f);
        assertEquals(0f, AboutPhoneAppearancePolicy.storageFraction(512, 600), 0f);
        assertEquals(1f, AboutPhoneAppearancePolicy.storageFraction(512, -1), 0f);
    }

}
