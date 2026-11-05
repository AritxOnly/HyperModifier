#pragma once
#include <atomic>
#include <cstdio>
#include "fold_dual_page_status.h"

namespace widget_status {
// The UID comes ONLY from the already authenticated SystemUI carrier. Xiaomi
// SystemUI may use an application UID rather than android.uid.system.
inline std::atomic<unsigned> readerUid{1000};
inline bool authorizeReader(unsigned uid, unsigned launcherUid) {
    unsigned appId = uid % 100000;
    if (uid / 100000 != launcherUid / 100000 || (appId != 1000 && (appId < 10000 || appId > 19999))) return false;
    readerUid.store(uid, std::memory_order_release);
    return true;
}
enum Stage { Pending = 0, Ready = 1, Incompatible = 2, Failed = 3, Timeout = 4 };
inline std::atomic<int> schema{Pending}, picker{Pending}, instances{Pending}, bridge{Pending};
inline std::atomic<bool> config{false};
// Only installer writes, before release-publishing schema status.
inline char schemaDetail[160] = "pending";
inline char schemaPath[256] = "/system_ext/lib64/libhyper_os_schema_public.so";
inline size_t snapshot(char *out, size_t capacity) {
    int s = schema.load(std::memory_order_acquire);
    return static_cast<size_t>(std::snprintf(out, capacity,
        "MHM_WIDGET_STATUS_V1\nschema=%d\npicker=%d\ninstances=%d\nbridge=%d\nconfig=%d\nreason=%s\npath=%s\nfold_protocol=2\nfold_stage=%d\nfold_received=%d\nfold_requested=%d\nfold_active=%d\nfold_mode_seen=%d\nfold_mode_applied=%d\nfold_grid_seen=%d\nfold_grid_applied=%d\n",
        s, picker.load(), instances.load(), bridge.load(), config.load(),
        s == Ready || s == Incompatible || s == Failed ? schemaDetail : "pending", schemaPath,
        fold_dual_status::stage.load(), fold_dual_status::received.load(), fold_dual_status::requested.load(), fold_dual_status::active.load(),
        mhm_fold_dual_mode_seen.load(), mhm_fold_dual_mode_applied.load(), mhm_fold_dual_grid_seen.load(), mhm_fold_dual_grid_applied.load()));
}
}
