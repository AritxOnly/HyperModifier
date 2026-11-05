#include "widget_cold_guard.h"
#include "cold_pages.h"
#include "native_code_contract.h"
#include <android/log.h>
#include <array>
#include <cerrno>
#include <mutex>
#include <unistd.h>

namespace {
constexpr unsigned char engineId[]{0x14,0x62,0x38,0x20,0xe9,0x4f,0x10,0x87,0x1f,0x68,
                                   0xd1,0xe2,0xcd,0x1f,0x94,0xc2,0xd1,0x4c,0x50,0xa5};
constexpr uintptr_t helperRva = 0x109cea0;
constexpr clock_glass::Contract contracts[]{{helperRva, 0xa8, UINT64_C(0xc2f8ec43d328c8a2)}};
constexpr char tag[] = "MHMDesktopWidget";
using Release = bool (*)(uintptr_t, size_t, const void *, size_t *);
WidgetHookBackend backend{};
void *originalRelease = nullptr;
bool attempted = false, installed = false, reported = false;
unsigned pendingChecks = 0;
constexpr unsigned maxChecks = 10; // existing 500 ms startup worker only
size_t pageSize = 0;

// Only widget metadata/instance hooks, picker, two material selectors and the
// authenticated configuration receiver use this backend. No per-frame access.
std::array<void *, 24> targets{};
std::array<uintptr_t, 2 * targets.size()> pages{};
unsigned targetCount = 0, pageCount = 0;
std::mutex mutex;

void rebuildPages() {
    if (!pageSize) return;
    pageCount = 0;
    for (unsigned i = 0; i < targetCount; ++i) {
        uintptr_t address = reinterpret_cast<uintptr_t>(targets[i]);
        uintptr_t first = address & ~(pageSize - 1);
        uintptr_t last = (address + 31) & ~(pageSize - 1); // ARM64 inline detour window
        pages[pageCount++] = first;
        if (last != first) pages[pageCount++] = last;
    }
    std::sort(pages.begin(), pages.begin() + pageCount);
    pageCount = std::unique(pages.begin(), pages.begin() + pageCount) - pages.begin();
}

int installTarget(void *target, void *entry, void **original) {
    std::lock_guard<std::mutex> lock(mutex);
    if (targetCount == targets.size() || reinterpret_cast<uintptr_t>(target) > UINTPTR_MAX - 31)
        return -ENOSPC;
    int result = backend.hook(target, entry, original);
    if (result == 0) {
        targets[targetCount++] = target;
        rebuildPages();
    }
    return result;
}

int removeTarget(void *target) {
    std::lock_guard<std::mutex> lock(mutex);
    int result = backend.unhook(target);
    if (result == 0) {
        auto end = targets.begin() + targetCount;
        auto found = std::find(targets.begin(), end, target);
        if (found != end) {
            *found = targets[--targetCount];
            rebuildPages();
        }
    }
    return result;
}

bool releaseRange(uintptr_t begin, size_t size, const void *systemPin, size_t *released) {
    // Serialize only the OEM reclamation worker with detour install/rollback.
    // A release that began just before a new patch cannot erase that patch.
    std::lock_guard<std::mutex> lock(mutex);
    auto original = reinterpret_cast<Release>(originalRelease);
    if (!original) return false;
    size_t skipped = 0;
    bool ok = releaseColdSpans(begin, size, pages.data(), pageCount, pageSize,
        [&](uintptr_t at, size_t bytes) { return original(at, bytes, systemPin, released); }, skipped);
    if (skipped && !reported) {
        reported = true;
        __android_log_print(ANDROID_LOG_INFO, tag, "6325 cold reclaim skipped %zu bytes; widget pages=%u",
                            skipped, pageCount);
    }
    return ok;
}

bool systemPinsHelper(ClockGlassImage image) {
    // The engine's pin-range constructor at 0xe9240c reads this relocated pair.
    // The guard must itself reside in the range that the OEM already preserves.
    constexpr uintptr_t slot = 0x1101900;
    auto *headers = static_cast<const Elf64_Phdr *>(image.headers);
    for (size_t i = 0; i < image.count; ++i) {
        const auto &p = headers[i];
        if (p.p_type != PT_LOAD || !(p.p_flags & PF_R) || slot < p.p_vaddr
                || slot - p.p_vaddr > p.p_memsz || p.p_memsz - (slot - p.p_vaddr) < 2 * sizeof(uintptr_t)) continue;
        uintptr_t pin[2];
        std::memcpy(pin, reinterpret_cast<const void *>(image.base + slot), sizeof(pin));
        return pin[0] == image.base + 0x109cd78 && pin[1] == image.base + 0x109d888;
    }
    return false;
}
}

bool widgetColdGuardPending() {
    return !attempted && pendingChecks != 0;
}

WidgetHookBackend widgetColdGuard(ClockGlassImage flutter, WidgetHookBackend api) {
    if (!api.hook || !api.unhook) return api;
    // The installer is the only writer. backend remains immutable once callbacks can run.
    if (!backend.hook) backend = api;
    if (!attempted && flutter.base) {
        bool known = clock_glass::hasBuildId(flutter, engineId);
        bool code = known && clock_glass::matches(flutter, engineId, contracts);
        bool pins = known && systemPinsHelper(flutter);
        if (!code || !pins) {
            ++pendingChecks;
            attempted = pendingChecks >= maxChecks;
            if (pendingChecks == 1 || attempted)
                __android_log_print(ANDROID_LOG_INFO, tag,
                    "cold guard preflight base=%p build=%d code=%d pins=%d check=%u",
                    reinterpret_cast<void *>(flutter.base), known, code, pins, pendingChecks);
            // Keep tracking any detours installed while the engine is settling.
        } else {
            attempted = true; // Never retry an occupied backend entry.
            std::lock_guard<std::mutex> lock(mutex);
            long bytes = sysconf(_SC_PAGESIZE);
            if (bytes == 4096 || bytes == 16384) {
                pageSize = static_cast<size_t>(bytes);
                rebuildPages();
                void *target = reinterpret_cast<void *>(flutter.base + helperRva);
                int result = api.hook(target, reinterpret_cast<void *>(releaseRange), &originalRelease);
                installed = result == 0 && originalRelease != nullptr;
                if (!result && !installed) api.unhook(target);
                __android_log_print(ANDROID_LOG_INFO, tag, "6325 cold reclaim guard installed=%d result=%d",
                                    installed, result);
            }
        }
    }
    return (!attempted || installed) ? WidgetHookBackend{installTarget, removeTarget} : api;
}
