#pragma once
#include <atomic>
#include <cstdio>

namespace widget_status {
enum Stage { Pending = 0, Ready = 1, Incompatible = 2, Failed = 3, Timeout = 4 };
inline std::atomic<int> schema{Pending}, picker{Pending}, instances{Pending}, bridge{Pending};
inline std::atomic<bool> config{false};
// Only installer writes, before release-publishing schema status.
inline char schemaDetail[160] = "pending";
inline char schemaPath[256] = "/system_ext/lib64/libhyper_os_schema_public.so";
inline size_t snapshot(char *out, size_t capacity) {
    int s = schema.load(std::memory_order_acquire);
    return static_cast<size_t>(std::snprintf(out, capacity,
        "MHM_WIDGET_STATUS_V1\nschema=%d\npicker=%d\ninstances=%d\nbridge=%d\nconfig=%d\nreason=%s\npath=%s\n",
        s, picker.load(), instances.load(), bridge.load(), config.load(),
        s == Ready || s == Incompatible || s == Failed ? schemaDetail : "pending", schemaPath));
}
}
