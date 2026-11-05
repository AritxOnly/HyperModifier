#include "clock_glass.h"
#include "native_code_contract.h"
#include "clock_glass_render.h"
#include "clock_glass_playback.h"
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

namespace {
using namespace clock_glass;
constexpr char TAG[] = "MHMClockMaterial";
std::atomic<unsigned> configured{Default};
std::atomic<bool> ready{false};
std::atomic<uint64_t> targets{0}, draws{0};
std::atomic<uint64_t> textures{0}, missing{0};
std::atomic<unsigned> sourceStates{0};
unsigned reportedStates = 0;
void (*wakeDiagnostics)() = nullptr;
void (*consumePending)(void *) = nullptr;
bool attempted = false; // Only the existing native installation worker writes this.
void *originalText = nullptr, *originalParagraph = nullptr, *originalRect = nullptr;
struct Text { const char *data; size_t size; };
using Name = Text (*)(const void *);
using DrawText = void (*)(void *, void *, float);
using Paragraph = void (*)(void *, const void *, float, float, const void *);
using ReplayRect = void (*)(void *, const Rect *);
Name name = nullptr;
Renderer renderer;
std::atomic<uint32_t> tuning{Parameters::defaults};
void configPath(char (&path)[256]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_clock_material", getuid() / 100000);
}
bool target(void *self) {
    if (!self || !(read<uint8_t>(self, 0x4d4) & 1)) return false;
    Text value = name(static_cast<unsigned char *>(self) + 0x430);
    constexpr char id[] = "center_classic_plus";
    return value.data && value.size == sizeof(id) - 1 && !std::memcmp(value.data, id, sizeof(id) - 1);
}
void textHook(void *self, void *canvas, float offset) {
    unsigned mode = configured.load(std::memory_order_relaxed);
    if (mode != Blur || !ready.load(std::memory_order_acquire)) {
        reinterpret_cast<DrawText>(originalText)(self, canvas, offset); return;
    }
    bool selected = target(self);
    Scope scope(selected ? mode : Default);
    if (selected) targets.fetch_add(1, std::memory_order_relaxed);
    reinterpret_cast<DrawText>(originalText)(self, canvas, offset);
}
void rectHook(void *self, const Rect *rect) {
    auto original = reinterpret_cast<ReplayRect>(originalRect);
    if (read<int32_t>(self, 0x68) != kBlurMarker || !read<uint8_t>(self, 0x51)) {
        original(self, rect); return;
    }
    // Same virtual GetCanvas call made by the original rectangle dispatcher.
    using GetCanvas = void *(*)(void *);
    void *canvas = reinterpret_cast<GetCanvas>(read<void *>(read<void *>(self), 0x3a0))(self);
    replayBlur(self, rect, canvas, consumePending, original, [](Backdrop state) {
        (state == Backdrop::TextureAvailable ? textures : missing).fetch_add(1, std::memory_order_relaxed);
        unsigned bit = 1u << static_cast<unsigned>(state);
        // At most three diagnostic wakes per process. No per-frame logging,
        // polling, object allocation or synchronous cross-process call.
        if (!(sourceStates.fetch_or(bit, std::memory_order_relaxed) & bit) && wakeDiagnostics) wakeDiagnostics();
    });
}
void paragraphHook(void *self, const void *blob, float x, float y, const void *paint) {
    unsigned mode = Scope::current();
    if (mode && configured.load(std::memory_order_relaxed) == mode
            && ready.load(std::memory_order_acquire) && renderer.draw(self, blob, x, y, paint, mode, Parameters::decode(tuning.load(std::memory_order_relaxed)))) {
        draws.fetch_add(1, std::memory_order_relaxed); return;
    }
    reinterpret_cast<Paragraph>(originalParagraph)(self, blob, x, y, paint);
}

}
void loadClockGlassConfig() {
    char path[256], value = '0'; configPath(path);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        if (read(fd, &value, 1) == 1) configured.store(clock_glass::normalize(unsigned(value - '0')));
        close(fd);
    }
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_clock_tuning_v1", getuid() / 100000);
    fd = open(path, O_RDONLY | O_CLOEXEC);
    uint32_t parameters = Parameters::defaults;
    if (fd >= 0) {
        if (read(fd, &parameters, sizeof(parameters)) == sizeof(parameters)) tuning.store(Parameters::normalize(parameters));
        close(fd);
    }
}
void configureClockGlassParameters(uint32_t value) {
    value = Parameters::normalize(value);
    if (tuning.exchange(value) == value) return;
    char path[256];
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_clock_tuning_v1", getuid() / 100000);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd >= 0) { (void)write(fd, &value, sizeof(value)); close(fd); }
}
bool configureClockGlass(unsigned value) {
    unsigned mode = clock_glass::normalize(value);
    unsigned previous = configured.exchange(mode);
    if (previous == mode) return false;
    char path[256]; configPath(path);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd >= 0) { char byte = char('0' + mode); (void)write(fd, &byte, 1); close(fd); }
    __android_log_print(ANDROID_LOG_INFO, TAG, "mode=%u ready=%d target=%llu clip=%llu texture=%llu missing=%llu%s", mode,
        ready.load(), static_cast<unsigned long long>(targets.load()), static_cast<unsigned long long>(draws.load()),
        static_cast<unsigned long long>(textures.load()), static_cast<unsigned long long>(missing.load()),
        mode == SoftGlass ? " glass unavailable: keeping stock text" : "");
    return mode == clock_glass::Blur;
}
void installClockGlass(ClockGlassImage maml, ClockGlassImage flutter,
        int (*hook)(void *, void *, void **), int (*unhook)(void *), void (*wakeWorker)()) {
    using namespace clock_glass;
    unsigned states = sourceStates.load();
    if (states != reportedStates) {
        reportedStates = states;
        __android_log_print(ANDROID_LOG_INFO, TAG, "backdrop states=%u texture=%llu missing=%llu (1=no state,2=no texture,4=texture present)",
            states, static_cast<unsigned long long>(textures.load()), static_cast<unsigned long long>(missing.load()));
    }
    if (attempted || configured.load() != Blur || !maml.base || !flutter.base) return;
    attempted = true; // Publish before any backend call; no failed-install retry loop.
    if (!hook || !unhook || !matches(maml, kMamlBuildId, kMamlContracts)
            || !matches(flutter, kFlutterBuildId, kFlutterContracts)) {
        __android_log_write(ANDROID_LOG_WARN, TAG, "rejected: native ABI/code mismatch or missing rollback API"); return;
    }
    name = reinterpret_cast<Name>(maml.base + kName);
    consumePending = reinterpret_cast<decltype(consumePending)>(flutter.base + kConsumePending);
    wakeDiagnostics = wakeWorker;
    renderer.setPaint = reinterpret_cast<decltype(renderer.setPaint)>(flutter.base + kSetPaint);
    renderer.drawRect = reinterpret_cast<decltype(renderer.drawRect)>(flutter.base + kDrawRect);
    renderer.saveLayer = reinterpret_cast<decltype(renderer.saveLayer)>(flutter.base + kSaveLayer);
    renderer.restore = reinterpret_cast<decltype(renderer.restore)>(flutter.base + kRestore);
    void *rect = reinterpret_cast<void *>(flutter.base + kReplayRect);
    int result = hook(rect, reinterpret_cast<void *>(rectHook), &originalRect);
    if (result != 0 || !originalRect) {
        if (!result) (void)unhook(rect);
        __android_log_print(ANDROID_LOG_WARN, TAG, "rect rejected result=%d", result); return;
    }
    void *paragraph = reinterpret_cast<void *>(flutter.base + kParagraph);
    result = hook(paragraph, reinterpret_cast<void *>(paragraphHook), &originalParagraph);
    if (result != 0 || !originalParagraph) {
        if (!result) (void)unhook(paragraph);
        (void)unhook(rect);
        __android_log_print(ANDROID_LOG_WARN, TAG, "paragraph rejected result=%d", result); return;
    }
    void *text = reinterpret_cast<void *>(maml.base + kTextScope);
    result = hook(text, reinterpret_cast<void *>(textHook), &originalText);
    if (result != 0 || !originalText) {
        if (!result) (void)unhook(text);
        int rollback = unhook(paragraph);
        (void)unhook(rect);
        __android_log_print(ANDROID_LOG_WARN, TAG, "scope rejected result=%d rollback=%d", result, rollback); return;
    }
    renderer.paragraph = reinterpret_cast<Renderer::Paragraph>(originalParagraph);
    ready.store(true, std::memory_order_release);
    __android_log_write(ANDROID_LOG_INFO, TAG, "ready: native text alpha mask + PassBlur SrcIn; glass unavailable");
}
