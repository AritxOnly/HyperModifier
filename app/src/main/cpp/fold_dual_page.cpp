#include "fold_dual_page.h"
#include "fold_dual_page_status.h"
#include "fold_dual_page_profile.h"
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <fcntl.h>
#include <mutex>
#include <sys/system_properties.h>
#include <unistd.h>

extern "C" {
__attribute__((visibility("hidden"))) std::atomic<uint8_t> mhm_fold_dual_enabled{0};
__attribute__((visibility("hidden"))) void *mhm_fold_dual_orientation_original = nullptr;
__attribute__((visibility("hidden"))) void *mhm_fold_dual_grid_original = nullptr;
__attribute__((visibility("hidden"))) uintptr_t mhm_fold_dual_grid_target = 0;
__attribute__((visibility("hidden"))) uintptr_t mhm_fold_dual_mode_return = 0;
__attribute__((visibility("hidden"))) uintptr_t mhm_fold_dual_editing_return = 0;
void mhm_fold_dual_orientation_entry();
void mhm_fold_dual_grid_entry();
}

namespace {
static_assert(sizeof(mhm_fold_dual_enabled) == 1);
std::mutex configMutex;
std::atomic<bool> requested{false};
bool ready = false; // protected by configMutex
bool attempted = false; // existing native install worker only
bool isLhasa() {
    static const bool match = [] {
        char device[PROP_VALUE_MAX]{};
        __system_property_get("ro.product.device", device);
        return std::strcmp(device, "lhasa") == 0;
    }();
    return match;
}
void cachePath(char (&path)[160]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_fold_dual_page", getuid() / 100000);
}
}

void loadFoldDualPageConfig() {
    if (!isLhasa()) return;
    std::lock_guard<std::mutex> guard(configMutex);
    char path[160]; cachePath(path);
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return;
    char value = '0';
    if (read(fd, &value, 1) == 1) requested.store(value == '1');
    fold_dual_status::requested.store(requested.load());
    fold_dual_status::stage.store(requested.load() ? fold_dual_status::Waiting : fold_dual_status::NotRequested);
    close(fd);
}

bool configureFoldDualPage(bool value) {
    if (!isLhasa()) return false;
    std::lock_guard<std::mutex> guard(configMutex);
    fold_dual_status::received.store(true);
    fold_dual_status::requested.store(value);
    bool previous = requested.exchange(value);
    mhm_fold_dual_enabled.store(value && ready, std::memory_order_release);
    fold_dual_status::active.store(value && ready);
    if (value && fold_dual_status::stage.load() == fold_dual_status::NotRequested) fold_dual_status::stage.store(fold_dual_status::Waiting);
    if (previous == value) return false;
    __android_log_print(ANDROID_LOG_INFO, "MHM.FoldDualPage", "Config received requested=%d active=%d", value, value && ready);
    char path[160]; cachePath(path);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd >= 0) {
        char byte = value ? '1' : '0';
        (void)write(fd, &byte, 1);
        close(fd);
    }
    return value;
}

void installFoldDualPage(ClockGlassImage dart, int (*hook)(void *, void *, void **), int (*unhook)(void *)) {
    if (!isLhasa() || !requested.load() || attempted || !dart.base || !hook) return;
    attempted = true;
    fold_dual_status::stage.store(fold_dual_status::Waiting);
    if (!unhook || !clock_glass::matches(dart, fold_dual_page::buildId, fold_dual_page::contracts)) {
        fold_dual_status::stage.store(unhook ? fold_dual_status::Unsupported : fold_dual_status::Failed);
        __android_log_write(ANDROID_LOG_WARN, "MHM.FoldDualPage", "Unsupported dual-page ABI or missing rollback API");
        return;
    }
    mhm_fold_dual_grid_target = dart.base + fold_dual_page::dualGrid;
    mhm_fold_dual_mode_return = dart.base + fold_dual_page::modeReturn;
    mhm_fold_dual_editing_return = dart.base + fold_dual_page::editingReturn;
    void *orientation = reinterpret_cast<void *>(dart.base + fold_dual_page::orientation);
    void *grid = reinterpret_cast<void *>(dart.base + fold_dual_page::portraitGrid);
    int first = hook(orientation, reinterpret_cast<void *>(mhm_fold_dual_orientation_entry), &mhm_fold_dual_orientation_original);
    int second = -1;
    if (!first && mhm_fold_dual_orientation_original)
        second = hook(grid, reinterpret_cast<void *>(mhm_fold_dual_grid_entry), &mhm_fold_dual_grid_original);
    if (!first && !second && mhm_fold_dual_orientation_original && mhm_fold_dual_grid_original) {
        std::lock_guard<std::mutex> guard(configMutex);
        ready = true;
        // Publish only after BOTH paths are installed and all code pointers are visible.
        mhm_fold_dual_enabled.store(requested.load(), std::memory_order_release);
        fold_dual_status::active.store(requested.load());
        fold_dual_status::stage.store(fold_dual_status::Ready);
    } else {
        fold_dual_status::stage.store(fold_dual_status::Failed);
        // Never remove an occupied target owned by another module. Partial installs remain disabled.
        if (!second) unhook(grid);
        if (!first) unhook(orientation);
    }
    __android_log_print(ANDROID_LOG_INFO, "MHM.FoldDualPage", "Dual-page hooks orientation=%d grid=%d active=%d",
            first, second, mhm_fold_dual_enabled.load());
}
