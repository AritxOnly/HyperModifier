#pragma once
#include "widget_policy.h"
#include <atomic>
#include <memory>
#include <string_view>

// The settings writer publishes only complete rules. Cache misses on the Dart
// UI thread can use the last committed selection while settings are changing.
struct FoldWidgetRules {
    WidgetGlassRules glass, clock;
    WidgetStyleRules styles;
    bool clockEnabled;
    bool clear(const WidgetIdentity &identity) const {
        return styles.resolve(identity, glass.matches(identity), clockEnabled && clock.matches(identity)) == 2;
    }
};

struct FoldWidgetText {
    std::string_view bytes;
    bool wide = false;

    std::string ruleKey() const {
        if (!wide) return std::string(bytes);
        // Rule tokens are ASCII. Preserve all eligible identifiers exactly;
        // any other code unit cannot match a configured token but still
        // participates in the usual package and wildcard fallbacks.
        std::string result(bytes.size() / 2, '\0');
        for (size_t i = 0; i < result.size(); ++i) {
            uint16_t unit;
            std::memcpy(&unit, bytes.data() + 2 * i, sizeof(unit));
            result[i] = unit <= 127 ? static_cast<char>(unit) : static_cast<char>(0xff);
        }
        return result;
    }
};
