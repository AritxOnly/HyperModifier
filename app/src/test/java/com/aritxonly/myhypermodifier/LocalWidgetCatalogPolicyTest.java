package com.aritxonly.myhypermodifier;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocalWidgetCatalogPolicyTest {
    @Test public void localPreviewUsesProviderResourceThenIconsAndNeverAnEmptyUrl() {
        assertEquals("android.resource://org.example/123", LocalWidgetCatalogPolicy.previewUri("org.example", 123, 456, 789));
        assertEquals("android.resource://org.example/456", LocalWidgetCatalogPolicy.previewUri("org.example", 0, 456, 789));
        assertEquals("android.resource://org.example/789", LocalWidgetCatalogPolicy.previewUri("org.example", 0, 0, 789));
        assertTrue(LocalWidgetCatalogPolicy.previewUri("org.example", 0, 0, 0).startsWith("android.resource://android/"));
    }
    @Test public void cloudIdentifiersCannotSelectLocalDetails() {
        assertFalse(LocalWidgetCatalogPolicy.local(null));
        assertFalse(LocalWidgetCatalogPolicy.local("component/store/app"));
        assertTrue(LocalWidgetCatalogPolicy.local(LocalWidgetCatalogPolicy.PREFIX + "a.b/C"));
    }
    @Test public void stockStylesPreserveOrientationAndUnsupportedSizesAreExplicit() {
        assertEquals(6, LocalWidgetCatalogPolicy.style(2, 1));
        assertEquals(5, LocalWidgetCatalogPolicy.style(1, 2));
        assertEquals(2, LocalWidgetCatalogPolicy.style(4, 2));
        assertEquals(0, LocalWidgetCatalogPolicy.style(3, 2));
    }
    @Test public void localRowsDoNotChangeRemotePaginationOrMutateItsInput() {
        java.util.List<Object> remote = java.util.Arrays.asList(1,2,3,4,5,6);
        LocalWidgetCatalogPage page = new LocalWidgetCatalogPage(remote, true, 7);
        page.expand(java.util.Arrays.asList(1,2,3,4,5,6,7,8,9));
        assertEquals(6, page.size());
        assertEquals(9, page.expanded.size());
        assertEquals(6, remote.size());
        LocalWidgetCatalogPage offline = new LocalWidgetCatalogPage(java.util.Collections.emptyList(), true, 7);
        offline.expand(java.util.Arrays.asList("local-a", "local-b"));
        assertFalse(offline.isEmpty());
        assertTrue(offline.size() < 6);
        assertEquals(2, offline.expanded.size());
        LocalWidgetCatalogPage end = new LocalWidgetCatalogPage(java.util.Collections.emptyList(), false, 7);
        end.expand(java.util.Collections.emptyList());
        assertTrue(end.isEmpty());
    }
    @Test public void providerAliasesCannotDuplicateExistingMiuiProviders() {
        assertEquals(LocalWidgetCatalogPolicy.providerKey("org.example", "org.example.Widget"),
                LocalWidgetCatalogPolicy.providerKey("org.example", ".Widget"));
        assertEquals(LocalWidgetCatalogPolicy.providerKey("org.example", "org.example.Widget"),
                LocalWidgetCatalogPolicy.providerKey("org.example", "Widget"));
        assertNotEquals(LocalWidgetCatalogPolicy.providerKey("org.other", "Widget"),
                LocalWidgetCatalogPolicy.providerKey("org.example", "Widget"));
    }
}
