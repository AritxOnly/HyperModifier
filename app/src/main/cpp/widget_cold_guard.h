#pragma once
#include "clock_glass.h"
#include "widget_hooks.h"

struct WidgetHookBackend {
    WidgetNativeHook hook;
    WidgetNativeUnhook unhook;
};

// Called on the existing, serialized launcher installer. Records successful
// widget detours even if the Flutter mapping arrives after the metadata DSO.
WidgetHookBackend widgetColdGuard(ClockGlassImage flutter, WidgetHookBackend backend);

bool widgetColdGuardPending();
