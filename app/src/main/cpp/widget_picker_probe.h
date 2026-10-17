#pragma once
#include "widget_picker_profiles.h"
#include <cstddef>
#include <cstring>
#include <vector>
namespace widget_picker {
struct Code { uintptr_t address; size_t size; };
inline uint32_t wordAt(uintptr_t address) { uint32_t w; std::memcpy(&w, reinterpret_cast<const void *>(address), 4); return w; }
inline bool contains(const std::vector<Code> &ranges, uintptr_t target, size_t bytes) {
    for (const auto &range : ranges) if (!(target & 3) && target >= range.address
        && target - range.address <= range.size && bytes <= range.size - (target - range.address)) return true;
    return false;
}
inline bool executable(const std::vector<Code> &ranges, uintptr_t target) { return contains(ranges, target, 4); }
inline uintptr_t callTarget(uintptr_t pc, uint32_t word) {
    int64_t displacement = word & 0x3ffffff;
    if (displacement & 0x2000000) displacement -= 0x4000000;
    return pc + displacement * 4;
}
inline bool poolPair(uintptr_t pc, unsigned reg, uint32_t &offset) {
    uint32_t add = wordAt(pc), load = wordAt(pc + 4);
    if ((add & operandMask) != (0x91400000 | (27 << 5) | reg)
            || (load & operandMask) != (0xf9400000 | (reg << 5) | reg)) return false;
    offset = ((add >> 10) & 4095) * 4096 + ((load >> 10) & 4095) * 8;
    return offset >= 16 && offset < 8 * 1024 * 1024;
}
inline bool contextMatches(uintptr_t start, const std::vector<Code> &ranges) {
    uintptr_t site = start + 64;
    // All witness reads stay inside the same readable executable mapping.
    if (site < 0x124 || !contains(ranges, site - 0x124, 0x270)) return false;
    for (unsigned i = 0; i < 32; ++i) {
        uint32_t word = wordAt(start + i * 4), m = mask(i);
        if ((word & m) != (context[i] & m)) return false;
        if (m == 0xfc000000) {
            uintptr_t target = callTarget(start + i * 4, word);
            if (!executable(ranges, target) || (target >= site - 0x124 && target < site + 0x14c)) return false;
        }
    }
    // The branch is a compressed Dart bool read (x28 heap base, x22 NULL).
    // This false constructor is used by the same function's object setup.
    if (wordAt(site - 0x124) != 0x9100c2c4) return false; // add x4,x22,#0x30
    if (wordAt(site + 0x140) != 0xaa0203e1 || wordAt(site + 0x144) != 0xf9403f40) return false;
    // Both log paths must read the same late static. Its slot can move.
    if (wordAt(site - 0x80) != 0xf9403f40 || wordAt(site - 0x7c) != wordAt(site + 0x10)) return false;
    uint32_t logger = 0, repeatedLogger = 0, logA = 0, logB = 0, debugA = 0, debugB = 0;
    if (!poolPair(site - 0x24, 3, logger) || !poolPair(site + 0x120, 3, repeatedLogger)
            || logger != repeatedLogger) return false;
    // needsHideFromPicker / isMiuiWidget are neighboring literals; the shared
    // WidgetsVerticalList logger lives five entries before the first literal.
    if (!poolPair(site - 0x54, 16, logA) || !poolPair(site + 0xf0, 16, logB)
            || logB != logA + 8 || logA != logger + 40) return false;
    // isMIUIWidgetSupport / isPad debug literals are another adjacent pair,
    // independent of the list logger's pool page.
    if (!poolPair(site + 0x3c, 16, debugA) || !poolPair(site + 0x50, 16, debugB)
            || debugB != debugA + 8 || debugA == logA) return false;
    return true;
}
inline uintptr_t find(const std::vector<Code> &ranges) {
    size_t total = 0;
    for (const auto &range : ranges) {
        if ((range.address & 3) || range.size > 64 * 1024 * 1024 - total
                || range.address > UINTPTR_MAX - range.size) return 0;
        total += range.size;
    }
    uintptr_t match = 0;
    for (const auto &range : ranges) {
        if (range.size < 0x270) continue;
        // Start at the exact bool-load/decompression/branch triplet, not arbitrary
        // masked instructions. All expensive checks run only for these candidates.
        for (size_t offset = 0x124; offset + 0x14c <= range.size; offset += 4) {
            uintptr_t site = range.address + offset;
            if (wordAt(site) != 0x37200a00 || wordAt(site - 4) != 0x8b1c8000 || wordAt(site - 8) != 0xb84fb020) continue;
            if (contextMatches(site - 64, ranges)) {
                if (match) return 0; // ambiguous: never select the first match
                match = site;
            }
        }
    }
    return match;
}
}
