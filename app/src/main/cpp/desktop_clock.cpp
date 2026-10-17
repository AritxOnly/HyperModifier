#include "clock_format.h"
#include "widget_hooks.h"
#include "widget_status_socket.h"

#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <cstdio>
#include <cstdint>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <mutex>
#include <pthread.h>
#include <sys/eventfd.h>
#include <unistd.h>

// LSPosed's published Native Hook API. HyperOS Runtime has no JNI.
using Hook = int (*)(void *, void *, void **);
using Loaded = void (*)(const char *, void *);
using Unhook = int (*)(void *);
struct NativeAPIEntries { uint32_t version; Hook hook_func; Unhook unhook_func; };

extern "C" {
void *mhm_original_calendar = nullptr;
void mhm_calendar_entry();
}

namespace {
constexpr char TAG[] = "MHMDesktopClock";
constexpr char ACTION[] = "com.android.systemui.fsgesture";
constexpr char MARKER[] = "mhm_desktop_clock_control";
constexpr char ENABLED[] = "mhm_desktop_clock_enabled";
constexpr uintptr_t FORMAT_RVA = 0x3fcb7c;
constexpr unsigned char BUILD_ID[] = {
    0x00,0x02,0xd2,0xe1,0x66,0x15,0xeb,0xdb,0x6b,0x53,
    0x7b,0x4b,0xf8,0xfe,0xde,0x44,0x17,0x4b,0xf8,0x1d};
// Calendar::format profile for the MAML library pulled from this device.
// Also validate exact code before hooking; an OS upgrade must not reuse an RVA.
constexpr unsigned char ENTRY[] = {
    0xfd,0x7b,0xba,0xa9,0x71,0x6d,0x06,0x94,
    0xff,0xc3,0x0a,0xd1,0xe8,0x23,0x02,0x91,
    0xf8,0x03,0x03,0xaa,0xf7,0x03,0x02,0xaa,
    0xb0,0xd3,0x06,0x94,0xf3,0x03,0x01,0xaa};
constexpr char RECEIVER_SYMBOL[] =
    "_RNvMs3_NtNtCslLvADlVgqlk_26hyper_os_broadcast_private13dyn_"
    "broadcast23BroadcastReceiver_traitINtB5_20BroadcastReceiver_TOINtNtNtNt"
    "Cs9Neji4M1weT_10abi_stable9std_types5boxed7private4RBoxuEE10on_receiveB9_";

// Borrowed optional RString ABI and Bundle boolean Result ABI were verified
// against this device's HyperRT exports; see docs/investigations.
struct BorrowedString {
    uint8_t tag;
    uint8_t padding[7];
    const char *data;
    uintptr_t length;
};
static_assert(sizeof(BorrowedString) == 24);
using GetString = BorrowedString (*)(void *);
using GetExtras = void *(*)(void *);
using GetBool = uint64_t (*)(void *, const char *, size_t);
using Receiver = void (*)(void *, void *, void *);

Hook hook = nullptr;
Unhook unhook = nullptr;
void *originalReceiver = nullptr;
GetString getAction = nullptr, getSender = nullptr;
GetExtras getExtras = nullptr;
GetBool getBool = nullptr;
std::mutex installMutex;
std::atomic<bool> enabled{false};
std::atomic<unsigned> changedLogs{0};
std::atomic<bool> workerStarted{false};
bool profileRejected = false;
int installWake = -1;
std::atomic<bool> installRequested{false};

bool isLauncher() {
    char name[96]{};
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    ssize_t count = read(fd, name, sizeof(name) - 1);
    close(fd);
    return count > 0 && std::strcmp(name, "com.miui.home") == 0;
}

void cachePath(char (&path)[256]) {
    std::snprintf(path, sizeof(path),
        "/data/user_de/%u/com.miui.home/cache/mhm_desktop_clock_enabled", getuid() / 100000);
}
void readCachedConfig() {
    char path[256], value = '0'; cachePath(path);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;
    if (read(fd, &value, 1) == 1) enabled.store(value == '1');
    close(fd);
}
void cacheConfig(bool value) {
    char path[256]; cachePath(path);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) return;
    char byte = value ? '1' : '0';
    (void)write(fd, &byte, 1);
    close(fd);
}

