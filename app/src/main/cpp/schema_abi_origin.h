#pragma once
#include "schema_abi.h"
#include "appwidget_abi_profiles.h"

namespace schema_abi {
// Validate the selected export against reviewed ABI families in its *own* ELF.
// Neither the filename nor a coincidentally identical code size grants trust.
inline bool compatibleExport(const Image &image, const char *symbol, std::string &reason,
                             const Contract *&matched, std::vector<uint64_t> &locations) {
    std::string primaryReason;
    for (const Contract *profile : {&contract, &appwidget::contract}) {
        if (image.compatibleUsing(*profile, symbol, reason, &locations)) { matched = profile; return true; }
        if (primaryReason.empty()) primaryReason = reason;
    }
    if (reason == "unreviewed-export") reason = primaryReason;
    return false;
}
// Separate address identity from live-code identity. The loader must prove that
// the ranges are readable/executable before calling this bounded comparison.
inline bool sameLiveCode(const uint8_t *live, const uint8_t *file, size_t size) {
    return live && file && !std::memcmp(live, file, size);
}
}
