#include "../../app/src/main/cpp/widget_policy.h"
#include <cassert>
#include <thread>

int main() {
    auto compact = widgetIdentity("com.aritxonly.deadliner", ".widgets.glance.ui.CompactTaskGlanceWidgetReceiver");
    auto capsule = widgetIdentity("com.aritxonly.deadliner", ".widgets.glance.ui.CapsuleTaskGlanceWidgetReceiver");
    auto otherIdentity = widgetIdentity("org.example.calendar", "org.example.calendar.Widget");
    WidgetGlassRules rules;
    assert(rules.parse(compact.component));
    assert(rules.matches(compact)); assert(!rules.matches(capsule)); assert(!rules.matches(otherIdentity));
    assert(rules.parse(compact.package + "\n" + otherIdentity.component));
    assert(rules.matches(compact)); assert(rules.matches(capsule)); assert(rules.matches(otherIdentity));
    assert(!rules.parse("bad rule")); assert(rules.matches(compact)); // malformed input does not replace valid configuration
    assert(!rules.parse(std::string(16385, 'a')));
    assert(rules.parse("")); assert(!rules.matches(compact));
    assert(widgetIdentity("org.example", "Widget").component == "org.example/org.example.Widget");
    assert(!widgetIdentity("", "Widget").valid());
    assert(!overrideWidgetBoolean("miuiWidget", 10, false, false));
    assert(overrideWidgetBoolean("miuiWidget", 10, true, false));
    assert(overrideWidgetBoolean("miuiWidget", 10, false, true));
    assert(!overrideWidgetBoolean("supportBackgroundBlur", 21, true, false));
    assert(overrideWidgetBoolean("supportBackgroundBlur", 21, false, true));
    assert(!overrideWidgetBoolean("miuiWidgetRefresh", 17, true, true));
    assert(!overrideWidgetBoolean(nullptr, 0, true, true));
    // Value and presence must remain independent: Flutter still receives MIUI
    // capability for sizing / glass, but classification cannot gain a MIUI key.
    assert(!overrideWidgetPresence("miuiWidget", 10, false));
    assert(!overrideWidgetPresence("miuiWidget", 10, true));
    assert(!overrideWidgetPresence("supportBackgroundBlur", 21, false));
    assert(overrideWidgetPresence("supportBackgroundBlur", 21, true));
    assert(!overrideWidgetPresence("miuiWidgetRefresh", 17, true));
    assert(!overrideWidgetPresence(nullptr, 0, true));

    assert(rules.parse("*"));
    assert(rules.matches(compact) && rules.matches(capsule) && rules.matches(otherIdentity));
    assert(rules.parse("*\n-" + compact.component));
    assert(!rules.matches(compact) && rules.matches(capsule) && rules.matches(otherIdentity));
    assert(rules.parse("*\n-" + compact.package));
    assert(!rules.matches(compact) && !rules.matches(capsule) && rules.matches(otherIdentity));
    assert(!rules.parse("-*")); assert(!rules.parse("--org.example"));
    assert(!rules.matches(WidgetIdentity{}));

    assert(overrideWidgetGlassStyle(compact, "usingFolderGlassStyle", 21, true, true));
    assert(overrideWidgetGlassStyle(capsule, "usingFolderGlassStyle", 21, true, true));
    assert(overrideWidgetGlassStyle(otherIdentity, "usingFolderGlassStyle", 21, true, true));
    assert(!overrideWidgetGlassStyle(compact, "usingFolderGlassStyle", 21, false, true));
    assert(!overrideWidgetGlassStyle(compact, "usingFolderGlassStyle", 21, true, false));
    assert(!overrideWidgetGlassStyle(compact, "supportBackgroundBlur", 21, true, true));
    assert(!overrideWidgetGlassStyle({}, "usingFolderGlassStyle", 21, true, true));

    bool stack = false, glass = false; std::string cached;
    assert(decodeWidgetCache("11", stack, glass, cached));
    assert(stack && glass && cached == "*");
    assert(decodeWidgetCache(encodeWidgetCache(true, true, ""), stack, glass, cached));
    assert(stack && glass && cached.empty()); // empty new selection must never become the legacy whitelist
    assert(decodeWidgetCache(encodeWidgetCache(false, true, capsule.component), stack, glass, cached));
    assert(!stack && glass && cached == capsule.component);
    assert(decodeWidgetCache("211\n" + compact.component, stack, glass, cached));
    assert(stack && glass && cached == "*");
    assert(decodeWidgetCache(encodeWidgetCache(true, true, "*\n-" + compact.package), stack, glass, cached));
    assert(cached == "*\n-" + compact.package);
    assert(!decodeWidgetCache("311\n-*", stack, glass, cached));
    assert(!decodeWidgetCache("211", stack, glass, cached));
    assert(!decodeWidgetCache("2xx\n", stack, glass, cached));
    assert(!decodeWidgetCache("211\nbad rule", stack, glass, cached));

    bool style = false;
    assert(decodeWidgetStyleCache("1", style, cached) && style && cached == "*");
    assert(decodeWidgetStyleCache("0", style, cached) && !style && cached == "*");
    assert(decodeWidgetStyleCache(encodeWidgetStyleCache(true, "*\n-" + compact.component), style, cached));
    WidgetGlassRules styleRules;
    assert(style && styleRules.parse(cached));
    assert(!styleRules.matches(compact) && styleRules.matches(capsule) && styleRules.matches(otherIdentity));
    assert(rules.parse("*\n-" + otherIdentity.package));
    assert(!overrideWidgetGlassStyle(otherIdentity, "usingFolderGlassStyle", 21, style, rules.matches(otherIdentity)));
    assert(decodeWidgetStyleCache("1\n", style, cached) && cached.empty());
    assert(!decodeWidgetStyleCache("", style, cached));
    assert(!decodeWidgetStyleCache("x", style, cached));
    assert(!decodeWidgetStyleCache("1x", style, cached));
    assert(!decodeWidgetStyleCache("1\n-*", style, cached));
    assert(!decodeWidgetStyleCache("1\n" + std::string(16385, 'a'), style, cached));

    WidgetStyleRules explicitStyles;
    auto firstInstance = compact; firstInstance.appWidgetId = 42;
    auto secondInstance = compact; secondInstance.appWidgetId = 43;
    assert(explicitStyles.parse(compact.package + "\t1\n" + compact.component + "\t2\n" + compact.component + "#42\t0"));
    assert(explicitStyles.resolve(firstInstance, true, true) == 0);
    assert(explicitStyles.resolve(secondInstance, true, true) == 2);
    assert(explicitStyles.resolve(capsule, true, true) == 1);
    assert(!explicitStyles.parse(compact.component + "#0\t2"));
    assert(!explicitStyles.parse(compact.component + "#2147483648\t2"));
    assert(!explicitStyles.parse(compact.component + "\t3"));
    assert(!explicitStyles.parse("*\t1"));
    assert(!explicitStyles.parse("-org.example\t1"));
    assert(explicitStyles.resolve(firstInstance, true, true) == 0); // rejected update preserves last configuration
    assert(explicitStyles.parse(compact.package + "\t3"));
    assert(explicitStyles.resolve(compact, true, true) == 2);
    assert(explicitStyles.resolve(compact, false, true) == 0);
    assert(explicitStyles.parse(""));
    assert(explicitStyles.resolve(compact, true, false) == 1);
    WidgetInstanceOwners ownedIds;
    int infoOwner, activityOwner, clonedOwner, untaggedOwner;
    ownedIds.set(&infoOwner, 42);
    ownedIds.clone(&infoOwner, &activityOwner);
    ownedIds.clone(&activityOwner, &clonedOwner);
    ownedIds.set(&infoOwner, 0); ownedIds.set(&activityOwner, 0);
    assert(ownedIds.get(&clonedOwner) == 42);
    ownedIds.clone(&untaggedOwner, &clonedOwner);
    assert(ownedIds.get(&clonedOwner) == 0); // allocator reuse must never retain a previous ID

    // Metadata clones outlive the ActivityInfo that provided them. Another owner's
    // unrelated clone must not inherit the whitelist after allocator address reuse.
    WidgetMetadataRegistry registry;
    int owner1, owner2, bundle, copy, other;
    registry.observe(&owner1, &bundle, compact);
    registry.observe(&owner2, &bundle, compact);
    registry.cloned(&bundle, &copy);
    registry.forgetOwner(&owner1);
    assert(registry.selected(&bundle));
    registry.forgetOwner(&owner2);
    assert(!registry.selected(&bundle));
    assert(registry.selected(&copy));
    assert(registry.identity(&copy).component == compact.component);
    registry.dropped(&copy);
    assert(!registry.selected(&copy));
    registry.cloned(&other, &copy);
    assert(!registry.selected(&copy));
    registry.observe(&owner1, &bundle, compact);
    registry.observe(&owner1, &other, {});
    assert(!registry.selected(&bundle));
    registry.cloned(&bundle, &copy);
    assert(!registry.selected(&copy));
    registry.observe(&owner1, &bundle, compact);
    registry.observe(&owner2, &bundle, compact);
    registry.cloned(&bundle, &copy);
    registry.forgetBorrowed(&bundle); // owner/subobject aliases die together
    assert(!registry.selected(&bundle));
    assert(registry.selected(&copy));
    registry.forgetOwner(&owner1);
    registry.forgetOwner(&owner2);
    registry.dropped(&copy);

    registry.observe(&owner1, &bundle, firstInstance);
    registry.cloned(&bundle, &copy);
    assert(registry.identity(&copy).appWidgetId == 42);
    registry.observe(&owner2, &bundle, secondInstance);
    assert(registry.identity(&bundle).appWidgetId == 0); // shared metadata cannot leak the last instance's override
    assert(registry.identity(&copy).appWidgetId == 42); // already-owned clone retains its original identity
    registry.forgetOwner(&owner1); registry.forgetOwner(&owner2); registry.dropped(&copy);

    // Provider discovery and RemoteViews updates can run on different threads.
    auto exercise = [&registry, &compact]() {
        int owner, metadata, clone;
        for (int i = 0; i < 1000; ++i) {
            registry.observe(&owner, &metadata, compact);
            registry.cloned(&metadata, &clone);
            registry.forgetOwner(&owner);
            assert(registry.selected(&clone));
            registry.dropped(&clone);
            assert(!registry.selected(&clone));
        }
    };
    std::thread first(exercise), second(exercise);
    first.join(); second.join();
}
