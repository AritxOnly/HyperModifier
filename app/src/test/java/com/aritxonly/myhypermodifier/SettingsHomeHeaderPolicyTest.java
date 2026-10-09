package com.aritxonly.myhypermodifier;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsHomeHeaderPolicyTest {
    private static final long OWN = SettingsHomeHeaderPolicy.MODULE_ID, MANAGER = SettingsHomeHeaderPolicy.MANAGER_ID;
    private static final long START = SettingsHomeHeaderPolicy.SECTION_START_ID, END = SettingsHomeHeaderPolicy.SECTION_END_ID;
    private static final long AI = 10L, PERSONALIZE = 11L, SECURITY = 12L;

    private void insert(List<Long> headers, Long own, Long manager) {
        SettingsHomeHeaderPolicy.insertSection(headers, manager, own, START, END, PERSONALIZE, AI,
                Long::longValue, value -> value == -1 || value == START || value == END);
    }

    @Test public void nativeFilteringRemainsVisibleThroughTheSharedAdapterList() {
        List<Long> headers = new ArrayList<>(List.of(1L, 2L, SECURITY, 3L, AI, PERSONALIZE));
        List<Long> adapter = headers;
        headers.remove(Long.valueOf(2L)); // Unsupported feature remains filtered.
        headers.remove(Long.valueOf(SECURITY)); // Keep the native region restriction.
        insert(headers, OWN, MANAGER);
        assertSame(headers, adapter);
        assertEquals(List.of(1L, 3L, AI, START, MANAGER, OWN, END, PERSONALIZE), adapter);
    }

    @Test public void repeatedUpdatesAndDisabledEntriesDoNotLeaveDuplicateSections() {
        List<Long> headers = new ArrayList<>(List.of(AI, PERSONALIZE));
        insert(headers, OWN, MANAGER);
        SettingsHomeHeaderPolicy.removeInjected(headers, Long::longValue);
        insert(headers, OWN, null);
        assertEquals(List.of(AI, START, OWN, END, PERSONALIZE), headers);
        SettingsHomeHeaderPolicy.removeInjected(headers, Long::longValue);
        insert(headers, null, null);
        assertEquals(List.of(AI, PERSONALIZE), headers);
    }

    @Test public void existingNativeCategoryIsReused() {
        List<Long> headers = new ArrayList<>(List.of(AI, -1L, PERSONALIZE));
        insert(headers, OWN, MANAGER);
        assertEquals(List.of(AI, -1L, MANAGER, OWN, END, PERSONALIZE), headers);
        SettingsHomeHeaderPolicy.removeInjected(headers, Long::longValue);
        assertEquals(List.of(AI, -1L, PERSONALIZE), headers);
    }

    @Test public void missingAiStillUsesPersonalizationBoundary() {
        List<Long> headers = new ArrayList<>(List.of(1L, PERSONALIZE));
        insert(headers, null, MANAGER);
        assertEquals(List.of(1L, START, MANAGER, END, PERSONALIZE), headers);
    }

    @Test public void missingPersonalizationFallsBackToAiWithoutMergingNextGroup() {
        List<Long> headers = new ArrayList<>(List.of(AI, -1L, 5L));
        insert(headers, OWN, MANAGER);
        assertEquals(List.of(AI, START, MANAGER, OWN, -1L, 5L), headers);
    }

    @Test public void missingBothAnchorsDoesNotInjectIntoAnUnrelatedSection() {
        List<Long> headers = new ArrayList<>(List.of(1L, 2L));
        insert(headers, OWN, MANAGER);
        assertEquals(List.of(1L, 2L), headers);
    }
    @Test public void topSectionKeepsSecurityInsideDeviceGroupAndReusesWirelessSpacer() {
        long device = 20L, wifi = 21L;
        List<Long> headers = new ArrayList<>(List.of(device, SECURITY, -1L, wifi, AI, PERSONALIZE));
        SettingsHomeHeaderPolicy.insertSection(headers, MANAGER, OWN, START, END, wifi, 0,
                Long::longValue, value -> value == -1 || value == START || value == END);
        assertEquals(List.of(device, SECURITY, -1L, MANAGER, OWN, END, wifi, AI, PERSONALIZE), headers);
        // Moving back to the middle must leave the original wireless section boundary intact.
        SettingsHomeHeaderPolicy.removeInjected(headers, Long::longValue);
        insert(headers, OWN, MANAGER);
        assertEquals(List.of(device, SECURITY, -1L, wifi, AI, START, MANAGER, OWN, END, PERSONALIZE), headers);
    }

    @Test public void spacersAreExcludedFromTheEntryCardGroup() {
        // ProxyHeaderViewAdapter exposes groupId to the native card decoration.
        List<Integer> groupIds = List.of(SettingsHomeHeaderPolicy.groupFor(false),
                SettingsHomeHeaderPolicy.groupFor(true), SettingsHomeHeaderPolicy.groupFor(true),
                SettingsHomeHeaderPolicy.groupFor(false));
        assertTrue(groupIds.get(0) < 0);
        assertEquals(groupIds.get(1), groupIds.get(2));
        assertTrue(groupIds.get(1) >= 0);
        assertTrue(groupIds.get(3) < 0);
    }

    @Test public void unknownOrMissingSavedPositionFallsBackToMiddle() {
        assertEquals("middle", SettingsHomeHeaderPolicy.normalizePosition(null));
        assertEquals("middle", SettingsHomeHeaderPolicy.normalizePosition("invalid"));
        assertEquals("top", SettingsHomeHeaderPolicy.normalizePosition("top"));
    }

    @Test public void modernHyperOsUsesGroupsWithoutAddingDividerCategoryRows() {
        long device = 20L, wifi = 21L;
        List<Long> headers = new ArrayList<>(List.of(device, SECURITY, wifi, AI, PERSONALIZE));
        List<Long> adapter = headers;
        SettingsHomeHeaderPolicy.insertSection(headers, MANAGER, OWN, null, null,
                PERSONALIZE, AI, Long::longValue, value -> value == -1);
        assertEquals(List.of(device, SECURITY, wifi, AI, MANAGER, OWN, PERSONALIZE), headers);
        SettingsHomeHeaderPolicy.removeInjected(headers, Long::longValue);
        SettingsHomeHeaderPolicy.insertSection(headers, MANAGER, OWN, null, null,
                wifi, 0, Long::longValue, value -> value == -1);
        assertSame(adapter, headers);
        assertEquals(List.of(device, SECURITY, MANAGER, OWN, wifi, AI, PERSONALIZE), headers);
    }

    private static class Row {
        final long id;
        int group;
        Row(long id, int group) { this.id = id; this.group = group; }
    }

    @Test public void mergedEntriesInheritDeviceGroupAfterRemainingDeviceRows() {
        Row account = new Row(1, 0), device = new Row(2, 7), feature = new Row(3, 7), wifi = new Row(4, 9);
        Row manager = new Row(MANAGER, SettingsHomeHeaderPolicy.ENTRY_GROUP);
        Row module = new Row(OWN, SettingsHomeHeaderPolicy.ENTRY_GROUP);
        List<Row> headers = new ArrayList<>(List.of(account, device, feature, wifi));
        List<Row> adapter = headers;
        SettingsHomeHeaderPolicy.mergeIntoSection(headers, manager, module, device.id,
                row -> row.id, row -> row.group, (row, group) -> row.group = group);
        assertSame(adapter, headers);
        assertEquals(List.of(account, device, feature, manager, module, wifi), headers);
        assertEquals(device.group, manager.group);
        assertEquals(device.group, module.group);
        assertEquals(9, wifi.group);
        SettingsHomeHeaderPolicy.removeInjected(headers, row -> row.id);
        SettingsHomeHeaderPolicy.mergeIntoSection(headers, null, module, device.id,
                row -> row.id, row -> row.group, (row, group) -> row.group = group);
        assertEquals(List.of(account, device, feature, module, wifi), headers);
    }

    @Test public void mergeDoesNotInventMissingDeviceGroupsOrGroupNegativeSpacers() {
        Row spacer = new Row(2, -1), wifi = new Row(4, 9), module = new Row(OWN, 10);
        List<Row> headers = new ArrayList<>(List.of(spacer, wifi));
        SettingsHomeHeaderPolicy.mergeIntoSection(headers, null, module, 20,
                row -> row.id, row -> row.group, (row, group) -> row.group = group);
        SettingsHomeHeaderPolicy.mergeIntoSection(headers, null, module, 2,
                row -> row.id, row -> row.group, (row, group) -> row.group = group);
        assertEquals(List.of(spacer, wifi), headers);
        assertEquals("device", SettingsHomeHeaderPolicy.normalizePosition("device"));
    }

    @Test public void multipleModulesAtSameSlotFormOneOrderedSectionAndCleanupTogether() {
        Row device = new Row(20, 1), wifi = new Row(21, 2), ai = new Row(AI, 7), personal = new Row(PERSONALIZE, 3);
        Row a = new Row(SettingsHomeHeaderPolicy.moduleEntryId("com.example.a"), 0);
        Row b = new Row(SettingsHomeHeaderPolicy.moduleEntryId("com.example.b"), 0);
        Row c = new Row(SettingsHomeHeaderPolicy.moduleEntryId("com.example.c"), 0);
        List<Row> headers = new ArrayList<>(List.of(device, wifi, ai, personal));
        SettingsHomeHeaderPolicy.placeEntries(headers, List.of(a, b), "top", device.id, wifi.id, 0, null, null,
                row -> row.id, row -> row.group, (row, group) -> row.group = group, row -> row.group < 0);
        SettingsHomeHeaderPolicy.placeEntries(headers, List.of(c), "middle", device.id, personal.id, AI, null, null,
                row -> row.id, row -> row.group, (row, group) -> row.group = group, row -> row.group < 0);
        assertEquals(List.of(device, a, b, wifi, ai, c, personal), headers);
        assertEquals(a.group, b.group);
        assertNotEquals(a.group, c.group);
        assertEquals(2, wifi.group);
        SettingsHomeHeaderPolicy.removeInjected(headers, row -> row.id);
        assertEquals(List.of(device, wifi, ai, personal), headers);
    }

    @Test public void headlineModulesAllMergeIntoTheExistingDeviceCard() {
        Row device = new Row(20, 1), wifi = new Row(21, 2), a = new Row(OWN, 0), b = new Row(MANAGER, 0);
        List<Row> headers = new ArrayList<>(List.of(device, wifi));
        SettingsHomeHeaderPolicy.placeEntries(headers, List.of(a, b), "device", device.id, 0, 0, null, null,
                row -> row.id, row -> row.group, (row, group) -> row.group = group, row -> row.group < 0);
        assertEquals(List.of(device, a, b, wifi), headers);
        assertEquals(device.group, a.group);
        assertEquals(device.group, b.group);
    }

    @Test public void bottomBoundaryWorksInBothNativeOrdersAndWhenOneAnchorIsMissing() {
        assertEquals(2, SettingsHomeHeaderPolicy.betweenAnchor(List.of(1L, 9L, 2L), 1, 2, Long::longValue));
        assertEquals(1, SettingsHomeHeaderPolicy.betweenAnchor(List.of(2L, 9L, 1L), 1, 2, Long::longValue));
        assertEquals(2, SettingsHomeHeaderPolicy.betweenAnchor(List.of(9L, 2L), 1, 2, Long::longValue));
        assertEquals(0, SettingsHomeHeaderPolicy.betweenAnchor(List.of(9L), 1, 2, Long::longValue));
        assertEquals("bottom", SettingsHomeHeaderPolicy.normalizePosition("bottom"));
        assertNotEquals(SettingsHomeHeaderPolicy.moduleEntryId("com.example.Aa"), SettingsHomeHeaderPolicy.moduleEntryId("com.example.BB"));
    }

}
