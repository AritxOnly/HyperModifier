#pragma once
#include <cstdint>
using WidgetNativeHook = int (*)(void *, void *, void **);
using WidgetNativeUnhook = int (*)(void *);
void installAndroidWidgetHooks(void *launcherHandle, WidgetNativeHook hook);
bool androidWidgetHooksSettled();
bool configureAndroidWidgets(bool stack, bool glass, void *extras);
void loadAndroidWidgetConfig();

void installAndroidWidgetPickerHooks(const char *dartPath, uintptr_t dartBase, WidgetNativeHook hook, WidgetNativeUnhook unhook);
bool androidWidgetPickerHooksSettled();
