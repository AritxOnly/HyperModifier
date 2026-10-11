#include "../../app/src/main/cpp/clock_format.h"
#include <cassert>
#include <initializer_list>

int main() {
    // Borrowed slices need not be NUL-terminated. The minute suffix is retained.
    const char slice[] = {'H','H',':','m','m','X'};
    auto result = unpaddedClockFormat(slice, 5, true);
    assert(result.size == 4 && std::memcmp(result.data, "H:mm", 4) == 0);
    result = unpaddedClockFormat(slice, 5, false);
    assert(result.data == slice && result.size == 5);
    result = unpaddedClockFormat("HH", 2, true);
    assert(result.size == 1 && result.data[0] == 'H');
    result = unpaddedClockFormat("HH:mm:ss", 8, true);
    assert(result.size == 7 && std::memcmp(result.data, "H:mm:ss", 7) == 0);
    // Do not change 12-hour formats, minute-only strings, quoted literals,
    // fixed four-digit arrays, date/time composites, or malformed slices.
    for (const char *text : {"hh:mm", "h:mm", "mm", "HHmm", "'HH:mm'", "yyyy-MM-dd HH:mm", "HHH", ""}) {
        size_t size = std::strlen(text);
        result = unpaddedClockFormat(text, size, true);
        assert(result.data == text && result.size == size);
    }
    result = unpaddedClockFormat(nullptr, 0, true);
    assert(result.data == nullptr && result.size == 0);
}