bool equals(BorrowedString value, const char *text) {
    size_t length = std::strlen(text);
    return value.tag == 0 && value.data != nullptr && value.length == length
        && std::memcmp(value.data, text, length) == 0;
}
bool readBool(void *extras, const char *key, bool &value) {
    if (!extras) return false;
    uint64_t result = getBool(extras, key, std::strlen(key));
    if (result & 1) return false;
    value = ((result >> 8) & 1) != 0;
    return true;
}
void receiverHook(void *receiver, void *context, void *intent) {
    if (intent && equals(getAction(intent), ACTION)) {
        void *extras = getExtras(intent);
        bool marker = false, value = false;
        if (readBool(extras, MARKER, marker) && marker
                && equals(getSender(intent), "com.android.systemui")
                && readBool(extras, ENABLED, value)) {
            bool previous = enabled.exchange(value);
            cacheConfig(value);
            if (previous != value) changedLogs.store(0);
            __android_log_print(ANDROID_LOG_INFO, TAG, "CONFIG accepted enabled=%d", value);
            bool stack = false, glass = false;
            if (readBool(extras, "mhm_android_widget_stack", stack)
                    && readBool(extras, "mhm_android_widget_glass", glass))
                widget_status::config.store(configureAndroidWidgets(stack, glass, extras));
            // Consume our carrier; no gesture settings are sent to stock handlers.
            return;
        }
    }
    reinterpret_cast<Receiver>(originalReceiver)(receiver, context, intent);
}

