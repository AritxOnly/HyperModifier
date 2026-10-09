package com.aritxonly.myhypermodifier;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.ToLongFunction;

/** Preserve the list shared by native device filtering and the Settings adapter. */
public final class SettingsHomeHeaderPolicy {
    public static final long MODULE_ID = 0x4d484d0001L, MANAGER_ID = 0x4d484d0002L;
    public static final long SECTION_START_ID = 0x4d484d0003L, SECTION_END_ID = 0x4d484d0004L;
    public static final int ENTRY_GROUP = 0x4d484d, SEPARATOR_GROUP = -1;
    private SettingsHomeHeaderPolicy() {}

    public static String normalizePosition(String value) {
        // The HTML defaults editor exports the headline label as "headline".
        return ("device".equals(value) || "headline".equals(value)) ? "device" : "top".equals(value) ? "top" : "bottom".equals(value) ? "bottom" : "middle";
    }

    public static int groupFor(boolean entry) {
        return entry ? ENTRY_GROUP : SEPARATOR_GROUP;
    }


    public static <T> void removeInjected(List<T> headers, ToLongFunction<T> id) {
        headers.removeIf(item -> {
            long value = id.applyAsLong(item);
            return isModuleEntryId(value) || value == MODULE_ID || value == MANAGER_ID
                    || value == SECTION_START_ID || value == SECTION_END_ID;
        });
    }

    public static long moduleEntryId(String packageName) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < packageName.length(); i++) { hash ^= packageName.charAt(i); hash *= 0x100000001b3L; }
        return 0x4d48000000000000L | (hash & 0x0000ffffffffffffL);
    }

    public static boolean isModuleEntryId(long id) { return (id & 0xffff000000000000L) == 0x4d48000000000000L; }

    /** The lower of the two native anchors is the insertion boundary regardless of ROM order. */
    public static <T> long betweenAnchor(List<T> headers, long first, long second, ToLongFunction<T> id) {
        long boundary = 0;
        for (T item : headers) {
            long value = id.applyAsLong(item);
            if ((first != 0 && value == first) || (second != 0 && value == second)) boundary = value;
        }
        return boundary;
    }

    public static <T> void placeEntries(List<T> headers, List<T> entries, String position,
                                       long deviceId, long lowerId, long fallbackAfterId, T start, T end,
                                       ToLongFunction<T> id, ToIntFunction<T> group,
                                       ObjIntConsumer<T> setGroup, Predicate<T> category) {
        if (entries.isEmpty()) return;
        if ("device".equals(position)) {
            for (T entry : entries) mergeIntoSection(headers, null, entry, deviceId, id, group, setGroup);
        } else {
            // All entries for this position share one group and retain their display order.
            int groupId = ENTRY_GROUP + ("top".equals(position) ? 1 : "bottom".equals(position) ? 2 : 3);
            for (T entry : entries) setGroup.accept(entry, groupId);
            // Insert first, then attach the remainder in-place before the section's trailing spacer.
            T first = entries.get(0);
            insertSection(headers, null, first, start, end, lowerId, fallbackAfterId, id, category);
            int index = headers.indexOf(first);
            if (index >= 0) headers.addAll(index + 1, entries.subList(1, entries.size()));
        }
    }

    /** Append to the actual native device group, preserving its remaining device-specific rows. */
    public static <T> void mergeIntoSection(List<T> headers, T manager, T module, long deviceId,
                                           ToLongFunction<T> id, ToIntFunction<T> group,
                                           ObjIntConsumer<T> setGroup) {
        if (deviceId == 0 || (manager == null && module == null)) return;
        for (int i = 0; i < headers.size(); i++) {
            if (id.applyAsLong(headers.get(i)) != deviceId) continue;
            int nativeGroup = group.applyAsInt(headers.get(i));
            if (nativeGroup < 0) return;
            int position = i + 1;
            while (position < headers.size() && group.applyAsInt(headers.get(position)) == nativeGroup) position++;
            if (manager != null) { setGroup.accept(manager, nativeGroup); headers.add(position++, manager); }
            if (module != null) { setGroup.accept(module, nativeGroup); headers.add(position, module); }
            return;
        }
    }

    public static <T> void insertSection(List<T> headers, T manager, T module, T start, T end,
                                        long lowerAnchorId, long fallbackAfterId, ToLongFunction<T> id,
                                        Predicate<T> category) {
        if (manager == null && module == null) return;
        int position = -1;
        // Insert before the chosen native section; its preceding spacer may be reused.
        for (int i = 0; i < headers.size(); i++) {
            if (lowerAnchorId != 0 && id.applyAsLong(headers.get(i)) == lowerAnchorId) { position = i; break; }
        }
        if (position < 0) {
            for (int i = 0; i < headers.size(); i++) {
                if (fallbackAfterId != 0 && id.applyAsLong(headers.get(i)) == fallbackAfterId) { position = i + 1; break; }
            }
        }
        if (position < 0) return; // Do not silently place the section in an unrelated native group.
        if (start != null && (position == 0 || !category.test(headers.get(position - 1)))) headers.add(position++, start);
        if (manager != null) headers.add(position++, manager);
        if (module != null) headers.add(position++, module);
        if (end != null && position < headers.size() && !category.test(headers.get(position))) headers.add(position, end);
    }
}
