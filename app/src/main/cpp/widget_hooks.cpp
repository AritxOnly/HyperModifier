#include "widget_hooks.h"
#include "widget_policy.h"
#include "native_image_file.h"
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <cstdint>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>

extern "C" {
__attribute__((visibility("hidden"))) void *mhm_original_widget_picker = nullptr;
__attribute__((visibility("hidden"))) uint8_t mhm_widget_picker_enabled = 0;
void mhm_widget_picker_entry();
}

namespace {
constexpr char TAG[] = "MHMDesktopWidget";
struct OptionalString { uint8_t tag; uint8_t padding[7]; const char *data; size_t size; };
static_assert(sizeof(OptionalString) == 24);
using GetString = OptionalString (*)(void *);
using GetMetadata = void *(*)(void *);
using Drop = void (*)(void *);
using GetBoolean = uint64_t (*)(void *, const char *, size_t);
using Contains = bool (*)(void *, const char *, size_t);
GetString activityPackage = nullptr, activityName = nullptr, itemPackage = nullptr, itemName = nullptr;
void *originalActivityMetadata = nullptr, *originalItemMetadata = nullptr;
void *originalClone = nullptr, *originalDrop = nullptr, *originalBoolean = nullptr;
void *originalContains = nullptr;
void *originalActivityDrop = nullptr, *originalItemDrop = nullptr;
WidgetMetadataRegistry registry;
WidgetInstanceOwners instanceOwners;
struct OptionalInfo { uint64_t tag; void *info; };
using GetWidgetInfo = OptionalInfo (*)(void *, int32_t);
void *originalWidgetInfo = nullptr, *originalProviderActivity = nullptr;
void *originalProviderClone = nullptr, *originalProviderDrop = nullptr, *originalActivityClone = nullptr;

std::atomic<bool> stackEnabled{false}, glassEnabled{false}, active{false}, pickerReady{false};
std::atomic<bool> clockGlassEnabled{false};
std::atomic<unsigned> metadataLogs{0};
bool attempted = false, pickerAttempted = false;
GetBoolean getInteger = nullptr;
std::mutex configMutex, persistenceMutex;
WidgetGlassRules glassRules, clockGlassRules;
WidgetStyleRules styleRules;
std::string encodedStyles;
std::string encodedRules = "*", encodedClockGlassRules = "*";
// Called with configMutex held. A rejected / unavailable native bridge must not
// alter picker classification just because a saved option was enabled.
void refreshPickerEnabled() {
    bool enabled = active.load(std::memory_order_acquire) && pickerReady.load(std::memory_order_acquire)
        && (stackEnabled.load() || (glassEnabled.load() && (!encodedRules.empty() || !encodedStyles.empty())));
    __atomic_store_n(&mhm_widget_picker_enabled, static_cast<uint8_t>(enabled), __ATOMIC_RELEASE);
}

void configPath(char (&path)[256]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_deadliner_widgets", getuid() / 100000);
}
void stylesPath(char (&path)[256]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_widget_glass_styles", getuid() / 100000);
}
void experimentPath(char (&path)[256]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_deadliner_clock_glass", getuid() / 100000);
}
void *metadata(void *info, GetMetadata original, GetString package, GetString name) {
    void *result = original(info);
    WidgetIdentity identity;
    // Only real Android widget receivers: never classify application/activity metadata.
    constexpr char providerKey[] = "android.appwidget.provider";
    if (result && originalContains && reinterpret_cast<Contains>(originalContains)(result, providerKey, sizeof(providerKey) - 1)) {
        OptionalString p = package(info), n = name(info);
        if (p.tag == 0 && n.tag == 0 && p.data && n.data)
            identity = widgetIdentity(std::string(p.data, p.size), std::string(n.data, n.size));
    }
    identity.appWidgetId = instanceOwners.get(info);
    registry.observe(info, result, identity);
    if (identity.valid() && metadataLogs.fetch_add(1) < 12)
        __android_log_print(ANDROID_LOG_INFO, TAG, "METADATA provider=%s", identity.component.c_str());
    return result;
}
void *activityMetadataHook(void *info) {
    return metadata(info, reinterpret_cast<GetMetadata>(originalActivityMetadata), activityPackage, activityName);
}
void *itemMetadataHook(void *info) {
    return metadata(info, reinterpret_cast<GetMetadata>(originalItemMetadata), itemPackage, itemName);
}
void *cloneHook(void *bundle) {
    void *copy = reinterpret_cast<GetMetadata>(originalClone)(bundle);
    registry.cloned(bundle, copy);
    return copy;
}
void dropHook(void *bundle) {
    registry.dropped(bundle);
    reinterpret_cast<Drop>(originalDrop)(bundle);
}
void activityDropHook(void *info) {
    // A PackageItemInfo may be a borrowed subobject of this ActivityInfo; it need
    // not receive a separate drop call. Retire its borrowed metadata aliases too.
    if (info && originalActivityMetadata)
        registry.forgetBorrowed(reinterpret_cast<GetMetadata>(originalActivityMetadata)(info));
    registry.forgetOwner(info);
    instanceOwners.set(info, 0);
    reinterpret_cast<Drop>(originalActivityDrop)(info);
}
void itemDropHook(void *info) {
    if (info && originalItemMetadata)
        registry.forgetBorrowed(reinterpret_cast<GetMetadata>(originalItemMetadata)(info));
    registry.forgetOwner(info);
    reinterpret_cast<Drop>(originalItemDrop)(info);
}
OptionalInfo widgetInfoHook(void *manager, int32_t id) {
    OptionalInfo result = reinterpret_cast<GetWidgetInfo>(originalWidgetInfo)(manager, id);
    if ((result.tag & 1) == 0 && result.info) instanceOwners.set(result.info, id);
    return result;
}
void *providerActivityHook(void *provider) {
    void *activity = reinterpret_cast<GetMetadata>(originalProviderActivity)(provider);
    instanceOwners.set(activity, instanceOwners.get(provider));
    return activity;
}
void *providerCloneHook(void *provider) {
    void *copy = reinterpret_cast<GetMetadata>(originalProviderClone)(provider);
    instanceOwners.clone(provider, copy); return copy;
}
void providerDropHook(void *provider) {
    instanceOwners.set(provider, 0);
    reinterpret_cast<Drop>(originalProviderDrop)(provider);
}
void *activityCloneHook(void *activity) {
    void *copy = reinterpret_cast<GetMetadata>(originalActivityClone)(activity);
    instanceOwners.clone(activity, copy); return copy;
}
int materialMode(const WidgetIdentity &identity) {
    return styleRules.resolve(identity, glassRules.matches(identity),
        clockGlassEnabled.load() && clockGlassRules.matches(identity));
}
uint64_t booleanHook(void *bundle, const char *key, size_t size) {
    if (active.load(std::memory_order_acquire)
            && (widgetTextEquals(key, size, "miuiWidget") || widgetTextEquals(key, size, "supportBackgroundBlur")
                || widgetTextEquals(key, size, "usingFolderGlassStyle"))) {
        WidgetIdentity identity = registry.identity(bundle);
        if (identity.valid()) {
            std::lock_guard<std::mutex> guard(configMutex);
            int mode = materialMode(identity);
            if (glassEnabled.load() && widgetTextEquals(key, size, "supportBackgroundBlur"))
                return mode > 0 ? 0x100 : 0;
            if (glassEnabled.load() && mode > 0 && widgetTextEquals(key, size, "usingFolderGlassStyle"))
                return mode == 2 ? 0x100 : 0;
            if (overrideWidgetBoolean(key, size, stackEnabled.load(), glassEnabled.load() && mode > 0))
                return 0x100;
        }
    }
    return reinterpret_cast<GetBoolean>(originalBoolean)(bundle, key, size);
}
bool containsHook(void *bundle, const char *key, size_t size) {
    if (active.load(std::memory_order_acquire) && glassEnabled.load()
            && (widgetTextEquals(key, size, "supportBackgroundBlur") || widgetTextEquals(key, size, "usingFolderGlassStyle"))) {
        WidgetIdentity identity = registry.identity(bundle);
        if (identity.valid()) {
            std::lock_guard<std::mutex> guard(configMutex);
            if (widgetTextEquals(key, size, "supportBackgroundBlur")) return true;
            if (materialMode(identity) > 0 && widgetTextEquals(key, size, "usingFolderGlassStyle")) return true;
        }
    }
    // Preserve key presence for picker classification and all unrelated keys.
    return reinterpret_cast<Contains>(originalContains)(bundle, key, size);
}
bool matchingFile(const char *path, size_t expectedSize, uint64_t expectedHash) {
    return matchingNativeImageFile(path, expectedSize, expectedHash);
}
bool matchingSchema(void *target) {
    Dl_info info{};
    return target && dladdr(target, &info) && info.dli_fname
        && matchingFile(info.dli_fname, 601400, 0xf519ae5224ebc666ULL);
}

}