struct Images {
    uintptr_t maml = 0, dart = 0;
    const ElfW(Phdr) *headers = nullptr;
    size_t headerCount = 0;
    const char *launcherPath = nullptr, *broadcastPath = nullptr, *dartPath = nullptr;
};
int findImages(dl_phdr_info *info, size_t, void *opaque) {
    auto &images = *static_cast<Images *>(opaque);
    const char *name = info->dlpi_name;
    if (std::strstr(name, "/librust_maml_sdk.so")) {
        images.maml = info->dlpi_addr;
        images.headers = info->dlpi_phdr; images.headerCount = info->dlpi_phnum;
    } else if (std::strstr(name, "/libapp_launcher.so")) images.launcherPath = name;
    else if (std::strstr(name, "/libhyper_os_broadcast_private.dylib.so")) images.broadcastPath = name;
    else if (std::strstr(name, "/libapp.so")) { images.dart = info->dlpi_addr; images.dartPath = name; }
    return 0;
}
bool matchingProfile(const Images &images) {
    bool buildMatches = false, executable = false;
    for (size_t i = 0; i < images.headerCount; ++i) {
        const auto &ph = images.headers[i];
        if (ph.p_type == PT_LOAD && (ph.p_flags & PF_X)
                && FORMAT_RVA >= ph.p_vaddr && FORMAT_RVA + sizeof(ENTRY) <= ph.p_vaddr + ph.p_memsz)
            executable = true;
        if (ph.p_type != PT_NOTE) continue;
        const auto *cursor = reinterpret_cast<const unsigned char *>(images.maml + ph.p_vaddr);
        const auto *end = cursor + ph.p_memsz;
        while (static_cast<size_t>(end - cursor) >= sizeof(ElfW(Nhdr))) {
            const auto *note = reinterpret_cast<const ElfW(Nhdr) *>(cursor);
            size_t nameSize = (static_cast<size_t>(note->n_namesz) + 3) & ~size_t{3};
            size_t descSize = (static_cast<size_t>(note->n_descsz) + 3) & ~size_t{3};
            if (nameSize + descSize > static_cast<size_t>(end - cursor) - sizeof(*note)) break;
            const auto *name = cursor + sizeof(*note);
            const auto *desc = name + nameSize;
            if (note->n_type == NT_GNU_BUILD_ID && note->n_namesz == 4
                    && std::memcmp(name, "GNU", 4) == 0 && note->n_descsz == sizeof(BUILD_ID))
                buildMatches = std::memcmp(desc, BUILD_ID, sizeof(BUILD_ID)) == 0;
            cursor = desc + descSize;
        }
    }
    return executable && buildMatches
        && std::memcmp(reinterpret_cast<void *>(images.maml + FORMAT_RVA), ENTRY, sizeof(ENTRY)) == 0;
}
void install() {
    if (!hook) return;
    std::unique_lock<std::mutex> guard(installMutex, std::try_to_lock);
    if (!guard.owns_lock()) return;
    Images images; dl_iterate_phdr(findImages, &images);
    if (images.launcherPath && !androidWidgetHooksSettled()) {
        void *launcher = dlopen(images.launcherPath, RTLD_NOW | RTLD_NOLOAD);
        if (launcher) {
            installAndroidWidgetHooks(launcher, hook);
            dlclose(launcher);
        }
    }
    if (images.dartPath && !androidWidgetPickerHooksSettled())
        installAndroidWidgetPickerHooks(images.dartPath, images.dart, hook, unhook);
    int schemaStage = widget_status::schema.load();
    if (schemaStage == widget_status::Incompatible || schemaStage == widget_status::Failed)
        widget_status::bridge.store(schemaStage);
    if (!originalReceiver && images.launcherPath && images.broadcastPath
            && schemaStage == widget_status::Ready) {
        void *launcher = dlopen(images.launcherPath, RTLD_NOW | RTLD_NOLOAD);
        void *broadcast = dlopen(images.broadcastPath, RTLD_NOW | RTLD_NOLOAD);
        if (launcher && broadcast) {
            getAction = reinterpret_cast<GetString>(dlsym(launcher, "Intent_get_action"));
            getSender = reinterpret_cast<GetString>(dlsym(launcher, "Intent_get_sender_package_name"));
            getExtras = reinterpret_cast<GetExtras>(dlsym(launcher, "Intent_get_extras"));
            getBool = reinterpret_cast<GetBool>(dlsym(launcher, "Bundle_get_boolean"));
            void *target = dlsym(broadcast, RECEIVER_SYMBOL);
            if (getAction && getSender && getExtras && getBool && target) {
                int result = hook(target, reinterpret_cast<void *>(receiverHook), &originalReceiver);
                widget_status::bridge.store(result == 0 && originalReceiver ? widget_status::Ready : widget_status::Failed);
                __android_log_print(ANDROID_LOG_INFO, TAG, "BRIDGE install result=%d ready=%d", result, originalReceiver != nullptr);
            }
        }
        if (launcher) dlclose(launcher);
        if (broadcast) dlclose(broadcast);
    }
    if (images.maml && !mhm_original_calendar && !profileRejected) {
        if (!matchingProfile(images)) {
            profileRejected = true;
            __android_log_write(ANDROID_LOG_WARN, TAG, "HOOK rejected: MAML build ID or Calendar ABI fingerprint mismatch");
            return;
        }
        int result = hook(reinterpret_cast<void *>(images.maml + FORMAT_RVA),
            reinterpret_cast<void *>(mhm_calendar_entry), &mhm_original_calendar);
        __android_log_print(ANDROID_LOG_INFO, TAG, "HOOK Calendar::format result=%d ready=%d enabled=%d", result, mhm_original_calendar != nullptr, enabled.load());
    }
}
void *worker(void *) {
    WidgetStatusSocket status;
    bool settled = false;
    auto milliseconds = []() -> int64_t {
        timespec now{}; clock_gettime(CLOCK_MONOTONIC, &now);
        return int64_t(now.tv_sec) * 1000 + now.tv_nsec / 1000000;
    };
    int64_t deadline = milliseconds() + 60000, nextInstall = 0;
    while (!settled) {
        int64_t now = milliseconds();
        if (now >= nextInstall) {
            installRequested.store(false);
            install();
            nextInstall = milliseconds() + 500;
        }
        {
            std::lock_guard<std::mutex> guard(installMutex);
            settled = (originalReceiver || widget_status::schema.load() == widget_status::Incompatible
                    || widget_status::schema.load() == widget_status::Failed)
                && (mhm_original_calendar || profileRejected)
                && androidWidgetHooksSettled() && androidWidgetPickerHooksSettled();
        }
        now = milliseconds();
        if (!settled && now >= deadline) {
            __android_log_write(ANDROID_LOG_WARN, TAG, "HOOK timeout waiting for Runtime libraries");
            for (auto *stage : {&widget_status::schema, &widget_status::picker, &widget_status::bridge}) {
                int pending = widget_status::Pending;
                stage->compare_exchange_strong(pending, widget_status::Timeout);
            }
            break;
        }
        if (!settled) {
            int wait = static_cast<int>(std::max(int64_t{0}, std::min(nextInstall, deadline) - now));
            if (status.available() || installWake >= 0) status.wait(wait, installWake); else usleep(wait * 1000);
        }
    }
    if (status.available() || installWake >= 0) for (;;) {
        if (installRequested.exchange(false)) install();
        status.wait(-1, installWake);
    }
    return nullptr;
}
void onLoaded(const char *name, void *) {
    // Loader callbacks may run on the main thread. Only signal the worker;
    // never fingerprint, scan, dlopen or install detours on that thread.
    if (installWake < 0 || !name) return;
    const char *file = std::strrchr(name, '/'); file = file ? file + 1 : name;
    if (std::strcmp(file, "libapp_launcher.so") && std::strcmp(file, "libapp.so")
            && std::strcmp(file, "librust_maml_sdk.so")
            && std::strcmp(file, "libhyper_os_broadcast_private.dylib.so")) return;
    if (!installRequested.exchange(true)) { uint64_t value = 1; (void)write(installWake, &value, sizeof(value)); }
}
}

