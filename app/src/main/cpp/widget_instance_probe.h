#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

// Validate the SDK's *use* of the two foreign widget APIs, independently of
// APK size/hash. No native objects are constructed or foreign functions called.
namespace widget_instance {
struct Segment { uintptr_t address; size_t size; bool executable; };
inline bool readable(const std::vector<Segment> &segments, uintptr_t address, size_t size) {
    for (const auto &s : segments) if (address >= s.address && address - s.address <= s.size
        && size <= s.size - (address - s.address)) return true;
    return false;
}
inline uint32_t wordAt(uintptr_t address) { uint32_t w; std::memcpy(&w, reinterpret_cast<const void *>(address), 4); return w; }
inline int64_t sign(uint64_t value, unsigned bits) {
    return value & (uint64_t{1} << (bits - 1)) ? static_cast<int64_t>(value) - (int64_t{1} << bits) : value;
}
inline bool calls(uintptr_t pc, uintptr_t expected, const std::vector<Segment> &segments) {
    uint32_t call = wordAt(pc);
    if ((call & 0xfc000000) != 0x94000000) return false;
    uintptr_t target = pc + sign(call & 0x3ffffff, 26) * 4;
    if (target == expected) return true;
    bool executable = false;
    for (const auto &s : segments) if (s.executable && target >= s.address && target - s.address <= s.size
            && 16 <= s.size - (target - s.address)) { executable = true; break; }
    if (!executable) return false;
    uint32_t a = wordAt(target), b = wordAt(target + 4), c = wordAt(target + 8), d = wordAt(target + 12);
    if ((a & 0x9f00001f) != 0x90000010 || (b & 0xffc003ff) != 0xf9400211
            || (c & 0xffc003ff) != 0x91000210 || d != 0xd61f0220
            || ((c >> 10) & 4095) != ((b >> 10) & 4095) * 8) return false;
    uintptr_t got = (target & ~uintptr_t{4095})
        + sign((((a >> 5) & 0x7ffff) << 2) | ((a >> 29) & 3), 21) * 4096 + ((b >> 10) & 4095) * 8;
    if (!readable(segments, got, sizeof(uintptr_t))) return false;
    uintptr_t function; std::memcpy(&function, reinterpret_cast<const void *>(got), sizeof(function));
    return function == expected;
}
inline constexpr uint32_t infoContext[] = {
    0xf9400300, 0x2a1303e1, 0x94000000, 0x360000c0, 0xaa1f03f3,
    0x085ffe88, 0x340000c8, 0xd5033f5f, 0x14000007, 0xaa0103f3,
};
inline constexpr uint32_t providerContext[] = {
    0xf94002e0, 0x94000000, 0x085ffe68, 0xaa0003f4, 0x34000068,
    0xd5033f5f, 0x14000004, 0x52800028, 0x08097e68, 0x34000069,
};
template<size_t N>
inline bool matches(uintptr_t start, const uint32_t (&context)[N], unsigned callWord,
                    uintptr_t expected, const std::vector<Segment> &segments) {
    for (size_t i = 0; i < N; ++i) {
        uint32_t mask = i == callWord ? 0xfc000000 : 0xffffffff;
        if ((wordAt(start + i * 4) & mask) != context[i]) return false;
    }
    return calls(start + callWord * 4, expected, segments);
}
inline bool compatible(const std::vector<Segment> &segments, uintptr_t getInfo, uintptr_t getProvider) {
    if (!getInfo || !getProvider || getInfo == getProvider) return false;
    size_t size = 0;
    for (const auto &s : segments) {
        if (s.size > 16 * 1024 * 1024 - size || s.address > UINTPTR_MAX - s.size) return false;
        size += s.size;
    }
    bool info = false, provider = false;
    for (const auto &s : segments) {
        if (!s.executable || (s.address & 3) || s.size < 40) continue;
        for (size_t offset = 0; offset <= s.size - 40; offset += 4) {
            uintptr_t start = s.address + offset; uint32_t first = wordAt(start);
            if (first == infoContext[0] && matches(start, infoContext, 2, getInfo, segments)) info = true;
            if (first == providerContext[0] && matches(start, providerContext, 1, getProvider, segments)) provider = true;
            if (info && provider) return true;
        }
    }
    return false;
}
}
