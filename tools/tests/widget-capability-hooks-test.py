#!/usr/bin/env python3
"""Exercise production widget capability hooks, including host allocation checks.

Only the installer (which calls foreign ARM64 code) and Android logging are
excluded. Registry, rule parsing and the boolean/presence hooks are real code.
No device or app data is touched. This does not measure device CPU or power.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "app/src/main/cpp/widget_hooks.cpp").read_text()
source = source[:source.index("bool matchingSchema(")]
source = "\n".join(line for line in source.splitlines() if not line.startswith("#include"))

platform = r'''
#include "fold_widget_rules.h"
#include <atomic>
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <new>
#include <unistd.h>
constexpr int ANDROID_LOG_INFO = 4;
inline int __android_log_print(int, const char *, const char *, ...) { return 0; }
static std::atomic<size_t> allocations{0};
void *operator new(size_t size) {
    if (void *value = std::malloc(size ? size : 1)) { ++allocations; return value; }
    throw std::bad_alloc();
}
void operator delete(void *value) noexcept { std::free(value); }
void operator delete(void *value, size_t) noexcept { std::free(value); }
'''

driver = r'''
} // production anonymous namespace
constexpr uint64_t stock = 0x200;
unsigned booleanCalls = 0, containsCalls = 0;
uint64_t stockBoolean(void *, const char *, size_t) { ++booleanCalls; return stock; }
bool stockContains(void *, const char *, size_t) { ++containsCalls; return false; }
uint64_t get(void *bundle, const char *key) { return booleanHook(bundle, key, std::strlen(key)); }
bool contains(void *bundle, const char *key) { return containsHook(bundle, key, std::strlen(key)); }
int main() {
    originalBoolean = reinterpret_cast<void *>(stockBoolean);
    originalContains = reinterpret_cast<void *>(stockContains);
    int owner, bundle, copy, unknown, conflictOwner;
    auto identity = widgetIdentity("com.example.long.widget.package", ".LongWidgetProvider");
    identity.appWidgetId = 42;
    registry.observe(&owner, &bundle, identity);
    registry.cloned(&bundle, &copy);
    assert(glassRules.parse("*")); assert(clockGlassRules.parse("*"));
    assert(styleRules.parse(""));
    for (bool installed : {false, true}) for (bool stack : {false, true})
    for (bool glass : {false, true}) for (bool clock : {false, true}) {
        active.store(installed); stackEnabled.store(stack);
        glassEnabled.store(glass); clockGlassEnabled.store(clock);
        for (void *target : {&bundle, &copy}) {
            assert(get(target, "miuiWidget") == (installed && (stack || glass) ? 0x100 : stock));
            assert(get(target, "supportBackgroundBlur") == (installed && glass ? 0x100 : stock));
            assert(get(target, "usingFolderGlassStyle") == (installed && glass ? (clock ? 0x100 : 0) : stock));
            assert(!contains(target, "miuiWidget")); // Never invent picker classification metadata.
            assert(contains(target, "supportBackgroundBlur") == (installed && glass));
            assert(contains(target, "usingFolderGlassStyle") == (installed && glass));
            assert(get(target, "miuiWidgetRefresh") == stock && !contains(target, "miuiWidgetRefresh"));
        }
        assert(get(&unknown, "miuiWidget") == stock && get(&unknown, "supportBackgroundBlur") == stock);
        assert(!contains(&unknown, "supportBackgroundBlur"));
    }
    active.store(true); stackEnabled.store(true); glassEnabled.store(true);
    // Stack remains available for excluded / explicit OFF widgets. Blur OFF is
    // explicit false, while style OFF continues to delegate to the stock value.
    for (bool explicitOff : {false, true}) {
        assert(glassRules.parse(explicitOff ? "*" : "*\n-" + identity.component));
        assert(styleRules.parse(explicitOff ? identity.component + "#42\t0" : ""));
        assert(get(&bundle, "miuiWidget") == 0x100);
        assert(get(&unknown, "miuiWidget") == stock);
        assert(get(&bundle, "supportBackgroundBlur") == 0);
        assert(get(&bundle, "usingFolderGlassStyle") == stock);
        assert(contains(&bundle, "supportBackgroundBlur") && !contains(&bundle, "usingFolderGlassStyle"));
        stackEnabled.store(false);
        assert(get(&bundle, "miuiWidget") == stock);
        stackEnabled.store(true);
    }
    // These paths must not wait for material configuration, even when glass is on.
    configMutex.lock();
    auto before = allocations.load();
    for (unsigned i = 0; i < 10000; ++i) {
        assert(get(&bundle, "miuiWidget") == 0x100);
        assert(contains(&copy, "supportBackgroundBlur"));
    }
    assert(allocations.load() == before);
    configMutex.unlock();
    // With both features disabled even relevant keys must leave immediately.
    stackEnabled.store(false); glassEnabled.store(false);
    configMutex.lock();
    assert(get(&bundle, "miuiWidget") == stock);
    assert(get(&bundle, "supportBackgroundBlur") == stock);
    assert(get(&bundle, "usingFolderGlassStyle") == stock);
    assert(!contains(&bundle, "supportBackgroundBlur"));
    configMutex.unlock();
    stackEnabled.store(true); glassEnabled.store(true);
    // Material matchers must also avoid constructing exclusion/instance strings.
    assert(glassRules.parse("*\n-" + identity.package));
    assert(styleRules.parse(identity.component + "#42\t2"));
    before = allocations.load();
    for (unsigned i = 0; i < 10000; ++i) {
        assert(!glassRules.matches(identity));
        assert(styleRules.resolve(identity, false, false) == 2);
    }
    assert(allocations.load() == before);
    // Conflicting borrowed aliases, owned clones and address reuse retain the
    // lifecycle rules used by the original capability bridge.
    auto other = widgetIdentity("org.other.widget", ".Widget");
    registry.observe(&conflictOwner, &bundle, other);
    glassEnabled.store(false);
    assert(get(&bundle, "miuiWidget") == stock && get(&copy, "miuiWidget") == 0x100);
    registry.forgetBorrowed(&bundle); registry.dropped(&copy);
    assert(get(&bundle, "miuiWidget") == stock && get(&copy, "miuiWidget") == stock);
    registry.observe(&owner, &bundle, other);
    assert(get(&bundle, "miuiWidget") == 0x100);
    stackEnabled.store(false);
    assert(get(&bundle, "miuiWidget") == stock);
    std::cout << "Production widget hooks: enable/disable, stock fallbacks, OFF/exclusions, "
        "clone/conflict/address reuse; 20000 capability reads without config lock or allocation "
        "and 20000 rule lookups without allocation passed\n";
}
'''

with tempfile.TemporaryDirectory(prefix="mhm-widget-capability-") as temporary:
    path = Path(temporary)
    (path / "test.cpp").write_text(platform + source + driver)
    subprocess.run([
        "clang++", "-std=c++17", "-O1", "-Wall", "-Wextra", "-Werror",
        # The prefix contains state used only by the excluded installer.
        "-Wno-unused-variable", "-Wno-unused-function", "-pthread",
        "-fsanitize=address,undefined", "-I", str(root / "app/src/main/cpp"),
        str(path / "test.cpp"), "-o", str(path / "test"),
    ], check=True)
    subprocess.run([str(path / "test")], check=True, timeout=15)
