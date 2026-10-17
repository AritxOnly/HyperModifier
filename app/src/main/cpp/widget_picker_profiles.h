#pragma once
#include <cstdint>
namespace widget_picker {
// One reviewed instruction/dataflow shape shared by 7722, 7748 and 8244.
// Calls, object-pool operands and static-field indices are relocation inputs,
// not version identifiers. Their relationships are checked by the probe.
inline constexpr uint32_t context[32] = {
    0x8b1c8021, 0xb8013001, 0xf90001e0, 0x94000000,
    0xf85b03a1, 0xaa0003e5, 0xf9403f62, 0x91400363,
    0xf9400063, 0xaa1603e6, 0xaa1603e7, 0x94000000,
    0x140000d2, 0xf85c03a2, 0xb84fb020, 0x8b1c8000,
    0x37200a00, 0x94000000, 0xf81b03a0, 0xf9403f40,
    0xf9400000, 0xf9402370, 0x6b10001f, 0x54000061,
    0xf9403362, 0x94000000, 0xaa1603e1, 0xd2800142,
    0xf81a03a0, 0x94000000, 0xf81983a0, 0x91400370,
};
inline constexpr uint32_t operandMask = 0xffc003ff;
inline constexpr uint32_t mask(unsigned word) {
    return word == 3 || word == 11 || word == 17 || word == 25 || word == 29 ? 0xfc000000
        : word == 7 || word == 8 || word == 20 || word == 31 ? operandMask : 0xffffffff;
}
}
