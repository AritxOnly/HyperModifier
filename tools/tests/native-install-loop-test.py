"""Run the real launcher installer/worker with simulated loader and Hook APIs.

Only platform/dependency headers are replaced for the host. install(), worker(),
onLoaded(), their state and the notification/scope guards are production code.
The fake clock does not advance for a pending wake: feedback loops fail instead
of hiding behind a timeout. No device, root, Android service or app data needed.
"""
from pathlib import Path
import subprocess
import tempfile
import struct
import sys

root = Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/cpp/desktop_clock.cpp').read_text()
platform_includes = {
    '#include "widget_status_socket.h"', '#include <android/log.h>',
    '#include <dlfcn.h>', '#include <elf.h>', '#include <link.h>', '#include <sys/eventfd.h>',
}
source = '\n'.join(line for line in source.splitlines() if line not in platform_includes)
platform = r'''
#include "widget_status.h"
#include <atomic>
#include <cassert>
#include <cstdarg>
#include <cstdint>
#include <cstring>
#include <ctime>
#include <iostream>
#include <new>
#include <pthread.h>
#include <thread>
#include <unistd.h>
#include <vector>
struct Elf64_Phdr { uint32_t p_type, p_flags; uint64_t p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align; };
struct Elf64_Nhdr { uint32_t n_namesz, n_descsz, n_type; };
#define ElfW(type) Elf64_##type
constexpr int PT_LOAD=1, PT_NOTE=4, PF_X=1, NT_GNU_BUILD_ID=3;
constexpr int RTLD_NOW=2, RTLD_NOLOAD=4, EFD_CLOEXEC=1, EFD_NONBLOCK=2;
constexpr int ANDROID_LOG_INFO=4, ANDROID_LOG_WARN=5, ANDROID_LOG_ERROR=6;
struct dl_phdr_info { uintptr_t dlpi_addr; const char *dlpi_name; const Elf64_Phdr *dlpi_phdr; size_t dlpi_phnum; };
namespace sim {
int64_t now = 0;
unsigned waits = 0, loads = 0, closes = 0, bridgeHooks = 0, calendarHooks = 0;
unsigned bridgeRollbacks = 0, calendarRollbacks = 0, bridgeLogs = 0, writes = 0, searches = 0;
int loggedBridgeResult = -1, loggedBridgeReady = -1;
unsigned forwarded = 0, tuningCalls = 0;
uint32_t lastTuning = 0;
bool missingInteger = false, trustedSender = true, integerError = false;
void *forwardReceiver = nullptr, *forwardContext = nullptr, *forwardIntent = nullptr;
bool libraries = true, failLauncher = false, missingExport = false, missingTrampoline = false;
bool pointerOnFailure = false;
bool widgetSettled = false, pickerSettled = false;
int result = 1;
std::vector<uint8_t> maml;
uintptr_t calendarRva = 0;
Elf64_Phdr headers[2]{};
struct Idle {};
struct Spin {};
}
void *simDlopen(const char *, int);
void *simDlsym(void *, const char *);
int simDlclose(void *);
int simImages(int (*)(dl_phdr_info *, size_t, void *), void *);
int simClock(clockid_t, timespec *);
int simUsleep(useconds_t);
ssize_t simWrite(int, const void *, size_t);
int simEventfd(unsigned, int) { return 123456; }
int __android_log_print(int, const char *, const char *format, ...) {
    if (!std::strncmp(format, "BRIDGE install", 14)) {
        ++sim::bridgeLogs;
        va_list args; va_start(args, format);
        sim::loggedBridgeResult = va_arg(args, int); sim::loggedBridgeReady = va_arg(args, int);
        va_end(args);
    }
    return 0;
}
int __android_log_write(int, const char *, const char *) { return 0; }
class WidgetStatusSocket {
public:
    bool available() const { return true; }
    void wait(int, int = -1) const;
};
#define dlopen simDlopen
#define dlsym simDlsym
#define dlclose simDlclose
#define dl_iterate_phdr simImages
#define clock_gettime simClock
#define usleep simUsleep
#define write simWrite
#define eventfd simEventfd
'''
driver = r'''
#undef write
extern "C" void mhm_calendar_entry() {}
static void original(void *receiver, void *context, void *intent) {
    ++sim::forwarded;
    sim::forwardReceiver = receiver; sim::forwardContext = context; sim::forwardIntent = intent;
}
static void receiverTarget() {}
void *simDlopen(const char *name, int) {
    ++sim::loads;
    // Synchronous LSPosed notification for every NOLOAD, including duplicate
    // handles. This is the feedback reported by the other module's author.
    onLoaded(name, reinterpret_cast<void *>(0x1000));
    if (sim::failLauncher && std::strstr(name, "libapp_launcher.so")) return nullptr;
    return reinterpret_cast<void *>(0x1000);
}
int simDlclose(void *) {
    ++sim::closes;
    onLoaded("/runtime/libapp_launcher.so", reinterpret_cast<void *>(0x1000));
    return 0;
}
void *simDlsym(void *, const char *name) {
    if (sim::missingExport && !std::strcmp(name, "Intent_get_action")) return nullptr;
    if (sim::missingInteger && !std::strcmp(name, "Bundle_get_i32")) return nullptr;
    if (!std::strcmp(name, RECEIVER_SYMBOL)) return reinterpret_cast<void *>(receiverTarget);
    return reinterpret_cast<void *>(original);
}
int simImages(int (*callback)(dl_phdr_info *, size_t, void *), void *opaque) {
    if (!sim::libraries) return 0;
    for (const char *name : {"/runtime/libapp_launcher.so", "/runtime/libapp.so",
                           "/runtime/libhyper_os_broadcast_private.dylib.so", "/runtime/librust_maml_sdk.so"}) {
        dl_phdr_info info{}; info.dlpi_name = name;
        if (std::strstr(name, "/librust_maml_sdk.so")) {
            info.dlpi_addr = reinterpret_cast<uintptr_t>(sim::maml.data());
            info.dlpi_phdr = sim::headers; info.dlpi_phnum = 2;
        } else info.dlpi_addr = 0x100000;
        if (callback(&info, sizeof(info), opaque)) break;
    }
    return 0;
}
int simClock(clockid_t, timespec *out) {
    out->tv_sec = sim::now / 1000; out->tv_nsec = sim::now % 1000 * 1000000; return 0;
}
ssize_t simWrite(int fd, const void *, size_t size) { assert(fd == 123456); ++sim::writes; return size; }
int simUsleep(useconds_t time) { sim::now += time / 1000; return 0; }
void WidgetStatusSocket::wait(int milliseconds, int) const {
    if (++sim::waits > 256) throw sim::Spin{};
    if (installRequested.load()) return; // hot eventfd, no passage of time
    if (milliseconds < 0) throw sim::Idle{};
    assert(milliseconds > 0); sim::now += milliseconds;
}
bool androidWidgetHooksSettled() { return sim::widgetSettled; }
bool androidWidgetPickerHooksSettled() { return sim::pickerSettled; }
void loadFoldDualPageConfig() {}
bool configureFoldDualPage(bool) { return false; }
void installFoldDualPage(ClockGlassImage, WidgetNativeHook, WidgetNativeUnhook) {}
void loadFoldIconConfig() {}
bool configureFoldIconScale(unsigned) { return false; }
void installFoldIcons(ClockGlassImage, WidgetNativeHook) {}
bool widgetColdGuardPending() { return false; }
WidgetHookBackend widgetColdGuard(ClockGlassImage, WidgetHookBackend backend) { return backend; }
void installAndroidWidgetHooks(void *, WidgetNativeHook) {
    sim::widgetSettled = true; widget_status::schema.store(widget_status::Ready);
}
void installAndroidWidgetPickerHooks(const char *, uintptr_t, WidgetNativeHook, WidgetNativeUnhook) {
    sim::pickerSettled = true; widget_status::picker.store(widget_status::Ready);
}
void installDesktopSearch(const char *, uintptr_t, WidgetNativeHook, WidgetNativeUnhook) { ++sim::searches; }
bool configureDesktopSearch(bool value) { return value; }
void loadDesktopSearchConfig() {}
void loadClockGlassConfig() {}
void configureClockGlassParameters(uint32_t value) { ++sim::tuningCalls; sim::lastTuning = value; }
bool configureClockGlass(unsigned value) { return value != 0; }
void installClockGlass(ClockGlassImage, ClockGlassImage, WidgetNativeHook, WidgetNativeUnhook, void (*)()) {}
bool configureAndroidWidgets(bool, bool, void *) { return true; }
void loadAndroidWidgetConfig() {}
int backendHook(void *target, void *, void **trampoline) {
    if (target == reinterpret_cast<void *>(receiverTarget)) ++sim::bridgeHooks;
    else { assert(target == sim::maml.data() + sim::calendarRva); ++sim::calendarHooks; }
    // Optionally supply a non-null pointer on failure: completion must use the return
    // code / attempted state, not assume any pointer means installation success.
    if (!sim::missingTrampoline && (sim::result == 0 || sim::pointerOnFailure))
        *trampoline = reinterpret_cast<void *>(original);
    return sim::result;
}
int backendUnhook(void *target) {
    if (target == reinterpret_cast<void *>(receiverTarget)) ++sim::bridgeRollbacks;
    else { assert(target == sim::maml.data() + sim::calendarRva); ++sim::calendarRollbacks; }
    return 0;
}
void reset(size_t profileIndex = 0) {
    const auto &profile = CALENDAR_PROFILES[profileIndex];
    sim::calendarRva = profile.rva;
    sim::now = 0; sim::waits = sim::loads = sim::closes = sim::bridgeHooks = sim::calendarHooks = 0;
    sim::bridgeRollbacks = sim::calendarRollbacks = sim::bridgeLogs = sim::writes = sim::searches = 0;
    sim::forwarded = sim::tuningCalls = sim::lastTuning = 0; sim::missingInteger = sim::integerError = false; sim::trustedSender = true;
    sim::libraries = true; sim::failLauncher = sim::missingExport = sim::missingTrampoline = false;
    sim::pointerOnFailure = false; sim::loggedBridgeResult = sim::loggedBridgeReady = -1;
    sim::widgetSettled = sim::pickerSettled = false; sim::result = 1;
    sim::maml.assign(profile.rva + sizeof(profile.entry), 0);
    std::memcpy(sim::maml.data() + sim::calendarRva, profile.entry, sizeof(profile.entry));
    Elf64_Nhdr note{4, sizeof(profile.buildId), NT_GNU_BUILD_ID};
    std::memcpy(sim::maml.data(), &note, sizeof(note));
    std::memcpy(sim::maml.data() + sizeof(note), "GNU", 4);
    std::memcpy(sim::maml.data() + sizeof(note) + 4, profile.buildId, sizeof(profile.buildId));
    sim::headers[0] = {}; sim::headers[0].p_type = PT_LOAD;
    sim::headers[0].p_flags = PF_X; sim::headers[0].p_memsz = sim::maml.size();
    sim::headers[1] = {}; sim::headers[1].p_type = PT_NOTE;
    sim::headers[1].p_memsz = sizeof(note) + 4 + sizeof(profile.buildId);
    hook = backendHook; unhook = backendUnhook;
    originalReceiver = mhm_original_calendar = nullptr;
    bridgeAttempted = calendarAttempted = false;
    installWake = 123456; installRequested.store(false);
    widget_status::schema.store(widget_status::Pending);
    widget_status::picker.store(widget_status::Pending);
    widget_status::bridge.store(widget_status::Pending);
    libraryNotifications.~NativeLibraryNotifications(); new (&libraryNotifications) NativeLibraryNotifications;
}
void runToIdle() {
    try { worker(nullptr); assert(false); }
    catch (const sim::Idle &) {}
    catch (const sim::Spin &) { std::cerr << "Installer feedback loop\n"; std::abort(); }
}
int main() {
    // Shared target occupied by another module: backend failure is terminal,
    // never unhook someone else's target; worker immediately becomes idle.
    reset(); runToIdle();
    assert(sim::bridgeHooks == 1 && sim::calendarHooks == 1 && sim::waits == 1);
    assert(widget_status::bridge.load() == widget_status::Failed && sim::bridgeLogs == 1);
    assert(!originalReceiver && !mhm_original_calendar && sim::loggedBridgeResult == 1 && !sim::loggedBridgeReady);
    assert(!sim::bridgeRollbacks && !sim::calendarRollbacks && !installRequested.load());
    assert(sim::loads == sim::closes && sim::loads == 3);
    for (unsigned i = 0; i < 10000; ++i) {
        install(); onLoaded("/runtime/libapp_launcher.so", reinterpret_cast<void *>(0x1000));
        onLoaded("/runtime/libhyper_os_broadcast_private.dylib.so", reinterpret_cast<void *>(0x2000));
    }
    runToIdle();
    assert(sim::bridgeHooks == 1 && sim::calendarHooks == 1 && sim::loads == 3 && sim::bridgeLogs == 1);
    reset(); sim::pointerOnFailure = true; runToIdle();
    assert(originalReceiver && mhm_original_calendar && widget_status::bridge.load() == widget_status::Failed);
    assert(sim::loggedBridgeResult == 1 && !sim::loggedBridgeReady);
    assert(!sim::bridgeRollbacks && !sim::calendarRollbacks);
    // Missing exports / handle lookups also settle once, even on explicit work.
    reset(); sim::missingExport = true; runToIdle();
    assert(!sim::bridgeHooks && widget_status::bridge.load() == widget_status::Incompatible);
    unsigned loads = sim::loads; install(); assert(sim::loads == loads);
    reset(); sim::widgetSettled = true; widget_status::schema.store(widget_status::Ready);
    sim::failLauncher = true; runToIdle();
    assert(!sim::bridgeHooks && widget_status::bridge.load() == widget_status::Failed && sim::loads == 2);
    // Roll back only our own successful-but-unusable detour, never retry it.
    reset(); sim::result = 0; sim::missingTrampoline = true; runToIdle(); install();
    assert(sim::bridgeHooks == 1 && sim::calendarHooks == 1);
    assert(sim::bridgeRollbacks == 1 && sim::calendarRollbacks == 1);
    reset(); unhook = nullptr; runToIdle();
    assert(!sim::bridgeHooks && !sim::calendarHooks && widget_status::bridge.load() == widget_status::Failed);
    // Success and explicit later settings work keep their existing path.
    reset(); sim::result = 0; runToIdle();
    assert(widget_status::bridge.load() == widget_status::Ready);
    auto before = sim::searches;
    installRequested.store(true); runToIdle();
    assert(sim::searches > before && sim::bridgeHooks == 1 && sim::calendarHooks == 1);
    receiverHook(reinterpret_cast<void *>(1), reinterpret_cast<void *>(2), nullptr);
    assert(sim::forwarded == 1 && sim::forwardReceiver == reinterpret_cast<void *>(1)
        && sim::forwardContext == reinterpret_cast<void *>(2) && !sim::forwardIntent);
    // Optional scalar export never disables unrelated features on older carriers.
    reset(); sim::result = 0; sim::missingInteger = true; runToIdle();
    assert(widget_status::bridge.load() == widget_status::Ready && !getInteger);
    // Exercise the real authenticated receiver's packed tuning branch, rather
    // than reimplementing its decoding in a test helper.
    getAction = +[](void *) -> BorrowedString { return {0, {}, ACTION, std::strlen(ACTION)}; };
    getSender = +[](void *) -> BorrowedString {
        const char *name = sim::trustedSender ? "com.android.systemui" : "other.module";
        return {0, {}, name, std::strlen(name)};
    };
    getExtras = +[](void *intent) -> void * { return intent; };
    getBool = +[](void *, const char *, size_t) -> uint64_t { return UINT64_C(1) << 8; };
    getInteger = +[](void *, const char *key, size_t length) -> uint64_t {
        constexpr char readerKey[] = "mhm_status_reader_uid";
        if (length == sizeof(readerKey)-1 && !std::memcmp(key,readerKey,length)) return uint64_t(10193) << 32;
        constexpr char iconKey[] = "mhm_fold_icon_percent";
        if (length == sizeof(iconKey)-1 && !std::memcmp(key,iconKey,length)) return 1; // optional, absent
        assert(length == 19 && !std::memcmp(key, "mhm_clock_tuning_v1", length));
        return (uint64_t(40 | (80 << 6)) << 32) | (sim::integerError ? 1 : 0);
    };
    receiverHook(nullptr, nullptr, reinterpret_cast<void *>(1));
    assert(sim::tuningCalls == 1 && sim::lastTuning == (40 | (80 << 6)) && !sim::forwarded);
    assert(widget_status::readerUid.load() == 10193);
    sim::integerError = true; receiverHook(nullptr, nullptr, reinterpret_cast<void *>(1));
    assert(sim::tuningCalls == 1); // Missing/invalid extra preserves current parameters.
    sim::integerError = false; widget_status::readerUid.store(1000); sim::trustedSender = false;
    receiverHook(nullptr, nullptr, reinterpret_cast<void *>(1));
    assert(sim::tuningCalls == 1 && sim::forwarded == 1);
    assert(widget_status::readerUid.load() == 1000);
    sim::trustedSender = true; getInteger = nullptr;
    receiverHook(nullptr, nullptr, reinterpret_cast<void *>(1)); assert(sim::tuningCalls == 1);
    // A rejected clock profile is terminal without any backend call.
    reset(); sim::maml[sizeof(Elf64_Nhdr) + 4] ^= 1; runToIdle();
    assert(!sim::calendarHooks && calendarAttempted && sim::waits == 1);
    // Both verified system library builds use their own RVA, including the
    // upgraded OS4.0.4.0 image. Never reuse an old RVA after a library update.
    for (size_t i = 0; i < sizeof(CALENDAR_PROFILES) / sizeof(CALENDAR_PROFILES[0]); ++i) {
        reset(i); sim::result = 0; runToIdle();
        assert(sim::calendarHooks == 1 && mhm_original_calendar);
        reset(i); sim::maml[sim::calendarRva] ^= 1; runToIdle();
        assert(!sim::calendarHooks && calendarAttempted);
        reset(i); sim::headers[0].p_flags = 0; runToIdle();
        assert(!sim::calendarHooks && calendarAttempted);
    }
    // Timeout limits only startup waiting; a first genuine late library load
    // can still complete pending attempts after the 60-second window.
    reset(); sim::libraries = false; runToIdle();
    assert(sim::now == 60000 && !bridgeAttempted && !calendarAttempted && !sim::loads);
    sim::libraries = true; sim::result = 0;
    onLoaded("/runtime/libapp_launcher.so", reinterpret_cast<void *>(0x1000)); install();
    assert(sim::bridgeHooks == 1 && sim::calendarHooks == 1 && widget_status::bridge.load() == widget_status::Ready);
    // Genuine notifications on other threads are not suppressed by TLS.
    NativeLibraryNotifications notifications;
    std::atomic<unsigned> accepted{0};
    std::vector<std::thread> threads;
    { NativeInstallScope scope; assert(NativeInstallScope::active());
      for (unsigned i = 0; i < 8; ++i) threads.emplace_back([&] {
          assert(!NativeInstallScope::active());
          for (unsigned j = 0; j < 10000; ++j)
              if (notifications.accept("/runtime/libapp.so")) ++accepted;
      });
      for (auto &thread : threads) thread.join();
    }
    assert(!NativeInstallScope::active() && accepted == 1);
    assert(!notifications.accept(nullptr) && !notifications.accept("/other/libunrelated.so"));
    assert(notifications.accept("libapp_launcher.so"));
    assert(notifications.accept("librust_maml_sdk.so"));
    assert(notifications.accept("libhyper_os_broadcast_private.dylib.so"));
    assert(notifications.accept("libhyper_os_flutter.so"));
    assert(!notifications.accept("/runtime/libhyper_os_flutter.so"));
    std::cout << "Production installer: occupied target, missing export/handle/API/trampoline, success, "
                 "authenticated optional tuning carrier, late load/settings, 10000 duplicate installs and 80000 concurrent notifications passed\n";
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-install-loop-') as tmp:
    path = Path(tmp)
    # Optional real device ELF: reconstruct its PT_LOAD mapping, then run the
    # production matcher against every profile. No Android code is executed.
    real_check = ''
    if len(sys.argv) > 1:
        elf = Path(sys.argv[1]).read_bytes()
        assert elf[:6] == b'\x7fELF\x02\x01'
        offset = struct.unpack_from('<Q', elf, 32)[0]
        entry_size, count = struct.unpack_from('<HH', elf, 54)
        headers = [struct.unpack_from('<IIQQQQQQ', elf, offset + i * entry_size) for i in range(count)]
        mapped = bytearray(max(h[3] + h[6] for h in headers if h[0] == 1))
        for h in headers:
            if h[0] == 1:
                mapped[h[3]:h[3]+h[5]] = elf[h[2]:h[2]+h[5]]
        image_path = path / 'mapped.bin'
        image_path.write_bytes(mapped)
        rows = ','.join('{' + ','.join(str(v) for v in h) + '}' for h in headers)
        real_check = f'''{{
            std::vector<unsigned char> mapped({len(mapped)});
            FILE *file = std::fopen("{image_path}", "rb"); assert(file);
            assert(std::fread(mapped.data(), 1, mapped.size(), file) == mapped.size());
            std::fclose(file);
            Elf64_Phdr headers[] = {{{rows}}};
            Images image; image.maml = reinterpret_cast<uintptr_t>(mapped.data());
            image.headers = headers; image.headerCount = {count};
            unsigned matches = 0;
            for (const auto &profile : CALENDAR_PROFILES)
                if (matchingProfile(image, profile)) ++matches;
            assert(matches == 1);
            std::cout << "Real MAML ELF matched exactly one verified Calendar ABI profile\\n";
        }}'''
    test_driver = driver.replace('int main() {', 'int main() {\n' + real_check)
    (path / 'test.cpp').write_text(platform + '\n' + source + '\n' + test_driver)
    subprocess.run(['clang++', '-std=c++17', '-O1', '-Wall', '-Wextra', '-Werror', '-pthread',
                    '-fsanitize=address,undefined', '-I', str(root / 'app/src/main/cpp'),
                    str(path / 'test.cpp'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test')], check=True, timeout=30)
