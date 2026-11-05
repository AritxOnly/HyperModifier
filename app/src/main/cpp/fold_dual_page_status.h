#pragma once
#include <atomic>
#include <cstdint>
extern "C" {
// Sticky diagnostic flags, set once per process. Never log or perform I/O in Dart hooks.
__attribute__((visibility("hidden"))) inline std::atomic<uint8_t> mhm_fold_dual_mode_seen{0};
__attribute__((visibility("hidden"))) inline std::atomic<uint8_t> mhm_fold_dual_mode_applied{0};
__attribute__((visibility("hidden"))) inline std::atomic<uint8_t> mhm_fold_dual_grid_seen{0};
__attribute__((visibility("hidden"))) inline std::atomic<uint8_t> mhm_fold_dual_grid_applied{0};
}
namespace fold_dual_status {
enum Stage { NotRequested=0, Waiting=1, Ready=2, Unsupported=3, Failed=4 };
inline std::atomic<int> stage{NotRequested};
inline std::atomic<bool> requested{false}, received{false}, active{false};
}