extern "C" ClockFormat mhm_clock_format(const char *data, size_t size) {
    ClockFormat result = unpaddedClockFormat(data, size, enabled.load(std::memory_order_relaxed));
    if (result.data != data && changedLogs.fetch_add(1) < 4)
        __android_log_print(ANDROID_LOG_INFO, TAG, "FORMAT %.*s -> %.*s", static_cast<int>(size), data, static_cast<int>(result.size), result.data);
    return result;
}

extern "C" __attribute__((visibility("default"), used))
Loaded native_init(const NativeAPIEntries *entries) {
    if (!isLauncher() || !entries || !entries->hook_func) return nullptr;
    hook = entries->hook_func;
    unhook = entries->unhook_func;
    readCachedConfig();
    loadAndroidWidgetConfig();
    __android_log_print(ANDROID_LOG_INFO, TAG, "INIT launcher uid=%u api=%u", getuid(), entries->version);
    if (!workerStarted.exchange(true)) {
        installWake = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
        pthread_t thread;
        pthread_attr_t attributes;
        pthread_attr_init(&attributes);
        pthread_attr_setstacksize(&attributes, 256 * 1024);
        int result = pthread_create(&thread, &attributes, worker, nullptr);
        pthread_attr_destroy(&attributes);
        if (result == 0) pthread_detach(thread);
        else {
            if (installWake >= 0) { close(installWake); installWake = -1; }
            __android_log_write(ANDROID_LOG_ERROR, TAG, "INIT worker creation failed");
        }
    }
    return onLoaded;
}
