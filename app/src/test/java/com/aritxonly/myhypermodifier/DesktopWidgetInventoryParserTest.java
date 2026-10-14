package com.aritxonly.myhypermodifier;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DesktopWidgetInventoryParserTest {
    private String widget(int index, int id, int user, String host, int hostId, String provider) {
        return "  [" + index + "] id=" + id + "\n    host=HostId{user:" + user + ", app:10139, hostId:" + hostId + ", pkg:" + host + "}\n"
                + "    provider=ProviderId{cmp:ComponentInfo{" + provider + "}, profile:UserHandle{0}}\n";
    }
    @Test public void keepsOnlyBoundWidgetsFromThisUsersDesktopHost() {
        String provider = "org.example.widgets/org.example.widgets.Widget";
        String dump = "Providers:\n  [0] provider something\nWidgets:\n"
                + widget(0, 42, 0, "com.miui.home", 1024, provider)
                + widget(1, 43, 0, "com.miui.home", 1024, provider)
                + widget(2, 44, 10, "com.miui.home", 1024, provider)
                + widget(3, 45, 0, "com.miui.personalassistant", 2048, provider)
                + widget(4, 46, 0, "com.miui.home", 99, provider)
                + "Hosts:\n" + widget(5, 47, 0, "com.miui.home", 1024, provider);
        assertEquals(List.of("42\t" + provider, "43\t" + provider), DesktopWidgetInventoryParser.parse(dump, 0));
        assertEquals(List.of("44\t" + provider), DesktopWidgetInventoryParser.parse(dump, 10));
    }
    @Test public void pendingUnboundIdsAreNotDisplayedAsAddedWidgets() {
        assertTrue(DesktopWidgetInventoryParser.parse("Widgets:\n  [0] id=42\n    host=HostId{user:0, app:10139, hostId:1024, pkg:com.miui.home}\n    provider=null\nHosts:\n", 0).isEmpty());
        assertTrue(DesktopWidgetInventoryParser.parse("Widgets:\nHosts:\n", 0).isEmpty());
    }
    @Test(expected = IllegalArgumentException.class) public void permissionDenialMustNotLookLikeAnEmptyDesktop() {
        DesktopWidgetInventoryParser.parse("Permission Denial: can't dump AppWidgetService", 0);
    }
}
