#include "fold_icons.h"
#include "native_code_contract.h"
#include <android/log.h>
#include <atomic>
#include <fcntl.h>
#include <mutex>
#include <cstdio>
#include <sys/system_properties.h>
#include <unistd.h>

extern "C" {
__attribute__((visibility("hidden"))) std::atomic<uint64_t> mhm_fold_icon_scale{0};
void mhm_fold_icon_return();
}

namespace {
bool isLhasa() {
    static const bool match = [] {
        char device[PROP_VALUE_MAX]{};
        __system_property_get("ro.product.device", device);
        return std::strcmp(device, "lhasa") == 0;
    }();
    return match;
}

std::mutex configMutex;
unsigned configured = 100;
void cachePath(char (&path)[160]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_fold_icons", getuid() / 100000);
}
uint64_t scaleBits(unsigned value) {
    if (value < 70 || value >= 100) return 0;
    double ratio = value / 100.0;
    uint64_t bits;
    std::memcpy(&bits, &ratio, sizeof(bits));
    return bits;
}
constexpr unsigned char buildId[]{0x4f,0x1b,0xda,0xed,0xc7,0x2c,0x20,0xe7,
    0xde,0x7d,0x3e,0xbc,0x35,0x1b,0xdd,0x3a};
constexpr clock_glass::Contract contracts[]{
    // Verified return ABI, grid posture dispatch and the initialized device-config accessors.
    {0x9e5350, 0x140, UINT64_C(0x1ef9e02be2e93afb)},
    {0x19ebc5c, 0x68, UINT64_C(0x93dfda1c0d926de1)},
    {0xa24ce4, 0x64, UINT64_C(0x5b38c7bd9b0c88e2)},
    {0x1727778, 0x60, UINT64_C(0x5b2f7eca21306e7f)},
    {0x880a70, 0x2c, UINT64_C(0x3d49b0ea57e7797c)},
};
}

void loadFoldIconConfig() {
    if (!isLhasa()) return;
    char path[160]; cachePath(path);
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return;
    uint8_t value;
    if (read(fd, &value, 1) == 1 && value >= 70 && value <= 100) {
        configured = value;
        mhm_fold_icon_scale.store(scaleBits(value));
    }
    close(fd);
}

bool configureFoldIconScale(unsigned value) {
    if (!isLhasa() || value < 70 || value > 100) return false;
    std::lock_guard<std::mutex> guard(configMutex);
    if (configured == value) return false;
    configured = value;
    mhm_fold_icon_scale.store(scaleBits(value));
    char path[160]; cachePath(path);
    int fd = open(path, O_WRONLY | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd >= 0) {
        uint8_t byte = static_cast<uint8_t>(value);
        (void)write(fd, &byte, 1);
        close(fd);
    }
    return value < 100;
}

void installFoldIcons(ClockGlassImage dart, int (*hook)(void *, void *, void **)) {
    static bool attempted = false; // launcher worker only
    if (!dart.base || !hook || attempted || !mhm_fold_icon_scale.load()) return;
    attempted = true;
    if (!clock_glass::matches(dart, buildId, contracts)) {
        __android_log_write(ANDROID_LOG_WARN, "MHM.Fold", "Unrecognized launcher icon layout ABI");
        return;
    }
    void *unused = nullptr;
    int result = hook(reinterpret_cast<void *>(dart.base + 0x9e5480),
            reinterpret_cast<void *>(mhm_fold_icon_return), &unused);
    __android_log_print(result ? ANDROID_LOG_WARN : ANDROID_LOG_INFO, "MHM.Fold",
            "Inner portrait icon hook result=%d", result);
}
