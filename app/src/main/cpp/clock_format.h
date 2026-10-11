#pragma once
#include <cstddef>
#include <cstring>

struct ClockFormat { const char *data; size_t size; };

// Exact time-only formats observed in the classic MAML widget. No dates, quoted
// literals, fixed-width numeric arrays, or 12-hour formats are rewritten.
inline ClockFormat unpaddedClockFormat(const char *data, size_t size, bool enabled) {
    if (enabled && data != nullptr) {
        if (size == 2 && std::memcmp(data, "HH", 2) == 0) return {"H", 1};
        if (size == 5 && std::memcmp(data, "HH:mm", 5) == 0) return {"H:mm", 4};
        if (size == 8 && std::memcmp(data, "HH:mm:ss", 8) == 0) return {"H:mm:ss", 7};
    }
    return {data, size};
}
