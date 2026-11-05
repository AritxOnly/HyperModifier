// Xiaomi lhasa launcher 6325. RVAs plus whole consulted function contracts.
#pragma once
#include "native_code_contract.h"
namespace fold_dual_page {
inline constexpr unsigned char buildId[]{0x4f,0x1b,0xda,0xed,0xc7,0x2c,0x20,0xe7,
    0xde,0x7d,0x3e,0xbc,0x35,0x1b,0xdd,0x3a};
inline constexpr clock_glass::Contract contracts[]{
    {0x8809c4, 0xac, UINT64_C(0x56eed812efe7341c)}, // stub_6a084
    {0x880974, 0x50, UINT64_C(0x4543bebc20b4e958)}, // stub_6a034
    {0x8917f4, 0xec, UINT64_C(0x04676fa04c39ea48)}, // stub_7aeb4
    {0x19ebcd0, 0xba4, UINT64_C(0xce74dfa6d6d12806)}, // sub_11d5390
    {0x19ec99c, 0xa18, UINT64_C(0xce585b053286deba)}, // sub_11d605c
    {0x19ebc10, 0xc0, UINT64_C(0x815b1a2c9ca544bf)}, // sub_11d52d0
    {0xa24ce4, 0x64, UINT64_C(0x5b38c7bd9b0c88e2)}, // device config accessor
    {0x1727778, 0x60, UINT64_C(0x5b2f7eca21306e7f)}, // reactive state accessor
    {0x880a70, 0x2c, UINT64_C(0x3d49b0ea57e7797c)}, // orientation accessor
};
inline constexpr uintptr_t orientation = 0x8809c4;
inline constexpr uintptr_t portraitGrid = 0x19ebcd0;
inline constexpr uintptr_t dualGrid = 0x19ec99c;
inline constexpr uintptr_t modeReturn = 0x8918c4;
inline constexpr uintptr_t editingReturn = 0x8809b0;
}