void loadAndroidWidgetConfig() {
    {
        char path[256]; stylesPath(path);
        FILE *file = std::fopen(path, "rb");
        if (file) {
            char values[16385]{};
            size_t size = std::fread(values, 1, sizeof(values), file); std::fclose(file);
            std::lock_guard<std::mutex> guard(configMutex);
            std::string encoded(values, size);
            if (styleRules.parse(encoded)) encodedStyles = encoded;
        }
    }
    {
        std::lock_guard<std::mutex> guard(configMutex);
        clockGlassRules.parse("*");
        char experiment[256]; experimentPath(experiment);
        FILE *file = std::fopen(experiment, "rb");
        if (file) {
            char values[16387]{};
            size_t size = std::fread(values, 1, sizeof(values), file); std::fclose(file);
            bool enabled = false; std::string rules;
            if (decodeWidgetStyleCache(std::string(values, size), enabled, rules)) {
                clockGlassRules.parse(rules); encodedClockGlassRules = rules; clockGlassEnabled.store(enabled);
            }
        }
    }
    char path[256]; configPath(path);
    FILE *file = std::fopen(path, "rb");
    if (!file) { std::lock_guard<std::mutex> guard(configMutex); glassRules.parse("*"); return; }
    char values[16389]{};
    size_t size = std::fread(values, 1, sizeof(values), file); std::fclose(file);
    bool stack = false, glass = false; std::string rules;
    if (!decodeWidgetCache(std::string(values, size), stack, glass, rules)) return;
    std::lock_guard<std::mutex> guard(configMutex);
    if (!glassRules.parse(rules)) return;
    encodedRules = rules; stackEnabled.store(stack); glassEnabled.store(glass);
    refreshPickerEnabled();
}
bool configureAndroidWidgets(bool stack, bool glass, void *extras) {
    // Scalar Result<i32, error>: low 32 bits tag, high 32 bits value (verified 0x5717c).
    if (!getInteger || !originalBoolean || !extras) return false;
    constexpr char experimentKey[] = "mhm_deadliner_clock_glass";
    uint64_t experimentValue = reinterpret_cast<GetBoolean>(originalBoolean)(extras,
        experimentKey, sizeof(experimentKey) - 1);
    bool experiment = (experimentValue & 0xff) == 0 && ((experimentValue >> 8) & 1) != 0;
    auto readRules = [extras](const char *sizeKey, const char *prefix, std::string &rules) {
        uint64_t length = getInteger(extras, sizeKey, std::strlen(sizeKey));
        if ((length & 0xffffffffULL) != 0 || (length >> 32) > 16384) return false;
        rules.reserve(length >> 32);
        for (size_t offset = 0; offset < (length >> 32); offset += 4) {
            char key[64]; std::snprintf(key, sizeof(key), "%s%zu", prefix, offset / 4);
            uint64_t result = getInteger(extras, key, std::strlen(key));
            if ((result & 0xffffffffULL) != 0) return false;
            uint32_t word = result >> 32;
            for (size_t i = 0; i < 4 && offset + i < (length >> 32); ++i)
                rules.push_back(static_cast<char>((word >> (i * 8)) & 0xff));
        }
        return true;
    };
    std::string rules, styleRules, styles;
    if (!readRules("mhm_widget_rules_size", "mhm_widget_rule_", rules)
            || !readRules("mhm_clock_glass_rules_size", "mhm_clock_glass_rule_", styleRules)
            || !readRules("mhm_glass_styles_size", "mhm_glass_style_", styles)) return false;
    WidgetGlassRules parsedGlass, parsedStyle;
    WidgetStyleRules parsedStyles;
    if (!parsedStyles.parse(styles)) return false;
    if (!parsedGlass.parse(rules) || !parsedStyle.parse(styleRules)) return false;
    // Serialize configuration persistence, but keep disk writes away from capability readers.
    std::lock_guard<std::mutex> persist(persistenceMutex);
    std::unique_lock<std::mutex> guard(configMutex);
    glassRules = std::move(parsedGlass); clockGlassRules = std::move(parsedStyle);
    bool changed = stackEnabled.exchange(stack) != stack || encodedStyles != styles;
    ::styleRules = std::move(parsedStyles); encodedStyles = styles;
    changed = (clockGlassEnabled.exchange(experiment) != experiment) || changed || encodedClockGlassRules != styleRules;
    encodedClockGlassRules = styleRules;
    changed = (glassEnabled.exchange(glass) != glass) || changed || encodedRules != rules;
    encodedRules = rules;
    refreshPickerEnabled();
    if (!changed) return true;
    guard.unlock();
    char stylesFile[256], stylesTemp[272]; stylesPath(stylesFile);
    std::snprintf(stylesTemp, sizeof(stylesTemp), "%s.tmp", stylesFile);
    int stylesFd = open(stylesTemp, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    FILE *savedStyles = stylesFd >= 0 ? fdopen(stylesFd, "wb") : nullptr;
    if (!savedStyles && stylesFd >= 0) close(stylesFd);
    if (savedStyles) {
        bool ok = std::fwrite(styles.data(), 1, styles.size(), savedStyles) == styles.size();
        ok = (std::fclose(savedStyles) == 0) && ok;
        if (ok) rename(stylesTemp, stylesFile); else unlink(stylesTemp);
    }
    char experimentFile[256]; experimentPath(experimentFile);
    char experimentTemp[272]; std::snprintf(experimentTemp, sizeof(experimentTemp), "%s.tmp", experimentFile);
    int experimentFd = open(experimentTemp, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    FILE *styleFile = experimentFd >= 0 ? fdopen(experimentFd, "wb") : nullptr;
    if (!styleFile && experimentFd >= 0) close(experimentFd);
    if (styleFile) {
        std::string cache = encodeWidgetStyleCache(experiment, styleRules);
        bool ok = std::fwrite(cache.data(), 1, cache.size(), styleFile) == cache.size();
        ok = (std::fclose(styleFile) == 0) && ok;
        if (ok) rename(experimentTemp, experimentFile); else unlink(experimentTemp);
    }
    metadataLogs.store(0);
    char path[256], temporary[272]; configPath(path);
    std::snprintf(temporary, sizeof(temporary), "%s.tmp", path);
    int fd = open(temporary, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    FILE *file = fd >= 0 ? fdopen(fd, "wb") : nullptr;
    if (!file && fd >= 0) close(fd);
    if (file) {
        std::string cache = encodeWidgetCache(stack, glass, rules);
        bool ok = std::fwrite(cache.data(), 1, cache.size(), file) == cache.size();
        ok = (std::fclose(file) == 0) && ok;
        if (ok) rename(temporary, path); else unlink(temporary);
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "CONFIG stack=%d glass=%d rules=%zu bytes; reload launcher models", stack, glass, rules.size());
    return true;
}
bool androidWidgetHooksSettled() { return attempted; }
void installAndroidWidgetHooks(void *launcher, WidgetNativeHook hook) {
    if (attempted || !launcher || !hook) return;
    void *target = dlsym(launcher, "ActivityInfo_get_meta_data");
    if (!target) return;
    attempted = true;
    // Whole-file version fingerprint protects all schema getter/lifetime ABIs.
    if (!matchingSchema(target)) {
        __android_log_write(ANDROID_LOG_WARN, TAG, "HOOK rejected: schema file fingerprint mismatch");
        return;
    }
    activityPackage = reinterpret_cast<GetString>(dlsym(launcher, "ActivityInfo_get_package_name"));
    activityName = reinterpret_cast<GetString>(dlsym(launcher, "ActivityInfo_get_name"));
    itemPackage = reinterpret_cast<GetString>(dlsym(launcher, "PackageItemInfo_get_package_name"));
    itemName = reinterpret_cast<GetString>(dlsym(launcher, "PackageItemInfo_get_name"));
    struct Entry { const char *name; void *replacement; void **original; };
    Entry entries[] = {
        {"Bundle_get_boolean", reinterpret_cast<void *>(booleanHook), &originalBoolean},
        {"Bundle_contains_key", reinterpret_cast<void *>(containsHook), &originalContains},
        {"Bundle_clone", reinterpret_cast<void *>(cloneHook), &originalClone},
        {"Bundle_drop", reinterpret_cast<void *>(dropHook), &originalDrop},
        {"ActivityInfo_drop", reinterpret_cast<void *>(activityDropHook), &originalActivityDrop},
        {"PackageItemInfo_drop", reinterpret_cast<void *>(itemDropHook), &originalItemDrop},
        {"ActivityInfo_get_meta_data", reinterpret_cast<void *>(activityMetadataHook), &originalActivityMetadata},
        {"PackageItemInfo_get_meta_data", reinterpret_cast<void *>(itemMetadataHook), &originalItemMetadata},
    };
    if (!activityPackage || !activityName || !itemPackage || !itemName) return;
    for (const auto &entry : entries) if (!dlsym(launcher, entry.name)) return;
    for (const auto &entry : entries) {
        int result = hook(dlsym(launcher, entry.name), entry.replacement, entry.original);
        if (result != 0 || !*entry.original) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "HOOK %s result=%d; capability override disabled", entry.name, result);
            return;
        }
    }
    getInteger = reinterpret_cast<GetBoolean>(dlsym(launcher, "Bundle_get_i32"));
    if (!getInteger) return;
    // Verify the SDK whose call sites establish the Option<owned info> and pointer getter ABI.
    Dl_info sdk{}; void *sdkEntry = dlsym(launcher, "native_widget_free_native_data");
    if (sdkEntry && dladdr(sdkEntry, &sdk) && sdk.dli_fname
            && matchingFile(sdk.dli_fname, 2679408, 0x67fdc4bf9c2e1b24ULL)) {
        Entry instanceEntries[] = {
            {"AppWidgetManager_get_app_widget_info", reinterpret_cast<void *>(widgetInfoHook), &originalWidgetInfo},
            {"AppWidgetProviderInfo_get_providerInfo", reinterpret_cast<void *>(providerActivityHook), &originalProviderActivity},
            {"AppWidgetProviderInfo_clone", reinterpret_cast<void *>(providerCloneHook), &originalProviderClone},
            {"AppWidgetProviderInfo_drop", reinterpret_cast<void *>(providerDropHook), &originalProviderDrop},
            {"ActivityInfo_clone", reinterpret_cast<void *>(activityCloneHook), &originalActivityClone},
        };
        bool present = true;
        for (const auto &entry : instanceEntries) present = present && dlsym(launcher, entry.name);
        if (present) {
            // Install ID-producing hook last, so partial installation cannot tag metadata.
            for (int index = 4; index >= 0; --index) {
                const auto &entry = instanceEntries[index];
                if (hook(dlsym(launcher, entry.name), entry.replacement, entry.original) != 0 || !*entry.original) break;
            }
        }
    }
    active.store(true, std::memory_order_release);
    { std::lock_guard<std::mutex> guard(configMutex); refreshPickerEnabled(); }
    __android_log_write(ANDROID_LOG_INFO, TAG, "HOOK metadata capability bridge ready (full desktop capabilities; original MIUI key presence)");
}

bool androidWidgetPickerHooksSettled() { return pickerAttempted; }
void installAndroidWidgetPickerHooks(const char *path, uintptr_t base, WidgetNativeHook hook) {
    if (pickerAttempted || !path || !base || !hook) return;
    pickerAttempted = true;
    // Exact launcher Dart AOT image. Address is a classification read in
    // buildAppWidgetsItems, not a global isMiuiWidget getter or provider mutation.
    constexpr uintptr_t site = 0x1a83de0;
    constexpr unsigned char entry[] = {
        0x00, 0x0a, 0x20, 0x37, // tbnz w0, #4, 0x1a83f20
        0x51, 0x18, 0xc3, 0x97,
        0xa0, 0x03, 0x1b, 0xf8,
        0x40, 0x3f, 0x40, 0xf9,
    };
    if (!matchingFile(path, 29382056, 0xa9c299ea503f9171ULL)
            || std::memcmp(reinterpret_cast<void *>(base + site), entry, sizeof(entry)) != 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
            "PICKER rejected: Dart image or branch fingerprint mismatch path=%s", path);
        return;
    }
    int result = hook(reinterpret_cast<void *>(base + site),
        reinterpret_cast<void *>(mhm_widget_picker_entry), &mhm_original_widget_picker);
    if (result == 0 && mhm_original_widget_picker) {
        pickerReady.store(true, std::memory_order_release);
        std::lock_guard<std::mutex> guard(configMutex);
        refreshPickerEnabled();
    }
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "PICKER apk-aware Android AppWidget branch result=%d ready=%d path=%s",
        result, pickerReady.load(), path);
}
