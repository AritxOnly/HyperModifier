#include "schema_abi_origin.h"
#include <cassert>
#include <iostream>

int main(int argc, char **argv) {
    assert(argc == 3);
    std::vector<uint8_t> schemaBytes, widgetBytes;
    assert(schema_abi::load(argv[1], schemaBytes) && schema_abi::load(argv[2], widgetBytes));
    schema_abi::Image schema(schemaBytes), widget(widgetBytes);
    assert(schema.init() && widget.init());
    std::string reason;
    for (const auto &root : schema_abi::functions) if (root.name) {
        const schema_abi::Contract *matched = nullptr;
        std::vector<uint64_t> locations;
        assert(schema_abi::compatibleExport(schema, root.name, reason, matched, locations));
    }
    for (const char *name : {"AppWidgetProviderInfo_clone", "AppWidgetProviderInfo_drop"}) {
        const schema_abi::Contract *matched = nullptr;
        std::vector<uint64_t> locations;
        assert(schema_abi::compatibleExport(widget, name, reason, matched, locations));
        uint64_t schemaAddress = 0, schemaSize = 0, widgetAddress = 0, widgetSize = 0;
        assert(schema.find(name, schemaAddress, schemaSize) && widget.find(name, widgetAddress, widgetSize));
        // The old rule rejects this legitimate origin before checking any code.
        assert(0x10000000 + schemaAddress != 0x20000000 + widgetAddress);
        size_t helpers = 0;
        for (size_t i = 0; i < locations.size(); ++i) if (locations[i]) {
            const uint8_t *code = nullptr;
            assert(widget.code(locations[i], matched->functions[i].size, code));
            std::vector<uint8_t> live(code, code + matched->functions[i].size);
            assert(schema_abi::sameLiveCode(live.data(), code, live.size()));
            live[0] ^= 1;
            assert(!schema_abi::sameLiveCode(live.data(), code, live.size()));
            // A change in each reachable helper must also fail file ABI review.
            auto changed = widgetBytes;
            changed[static_cast<size_t>(code - widgetBytes.data())] ^= 1;
            schema_abi::Image invalid(changed); assert(invalid.init());
            const schema_abi::Contract *rejected = nullptr; std::vector<uint64_t> ignored;
            assert(!schema_abi::compatibleExport(invalid, name, reason, rejected, ignored));
            if (!matched->functions[i].name) ++helpers;
        }
        assert(helpers > 0); // Do not reduce drop validation to its 24-byte thunk.
    }
    const schema_abi::Contract *matched = nullptr; std::vector<uint64_t> ignored;
    assert(!schema_abi::compatibleExport(widget, "Bundle_get_boolean", reason, matched, ignored));
    assert(!schema_abi::compatibleExport(widget, "unreviewed", reason, matched, ignored));
    std::cout << "Schema origins: 19 canonical exports, both legitimate alternate exports, full clone/drop helper mutations and live edits passed\n";
}
