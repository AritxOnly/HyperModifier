#include "fold_widget_material.h"
#include "native_code_contract.h"
#include <android/log.h>
#include <atomic>

extern "C" {
void mhm_fold_widget_first();
void mhm_fold_widget_second();
__attribute__((visibility("hidden"))) uintptr_t mhm_fold_widget_sites[4]{};
__attribute__((visibility("hidden"))) std::atomic<uint8_t> mhm_fold_widget_ready{0};
}

namespace {
constexpr unsigned char buildId[]{0x4f,0x1b,0xda,0xed,0xc7,0x2c,0x20,0xe7,
    0xde,0x7d,0x3e,0xbc,0x35,0x1b,0xdd,0x3a};
constexpr clock_glass::Contract contracts[]{
    {0x13c7f1c, 0x324, UINT64_C(0xce088d70f4d01e64)}, // First widget model, selector, lazy call and merge
    {0x13c9990, 0x298, UINT64_C(0x6f6682b790481b84)}, // Second widget model and selector
    {0x15426d8, 0x148, UINT64_C(0x073d7805b6a497c3)}, // Model ID, package and class
    {0x10e7394, 0x48, UINT64_C(0x43f219a03dccc146)}, // AppWidget model CID
    {0x818298, 0x40, UINT64_C(0x0493ee7c9cf5b2e4)}, // One-byte string CID, tagged length and payload
    {0x81ad48, 0x70, UINT64_C(0xb29d9961b97be6d9)}, // Two-byte string CID, tagged length, stride and payload
    {0x10d5a40, 0x9c, UINT64_C(0x5db4647b6c67a72d)}, // System capsule brightness and folder presets
    {0x1727778, 0x5c, UINT64_C(0x40dfa854f9c74f64)}, // Reactive brightness value
};
}

void installFoldWidgetMaterial(ClockGlassImage dart, int (*hook)(void *, void *, void **),
        int (*unhook)(void *), void (*prepareRules)()) {
    if (!hook || !unhook || !clock_glass::matches(dart, buildId, contracts)) return;
    mhm_fold_widget_sites[0] = dart.base + 0x13c814c;
    mhm_fold_widget_sites[1] = dart.base + 0x13c817c;
    mhm_fold_widget_sites[2] = dart.base + 0x13c9ba8;
    mhm_fold_widget_sites[3] = dart.base + 0x13c9bd8;
    void *first = reinterpret_cast<void *>(dart.base + 0x13c8128);
    void *second = reinterpret_cast<void *>(dart.base + 0x13c9b84);
    void *unused = nullptr;
    if (hook(first, reinterpret_cast<void *>(mhm_fold_widget_first), &unused)) return;
    if (hook(second, reinterpret_cast<void *>(mhm_fold_widget_second), &unused)) {
        unhook(first);
        return;
    }
    prepareRules();
    mhm_fold_widget_ready.store(1, std::memory_order_release);
    __android_log_write(ANDROID_LOG_INFO, "MHMDesktopWidget", "6325 widget material selectors ready");
}
